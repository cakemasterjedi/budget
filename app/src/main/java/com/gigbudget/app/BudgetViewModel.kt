package com.gigbudget.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gigbudget.app.data.Bill
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Debt
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.GoalCategory
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.NotificationLog
import com.gigbudget.app.data.SavingsGoal
import com.gigbudget.app.data.Settings
import com.gigbudget.app.data.WatchedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val debts = db.debtDao().all().asState()
    val bills = db.billDao().all().asState()
    val watchedApps = db.watchedAppDao().all().asState()
    val notificationLog = db.notificationLogDao().recent().asState()
    val settings: StateFlow<Settings> = budgetApp.settings.state

    /** A goal milestone to celebrate (25 / 50 / 75 / 100%), shown once then cleared. */
    data class Celebration(val goalName: String, val emoji: String, val percent: Int)
    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()
    fun celebrationShown() { _celebration.value = null }

    fun saveIncome(income: Income) = viewModelScope.launch {
        if (income.id == 0L) db.incomeDao().insert(income) else db.incomeDao().update(income)
    }

    fun deleteIncome(income: Income) = viewModelScope.launch { db.incomeDao().delete(income) }

    fun saveExpense(expense: Expense) = viewModelScope.launch {
        if (expense.id == 0L) {
            db.expenseDao().insert(expense)
        } else {
            val old = db.expenseDao().get(expense.id)
            db.expenseDao().update(expense)
            // Editing a goal deposit or debt payment moves the goal / debt by the difference.
            if (old != null) applyLinked(expense, expense.amountCents - old.amountCents)
        }
        when (expense.category) {
            Categories.BOTTLE -> updateSettings { it.copy(lastBottlePriceCents = expense.amountCents) }
            Categories.PREROLL -> updateSettings { it.copy(lastPrerollPriceCents = expense.amountCents) }
        }
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        db.expenseDao().delete(expense)
        applyLinked(expense, -expense.amountCents)
    }

    private suspend fun applyLinked(expense: Expense, deltaCents: Long) {
        if (deltaCents == 0L) return
        expense.goalId?.let { id ->
            db.goalDao().get(id)?.let { db.goalDao().update(it.copy(savedCents = (it.savedCents + deltaCents).coerceAtLeast(0))) }
        }
        expense.debtId?.let { id ->
            db.debtDao().get(id)?.let { db.debtDao().update(it.copy(balanceCents = (it.balanceCents - deltaCents).coerceAtLeast(0))) }
        }
    }

    fun saveGoal(goal: SavingsGoal) = viewModelScope.launch {
        if (goal.id == 0L) db.goalDao().insert(goal) else db.goalDao().update(goal)
    }

    fun deleteGoal(goal: SavingsGoal) = viewModelScope.launch { db.goalDao().delete(goal) }

    /**
     * Positive moves money into a goal and records it as savings for this month's plan.
     * Negative takes money out of the goal.
     */
    fun adjustGoal(goal: SavingsGoal, deltaCents: Long) = viewModelScope.launch {
        val newSaved = (goal.savedCents + deltaCents).coerceAtLeast(0)
        db.goalDao().update(goal.copy(savedCents = newSaved))
        BudgetMath.crossedMilestone(goal.savedCents, newSaved, goal.targetCents)?.let { pct ->
            _celebration.value = Celebration(goal.name, GoalCategory.of(goal.category).emoji, pct)
        }
        if (deltaCents > 0) {
            db.expenseDao().insert(
                Expense(category = Categories.SAVINGS, amountCents = deltaCents, date = System.currentTimeMillis(),
                    note = "Saved for ${goal.name}", goalId = goal.id)
            )
        }
    }

    fun saveDebt(debt: Debt) = viewModelScope.launch {
        if (debt.id == 0L) db.debtDao().insert(debt) else db.debtDao().update(debt)
    }

    fun deleteDebt(debt: Debt) = viewModelScope.launch { db.debtDao().delete(debt) }

    fun payDebt(debt: Debt, cents: Long) = viewModelScope.launch {
        db.expenseDao().insert(
            Expense(category = Categories.DEBT, amountCents = cents, date = System.currentTimeMillis(),
                note = "Payment: ${debt.name}", debtId = debt.id)
        )
        db.debtDao().update(debt.copy(balanceCents = (debt.balanceCents - cents).coerceAtLeast(0)))
    }

    fun saveBill(bill: Bill) = viewModelScope.launch {
        if (bill.id == 0L) db.billDao().insert(bill) else db.billDao().update(bill)
    }

    fun deleteBill(bill: Bill) = viewModelScope.launch { db.billDao().delete(bill) }

    fun payBill(bill: Bill, cents: Long) = viewModelScope.launch {
        db.expenseDao().insert(
            Expense(category = bill.category, amountCents = cents, date = System.currentTimeMillis(),
                note = bill.name, billId = bill.id)
        )
    }

    fun setAppRole(app: WatchedApp, role: String) = viewModelScope.launch {
        db.watchedAppDao().update(app.copy(role = role))
    }

    /** After adding a skipped notification by hand, show it as handled in the log. */
    fun markLogHandled(entry: NotificationLog, outcome: String, detail: String) = viewModelScope.launch {
        db.notificationLogDao().update(entry.copy(outcome = outcome, detail = detail))
    }

    fun clearNotificationLog() = viewModelScope.launch { db.notificationLogDao().clear() }

    fun updateSettings(transform: (Settings) -> Settings) = budgetApp.settings.update(transform)

    private fun <T> Flow<List<T>>.asState(): StateFlow<List<T>> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
