package com.gigbudget.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gigbudget.app.ui.DashboardScreen
import com.gigbudget.app.ui.GoalsScreen
import com.gigbudget.app.ui.MilestoneDialog
import com.gigbudget.app.ui.MoneyScreen
import com.gigbudget.app.ui.PlanScreen
import com.gigbudget.app.ui.SettingsScreen
import com.gigbudget.app.ui.theme.GigBudgetTheme

class MainActivity : ComponentActivity() {
    /** Tab requested by a notification tap; consumed by [MainScreen]. */
    private val requestedTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = intent?.getStringExtra(EXTRA_OPEN_TAB)
        setContent {
            GigBudgetTheme {
                MainScreen(requestedTab.value, onTabHandled = { requestedTab.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab.value = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_GOALS = "GOALS"
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    PLAN("Plan", Icons.Filled.PieChart),
    MONEY("Money", Icons.AutoMirrored.Filled.ReceiptLong),
    GOALS("Goals", Icons.Filled.Savings),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(requestedTab: String?, onTabHandled: () -> Unit, vm: BudgetViewModel = viewModel()) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(requestedTab) {
        Tab.entries.firstOrNull { it.name == requestedTab }?.let { tabIndex = it.ordinal }
        if (requestedTab != null) onTabHandled()
    }
    val tab = Tab.entries[tabIndex]
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    if (tab == Tab.HOME) "Gig Budget 💜" else tab.title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            })
        },
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
        val celebration by vm.celebration.collectAsState()
        celebration?.let { MilestoneDialog(it, onDismiss = vm::celebrationShown) }
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> DashboardScreen(
                vm, modifier,
                onOpenSettings = { tabIndex = Tab.SETTINGS.ordinal },
                onOpenPlan = { tabIndex = Tab.PLAN.ordinal },
                onOpenGoals = { tabIndex = Tab.GOALS.ordinal },
            )
            Tab.PLAN -> PlanScreen(vm, modifier)
            Tab.MONEY -> MoneyScreen(vm, modifier)
            Tab.GOALS -> GoalsScreen(vm, modifier)
            Tab.SETTINGS -> SettingsScreen(vm, modifier)
        }
    }
}
