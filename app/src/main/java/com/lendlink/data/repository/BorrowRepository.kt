package com.lendlink.data.repository

import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.lendlink.data.local.*
import com.lendlink.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class BorrowRepository(
    private val borrowDao: BorrowDao,
    private val creditDao: LenderCreditHistoryDao,
    private val paymentDao: BorrowerPaymentHistoryDao,
    private val lendHistoryDao: LendHistoryDao,
    private val borrowHistoryDao: BorrowHistoryDao,
    private val damageHistoryDao: DamageHistoryDao? = null
) {
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    // ── BORROW ITEM ───────────────────────────────────────────
    suspend fun borrowItem(item: Item, borrower: User): Result<Unit> = runCatching {
        val borrowerUid = borrower.uid
        val lenderUid = item.lenderId
        val itemId = item.itemId

        // 1. Check wallet balance (pre-check)
        val walletSnap = db.child("wallets/$borrowerUid/balance").get().await()
        val balance = walletSnap.getValue(Long::class.java) ?: 0L
        if (balance < item.price) throw Exception("Insufficient credits (Need ₩${item.price})")

        val recordId = db.child("borrow_records").push().key ?: throw Exception("Record error")
        val now = System.currentTimeMillis()
        val deadline = now + (7L * 24 * 60 * 60 * 1000)

        // 2. Rigid Transaction on the item node to prevent double-borrowing
        val itemRef = db.child("items/$itemId")
        val transactionDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()

        itemRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentStatus = mutableData.child("status").getValue(String::class.java)
                if (currentStatus != "available") {
                    return Transaction.abort() // Someone else borrowed it first
                }

                // Update item status and borrower info atomically
                mutableData.child("status").value = "lent"
                mutableData.child("borrowerId").value = borrowerUid
                mutableData.child("borrowerName").value = borrower.username
                mutableData.child("borrowerPhone").value = borrower.phone
                mutableData.child("borrowerLocation").value = borrower.locationAddress
                mutableData.child("borrowedAt").value = now
                mutableData.child("deadline").value = deadline
                mutableData.child("recordId").value = recordId

                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) transactionDeferred.completeExceptionally(error.toException())
                else transactionDeferred.complete(committed)
            }
        })

        if (!transactionDeferred.await()) {
            throw Exception("Item is no longer available.")
        }

        // 3. Perform atomic multi-path update for wallets and records
        val record = BorrowRecord(
            recordId = recordId, itemId = itemId, itemName = item.name, itemImageUrl = item.imageUrl,
            itemCategory = item.category, // Added category
            lenderId = lenderUid, lenderName = item.lenderName, lenderPhone = item.lenderPhone, lenderLocation = item.lenderLocation,
            borrowerId = borrowerUid, borrowerName = borrower.username, borrowerPhone = borrower.phone, borrowerLocation = borrower.locationAddress,
            price = item.price, borrowedAt = now, deadline = deadline, status = "active"
        )

        val updates = hashMapOf<String, Any?>(
            "borrow_records/$recordId" to record,
            "wallets/$borrowerUid/balance" to ServerValue.increment(-item.price),
            "wallets/$lenderUid/balance" to ServerValue.increment(item.price)
        )
        db.updateChildren(updates).await()

        // 4. Log histories (Log these to separate nodes)
        val creditLog = LenderCreditHistory(
            entryId = db.child("lender_credit_history/$lenderUid").push().key ?: "",
            lenderId = lenderUid, amount = item.price, type = "borrow_payment",
            description = "₩${item.price} received from ${borrower.username} for ${item.name}",
            timestamp = now
        )
        db.child("lender_credit_history/$lenderUid/${creditLog.entryId}").setValue(creditLog)

        val paymentLog = BorrowerPaymentHistory(
            entryId = db.child("borrower_payment_history/$borrowerUid").push().key ?: "",
            borrowerId = borrowerUid, amount = item.price, type = "payment",
            description = "₩${item.price} paid to ${item.lenderName} for borrowing ${item.name}",
            timestamp = now
        )
        db.child("borrower_payment_history/$borrowerUid/${paymentLog.entryId}").setValue(paymentLog)

        // 5. Send notifications
        sendNotification(lenderUid, "Item Borrowed", "${borrower.username} borrowed your ${item.name}", "borrowed")
        sendNotification(borrowerUid, "Item Borrowed", "You have successfully borrowed ${item.name} from ${item.lenderName}", "borrowed")
    }

    // ── RETURN PROCESS ────────────────────────────────────────
    suspend fun requestReturn(record: BorrowRecord): Result<Unit> = runCatching {
        val reqId = db.child("return_requests").push().key ?: ""
        val request = ReturnRequest(
            requestId = reqId,
            recordId = record.recordId,
            itemId = record.itemId,
            lenderId = record.lenderId,
            borrowerId = record.borrowerId,
            requestedAt = System.currentTimeMillis()
        )
        
        db.child("return_requests/$reqId").setValue(request).await()
        db.child("borrow_records/${record.recordId}/status").setValue("return_requested").await()
        
        sendNotification(record.lenderId, "Return Request", "${record.borrowerName} wants to return ${record.itemName}", "return_request")
    }

    suspend fun acceptReturn(record: BorrowRecord, requestId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val updates = hashMapOf<String, Any?>(
            "items/${record.itemId}/status" to "available",
            "items/${record.itemId}/borrowerId" to "",
            "items/${record.itemId}/borrowerName" to "",
            "items/${record.itemId}/borrowerPhone" to "",
            "items/${record.itemId}/borrowerLocation" to "",
            "items/${record.itemId}/borrowedAt" to 0L,
            "items/${record.itemId}/deadline" to 0L,
            "items/${record.itemId}/recordId" to "",
            "items/${record.itemId}/damageReport" to null,
            
            "borrow_records/${record.recordId}" to null,
            "return_requests/$requestId" to null
        )
        db.updateChildren(updates).await()

        // Archive to history
        val historyId = record.recordId
        val lendH = LendHistory(
            historyId = historyId,
            lenderId = record.lenderId,
            itemId = record.itemId,
            itemName = record.itemName,
            itemImageUrl = record.itemImageUrl,
            itemCategory = record.itemCategory,
            borrowerName = record.borrowerName,
            borrowerId = record.borrowerId,
            borrowerPhone = record.borrowerPhone,
            borrowerLocation = record.borrowerLocation,
            price = record.price,
            lentAt = record.borrowedAt,
            returnedAt = now,
            recordId = record.recordId
        )
        val borrowH = BorrowHistory(
            historyId = historyId,
            borrowerId = record.borrowerId,
            itemId = record.itemId,
            itemName = record.itemName,
            itemImageUrl = record.itemImageUrl,
            itemCategory = record.itemCategory,
            lenderName = record.lenderName,
            lenderId = record.lenderId,
            lenderPhone = record.lenderPhone,
            lenderLocation = record.lenderLocation,
            price = record.price,
            borrowedAt = record.borrowedAt,
            returnedAt = now,
            recordId = record.recordId
        )
        db.child("lend_history/${record.lenderId}/$historyId").setValue(lendH)
        db.child("borrow_history/${record.borrowerId}/$historyId").setValue(borrowH)
        
        sendNotification(record.borrowerId, "Return Confirmed", "Lender accepted return of ${record.itemName}", "return_confirmed")
    }

    suspend fun applyPenalty(record: BorrowRecord, amount: Long): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val borrowerUid = record.borrowerId
        val lenderUid = record.lenderId

        val walletSnap = db.child("wallets/$borrowerUid/balance").get().await()
        val balance = walletSnap.getValue(Long::class.java) ?: 0L
        
        val updates = hashMapOf<String, Any?>(
            "wallets/$borrowerUid/balance" to (balance - amount),
            "wallets/$lenderUid/balance" to ServerValue.increment(amount),
            "borrow_records/${record.recordId}/penaltyAccrued" to ServerValue.increment(amount)
        )
        db.updateChildren(updates).await()

        val creditLog = LenderCreditHistory(
            entryId = db.child("lender_credit_history/$lenderUid").push().key ?: "",
            lenderId = lenderUid, amount = amount, type = "penalty",
            description = "₩$amount penalty received from ${record.borrowerName} for ${record.itemName} (Overdue)",
            timestamp = now
        )
        db.child("lender_credit_history/$lenderUid/${creditLog.entryId}").setValue(creditLog)

        val paymentLog = BorrowerPaymentHistory(
            entryId = db.child("borrower_payment_history/$borrowerUid").push().key ?: "",
            borrowerId = borrowerUid, amount = amount, type = "penalty",
            description = "₩$amount penalty paid for late return of ${record.itemName} (Overdue)",
            timestamp = now
        )
        db.child("borrower_payment_history/$borrowerUid/${paymentLog.entryId}").setValue(paymentLog)

        // Send in-app notification to borrower
        sendNotification(
            borrowerUid, 
            "🚨 OVERDUE WARNING", 
            "₩$amount deducted for '${record.itemName}'. Note: ₩1,000 will be deducted every 24 hours until the lender confirms the return.",
            "penalty"
        )
    }

    suspend fun sendReminder(record: BorrowRecord): Result<Unit> = runCatching {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateStr = if (record.deadline > 0L) sdf.format(Date(record.deadline)) else "—"
        sendNotification(record.borrowerId, "Return Reminder", "Please return ${record.itemName} soon. Deadline: $dateStr", "reminder")
    }

    private suspend fun sendNotification(uid: String, title: String, body: String, type: String) {
        val id = db.child("notifications/$uid").push().key ?: ""
        val n = AppNotification(
            notifId = id,
            recipientId = uid,
            title = title,
            body = body,
            type = type,
            read = false,
            createdAt = System.currentTimeMillis()
        )
        db.child("notifications/$uid/$id").setValue(n)
    }

    suspend fun markNotificationsRead(uid: String) = runCatching {
        val ref = db.child("notifications/$uid")
        val snap = ref.get().await()
        val updates = hashMapOf<String, Any?>()
        snap.children.forEach { notifSnap ->
            val notif = notifSnap.getValue(AppNotification::class.java)
            if (notif != null && !notif.read) {
                updates["${notifSnap.key}/read"] = true
            }
        }
        if (updates.isNotEmpty()) ref.updateChildren(updates).await()
    }

    // ── DAMAGE REPORTING (Phase 2) ──────────────────────────
    suspend fun submitDamageReport(record: BorrowRecord, condition: String, desc: String, amount: Long, imageBytes: ByteArray?): Result<Unit> = runCatching {
        var imageUrl = ""
        if (imageBytes != null) {
            val ref = storage.child("damage_images/${record.recordId}.jpg")
            val metadata = com.google.firebase.storage.storageMetadata {
                contentType = "image/jpeg"
            }
            ref.putBytes(imageBytes, metadata).await()
            imageUrl = ref.downloadUrl.await().toString()
        }
        
        val report = DamageReport(condition, desc, amount, imageUrl, System.currentTimeMillis(), "pending")
        val updates = hashMapOf<String, Any?>(
            "borrow_records/${record.recordId}/status" to "damaged",
            "borrow_records/${record.recordId}/damageReport" to report,
            "items/${record.itemId}/status" to "damaged",
            "items/${record.itemId}/damageReport" to report
        )
        
        // Also find and remove any pending return requests for this record/item
        val reqSnap = db.child("return_requests").orderByChild("lenderId").equalTo(record.lenderId).get().await()
        reqSnap.children.forEach { snap ->
            val req = snap.getValue(ReturnRequest::class.java)
            if (req?.recordId == record.recordId || req?.itemId == record.itemId) {
                updates["return_requests/${snap.key}"] = null
            }
        }

        db.updateChildren(updates).await()
        
        sendNotification(record.borrowerId, "Damage Reported", "Lender reported damage for '${record.itemName}'. View details to respond.", "damage_report")
        sendNotification(record.lenderId, "Report Submitted", "Damage report for '${record.itemName}' sent to borrower.", "damage_report")
    }

    suspend fun cancelDamageReport(record: BorrowRecord): Result<Unit> = runCatching {
        // As per checklist: Cancel report and accept return normally
        acceptReturn(record, "cancelled_report") // Pass dummy requestId or handle in acceptReturn
    }

    suspend fun payDamageCharge(record: BorrowRecord, charge: Long, borrower: User): Result<Unit> = runCatching {
        val borrowerUid = borrower.uid
        val lenderUid = record.lenderId
        
        // 1. Pre-check balance
        val walletSnap = db.child("wallets/$borrowerUid/balance").get().await()
        val balance = walletSnap.getValue(Long::class.java) ?: 0L
        if (balance < charge) {
            // Step 9: Notify lender of insufficient funds and request negotiation
            sendNotification(lenderUid, "Payment Failed", "${borrower.username} has insufficient credits to pay damage charge. Negotiation requested.", "negotiation")
            throw Exception("Insufficient credits (Need ₩$charge)")
        }

        val now = System.currentTimeMillis()
        // 2. Multi-path update for payment and finalizing return
        val updates = hashMapOf<String, Any?>(
            "wallets/$borrowerUid/balance" to ServerValue.increment(-charge),
            "wallets/$lenderUid/balance" to ServerValue.increment(charge),
            "items/${record.itemId}" to null,
            "borrow_records/${record.recordId}" to null
        )

        // Clear any lingering return requests for this record
        val reqSnap = db.child("return_requests").orderByChild("lenderId").equalTo(record.lenderId).get().await()
        reqSnap.children.forEach { snap ->
            val req = snap.getValue(ReturnRequest::class.java)
            if (req?.recordId == record.recordId || req?.itemId == record.itemId) {
                updates["return_requests/${snap.key}"] = null
            }
        }

        db.updateChildren(updates).await()

        // 3. Log to histories (Credit/Payment/Damage History)
        val creditLog = LenderCreditHistory(
            entryId = db.child("lender_credit_history/$lenderUid").push().key ?: "",
            lenderId = lenderUid, amount = charge, type = "damage_charge",
            description = "₩$charge paid by ${borrower.username} for damaging the '${record.itemName}'",
            timestamp = now
        )
        db.child("lender_credit_history/$lenderUid/${creditLog.entryId}").setValue(creditLog)

        val paymentLog = BorrowerPaymentHistory(
            entryId = db.child("borrower_payment_history/$borrowerUid").push().key ?: "",
            borrowerId = borrowerUid, amount = charge, type = "damage_charge",
            description = "₩$charge paid to ${record.lenderName} as damage charge for damaging the '${record.itemName}'",
            timestamp = now
        )
        db.child("borrower_payment_history/$borrowerUid/${paymentLog.entryId}").setValue(paymentLog)

        val damageH = DamageHistory(
            historyId = record.recordId, 
            itemId = record.itemId, 
            itemName = record.itemName,
            itemImageUrl = record.itemImageUrl,
            itemCategory = record.itemCategory,
            lenderId = lenderUid, 
            lenderName = record.lenderName,
            lenderPhone = record.lenderPhone,
            lenderLocation = record.lenderLocation,
            borrowerId = borrowerUid, 
            borrowerName = borrower.username,
            borrowerPhone = borrower.phone,
            borrowerLocation = borrower.locationAddress,
            damageImageUrl = record.damageReport?.damageImageUrl ?: "",
            condition = record.damageReport?.condition ?: "", 
            description = record.damageReport?.description ?: "",
            chargeAmount = charge, 
            borrowedAt = record.borrowedAt,
            paymentStatus = "Paid", 
            timestamp = now
        )
        db.child("damage_history/${lenderUid}/${record.recordId}").setValue(damageH)
        db.child("damage_history/${borrowerUid}/${record.recordId}").setValue(damageH)

        sendNotification(lenderUid, "Damage Charge Paid", "${borrower.username} paid ₩$charge for '${record.itemName}'. Item has been removed from your listings.", "payment")
        sendNotification(borrowerUid, "Damage Payment Success", "₩$charge paid to ${record.lenderName}. Return completed.", "payment")
    }

    suspend fun requestNegotiation(record: BorrowRecord, borrowerName: String) = runCatching {
        val updates = hashMapOf<String, Any?>(
            "borrow_records/${record.recordId}/status" to "negotiating",
            "items/${record.itemId}/status" to "negotiating"
        )
        db.updateChildren(updates).await()
        sendNotification(record.lenderId, "Negotiation Requested", "$borrowerName wants to negotiate the damage report for '${record.itemName}'.", "negotiation")
    }

    suspend fun completeNegotiation(record: BorrowRecord): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val updates = hashMapOf<String, Any?>(
            "items/${record.itemId}" to null,
            "borrow_records/${record.recordId}" to null
        )

        // Clear any lingering return requests for this record
        val reqSnap = db.child("return_requests").orderByChild("lenderId").equalTo(record.lenderId).get().await()
        reqSnap.children.forEach { snap ->
            val req = snap.getValue(ReturnRequest::class.java)
            if (req?.recordId == record.recordId || req?.itemId == record.itemId) {
                updates["return_requests/${snap.key}"] = null
            }
        }

        db.updateChildren(updates).await()

        val damageH = DamageHistory(
            historyId = record.recordId, 
            itemId = record.itemId, 
            itemName = record.itemName,
            itemImageUrl = record.itemImageUrl,
            itemCategory = record.itemCategory,
            lenderId = record.lenderId, 
            lenderName = record.lenderName,
            lenderPhone = record.lenderPhone,
            lenderLocation = record.lenderLocation,
            borrowerId = record.borrowerId, 
            borrowerName = record.borrowerName,
            borrowerPhone = record.borrowerPhone,
            borrowerLocation = record.borrowerLocation,
            damageImageUrl = record.damageReport?.damageImageUrl ?: "",
            condition = record.damageReport?.condition ?: "", 
            description = record.damageReport?.description ?: "",
            chargeAmount = record.damageReport?.chargeAmount ?: 0L, 
            borrowedAt = record.borrowedAt,
            paymentStatus = "Negotiated", 
            timestamp = now
        )
        db.child("damage_history/${record.lenderId}/${record.recordId}").setValue(damageH)
        db.child("damage_history/${record.borrowerId}/${record.recordId}").setValue(damageH)

        sendNotification(record.borrowerId, "Negotiation Completed", "Lender marked negotiation for '${record.itemName}' as completed. Return finalized.", "negotiation")
        sendNotification(record.lenderId, "Negotiation Finalized", "Return for '${record.itemName}' completed after negotiation. Item removed from listings.", "negotiation")
    }

    fun observeDamageHistory(uid: String): Flow<List<DamageHistory>> = callbackFlow {
        val q = db.child("damage_history/$uid").orderByChild("timestamp")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(DamageHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) { if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException()) }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    suspend fun getAllActive(): List<BorrowRecord> {
        val s = db.child("borrow_records").get().await()
        return s.children.mapNotNull { it.getValue(BorrowRecord::class.java) }
    }

    // ── Real-time Observers ──────────────────────────────────
    fun observeWallet(uid: String): Flow<Long> = callbackFlow {
        val ref = db.child("wallets/$uid/balance")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.getValue(Long::class.java) ?: 0L) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }

    fun observeBorrowerActive(uid: String): Flow<List<BorrowRecord>> = callbackFlow {
        val q = db.child("borrow_records").orderByChild("borrowerId").equalTo(uid)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(BorrowRecord::class.java) }) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observePendingReturns(lenderId: String): Flow<List<ReturnRequest>> = callbackFlow {
        val q = db.child("return_requests").orderByChild("lenderId").equalTo(lenderId)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(ReturnRequest::class.java) }) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val q = db.child("notifications/$uid").orderByChild("createdAt")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(AppNotification::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeLenderCreditHistory(uid: String): Flow<List<LenderCreditHistory>> = callbackFlow {
        val q = db.child("lender_credit_history/$uid").orderByChild("timestamp")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(LenderCreditHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeBorrowerPaymentHistory(uid: String): Flow<List<BorrowerPaymentHistory>> = callbackFlow {
        val q = db.child("borrower_payment_history/$uid").orderByChild("timestamp")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(BorrowerPaymentHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeLendHistory(uid: String): Flow<List<LendHistory>> = callbackFlow {
        val q = db.child("lend_history/$uid").orderByChild("returnedAt")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(LendHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeBorrowHistory(uid: String): Flow<List<BorrowHistory>> = callbackFlow {
        val q = db.child("borrow_history/$uid").orderByChild("returnedAt")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(BorrowHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    // ── NEW: Methods for searching and filtering all available items (Borrower Side) ──
    fun observeAllAvailableItems(): Flow<List<Item>> = callbackFlow {
        val query = db.child("items").orderByChild("status").equalTo("available")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                try {
                    val items = s.children.mapNotNull { it.getValue(Item::class.java) }
                    trySend(items)
                } catch (e: Exception) {
                    // Log or handle deserialization error
                }
            }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) {
                    // Try to send an empty list instead of closing, to keep UI alive
                    trySend(emptyList())
                } else {
                    close(e.toException())
                }
            }
        }
        query.addValueEventListener(l)
        awaitClose { query.removeEventListener(l) }
    }

    fun observeSystemCategories(): Flow<List<String>> = callbackFlow {
        val ref = db.child("items")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val cats = s.children.mapNotNull { it.getValue(Item::class.java) }
                    .filter { it.status == "available" }
                    .map { it.category }
                    .distinct()
                    .sorted()
                trySend(cats)
            }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }
}
