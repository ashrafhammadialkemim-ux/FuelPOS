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
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
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
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_URL =
            "http://arfksa.dyndns.org:987/ords/f?p=234:2:4549823393311::NO::P2_OST_SYS_CODE:2"

        private const val PREFS = "fuel_pos_prefs"
        private const val KEY_URL = "server_url"
        private const val KEY_FIRST_RUN_DONE = "first_run_done"
        private const val KEY_HOME = "home_url"
        private const val KEY_ZOOM = "text_zoom"
        private const val KEY_FAB_X = "fab_x"
        private const val KEY_FAB_Y = "fab_y"

        /**
         * Google Website Translator, injected into every page (and same-origin APEX dialog
         * iframes) when a translation language is chosen. Google's own bar is hidden.
         */
        /**
         * Second pass: anything Google leaves in Arabic (customer names, drop-down options,
         * placeholders, dialog iframes) is collected and translated through the app.
         */
        private const val FALLBACK_JS = """
(function(){
  if(window.__fpX){ window.__fpX.scan(); return; }
  var AR=/[\u0600-\u06FF]/, map={}, asked={};
  function docs(){ var a=[document], f=document.querySelectorAll('iframe');
    for(var i=0;i<f.length;i++){ try{ var d=f[i].contentDocument; if(d&&d.body) a.push(d); }catch(e){} } return a; }
  function key(s){ return (s||'').replace(/\s+/g,' ').trim(); }
  function each(fn){
    docs().forEach(function(doc){
      var w=doc.createTreeWalker(doc.body,NodeFilter.SHOW_TEXT,null,false), n;
      while((n=w.nextNode())){
        var p=n.parentNode; if(!p) continue; var t=p.nodeName;
        if(t==='SCRIPT'||t==='STYLE'||t==='TEXTAREA'||t==='NOSCRIPT') continue;
        if(AR.test(n.nodeValue)) fn('t',n,key(n.nodeValue));
      }
      var o=doc.querySelectorAll('option');
      for(var i=0;i<o.length;i++){ if(AR.test(o[i].text)) fn('o',o[i],key(o[i].text)); }
      var ph=doc.querySelectorAll('[placeholder]');
      for(var j=0;j<ph.length;j++){ var v=ph[j].getAttribute('placeholder'); if(v&&AR.test(v)) fn('p',ph[j],key(v)); }
    });
  }
  function apply(kind,el,k){
    var v=map[k]; if(!v) return false;
    if(kind==='t'){ var s=el.nodeValue, lead=s.match(/^\s*/)[0], tail=s.match(/\s*$/)[0]; el.nodeValue=lead+v+tail; }
    else if(kind==='o'){ el.text=v; }
    else { el.setAttribute('placeholder',v); }
    return true;
  }
  function scan(){
    var need=[];
    each(function(kind,el,k){
      if(!k||k.length>500) return;
      if(!apply(kind,el,k)&&!asked[k]){ asked[k]=1; need.push(k); }
    });
    if(need.length&&window.FPTrans){ for(var i=0;i<need.length;i+=40) FPTrans.request(JSON.stringify(need.slice(i,i+40))); }
  }
  var timer=null;
  function later(ms){ clearTimeout(timer); timer=setTimeout(scan,ms); }
  window.__fpX={scan:scan};
  window.fpApply=function(obj){ for(var k in obj){ map[k]=obj[k]; } later(80); };
  later(2500);
  if(document.body) new MutationObserver(function(){ later(900); }).observe(document.body,{childList:true,subtree:true,characterData:true});
  setInterval(function(){ if(!document.hidden) scan(); },4000);
})();
"""

        // Shared across screen rotations / language switches
        private val trCache = ConcurrentHashMap<String, String>()
        private val trInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val trPool = Executors.newFixedThreadPool(4)
        private val ARABIC = Regex("[\\u0600-\\u06FF]")

        private const val TRANSLATE_JS = """
(function(){
  var L='__LANG__';
  function css(doc){
    if(doc.getElementById('fp-gt-css'))return;
    var st=doc.createElement('style');st.id='fp-gt-css';
    st.textContent='iframe.skiptranslate,.goog-te-banner-frame,#goog-gt-tt,.goog-te-balloon-frame,.goog-tooltip,.VIpgJd-ZVi9od-ORHb-OEVmcd,.VIpgJd-ZVi9od-aZ2wEe-wOHMyf,.VIpgJd-ZVi9od-l4eHX-hSRGPd,#google_translate_element{display:none!important;visibility:hidden!important}body{top:0!important}.goog-text-highlight,.VIpgJd-yAWNEb-VIpgJd-fmcmS-sn54Q{background:none!important;box-shadow:none!important}';
    (doc.head||doc.documentElement).appendChild(st);
  }
  function setup(win){
    try{
      var doc=win.document; if(!doc||!doc.body)return;
      css(doc);
      if(win.__fpGT)return; win.__fpGT=1;
      var d=doc.createElement('div');d.id='google_translate_element';doc.body.appendChild(d);
      win.fpGTInit=function(){
        try{ new win.google.translate.TranslateElement({pageLanguage:'ar',autoDisplay:false},'google_translate_element'); }catch(e){}
        var n=0,t=win.setInterval(function(){
          n++; var s=doc.querySelector('select.goog-te-combo');
          if(s&&s.options.length>1){ if(s.value!==L){ s.value=L; s.dispatchEvent(new Event('change')); } win.clearInterval(t); }
          if(n>80)win.clearInterval(t);
        },250);
      };
      var sc=doc.createElement('script');
      sc.src='https://translate.google.com/translate_a/element.js?cb=fpGTInit';
      (doc.head||doc.body).appendChild(sc);
    }catch(e){}
  }
  function hook(fr){
    if(fr.__fpL)return; fr.__fpL=1;
    fr.addEventListener('load',function(){ try{ setup(fr.contentWindow); }catch(e){} });
    try{ if(fr.contentWindow.document.readyState==='complete') setup(fr.contentWindow); }catch(e){}
  }
  function frames(){ var f=document.querySelectorAll('iframe'); for(var i=0;i<f.length;i++) hook(f[i]); }
  setup(window); frames();
  if(!window.__fpObs&&document.body){ window.__fpObs=new MutationObserver(frames); window.__fpObs.observe(document.body,{childList:true,subtree:true}); }
})();
"""
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
        if (prefs.getString(Cfg.ADMIN_USER, null) == null) {
            prefs.edit().putString(Cfg.ADMIN_USER, Cfg.DEF_USER)
                .putString(Cfg.ADMIN_PASS, Cfg.DEF_PASS).apply()
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
        applyTranslateCookie()
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
            textZoom = prefs.getInt(KEY_ZOOM, Cfg.DEF_ZOOM)
            defaultTextEncodingName = "utf-8"
            // Present as normal mobile Chrome so APEX serves its standard pages
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.addJavascriptInterface(TranslateBridge(), "FPTrans")
        loadTranslationCache()
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
            injectTranslator(view)
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
            translateMessage(message) { msg ->
                styledDialog(
                    icon = R.drawable.ic_info, accent = R.color.primary,
                    title = getString(R.string.alert_title), message = msg,
                    primaryText = getString(R.string.ok), primaryColor = R.color.primary,
                    onPrimary = { result.confirm() },
                    onDismiss = { result.confirm() }
                )
            }
            return true
        }

        override fun onJsConfirm(
            view: WebView, url: String?, message: String?, result: android.webkit.JsResult
        ): Boolean {
            translateMessage(message) { msg ->
                styledDialog(
                    icon = R.drawable.ic_question, accent = R.color.primary,
                    title = getString(R.string.confirm_title), message = msg,
                    primaryText = getString(R.string.yes_continue), primaryColor = R.color.primary,
                    onPrimary = { result.confirm() },
                    secondaryText = getString(R.string.cancel), secondaryColor = R.color.text_secondary,
                    onSecondary = { result.cancel() },
                    onDismiss = { result.cancel() }
                )
            }
            return true
        }

        /** "Leave this page?" (APEX unsaved-changes warning), as an in-app dialog. */
        override fun onJsBeforeUnload(
            view: WebView, url: String?, message: String?, result: android.webkit.JsResult
        ): Boolean {
            val toHome = homeNavigationPending
            homeNavigationPending = false
            LeaveDialog.show(
                this@MainActivity,
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
                    when (prefs.getInt(Cfg.INVOICE_ACTION, 0)) {
                        0 -> shareFile(local, finalMime)
                        1 -> if (isPdf) printPdf(local) else openFile(local, finalMime)
                        2 -> openFile(local, finalMime)
                        else -> Unit   // saved to Downloads only
                    }
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
        val lang = findViewById<ImageButton>(R.id.langButton)

        lang.setOnClickListener { showLanguagePicker() }
        home.setOnClickListener { goHome() }
        home.setOnLongClickListener { toast(getString(R.string.drag_hint)); true }
        gear.setOnClickListener { toast(getString(R.string.hold_for_settings)) }
        gear.setOnLongClickListener { askPinThenSettings(); true }

        makeDraggable(bar, listOf(home, lang, gear))
        applyAppearance()
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
                        if (!prefs.getBoolean(Cfg.EXIT_CONFIRM, true) || now - lastBackPress < 2000) {
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

    private fun exitFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && prefs.getBoolean(Cfg.FULLSCREEN, true)) enterFullScreen()
    }

    // ---------------- Admin settings (PIN protected) ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            applyAppearance()
            if (r.data?.getBooleanExtra(SettingsActivity.EXTRA_RELOAD, false) == true) {
                applyTranslateCookie()
                webView.clearHistory()
                loadHome()
            }
        }

    /** Admin sign-in (username + password), then the settings screen. */
    private fun askPinThenSettings() {
        AdminLogin.show(this) {
            settingsLauncher.launch(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_CURRENT_URL, webView.url)
            )
        }
    }

    /** Apply every look & behavior setting: zoom, screen, floating bar. */
    private fun applyAppearance() {
        webView.settings.textZoom = prefs.getInt(KEY_ZOOM, Cfg.DEF_ZOOM)

        if (prefs.getBoolean(Cfg.KEEP_SCREEN, true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (prefs.getBoolean(Cfg.FULLSCREEN, true)) enterFullScreen() else exitFullScreen()
        swipeRefresh.isEnabled = prefs.getBoolean(Cfg.PULL_REFRESH, true)

        val icons = intArrayOf(R.drawable.ic_fuel, R.drawable.ic_home, R.drawable.ic_drop, R.drawable.ic_receipt)
        val home = findViewById<ImageButton>(R.id.homeButton)
        val lang = findViewById<ImageButton>(R.id.langButton)
        val gear = findViewById<ImageButton>(R.id.settingsButton)
        home.setImageResource(icons[prefs.getInt(Cfg.FAB_ICON, 0).coerceIn(0, icons.size - 1)])

        val showHome = prefs.getBoolean(Cfg.FAB_SHOW_HOME, true)
        val showLang = prefs.getBoolean(Cfg.FAB_SHOW_LANG, true)
        home.visibility = if (showHome) View.VISIBLE else View.GONE
        findViewById<View>(R.id.divHome).visibility = if (showHome) View.VISIBLE else View.GONE
        lang.visibility = if (showLang) View.VISIBLE else View.GONE
        findViewById<View>(R.id.divLang).visibility = if (showLang) View.VISIBLE else View.GONE

        val size = dp(intArrayOf(38, 44, 54)[prefs.getInt(Cfg.FAB_SIZE, 1).coerceIn(0, 2)])
        listOf(home, lang, gear).forEach { b ->
            b.layoutParams = b.layoutParams.apply { width = size; height = size }
            val pad = (size * 0.24f).toInt()
            b.setPadding(pad, pad, pad, pad)
        }

        val bar = findViewById<View>(R.id.fabBar)
        bar.alpha = prefs.getInt(Cfg.FAB_ALPHA, 100) / 100f
        bar.background = GradientDrawable().apply {
            cornerRadius = (size * 0.64f)
            setColor(ColorUtils.setAlphaComponent(prefs.getInt(Cfg.FAB_COLOR, Color.parseColor("#081A32")), 0xD9))
        }
        if (prefs.getFloat(KEY_FAB_X, -1f) < 0f) { bar.translationX = 0f; bar.translationY = 0f }
        else bar.post { restoreFabPosition(bar) }
    }

    // ---------------- Page translation (for foreign workers) ----------------

    private fun pageLang(): String = prefs.getString(Cfg.PAGE_LANG, "").orEmpty().let { if (it == "ar") "" else it }

    /** Google's translator reads the "googtrans" cookie; set or clear it for the system's site. */
    private fun applyTranslateCookie() {
        val lang = pageLang()
        val cm = CookieManager.getInstance()
        val expired = "expires=Thu, 01 Jan 1970 00:00:00 GMT"
        listOfNotNull(webView.url, savedUrl()).mapNotNull { Uri.parse(it) }
            .filter { it.host != null && (it.scheme == "http" || it.scheme == "https") }
            .distinctBy { it.authority }
            .forEach { u ->
                val origin = "${u.scheme}://${u.authority}"
                val host = u.host!!
                val parts = host.split(".")
                val domains = (0 until (parts.size - 1).coerceAtLeast(0)).map { "." + parts.drop(it).joinToString(".") }
                cm.setCookie(origin, "googtrans=; $expired; path=/")
                domains.forEach { d -> cm.setCookie(origin, "googtrans=; $expired; path=/; domain=$d") }
                if (lang.isNotEmpty()) cm.setCookie(origin, "googtrans=/ar/$lang; path=/")
            }
        cm.flush()
    }

    private fun injectTranslator(view: WebView) {
        val lang = pageLang()
        if (lang.isEmpty()) return
        view.evaluateJavascript(TRANSLATE_JS.replace("__LANG__", lang), null)
        view.evaluateJavascript(FALLBACK_JS, null)
    }

    /** Called from the page with Arabic strings Google didn't translate. */
    inner class TranslateBridge {
        @JavascriptInterface
        fun request(json: String) {
            val lang = pageLang()
            if (lang.isEmpty()) return
            val list = try { JSONArray(json) } catch (e: Exception) { return }
            val ready = JSONObject()
            for (i in 0 until list.length()) {
                val text = list.optString(i)
                if (text.isBlank()) continue
                val k = "$lang|$text"
                val cached = trCache[k]
                if (cached != null) {
                    ready.put(text, cached)
                } else if (trInFlight.add(k)) {
                    trPool.execute {
                        val r = googleTranslate(text, lang)
                        trInFlight.remove(k)
                        if (r != null) {
                            trCache[k] = r
                            pushTranslations(JSONObject().put(text, r))
                        }
                    }
                }
            }
            if (ready.length() > 0) pushTranslations(ready)
        }
    }

    private fun pushTranslations(o: JSONObject) = runOnUiThread {
        if (!isFinishing && !isDestroyed) webView.evaluateJavascript("window.fpApply&&fpApply($o)", null)
    }

    /** Google Translate (public web endpoint). Returns null on any failure. */
    private fun googleTranslate(text: String, target: String): String? = try {
        val url = URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=ar&tl=$target&dt=t&q=" +
                URLEncoder.encode(text, "UTF-8")
        )
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val parts = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until parts.length()) parts.optJSONArray(i)?.let { sb.append(it.optString(0)) }
        sb.toString().trim().ifEmpty { null }
    } catch (e: Exception) { null }

    /** Translate a system message (alert / confirm) before showing it, when a language is chosen. */
    private fun translateMessage(msg: String?, done: (String?) -> Unit) {
        val lang = pageLang()
        if (lang.isEmpty() || msg.isNullOrBlank() || !ARABIC.containsMatchIn(msg)) { done(msg); return }
        trPool.execute {
            val k = "$lang|$msg"
            val r = trCache[k] ?: googleTranslate(msg, lang)?.also { trCache[k] = it }
            runOnUiThread { if (!isFinishing) done(r ?: msg) }
        }
    }

    private fun cacheFile() = File(filesDir, "translations.json")

    private fun loadTranslationCache() {
        if (trCache.isNotEmpty()) return
        trPool.execute {
            try {
                val o = JSONObject(cacheFile().readText())
                o.keys().forEach { trCache[it] = o.getString(it) }
            } catch (_: Exception) {}
        }
    }

    private fun saveTranslationCache() {
        if (trCache.isEmpty()) return
        val snapshot = HashMap(trCache)
        trPool.execute {
            try {
                val o = JSONObject()
                snapshot.entries.take(8000).forEach { o.put(it.key, it.value) }
                cacheFile().writeText(o.toString())
            } catch (_: Exception) {}
        }
    }

    /** Language list for workers (no password needed). */
    private fun showLanguagePicker() {
        val sheet = BottomSheetDialog(this)
        val current = pageLang().ifEmpty { "ar" }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(20))
        }
        list.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_language)
                imageTintList = ColorStateList.valueOf(Cfg.APEX_BLUE)
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                addView(TextView(context).apply {
                    text = getString(R.string.choose_language)
                    textSize = 20f; setTextColor(Color.parseColor("#262626"))
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = getString(R.string.choose_language_sub)
                    textSize = 13.5f; setTextColor(Color.parseColor("#707070"))
                })
            })
        })
        list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E2E2E2")) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        Cfg.LANGS.forEach { l ->
            val selected = l.code == current
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(12))
                minimumHeight = dp(64)
                isClickable = true
                val tv = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                if (selected) setBackgroundColor(Color.parseColor("#EAF3FC"))
                addView(TextView(context).apply { text = l.flag; textSize = 28f })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    addView(TextView(context).apply {
                        text = l.native; textSize = 18f
                        setTextColor(if (selected) Cfg.APEX_BLUE else Color.parseColor("#262626"))
                        setTypeface(typeface, Typeface.BOLD)
                    })
                    addView(TextView(context).apply {
                        text = l.english; textSize = 13f; setTextColor(Color.parseColor("#707070"))
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (selected) addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(Cfg.APEX_BLUE)
                }, LinearLayout.LayoutParams(dp(26), dp(26)))
                setOnClickListener {
                    sheet.dismiss()
                    if (!selected) setPageLanguage(l)
                }
            })
        }
        sheet.setContentView(ScrollView(this).apply { addView(list) })
        sheet.show()
    }

    private fun setPageLanguage(l: Cfg.Lang) {
        val original = l.code == "ar"
        prefs.edit().putString(Cfg.PAGE_LANG, if (original) "" else l.code).apply()
        applyTranslateCookie()
        if (!original) toast(getString(R.string.translating_to, l.native))

        // App buttons and messages follow: Arabic stays Arabic, any other language uses English.
        val ui = if (original) "ar" else "en"
        prefs.edit().putString(Cfg.APP_LANG, ui).apply()
        if (Cfg.uiLang(this) != ui) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(ui))  // recreates & reloads
        } else if (webView.url.isNullOrBlank()) {
            loadHome()
        } else {
            webView.reload()
        }
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
        saveTranslationCache()
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
