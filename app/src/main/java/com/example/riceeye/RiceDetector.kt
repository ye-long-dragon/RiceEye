package com.example.riceeye

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.example.riceeye.data.AppDatabase
import com.example.riceeye.data.AnalysisResult
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RiceDetector : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var btnCapture: Button
    private lateinit var btnSelect: Button
    private lateinit var btnAnalyze: Button
    private lateinit var btnSave: Button

    private var selectedImageUri: Uri? = null
    private var photoUri: Uri? = null
    private var currentPhotoPath: String? = null

    private val REQUEST_CAMERA = 1001
    private val REQUEST_GALLERY = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rice_detector)

        imagePreview = findViewById(R.id.imagePreview)
        btnCapture = findViewById(R.id.btnCapture)
        btnSelect = findViewById(R.id.btnSelect)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnSave = findViewById(R.id.btnSave)

        btnCapture.setOnClickListener { capturePhoto() }
        btnSelect.setOnClickListener { selectFromGallery() }
        btnAnalyze.setOnClickListener { analyzeImage() }
        btnSave.setOnClickListener { saveImage() }   // ✅ Save to database
        val btnBack: Button = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            // Return to previous screen (MainActivity)
            finish()
        }

    }

    // ----------------------------------------------------------------------
    // Camera & Gallery
    // ----------------------------------------------------------------------

    private fun capturePhoto() {
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "captured_image.jpg")
        currentPhotoPath = photoFile.absolutePath
        photoUri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    private fun selectFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    // ----------------------------------------------------------------------
    // Handle Image Results
    // ----------------------------------------------------------------------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CAMERA -> {
                    currentPhotoPath?.let { path ->
                        val bitmap = BitmapFactory.decodeFile(path)
                        val rotatedBitmap = fixImageOrientation(path, bitmap)
                        imagePreview.setImageBitmap(rotatedBitmap)

                        val rotatedFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "captured_rotated.jpg")
                        saveBitmapToFile(rotatedBitmap, rotatedFile)

                        selectedImageUri = FileProvider.getUriForFile(this, "$packageName.provider", rotatedFile)
                    }
                }
                REQUEST_GALLERY -> {
                    val uri = data?.data ?: return
                    val rotatedBitmap = fixImageOrientationFromUri(uri)
                    imagePreview.setImageBitmap(rotatedBitmap)

                    val rotatedFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "gallery_rotated.jpg")
                    saveBitmapToFile(rotatedBitmap, rotatedFile)

                    selectedImageUri = FileProvider.getUriForFile(this, "$packageName.provider", rotatedFile)
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Orientation Helpers
    // ----------------------------------------------------------------------

    private fun fixImageOrientation(photoPath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(photoPath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: IOException) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun fixImageOrientationFromUri(uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        val tempFile = File.createTempFile("temp_image", ".jpg", cacheDir)
        saveBitmapToFile(bitmap, tempFile)
        return fixImageOrientation(tempFile.absolutePath, bitmap)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // ----------------------------------------------------------------------
    // Actions
    // ----------------------------------------------------------------------

    private fun analyzeImage() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select or capture an image first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, RiceDetectorLoading::class.java)
        intent.putExtra("imageUri", selectedImageUri.toString())
        startActivity(intent)
    }

    // ✅ Save the selected image URI into Room database
    private fun saveImage() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "No image to save!", Toast.LENGTH_SHORT).show()
            return
        }

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@RiceDetector)
            val dao = db.analysisResultDao()

            // Save with placeholders for result and confidence (since analysis not done yet)
            val result = AnalysisResult(
                date = date,
                imageUri = selectedImageUri.toString(),
                result = "Unanalyzed Image",
                confidenceLevel = "N/A"
            )

            dao.insert(result)

            runOnUiThread {
                Toast.makeText(
                    this@RiceDetector,
                    "Image saved to database!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
