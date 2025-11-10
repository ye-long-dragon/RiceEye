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
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

class RiceDetectorLoading : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var backgroundImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rice_detector_loading)

        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        backgroundImage = findViewById(R.id.loadingBackground)

        val imageUriString = intent.getStringExtra("imageUri") ?: return
        val imageUri = Uri.parse(imageUriString)

        // Show selected image in background
        contentResolver.openInputStream(imageUri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)
            backgroundImage.setImageBitmap(bitmap)

            lifecycleScope.launch {
                try {
                    loadingText.text = "Analyzing..."
                    val results = runModel(bitmap)
                    loadingText.text = "Analysis complete!"

                    // Go to RiceResult screen
                    startActivity(
                        RiceResult.newIntent(
                            this@RiceDetectorLoading,
                            results,
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
    }

    // ------------------------- TensorFlow Lite Model -------------------------
    private suspend fun runModel(bitmap: Bitmap): List<Pair<String, Float>> =
        withContext(Dispatchers.Default) {
            // Load model file from assets
            val modelFile = loadModelFile("best_float32.tflite")
            val interpreter = Interpreter(modelFile)

            // Prepare input data
            val inputSize = 640
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

            val inputData = Array(1) {
                Array(inputSize) {
                    Array(inputSize) {
                        FloatArray(3)
                    }
                }
            }

            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val px = scaledBitmap.getPixel(x, y)
                    inputData[0][y][x][0] = ((px shr 16) and 0xFF) / 255f
                    inputData[0][y][x][1] = ((px shr 8) and 0xFF) / 255f
                    inputData[0][y][x][2] = (px and 0xFF) / 255f
                }
            }

            // Prepare output tensor [1, N, 6]
            val output = Array(1) { Array(300) { FloatArray(6) } }


            // Run inference
            interpreter.run(inputData, output)

            // Decode: (xmin, ymin, xmax, ymax, conf, class_id)
            val names = listOf("Jasmine", "Sinandomeng", "Malagkit", "Dinorado", "V-160")
            val confThres = 0.25f
            val detections = mutableListOf<Pair<String, Float>>()

            for (det in output[0]) {
                val conf = det[4]
                val clsId = det[5]
                if (conf < confThres) continue
                val label = names.getOrElse(clsId.roundToInt()) { "cls${clsId.roundToInt()}" }
                detections.add(label to conf)
            }

            interpreter.close()
            detections
        }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd(modelName)
        FileInputStream(assetFileDescriptor.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
        }
    }


}
