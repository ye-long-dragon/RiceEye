package com.example.riceeye

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.riceeye.data.AnalysisResult

class RiceGrainAdapter(
    private val context: Context,
    private var results: List<AnalysisResult>
) : RecyclerView.Adapter<RiceGrainAdapter.RiceGrainViewHolder>() {

    class RiceGrainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgRice: ImageView = itemView.findViewById(R.id.imgRice)
        val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        val btnAnalyze: Button = itemView.findViewById(R.id.btnAnalyzeItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiceGrainViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_rice_grain, parent, false)
        return RiceGrainViewHolder(view)
    }

    override fun onBindViewHolder(holder: RiceGrainViewHolder, position: Int) {
        val item = results[position]

        // Load image preview
        val uri = Uri.parse(item.imageUri)
        context.contentResolver.openInputStream(uri)?.use {
            holder.imgRice.setImageBitmap(BitmapFactory.decodeStream(it))
        }

        // --- Format date nicely ---
        val formattedDate = try {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val formatter = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
            val date = parser.parse(item.date)
            if (date != null) formatter.format(date) else item.date
        } catch (e: Exception) {
            item.date  // fallback if parsing fails
        }

        holder.txtDate.text = formattedDate

        // tap image → enlarge
        holder.imgRice.setOnClickListener {
            val intent = Intent(context, EnlargedImage::class.java)
            intent.putExtra("imageUri", item.imageUri)
            context.startActivity(intent)
        }

        // tap analyze → send to RiceDetectorLoading
        holder.btnAnalyze.setOnClickListener {
            val intent = Intent(context, RiceDetectorLoading::class.java)
            intent.putExtra("imageUri", item.imageUri)
            context.startActivity(intent)
        }

    }


    override fun getItemCount() = results.size

    fun updateData(newResults: List<AnalysisResult>) {
        results = newResults
        notifyDataSetChanged()
    }
}
