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
    fun all(): Flow<List<Income>>

    /** Returns -1 when an auto-imported entry with the same dedupeKey already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(income: Income): Long

    @Update
    suspend fun update(income: Income)

    @Delete
    suspend fun delete(income: Income)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun all(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate, name")
    fun all(): Flow<List<SavingsGoal>>

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
