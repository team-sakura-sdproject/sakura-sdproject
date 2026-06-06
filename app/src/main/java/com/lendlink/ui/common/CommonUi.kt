package com.lendlink.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lendlink.data.model.AppNotification
import com.lendlink.ui.theme.WalletBg
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Wallet card ───────────────────────────────────────────────
@Composable
fun WalletCard(balance: Long, ownerName: String, label: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = WalletBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.7f))
            Spacer(Modifier.height(4.dp))
            Text(krw(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(ownerName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.7f))
        }
    }
}

// ── Status chip ───────────────────────────────────────────────
@Composable
fun StatusChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Item thumbnail ────────────────────────────────────────────
@Composable
fun ItemImage(url: String, modifier: Modifier = Modifier, size: Int = 64, clip: Boolean = true) {
    val finalModifier = if (modifier === Modifier) Modifier.size(size.dp) else modifier
    
    val imageModifier = if (clip) finalModifier.clip(RoundedCornerShape(8.dp)) else finalModifier
    
    if (url.isNotEmpty()) {
        AsyncImage(
            model = url, contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = imageModifier
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ── Empty state ───────────────────────────────────────────────
@Composable
fun EmptyState(title: String, subtitle: String, icon: ImageVector) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(.25f))
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.6f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(.4f))
        }
    }
}

// ── Detail row ────────────────────────────────────────────────
@Composable
fun DetailRow(label: String, value: String, warn: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(.6f), modifier = Modifier.weight(.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(.6f))
    }
}

// ── Loading button ────────────────────────────────────────────
@Composable
fun LoadingButton(text: String, loading: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !loading) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, fontSize = 16.sp)
    }
}

// ── Notifications Screen ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(notifications: List<AppNotification>, onBack: () -> Unit, onMarkAsRead: () -> Unit) {
    LaunchedEffect(Unit) {
        onMarkAsRead()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        if (notifications.isEmpty()) {
            EmptyState("No notifications", "You'll see alerts about your items here", Icons.Default.Notifications)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
                items(notifications, key = { it.notifId }) { notif ->
                    NotificationItem(notif)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun NotificationItem(notif: AppNotification) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp).background(if (notif.read) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(notif.title, fontWeight = if (notif.read) FontWeight.Normal else FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text(fmtDateTime(notif.createdAt), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────
fun krw(amount: Long): String = "₩${NumberFormat.getNumberInstance(Locale.KOREA).format(amount)}"
fun fmtDate(ts: Long): String = if (ts <= 0L) "—"
    else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
fun fmtDateTime(ts: Long): String = if (ts <= 0L) "—"
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
