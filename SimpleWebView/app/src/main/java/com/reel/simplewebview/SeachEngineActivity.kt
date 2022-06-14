package com.reel.simplewebview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SearchEngineActivity : AppCompatActivity() {
    companion object {
        val SEARCH_ENGINE_EXTRA = "search_engine_data"
        val PREFERENCES_NAME = "search_engine"
        val PREFERENCES_STRING_SET_NAME = "search_engine_list"

        fun getIntent(context: Context, engine: String) =
            Intent(context, SearchEngineActivity::class.java)
                .apply { this.putExtra(SearchEngineActivity.SEARCH_ENGINE_EXTRA, engine) }
    }

    private val preferences by lazy {
        this.getSharedPreferences(
            SearchEngineActivity.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    }
    private val dataList by lazy(LazyThreadSafetyMode.NONE) { ArrayList<SearchEngine>() }
    private val scrollAdapter by lazy(LazyThreadSafetyMode.NONE) {
        ScrollAdapter(
            this,
            this.dataList
        )
    }
    private var isSelectionChanged = false
    private lateinit var selectedEngine: String

    private lateinit var btnGoBack: FloatingActionButton
    private lateinit var rcvListUrls: RecyclerView
    private lateinit var btnAdd: Button
    private lateinit var searchTextEditText: EditText
    private lateinit var labelDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine)
        bindViews()
        this.initGUI()
        this.init()
    }

    private fun bindViews() {
        btnGoBack = this.findViewById(R.id.BtnGoBack)
        btnAdd = this.findViewById(R.id.btnAdd)
        rcvListUrls = this.findViewById(R.id.rcvListUrls)
        searchTextEditText = this.findViewById(R.id.SearchText)
        labelDescription = this.findViewById(R.id.LabelDescription)

    }

    private fun initGUI() {
        this.btnAdd.setOnClickListener {
            val url = this.searchTextEditText.text.toString().trim()
            if (url.isBlank()) {
                this.searchTextEditText.text.clear()
                Toast.makeText(this, "No search engine added", Toast.LENGTH_SHORT).show()
            } else {
                val added = this.dataList.asSequence()
                    .firstOrNull { it.url == url } != null
                if (added) {
                    Toast.makeText(this, "Already added", Toast.LENGTH_SHORT).show()
                } else {
                    val name = "Default Name"
                    this.dataList.add(SearchEngine(name, url, false))
                    this.scrollAdapter.notifyDataSetChanged()
                    this.saveData()
                }
            }
        }


        this.btnGoBack.setOnClickListener{
            if(this.isSelectionChanged) {
                val data = Intent()
                data.putExtra(MainActivity.DATA_SEARCH_ENGINE, this.selectedEngine)
                this.setResult(Activity.RESULT_OK, data)
            }
            this.finish()
        }
    }

    private fun init() {
        this.scrollAdapter.clickCallback = { view ->
            val position = this.rcvListUrls.getChildAdapterPosition(view)
            val engine = this.dataList[position]
            if (!engine.selected) {
                this.isSelectionChanged = true
                this.selectedEngine = engine.url
                this.dataList.forEach { it.selected = false }
                engine.selected = true
                this.scrollAdapter.notifyDataSetChanged()
            }
        }

        this.scrollAdapter.longClickCallback = { view ->
            val position = this.rcvListUrls.getChildAdapterPosition(view)
            val engine = this.dataList[position]
            AlertDialog.Builder(this)
                .setTitle("Warning")
                .setMessage("Do you want to delete this item?")
                .setCancelable(true)
                .setNegativeButton("No, Keep") { _, _ -> }
                .setPositiveButton("Yes, Delete") { _, _ ->
                    this.dataList.removeAt(position)
                    if (engine.selected) {
                        if (this.dataList.isNotEmpty()) {
                            this.dataList.first().selected = true
                            this.selectedEngine = this.dataList.first().url
                        } else {
                            this.selectedEngine = ""
                        }
                        this.isSelectionChanged = true
                    }
                    this.scrollAdapter.notifyDataSetChanged()
                    this.saveData()
                }
                .show()

            true
        }

        val selectedEngine =
            this.intent?.getStringExtra(SearchEngineActivity.SEARCH_ENGINE_EXTRA) ?: ""
        val list = this.preferences.getStringSet(
            SearchEngineActivity.PREFERENCES_STRING_SET_NAME,
            setOf(this.getString(R.string.search_engine_baidu))
        )
        list?.forEach {
            val name = "Default Name"; this.dataList.add(
            SearchEngine(
                name,
                it,
                selectedEngine == it
            )
        )
        }

        this.rcvListUrls.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        this.rcvListUrls.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )
        this.rcvListUrls.adapter = this.scrollAdapter
    }

    private fun saveData() {
        val data = arrayListOf<String>()
        this.dataList.forEach { data.add(it.url) }
        val edit = this.preferences.edit()
        edit.putStringSet(SearchEngineActivity.PREFERENCES_STRING_SET_NAME, data.toSet())
        edit.apply()
    }

    override fun onBackPressed() {
        if (this.isSelectionChanged) {
            AlertDialog.Builder(this)
                .setTitle("Are you sure?")
                .setMessage("Configuration has changed, are you sure to discard it?")
                .setCancelable(true)
                .setNegativeButton("Stay") { _, _ -> }
                .setPositiveButton("Discard") { _, _ ->
                    super.onBackPressed()
                }
                .show()
        } else super.onBackPressed()
    }
}