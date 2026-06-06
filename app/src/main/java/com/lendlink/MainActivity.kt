package com.lendlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.lendlink.navigation.NavGraph
import com.lendlink.ui.theme.LendLinkTheme
import com.lendlink.viewmodel.*
import com.lendlink.worker.WorkerScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize Splash Screen before super.onCreate
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule background workers (penalty & deadline checks)
        WorkerScheduler.schedule(this)

        setContent {
            LendLinkTheme {
                val nav = rememberNavController()
                
                // ViewModels
                val authVm: AuthViewModel = viewModel()
                val lenderVm: LenderViewModel = viewModel()
                val borrowerVm: BorrowerViewModel = viewModel()

                // Logic to handle "Login Everytime" requirement
                // We logout only on fresh start to avoid annoying logout on rotation
                LaunchedEffect(Unit) {
                    if (savedInstanceState == null) {
                        authVm.logout()
                        lenderVm.clearData()
                        borrowerVm.clearData()
                    }
                }

                NavGraph(
                    navController = nav,
                    authVm = authVm,
                    lenderVm = lenderVm,
                    borrowerVm = borrowerVm
                )
            }
        }
    }
}
