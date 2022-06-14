package com.reel.simplewebview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScrollAdapter(
    private val context: Context,
    private val dataList: ArrayList<SearchEngine>,
    var clickCallback: ((View) -> Unit)? = null,
    var longClickCallback: ((View) -> Boolean)? = null
) : RecyclerView.Adapter<ScrollAdapter.ViewHolder>() {

    private val inflater by lazy(LazyThreadSafetyMode.NONE) { LayoutInflater.from(context) }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageSearchEngine: ImageView = this.itemView.findViewById(R.id.ImageSearchIcon)
        val labelSearchEngine: TextView = this.itemView.findViewById(R.id.TextSearchEngine)
        val imageSelected: ImageView = this.itemView.findViewById(R.id.ImageSelected)

        fun bind(searchEngine: SearchEngine) {
            imageSelected.visibility = if (searchEngine.selected) View.VISIBLE else View.INVISIBLE
            labelSearchEngine.text = searchEngine.url
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = this.inflater.inflate(R.layout.layout_search_list, parent, false)
        this.clickCallback?.let { view.setOnClickListener(it) }
        this.longClickCallback?.let { view.setOnLongClickListener(it) }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentEngine = this.dataList[position]
        holder.bind(currentEngine)
    }

    override fun getItemCount(): Int {
        return dataList.size
    }
}