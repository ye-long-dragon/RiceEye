// 📁 app/src/main/java/com/example/riceeye/data/AnalysisResult.kt
package com.example.riceeye.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_results")
data class AnalysisResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val imageUri: String,
    val result: String,
    val confidenceLevel: String
)
