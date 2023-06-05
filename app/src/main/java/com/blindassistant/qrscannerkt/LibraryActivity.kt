package com.blindassistant.qrscannerkt

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

val libraryArray = ArrayList<String>()
class LibraryActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryArray.clear()
        val infoCursor = mDB.rawQuery("SELECT name FROM info",null)
        infoCursor.moveToFirst()
        while (!infoCursor.isAfterLast) {
            libraryArray.add(infoCursor.getString(0))
            infoCursor.moveToNext()
        }
        infoCursor.close()
        setContentView(R.layout.activity_library)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = CustomRecyclerAdapter(libraryArray)
    }
}

class CustomRecyclerAdapter(private val names: List<String>) : RecyclerView.Adapter<CustomRecyclerAdapter.MyViewHolder>() {
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val largeTextView: TextView = itemView.findViewById(R.id.textViewLarge)
        val buttonDelete: Button = itemView.findViewById(R.id.buttondel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.library_item, parent, false)
        return MyViewHolder(itemView)
    }

    @SuppressLint("ResourceAsColor")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.largeTextView.text = names[position]
            if(preInstalled.contains(holder.largeTextView.text)) {
                holder.buttonDelete.isEnabled = false
                holder.buttonDelete.setTextColor(R.color.purple_700)
                holder.buttonDelete.setBackgroundColor(R.color.purple_700)
            }
            holder.buttonDelete.setOnClickListener {
                if(!preInstalled.contains(holder.largeTextView.text)) {
                    libraryArray.remove(holder.largeTextView.text)
                    mDB.execSQL("DELETE FROM info WHERE name='" + holder.largeTextView.text + "'")
                    mDB.execSQL("DROP TABLE " + holder.largeTextView.text)
                    val audio = File(MainActivity.appContext.getExternalFilesDir("audio").toString() + "/")
                    for (file in audio.listFiles()!!) {
                        if (!file.isDirectory && file.toString().substringAfterLast('/').contains(holder.largeTextView.text)) {
                            file.delete()
                        }
                    }
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, libraryArray.size-position)
                } else {
                    holder.buttonDelete.setBackgroundColor(Color.parseColor("#db0f35"))
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            holder.buttonDelete.setBackgroundColor(Color.parseColor("#db0f35"))
                        },
                        500
                    )
                }
            }


    }

    override fun getItemCount() = names.size
}