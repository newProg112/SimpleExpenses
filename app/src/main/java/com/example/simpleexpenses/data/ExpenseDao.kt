package com.example.simpleexpenses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Expense>>

    @Insert
    suspend fun insert(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses WHERE status = 'Paid'")
    suspend fun getPaidExpenses(): List<Expense>

    @Query("""
        SELECT DISTINCT merchant
        FROM expenses
        WHERE merchant IS NOT NULL AND merchant != '' AND merchant LIKE :prefix || '%'
        ORDER BY merchant
        LIMIT 10
    """)
    suspend fun suggestMerchants(prefix: String): List<String>

    // Optional (for dynamic category lists later)
    @Query("SELECT DISTINCT category FROM expenses ORDER BY category")
    suspend fun allCategories(): List<String>
}