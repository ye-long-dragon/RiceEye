package com.example.riceeye

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.riceeye.data.AppDatabase
import com.example.riceeye.data.AnalysisResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RiceResult : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rice_result)

        val resultImage: ImageView = findViewById(R.id.resultImage)
        val txtLabel: TextView = findViewById(R.id.txtLabel)
        val txtConfidence: TextView = findViewById(R.id.txtConfidence)
        val btnSave: Button = findViewById(R.id.btnSaveResult)
        val btnRetake: Button = findViewById(R.id.btnRetake)
        val btnReturnHome: Button = findViewById(R.id.btnReturnHome)

        // Get data from intent
        val rawResults = intent.getStringArrayExtra("results")
        val imageUriString = intent.getStringExtra("imageUri")
        val imageUri = imageUriString?.let { Uri.parse(it) }

        // Display the image
        imageUri?.let {
            contentResolver.openInputStream(it)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                resultImage.setImageBitmap(bmp)
            }
        }

        // Display detection details
        if (!rawResults.isNullOrEmpty()) {
            val best = rawResults[0].split(":")
            txtLabel.text = "Detected: ${best.getOrNull(0) ?: "Unknown"}"
            txtConfidence.text = "Confidence: ${best.getOrNull(1)?.trim() ?: "N/A"}%"
        } else {
            txtLabel.text = "No detection result"
            txtConfidence.text = ""
        }

        // ✅ Save Result button – stores result into Room Database
        btnSave.setOnClickListener {
            val label = txtLabel.text.toString().removePrefix("Detected: ").trim()
            val confidence = txtConfidence.text.toString().removePrefix("Confidence: ").trim()
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@RiceResult)
                val dao = db.analysisResultDao()

                val result = AnalysisResult(
                    date = date,
                    imageUri = imageUriString ?: "",
                    result = label,
                    confidenceLevel = confidence
                )

                dao.insert(result)

                runOnUiThread {
                    Toast.makeText(
                        this@RiceResult,
                        "Result saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Retake Photo -> back to RiceDetector
        btnRetake.setOnClickListener {
            val intent = Intent(this, RiceDetector::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // Return to MainActivity
        btnReturnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        fun newIntent(
            context: Context,
            results: List<Pair<String, Float>>,
            imageUri: Uri
        ): Intent {
            return Intent(context, RiceResult::class.java).apply {
                putExtra(
                    "results",
                    results.map { "${it.first}:${"%.2f".format(it.second * 100)}" }.toTypedArray()
                )
                putExtra("imageUri", imageUri.toString())
            }
        }
    }
}
