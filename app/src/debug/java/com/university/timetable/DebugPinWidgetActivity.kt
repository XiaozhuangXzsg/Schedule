package com.university.timetable

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.university.timetable.widget.FullScheduleWidgetReceiver

/**
 * 仅 debug 变体存在的调试入口：**自己当宿主**，把「待办 + 课表」4×2 卡片渲染出来。
 *
 * 为什么需要它：这台 ColorOS 设备上，第三方卡片没法用 adb 命令放到桌面上——
 * `cmd appwidget` 没有实现；`APPWIDGET_PICK` 需要调用方先持有 appWidgetId；
 * 启动器侧只有「卡片中心」（只收录 OPPO 自家卡片）。
 * 于是用一个普通 [AppWidgetHost] 在 Activity 里把真实的 RemoteViews 渲染出来，
 * 用来核对留白、行距与背景透明度这些**只能用眼睛看**的东西。
 *
 * 启动方式：
 * `adb shell am start -n com.university.timetable.debug/com.university.timetable.DebugPinWidgetActivity`
 */
class DebugPinWidgetActivity : ComponentActivity() {

    private lateinit var host: AppWidgetHost
    private lateinit var stage: FrameLayout
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val provider: ComponentName
        get() = RELEASE_PROVIDER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 用高对比渐变当底：卡片背景透明度一变，透出来的底色立刻能看出来。
        stage = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#FF3D7E"), Color.parseColor("#2BD2FF"))
            )
        }
        setContentView(stage)

        val manager = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, HOST_ID).also { it.startListening() }
        appWidgetId = host.allocateAppWidgetId()

        if (manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)) {
            afterBind()
        } else {
            // 系统会弹出「允许绑定微件？」确认框，允许后再渲染。
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                },
                REQUEST_BIND
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BIND) afterBind()
    }

    /** 必须先绑定成功再写选项，否则系统侧 `updateAppWidgetOptions` 会 NPE。 */
    private fun afterBind() {
        val widthDp = intent.getIntExtra(EXTRA_WIDTH_DP, CELL_WIDTH_DP)
        val heightDp = intent.getIntExtra(EXTRA_HEIGHT_DP, CELL_HEIGHT_DP)
        // Glance 的 SizeMode.Exact 会读这四个值来决定排版尺寸。
        AppWidgetManager.getInstance(this).updateAppWidgetOptions(
            appWidgetId,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
        )
        render(widthDp, heightDp)
    }

    private fun render(widthDp: Int, heightDp: Int) {
        val manager = AppWidgetManager.getInstance(this)
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return
        stage.removeAllViews()

        val title = TextView(this).apply {
            text = "宿主模拟卡片（${widthDp}×${heightDp} dp）"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        stage.addView(
            title,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
        )

        val widgetView = host.createView(this, appWidgetId, info)
        stage.addView(
            widgetView,
            // AppWidgetHostView 自己会吃掉 8dp 一圈的内边距，所以外层多给 16dp，
            // 让卡片本体正好是给定的尺寸。
            FrameLayout.LayoutParams(
                dp(widthDp + HOST_INSET_DP * 2),
                dp(heightDp + HOST_INSET_DP * 2)
            ).apply {
                gravity = Gravity.CENTER
            }
        )
    }

    override fun onDestroy() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { host.deleteAppWidgetId(appWidgetId) }
        }
        runCatching { host.stopListening() }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val HOST_ID = 0x7A7A
        const val REQUEST_BIND = 0x51
        const val CELL_WIDTH_DP = 250
        const val CELL_HEIGHT_DP = 110
        const val HOST_INSET_DP = 8
        const val EXTRA_WIDTH_DP = "widthDp"
        const val EXTRA_HEIGHT_DP = "heightDp"

        /**
         * 直接绑定 **release 包** 的卡片提供者。
         *
         * 调试包自己的课表数据是另一套（备份不一定还原），用它渲染出来的不是真实课表；
         * release 包里才是用户真正的课程与偏好，所以宿主指向 release 的 receiver。
         */
        val RELEASE_PROVIDER: ComponentName = ComponentName(
            "com.university.timetable",
            "com.university.timetable.widget.FullScheduleWidgetReceiver"
        )
    }
}
