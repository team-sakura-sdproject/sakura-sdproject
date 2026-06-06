package com.lendlink.navigation

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.lendlink.viewmodel.*
import com.lendlink.ui.auth.*
import com.lendlink.ui.lender.*
import com.lendlink.ui.borrower.*
import com.lendlink.ui.splash.SplashScreen
import com.lendlink.ui.common.*
import com.lendlink.data.model.*

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val LENDER_DASHBOARD = "lender_dashboard"
    const val LENDER_ADD_ITEM = "lender_add_item"
    const val LENDER_ITEM_DETAIL = "lender_item_detail"
    const val LENDER_LENT_DETAIL = "lender_lent_detail"
    const val LENDER_CATEGORY_MANAGER = "lender_category_manager"
    const val LENDER_CREDIT_HISTORY = "lender_credit_history"
    const val LENDER_HISTORY = "lender_history"
    const val BORROWER_DASHBOARD = "borrower_dashboard"
    const val BORROWER_AVAILABLE_DETAIL = "borrower_available_detail"
    const val BORROWER_ITEM_DETAIL = "borrower_item_detail"
    const val BORROWER_SCAN_QR = "borrower_scan_qr"
    const val NOTIFICATIONS = "notifications"
    const val BORROWER_PAYMENT_HISTORY = "borrower_payment_history"
    const val BORROWER_HISTORY = "borrower_history"
    const val PROFILE = "profile"
    const val LENDER_REPORT_DAMAGE = "lender_report_damage"
    const val DAMAGE_REPORT_DETAIL = "damage_report_detail"
    const val DAMAGE_HISTORY = "damage_history"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    authVm: AuthViewModel,
    lenderVm: LenderViewModel,
    borrowerVm: BorrowerViewModel
) {
    val authState by authVm.state.collectAsState()
    val currentUser by authVm.currentUser.collectAsState()
    
    // Prioritize real-time user data from observeUser flow
    val user = currentUser ?: (authState as? AuthState.Success)?.user

    LaunchedEffect(user) {
        user?.let {
            lenderVm.init(it)
            borrowerVm.init(it)
        }
    }

    var splashFinished by remember { mutableStateOf(false) }

    // Logic to navigate out of splash only once user and splash are ready
    LaunchedEffect(user, splashFinished) {
        if (splashFinished && navController.currentDestination?.route == Routes.SPLASH) {
            val next = if (user != null) {
                if (user.role == "lender") Routes.LENDER_DASHBOARD else Routes.BORROWER_DASHBOARD
            } else Routes.LOGIN
            
            navController.navigate(next) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                splashFinished = true
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(authVm, onSuccess = { role ->
                val route = if (role == "lender") Routes.LENDER_DASHBOARD else Routes.BORROWER_DASHBOARD
                navController.navigate(route) { 
                    popUpTo(0) { inclusive = true } 
                }
            }, onRegister = { navController.navigate(Routes.REGISTER) })
        }

        composable(Routes.REGISTER) {
            RegisterScreen(authVm, onRegistered = { navController.popBackStack() }, onLogin = { navController.popBackStack() })
        }

        // LENDER
        composable(Routes.LENDER_DASHBOARD) {
            LenderDashboard(lenderVm, user?.username ?: "",
                onAddItem = { navController.navigate(Routes.LENDER_ADD_ITEM) },
                onAvailableClick = { item: Item -> navController.navigate("${Routes.LENDER_ITEM_DETAIL}/${item.itemId}") },
                onLentClick = { item: Item -> navController.navigate("${Routes.LENDER_LENT_DETAIL}/${item.itemId}") },
                onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                    authVm.logout()
                    lenderVm.clearData()
                },
                onManageCategories = { navController.navigate(Routes.LENDER_CATEGORY_MANAGER) },
                onCreditHistory = { navController.navigate(Routes.LENDER_CREDIT_HISTORY) },
                onLendHistory = { navController.navigate(Routes.LENDER_HISTORY) },
                onDamageHistory = { navController.navigate(Routes.DAMAGE_HISTORY) },
                onProfile = { navController.navigate(Routes.PROFILE) }
            )
        }

        composable("${Routes.LENDER_ADD_ITEM}?itemId={itemId}") { back ->
            val id = back.arguments?.getString("itemId")
            val items by lenderVm.available.collectAsState()
            val item = items.find { it.itemId == id }
            AddItemScreen(lenderVm, item, onBack = { navController.popBackStack() })
        }

        composable("${Routes.LENDER_ITEM_DETAIL}/{itemId}") { back ->
            val id = back.arguments?.getString("itemId") ?: ""
            val items by lenderVm.available.collectAsState()
            
            val item = items.find { it.itemId == id }
            val lastValidItem = remember(id) { mutableStateOf<Item?>(null) }
            if (item != null) lastValidItem.value = item
            
            // Detect if item disappeared (e.g. borrowed by someone else or deleted)
            LaunchedEffect(item) {
                if (item == null && lastValidItem.value != null) {
                    navController.popBackStack()
                }
            }
            
            val displayItem = item ?: lastValidItem.value

            if (displayItem != null) {
                LenderAvailableDetailScreen(displayItem, lenderVm, onBack = { navController.popBackStack() },
                    onEdit = { i: Item -> navController.navigate("${Routes.LENDER_ADD_ITEM}?itemId=${i.itemId}") })
            } else {
                LaunchedEffect(id) {
                    navController.popBackStack()
                }
            }
        }

        composable("${Routes.LENDER_LENT_DETAIL}/{itemId}") { back ->
            val id = back.arguments?.getString("itemId") ?: ""
            val items by lenderVm.lent.collectAsState()
            val requests by lenderVm.pendingReturns.collectAsState()
            
            val item = items.find { it.itemId == id }
            val lastValidItem = remember(id) { mutableStateOf<Item?>(null) }
            if (item != null) lastValidItem.value = item
            
            val req = requests.find { it.itemId == id }
            val lastValidReq = remember(id) { mutableStateOf<ReturnRequest?>(null) }
            if (req != null) lastValidReq.value = req
            
            val displayItem = item ?: lastValidItem.value
            val displayReq = req ?: lastValidReq.value
            
            // Auto-exit if item is removed (return accepted)
            LaunchedEffect(item) {
                if (item == null && lastValidItem.value != null) {
                    kotlinx.coroutines.delay(500) // Give UI time to navigate manually
                    if (navController.currentDestination?.route?.contains(Routes.LENDER_LENT_DETAIL) == true) {
                        navController.popBackStack()
                    }
                }
            }

            if (displayItem != null) {
                LentItemDetailScreen(displayItem, lenderVm, displayReq, 
                    onBack = { navController.popBackStack() },
                    onReportDamage = { recordId -> navController.navigate("${Routes.LENDER_REPORT_DAMAGE}/$recordId") },
                    onViewDamage = { recordId -> navController.navigate("${Routes.DAMAGE_REPORT_DETAIL}/$recordId") }
                )
            } else {
                LaunchedEffect(id) {
                    navController.popBackStack()
                }
            }
        }

        composable(Routes.LENDER_CATEGORY_MANAGER) {
            CategoryManagerScreen(lenderVm, onBack = { navController.popBackStack() })
        }

        composable(Routes.LENDER_CREDIT_HISTORY) {
            LenderCreditHistoryScreen(lenderVm, onBack = { navController.popBackStack() })
        }

        composable(Routes.LENDER_HISTORY) {
            LendHistoryScreen(lenderVm, onBack = { navController.popBackStack() })
        }

        // BORROWER
        composable(Routes.BORROWER_DASHBOARD) {
            BorrowerDashboard(borrowerVm, user?.username ?: "",
                onItemClick = { record: BorrowRecord -> navController.navigate("${Routes.BORROWER_ITEM_DETAIL}/${record.recordId}") },
                onBrowseItem = { item: Item -> navController.navigate("${Routes.BORROWER_AVAILABLE_DETAIL}/${item.itemId}") },
                onScan = { navController.navigate(Routes.BORROWER_SCAN_QR) },
                onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onPaymentHistory = { navController.navigate(Routes.BORROWER_PAYMENT_HISTORY) },
                onBorrowHistory = { navController.navigate(Routes.BORROWER_HISTORY) },
                onDamageHistory = { navController.navigate(Routes.DAMAGE_HISTORY) },
                onProfile = { navController.navigate(Routes.PROFILE) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                    authVm.logout()
                    borrowerVm.clearData()
                }
            )
        }

        composable("${Routes.BORROWER_AVAILABLE_DETAIL}/{itemId}") { back ->
            val id = back.arguments?.getString("itemId") ?: ""
            val items by borrowerVm.allItems.collectAsState()
            
            val item = items.find { it.itemId == id }
            val lastValidItem = remember(id) { mutableStateOf<Item?>(null) }
            if (item != null) lastValidItem.value = item
            
            // Detect if item status changed (e.g. successfully borrowed)
            LaunchedEffect(item) {
                if (item == null && lastValidItem.value != null) {
                    navController.popBackStack()
                }
            }
            
            val displayItem = item ?: lastValidItem.value

            displayItem?.let {
                BorrowerAvailableItemDetailScreen(it, borrowerVm, onBack = { navController.popBackStack() }, onScan = { navController.navigate("${Routes.BORROWER_SCAN_QR}?expectedId=$id") })
            } ?: LaunchedEffect(id) { navController.popBackStack() }
        }

        composable("${Routes.BORROWER_ITEM_DETAIL}/{recordId}") { back ->
            val id = back.arguments?.getString("recordId") ?: ""
            val records by borrowerVm.active.collectAsState()
            
            val record = records.find { it.recordId == id }
            val lastValidRecord = remember(id) { mutableStateOf<BorrowRecord?>(null) }
            if (record != null) lastValidRecord.value = record
            
            // Auto-exit if borrower record is removed (e.g. return accepted by lender)
            LaunchedEffect(record) {
                if (record == null && lastValidRecord.value != null) {
                    navController.popBackStack()
                }
            }
            
            val displayRecord = record ?: lastValidRecord.value

            displayRecord?.let {
                BorrowerItemDetailScreen(it, borrowerVm, 
                    onBack = { navController.popBackStack() },
                    onViewDamage = { recordId -> navController.navigate("${Routes.DAMAGE_REPORT_DETAIL}/$recordId") }
                )
            } ?: LaunchedEffect(id) { navController.popBackStack() }
        }

        composable(
            route = "${Routes.BORROWER_SCAN_QR}?expectedId={expectedId}",
            arguments = listOf(navArgument("expectedId") { nullable = true; defaultValue = null })
        ) { back ->
            val expectedId = back.arguments?.getString("expectedId")
            ScanQRScreen(borrowerVm, expectedId, onScanned = {
                navController.popBackStack() 
            }, onBack = { navController.popBackStack() })
        }

        composable(Routes.BORROWER_PAYMENT_HISTORY) {
            BorrowerPaymentHistoryScreen(borrowerVm, onBack = { navController.popBackStack() })
        }

        composable(Routes.BORROWER_HISTORY) {
            BorrowHistoryScreen(borrowerVm, onBack = { navController.popBackStack() })
        }

        composable(Routes.NOTIFICATIONS) {
            val role = user?.role ?: ""
            val notifs = if (role == "lender") lenderVm.notifications.collectAsState().value else borrowerVm.notifications.collectAsState().value
            NotificationsScreen(
                notifications = notifs,
                onBack = { navController.popBackStack() },
                onMarkAsRead = { if (role == "lender") lenderVm.markNotificationsRead() else borrowerVm.markNotificationsRead() }
            )
        }
        
        composable(Routes.PROFILE) {
            if (user != null) {
                ProfileScreen(user, authVm, onBack = { navController.popBackStack() })
            }
        }

        // ── DAMAGE REPORTING ROUTES ──────────────────────────
        composable("${Routes.LENDER_REPORT_DAMAGE}/{recordId}") { back ->
            val id = back.arguments?.getString("recordId") ?: ""
            val items by lenderVm.lent.collectAsState()
            
            // Try finding by recordId first, then itemId as backup
            val item = items.find { it.recordId == id } ?: items.find { it.itemId == id }
            val lastValidItem = remember(id) { mutableStateOf<Item?>(null) }
            if (item != null) lastValidItem.value = item
            
            // Auto-exit if item disappears
            LaunchedEffect(item) {
                if (item == null && lastValidItem.value != null) {
                    navController.popBackStack()
                }
            }
            
            val displayItem = item ?: lastValidItem.value
            
            displayItem?.let {
                val record = it.toRecord(it.recordId.ifBlank { id })
                ReportDamageScreen(record, lenderVm, onBack = { navController.popBackStack() })
            } ?: LaunchedEffect(id) {
                navController.popBackStack()
            }
        }

        composable("${Routes.DAMAGE_REPORT_DETAIL}/{recordId}") { back ->
            val id = back.arguments?.getString("recordId") ?: ""
            val role = user?.role ?: ""
            
            val items = lenderVm.lent.collectAsState().value
            val active = borrowerVm.active.collectAsState().value
            
            val record = if (role == "lender") {
                items.find { it.recordId == id || it.itemId == id }?.let { found ->
                    found.toRecord(found.recordId.ifBlank { id })
                }
            } else {
                active.find { it.recordId == id }
            }
            
            val lastValidRecord = remember(id) { mutableStateOf<BorrowRecord?>(null) }
            if (record != null) lastValidRecord.value = record
            
            // Detect external resolution (e.g. lender accepted return after damage pay)
            LaunchedEffect(record) {
                if (record == null && lastValidRecord.value != null) {
                    navController.popBackStack()
                }
            }

            val displayRecord = record ?: lastValidRecord.value
            
            displayRecord?.let {
                DamageReportDetailScreen(it, role, lenderVm, borrowerVm, onBack = { navController.popBackStack() })
            } ?: LaunchedEffect(id) {
                navController.popBackStack()
            }
        }

        composable(Routes.DAMAGE_HISTORY) {
            val role = user?.role ?: ""
            val history = if (role == "lender") lenderVm.damageHistory.collectAsState().value else borrowerVm.damageHistory.collectAsState().value
            DamageHistoryScreen(history, role, onBack = { navController.popBackStack() })
        }
    }
}
