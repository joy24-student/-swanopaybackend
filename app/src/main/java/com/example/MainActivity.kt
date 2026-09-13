package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.example.ui.AppNavigation
import com.example.ui.AppViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.service.SmsMonitoringService

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private var isRequestingPermission = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isRequestingPermission = false
        val smsReceivedGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        if (smsReceivedGranted) {
            startSmsService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("SwapnoPayCrash", "Fatal uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
        }
        enableEdgeToEdge()

        // Handle deep link callback from Supabase email confirmation or reset links
        handleDeepLinkIntent(intent)

        // Check and request dynamic permissions
        checkAndRequestPermissions()

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            MyApplicationTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        viewModel.handleAuthDeepLink(uri)
    }

    private var isAppInBackground = false

    override fun onStop() {
        super.onStop()
        isAppInBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (isAppInBackground) {
            isAppInBackground = false
            if (!isRequestingPermission && !viewModel.isExternalActivityExpected) {
                viewModel.lockAppOnBackground()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf(
            Manifest.permission.RECEIVE_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startSmsService()
        } else {
            isRequestingPermission = true
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startSmsService() {
        runCatching {
            val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            if (!hasSms) return@runCatching
            val serviceIntent = Intent(this, SmsMonitoringService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }.onFailure {
            android.util.Log.e("MainActivity", "Unable to start SMS monitoring service", it)
        }
    }
}
