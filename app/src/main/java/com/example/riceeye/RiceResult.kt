package com.example.riceeye

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.riceeye.data.AppDatabase
import com.example.riceeye.data.AnalysisResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RiceResult : AppCompatActivity() {

    private lateinit var resultImage: ImageView
    private lateinit var txtLabel: TextView
    private lateinit var txtConfidence: TextView
    private lateinit var txtAllScores: TextView
    private lateinit var btnSave: Button
    private lateinit var btnRetake: Button
    private lateinit var btnReturnHome: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rice_result)

        // Initialize views
        resultImage = findViewById(R.id.resultImage)
        txtLabel = findViewById(R.id.txtLabel)
        txtConfidence = findViewById(R.id.txtConfidence)
        txtAllScores = findViewById(R.id.txtAllScores)
        btnSave = findViewById(R.id.btnSaveResult)
        btnRetake = findViewById(R.id.btnRetake)
        btnReturnHome = findViewById(R.id.btnReturnHome)

        // -------------------------------------------------------
        // Retrieve and display results
        // -------------------------------------------------------
        val imageUriString = intent.getStringExtra("imageUri")
        val label = intent.getStringExtra("label") ?: "Unknown"
        val confidence = intent.getFloatExtra("confidence", 0f)
        val allScores = intent.getFloatArrayExtra("allScores")

        // Display image from URI
        imageUriString?.let { path ->
            val uri = Uri.parse(path)
            contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                resultImage.setImageBitmap(bmp)
            }
        }

        // Display main label and top confidence
        txtLabel.text = "Detected: $label"
        txtConfidence.text = "Confidence: ${"%.2f".format(confidence * 100)}%"

        // Ensure labels match model's class list from RiceDetectorLoading
        val classNames = listOf("Dinorado", "Jasmine", "Malagkit", "Sinadomeng", "V160")

        // Show all confidences neatly
        if (allScores != null && allScores.size == classNames.size) {
            val details = buildString {
                append("All Confidence Levels:\n\n")
                for (i in classNames.indices) {
                    append("${classNames[i]}: ${"%.2f".format(allScores[i] * 100)}%\n")
                }
            }
            txtAllScores.text = details
        } else {
            txtAllScores.text = "Confidence data unavailable."
        }

        // -------------------------------------------------------
        // Save result to Room Database
        // -------------------------------------------------------
        btnSave.setOnClickListener {
            val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@RiceResult)
                val dao = db.analysisResultDao()

                val record = AnalysisResult(
                    date = currentDate,
                    imageUri = imageUriString ?: "",
                    result = label,
                    confidenceLevel = String.format("%.2f%%", confidence * 100)
                )

                dao.insert(record)
                runOnUiThread {
                    Toast.makeText(this@RiceResult, "Result saved successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // -------------------------------------------------------
        // Navigation buttons
        // -------------------------------------------------------
        btnRetake.setOnClickListener {
            val intent = Intent(this, RiceDetector::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        btnReturnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        /**
         * Helper method to start RiceResult with full confidence data.
         */
        fun newIntentWithConfidences(
            context: Context,
            label: String,
            confidence: Float,
            allScores: FloatArray,
            imageUri: Uri
        ): Intent {
            return Intent(context, RiceResult::class.java).apply {
                putExtra("label", label)
                putExtra("confidence", confidence)
                putExtra("allScores", allScores)
                putExtra("imageUri", imageUri.toString())
            }
        }
    }
}
