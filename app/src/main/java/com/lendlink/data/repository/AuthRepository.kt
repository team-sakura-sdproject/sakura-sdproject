package com.lendlink.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.lendlink.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun login(email: String, pass: String): Result<User> = runCatching {
        val res = auth.signInWithEmailAndPassword(email, pass).await()
        val uid = res.user?.uid ?: throw Exception("Login failed")
        val snap = db.child("users/$uid").get().await()
        snap.getValue(User::class.java) ?: throw Exception("User data not found")
    }

    suspend fun register(
        username: String, email: String, phone: String, 
        pass: String, role: String, lat: Double, lng: Double, addr: String
    ): Result<Unit> = runCatching {
        val res = auth.createUserWithEmailAndPassword(email, pass).await()
        val uid = res.user?.uid ?: throw Exception("Registration failed")
        val user = User(uid, username, email, phone, role, addr, lat, lng)
        
        // Save user and init wallet
        try {
            db.child("users/$uid").setValue(user).await()
        } catch (e: Exception) {
            // Delete user if database entry fails to avoid orphaned auth users
            auth.currentUser?.delete()?.await()
            throw e
        }
        
        val initialBalance = if (role == "borrower") 100000L else 0L
        try {
            db.child("wallets/$uid/balance").setValue(initialBalance).await()
        } catch (e: Exception) {
            // Non-fatal for account creation, but log it
            e.printStackTrace()
        }
        
        // Auto-logout after registration to force first login
        auth.signOut()
    }

    suspend fun checkUsernameExists(u: String): Boolean = try {
        val snap = db.child("users").orderByChild("username").equalTo(u).get().await()
        snap.exists()
    } catch (e: Exception) {
        false // Default to false if we can't check (e.g. permission denied or offline)
    }

    suspend fun checkEmailExists(e: String): Boolean = try {
        val snap = db.child("users").orderByChild("email").equalTo(e).get().await()
        snap.exists()
    } catch (ex: Exception) {
        false
    }

    suspend fun checkPhoneExists(p: String): Boolean = try {
        val snap = db.child("users").orderByChild("phone").equalTo(p).get().await()
        snap.exists()
    } catch (ex: Exception) {
        false
    }

    fun observeUser(uid: String): Flow<User> = callbackFlow {
        val ref = db.child("users/$uid")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                s.getValue(User::class.java)?.let { trySend(it) }
            }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    fun logout() = auth.signOut()

    suspend fun updateProfileImage(uid: String, bytes: ByteArray?): Result<String> = runCatching {
        if (bytes == null) throw Exception("No image data")
        
        // Match the path in Firebase Storage rules: profile_images/
        val ref = storage.child("profile_images/$uid.jpg")
        
        // Add metadata to satisfy storage rules requirement for contentType
        val metadata = com.google.firebase.storage.storageMetadata {
            contentType = "image/jpeg"
        }
        
        ref.putBytes(bytes, metadata).await()
        val url = ref.downloadUrl.await().toString()
        db.child("users/$uid/profileImageUrl").setValue(url).await()
        url
    }
}
