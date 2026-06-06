package com.lendlink.ui.lender

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.lendlink.data.model.*
import com.lendlink.ui.common.*
import com.lendlink.ui.theme.*
import com.lendlink.viewmodel.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LenderDashboard(
    vm: LenderViewModel,
    lenderName: String,
    onAddItem: () -> Unit,
    onAvailableClick: (Item) -> Unit,
    onLentClick: (Item) -> Unit,
    onLogout: () -> Unit,
    onNotifications: () -> Unit,
    onCreditHistory: () -> Unit,
    onLendHistory: () -> Unit,
    onDamageHistory: () -> Unit,
    onManageCategories: () -> Unit,
    onProfile: () -> Unit
) {
    val available by vm.filteredAvailable.collectAsState()
    val lent by vm.filteredLent.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCat by vm.selectedCat.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val pendingReturns by vm.pendingReturns.collectAsState()
    val notifications by vm.notifications.collectAsState()
    val unreadCount = notifications.count { !it.read }
    val ui by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(ui) {
        val s = ui
        if (s is UiState.Success) { 
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi()
        } else if (s is UiState.Error) { 
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi() 
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lenderName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Lender", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, actionIconContentColor = Color.White),
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
                            DropdownMenuItem(text = { Text("Credit History") }, leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) }, onClick = { menu = false; onCreditHistory() })
                            DropdownMenuItem(text = { Text("Lend History") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { menu = false; onLendHistory() })
                            DropdownMenuItem(text = { Text("Damage History") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, onClick = { menu = false; onDamageHistory() })
                            DropdownMenuItem(text = { Text("Manage Categories") }, leadingIcon = { Icon(Icons.Default.Category, null) }, onClick = { menu = false; onManageCategories() })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Logout", color = MaterialTheme.colorScheme.error) }, 
                                leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error) }, 
                                onClick = { 
                                    menu = false
                                    onLogout() 
                                }
                            )
                        }
                    }
                })
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = onAddItem, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            WalletCard(wallet, lenderName, "Lender Earnings")
            
            if (pendingReturns.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { tab = 1 }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AssignmentReturn, null, tint = LentOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("${pendingReturns.size} return request(s) awaiting approval", color = LentOrange, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = LentOrange)
                    }
                }
            }
            TabRow(selectedTabIndex = tab, indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[tab]), color = MaterialTheme.colorScheme.primary)
            }) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Available (${available.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Lent (${lent.size})") })
            }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search my items...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton({ vm.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, null)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selectedCat == "All", onClick = { vm.selectCat("All") }, label = { Text("All") }) }
                items(categories) { cat -> FilterChip(selected = selectedCat == cat.name, onClick = { vm.selectCat(cat.name) }, label = { Text(cat.name) }) }
            }
            
            when (tab) {
                0 -> AvailableTab(available, onAvailableClick)
                1 -> LentTab(lent, pendingReturns, onLentClick)
            }
        }
    }
}

@Composable
private fun AvailableTab(items: List<Item>, onItem: (Item) -> Unit) {
    if (items.isEmpty()) EmptyState("No items found", "Try changing your search or category", Icons.Default.SearchOff)
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.itemId }) { item -> LenderItemCard(item, onItem) }
    }
}

@Composable
private fun LentTab(items: List<Item>, pendingReturns: List<ReturnRequest>, onItem: (Item) -> Unit) {
    if (items.isEmpty()) EmptyState("No lent items found", "Try changing your search or category", Icons.Default.SearchOff)
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.itemId }) { item ->
            val overdue = item.deadline > 0L && System.currentTimeMillis() > item.deadline
            val hasReturnRequest = pendingReturns.any { it.itemId == item.itemId || it.recordId == item.recordId }
            
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onItem(item) },
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemImage(item.imageUrl, size = 64)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "By: ${item.borrowerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(.6f)
                        )
                        Text(
                            "Due: ${fmtDate(item.deadline)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) OverdueRed else MaterialTheme.colorScheme.onSurface.copy(.6f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(if (overdue) "Overdue" else "Lent", if (overdue) OverdueRed else LentOrange)
                            
                            if (item.status == "damaged" || item.status == "negotiating") {
                                Spacer(Modifier.width(8.dp))
                                StatusChip("Damaged", DamagePurple)
                            } else if (hasReturnRequest) {
                                Spacer(Modifier.width(8.dp))
                                StatusChip("Return Pending", PendingBlue)
                            }
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.4f))
                }
            }
        }
    }
}

@Composable
fun LenderItemCard(item: Item, onClick: (Item) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick(item) }, elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemImage(item.imageUrl, size = 64)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(item.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("Available", AvailGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(krw(item.price), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.4f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddItemScreen(vm: LenderViewModel, existing: Item? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val isEdit = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var price by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "General") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }
    var showNewCatDialog by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }
    
    val categories by vm.categories.collectAsState()
    val uiState by vm.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showNewCatDialog) {
        AlertDialog(
            onDismissRequest = { showNewCatDialog = false },
            title = { Text("Add New Category") },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCatName.isNotBlank()) {
                        vm.addCategory(newCatName.trim())
                        category = newCatName.trim()
                        newCatName = ""
                        showNewCatDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCatDialog = false }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(uiState) {
        val s = uiState
        if (s is UiState.Success) {
            if (s.message == "Updated successfully" || s.message == "Item added successfully!") {
                vm.resetUi()
                onBack()
            } else {
                scope.launch { snackbarHostState.showSnackbar(s.message, duration = SnackbarDuration.Short) }
                vm.resetUi()
            }
        } else if (s is UiState.Error) {
            scope.launch { snackbarHostState.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi()
        }
    }

    if (showCamera) {
        CameraDialog(
            onCaptured = { uri ->
                imageUri = uri
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                showCamera = false
            },
            onDismiss = { showCamera = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Item" else "Add New Item") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray).clickable { showCamera = true }, contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (existing?.imageUrl?.isNotEmpty() == true) AsyncImage(existing.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Text("Tap to capture item photo", color = Color.Gray)
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat.name) }, onClick = { category = cat.name; catExpanded = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("+ Add New Category", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                        onClick = { 
                            catExpanded = false
                            showNewCatDialog = true
                        }
                    )
                }
            }
            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Rental Price") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), prefix = { Text("₩ ") }, shape = RoundedCornerShape(10.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(10.dp))
            Button(
                onClick = {
                    if (name.isBlank()) {
                        vm.setUiError("Please enter item name")
                        return@Button
                    }
                    if (price.isBlank()) {
                        vm.setUiError("Please enter rental price")
                        return@Button
                    }
                    val item = if (isEdit) existing!!.copy(name = name, description = desc, price = price.toLongOrNull() ?: 0L, category = category)
                               else Item(name = name, description = desc, price = price.toLongOrNull() ?: 0L, category = category)
                    
                    if (isEdit) vm.updateItem(item, imageUri)
                    else vm.addItem(item, imageUri)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = uiState !is UiState.Loading
            ) {
                val s = uiState
                if (s is UiState.Loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text(if (isEdit) "Update Item" else "List Item")
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraDialog(onCaptured: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val permission = rememberPermissionState(Manifest.permission.CAMERA)
    
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    LaunchedEffect(Unit) { permission.launchPermissionRequest() }

    if (permission.status.isGranted) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(modifier = Modifier.aspectRatio(0.75f)) { // 3:4 Aspect Ratio for small format
                    AndroidView(
                        factory = { ctx ->
                            previewView.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Separate binding logic - only happens once when dialog opens
                    LaunchedEffect(Unit) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                                cameraControl = camera.cameraControl
                                camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                                    maxZoom = zoomState.maxZoomRatio
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                    
                    IconButton(
                        onClick = onDismiss, 
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }

                    // Zoom Slider
                    if (maxZoom > 1f) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .height(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Zoom", color = Color.White, fontSize = 10.sp)
                            Slider(
                                value = zoomRatio,
                                onValueChange = { 
                                    zoomRatio = it
                                    cameraControl?.setZoomRatio(it)
                                },
                                valueRange = 1f..maxZoom,
                                modifier = Modifier
                                    .graphicsLayer {
                                        rotationZ = 270f
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    }
                                    .width(150.dp)
                            )
                        }
                    }
                    
                    FloatingActionButton(
                        onClick = {
                            imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val bitmap = image.toBitmap()
                                    val rotatedBitmap = rotateBitmap(bitmap, rotation)
                                    image.close()
                                    val uri = saveBitmapToUri(context, rotatedBitmap)
                                    onCaptured(uri)
                                }
                                override fun onError(exception: ImageCaptureException) {}
                            })
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        containerColor = Color.White
                    ) { Icon(Icons.Default.CameraAlt, null) }
                }
            }
        }
    }
}

private fun saveBitmapToUri(context: android.content.Context, bitmap: Bitmap, prefix: String = "captured"): Uri {
    val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
    
    // Resize to reasonable profile/item size to stay under Firebase limits
    val maxSide = 1200
    val scaledBitmap = if (bitmap.width > maxSide || bitmap.height > maxSide) {
        val scale = maxSide.toFloat() / Math.max(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else {
        bitmap
    }
    
    file.outputStream().use { 
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
    }
    return Uri.fromFile(file)
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(vm: LenderViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val ui by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var editCat by remember { mutableStateOf<Category?>(null) }
    var newName by remember { mutableStateOf("") }
    LaunchedEffect(ui) {
        val s = ui
        if (s is UiState.Success) { 
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi() 
        } else if (s is UiState.Error) { 
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi() 
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false; newName = "" },
            title = { Text("Add Category") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Category name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { Button(onClick = { if (newName.isNotBlank()) { vm.addCategory(newName.trim()); newName = ""; showAdd = false } }) { Text("Add") } },
            dismissButton = { TextButton({ showAdd = false; newName = "" }) { Text("Cancel") } }
        )
    }
    editCat?.let { cat ->
        var editName by remember { mutableStateOf(cat.name) }
        AlertDialog(
            onDismissRequest = { editCat = null },
            title = { Text("Rename Category") },
            text = { OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("New name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { Button(onClick = { if (editName.isNotBlank()) { vm.editCategory(cat.categoryId, editName.trim()); editCat = null } }) { Text("Save") } },
            dismissButton = { TextButton({ editCat = null }) { Text("Cancel") } }
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Manage Categories") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton({ showAdd = true }) { Icon(Icons.Default.Add, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        }
    ) { pad ->
        if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Category, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurface.copy(.25f))
                    Spacer(Modifier.height(12.dp))
                    Text("No categories yet")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAdd = true }) { Text("Add First Category") }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Tap edit to rename. Categories with items cannot be deleted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Spacer(Modifier.height(8.dp))
                }
                items(categories, key = { it.categoryId }) { cat ->
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Label, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(cat.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editCat = cat }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { vm.deleteCategory(cat.categoryId, cat.name) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LenderAvailableDetailScreen(item: Item, vm: LenderViewModel, onBack: () -> Unit, onEdit: (Item) -> Unit) {
    var showQr by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val uiState by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        val s = uiState
        if (s is UiState.Success && s.message == "Updated successfully") {
            scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
            vm.resetUi()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Listing?") },
            text = { Text("Are you sure you want to permanently remove '${item.name}' from your listings?") },
            confirmButton = { 
                Button(
                    onClick = { 
                        onBack() // Navigate back FIRST before deleting to avoid recompose crash
                        vm.deleteItem(item.itemId, item.category)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") } 
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    // Auto-exit if item is no longer available (e.g. borrowed or deleted)
    LaunchedEffect(item.status) {
        if (item.status != "available") {
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Item Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { 
                    IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, null) }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            ItemImage(item.imageUrl, modifier = Modifier.fillMaxWidth().height(300.dp), clip = false)
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusChip("Available", AvailGreen)
                }
                Text(krw(item.price), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                DetailRow("Category", item.category.ifBlank { "General" })
                DetailRow("Description", item.description.ifEmpty { "No description provided." })
                DetailRow("Added on", fmtDate(item.createdAt))
                
                Spacer(Modifier.height(24.dp))
                
                if (!showQr) {
                    Button(
                        onClick = { showQr = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCode, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Item QR Code", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Item QR Code",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Borrowers can scan this code to instantly rent this item from you.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Surface(
                                modifier = Modifier.size(260.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 8.dp
                            ) {
                                val qrBitmap = remember(item.itemId) {
                                    try {
                                        val size = 512
                                        val hints = mapOf(EncodeHintType.MARGIN to 1)
                                        val bits = QRCodeWriter().encode(item.itemId, BarcodeFormat.QR_CODE, size, size, hints)
                                        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                                        for (x in 0 until size) {
                                            for (y in 0 until size) {
                                                bitmap.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                            }
                                        }
                                        bitmap
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                
                                qrBitmap?.let {
                                    Image(
                                        it.asImageBitmap(),
                                        null,
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            
                            OutlinedButton(
                                onClick = { showQr = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.VisibilityOff, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Hide QR Code")
                            }
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
fun LentItemDetailScreen(item: Item, vm: LenderViewModel, req: ReturnRequest?, onBack: () -> Unit, onReportDamage: (String) -> Unit, onViewDamage: (String) -> Unit) {
    val uiState by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(uiState) {
        val s = uiState
        if (s is UiState.Success) {
            if (s.message == "Accepted") {
                scope.launch {
                    snack.showSnackbar("Return accepted successfully", duration = SnackbarDuration.Short)
                    kotlinx.coroutines.delay(300) // Brief delay for database sync
                    vm.resetUi()
                    onBack()
                }
            } else {
                scope.launch { snack.showSnackbar(s.message, duration = SnackbarDuration.Short) }
                vm.resetUi()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Lent Item Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            ItemImage(item.imageUrl, modifier = Modifier.fillMaxWidth().height(240.dp), clip = false)
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val overdue = item.deadline > 0 && System.currentTimeMillis() > item.deadline
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(if (overdue) "Overdue" else "Lent Out", if (overdue) OverdueRed else LentOrange)
                    if (item.status == "damaged" || item.status == "negotiating") {
                        Spacer(Modifier.width(8.dp))
                        StatusChip("Damaged", DamagePurple)
                    }
                }
                HorizontalDivider()
                DetailRow("Category", item.category.ifBlank { "General" })
                HorizontalDivider()
                Text("Borrower Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DetailRow("Name", item.borrowerName)
                DetailRow("Phone", item.borrowerPhone)
                DetailRow("Location", item.borrowerLocation)
                HorizontalDivider()
                Text("Lending Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DetailRow("Lent on", fmtDate(item.borrowedAt))
                DetailRow("Return deadline", fmtDate(item.deadline), warn = overdue)
                
                Spacer(Modifier.height(24.dp))
                
                if (item.status == "damaged" || item.status == "negotiating") {
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
                                    "Damage report has been submitted. Borrower needs to review and pay.", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    color = OverdueRed, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { onViewDamage(item.recordId) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OverdueRed)
                            ) {
                                Text("View Damage Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (req != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AssignmentReturn, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Return Requested", 
                                        style = MaterialTheme.typography.titleMedium, 
                                        fontWeight = FontWeight.ExtraBold, 
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "Action Required", 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Text(
                                "The borrower has requested to complete the return process. Please confirm only if you have physically received the item.", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        val record = item.toRecord(req.recordId)
                                        vm.acceptReturn(record, req.requestId) 
                                    },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    enabled = uiState !is UiState.Loading
                                ) {
                                    if (uiState is UiState.Loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    else Text("Confirm & Accept Return", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                                
                                Spacer(Modifier.width(8.dp))
                                
                                OutlinedButton(
                                    onClick = { 
                                        onReportDamage(req.recordId)
                                    },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Report Damage", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { 
                            val record = item.toRecord()
                            vm.sendReminder(record) 
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = uiState !is UiState.Loading
                    ) {
                        val s = uiState
                        if (s is UiState.Loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("Send Return Reminder")
                    }
                }
            }
        }
    }
}
