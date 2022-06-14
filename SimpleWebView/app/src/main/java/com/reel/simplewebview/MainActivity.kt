package com.reel.simplewebview

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import android.widget.SearchView
import android.widget.Toast
import android.widget.Toolbar

class MainActivity : AppCompatActivity() {
    companion object {
        const val DATA_SEARCH_ENGINE = "data_search_engine"
        private val REQUEST_CODE = 1001
        private val TAG = "MainActivity"
    }

    private lateinit var searchEngineBase: String
    private lateinit var searchReplacement: String
    private var searchString: String? = null
    private var isLoading = false
    private lateinit var webView: NestedScrollWebView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = this.findViewById(R.id.CustomWebView)
        toolbar = this.findViewById(R.id.MainToolbar)
        this.init()
    }

    private fun init() {
        this.initStrings()
        this.initToolbar()
        this.initWebview()
    }

    private fun initWebview() {
        val settings = this.webView.settings.apply {
            javaScriptEnabled = true
            setAppCacheEnabled(true)
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
        }

        this.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                this@MainActivity.isLoading = false
                super.onPageFinished(view, url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                this@MainActivity.isLoading = true
                super.onPageStarted(view, url, favicon)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                this@MainActivity.isLoading = false
                super.onReceivedError(view, request, error)
            }


            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                this@MainActivity.isLoading = false
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        this.webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                return super.onJsAlert(view, url, message, result)
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                Log.i(TAG, "onProgressChanged: $newProgress%")
                super.onProgressChanged(view, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                Log.i(TAG, "onReceivedTitle: $title received")
                super.onReceivedTitle(view, title)
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                Log.i(TAG, "onReceivedIcon: icon received")
                super.onReceivedIcon(view, icon)
            }
        }

    }

    private fun initToolbar() {
        this.setSupportActionBar(this.findViewById(R.id.MainToolbar))
        this.supportActionBar?.setLogo(R.drawable.ic_cloud_24)
        this.supportActionBar?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        ) {
            this.webView.isNestedScrollingEnabled = true
        }
    }

    private fun initStrings() {
        this.searchEngineBase = this.getString(R.string.search_engine_google)
        this.searchReplacement = this.getString(R.string.search_replacement)
    }


    override fun onDestroy() {
        super.onDestroy()
        this.webView.destroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == MainActivity.REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.getStringExtra(MainActivity.DATA_SEARCH_ENGINE)?.let {
                this.searchEngineBase = it.ifBlank { this.searchEngineBase }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && this.webView.canGoBack()) {
            this.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        this.menuInflater.inflate(R.menu.menu_toolbar, menu)
        val menuSearch = menu?.findItem(R.id.menuSearch)
        val searchView = menuSearch?.actionView as? SearchView
        searchView?.isIconifiedByDefault = true
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean {
                if (q == null || q.isBlank()) {
                    Toast.makeText(this@MainActivity, "Nothing to search", Toast.LENGTH_SHORT)
                        .show()
                    return true
                }
                this@MainActivity.searchString = q
                this@MainActivity.doSearch()

                searchView.onActionViewCollapsed()

                return false
            }

            override fun onQueryTextChange(q: String?): Boolean {
                this@MainActivity.searchString = q
                return false
            }

        })
        return true
    }

    private fun doSearch() {
        if (this.isLoading) return
        val q = this.searchString
        if (q != null && q.isNotBlank()) {
            this.toolbar.title = q
            val url = this.searchEngineBase.replace(this.searchReplacement, q)
            this.webView.loadUrl(url) //connect to url
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item?.itemId) {
            R.id.menuRefresh -> {
                if (this.isLoading) {
                    this.webView.stopLoading()
                    this.isLoading = false
                }
                this.doSearch()
            }

            R.id.menuDefaultEngine -> {
                if (this.isLoading) {
                    this.webView.stopLoading()
                    this.isLoading = false
                }

                val intent = SearchEngineActivity.getIntent(this, this.searchEngineBase)
                this.startActivityForResult(intent, MainActivity.REQUEST_CODE)
            }

            R.id.menuStop -> {
                if (this.isLoading) {
                    this.webView.stopLoading()
                }
            }

            else -> {
                Toast.makeText(this, "Not implemented yet!", Toast.LENGTH_SHORT).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}