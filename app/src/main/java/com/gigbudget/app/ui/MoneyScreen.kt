package com.gigbudget.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gigbudget.app.BudgetViewModel

/** Spending and income lists in one tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(vm: BudgetViewModel, modifier: Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("🧾 Spending", "💵 Income")
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) { Text(label) }
            }
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) SpendingScreen(vm, Modifier) else IncomeScreen(vm, Modifier)
        }
    }
}
