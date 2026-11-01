package com.university.timetable.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.R
import com.university.timetable.data.prefs.NowLineStyle
import com.university.timetable.data.prefs.NowProgressStyle
import com.university.timetable.data.prefs.PastCourseMode
import com.university.timetable.data.prefs.TransitionStyle
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.ui.MainViewModel
import com.university.timetable.ui.component.GlassRadius
import com.university.timetable.ui.component.GlassSurface
import com.university.timetable.ui.component.SectionHeader
import com.university.timetable.ui.screen.dialog.SettingSwitch
import com.university.timetable.ui.theme.LocalIsDark
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.ui.theme.ThemePresets
import com.university.timetable.ui.tutorial.TutorialAnchors
import com.university.timetable.ui.tutorial.TutorialSettingsRow
import com.university.timetable.ui.tutorial.tutorialAnchor
import com.university.timetable.backup.AppRestarter
import com.university.timetable.backup.DataBackup
import com.university.timetable.ui.screen.dialog.RestoreConfirmDialog
import com.university.timetable.update.UpdateConfig
import com.university.timetable.util.ImageLoader
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ABOUT_EMAIL = "zhuangzhongao@gmail.com"
private const val ABOUT_BILIBILI = "一只小庄呀"

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    prefs: UiPreferences,
    contentPadding: PaddingValues,
    onOpenImport: () -> Unit,
    onOpenPeriodEditor: () -> Unit,
    onOpenWidgetHelp: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenAi: () -> Unit = {},
    onPlayEgg: () -> Unit = {},
    onOpenTutorial: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    updateChecking: Boolean = false
) {
    val colors = LocalTimetableColors.current
    val dark = LocalIsDark.current
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var confirmClearAll by remember { mutableStateOf(false) }
    var editingNickname by remember { mutableStateOf(false) }

    // 头像：用 OpenDocument 而不是 GetContent，这样能申请持久读取权限，重启后仍然能显示。
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.setAvatarUri(uri.toString())
    }

    // ---------------- 数据备份 / 恢复 ----------------
    // 导出走 SAF 的 CreateDocument，用户自己选保存位置（下载目录、网盘等），不落在应用缓存里。
    val backupScope = rememberCoroutineScope()
    var backupBusy by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var restorePending by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showChangelog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = true
        backupScope.launch {
            val result = DataBackup.export(context, uri)
            backupBusy = false
            backupMessage = result.message
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 先让用户确认，再真正覆盖本地数据。
        pendingRestoreUri = uri
    }

    // 恢复后必须重启进程：Room 与 DataStore 才会重新打开恢复后的文件，避免内存里的旧状态回写。
    // 系统不允许应用在后台把自己拉到前台（Android 14+ 的后台启动限制），
    // 所以恢复后只能退出；另发一条「数据已恢复」的通知做兜底，用户在通知栏里点一下也能回来。
    LaunchedEffect(restorePending) {
        if (restorePending) {
            delay(1600)
            AppRestarter.restartLater(context)
            AppRestarter.killNow()
        }
    }

    if (editingNickname) {
        NicknameDialog(
            initial = prefs.nickname,
            onDismiss = { editingNickname = false },
            onConfirm = {
                viewModel.setNickname(it)
                editingNickname = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 72.dp
        )
    ) {
        item {
            // 注意：一个 item 里只放**一个**根布局。LazyColumn 的 item 内容不是自动排布的
            // 纵向容器，并列写多个 Composable 时它们会叠在同一位置，导致后写的头像昵称丢失。
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "个性化你的课表与校园生活",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedText
                    )
                }
                Spacer(Modifier.height(18.dp))

                // 头像 + 昵称（居中，可点击修改）
                ProfileHeader(
                    nickname = prefs.nickname,
                    avatarUri = prefs.avatarUri,
                    gilded = prefs.columbinaStyle,
                    onPickAvatar = { avatarPicker.launch(arrayOf("image/*")) },
                    onResetAvatar = { viewModel.setAvatarUri("") },
                    onEditNickname = { editingNickname = true }
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // 智能导入
        item {
            SettingsGroup(title = "课表数据") {
                Box(modifier = Modifier.tutorialAnchor(TutorialAnchors.SETTINGS_IMPORT)) {
                    SettingsRow(
                        icon = Icons.Filled.Code,
                        title = "智能导入课表",
                        subtitle = "HTML / CSV / 截图 OCR 识别",
                        onClick = onOpenImport
                    )
                }
                SettingsRow(
                    icon = Icons.Filled.Schedule,
                    title = "作息时间设置",
                    subtitle = "修改每节课的开始与结束时间",
                    onClick = onOpenPeriodEditor
                )
                Box(modifier = Modifier.tutorialAnchor(TutorialAnchors.WIDGET_ENTRY)) {
                    SettingsRow(
                        icon = Icons.Filled.Widgets,
                        title = "添加桌面卡片",
                        subtitle = "4 种尺寸的桌面卡片，内容自动刷新",
                        onClick = onOpenWidgetHelp
                    )
                }
                TutorialSettingsRow(onOpenTutorial = onOpenTutorial)
                SettingsRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = "清空全部课程",
                    subtitle = "导入前建议先清空，避免重复",
                    destructive = true,
                    onClick = { confirmClearAll = true }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // AI 助手
        item {
            SettingsGroup(title = "AI 助手") {
                Box(modifier = Modifier.tutorialAnchor(TutorialAnchors.AI_ENTRY)) {
                    SettingsRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "打开 AI 助手",
                        subtitle = "对话管理课表、待办与校园生活；支持截图识别导入",
                        onClick = onOpenAi
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 个性化
        item {
            Box(modifier = Modifier.tutorialAnchor(TutorialAnchors.PERSONALIZE_ENTRY)) {
            SettingsGroup(title = "个性化") {
                // 页面转场动画
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "页面转场动画",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            TransitionStyle.CROSSFADE to "交叉淡化",
                            TransitionStyle.ZOOM to "缩放淡入",
                            TransitionStyle.FLUID to "流体滑入"
                        ).forEach { (value, label) ->
                            val selected = prefs.transitionStyle == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { viewModel.setTransitionStyle(value) }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "子页面（校园生活、AI 助手等）的打开与返回动画",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )
                }

                // 柔性反馈：开关、切页、长按时的极轻触感
                SettingSwitch("触感反馈", prefs.hapticFeedback) { viewModel.setHapticFeedback(it) }
                Text(
                    text = "开关、切换页面、长按课程时的轻微震动；关闭后完全不震",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(6.dp))

                // 已上课程显示方式
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "已上课程显示",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PastCourseMode.DIM to "置灰显示",
                            PastCourseMode.HIDE to "隐藏"
                        ).forEach { (value, label) ->
                            val selected = prefs.pastCourseMode == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { viewModel.setPastCourseMode(value) }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "今日课程列表里已经结束的课程如何显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )
                }

                // 外观与主题（并入个性化）
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "主题色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemePresets.all.forEachIndexed { index, preset ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(preset.seedLight)
                                    .clickable {
                                        // 选主题色 = 想用普通风格，顺手关掉哥伦比娅风格，
                                        // 否则调色板会被风格配色盖掉、看起来「点了没反应」。
                                        if (prefs.columbinaStyle) viewModel.setColumbinaStyle(false)
                                        viewModel.setThemePreset(index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (prefs.themePreset == index && !prefs.columbinaStyle) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (prefs.columbinaStyle) "当前使用「娅娅 · 哥伦比娅」风格，选择上方任意主题色即可切换回普通风格"
                        else ThemePresets.byIndex(prefs.themePreset).name,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )
                }

                SettingSwitch(
                    "哥伦比娅风格（娅娅）",
                    prefs.columbinaStyle
                ) { viewModel.setColumbinaStyle(it) }
                Text(
                    text = "启用后整体换用蔷薇·丁香配色，并加入白羽与星钻点缀；关闭即回到普通主题色",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingSwitch(
                    "液态玻璃风格",
                    prefs.liquidGlass
                ) { viewModel.setLiquidGlass(it) }
                Text(
                    text = "更薄更透的玻璃面板：带镜面扫光、环境光背景与柔和投影，动画也会更弹性；关闭则回到原来的凝光样式",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingSwitch(
                    "跟随系统深浅色",
                    prefs.followSystemTheme
                ) { viewModel.setFollowSystem(it) }

                if (!prefs.followSystemTheme) {
                    SettingsRow(
                        icon = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        title = if (dark) "切换为日间模式" else "切换为夜间模式",
                        subtitle = null,
                        onClick = { viewModel.setDarkTheme(!dark) }
                    )
                }

                SettingsRow(
                    icon = Icons.Filled.ColorLens,
                    title = "背景与透明度",
                    subtitle = "主题色、自定义背景图、模糊度",
                    onClick = onOpenBackground
                )

                // 桌面卡片外观：目前只有背景透明度（配色 / 深浅色跟随上面的主题设置）
                WidgetAlphaSlider(
                    current = prefs.widgetBgAlpha,
                    onCommit = { viewModel.setWidgetBgAlpha(it) }
                )
            }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 显示
        item {
            SettingsGroup(title = "显示") {
                SettingSwitch("简洁模式（极简色块）", prefs.simpleMode) { viewModel.setSimpleMode(it) }
                SettingSwitch("显示授课教师", prefs.showTeacher) { viewModel.setShowTeacher(it) }
                SettingSwitch("显示上课地点", prefs.showLocation) { viewModel.setShowLocation(it) }
                SettingSwitch("显示单双周标记", prefs.showWeekTag) { viewModel.setShowWeekTag(it) }
                SettingSwitch("周视图显示周末", prefs.showWeekend) { viewModel.setShowWeekend(it) }
                SettingSwitch("今日页显示生活助手", prefs.showLifeAssistant) { viewModel.setShowLifeAssistant(it) }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 课表显示细节：周视图时间轴条、正在上的课的进度
        item {
            SettingsGroup(title = "课表显示") {
                SettingSwitch("周视图时间轴条", prefs.weekNowLine) { viewModel.setWeekNowLine(it) }
                if (prefs.weekNowLine) {
                    OptionSelector(
                        label = "线型",
                        options = listOf(
                            NowLineStyle.DOT to "细线圆点",
                            NowLineStyle.BOLD to "加粗",
                            NowLineStyle.PLAIN to "仅细线",
                            NowLineStyle.DASHED to "虚线"
                        ),
                        current = prefs.weekNowLineStyle,
                        onSelect = { viewModel.setWeekNowLineStyle(it) }
                    )
                    NowLineColorSelector(
                        current = prefs.weekNowLineColor,
                        onSelect = { viewModel.setWeekNowLineColor(it) }
                    )
                }

                SettingSwitch("正在上的课显示进度", prefs.nowProgress) { viewModel.setNowProgress(it) }
                if (prefs.nowProgress) {
                    OptionSelector(
                        label = "进度样式",
                        options = listOf(
                            NowProgressStyle.LEFT_TO_RIGHT to "从左到右",
                            NowProgressStyle.BOTTOM_TO_TOP to "从下到上"
                        ),
                        current = prefs.nowProgressStyle,
                        onSelect = { viewModel.setNowProgressStyle(it) }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 提醒与流体云
        item {
            SettingsGroup(title = "提醒与流体云") {
                SettingSwitch("上课提醒", prefs.reminderEnabled) { viewModel.setReminderEnabled(it) }
                if (prefs.reminderEnabled) {
                    ReminderLeadSelector(
                        current = prefs.remindBeforeMinutes,
                        onSelect = { viewModel.setRemindBefore(it) }
                    )
                }
                SettingSwitch("流体云常驻通知", prefs.fluidCloudEnabled) {
                    viewModel.setFluidCloudEnabled(it)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 软件更新
        item {
            SettingsGroup(title = "软件更新") {
                SettingsRow(
                    icon = Icons.Filled.SystemUpdateAlt,
                    title = "检查更新",
                    subtitle = if (updateChecking) {
                        "正在检查…"
                    } else {
                        "当前版本 ${UpdateConfig.CURRENT_VERSION_LABEL}"
                    },
                    onClick = { if (!updateChecking) onCheckUpdate() }
                )
                SettingSwitch("自动检查更新", prefs.autoCheckUpdates) {
                    viewModel.setAutoCheckUpdates(it)
                }
                SettingsRow(
                    icon = Icons.Filled.History,
                    title = "更新日志",
                    subtitle = "查看本版本更新内容",
                    onClick = { showChangelog = true }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // 数据与备份
        item {
            SettingsGroup(title = "数据与备份") {
                SettingsRow(
                    icon = Icons.Filled.CloudUpload,
                    title = "导出数据备份",
                    subtitle = if (backupBusy) "正在导出…" else "课表、待办、对话、设置与 AI 配置",
                    onClick = {
                        if (!backupBusy) exportLauncher.launch(DataBackup.suggestedFileName())
                    }
                )
                SettingsRow(
                    icon = Icons.Filled.CloudDownload,
                    title = "导入数据恢复",
                    subtitle = "从之前导出的 .zip 备份恢复",
                    onClick = {
                        if (!backupBusy) {
                            importLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream")
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // 关于
        item {
            SettingsGroup(title = "关于") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "娅娅课表 ${UpdateConfig.CURRENT_VERSION_LABEL}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "作者：兰大数学院 zza。本软件完全开源免费，源码托管在 GitHub，欢迎反馈问题与建议。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )

                    Spacer(Modifier.height(10.dp))

                    // 可点击的 GitHub 仓库
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .clickable {
                                runCatching { uriHandler.openUri(UpdateConfig.REPO_URL) }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "开源项目 · GitHub",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = UpdateConfig.REPO_URL,
                                fontSize = 10.sp,
                                color = colors.mutedText
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    AboutLine(icon = Icons.Filled.Email, text = "问题反馈：$ABOUT_EMAIL")
                    Spacer(Modifier.height(6.dp))
                    AboutLine(icon = Icons.Filled.PlayArrow, text = "B 站：$ABOUT_BILIBILI")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "为大学生设计的课表应用：今日课程列表、本周网格、桌面卡片、" +
                            "流体云常驻通知、OCR 智能导课、校园生活作息与洗衣机倒计时。全部功能免费开放。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Album,
                            contentDescription = null,
                            tint = colors.mutedText,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "OCR 由 Google ML Kit 提供，离线可用",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText
                        )
                    }

                    // 隐藏彩蛋：只播放一遍
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable { onPlayEgg() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "不要点我",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空全部课程？") },
            text = { Text("将删除所有课程与上课时段，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllCourses()
                    confirmClearAll = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            }
        )
    }

    // ---------------- 导入备份：确认后在后台执行 ----------------
    val pendingUri = pendingRestoreUri
    if (pendingUri != null) {
        RestoreConfirmDialog(
            onConfirm = {
                pendingRestoreUri = null
                backupBusy = true
                backupScope.launch {
                    val result = DataBackup.restore(context, pendingUri)
                    backupBusy = false
                    backupMessage = result.message
                    if (result.ok) restorePending = true
                }
            },
            onDismiss = { pendingRestoreUri = null }
        )
    }

    if (backupBusy) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("正在处理数据") },
            text = { Text("请保持应用在前台，不要退出。") },
            confirmButton = { }
        )
    }

    if (backupMessage != null) {
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text(if (restorePending) "恢复完成" else "备份") },
            text = {
                Text(
                    text = backupMessage.orEmpty() +
                        if (restorePending) "\n\n应用将自动重启以加载恢复后的数据；若没有自动打开，请手动启动应用。" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { backupMessage = null }) { Text("知道了") }
            }
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("更新日志") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = UpdateConfig.CURRENT_VERSION_LABEL,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = UpdateConfig.BUILTIN_NOTES,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) { Text("知道了") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangelog = false
                    runCatching { uriHandler.openUri(UpdateConfig.RELEASES_URL) }
                }) { Text("GitHub 发布页") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ProfileHeader(
    nickname: String,
    avatarUri: String,
    gilded: Boolean,
    onPickAvatar: () -> Unit,
    onResetAvatar: () -> Unit,
    onEditNickname: () -> Unit
) {
    val colors = LocalTimetableColors.current
    val context = LocalContext.current
    // 头像解码一次后缓存在 ImageLoader 的 LruCache 里，重组不会重复解码。
    val avatar = remember(avatarUri) {
        if (avatarUri.isBlank()) null else ImageLoader.load(context, avatarUri)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(colors.surfaceCardElevated)
                .then(
                    if (gilded) Modifier.border(1.5.dp, ThemePresets.columbinaGold, CircleShape)
                    else Modifier
                )
                .combinedClickable(
                    onClick = onPickAvatar,
                    onLongClick = onResetAvatar
                ),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar.asImageBitmap(),
                    contentDescription = "修改头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 默认头像就是应用图标（娅娅抱着课程表那张）。
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_fg),
                    contentDescription = "修改头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = nickname,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "修改昵称",
                tint = colors.mutedText,
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEditNickname)
            )
        }

        Spacer(Modifier.height(3.dp))
        Text(
            text = "点头像换图（长按恢复默认），点铅笔改名",
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText
        )
    }
}

@Composable
private fun NicknameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改昵称") },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 16) draft = it },
                    singleLine = true,
                    label = { Text("昵称") },
                    supportingText = { Text("最多 16 个字，留空恢复默认「${UiPreferences.DEFAULT_NICKNAME}」") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        SectionHeader(title = title)
        Spacer(Modifier.height(8.dp))
        // ColorOS 17 凝光视效：分组卡片改用统一的玻璃材质（半透明底 + 厚度渐变 + 光感描边）。
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(GlassRadius.Card)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp)) {
                content()
            }
        }
    }
}

/** 关于页面里的一行「图标 + 文字」。 */
@Composable
private fun AboutLine(icon: ImageVector, text: String) {
    val colors = LocalTimetableColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val colors = LocalTimetableColors.current
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
            }
        }
    }
}

@Composable
private fun ReminderLeadSelector(current: Int, onSelect: (Int) -> Unit) {
    val colors = LocalTimetableColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "提前提醒",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 15, 30).forEach { minutes ->
                val selected = current == minutes
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else colors.surfaceCardElevated
                        )
                        .clickable { onSelect(minutes) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$minutes 分",
                        fontSize = 11.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else colors.mutedText,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private val unusedIcons = listOf(Icons.Filled.Refresh, Icons.Filled.Image, Icons.Filled.NotificationsActive)

/**
 * 「标签 + 一排选项」的选择器，样式对齐上面提前提醒那一行，
 * 让新增的线型 / 进度样式选择和设置页现有观感一致。
 */
@Composable
private fun OptionSelector(
    label: String,
    options: List<Pair<Int, String>>,
    current: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LocalTimetableColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, text) ->
                val selected = current == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else colors.surfaceCardElevated
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else colors.mutedText,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 桌面卡片背景透明度。
 *
 * 拖动过程中只改本地草稿值，松手才写偏好并刷新卡片——否则拖动一次会连写十几次 DataStore、
 * 顺带排队十几次卡片刷新。写完 [current] 会变成新值，草稿随之对齐。
 */
@Composable
private fun WidgetAlphaSlider(current: Float, onCommit: (Float) -> Unit) {
    val colors = LocalTimetableColors.current
    var draft by remember(current) { mutableStateOf(current) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "桌面卡片背景透明度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "调低后桌面卡片会透出壁纸，只影响卡片，不影响应用内页面",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${(draft * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            valueRange = UiPreferences.MIN_WIDGET_BG_ALPHA..1f
        )
    }
}

/**
 * 时间轴条颜色选择：第一枚是「跟随主题」，其余取自主题色预设的强调色。
 *
 * 复用 [ThemePresets] 而不是另起一套写死的色值，
 * 这样夜间模式下的观感和主题色板始终一致。
 */
@Composable
private fun NowLineColorSelector(current: Int, onSelect: (Int) -> Unit) {
    val colors = LocalTimetableColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "颜色",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 跟随主题：用一个空心圆表示「不指定」
            val follow = current == UiPreferences.UNSET
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceCardElevated)
                    .border(
                        width = if (follow) 2.dp else 1.dp,
                        color = if (follow) MaterialTheme.colorScheme.primary else colors.mutedText,
                        shape = CircleShape
                    )
                    .clickable { onSelect(UiPreferences.UNSET) }
            )
            ThemePresets.all.forEach { preset ->
                val argb = preset.accent.toArgb()
                val selected = current == argb
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(preset.accent)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable { onSelect(argb) }
                )
            }
        }
    }
}
