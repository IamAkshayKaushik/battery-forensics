package com.batteryforensics.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint
import com.batteryforensics.app.ui.navigation.BatteryForensicsNavHost
import com.batteryforensics.app.ui.theme.BatteryForensicsTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BatteryForensicsAppRoot()
        }
    }
}

@Composable
private fun BatteryForensicsAppRoot() {
    BatteryForensicsTheme(darkTheme = isSystemInDarkTheme()) {
        BatteryForensicsNavHost()
    }
}
