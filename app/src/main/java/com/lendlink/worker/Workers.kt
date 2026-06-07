package com.lendlink.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.lendlink.data.local.AppDatabase
import com.lendlink.data.repository.BorrowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

const val CHANNEL_ID = "lendlink_alerts"
const val PENALTY_AMOUNT = 5_000L

class DeadlineCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createChannel(applicationContext)
            val db = AppDatabase.get(applicationContext)
            val repo = BorrowRepository(db.borrowDao(), db.lenderCreditHistoryDao(),
                db.borrowerPaymentHistoryDao(), db.lendHistoryDao(), db.borrowHistoryDao())
            val records = repo.getAllActive().filter { it.status == "active" }
            val now = System.currentTimeMillis()
            records.forEach { rec ->
                val remaining = rec.deadline - now
                val hrs = (remaining / 3_600_000L)
                when {
                    remaining > 0 && hrs <= 24 && hrs > 10 ->
                        notify(applicationContext, rec.recordId.hashCode(),
                            "⏰ Return Reminder — 24 Hours Left",
                            "Please return '${rec.itemName}'. Overdue items are charged ₩$PENALTY_AMOUNT every 24 hours.")
                    remaining > 0 && hrs <= 10 ->
                        notify(applicationContext, (rec.recordId + hrs).hashCode(),
                            "⚠️ ${hrs}h Left to Return",
                            "Return '${rec.itemName}' ASAP! Daily penalties apply after the deadline.")
                    remaining < 0 -> {
                        // Periodic reminder for every 24 hours of delay
                        val hrsOverdue = Math.abs(hrs)
                        if (hrsOverdue > 0 && hrsOverdue % 24 == 0L) {
                            val days = hrsOverdue / 24
                            notify(applicationContext, (rec.recordId + "_overdue_daily").hashCode(),
                                "🚨 ITEM OVERDUE - Day $days",
                                "Your borrow of '${rec.itemName}' is $days day(s) late. ₩$PENALTY_AMOUNT is deducted every 24 hours until returned.")
                        }
                    }
                }
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

class PenaltyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createChannel(applicationContext)
            val db = AppDatabase.get(applicationContext)
            val repo = BorrowRepository(db.borrowDao(), db.lenderCreditHistoryDao(),
                db.borrowerPaymentHistoryDao(), db.lendHistoryDao(), db.borrowHistoryDao())
            val now = System.currentTimeMillis()
            repo.getAllActive().filter { it.deadline < now && it.status == "active" }.forEach { rec ->
                // Calculate how many 24-hour periods have passed since the deadline
                // We add 1 to charge the first penalty immediately upon being overdue
                val daysOverdue = 1 + (now - rec.deadline) / (24 * 60 * 60 * 1000)
                val expectedTotalPenalty = daysOverdue * PENALTY_AMOUNT
                val amountToChargeNow = expectedTotalPenalty - rec.penaltyAccrued

                if (amountToChargeNow > 0) {
                    repo.applyPenalty(rec, amountToChargeNow)
                    notify(applicationContext, (rec.recordId + "_pen").hashCode(),
                        "🚨 Item Overdue - Penalty Charged",
                        "₩$amountToChargeNow deducted for '${rec.itemName}'. A penalty of ₩$PENALTY_AMOUNT will be charged every 24 hours until returned.")
                }
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

object WorkerScheduler {
    fun schedule(ctx: Context) {
        try {
            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniquePeriodicWork("deadline_check",
                ExistingPeriodicWorkPolicy.UPDATE, 
                PeriodicWorkRequestBuilder<DeadlineCheckWorker>(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build())
            wm.enqueueUniquePeriodicWork("penalty_check",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<PenaltyWorker>(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun createChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(CHANNEL_ID, "LendLink Alerts", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Deadline reminders and overdue penalty alerts"; enableVibration(true) }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

fun notify(ctx: Context, id: Int, title: String, body: String) {
    try {
        NotificationManagerCompat.from(ctx).notify(id,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
    } catch (_: SecurityException) { /* Permission not granted */ }
}
