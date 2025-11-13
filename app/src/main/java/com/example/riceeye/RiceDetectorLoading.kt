package com.example.riceeye

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class RiceDetectorLoading : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var backgroundImage: ImageView
    private lateinit var interpreter: Interpreter

    private val classNames = listOf("Dinorado", "Jasmine", "Malagkit", "Sinadomeng", "V160")
    private val inputSize = 224

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rice_detector_loading)

        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        backgroundImage = findViewById(R.id.loadingBackground)

        val imageUriString = intent.getStringExtra("imageUri") ?: return
        val imageUri = Uri.parse(imageUriString)

        contentResolver.openInputStream(imageUri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)
            backgroundImage.setImageBitmap(bitmap)
        }

        lifecycleScope.launch {
            try {
                loadingText.text = "Initializing model..."
                withContext(Dispatchers.IO) {
                    interpreter = Interpreter(loadModelFile("rice_model.tflite"))
                }

                loadingText.text = "Analyzing image..."
                progressBar.isIndeterminate = true

                val (label, topConf, allScores) = runClassificationFromBitmap(imageUri)

                loadingText.text =
                    "Detected: $label\nConfidence: ${"%.2f".format(topConf * 100)}%"

                // Launch results screen
                startActivity(
                    RiceResult.newIntentWithConfidences(
                        this@RiceDetectorLoading,
                        label,
                        topConf,
                        allScores,
                        imageUri
                    )
                )
                finish()
            } catch (e: Exception) {
                android.util.Log.e("RiceEye", "Error running model", e)
                loadingText.text = "Model error: ${e.message}"
            }
        }
    }

    private suspend fun runClassificationFromBitmap(imageUri: Uri)
            : Triple<String, Float, FloatArray> = withContext(Dispatchers.Default) {

        val bitmap = contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalStateException("Failed to load image")

        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Prepare input tensor
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = scaled.getPixel(x, y)
                inputBuffer.putFloat(((px shr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((px shr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((px and 0xFF) / 255f)
            }
        }

        val output = Array(1) { FloatArray(classNames.size) }
        interpreter.run(inputBuffer, output)
        val scores = output[0]

        var bestIdx = 0
        var bestScore = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestIdx = i
            }
        }

        Triple(classNames[bestIdx], bestScore, scores)
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val afd = assets.openFd(modelName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val channel = fis.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    override fun onDestroy() {
        if (::interpreter.isInitialized) interpreter.close()
        super.onDestroy()
    }
}
