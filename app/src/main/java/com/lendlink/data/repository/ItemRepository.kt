package com.lendlink.data.repository

import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.lendlink.data.local.ItemDao
import com.lendlink.data.model.Category
import com.lendlink.data.model.Item
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ItemRepository(private val dao: ItemDao) {
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    suspend fun addItem(item: Item, imageBytes: ByteArray?): Result<Item> = runCatching {
        val itemId = db.child("items").push().key ?: throw Exception("Failed to generate item ID")
        var imageUrl = ""
        if (imageBytes != null) {
            val imageId = "${itemId}_${System.currentTimeMillis()}"
            val ref = storage.child("item_images/$imageId.jpg")
            val metadata = com.google.firebase.storage.storageMetadata {
                contentType = "image/jpeg"
            }
            ref.putBytes(imageBytes, metadata).await()
            imageUrl = ref.downloadUrl.await().toString()
        }
        val finalItem = item.copy(itemId = itemId, imageUrl = imageUrl)
        db.child("items/$itemId").setValue(finalItem).await()
        dao.upsert(finalItem)
        finalItem
    }

    suspend fun updateItem(item: Item, newImageBytes: ByteArray?): Result<Item> = runCatching {
        var imageUrl = item.imageUrl
        if (newImageBytes != null) {
            val imageId = "${item.itemId}_${System.currentTimeMillis()}"
            val ref = storage.child("item_images/$imageId.jpg")
            val metadata = com.google.firebase.storage.storageMetadata {
                contentType = "image/jpeg"
            }
            ref.putBytes(newImageBytes, metadata).await()
            imageUrl = ref.downloadUrl.await().toString()
        }
        val updated = item.copy(imageUrl = imageUrl)
        db.child("items/${item.itemId}").setValue(updated).await()
        dao.upsert(updated)
        updated
    }

    suspend fun deleteItem(itemId: String, lenderId: String, category: String): Result<Unit> = runCatching {
        db.child("items/$itemId").removeValue().await()
        dao.delete(itemId)
    }

    fun observeAvailable(lenderId: String): Flow<List<Item>> = callbackFlow {
        if (lenderId.isEmpty()) { trySend(emptyList()); return@callbackFlow }
        val q = db.child("items").orderByChild("lenderId").equalTo(lenderId)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                trySend(s.children.mapNotNull { it.getValue(Item::class.java) }.filter { it.status == "available" })
            }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeLent(lenderId: String): Flow<List<Item>> = callbackFlow {
        if (lenderId.isEmpty()) { trySend(emptyList()); return@callbackFlow }
        val q = db.child("items").orderByChild("lenderId").equalTo(lenderId)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                trySend(s.children.mapNotNull { it.getValue(Item::class.java) }.filter { it.status != "available" })
            }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeCategories(lenderId: String): Flow<List<Category>> = callbackFlow {
        if (lenderId.isEmpty()) { trySend(emptyList()); return@callbackFlow }
        val ref = db.child("categories/$lenderId")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                trySend(s.children.mapNotNull { it.getValue(Category::class.java) }.sortedBy { it.name })
            }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }

    suspend fun addCategory(lenderId: String, name: String): Result<Category> = runCatching {
        val existing = db.child("categories/$lenderId").orderByChild("name").equalTo(name).get().await()
        if (existing.exists()) throw Exception("Category '$name' already exists.")
        val catId = db.child("categories/$lenderId").push().key ?: UUID.randomUUID().toString()
        val cat = Category(categoryId = catId, name = name, lenderId = lenderId, createdAt = System.currentTimeMillis())
        db.child("categories/$lenderId/$catId").setValue(cat).await()
        cat
    }

    suspend fun editCategory(lenderId: String, catId: String, newName: String): Result<Unit> = runCatching {
        db.child("categories/$lenderId/$catId/name").setValue(newName).await()
    }

    suspend fun deleteCategory(lenderId: String, catId: String, catName: String): Result<Unit> = runCatching {
        val items = db.child("items").orderByChild("lenderId").equalTo(lenderId).get().await()
        val count = items.children.mapNotNull { it.getValue(Item::class.java) }.count { it.category == catName }
        if (count > 0) throw Exception("Category in use")
        db.child("categories/$lenderId/$catId").removeValue().await()
    }

    suspend fun getItemById(itemId: String): Item? = try {
        db.child("items/$itemId").get().await().getValue(Item::class.java)
    } catch (_: Exception) { null }
}
