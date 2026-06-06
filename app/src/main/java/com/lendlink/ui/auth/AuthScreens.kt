package com.lendlink.ui.auth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.lendlink.viewmodel.AuthState
import com.lendlink.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: AuthViewModel, onSuccess: (String) -> Unit, onRegister: () -> Unit) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVis by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthState.Success -> { onSuccess(s.user.role); vm.reset() }
            is AuthState.Error -> { snack.showSnackbar(s.msg); vm.reset() }
            else -> {}
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("LendLink", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("Community Sharing Platform", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            Spacer(Modifier.height(48.dp))
            
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = if (passVis) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ passVis = !passVis }) { Icon(if (passVis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            
            Spacer(Modifier.height(32.dp))
            Button(onClick = { if (email.isNotBlank() && pass.isNotBlank()) vm.login(email.trim(), pass.trim()) },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
                if (state is AuthState.Loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Login to Account", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onRegister) { Text("New here? Create an account", fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RegisterScreen(vm: AuthViewModel, onRegistered: () -> Unit, onLogin: () -> Unit) {
    val state by vm.state.collectAsState()
    val usernameTaken by vm.usernameTaken.collectAsState()
    val emailTaken by vm.emailTaken.collectAsState()
    val phoneTaken by vm.phoneTaken.collectAsState()

    val snack = remember { SnackbarHostState() }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("borrower") }
    var passVis by remember { mutableStateOf(false) }
    var confirmPassVis by remember { mutableStateOf(false) }
    var locationAddress by remember { mutableStateOf("") }
    var lat by remember { mutableDoubleStateOf(37.5665) } // Default to Seoul
    var lng by remember { mutableDoubleStateOf(126.9780) }
    var showMap by remember { mutableStateOf(false) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showMap = true
                return
            }

            val location = fusedLocationClient.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(10000)
                    .build(),
                null
            ).await()

            location?.let { loc ->
                lat = loc.latitude
                lng = loc.longitude
                
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(loc.latitude, loc.longitude, 1) { addresses ->
                            if (addresses.isNotEmpty()) {
                                locationAddress = addresses[0].getAddressLine(0)
                            } else {
                                locationAddress = "Lat: ${String.format(Locale.getDefault(), "%.4f", loc.latitude)}, Lng: ${String.format(Locale.getDefault(), "%.4f", loc.longitude)}"
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            locationAddress = addresses[0].getAddressLine(0)
                        } else {
                            locationAddress = "Lat: ${String.format(Locale.getDefault(), "%.4f", loc.latitude)}, Lng: ${String.format(Locale.getDefault(), "%.4f", loc.longitude)}"
                        }
                    }
                } catch (_: Exception) {
                    locationAddress = "Lat: ${String.format(Locale.getDefault(), "%.4f", loc.latitude)}, Lng: ${String.format(Locale.getDefault(), "%.4f", loc.longitude)}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchAndShowMap() {
        scope.launch {
            isFetchingLocation = true
            fetchCurrentLocation()
            isFetchingLocation = false
            showMap = true
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fetchAndShowMap()
        }
    }

    fun checkAndRequestLocation() {
        if (!locPerm.status.isGranted) {
            locPerm.launchPermissionRequest()
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        
        if (!isGpsEnabled) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
            val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
            val client = LocationServices.getSettingsClient(context)
            val task = client.checkLocationSettings(builder.build())

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                        locationLauncher.launch(intentSenderRequest)
                    } catch (_: IntentSender.SendIntentException) {}
                }
            }
            task.addOnSuccessListener {
                fetchAndShowMap()
            }
        } else {
            fetchAndShowMap()
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthState.Success -> {
                onRegistered()
                vm.reset()
            }
            is AuthState.Error -> { snack.showSnackbar(s.msg); vm.reset() }
            else -> {}
        }
    }

    fun hideMap() { showMap = false }

    if (showMap) {
        LocationPickerDialog(
            initialLat = lat,
            initialLng = lng,
            onSelect = { sLat, sLng, sAddr ->
                lat = sLat
                lng = sLng
                locationAddress = sAddr
                hideMap()
            },
            onDismiss = { hideMap() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { 
            TopAppBar(
                title = { Text("Create Account") },
                navigationIcon = { IconButton(onClick = onLogin) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            ) 
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(20.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = username,
                onValueChange = { 
                    username = it
                    vm.validateUsername(it)
                },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                isError = usernameTaken,
                supportingText = { if (usernameTaken) Text("This username is already taken", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { 
                    email = it
                    vm.validateEmail(it)
                },
                label = { Text("Email (@gmail.com)") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                isError = emailTaken || (email.isNotEmpty() && !email.endsWith("@gmail.com")),
                supportingText = { 
                    if (emailTaken) {
                        Text("This email is already registered", color = MaterialTheme.colorScheme.error)
                    } else if (email.isNotEmpty() && !email.endsWith("@gmail.com")) {
                        Text("Enter a valid email address (e.g., name@gmail.com)", color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    vm.validatePhone(it)
                },
                label = { Text("Phone number") },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                isError = phoneTaken,
                supportingText = { if (phoneTaken) Text("This phone number is already used", color = MaterialTheme.colorScheme.error) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))

            val passwordPattern = remember { Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$") }
            val isRegPassValid = pass.isEmpty() || passwordPattern.matches(pass)
            val isPassValid = passwordPattern.matches(pass)

            OutlinedTextField(value = pass, onValueChange = { pass = it },
                label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = if (passVis) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ passVis = !passVis }) {
                    Icon(if (passVis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                isError = !isRegPassValid,
                supportingText = { if (!isRegPassValid) Text("Requires 6+ chars, uppercase, lowercase, and a digit", color = MaterialTheme.colorScheme.error) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = confirmPass, onValueChange = { confirmPass = it },
                label = { Text("Confirm password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = if (confirmPassVis) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ confirmPassVis = !confirmPassVis }) {
                    Icon(if (confirmPassVis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                isError = confirmPass.isNotEmpty() && pass != confirmPass,
                supportingText = { if (confirmPass.isNotEmpty() && pass != confirmPass) Text("Passwords do not match") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (isFetchingLocation) "Fetching location..." else locationAddress.ifEmpty { "Tap to select your location" },
                    onValueChange = {},
                    label = { Text("Location") },
                    leadingIcon = { 
                        if (isFetchingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingIcon = { Icon(Icons.Default.Map, null) },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = if (isFetchingLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Box(modifier = Modifier.matchParentSize().clickable(enabled = !isFetchingLocation) {
                    checkAndRequestLocation()
                })
            }
            Spacer(Modifier.height(16.dp))

            Text("Register as", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (role == "lender") {
                    Button(onClick = { role = "lender" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Lender") }
                } else {
                    OutlinedButton(onClick = { role = "lender" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Lender") }
                }
                if (role == "borrower") {
                    Button(onClick = { role = "borrower" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Borrower") }
                } else {
                    OutlinedButton(onClick = { role = "borrower" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Borrower") }
                }
            }

            Spacer(Modifier.height(32.dp))
            val isEmailValid = email.endsWith("@gmail.com")
            val canRegister = username.isNotBlank() && isEmailValid && isPassValid && pass == confirmPass && locationAddress.isNotBlank() && !usernameTaken && !emailTaken && !phoneTaken
            Button(
                onClick = { if (canRegister) vm.register(username.trim(), email.trim(), phone.trim(), pass, role, lat, lng, locationAddress) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = canRegister && state !is AuthState.Loading
            ) {
                if (state is AuthState.Loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Create Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocationPickerDialog(initialLat: Double, initialLng: Double, onSelect: (Double, Double, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedLat by remember(initialLat) { mutableDoubleStateOf(initialLat) }
    var selectedLng by remember(initialLng) { mutableDoubleStateOf(initialLng) }
    var address by remember { mutableStateOf("Searching...") }

    fun updateAddress(lt: Double, lg: Double) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lt, lg, 1) { addresses ->
                    address = if (addresses.isNotEmpty()) addresses[0].getAddressLine(0) else "Unknown location"
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lt, lg, 1)
                address = if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0) else "Unknown location"
            }
        } catch (_: Exception) { address = "Error fetching address" }
    }

    LaunchedEffect(selectedLat, selectedLng) { updateAddress(selectedLat, selectedLng) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize(),
        title = null,
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    Text("Select Location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    @Suppress("unused")
                                    fun onLocationSelected(lat: Double, lng: Double) {
                                        selectedLat = lat; selectedLng = lng
                                    }
                                }, "Android")
                                
                                // noinspection SpellCheckingInspection
                                val mapHtml = """
                                    <!DOCTYPE html><html><head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css"/>
                                    <script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"></script>
                                    <style>#map { height: 100vh; width: 100%; margin: 0; padding: 0; }</style></head>
                                    <body><div id="map"></div><script>
                                    var map = L.map('map').setView([$initialLat, $initialLng], 13);
                                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                        attribution: '&copy; OpenStreetMap'
                                    }).addTo(map);
                                    var marker = L.marker([$initialLat, $initialLng], {draggable: true}).addTo(map);
                                    marker.on('dragend', function(event) {
                                        var position = marker.getLatLng();
                                        Android.onLocationSelected(position.lat, position.lng);
                                    });
                                    map.on('click', function(event) {
                                        var clickLatLng = event.latlng;
                                        marker.setLatLng(clickLatLng);
                                        Android.onLocationSelected(clickLatLng.lat, clickLatLng.lng);
                                    });
                                    </script></body></html>
                                """.trimIndent()
                                loadDataWithBaseURL(null, mapHtml, "text/html", "UTF-8", null)
                            }
                        }, modifier = Modifier.fillMaxSize()
                    )
                }
                
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Selected Address", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onSelect(selectedLat, selectedLng, address) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Text("Confirm This Location")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = null
    )
}
