package com.university.timetable.ui.screen.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.ui.MainViewModel
import com.university.timetable.ui.component.LiquidGlassSwitch
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.ui.theme.ThemePresets
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 课表设置：学期起始日、总周数、显示项与提醒。 */
@Composable
fun TimetableSettingsDialog(
    prefs: UiPreferences,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val colors = LocalTimetableColors.current
    var semesterStart by remember { mutableStateOf(prefs.semesterStart) }
    var totalWeeks by remember { mutableFloatStateOf(prefs.totalWeeks.toFloat()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "课表设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))

            Text("第 1 周周一", style = MaterialTheme.typography.labelLarge, color = colors.mutedText)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateStepper(
                    value = semesterStart,
                    onChange = { semesterStart = it }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "总周数：${totalWeeks.toInt()} 周",
                style = MaterialTheme.typography.labelLarge,
                color = colors.mutedText
            )
            Slider(
                value = totalWeeks,
                onValueChange = { totalWeeks = it },
                valueRange = 8f..30f,
                steps = 21
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            SettingSwitch("显示授课教师", prefs.showTeacher) { viewModel.setShowTeacher(it) }
            SettingSwitch("显示上课地点", prefs.showLocation) { viewModel.setShowLocation(it) }
            SettingSwitch("显示单双周标记", prefs.showWeekTag) { viewModel.setShowWeekTag(it) }
            SettingSwitch("网格显示周末", prefs.showWeekend) { viewModel.setShowWeekend(it) }
            SettingSwitch("今日页显示校园生活助手", prefs.showLifeAssistant) { viewModel.setShowLifeAssistant(it) }
            SettingSwitch("上课提醒", prefs.reminderEnabled) { viewModel.setReminderEnabled(it) }
            SettingSwitch("流体云常驻通知", prefs.fluidCloudEnabled) { viewModel.setFluidCloudEnabled(it) }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        viewModel.setSemesterStart(semesterStart)
                        viewModel.setTotalWeeks(totalWeeks.toInt())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.4f)
                ) { Text("保存") }
            }
        }
    }
}

/**
 * 一行「标题 + 开关」的设置项。
 *
 * 开关统一用液态玻璃样式（[LiquidGlassSwitch]），**全应用所有设置项开关都走这里**，
 * 所以换样式只需要改这一处；新增设置项也请继续调用本函数，不要直接用 Material3 的 `Switch`。
 */
@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        LiquidGlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DateStepper(value: String, onChange: (String) -> Unit) {
    val colors = LocalTimetableColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.surfaceCardElevated)
                .clickable {
                    onChange(LocalDate.parse(value).minusWeeks(1).toString())
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "上一周", modifier = Modifier.size(13.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.surfaceCardElevated)
                .clickable {
                    onChange(LocalDate.parse(value).plusWeeks(1).toString())
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "下一周", modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "= 第 1 周",
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText
        )
    }
}

/** 小组件（桌面卡片）使用指引。 */
@Composable
fun WidgetHelpDialog(onDismiss: () -> Unit, viewModel: MainViewModel) {
    val colors = LocalTimetableColors.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "添加桌面卡片",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(14.dp))

            Text(
                text = "本应用提供 4 种桌面卡片（Glance 小组件），内容会自动刷新：",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Bullet("当前/下一节课（2×2）：显示正在上的课，下方显示接下来那节")
            Bullet("待办 + 课表（4×2）：左侧待办（最多 3 条）、右侧课程（最多 5 条，课名与地点）")
            Bullet("今日课表（4×2）：横向卡片，按时间顺序列出今天的课程")
            Bullet("本周课表（4×4）：显示本周每天的课程概览")

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.makeupContainer)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "今天没课也没待办？卡片会自动变成「明日预告」",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onMakeupContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "4×2 卡片此时整卡切换为「明日周X，共有 N 门课要上」，右上角显示明天的日期与周次，" +
                            "下方用 2×2 色块列出明天要上的课程（课名 + 起止时间 + 地点）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onMakeupContainer
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("添加步骤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Numbered(1, "回到桌面，长按桌面空白处")
            Numbered(2, "选择「小组件」（ColorOS 也可通过「桌面设置 → 小组件」）")
            Numbered(3, "在列表中找到「娅娅课表」")
            Numbered(4, "选择要用的卡片（如上表 4 种）拖到桌面")
            Numbered(5, "拖动卡片边缘可调整大小，卡片内容会按新尺寸自动排布")
            Numbered(6, "若列表里只看到部分尺寸，把卡片拖大/拖小即可切换网格")

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.makeupContainer)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "卡片显示「载入窗口小部件时出现问题」？",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onMakeupContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "1）先打开一次本应用（首次启动需要写入课表数据），卡片会自动刷新。\n" +
                            "2）该错误只要出现一次就会常驻；但从 1.8.6 起，应用发出的新卡片内容一旦渲染成功，" +
                            "桌面上的卡片会自动恢复正常，一般无需手动处理。\n" +
                            "3）若长时间仍是错误页，长按卡片 → 移除，然后重新添加一张；" +
                            "或到系统「设置 → 应用 → 娅娅课表 → 存储」清除缓存后重试。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onMakeupContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // 手动刷新入口：正常情况下卡片会随数据变更 / 打开应用 / 回到前台自动刷新，
            // 这里是给用户的一个「立刻刷一次」的兜底手段（只重读数据库并重绘，不改任何数据）。
            Text(
                text = "卡片没有及时变化？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "应用会在保存待办 / 课程后、启动时和从后台回到前台时自动刷新卡片，通常无需手动操作。" +
                    "若卡片仍是旧内容，可点下方按钮立即重读本地数据并重绘。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (refreshing) return@Button
                    refreshing = true
                    refreshMessage = null
                    scope.launch {
                        val ok = runCatching { viewModel.refreshWidgetsNow() }.getOrDefault(false)
                        refreshing = false
                        // 明确区分「请求已提交」与「桌面已重绘」：接口不报错不等于桌面已经变了
                        refreshMessage = if (ok) {
                            "刷新请求已提交。请回到桌面查看；若仍未变化，可稍后重试或重新添加卡片。"
                        } else {
                            "刷新请求失败，可稍后重试（不会影响已保存的数据）。"
                        }
                    }
                },
                enabled = !refreshing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (refreshing) "刷新中…" else "立即刷新桌面卡片")
            }
            refreshMessage?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
        }
    }
}

@Composable
private fun Numbered(index: Int, text: String) {
    val colors = LocalTimetableColors.current
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 背景个性化：主题色、单页背景色、背景图透明度与模糊。 */
@Composable
fun BackgroundSettingsDialog(
    viewModel: MainViewModel,
    prefs: UiPreferences,
    onDismiss: () -> Unit
) {
    val colors = LocalTimetableColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setPageBackgroundImage(com.university.timetable.data.prefs.PageKeys.TODAY, uri.toString())
            viewModel.setPageBackgroundImage(com.university.timetable.data.prefs.PageKeys.WEEK, uri.toString())
            viewModel.setPageBackgroundImage(com.university.timetable.data.prefs.PageKeys.SETTINGS, uri.toString())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "个性化与背景",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))

            Text("主题色", style = MaterialTheme.typography.labelLarge, color = colors.mutedText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemePresets.all.forEachIndexed { index, preset ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(preset.seedLight)
                            .clickable { viewModel.setThemePreset(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (prefs.themePreset == index) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("自定义背景图", style = MaterialTheme.typography.labelLarge, color = colors.mutedText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { imagePicker.launch("image/*") }) { Text("从相册选择") }
                TextButton(onClick = {
                    listOf(
                        com.university.timetable.data.prefs.PageKeys.TODAY,
                        com.university.timetable.data.prefs.PageKeys.WEEK,
                        com.university.timetable.data.prefs.PageKeys.SETTINGS
                    ).forEach { viewModel.setPageBackgroundImage(it, "") }
                }) { Text("移除背景图") }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "背景图透明度：${(prefs.backgroundImageAlpha * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = prefs.backgroundImageAlpha,
                onValueChange = { viewModel.setBackgroundImageAlpha(it) },
                valueRange = 0f..1f
            )

            Text(
                "背景图模糊度：${prefs.backgroundImageBlur.toInt()} dp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = prefs.backgroundImageBlur,
                onValueChange = { viewModel.setBackgroundImageBlur(it) },
                valueRange = 0f..40f
            )

            Spacer(Modifier.height(10.dp))
            Text("页面背景色", style = MaterialTheme.typography.labelLarge, color = colors.mutedText)
            Spacer(Modifier.height(8.dp))
            PageColorRow("今日页", com.university.timetable.data.prefs.PageKeys.TODAY, prefs, viewModel)
            PageColorRow("本周页", com.university.timetable.data.prefs.PageKeys.WEEK, prefs, viewModel)
            PageColorRow("设置页", com.university.timetable.data.prefs.PageKeys.SETTINGS, prefs, viewModel)

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun PageColorRow(
    label: String,
    page: String,
    prefs: UiPreferences,
    viewModel: MainViewModel
) {
    val swatches = listOf(
        UiPreferences.UNSET,
        0xFFF6F7FB.toInt(),
        0xFFFDF6F0.toInt(),
        0xFFF1F6FF.toInt(),
        0xFFF2F7F2.toInt(),
        0xFFFBF1F6.toInt(),
        0xFF17181D.toInt()
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(64.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { color ->
                val selected = UiPreferences.backgroundFor(prefs, page) == color
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (color == UiPreferences.UNSET) Color.Transparent else Color(color))
                        .clickable { viewModel.setPageBackground(page, color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (color == UiPreferences.UNSET) {
                        Text("默认", fontSize = 8.sp, color = LocalTimetableColors.current.mutedText)
                    } else if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun todayIso() = LocalDate.now().toString()
