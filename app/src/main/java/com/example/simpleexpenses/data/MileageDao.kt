package com.example.simpleexpenses.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MileageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MileageEntry): Long

    @Query("DELETE FROM mileage_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM mileage_entries ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<MileageEntry>>

    @Query("""
        SELECT * FROM mileage_entries
        WHERE date BETWEEN :from AND :to
        ORDER BY date DESC, id DESC
    """)
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MileageEntry>>

    @Query("""
        SELECT COALESCE(SUM(amountPence), 0)
        FROM mileage_entries
        WHERE date BETWEEN :from AND :to
    """)
    fun observeTotalPenceInRange(from: LocalDate, to: LocalDate): Flow<Int>
}