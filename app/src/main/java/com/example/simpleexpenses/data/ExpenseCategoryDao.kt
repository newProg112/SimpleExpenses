package com.example.simpleexpenses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseCategoryDao {

    @Query("SELECT * FROM expense_categories WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ExpenseCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: ExpenseCategory): Long

    @Delete
    suspend fun delete(category: ExpenseCategory)

    @Query("SELECT COUNT(*) FROM expense_categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<ExpenseCategory>)

    @Update
    suspend fun update(category: ExpenseCategory)

    @Query("SELECT * FROM expense_categories WHERE isActive = 1 ORDER BY sortOrder ASC")
    suspend fun getActiveOrderedOnce(): List<ExpenseCategory>

}