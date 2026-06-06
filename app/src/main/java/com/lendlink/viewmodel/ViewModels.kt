package com.lendlink.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.lendlink.data.local.AppDatabase
import com.lendlink.data.model.*
import com.lendlink.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val message: String) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User = User(), val message: String = "") : AuthState()
    data class Error(val msg: String) : AuthState()
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository()
    
    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _usernameTaken = MutableStateFlow(false)
    val usernameTaken: StateFlow<Boolean> = _usernameTaken.asStateFlow()

    private val _emailTaken = MutableStateFlow(false)
    val emailTaken: StateFlow<Boolean> = _emailTaken.asStateFlow()

    private val _phoneTaken = MutableStateFlow(false)
    val phoneTaken: StateFlow<Boolean> = _phoneTaken.asStateFlow()

    private var userJob: kotlinx.coroutines.Job? = null

    init {
        repo.currentUid?.let { observeUser(it) }
    }

    private fun observeUser(uid: String) {
        userJob?.cancel()
        userJob = viewModelScope.launch {
            repo.observeUser(uid)
                .catch { /* Handle or ignore errors */ }
                .collect { _currentUser.value = it }
        }
    }

    fun reset() { _state.value = AuthState.Idle }

    fun validateUsername(u: String) = viewModelScope.launch {
        try {
            if (u.isBlank()) _usernameTaken.value = false
            else _usernameTaken.value = repo.checkUsernameExists(u)
        } catch (e: Exception) {
            _usernameTaken.value = false // Default to false on error to avoid blocking user
        }
    }

    fun validateEmail(e: String) = viewModelScope.launch {
        try {
            if (e.isBlank()) _emailTaken.value = false
            else _emailTaken.value = repo.checkEmailExists(e)
        } catch (e: Exception) {
            _emailTaken.value = false
        }
    }

    fun validatePhone(p: String) = viewModelScope.launch {
        try {
            if (p.isBlank()) _phoneTaken.value = false
            else _phoneTaken.value = repo.checkPhoneExists(p)
        } catch (e: Exception) {
            _phoneTaken.value = false
        }
    }

    fun login(e: String, p: String) = viewModelScope.launch {
        _state.value = AuthState.Loading
        repo.login(e, p)
            .onSuccess { 
                _currentUser.value = it
                observeUser(it.uid)
                _state.value = AuthState.Success(user = it, message = "Welcome back!") 
            }
            .onFailure { _state.value = AuthState.Error(it.message ?: "Login failed") }
    }

    fun register(
        username: String, email: String, phone: String,
        pass: String, role: String, lat: Double, lng: Double, addr: String
    ) = viewModelScope.launch {
        _state.value = AuthState.Loading
        repo.register(username, email, phone, pass, role, lat, lng, addr)
            .onSuccess { 
                _state.value = AuthState.Success(message = "Account created successfully!") 
            }
            .onFailure { _state.value = AuthState.Error(it.message ?: "Registration failed") }
    }

    fun logout() {
        userJob?.cancel()
        userJob = null
        _currentUser.value = null
        _state.value = AuthState.Idle
        repo.logout()
    }

    fun updateProfileImage(uid: String, uri: Uri) = viewModelScope.launch {
        _state.value = AuthState.Loading
        try {
            val context = getApplication<Application>()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            
            if (bytes == null) {
                _state.value = AuthState.Error("Could not read image data")
                return@launch
            }

            repo.updateProfileImage(uid, bytes)
                .onSuccess { url ->
                    // Reset to Idle instead of Success(user, message) to avoid triggering 
                    // AuthState-dependent navigation logic in NavGraph
                    _state.value = AuthState.Idle
                    // User data update is handled by repo.observeUser
                }
                .onFailure { 
                    _state.value = AuthState.Error(it.message ?: "Failed to upload image")
                }
        } catch (e: Exception) {
            _state.value = AuthState.Error(e.message ?: "Error reading image file")
        }
    }
}

class LenderViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val itemRepo = ItemRepository(db.itemDao())
    private val borrowRepo = BorrowRepository(
        db.borrowDao(), db.lenderCreditHistoryDao(), 
        db.borrowerPaymentHistoryDao(), db.lendHistoryDao(), 
        db.borrowHistoryDao(), db.damageHistoryDao()
    )

    private val _uid = MutableStateFlow("")
    private val _userProfile = MutableStateFlow<User?>(null)
    val uid: StateFlow<String> = _uid.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val available = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else itemRepo.observeAvailable(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lent = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else itemRepo.observeLent(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else itemRepo.observeCategories(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val wallet = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(0L) else borrowRepo.observeWallet(id).catch { emit(0L) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingReturns = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else borrowRepo.observePendingReturns(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notifications = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else borrowRepo.observeNotifications(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val creditHistory = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else borrowRepo.observeLenderCreditHistory(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lendHistory = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList()) else borrowRepo.observeLendHistory(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCat = MutableStateFlow("All")
    val selectedCat: StateFlow<String> = _selectedCat.asStateFlow()

    val filteredAvailable = combine(available, _selectedCat, _searchQuery) { items, cat, query ->
        items.filter { (if (cat == "All") true else it.category == cat) && (if (query.isBlank()) true else it.name.contains(query, ignoreCase = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredLent = combine(lent, _selectedCat, _searchQuery) { items, cat, query ->
        items.filter { (if (cat == "All") true else it.category == cat) && (if (query.isBlank()) true else it.name.contains(query, ignoreCase = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun init(user: User) { 
        _uid.value = user.uid
        _userProfile.value = user 
    }
    fun clearData() { 
        _uid.value = ""
        _userProfile.value = null
        _searchQuery.value = ""
        _selectedCat.value = "All"
        _ui.value = UiState.Idle 
    }
    fun selectCat(cat: String) { _selectedCat.value = cat }
    fun updateSearchQuery(q: String) { _searchQuery.value = q }
    fun resetUi() { _ui.value = UiState.Idle }
    fun setUiError(msg: String) { _ui.value = UiState.Error(msg) }

    fun addItem(item: Item, uri: Uri?) = viewModelScope.launch {
        _ui.value = UiState.Loading
        val profile = _userProfile.value ?: run {
            _ui.value = UiState.Error("Session expired. Log in again.")
            return@launch
        }
        val bytes = uri?.let { u ->
            try { getApplication<Application>().contentResolver.openInputStream(u)?.use { it.readBytes() } } catch (_: Exception) { null }
        }
        val currentCats = categories.value.map { it.name }
        if (item.category != "General" && item.category !in currentCats) {
             _ui.value = UiState.Error("Invalid category. Please select or add a category first.")
             return@launch
        }
        val fullItem = item.copy(
            lenderId = profile.uid, 
            lenderName = profile.username,
            lenderPhone = profile.phone,
            lenderLocation = profile.locationAddress,
            lenderLatitude = profile.latitude,
            lenderLongitude = profile.longitude
        )
        itemRepo.addItem(fullItem, bytes)
            .onSuccess { _ui.value = UiState.Success("Item added successfully!") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Failed to add item.") }
    }

    fun updateItem(item: Item, uri: Uri?) = viewModelScope.launch {
        _ui.value = UiState.Loading
        val bytes = uri?.let { u ->
            try { getApplication<Application>().contentResolver.openInputStream(u)?.use { it.readBytes() } } catch (_: Exception) { null }
        }
        itemRepo.updateItem(item, bytes)
            .onSuccess { _ui.value = UiState.Success("Updated successfully") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Update failed") }
    }

    fun deleteItem(id: String, cat: String) = viewModelScope.launch {
        _ui.value = UiState.Loading
        itemRepo.deleteItem(id, _uid.value, cat)
            .onSuccess { _ui.value = UiState.Success("Deleted successfully") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Delete failed") }
    }

    fun addCategory(n: String) = viewModelScope.launch {
        itemRepo.addCategory(_uid.value, n)
            .onSuccess { _ui.value = UiState.Success("Added category: $n") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Failed to add category") }
    }

    fun editCategory(id: String, n: String) = viewModelScope.launch {
        itemRepo.editCategory(_uid.value, id, n).onSuccess { _ui.value = UiState.Success("Renamed") }
    }

    fun deleteCategory(id: String, n: String) = viewModelScope.launch {
        itemRepo.deleteCategory(_uid.value, id, n).onSuccess { _ui.value = UiState.Success("Deleted") }.onFailure { _ui.value = UiState.Error(it.message ?: "") }
    }

    fun sendReminder(r: BorrowRecord) = viewModelScope.launch { borrowRepo.sendReminder(r).onSuccess { _ui.value = UiState.Success("Reminder sent") } }
    fun acceptReturn(r: BorrowRecord, reqId: String) = viewModelScope.launch { _ui.value = UiState.Loading; borrowRepo.acceptReturn(r, reqId).onSuccess { _ui.value = UiState.Success("Accepted") } }

    fun markNotificationsRead() = viewModelScope.launch {
        if (_uid.value.isNotEmpty()) borrowRepo.markNotificationsRead(_uid.value)
    }

    // ── DAMAGE REPORTING (Lender) ───────────────────────────
    fun submitDamageReport(record: BorrowRecord, condition: String, desc: String, amount: Long, uri: Uri?) = viewModelScope.launch {
        _ui.value = UiState.Loading
        val bytes = uri?.let { u ->
            try { getApplication<Application>().contentResolver.openInputStream(u)?.use { it.readBytes() } } catch (_: Exception) { null }
        }
        borrowRepo.submitDamageReport(record, condition, desc, amount, bytes)
            .onSuccess { _ui.value = UiState.Success("Damage report submitted") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Failed to submit report") }
    }

    fun completeNegotiation(record: BorrowRecord) = viewModelScope.launch {
        _ui.value = UiState.Loading
        borrowRepo.completeNegotiation(record)
            .onSuccess { _ui.value = UiState.Success("Negotiation completed") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Failed to complete") }
    }

    val damageHistory: StateFlow<List<DamageHistory>> = _uid.flatMapLatest { borrowRepo.observeDamageHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class BorrowerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val itemRepo = ItemRepository(db.itemDao())
    private val borrowRepo = BorrowRepository(
        db.borrowDao(), db.lenderCreditHistoryDao(), 
        db.borrowerPaymentHistoryDao(), db.lendHistoryDao(), 
        db.borrowHistoryDao(), db.damageHistoryDao()
    )

    private val _uid = MutableStateFlow("")
    private val _userProfile = MutableStateFlow<User?>(null)
    val uid: StateFlow<String> = _uid.asStateFlow()

    private val _searchQueryAvailable = MutableStateFlow("")
    val searchQueryAvailable: StateFlow<String> = _searchQueryAvailable.asStateFlow()

    private val _searchQueryActive = MutableStateFlow("")
    val searchQueryActive: StateFlow<String> = _searchQueryActive.asStateFlow()

    private val _selectedCatAvailable = MutableStateFlow("All")
    val selectedCatAvailable: StateFlow<String> = _selectedCatAvailable.asStateFlow()

    private val _selectedCatActive = MutableStateFlow("All")
    val selectedCatActive: StateFlow<String> = _selectedCatActive.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val active = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList<BorrowRecord>()) else borrowRepo.observeBorrowerActive(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val wallet = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(0L) else borrowRepo.observeWallet(id).catch { emit(0L) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notifications = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList<AppNotification>()) else borrowRepo.observeNotifications(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val paymentHistory = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList<BorrowerPaymentHistory>()) else borrowRepo.observeBorrowerPaymentHistory(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val borrowHistory = _uid.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(emptyList<BorrowHistory>()) else borrowRepo.observeBorrowHistory(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allItems = _uid.flatMapLatest { _ -> borrowRepo.observeAllAvailableItems().catch { emit(emptyList()) } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAllItems = combine(allItems, _selectedCatAvailable, _searchQueryAvailable) { items, cat, query ->
        items.filter { (if (cat == "All") true else it.category == cat) && (if (query.isBlank()) true else it.name.contains(query, ignoreCase = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredActive = combine(active, _selectedCatActive, _searchQueryActive) { items, cat, query ->
        items.filter { (if (cat == "All") true else it.itemCategory == cat) && (if (query.isBlank()) true else it.itemName.contains(query, ignoreCase = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCategories = active.map { items ->
        items.map { it.itemCategory.ifBlank { "General" } }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val systemCategories = borrowRepo.observeSystemCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _scanned = MutableStateFlow<Item?>(null)
    val scanned: StateFlow<Item?> = _scanned.asStateFlow()

    fun init(user: User) { 
        _uid.value = user.uid
        _userProfile.value = user 
    }
    fun clearData() { 
        _uid.value = ""
        _userProfile.value = null
        _searchQueryAvailable.value = ""
        _searchQueryActive.value = ""
        _selectedCatAvailable.value = "All"
        _selectedCatActive.value = "All"
        _ui.value = UiState.Idle 
        _scanned.value = null
    }

    fun selectCatAvailable(cat: String) { _selectedCatAvailable.value = cat }
    fun updateSearchQueryAvailable(q: String) { _searchQueryAvailable.value = q }

    fun selectCatActive(cat: String) { _selectedCatActive.value = cat }
    fun updateSearchQueryActive(q: String) { _searchQueryActive.value = q }
    fun resetUi() { _ui.value = UiState.Idle }
    fun setUiError(msg: String) { _ui.value = UiState.Error(msg) }
    fun clearScanned() { _scanned.value = null }

    fun fetchItem(id: String, onFetched: () -> Unit) = viewModelScope.launch {
        val item = itemRepo.getItemById(id)
        when {
            item == null -> _ui.value = UiState.Error("Item not found")
            item.status != "available" -> _ui.value = UiState.Error("This item is currently not available.")
            item.lenderId == _uid.value -> _ui.value = UiState.Error("You cannot borrow your own item.")
            else -> { 
                _scanned.value = item
                _ui.value = UiState.Idle 
                onFetched()
            }
        }
    }

    fun confirmBorrow(item: Item) = viewModelScope.launch {
        _ui.value = UiState.Loading
        val profile = _userProfile.value ?: return@launch
        borrowRepo.borrowItem(item, profile)
            .onSuccess { _scanned.value = null; _ui.value = UiState.Success("Item borrowed successfully!") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Borrowing failed") }
    }

    fun requestReturn(r: BorrowRecord) = viewModelScope.launch {
        _ui.value = UiState.Loading
        borrowRepo.requestReturn(r)
            .onSuccess { _ui.value = UiState.Success("Return request sent to lender") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Failed to request return") }
    }

    fun markNotificationsRead() = viewModelScope.launch {
        if (_uid.value.isNotEmpty()) borrowRepo.markNotificationsRead(_uid.value)
    }

    // ── DAMAGE REPORTING (Borrower) ─────────────────────────
    fun payDamageCharge(record: BorrowRecord, charge: Long) = viewModelScope.launch {
        val user = _userProfile.value ?: return@launch
        _ui.value = UiState.Loading
        borrowRepo.payDamageCharge(record, charge, user)
            .onSuccess { _ui.value = UiState.Success("Damage charge paid successfully") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Payment failed") }
    }

    fun requestNegotiation(record: BorrowRecord) = viewModelScope.launch {
        _ui.value = UiState.Loading
        val name = _userProfile.value?.username ?: "Borrower"
        borrowRepo.requestNegotiation(record, name)
            .onSuccess { _ui.value = UiState.Success("Negotiation request sent to lender") }
            .onFailure { _ui.value = UiState.Error(it.message ?: "Request failed") }
    }

    val damageHistory: StateFlow<List<DamageHistory>> = _uid.flatMapLatest { borrowRepo.observeDamageHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
