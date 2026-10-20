package com.arf.fuelpos

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_URL =
            "http://arfksa.dyndns.org:987/ords/f?p=234:2:4549823393311::NO::P2_OST_SYS_CODE:2"
        private const val DEFAULT_PIN = "1234"

        private const val PREFS = "fuel_pos_prefs"
        private const val KEY_URL = "server_url"
        private const val KEY_PIN = "admin_pin"
        private const val KEY_FIRST_RUN_DONE = "first_run_done"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var errorMessage: TextView

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var mainFrameFailed = false
    private var lastBackPress = 0L

    // ---------- File upload (Oracle APEX "Browse" fields) ----------
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast(getString(R.string.storage_denied))
        }

    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureDefaults()

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        errorMessage = findViewById(R.id.errorMessage)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterFullScreen()

        setupWebView()
        setupSwipeRefresh()
        setupButtons()
        setupBackNavigation()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else if (!prefs.getBoolean(KEY_FIRST_RUN_DONE, false)) {
            showFirstRunSetup()
        } else {
            loadHome()
        }
    }

    /** Make sure a PIN exists. The URL is entered by the admin on first launch. */
    private fun ensureDefaults() {
        if (prefs.getString(KEY_PIN, null) == null) {
            prefs.edit().putString(KEY_PIN, DEFAULT_PIN).apply()
        }
    }

    /** First launch only: ask for the system link once, save it, then open it. */
    private fun showFirstRunSetup() {
        val urlInput = EditText(this).apply {
            setText(DEFAULT_URL)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textDirection = View.TEXT_DIRECTION_LTR
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.first_run_title)
            .setMessage(R.string.first_run_message)
            .setView(wrap(urlInput))
            .setCancelable(false)
            .setPositiveButton(R.string.save_and_open, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var url = urlInput.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url"
                }
                if (url.isEmpty() || !URLUtil.isValidUrl(url)) {
                    toast(getString(R.string.invalid_url))
                } else {
                    prefs.edit()
                        .putString(KEY_URL, url)
                        .putBoolean(KEY_FIRST_RUN_DONE, true)
                        .apply()
                    dialog.dismiss()
                    loadHome()
                }
            }
        }
        dialog.show()
    }

    private fun savedUrl(): String = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

    private fun loadHome() {
        if (!isOnline()) {
            showError(getString(R.string.error_no_internet))
            return
        }
        hideError()
        webView.loadUrl(savedUrl())
    }

    // ---------------- WebView configuration ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        with(webView.settings) {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = 100
            defaultTextEncodingName = "utf-8"
            // Present as normal mobile Chrome so APEX serves its standard pages
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.webViewClient = KioskWebViewClient()
        webView.webChromeClient = KioskChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private inner class KioskWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> false // keep everything inside the app
                "tel", "mailto", "sms", "whatsapp", "intent", "geo" -> {
                    openExternally(uri); true
                }
                else -> {
                    openExternally(uri); true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            mainFrameFailed = false
            progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            CookieManager.getInstance().flush()
            if (!mainFrameFailed) hideError()
        }

        override fun onReceivedError(
            view: WebView, request: WebResourceRequest, error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                mainFrameFailed = true
                val msg = if (!isOnline()) getString(R.string.error_no_internet)
                else getString(R.string.error_server_unreachable)
                showError(msg)
            }
        }

        override fun onReceivedHttpError(
            view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                mainFrameFailed = true
                showError(getString(R.string.error_server, errorResponse.statusCode))
            }
        }

        override fun onRenderProcessGone(
            view: WebView, detail: android.webkit.RenderProcessGoneDetail?
        ): Boolean {
            // WebView renderer crashed: rebuild the activity instead of closing the app
            recreate()
            return true
        }
    }

    private inner class KioskChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            if (newProgress >= 100) progressBar.visibility = View.GONE
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            return try {
                val intent = fileChooserParams.createIntent().apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
                fileChooserLauncher.launch(intent)
                true
            } catch (e: ActivityNotFoundException) {
                fileCallback = null
                toast(getString(R.string.no_file_app))
                false
            }
        }

        // APEX popups / window.open: open them in the same WebView
        override fun onCreateWindow(
            view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
        ): Boolean {
            val popup = WebView(this@MainActivity)
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView, request: WebResourceRequest
                ): Boolean {
                    webView.loadUrl(request.url.toString())
                    popup.destroy()
                    return true
                }
            }
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onJsAlert(
            view: WebView, url: String?, message: String?, result: android.webkit.JsResult
        ): Boolean {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsConfirm(
            view: WebView, url: String?, message: String?, result: android.webkit.JsResult
        ): Boolean {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }
    }

    // ---------------- Downloads (reports, PDF, Excel) ----------------

    private fun startDownload(
        url: String, userAgent: String, contentDisposition: String?, mimeType: String?
    ) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setDescription(getString(R.string.downloading))
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast(getString(R.string.download_started, fileName))
        } catch (e: Exception) {
            // Fallback: let the system browser handle it
            openExternally(Uri.parse(url))
        }
    }

    // ---------------- UI helpers ----------------

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary))
        swipeRefresh.setOnRefreshListener {
            if (errorView.visibility == View.VISIBLE || webView.url.isNullOrBlank()) {
                loadHome()
                swipeRefresh.isRefreshing = false
            } else {
                webView.reload()
            }
        }
        // Only allow pull-to-refresh when the page is scrolled to the very top,
        // so scrolling inside long APEX reports doesn't trigger a reload.
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.retryButton).setOnClickListener { loadHome() }
        findViewById<TextView>(R.id.errorSettings).setOnClickListener { askPinThenSettings() }
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            toast(getString(R.string.hold_for_settings))
        }
        findViewById<ImageButton>(R.id.settingsButton).setOnLongClickListener {
            askPinThenSettings(); true
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    errorView.visibility == View.VISIBLE && webView.canGoBack() -> {
                        hideError(); webView.goBack()
                    }
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        val now = System.currentTimeMillis()
                        if (now - lastBackPress < 2000) {
                            finish()
                        } else {
                            lastBackPress = now
                            toast(getString(R.string.press_again_to_exit))
                        }
                    }
                }
            }
        })
    }

    private fun showError(message: String) {
        errorMessage.text = message
        errorView.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun hideError() {
        errorView.visibility = View.GONE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            toast(getString(R.string.cannot_open_link))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun enterFullScreen() {
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullScreen()
    }

    // ---------------- Admin settings (PIN protected) ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun askPinThenSettings() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.pin_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_title)
            .setView(wrap(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (input.text.toString() == prefs.getString(KEY_PIN, DEFAULT_PIN)) {
                    showSettingsDialog()
                } else {
                    toast(getString(R.string.wrong_pin))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSettingsDialog() {
        val urlInput = EditText(this).apply {
            setText(savedUrl())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textDirection = View.TEXT_DIRECTION_LTR
            setSelection(text.length)
        }
        val pinInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.new_pin_hint)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(context).apply { text = getString(R.string.server_url_label) })
            addView(urlInput)
            addView(TextView(context).apply {
                text = getString(R.string.pin_change_label); setPadding(0, dp(12), 0, 0)
            })
            addView(pinInput)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.server_settings)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                var url = urlInput.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url"
                }
                if (url.isEmpty() || !URLUtil.isValidUrl(url)) {
                    toast(getString(R.string.invalid_url)); return@setPositiveButton
                }
                val editor = prefs.edit().putString(KEY_URL, url)
                val newPin = pinInput.text.toString().trim()
                if (newPin.length >= 4) editor.putString(KEY_PIN, newPin)
                editor.apply()
                toast(getString(R.string.saved))
                webView.clearHistory()
                loadHome()
            }
            .setNeutralButton(R.string.reset_default) { _, _ ->
                prefs.edit().putString(KEY_URL, DEFAULT_URL).apply()
                toast(getString(R.string.reset_done))
                webView.clearHistory()
                loadHome()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun wrap(view: View): View = LinearLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---------------- Lifecycle ----------------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
