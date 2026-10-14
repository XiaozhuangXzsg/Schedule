package com.university.timetable.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.university.timetable.ServiceLocator
import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.importer.LzuTimetableParser
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.ui.theme.UniversityTimetableTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在应用内打开兰州大学教务系统，登录并导航到「本学期课表」后，
 * 点右下角「导入课表」即可自动识别并导入。
 *
 * 实现要点：
 * - 用 **WebView 内嵌页面**（而非自建网络请求），因此登录、验证码、CAS 跳转都由网页自己完成，
 *   Cookie 由 [CookieManager] 持久化，下次打开仍是登录态。
 * - 导入时通过 `evaluateJavascript` 取 `document.documentElement.outerHTML`：
 *   JS 字符串本身就是 Unicode，**天然绕开页面 GBK 编码问题**，无需额外转码。
 * - 用 [LzuTimetableParser] 解析课表；若页面表头带作息时间，一并同步到本地设置。
 */
class LzuImportActivity : ComponentActivity() {

    /** 供 JS 回调的桥接对象。 */
    private class HtmlBridge {
        var onHtml: ((String) -> Unit)? = null

        @android.webkit.JavascriptInterface
        fun onResult(html: String?) {
            onHtml?.invoke(html.orEmpty())
        }
    }

    private val bridge = HtmlBridge()
    private var webView: WebView? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var pageTitle by mutableStateOf("兰州大学教务")
    private var pageUrl by mutableStateOf(START_URL)
    private var loading by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var importedCount by mutableStateOf<Int?>(null)
    private var errorText by mutableStateOf<String?>(null)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 返回键：先让网页后退，退无可退再关闭
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val web = webView
                if (web != null && web.canGoBack()) web.goBack() else finish()
            }
        })

        val startUrl = intent?.getStringExtra(EXTRA_START_URL)?.takeIf { it.isNotBlank() } ?: START_URL

        setContent {
            UniversityTimetableTheme {
                LzuImportScreen(
                    title = pageTitle,
                    url = pageUrl,
                    loading = loading,
                    busy = busy,
                    message = message,
                    importedCount = importedCount,
                    errorText = errorText,
                    onBack = { finish() },
                    onReload = { webView?.reload() },
                    onOpenInBrowser = {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(pageUrl)))
                        }
                    },
                    onImport = { importFromPage() },
                    onDismissResult = {
                        importedCount = null
                        errorText = null
                    },
                    onFinish = { finish() },
                    webViewFactory = { context ->
                        createWebView(context, startUrl)
                    }
                )
            }
        }
    }

    private fun createWebView(context: Context, startUrl: String): WebView {
        val web = WebView(context)
        web.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // 教务系统分两套界面，UA 得跟着入口走：
            // - `jwk.lzu.edu.cn/academic/index_frame.jsp` 是**手机版**框架页，只有手机 UA 才渲染移动布局；
            // - 其余页面（个人工作台等）反过来在手机 UA 下会布局错乱，仍按原来的桌面 UA 处理。
            userAgentString = if (startUrl.contains("jwk.lzu.edu.cn")) {
                userAgentString
            } else {
                userAgentString.replace("Mobile", "").replace("Android", "Android Desktop")
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.addJavascriptInterface(bridge, JS_BRIDGE)

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
                url?.let { pageUrl = it }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                url?.let { pageUrl = it }
                view?.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
            }
        }

        webView = web
        web.loadUrl(startUrl)
        return web
    }

    /**
     * 在页面里执行 JS，取出整页 HTML 后解析导入。
     * 优先取课表表格，取不到再退回整页。
     */
    private fun importFromPage() {
        val web = webView ?: return
        busy = true
        errorText = null
        importedCount = null
        message = "正在读取页面…"

        bridge.onHtml = { html ->
            busy = false
            message = null
            if (html.isBlank()) {
                errorText = "没有读取到页面内容，请等页面加载完成后再试"
            } else {
                handleHtml(html)
            }
        }

        val js = """
            (function() {
              try {
                var t = document.getElementById('timetable');
                if (t) {
                  var wrap = document.createElement('div');
                  wrap.appendChild(t.cloneNode(true));
                  ${JS_BRIDGE}.onResult(wrap.innerHTML);
                  return;
                }
              } catch (e) {}
              try {
                ${JS_BRIDGE}.onResult(document.documentElement.outerHTML);
              } catch (e) {
                ${JS_BRIDGE}.onResult('');
              }
            })();
        """.trimIndent()

        web.evaluateJavascript(js, null)
    }

    private fun handleHtml(html: String) {
        if (!LzuTimetableParser.looksLikeLzuTimetable(html)) {
            errorText = "当前页面看起来不是课表页面。\n请先登录，并进入「个人课表 / 本学期课表」页面后再点导入。"
            return
        }

        scope.launch {
            busy = true
            message = "正在解析课表…"
            val parsed = withContext(Dispatchers.Default) { LzuTimetableParser.parse(html) }
            val result = parsed.result

            if (result.isEmpty) {
                busy = false
                message = null
                errorText = "没有识别到课程。请确认页面已完整加载，或换用「大节课表」页面再试。"
                return@launch
            }

            // 写入数据库
            message = "正在导入 ${result.courseCount} 门课程…"
            val repo = ServiceLocator.schedule(applicationContext)
            withContext(Dispatchers.IO) {
                repo.replaceSchedule(
                    result.courses.map { it.course to it.sessions }
                )
                // 若页面表头带作息时间，同步更新本地作息设置
                if (parsed.periodTimes.isNotEmpty()) {
                    repo.savePeriodTimes(
                        if (parsed.periodTimes.size >= DefaultPeriodTimes.ALL.size) {
                            parsed.periodTimes
                        } else {
                            // 少于默认节次时补齐，避免后面的节次丢失
                            val byPeriod = parsed.periodTimes.associateBy { it.period }
                            DefaultPeriodTimes.ALL.map { byPeriod[it.period] ?: it }
                        }
                    )
                }
            }
            busy = false
            message = null
            importedCount = result.courseCount
        }
    }

    override fun onDestroy() {
        webView?.let { web ->
            web.removeJavascriptInterface(JS_BRIDGE)
            web.destroy()
        }
        webView = null
        bridge.onHtml = null
        super.onDestroy()
    }

    companion object {
        private const val JS_BRIDGE = "LzuBridge"

        /**
         * 兰州大学教务入口。
         *
         * 用 `academic/index_frame.jsp`（**手机版**）而不是 `http://my.lzu.edu.cn/`（个人工作台）：
         * 工作台是桌面布局的门户，手机上要缩放拖拽才能点，且登录后还要自己找教务入口；
         * index_frame 是教务系统的移动版框架页，进去就是「个人课表」等菜单。
         */
        const val START_URL = "https://jwk.lzu.edu.cn/academic/index_frame.jsp"

        /** 直接用课表页打开时可通过该 extra 指定。 */
        const val EXTRA_START_URL = "start_url"

        fun intent(context: Context, startUrl: String? = null): Intent =
            Intent(context, LzuImportActivity::class.java).apply {
                if (!startUrl.isNullOrBlank()) putExtra(EXTRA_START_URL, startUrl)
            }
    }
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

@Composable
private fun LzuImportScreen(
    title: String,
    url: String,
    loading: Boolean,
    busy: Boolean,
    message: String?,
    importedCount: Int?,
    errorText: String?,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onImport: () -> Unit,
    onDismissResult: () -> Unit,
    onFinish: () -> Unit,
    webViewFactory: (Context) -> WebView
) {
    val colors = LocalTimetableColors.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "兰州大学教务" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenInBrowser, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.OpenInBrowser,
                        contentDescription = "用浏览器打开",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (loading) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 页面提示条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "已为你打开教务系统的手机版界面，请先登录，再打开「个人课表」，" +
                        "然后点击本软件的「导入课表」按钮。\n\n" +
                        "顺序：① 登录教务系统 → ② 打开「个人课表」 → " +
                        "③ 点击本软件右下角的「导入课表」。\n\n" +
                        "注意：「个人课表」是教务系统网页里的菜单，" +
                        "「导入课表」是本软件右下角的按钮，两者不是同一个东西。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // 网页
            AndroidView(
                factory = webViewFactory,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 右下角导入按钮
        ExtendedFloatingActionButton(
            onClick = onImport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            },
            text = { Text(if (busy) "处理中…" else "导入课表") }
        )

        // 进度提示
        message?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(msg, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    // 成功 / 失败弹窗
    if (importedCount != null || errorText != null) {
        Dialog(onDismissRequest = onDismissResult) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                if (importedCount != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "导入成功",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "已从教务系统导入 $importedCount 门课程，" +
                            "并同步了课表作息时间。原有课表已被替换。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "导入失败",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismissResult, modifier = Modifier.weight(1f)) {
                        Text(if (importedCount != null) "继续浏览" else "重试")
                    }
                    Button(
                        onClick = {
                            onDismissResult()
                            if (importedCount != null) onFinish()
                        },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(if (importedCount != null) "完成" else "知道了")
                    }
                }
            }
        }
    }
}
