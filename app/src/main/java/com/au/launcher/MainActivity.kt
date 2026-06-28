package com.au.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.au.launcher.ui.screens.HomeScreen
import com.au.launcher.ui.screens.ImportScreen
import com.au.launcher.ui.screens.SettingsScreen
import com.au.launcher.ui.screens.UploadScreen
import com.au.launcher.ui.theme.AULauncherTheme
import com.au.launcher.utils.SoundHelper
import com.au.launcher.viewmodel.GameViewModel
import com.au.launcher.viewmodel.SettingsViewModel

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundHelper.init(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            AULauncherTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundHelper.release()
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = viewModel()
    val gameViewModel: GameViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = gameViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToUpload = { navController.navigate("upload") },
                onNavigateToImport = { navController.navigate("import") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("upload") {
            UploadScreen(onBack = { navController.popBackStack() })
        }
        composable("import") {
            ImportScreen(onBack = { 
                gameViewModel.refreshGames()
                navController.popBackStack() 
            })
        }
    }
}
