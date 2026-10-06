package com.gigbudget.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gigbudget.app.ui.DashboardScreen
import com.gigbudget.app.ui.GoalsScreen
import com.gigbudget.app.ui.IncomeScreen
import com.gigbudget.app.ui.SettingsScreen
import com.gigbudget.app.ui.SpendingScreen
import com.gigbudget.app.ui.theme.GigBudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GigBudgetTheme {
                MainScreen()
            }
        }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Dashboard),
    INCOME("Income", Icons.Filled.Payments),
    SPENDING("Spending", Icons.Filled.ShoppingCart),
    GOALS("Savings", Icons.Filled.Savings),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: BudgetViewModel = viewModel()) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == Tab.HOME) "Gig Budget" else tab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> DashboardScreen(vm, modifier, onOpenSettings = { tabIndex = Tab.SETTINGS.ordinal })
            Tab.INCOME -> IncomeScreen(vm, modifier)
            Tab.SPENDING -> SpendingScreen(vm, modifier)
            Tab.GOALS -> GoalsScreen(vm, modifier)
            Tab.SETTINGS -> SettingsScreen(vm, modifier)
        }
    }
}
