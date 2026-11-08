package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navigation.AppNavigation
import com.example.ui.theme.DelivioRiderTheme
import com.example.viewmodel.RiderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            DelivioApplication.ensureFirebase(applicationContext)
        } catch (_: Throwable) {
            // Safety guard
        }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val app = context.applicationContext as Application
            val riderViewModel: RiderViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        DelivioApplication.ensureFirebase(app)
                        return RiderViewModel(app) as T
                    }
                }
            )
            val isDarkMode by riderViewModel.isDarkMode.collectAsState()
            val toastMessage by riderViewModel.toastMessage.collectAsState()

            LaunchedEffect(toastMessage) {
                toastMessage?.let { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    riderViewModel.clearToast()
                }
            }

            DelivioRiderTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = riderViewModel)
                }
            }
        }
    }
}
