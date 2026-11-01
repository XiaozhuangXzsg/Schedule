package com.university.timetable

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalView
import com.university.timetable.ui.MainViewModel
import com.university.timetable.ui.TimetableAppRoot
import com.university.timetable.ui.ai.AiViewModel
import com.university.timetable.ui.component.LocalHapticsEnabled
import com.university.timetable.ui.theme.UniversityTimetableTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(applicationContext)
    }

    /** AI 助手与其工具共用本 Activity 的 ViewModel（倒计时、写入课表）。 */
    private val aiViewModel: AiViewModel by viewModels {
        AiViewModel.factory(applicationContext)
    }

    /** 应用内更新：AndroidViewModel，默认工厂即可，无需自定义。 */
    private val updateViewModel: com.university.timetable.update.UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 用户从「数据已恢复」的通知或图标回到应用，这条通知就没必要留着了。
        androidx.core.app.NotificationManagerCompat.from(this)
            .cancel(com.university.timetable.backup.AppRestarter.NOTIFICATION_ID)

        val openToday = intent?.action == ACTION_OPEN_TODAY

        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = if (prefs.followSystemTheme) systemDark else prefs.darkTheme

            // 通知权限（Android 13+）
            var askedPermission by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedPermission) {
                    askedPermission = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            UniversityTimetableTheme(
                darkTheme = dark,
                themePresetIndex = prefs.themePreset,
                columbina = prefs.columbinaStyle,
                liquidGlass = prefs.liquidGlass
            ) {
                // 触感反馈开关：在整棵 UI 树上生效，子组件通过 rememberSoftHaptics() 取用。
                CompositionLocalProvider(
                    LocalHapticsEnabled provides prefs.hapticFeedback
                ) {
                    // 系统状态栏的文字/图标颜色跟随深浅模式：
                    // 浅色模式用深色图标，深色模式用浅色图标，两种模式下都清晰可见。
                    val view = LocalView.current
                    if (!view.isInEditMode) {
                        SideEffect {
                            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                            controller.isAppearanceLightStatusBars = !dark
                            controller.isAppearanceLightNavigationBars = !dark
                        }
                    }

                    TimetableAppRoot(
                        viewModel = viewModel,
                        aiViewModel = aiViewModel,
                        updateViewModel = updateViewModel,
                        darkTheme = dark,
                        startOnToday = openToday
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 重新核对日期 / 时区 / 教学周，避免从后台回来时仍显示昨天的数据。
        // 桌面卡片的前台刷新由 TimetableApp 的应用级生命周期回调统一处理。
        viewModel.onEnterForeground()
    }

    companion object {
        const val ACTION_OPEN_TODAY = "com.university.timetable.OPEN_TODAY"
    }
}

/**
 * 悬浮的 AI 助手入口按钮。
 * 放在这里而不是各页面里，保证三个页面都能一键唤起。
 */
@Composable
fun AiAssistantFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
            Icon(Icons.Filled.SmartToy, contentDescription = null)
        },
        text = { Text("AI 助手") }
    )
}

@Suppress("unused")
private val unusedAiIcons = listOf(Icons.Filled.Mic, Icons.Filled.Send)
