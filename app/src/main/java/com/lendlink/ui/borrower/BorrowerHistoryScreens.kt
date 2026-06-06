package com.lendlink.ui.borrower

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lendlink.data.model.BorrowHistory
import com.lendlink.data.model.BorrowerPaymentHistory
import com.lendlink.ui.common.WalletCard
import com.lendlink.ui.common.fmtDate
import com.lendlink.ui.common.fmtDateTime
import com.lendlink.ui.common.krw
import com.lendlink.ui.theme.AvailGreen
import com.lendlink.ui.theme.OverdueRed
import com.lendlink.viewmodel.BorrowerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerPaymentHistoryScreen(vm: BorrowerViewModel, onBack: () -> Unit) {
    val history by vm.paymentHistory.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val totalBorrow = history.filter { it.type == "payment" }.sumOf { it.amount }
    val totalPenalty = history.filter { it.type == "penalty" }.sumOf { it.amount }
    val totalDamage = history.filter { it.type == "damage_charge" }.sumOf { it.amount }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Payment History") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            // Reusing WalletCard for consistency with Lender side
            WalletCard(balance = wallet, ownerName = "Borrower Wallet", label = "Current Balance")

            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Borrow payments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(krw(totalBorrow), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    VerticalDivider(modifier = Modifier.height(32.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Penalties paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(krw(totalPenalty), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OverdueRed)
                    }
                    VerticalDivider(modifier = Modifier.height(32.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Damage charges", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(krw(totalDamage), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OverdueRed)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Receipt, null, modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(.25f))
                        Spacer(Modifier.height(12.dp))
                        Text("No payment history yet", color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    }
                }
            } else {
                val grouped = history.groupBy { try { dayLabel(it.timestamp) } catch(_: Exception) { "Other" } }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    grouped.forEach { (day, entries) ->
                        item {
                            Text(day, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(.5f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        items(entries, key = { it.entryId.ifEmpty { UUID.randomUUID().toString() } }) { entry ->
                            PaymentEntryRow(entry)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentEntryRow(entry: BorrowerPaymentHistory) {
    val isBorrow = entry.type == "payment"
    val iconTint = if (isBorrow) Color(0xFF1565C0) else OverdueRed
    val icon = when (entry.type) {
        "payment" -> Icons.Default.ArrowUpward
        "penalty" -> Icons.Default.Warning
        "damage_charge" -> Icons.Default.ReportProblem
        else -> Icons.Default.Payments
    }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(iconTint.copy(.12f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(fmtDateTime(entry.timestamp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(.5f))
        }
        Spacer(Modifier.width(8.dp))
        Text("-${krw(entry.amount)}", fontWeight = FontWeight.Bold, color = iconTint, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowHistoryScreen(vm: BorrowerViewModel, onBack: () -> Unit) {
    val history by vm.borrowHistory.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Borrow History") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { pad ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(.25f))
                    Spacer(Modifier.height(12.dp))
                    Text("No borrow history yet", color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Text("Items appear here after they are returned", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(.4f))
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp,
                start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history, key = { it.historyId.ifEmpty { UUID.randomUUID().toString() } }) { record ->
                    BorrowHistoryCard(record)
                }
            }
        }
    }
}

@Composable
private fun BorrowHistoryCard(record: BorrowHistory) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (record.itemImageUrl.isNotEmpty()) {
                AsyncImage(model = record.itemImageUrl, contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(record.itemName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(record.itemCategory.ifBlank { "General" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), 
                        tint = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Spacer(Modifier.width(4.dp))
                    Text(record.lenderName, style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurface.copy(.7f))
                }
                
                if (record.lenderPhone.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(14.dp), 
                            tint = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Spacer(Modifier.width(4.dp))
                        Text(record.lenderPhone, style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurface.copy(.7f))
                    }
                }
                
                if (record.lenderLocation.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp).padding(top = 2.dp), 
                            tint = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Spacer(Modifier.width(4.dp))
                        Text(record.lenderLocation, style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurface.copy(.7f), maxLines = 1)
                    }
                }

                Spacer(Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("Borrowed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Text(fmtDate(record.borrowedAt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Returned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Text(fmtDate(record.returnedAt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("Returned", AvailGreen)
                    Text(krw(record.price), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(4.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
            color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun dayLabel(ts: Long): String {
    val cal = Calendar.getInstance(); val today = Calendar.getInstance()
    cal.timeInMillis = ts
    return try {
        when {
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
            else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(ts))
        }
    } catch (e: Exception) {
        "Previous"
    }
}
