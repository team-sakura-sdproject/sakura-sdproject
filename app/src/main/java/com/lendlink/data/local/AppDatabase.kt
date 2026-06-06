package com.lendlink.data.local

import androidx.room.*
import com.lendlink.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE lenderId = :lenderId AND status = 'available'")
    fun getLenderAvailable(lenderId: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE lenderId = :lenderId AND status = 'lent'")
    fun getLenderLent(lenderId: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE lenderId = :lenderId AND category = :cat AND status = 'available'")
    fun getByCategory(lenderId: String, cat: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE itemId = :id")
    suspend fun getById(id: String): Item?

    @Upsert suspend fun upsert(item: Item)

    @Query("DELETE FROM items WHERE itemId = :id")
    suspend fun delete(id: String)
}

@Dao
interface BorrowDao {
    @Query("SELECT * FROM borrow_records WHERE borrowerId = :uid AND (status = 'active' OR status = 'return_requested') ORDER BY borrowedAt DESC")
    fun getActiveBorrower(uid: String): Flow<List<BorrowRecord>>

    @Query("SELECT * FROM borrow_records WHERE status = 'active' OR status = 'return_requested'")
    suspend fun getAllActive(): List<BorrowRecord>

    @Query("SELECT * FROM borrow_records WHERE status = 'active' AND deadline < :now")
    suspend fun getOverdue(now: Long): List<BorrowRecord>

    @Upsert suspend fun upsert(record: BorrowRecord)

    @Query("UPDATE borrow_records SET status = :status WHERE recordId = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE borrow_records SET penaltyAccrued = penaltyAccrued + :amt WHERE recordId = :id")
    suspend fun addPenalty(id: String, amt: Long)
}

@Dao
interface LenderCreditHistoryDao {
    @Query("SELECT * FROM lender_credit_history WHERE lenderId = :uid ORDER BY timestamp DESC")
    fun getByLender(uid: String): Flow<List<LenderCreditHistory>>
    @Upsert suspend fun upsert(e: LenderCreditHistory)
}

@Dao
interface BorrowerPaymentHistoryDao {
    @Query("SELECT * FROM borrower_payment_history WHERE borrowerId = :uid ORDER BY timestamp DESC")
    fun getByBorrower(uid: String): Flow<List<BorrowerPaymentHistory>>
    @Upsert suspend fun upsert(e: BorrowerPaymentHistory)
}

@Dao
interface LendHistoryDao {
    @Query("SELECT * FROM lend_history WHERE lenderId = :uid ORDER BY returnedAt DESC")
    fun getByLender(uid: String): Flow<List<LendHistory>>
    @Upsert suspend fun upsert(e: LendHistory)
}

@Dao
interface BorrowHistoryDao {
    @Query("SELECT * FROM borrow_history WHERE borrowerId = :uid ORDER BY returnedAt DESC")
    fun getByBorrower(uid: String): Flow<List<BorrowHistory>>
    @Upsert suspend fun upsert(e: BorrowHistory)
}

@Dao
interface DamageHistoryDao {
    @Query("SELECT * FROM damage_history WHERE lenderId = :uid OR borrowerId = :uid ORDER BY timestamp DESC")
    fun getByUser(uid: String): Flow<List<DamageHistory>>
    @Upsert suspend fun upsert(e: DamageHistory)
}

@Database(
    entities = [
        Item::class, BorrowRecord::class,
        LenderCreditHistory::class, BorrowerPaymentHistory::class,
        LendHistory::class, BorrowHistory::class, DamageHistory::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun borrowDao(): BorrowDao
    abstract fun lenderCreditHistoryDao(): LenderCreditHistoryDao
    abstract fun borrowerPaymentHistoryDao(): BorrowerPaymentHistoryDao
    abstract fun lendHistoryDao(): LendHistoryDao
    abstract fun borrowHistoryDao(): BorrowHistoryDao
    abstract fun damageHistoryDao(): DamageHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: android.content.Context): AppDatabase = INSTANCE ?: synchronized(this) {
            androidx.room.Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "lendlink_db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
