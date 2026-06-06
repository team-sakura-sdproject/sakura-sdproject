package com.lendlink.ui.borrower

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.lendlink.data.model.*
import com.lendlink.ui.common.*
import com.lendlink.ui.lender.*
import com.lendlink.ui.theme.*
import com.lendlink.viewmodel.*
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

// ══════════════════════════════════════════════════════════════
//  BORROWER DASHBOARD
// ══════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerDashboard(
    vm: BorrowerViewModel,
    borrowerName: String,
    onItemClick: (BorrowRecord) -> Unit,
    onScan: () -> Unit,
    onNotifications: () -> Unit,
    onPaymentHistory: () -> Unit,
    onBorrowHistory: () -> Unit,
    onDamageHistory: () -> Unit,
    onProfile: () -> Unit,
    onBrowseItem: (Item) -> Unit,
    onLogout: () -> Unit,
) {
    val wallet by vm.wallet.collectAsState()
    val searchQueryAvailable by vm.searchQueryAvailable.collectAsState()
    val searchQueryActive by vm.searchQueryActive.collectAsState()
    val notifications by vm.notifications.collectAsState()
    val unreadCount = notifications.count { !it.read }
    val ui by vm.ui.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val filteredAvailable by vm.filteredAllItems.collectAsState()
    val filteredActive by vm.filteredActive.collectAsState()
    val systemCats by vm.systemCategories.collectAsState()
    val activeCats by vm.activeCategories.collectAsState()
    val selectedCatAvailable by vm.selectedCatAvailable.collectAsState()
    val selectedCatActive by vm.selectedCatActive.collectAsState()

    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(value = false) }
    var selectedTab by remember { mutableIntStateOf(value = 0) }

    LaunchedEffect(ui) {
        val s = ui
        if (s is UiState.Success) { 
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi()
            // If we are scanning from dashboard and succeed, maybe switch tab to show active borrows
            if (s.message.contains("successfully")) selectedTab = 0
        }
        else if (s is UiState.Error) { 
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi() 
        }
    }

    if (scanned != null) {
        BorrowConfirmDialog(
            item = scanned!!,
            onDismiss = {
                vm.clearScanned()
                vm.resetUi()
            },
        ) {
            vm.confirmBorrow(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(borrowerName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Borrower", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                actions = {
                    IconButton(onClick = onNotifications) {
                        BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }) {
                            Icon(Icons.Default.Notifications, null)
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("My Profile") }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { menu = false; onProfile() })
                            DropdownMenuItem(text = { Text("Payment History") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, onClick = { menu = false; onPaymentHistory() })
                            DropdownMenuItem(text = { Text("Borrow History") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { menu = false; onBorrowHistory() })
                            DropdownMenuItem(text = { Text("Damage History") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, onClick = { menu = false; onDamageHistory() })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Logout", color = MaterialTheme.colorScheme.error) }, 
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) }, 
                                onClick = { 
                                    menu = false
                                    onLogout() 
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.QrCodeScanner, null) },
                text = { Text("Scan to Borrow") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            // Wallet Card 
            WalletCard(wallet, borrowerName, "Wallet Balance")

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = MaterialTheme.colorScheme.primary)
                },
                divider = { HorizontalDivider() }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Active Borrows", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Available to Borrow", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search 
            OutlinedTextField(
                value = if (selectedTab == 0) searchQueryActive else searchQueryAvailable,
                onValueChange = { if (selectedTab == 0) vm.updateSearchQueryActive(it) else vm.updateSearchQueryAvailable(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(if (selectedTab == 0) "Search active borrows..." else "Search items...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )

            // Categories
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val selected = if (selectedTab == 0) selectedCatActive == "All" else selectedCatAvailable == "All"
                    FilterChip(selected = selected, onClick = { if (selectedTab == 0) vm.selectCatActive("All") else vm.selectCatAvailable("All") }, label = { Text("All") })
                }
                val categoriesToShow = if (selectedTab == 0) activeCats else systemCats
                items(categoriesToShow) { cat ->
                    val selected = if (selectedTab == 0) selectedCatActive == cat else selectedCatAvailable == cat
                    FilterChip(selected = selected, onClick = { if (selectedTab == 0) vm.selectCatActive(cat) else vm.selectCatAvailable(cat) }, label = { Text(cat) })
                }
            }

            if (selectedTab == 0) {
                // Active Borrows Tab
                if (filteredActive.isEmpty()) {
                    EmptyState("No active borrows", "Try changing your search or category", Icons.Default.SearchOff)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filteredActive, key = { it.recordId }) { record -> ActiveBorrowCard(record, onItemClick) }
                    }
                }
            } else {
                // Available to Borrow Tab
                if (filteredAvailable.isEmpty()) {
                    EmptyState("No items found", "Try changing your search or category", Icons.Default.SearchOff)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filteredAvailable, key = { it.itemId }) { item -> ItemCard(item, onBrowseItem) }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveBorrowCard(record: BorrowRecord, onClick: (BorrowRecord) -> Unit) {
    val overdue = (record.deadline > 0L) && (System.currentTimeMillis() > record.deadline)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(record) },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemImage(record.itemImageUrl, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.itemName, fontWeight = FontWeight.ExtraBold)
                Text("Lender: ${record.lenderName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                if (overdue) {
                    Text("Due: ${fmtDate(record.deadline)}", style = MaterialTheme.typography.labelSmall, color = OverdueRed, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(if (overdue) "Overdue" else "Active", if (overdue) OverdueRed else LentOrange)
                if (record.status == "damaged" || record.status == "negotiating") {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("Damaged", DamagePurple)
                } else if (record.status == "return_requested") {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("Returning", PendingBlue)
                }
            }
        }
    }
}

@Composable
fun ItemCard(item: Item, onClick: (Item) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick(item) }, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemImage(item.imageUrl, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(item.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(krw(item.price), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("Lender: ${item.lenderName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerAvailableItemDetailScreen(item: Item, vm: BorrowerViewModel, onBack: () -> Unit, onScan: () -> Unit) {
    val scanned by vm.scanned.collectAsState()
    val ui by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ui) {
        val s = ui
        if (s is UiState.Success) {
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi()
            // If borrowed successfully, go back to dashboard to see active borrows
            if (s.message.contains("successfully")) {
                kotlinx.coroutines.delay(300) // Brief delay to ensure database syncs
                onBack()
            }
        } else if (s is UiState.Error) {
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi()
        }
    }

    if (scanned != null) {
        BorrowConfirmDialog(
            item = scanned!!,
            onDismiss = {
                vm.clearScanned()
                vm.resetUi()
            },
        ) {
            vm.confirmBorrow(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Item Details") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ) {
            ItemImage(item.imageUrl, modifier = Modifier.fillMaxWidth().height(300.dp), clip = false)
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip("Available", AvailGreen)
                }
                Text(
                    krw(item.price),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                
                DetailRow("Category", item.category.ifBlank { "General" })
                DetailRow("Lender Name", item.lenderName)
                DetailRow("Lender Phone", item.lenderPhone.ifEmpty { "Not provided" })
                DetailRow("Lender Location", item.lenderLocation)
                DetailRow("Description", item.description.ifEmpty { "No description provided." })

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "To borrow this item, please meet the lender and scan their item QR code.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Button(
                            onClick = onScan,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan QR Code Now", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerItemDetailScreen(record: BorrowRecord, vm: BorrowerViewModel, onBack: () -> Unit, onViewDamage: (String) -> Unit) {
    var showReturnDialog by remember { mutableStateOf(value = false) }
    val uiState by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        val s = uiState
        if (s is UiState.Success) {
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi()
        }
    }

    fun hideReturnDialog() { showReturnDialog = false }

    if (showReturnDialog) {
        AlertDialog(
            onDismissRequest = { hideReturnDialog() },
            title = { Text("Request Return?") },
            text = { Text("Do you want to request a return for ${record.itemName}? The lender must accept this request to finalize the return.") },
            confirmButton = { Button({ hideReturnDialog(); vm.requestReturn(record) }) { Text("Request") } },
            dismissButton = { TextButton({ hideReturnDialog() }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Text("Borrowed Item") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            ItemImage(record.itemImageUrl, modifier = Modifier.fillMaxWidth().height(300.dp), clip = false)
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val overdue = record.deadline > 0L && (System.currentTimeMillis() > record.deadline)
                    Text(record.itemName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusChip(if (overdue) "Overdue" else "Active", if (overdue) OverdueRed else LentOrange)
                    if (record.status == "damaged" || record.status == "negotiating") {
                        Spacer(Modifier.width(8.dp))
                        StatusChip("Damaged", DamagePurple)
                    } else if (record.status == "return_requested") {
                        Spacer(Modifier.width(8.dp))
                        StatusChip("Returning", PendingBlue)
                    }
                }
                Text(krw(record.price), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                
                HorizontalDivider()
                DetailRow("Category", record.itemCategory.ifBlank { "General" })
                DetailRow("Lender Name", record.lenderName)
                DetailRow("Lender Phone", record.lenderPhone.ifEmpty { "Not provided" })
                DetailRow("Lender Location", record.lenderLocation)
                DetailRow("Borrowed At", fmtDate(record.borrowedAt))
                DetailRow("Deadline", fmtDate(record.deadline))
                
                Spacer(Modifier.height(8.dp))
                
                if (record.status == "damaged" || record.status == "negotiating") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OverdueRed.copy(0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OverdueRed.copy(0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = OverdueRed, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Lender has reported damage for this item. Please review the report.", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    color = OverdueRed, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { onViewDamage(record.recordId) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OverdueRed)
                            ) {
                                Text("View Damage Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    val isRequested = record.status == "return_requested"
                    
                    Button(
                        onClick = { if (!isRequested) showReturnDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isRequested,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFE0E0E0),
                            disabledContentColor = Color(0xFF757575)
                        )
                    ) {
                        Icon(
                            if (isRequested) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.AssignmentReturn, 
                            null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isRequested) "Return Request Sent" else "Request Return to Lender", 
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (isRequested) {
                        Text(
                            "Please wait for the lender to accept your return request.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun BorrowConfirmDialog(item: Item, onDismiss: () -> Unit, onConfirm: (Item) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Confirm Borrow",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Item Photo Placeholder/Image
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE3F2FD),
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                ) {
                    if (item.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("ITEM PHOTO", color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Details Table
                DetailRow("Item", item.name, labelColor = Color.Black, valueColor = Color.Black)
                HorizontalDivider(color = Color.LightGray.copy(0.5f), thickness = 1.dp)
                DetailRow("Lender", item.lenderName, labelColor = Color.Black, valueColor = Color.Black)
                HorizontalDivider(color = Color.LightGray.copy(0.5f), thickness = 1.dp)
                DetailRow("Price", krw(item.price), labelColor = Color.Black, valueColor = Color.Black)
                HorizontalDivider(color = Color.LightGray.copy(0.5f), thickness = 1.dp)
                DetailRow("Return by", "7 days from today", labelColor = Color.Black, valueColor = Color.Black)

                Spacer(Modifier.height(16.dp))

                // Info Box
                Surface(
                    color = Color(0xFFF5F5F5),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(4.dp).height(24.dp).background(Color.Black, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${krw(item.price)} will be deducted from your wallet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    }
                    Button(
                        onClick = { onConfirm(item) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("Borrow", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String, 
    value: String, 
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, 
            style = MaterialTheme.typography.bodyLarge, 
            color = labelColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(user: User, authVm: AuthViewModel, onBack: () -> Unit) {
    var showCamera by remember { mutableStateOf(false) }
    val state by authVm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthState.Error -> {
                snackbarHostState.showSnackbar(s.msg)
                authVm.reset()
            }
            else -> {}
        }
    }

    if (showCamera) {
        CameraDialog(
            onCaptured = { uri ->
                authVm.updateProfileImage(user.uid, uri)
                showCamera = false
            }
        ) { showCamera = false }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("My Profile") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                if (user.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.LightGray),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                if (state is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(120.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }

                IconButton(onClick = { showCamera = true }, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) { Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp), tint = Color.White) }
            }
            Spacer(Modifier.height(16.dp))
            Text(user.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(user.role.replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(32.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfileItem(Icons.Default.Email, "Email", user.email)
                    ProfileItem(Icons.Default.Phone, "Phone", user.phone)
                    ProfileItem(Icons.Default.LocationOn, "Location", user.locationAddress)
                }
            }
        }
    }
}

@Composable
fun ProfileItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ScanQRScreen(vm: BorrowerViewModel, expectedId: String? = null, onScanned: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val ui by vm.ui.collectAsState()
    var isScanning by remember { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Handle global VM errors (like "Item not found")
    LaunchedEffect(ui) {
        if (ui is UiState.Error) {
            scope.launch { 
                snack.showSnackbar((ui as UiState.Error).message, duration = SnackbarDuration.Short) 
            }
            vm.resetUi()
        }
    }

    // Handle local validation errors with custom short duration
    LaunchedEffect(localError) {
        localError?.let { msg ->
            val job = scope.launch {
                snack.showSnackbar(msg, duration = SnackbarDuration.Indefinite)
            }
            kotlinx.coroutines.delay(1500) // Display for 1.5 seconds
            job.cancel()
            localError = null
        }
    }

    // Ensure VM state is cleared when leaving the scanner
    DisposableEffect(Unit) {
        onDispose { vm.resetUi() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also {
                                it.setAnalyzer(cameraExecutor) { imageProxy ->
                                    if (isScanning) {
                                        val buffer = imageProxy.planes[0].buffer
                                        val data = ByteArray(buffer.remaining())
                                        buffer[data]
                                        val source = PlanarYUVLuminanceSource(
                                            data,
                                            imageProxy.width,
                                            imageProxy.height,
                                            0,
                                            0,
                                            imageProxy.width,
                                            imageProxy.height,
                                            false
                                        )
                                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                                        try {
                                            val result = MultiFormatReader().apply {
                                                setHints(
                                                    mapOf(
                                                        DecodeHintType.POSSIBLE_FORMATS to listOf(
                                                            BarcodeFormat.QR_CODE
                                                        )
                                                    )
                                                )
                                            }.decode(bitmap)
                                            val code = result.text
                                            if (!code.isNullOrEmpty()) {
                                                if (expectedId != null && code != expectedId) {
                                                    // Wrong item scanned from details page
                                                    localError = "The QR you are trying to scan is of different item than the item you are trying to borrow"
                                                    // Reset isScanning after some delay to allow another attempt
                                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                        isScanning = true
                                                    }, 2000)
                                                } else {
                                                    isScanning = false
                                                    vm.fetchItem(code) {
                                                        ContextCompat.getMainExecutor(context).execute {
                                                            onScanned()
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {
                                            // Ignore
                                        }
                                    }
                                    imageProxy.close()
                                }
                            }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // QR Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sizePx = 250.dp.toPx()
                val left = (size.width - sizePx) / 2
                val top = (size.height - sizePx) / 2
                drawRect(Color.Black.copy(alpha = 0.5f))
                drawRect(
                    color = Color.Transparent,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
