package com.lendlink.data.model

import androidx.room.*

// ── User ──────────────────────────────────────────────────────────
data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "borrower", // "lender" or "borrower"
    val locationAddress: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val profileImageUrl: String = ""
)

// ── Item ──────────────────────────────────────────────────────────
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val itemId: String = "",
    val name: String = "",
    val category: String = "General",
    val price: Long = 0L,
    val description: String = "",
    val imageUrl: String = "",
    val lenderId: String = "",
    val lenderName: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val lenderLatitude: Double = 0.0,
    val lenderLongitude: Double = 0.0,
    val status: String = "available", // "available", "lent", "damaged", "negotiating"
    val createdAt: Long = System.currentTimeMillis(),
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val borrowedAt: Long = 0L,
    val deadline: Long = 0L,
    val recordId: String = "",
    val damageReport: DamageReport? = null
)

// ── Borrow Record ────────────────────────────────────────────────
@Entity(tableName = "borrow_records")
data class BorrowRecord(
    @PrimaryKey val recordId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val lenderId: String = "",
    val lenderName: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val price: Long = 0L,
    val borrowedAt: Long = 0L,
    val deadline: Long = 0L,
    val penaltyAccrued: Long = 0L,
    val status: String = "active", // "active", "return_requested", "damaged", "negotiating"
    val damageReport: DamageReport? = null
)

// ── Damage Report (Nested in Record/Item) ───────────────────────
data class DamageReport(
    val condition: String = "", // "Minor", "Severe"
    val description: String = "",
    val chargeAmount: Long = 0L,
    val damageImageUrl: String = "",
    val reportedAt: Long = 0L,
    val status: String = "pending" // "pending", "resolved"
)

// ── Wallets (Firebase Only, but helper classes) ──────────────────
data class Wallet(
    val balance: Long = 0L
)

// ── Lender Credit History ─────────────────────────────────────────
@Entity(tableName = "lender_credit_history")
data class LenderCreditHistory(
    @PrimaryKey val entryId: String = "",
    val lenderId: String = "",
    val amount: Long = 0L,
    val type: String = "", // "borrow_payment", "penalty", "damage_charge"
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ── Borrower Payment History ──────────────────────────────────────
@Entity(tableName = "borrower_payment_history")
data class BorrowerPaymentHistory(
    @PrimaryKey val entryId: String = "",
    val borrowerId: String = "",
    val amount: Long = 0L,
    val type: String = "", // "payment", "penalty", "damage_charge"
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ── Lend History ──────────────────────────────────────────────────
@Entity(tableName = "lend_history")
data class LendHistory(
    @PrimaryKey val historyId: String = "",
    val lenderId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val price: Long = 0L,
    val lentAt: Long = 0L,
    val returnedAt: Long = 0L,
    val recordId: String = ""
)

// ── Borrow History ────────────────────────────────────────────────
@Entity(tableName = "borrow_history")
data class BorrowHistory(
    @PrimaryKey val historyId: String = "",
    val borrowerId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val lenderId: String = "",
    val lenderName: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val price: Long = 0L,
    val borrowedAt: Long = 0L,
    val returnedAt: Long = 0L,
    val recordId: String = ""
)

// ── Damage History ────────────────────────────────────────────
@Entity(tableName = "damage_history")
data class DamageHistory(
    @PrimaryKey val historyId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val lenderId: String = "",
    val lenderName: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val damageImageUrl: String = "",
    val condition: String = "",
    val description: String = "",
    val chargeAmount: Long = 0L,
    val borrowedAt: Long = 0L,
    val paymentStatus: String = "", // "Paid" or "Negotiated"
    val timestamp: Long = System.currentTimeMillis()
)

// ── Category Model ──────────────────────────────────────────────
data class Category(
    val categoryId: String = "",
    val name: String = "",
    val lenderId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ── Notifications ────────────────────────────────────────────────
data class AppNotification(
    val notifId: String = "",
    val recipientId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "", // "borrowed", "return_request", "return_confirmed", "damage_report", "penalty", "reminder", "payment", "negotiation"
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ── Return Request ──────────────────────────────────────────────
data class ReturnRequest(
    val requestId: String = "",
    val recordId: String = "",
    val itemId: String = "",
    val lenderId: String = "",
    val borrowerId: String = "",
    val requestedAt: Long = 0L
)

// Extension function to quickly create a record from an item
fun Item.toRecord(id: String = ""): BorrowRecord {
    return BorrowRecord(
        recordId = id.ifBlank { this.recordId },
        itemId = this.itemId,
        itemName = this.name,
        itemImageUrl = this.imageUrl,
        itemCategory = this.category,
        lenderId = this.lenderId,
        lenderName = this.lenderName,
        lenderPhone = this.lenderPhone,
        lenderLocation = this.lenderLocation,
        borrowerId = this.borrowerId,
        borrowerName = this.borrowerName,
        borrowerPhone = this.borrowerPhone,
        borrowerLocation = this.borrowerLocation,
        price = this.price,
        borrowedAt = this.borrowedAt,
        deadline = this.deadline,
        status = this.status,
        damageReport = this.damageReport
    )
}
