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
import java.util.*

class RiceDetector : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var btnCapture: Button
    private lateinit var btnSelect: Button
    private lateinit var btnAnalyze: Button
    private lateinit var btnSave: Button
    private lateinit var btnBack: Button

    private var selectedImageUri: Uri? = null
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
        btnBack = findViewById(R.id.btnBack)

        btnCapture.setOnClickListener { capturePhoto() }
        btnSelect.setOnClickListener { selectFromGallery() }
        btnAnalyze.setOnClickListener { analyzeImage() }
        btnSave.setOnClickListener { saveImageToDatabase() }
        btnBack.setOnClickListener { finish() }
    }

    // ----------------------------------------------------------------------
    // Camera & Gallery Selection
    // ----------------------------------------------------------------------

    private fun capturePhoto() {
        val photoFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "captured_image_${System.currentTimeMillis()}.jpg"
        )
        currentPhotoPath = photoFile.absolutePath
        val photoUri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            photoFile
        )

        val camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        camIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        camIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(camIntent, REQUEST_CAMERA)
    }

    private fun selectFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    // ----------------------------------------------------------------------
    // Handle image result callbacks
    // ----------------------------------------------------------------------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_CAMERA -> currentPhotoPath?.let { path ->
                val bitmap = BitmapFactory.decodeFile(path)
                val rotated = fixOrientation(path, bitmap)
                displayAndCache(rotated, "camera_rotated_${System.currentTimeMillis()}.jpg")
            }

            REQUEST_GALLERY -> {
                val uri = data?.data ?: return
                val rotated = fixOrientationFromUri(uri)
                displayAndCache(rotated, "gallery_rotated_${System.currentTimeMillis()}.jpg")
            }
        }
    }

    private fun displayAndCache(bitmap: Bitmap, fileName: String) {
        imagePreview.setImageBitmap(bitmap)
        val outFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
        saveBitmap(bitmap, outFile)
        selectedImageUri = FileProvider.getUriForFile(this, "$packageName.provider", outFile)
    }

    // ----------------------------------------------------------------------
    // Orientation & Save Helpers
    // ----------------------------------------------------------------------

    private fun fixOrientation(path: String, bmp: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (e: Exception) {
            bmp
        }
    }

    private fun fixOrientationFromUri(uri: Uri): Bitmap {
        val input = contentResolver.openInputStream(uri)
        val bmp = BitmapFactory.decodeStream(input)
        input?.close()
        val tmp = File.createTempFile("temp_img", ".jpg", cacheDir)
        saveBitmap(bmp, tmp)
        return fixOrientation(tmp.absolutePath, bmp)
    }

    private fun saveBitmap(bmp: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // ----------------------------------------------------------------------
    // Analyze & Save
    // ----------------------------------------------------------------------

    private fun analyzeImage() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select or capture an image first.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, RiceDetectorLoading::class.java)
        intent.putExtra("imageUri", selectedImageUri.toString())
        startActivity(intent)
    }

    private fun saveImageToDatabase() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "No image to save!", Toast.LENGTH_SHORT).show()
            return
        }

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@RiceDetector)
            val dao = db.analysisResultDao()

            val record = AnalysisResult(
                date = date,
                imageUri = selectedImageUri.toString(),
                result = "Unanalyzed Image",
                confidenceLevel = "N/A"
            )
            dao.insert(record)

            runOnUiThread {
                Toast.makeText(this@RiceDetector, "Image saved successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
