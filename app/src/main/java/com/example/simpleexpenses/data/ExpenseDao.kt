package com.example.simpleexpenses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // ----- Inserts / updates -----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ----- Reads -----
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun all(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Expense?

    // ✅ New: get all Paid expenses (one-shot list)
    @Query("SELECT * FROM expenses WHERE status = 'Paid' ORDER BY timestamp DESC")
    suspend fun getPaidExpenses(): List<Expense>

    // ✅ New: merchant suggestions (prefix match, distinct)
    @Query("""
        SELECT DISTINCT merchant 
        FROM expenses 
        WHERE merchant LIKE :prefix || '%' 
        ORDER BY merchant ASC
    """)
    suspend fun suggestMerchants(prefix: String): List<String>

    // ----- Flexible filters -----
    @Query("""
        SELECT * FROM expenses
        WHERE (:category IS NULL OR category = :category)
          AND (:status IS NULL OR status = :status)
          AND (:fromDate IS NULL OR timestamp >= :fromDate)
          AND (:toDate IS NULL OR timestamp <= :toDate)
          AND (:hasReceipt IS NULL OR hasReceipt = :hasReceipt)
        ORDER BY timestamp DESC
    """)

    fun filtered(
        category: String?,
        status: ExpenseStatus?,
        fromDate: Long?,
        toDate: Long?,
        hasReceipt: Boolean?
    ): Flow<List<Expense>>

    @Query("""
    SELECT * FROM expenses
    WHERE (:category IS NULL OR category = :category)
      AND (:status IS NULL OR status = :status)
      AND (:fromDate IS NULL OR timestamp >= :fromDate)
      AND (:toDate IS NULL OR timestamp <= :toDate)
      AND (:hasReceipt IS NULL OR hasReceipt = :hasReceipt)
    ORDER BY timestamp DESC
""")
    suspend fun getFilteredOnce(
        category: String?,
        status: ExpenseStatus?,
        fromDate: Long?,
        toDate: Long?,
        hasReceipt: Boolean?
    ): List<Expense>

    @Query("UPDATE expenses SET receiptUri = :uri, hasReceipt = 1 WHERE id = :id")
    suspend fun updateReceiptUri(id: Long, uri: String?)

    @Query("UPDATE expenses SET receiptUri = NULL, hasReceipt = 0 WHERE id = :id")
    suspend fun clearReceiptUri(id: Long)
}