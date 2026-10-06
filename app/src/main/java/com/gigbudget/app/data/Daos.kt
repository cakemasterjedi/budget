package com.gigbudget.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income ORDER BY date DESC")
    suspend fun list(): List<Income>

    @Query("SELECT * FROM income ORDER BY date DESC")
    fun all(): Flow<List<Income>>

    /** Returns -1 when an auto-imported entry with the same dedupeKey already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(income: Income): Long

    /** Auto-imported income of [source] since [since] that came from an app set to [role]. */
    @Query(
        """SELECT COUNT(*) FROM income WHERE auto = 1 AND source = :source AND date >= :since
           AND sourcePackage IN (SELECT packageName FROM watched_apps WHERE role = :role)"""
    )
    suspend fun countAutoFromRole(source: String, role: String, since: Long): Int

    @Update
    suspend fun update(income: Income)

    @Delete
    suspend fun delete(income: Income)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun list(): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun all(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense): Long

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): Expense?

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate, name")
    fun all(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: Long): SavingsGoal?

    @Insert
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)
}

@Dao
interface WatchedAppDao {
    @Query("SELECT * FROM watched_apps ORDER BY CASE WHEN role = 'IGNORE' THEN 1 ELSE 0 END, lastSeen DESC, label")
    fun all(): Flow<List<WatchedApp>>

    @Query("SELECT * FROM watched_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): WatchedApp?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(app: WatchedApp)

    @Update
    suspend fun update(app: WatchedApp)
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_log ORDER BY postedAt DESC LIMIT 300")
    fun recent(): Flow<List<NotificationLog>>

    @Query("SELECT EXISTS(SELECT 1 FROM notification_log WHERE dedupeKey = :key)")
    suspend fun exists(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: NotificationLog): Long

    @Update
    suspend fun update(entry: NotificationLog)

    @Query("DELETE FROM notification_log WHERE id NOT IN (SELECT id FROM notification_log ORDER BY postedAt DESC LIMIT 300)")
    suspend fun prune()

    @Query("DELETE FROM notification_log")
    suspend fun clear()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY name")
    suspend fun list(): List<Debt>

    @Query("SELECT * FROM debts ORDER BY balanceCents = 0, name")
    fun all(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: Long): Debt?

    @Insert
    suspend fun insert(debt: Debt): Long

    @Update
    suspend fun update(debt: Debt)

    @Delete
    suspend fun delete(debt: Debt)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY dueDay")
    suspend fun list(): List<Bill>

    @Query("SELECT * FROM bills ORDER BY dueDay, name")
    fun all(): Flow<List<Bill>>

    @Insert
    suspend fun insert(bill: Bill): Long

    @Update
    suspend fun update(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)
}
