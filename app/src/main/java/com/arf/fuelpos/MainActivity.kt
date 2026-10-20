package com.arf.fuelpos

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ContentValues
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.CancellationSignal
import android.os.Message
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import kotlin.math.hypot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_URL =
            "http://arfksa.dyndns.org:987/ords/f?p=234:2:4549823393311::NO::P2_OST_SYS_CODE:2"
        private const val DEFAULT_PIN = "1234"

        private const val PREFS = "fuel_pos_prefs"
        private const val KEY_URL = "server_url"
        private const val KEY_PIN = "admin_pin"
        private const val KEY_FIRST_RUN_DONE = "first_run_done"
        private const val KEY_HOME = "home_url"
        private const val KEY_ZOOM = "text_zoom"
        private const val KEY_FAB_X = "fab_x"
        private const val KEY_FAB_Y = "fab_y"
        private val ZOOM_LEVELS = intArrayOf(85, 100, 115, 130)
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
    private var homeNavigationPending = false

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
            textZoom = prefs.getInt(KEY_ZOOM, 100)
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
            styledDialog(
                icon = R.drawable.ic_info, accent = R.color.primary,
                title = getString(R.string.alert_title), message = message,
                primaryText = getString(R.string.ok), primaryColor = R.color.primary,
                onPrimary = { result.confirm() },
                onDismiss = { result.confirm() }
            )
            return true
        }

        override fun onJsConfirm(
            view: WebView, url: String?, message: String?, result: android.webkit.JsResult
        ): Boolean {
            styledDialog(
                icon = R.drawable.ic_question, accent = R.color.primary,
                title = getString(R.string.confirm_title), message = message,
                primaryText = getString(R.string.yes_continue), primaryColor = R.color.primary,
                onPrimary = { result.confirm() },
                secondaryText = getString(R.string.cancel), secondaryColor = R.color.text_secondary,
                onSecondary = { result.cancel() },
                onDismiss = { result.cancel() }
            )
            return true
        }

        /** "Leave this page?" (APEX unsaved-changes warning), as an in-app dialog. */
        override fun onJsBeforeUnload(
            view: WebView, url: String?, message: String?, result: android.webkit.JsResult
        ): Boolean {
            val toHome = homeNavigationPending
            homeNavigationPending = false
            showLeaveDialog(
                toHome = toHome,
                onStay = { result.cancel() },
                onLeave = { result.confirm() }
            )
            return true
        }
    }

    // ---------------- Downloads (invoices, reports, PDF, Excel) ----------------

    /**
     * Oracle Reports sends invoices from URLs like ".../f?p=..." with no file name,
     * so Android saved them as "f.bin". We download the file ourselves (with the
     * APEX session cookies), detect a real PDF by its "%PDF" header, save it to
     * Downloads with a proper .pdf name, and open the print dialog straight away.
     */
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
        toast(getString(R.string.preparing_file))
        val referer = webView.url
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.let {
                        setRequestProperty("Cookie", it)
                    }
                    referer?.let { setRequestProperty("Referer", it) }
                }
                val headerDisposition = conn.getHeaderField("Content-Disposition") ?: contentDisposition
                val headerMime = conn.contentType ?: mimeType

                val temp = File(cacheDir, "download.tmp")
                conn.inputStream.use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
                conn.disconnect()

                val isPdf = temp.inputStream().use { ins ->
                    val head = ByteArray(5)
                    ins.read(head) == 5 && String(head, Charsets.US_ASCII) == "%PDF-"
                }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName: String
                val finalMime: String
                if (isPdf) {
                    fileName = "Invoice_$stamp.pdf"
                    finalMime = "application/pdf"
                } else {
                    var guessed = URLUtil.guessFileName(url, headerDisposition, headerMime)
                    if (guessed.endsWith(".bin")) guessed = "File_$stamp.bin"
                    fileName = guessed
                    finalMime = headerMime?.substringBefore(';')?.trim()
                        ?: "application/octet-stream"
                }

                val local = File(File(cacheDir, "docs").apply { mkdirs() }, fileName)
                temp.copyTo(local, overwrite = true)
                temp.delete()
                saveToDownloads(local, fileName, finalMime)

                runOnUiThread {
                    toast(getString(R.string.file_saved, fileName))
                    shareFile(local, finalMime)
                }
            } catch (e: Exception) {
                runOnUiThread { toast(getString(R.string.download_failed)) }
            }
        }.start()
    }

    /** Keep a copy in the phone's Downloads folder. */
    private fun saveToDownloads(source: File, fileName: String, mime: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return
                contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                source.copyTo(File(dir, fileName), overwrite = true)
            }
        } catch (_: Exception) {
            // Saving a copy is a bonus; printing still works from the app's own copy.
        }
    }

    /** Send the PDF straight to Android's print dialog (printer, or "Save as PDF"). */
    private fun printPdf(file: File) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?, callback: PrintDocumentAdapter.LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled(); return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?, callback: PrintDocumentAdapter.WriteResultCallback
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        try {
            printManager.print(file.nameWithoutExtension, adapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            openFile(file, "application/pdf")
        }
    }

    /** Open the system share sheet with the file attached (WhatsApp, email, ...). */
    private fun shareFile(file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_invoice)))
        } catch (e: Exception) {
            openFile(file, mime)
        }
    }

    private fun openFile(file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, file.name))
        } catch (e: Exception) {
            toast(getString(R.string.no_app_to_open))
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

        val bar = findViewById<View>(R.id.fabBar)
        val home = findViewById<ImageButton>(R.id.homeButton)
        val gear = findViewById<ImageButton>(R.id.settingsButton)

        home.setOnClickListener { goHome() }
        home.setOnLongClickListener { toast(getString(R.string.drag_hint)); true }
        gear.setOnClickListener { toast(getString(R.string.hold_for_settings)) }
        gear.setOnLongClickListener { askPinThenSettings(); true }

        makeDraggable(bar, listOf(home, gear))
        bar.post { restoreFabPosition(bar) }
    }

    /** Home page of the system, keeping the current APEX session (no re-login). */
    private fun homeUrl(): String {
        val base = prefs.getString(KEY_HOME, null) ?: savedUrl()
        val session = Regex("[?&]p=[^:&]*:[^:&]*:(\\d+)")
            .find(webView.url ?: "")?.groupValues?.get(1)
        if (session.isNullOrEmpty() || session == "0") return base
        return Regex("([?&]p=[^:&]*:[^:&]*:)(\\d*)")
            .replace(base) { it.groupValues[1] + session }
    }

    private fun goHome() {
        if (!isOnline()) { showError(getString(R.string.error_no_internet)); return }
        hideError()
        homeNavigationPending = true
        webView.loadUrl(homeUrl())
        // If the page has nothing unsaved, no dialog appears; reset the flag shortly after.
        webView.postDelayed({ homeNavigationPending = false }, 1500)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(container: View, handles: List<View>) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop * 2
        var offX = 0f; var offY = 0f; var downX = 0f; var downY = 0f; var dragging = false
        val listener = View.OnTouchListener { v, e ->
            val parent = container.parent as View
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    offX = container.x - e.rawX; offY = container.y - e.rawY
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                        dragging = true
                        v.cancelLongPress(); v.isPressed = false
                    }
                    if (dragging) {
                        container.x = (e.rawX + offX).coerceIn(0f, (parent.width - container.width).toFloat())
                        container.y = (e.rawY + offY).coerceIn(0f, (parent.height - container.height).toFloat())
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        v.isPressed = false
                        val fx = container.x / (parent.width - container.width).coerceAtLeast(1)
                        val fy = container.y / (parent.height - container.height).coerceAtLeast(1)
                        prefs.edit().putFloat(KEY_FAB_X, fx).putFloat(KEY_FAB_Y, fy).apply()
                        dragging = false
                        true
                    } else false
                }
                else -> false
            }
        }
        handles.forEach { it.setOnTouchListener(listener) }
    }

    private fun restoreFabPosition(bar: View) {
        val fx = prefs.getFloat(KEY_FAB_X, -1f)
        val fy = prefs.getFloat(KEY_FAB_Y, -1f)
        if (fx < 0f || fy < 0f) return
        val parent = bar.parent as View
        bar.x = fx * (parent.width - bar.width)
        bar.y = fy * (parent.height - bar.height)
    }

    // ---------------- Styled in-app dialogs ----------------

    private fun showLeaveDialog(toHome: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_leave, null)
        view.findViewById<TextView>(R.id.leaveMessage).setText(
            if (toHome) R.string.leave_message_home else R.string.leave_message
        )

        // Animated gradient border on the "leave" button
        val border = AnimatedBorderDrawable(dp(2).toFloat() + 0.5f, dp(16).toFloat())
        val leaveBtn = view.findViewById<FrameLayout>(R.id.leaveButton)
        leaveBtn.background = border
        view.findViewById<TextView>(R.id.leaveButtonText).apply {
            setText(if (toHome) R.string.go_home_anyway else R.string.leave_page)
            val icon = ContextCompat.getDrawable(context, if (toHome) R.drawable.ic_home else R.drawable.ic_leave)!!.mutate()
            icon.setTint(ContextCompat.getColor(context, R.color.danger_dark))
            icon.setBounds(0, 0, dp(22), dp(22))
            setCompoundDrawablesRelative(icon, null, null, null)
        }

        // Soft blue shadow under the primary button
        val stay = view.findViewById<MaterialButton>(R.id.stayButton)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val blue = ContextCompat.getColor(this, R.color.royal_blue)
            stay.outlineSpotShadowColor = blue
            stay.outlineAmbientShadowColor = blue
        }

        // Breathing pulse ring around the top icon
        val ring = view.findViewById<View>(R.id.pulseRing)
        val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                ring.scaleX = 1f + 0.42f * t
                ring.scaleY = 1f + 0.42f * t
                ring.alpha = 1f - t
            }
        }
        val iconBg = view.findViewById<View>(R.id.leaveIconBg)
        val breathe = ObjectAnimator.ofPropertyValuesHolder(
            iconBg,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        var handled = false
        stay.setOnClickListener { handled = true; dialog.dismiss(); onStay() }
        leaveBtn.setOnClickListener { handled = true; dialog.dismiss(); onLeave() }
        dialog.setOnShowListener { border.start(); pulse.start(); breathe.start() }
        dialog.setOnDismissListener {
            border.stop(); pulse.cancel(); breathe.cancel()
            if (!handled) { handled = true; onStay() }
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt().coerceAtMost(dp(440)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun styledDialog(
        icon: Int, accent: Int, title: String, message: String?,
        primaryText: String, primaryColor: Int, onPrimary: () -> Unit,
        secondaryText: String? = null, secondaryColor: Int = R.color.text_secondary,
        onSecondary: () -> Unit = {}, onDismiss: () -> Unit = {}
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_styled, null)
        val accentColor = ContextCompat.getColor(this, accent)
        view.findViewById<View>(R.id.dlgIconBg).backgroundTintList =
            ColorStateList.valueOf(accentColor)
        view.findViewById<ImageView>(R.id.dlgIcon).setImageResource(icon)
        view.findViewById<TextView>(R.id.dlgTitle).text = title
        view.findViewById<TextView>(R.id.dlgMessage).apply {
            text = message
            visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        var handled = false

        view.findViewById<MaterialButton>(R.id.dlgPrimary).apply {
            text = primaryText
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, primaryColor))
            setTextColor(Color.WHITE)
            setOnClickListener { handled = true; dialog.dismiss(); onPrimary() }
        }
        view.findViewById<MaterialButton>(R.id.dlgSecondary).apply {
            if (secondaryText == null) {
                visibility = View.GONE
            } else {
                val c = ContextCompat.getColor(context, secondaryColor)
                text = secondaryText
                setTextColor(c)
                strokeColor = ColorStateList.valueOf(c)
                rippleColor = ColorStateList.valueOf(c and 0x33FFFFFF)
                setOnClickListener { handled = true; dialog.dismiss(); onSecondary() }
            }
        }
        dialog.setOnDismissListener { if (!handled) { handled = true; onDismiss() } }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt().coerceAtMost(dp(440)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
        val items = arrayOf(
            getString(R.string.menu_url),
            getString(R.string.menu_zoom),
            getString(R.string.menu_set_home),
            getString(R.string.menu_reset_home),
            getString(R.string.menu_pin),
            getString(R.string.menu_reset_buttons)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_menu_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showUrlDialog()
                    1 -> showZoomDialog()
                    2 -> {
                        webView.url?.let { prefs.edit().putString(KEY_HOME, it).apply() }
                        toast(getString(R.string.home_set))
                    }
                    3 -> {
                        prefs.edit().remove(KEY_HOME).apply()
                        toast(getString(R.string.home_reset))
                    }
                    4 -> showPinDialog()
                    5 -> {
                        prefs.edit().remove(KEY_FAB_X).remove(KEY_FAB_Y).apply()
                        findViewById<View>(R.id.fabBar).apply { translationX = 0f; translationY = 0f }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showUrlDialog() {
        val urlInput = EditText(this).apply {
            setText(savedUrl())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textDirection = View.TEXT_DIRECTION_LTR
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.server_url_label)
            .setView(wrap(urlInput))
            .setPositiveButton(R.string.save) { _, _ ->
                var url = urlInput.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url"
                }
                if (url.isEmpty() || !URLUtil.isValidUrl(url)) {
                    toast(getString(R.string.invalid_url)); return@setPositiveButton
                }
                prefs.edit().putString(KEY_URL, url).remove(KEY_HOME).apply()
                toast(getString(R.string.saved))
                webView.clearHistory()
                loadHome()
            }
            .setNeutralButton(R.string.reset_default) { _, _ ->
                prefs.edit().putString(KEY_URL, DEFAULT_URL).remove(KEY_HOME).apply()
                toast(getString(R.string.reset_done))
                webView.clearHistory()
                loadHome()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showZoomDialog() {
        val labels = arrayOf(
            getString(R.string.zoom_small), getString(R.string.zoom_normal),
            getString(R.string.zoom_large), getString(R.string.zoom_xlarge)
        )
        val current = ZOOM_LEVELS.indexOf(prefs.getInt(KEY_ZOOM, 100)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_zoom)
            .setSingleChoiceItems(labels, current) { d, which ->
                val z = ZOOM_LEVELS[which]
                prefs.edit().putInt(KEY_ZOOM, z).apply()
                webView.settings.textZoom = z
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPinDialog() {
        val pinInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.new_pin_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_pin)
            .setView(wrap(pinInput))
            .setPositiveButton(R.string.save) { _, _ ->
                val pin = pinInput.text.toString().trim()
                if (pin.length < 4) toast(getString(R.string.pin_too_short))
                else { prefs.edit().putString(KEY_PIN, pin).apply(); toast(getString(R.string.saved)) }
            }
            .setNegativeButton(R.string.cancel, null)
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
