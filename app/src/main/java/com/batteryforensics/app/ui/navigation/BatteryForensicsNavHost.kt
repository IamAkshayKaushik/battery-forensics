package com.batteryforensics.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.batteryforensics.app.ui.screens.ChemistryScreen
import com.batteryforensics.app.ui.screens.DiagnosticsScreen
import com.batteryforensics.app.ui.screens.ExportScreen
import com.batteryforensics.app.ui.screens.HomeScreen
import com.batteryforensics.app.ui.screens.LiveMonitorScreen
import com.batteryforensics.app.ui.screens.NetworkScreen
import com.batteryforensics.app.ui.screens.SettingsScreen
import com.batteryforensics.app.ui.screens.ThermalScreen
import com.batteryforensics.app.ui.screens.TimelineScreen

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean = false,
) {
    Home("home", "Investigate", Icons.Outlined.Home, showInBottomBar = true),
    LiveMonitor("live", "Live", Icons.Outlined.BatteryChargingFull, showInBottomBar = true),
    Timeline("timeline", "Timeline", Icons.Outlined.Timeline, showInBottomBar = true),
    Diagnostics("diagnostics", "Causes", Icons.Outlined.TravelExplore, showInBottomBar = true),
    Settings("settings", "Settings", Icons.Outlined.Settings, showInBottomBar = true),
    Chemistry("chemistry", "Chemistry", Icons.Outlined.Biotech),
    Thermal("thermal", "Thermal", Icons.Outlined.TravelExplore),
    Network("network", "Network", Icons.Outlined.TravelExplore),
    Export("export", "Export", Icons.Outlined.TravelExplore),
}

@Composable
fun BatteryForensicsNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val bottomDestinations = AppDestination.entries.filter { it.showInBottomBar }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomDestinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    onOpenChemistry = { navController.navigate(AppDestination.Chemistry.route) },
                    onOpenThermal = { navController.navigate(AppDestination.Thermal.route) },
                    onOpenNetwork = { navController.navigate(AppDestination.Network.route) },
                    onOpenExport = { navController.navigate(AppDestination.Export.route) },
                )
            }
            composable(AppDestination.LiveMonitor.route) { LiveMonitorScreen() }
            composable(AppDestination.Timeline.route) { TimelineScreen() }
            composable(AppDestination.Diagnostics.route) { DiagnosticsScreen() }
            composable(AppDestination.Settings.route) { SettingsScreen() }
            composable(AppDestination.Chemistry.route) { ChemistryScreen() }
            composable(AppDestination.Thermal.route) { ThermalScreen() }
            composable(AppDestination.Network.route) { NetworkScreen() }
            composable(AppDestination.Export.route) { ExportScreen() }
        }
    }
}
