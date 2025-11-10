package com.example.riceeye

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class EnlargedImage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enlarged_image)

        val imgView: ImageView = findViewById(R.id.fullImage)
        val imageUriString = intent.getStringExtra("imageUri")

        imageUriString?.let {
            val uri = Uri.parse(it)
            contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                imgView.setImageBitmap(bmp)
            }
        }

        // click anywhere to close
        imgView.setOnClickListener { finish() }
    }
}
