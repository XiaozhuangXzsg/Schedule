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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.university.timetable.ui.component.glassLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.ui.theme.LocalTimetableColors

/** 首次启动展示一次的软件说明中，可点击的 GitHub 地址。 */
private const val GITHUB_URL = "https://github.com/XiaozhuangXzsg/-Schedule"

/** 联系方式（关于页面 / 联系我们弹窗共用）。 */
internal const val CONTACT_EMAIL = "zhuangzhongao@gmail.com"
internal const val CONTACT_BILIBILI = "一只小庄呀"

/** 弹窗内容区最大高度；超出后内部滚动，并显示细滚动条。 */
private val ContentMaxHeight = 360.dp

/** 周视图 ⋯ 菜单里「联系我们」对应的独立弹窗（不再跳转校园生活）。 */
@Composable
fun ContactDialog(onDismiss: () -> Unit) {
    val colors = LocalTimetableColors.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Text(
                text = "联系我们",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "使用中遇到问题，或是有新的想法，都欢迎告诉作者。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )

            Spacer(Modifier.height(14.dp))

            ContactRow(
                icon = Icons.Filled.Email,
                title = "邮箱",
                body = "zhuangzhongao@gmail.com",
                hint = "若发现软件问题，请发邮件联系。"
            )
            Spacer(Modifier.height(10.dp))
            ContactRow(
                icon = Icons.Filled.PlayArrow,
                title = "B 站",
                body = "一只小庄呀",
                hint = "也可以直接在 B 站私信联系。"
            )

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "若有新的想法，也欢迎提出建议。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
        }
    }
}

@Composable
private fun ContactRow(
    icon: ImageVector,
    title: String,
    body: String,
    hint: String
) {
    val colors = LocalTimetableColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        }
    }
}

/**
 * 首次启动的软件说明浮窗（只自动弹出一次）。
 *
 * - 内容超出可视范围时可上下滑动，右侧有细滚动条指示位置；
 * - GitHub 地址可点击跳转；
 * - 底部固定「知道啦」按钮，容易找到，随时可关闭。
 */
@Composable
fun IntroDialog(onDismiss: () -> Unit) {
    val colors = LocalTimetableColors.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Text(
                text = "关于「娅娅课表」",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "第一次见面，先说几句～",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )

            Spacer(Modifier.height(12.dp))

            // 内容区 + 右侧细滚动条；正文预留 end padding，滚动条不压字
            Box(modifier = Modifier.heightIn(max = ContentMaxHeight)) {
                Column(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .verticalScroll(scrollState)
                ) {
                    // 本次提供的图片：放在说明最前面
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                id = com.university.timetable.R.drawable.intro_mascot
                            ),
                            contentDescription = "软件吉祥物",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    }

                    IntroParagraph("本软件由兰大数学院 zza 制作。软件更新与修复由 ChatGPT Codex、Google Gemini、DeepSeek Harness 提供技术支持。")
                    IntroParagraph("开源项目：GitHub，更新软件请前往 GitHub。")

                    // GitHub 可点击入口
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .clickable { runCatching { uriHandler.openUri(GITHUB_URL) } }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "前往 GitHub",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = GITHUB_URL,
                                fontSize = 10.sp,
                                color = colors.mutedText
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    IntroParagraph("感谢高松灯为软件制作提供动力。")
                    IntroParagraph("本软件完全开源免费，由全人类智慧结晶而成。严禁用于盈利或二次售卖。")
                    IntroParagraph("咕咕嘎嘎，关注塔菲喵，灌注塔菲谢谢喵。")
                }

                ThinScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("知道啦")
            }
        }
    }
}

/** 说明弹窗里的一个段落。 */
@Composable
private fun IntroParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
}

/**
 * 细滚动条。
 *
 * - 内容未超出可视范围时不显示；
 * - 视觉宽度 4dp，高度与位置按滚动进度等比计算，与正文互不遮挡；
 * - 与整体风格一致（低对比底色 + 中性滑块）。
 */
@Composable
private fun ThinScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    val colors = LocalTimetableColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var trackHeightPx by remember { mutableIntStateOf(0) }

    // maxValue 与 viewportSize 会随内容变化重组刷新
    val maxScroll = scrollState.maxValue
    val viewport = scrollState.viewportSize

    Box(
        modifier = modifier
            .width(16.dp)
            .heightIn(max = ContentMaxHeight)
            .onSizeChanged { trackHeightPx = it.height }
    ) {
        if (maxScroll <= 0 || trackHeightPx <= 0 || viewport <= 0) return@Box

        val total = (maxScroll + viewport).coerceAtLeast(1)
        val fraction = (viewport.toFloat() / total).coerceIn(0.12f, 1f)
        val trackHeightDp = (trackHeightPx / density).dp
        val thumbHeight = (trackHeightDp * fraction).coerceAtLeast(24.dp)
        val progress = (scrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f)
        val offsetY = (trackHeightDp - thumbHeight) * progress

        // 轨道（很淡），避免看起来是缺口
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(2.dp)
                .height(trackHeightDp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        )
        // 滑块
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = offsetY)
                .width(2.dp)
                .height(thumbHeight)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.mutedText.copy(alpha = 0.6f))
        )
    }
}
