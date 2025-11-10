package com.example.riceeye.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AnalysisResultDao {
    @Insert
    suspend fun insert(result: AnalysisResult)

    @Query("SELECT * FROM analysis_results ORDER BY id DESC")
    suspend fun getAll(): List<AnalysisResult>

    @Query("DELETE FROM analysis_results")
    suspend fun deleteAll()
}
