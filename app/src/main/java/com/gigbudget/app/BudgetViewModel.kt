package com.gigbudget.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.SavingsGoal
import com.gigbudget.app.data.Settings
import com.gigbudget.app.data.WatchedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(app: Application) : AndroidViewModel(app) {
    private val budgetApp = app as BudgetApp
    private val db = budgetApp.db

    val incomes = db.incomeDao().all().asState()
    val expenses = db.expenseDao().all().asState()
    val goals = db.goalDao().all().asState()
    val watchedApps = db.watchedAppDao().all().asState()
    val settings: StateFlow<Settings> = budgetApp.settings.state

    fun saveIncome(income: Income) = viewModelScope.launch {
        if (income.id == 0L) db.incomeDao().insert(income) else db.incomeDao().update(income)
    }

    fun deleteIncome(income: Income) = viewModelScope.launch { db.incomeDao().delete(income) }

    fun saveExpense(expense: Expense) = viewModelScope.launch {
        if (expense.id == 0L) db.expenseDao().insert(expense) else db.expenseDao().update(expense)
        when (expense.category) {
            Categories.BOTTLE -> updateSettings { it.copy(lastBottlePriceCents = expense.amountCents) }
            Categories.PREROLL -> updateSettings { it.copy(lastPrerollPriceCents = expense.amountCents) }
        }
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch { db.expenseDao().delete(expense) }

    fun saveGoal(goal: SavingsGoal) = viewModelScope.launch {
        if (goal.id == 0L) db.goalDao().insert(goal) else db.goalDao().update(goal)
    }

    fun deleteGoal(goal: SavingsGoal) = viewModelScope.launch { db.goalDao().delete(goal) }

    /** Positive to add money to a goal, negative to take it out. */
    fun adjustGoal(goal: SavingsGoal, deltaCents: Long) = viewModelScope.launch {
        db.goalDao().update(goal.copy(savedCents = (goal.savedCents + deltaCents).coerceAtLeast(0)))
    }

    fun setAppRole(app: WatchedApp, role: String) = viewModelScope.launch {
        db.watchedAppDao().update(app.copy(role = role))
    }

    fun updateSettings(transform: (Settings) -> Settings) = budgetApp.settings.update(transform)

    private fun <T> Flow<List<T>>.asState(): StateFlow<List<T>> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
