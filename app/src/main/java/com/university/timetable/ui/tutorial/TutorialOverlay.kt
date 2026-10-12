package com.university.timetable.ui.tutorial

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 新手引导宿主。
 *
 * 用法（放在最外层，包住整个 App 的界面）：
 * ```
 * TutorialHost(
 *     controller = tutorial,
 *     hasCourseData = courses.isNotEmpty(),
 *     onSeen = { viewModel.setTutorialSeen(true) }
 * ) {
 *     // 原来的界面
 * }
 * ```
 *
 * 三个刻意的设计决定：
 * 1. **遮罩用 4 块矩形围出中间的洞**，而不是「整屏遮罩 + 透明洞」。后者在 Compose 里依然会吃掉
 *    落在洞里的点击，用户就点不到高亮目标了 —— 那正是需求里点名要避免的「无法操作」。
 * 2. **锚点缺失时不拦截点击**。页面切走 / 返回后目标可能不在界面上，此时退化成「浅色浮层 + 居中卡片」，
 *    用户仍然可以自由翻页去把目标找回来，不会有遮罩残留卡死。
 * 3. 叠加层画什么**只**由 [TutorialController.state] 决定：没有动画状态机、没有延时任务，
 *    所以中断在任何时刻都不会留下「看不见但还在吃点击」的层。
 *
 * 实现上刻意**不使用任何作用域相关的 `Modifier.align(...)`**（Box/Column/Row 作用域各有各的 align，
 * 混用会导致 "Unresolved reference 'align'"），统一用 `fillMaxSize() + wrapContentSize(align = ...)`，
 * 这样这些函数放在哪个作用域里都能编译。
 */
@Composable
fun TutorialHost(
    controller: TutorialController,
    modifier: Modifier = Modifier,
    hasCourseData: Boolean = true,
    onSeen: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val registry = remember { TutorialAnchorRegistry() }
    val state = controller.state
    // 读取快照 → 建立快照依赖：任何锚点增删/移动都会让引导层重新测量。
    val anchors = registry.snapshot()

    // 每次成功组合之后评估一次「是否可以自动继续」。不是定时器，只在真实布局信号下推进。
    SideEffect { controller.onAnchorsChanged(anchors) }

    // 完成或跳过后落一次「已看过」。
    LaunchedEffect(state.finished) {
        if (state.finished) onSeen()
    }

    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    CompositionLocalProvider(LocalTutorialRegistry provides registry) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { rootSize = it.size }
        ) {
            content()

            if (state.active && rootSize.width > 0 && rootSize.height > 0) {
                Box(Modifier.matchParentSize()) {
                    TutorialLayer(
                        controller = controller,
                        anchors = anchors,
                        rootSize = rootSize,
                        hasCourseData = hasCourseData
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialLayer(
    controller: TutorialController,
    anchors: Map<String, Rect>,
    rootSize: IntSize,
    hasCourseData: Boolean
) {
    val state = controller.state
    val step = controller.current
    val density = LocalDensity.current

    // 颜色全部走主题，跟随深色/浅色模式；不写死 Color(0xFF...)，
    // 避免夜间模式下遮罩与卡片看不清。
    val fullScrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f)
    val softScrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f)
    val ringColor = MaterialTheme.colorScheme.primary

    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2200)
            copied = false
        }
    }

    val copyQq: () -> Unit = {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(
            ClipData.newPlainText("作者 QQ", TutorialSteps.AUTHOR_QQ)
        )
        copied = true
        Toast.makeText(
            context,
            "已复制 QQ：${TutorialSteps.AUTHOR_QQ}（验证信息：${TutorialSteps.AUTHOR_QQ_VERIFY}）",
            Toast.LENGTH_SHORT
        ).show()
    }

    val screenW = rootSize.width.toFloat()
    val screenH = rootSize.height.toFloat()

    // ---------------- 欢迎卡片 ----------------
    if (state.showWelcome || step == null) {
        ScrimQuad(0f, 0f, screenW, screenH, fullScrim, blocking = true)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(align = Alignment.Center)
        ) {
            TutorialCard(
                title = TutorialSteps.WELCOME_TITLE,
                body = TutorialSteps.WELCOME_BODY,
                actionHint = null,
                stepLabel = null,
                primaryLabel = "开始引导",
                secondaryLabel = "暂时跳过",
                showNavHint = false,
                finishContent = false,
                copied = false,
                maxHeight = with(density) { rootSize.height.toDp() } - 64.dp,
                onPrimary = { controller.beginSteps() },
                onSecondary = { controller.skip() },
                onSkip = null,
                onCopy = copyQq,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        }
        return
    }

    val isFinish = step.hideNext
    val anchorId = step.anchor
    val rawBounds = anchorId?.let { anchors[it] }
    val insetPx = with(density) { 6.dp.toPx() }

    val hole: Rect? = rawBounds?.let { b ->
        val l = (b.left + insetPx).coerceAtLeast(0f)
        val t = (b.top + insetPx).coerceAtLeast(0f)
        val r = (b.right - insetPx).coerceAtMost(screenW)
        val bo = (b.bottom - insetPx).coerceAtMost(screenH)
        if (r - l >= 8f && bo - t >= 8f) Rect(l, t, r, bo) else null
    }

    // ---------------- 有洞：四块遮罩围住高亮目标 ----------------
    if (hole != null) {
        // 上
        ScrimQuad(0f, 0f, screenW, hole.top, fullScrim, blocking = true)
        // 下
        ScrimQuad(0f, hole.bottom, screenW, screenH - hole.bottom, fullScrim, blocking = true)
        // 左
        ScrimQuad(0f, hole.top, hole.left, hole.height, fullScrim, blocking = true)
        // 右
        ScrimQuad(hole.right, hole.top, screenW - hole.right, hole.height, fullScrim, blocking = true)

        // 洞的描边，让目标更醒目
        Box(
            modifier = Modifier
                .offset { IntOffset(hole.left.roundToInt(), hole.top.roundToInt()) }
                .size(
                    with(density) { hole.width.toDp() },
                    with(density) { hole.height.toDp() }
                )
                .border(2.dp, ringColor, RoundedCornerShape(12.dp))
        )

        // 卡片优先放在目标下方；下方空间不够就翻到上方，保证不超出屏幕。
        val spaceBelowPx = screenH - hole.bottom
        val placeBelow = spaceBelowPx >= screenH * 0.34f

        val maxHeight = if (placeBelow) {
            (with(density) { spaceBelowPx.toDp() } - 26.dp).coerceAtLeast(170.dp)
        } else {
            (with(density) { hole.top.toDp() } - 26.dp).coerceAtLeast(170.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(
                    align = if (placeBelow) Alignment.TopCenter else Alignment.BottomCenter
                )
        ) {
            TutorialCard(
                title = step.title,
                body = TutorialSteps.bodyOf(step, hasCourseData),
                actionHint = step.actionHint,
                stepLabel = "第 ${state.index + 1}/${controller.total} 步",
                primaryLabel = if (isFinish) "开始使用" else "下一步",
                secondaryLabel = if (isFinish) null else "上一步",
                showNavHint = step.requiresAction,
                finishContent = isFinish,
                copied = copied,
                maxHeight = maxHeight,
                onPrimary = { controller.next() },
                onSecondary = { controller.previous() },
                onSkip = if (isFinish) null else ({ controller.skip() }),
                onCopy = copyQq,
                modifier = if (placeBelow) {
                    Modifier.padding(
                        top = with(density) { hole.bottom.toDp() } + 14.dp,
                        start = 18.dp,
                        end = 18.dp
                    )
                } else {
                    Modifier.padding(
                        bottom = with(density) { (screenH - hole.top).toDp() } + 14.dp,
                        start = 18.dp,
                        end = 18.dp
                    )
                }
            )
        }
        return
    }

    // ---------------- 没洞：目标当前不在界面上（或本步本来就没有目标） ----------------
    // 本步压根不需要目标（④⑤⑩）+ 欢迎页 → 拦截点击，避免误触到下面的界面。
    // 本步有目标但当前不可见（例如要求看课程块、用户还在设置页）→ **不拦截点击**，
    // 用户可以正常翻页/返回去找目标，绝不会被遮罩卡住。
    val blocking = anchorId == null

    ScrimQuad(
        0f, 0f, screenW, screenH,
        if (blocking) fullScrim else softScrim,
        blocking = blocking
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(align = Alignment.Center)
    ) {
        TutorialCard(
            title = step.title,
            body = TutorialSteps.bodyOf(step, hasCourseData),
            actionHint = step.actionHint,
            stepLabel = "第 ${state.index + 1}/${controller.total} 步",
            primaryLabel = if (isFinish) "开始使用" else "下一步",
            secondaryLabel = if (isFinish) null else "上一步",
            showNavHint = step.requiresAction || !blocking,
            finishContent = isFinish,
            copied = copied,
            maxHeight = with(density) { rootSize.height.toDp() } - 72.dp,
            onPrimary = { controller.next() },
            onSecondary = { controller.previous() },
            onSkip = if (isFinish) null else ({ controller.skip() }),
            onCopy = copyQq,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

/**
 * 一块遮罩。用真实的 [Box] + 偏移/尺寸拼出洞的四条边。
 *
 * [blocking] = true 时吃掉点击（防止用户点到高亮目标以外的界面）；
 * false 时纯装饰，不参与事件分发 —— 这是「中断后仍可操作」的关键。
 */
@Composable
private fun ScrimQuad(
    xPx: Float,
    yPx: Float,
    wPx: Float,
    hPx: Float,
    color: Color,
    blocking: Boolean
) {
    if (wPx <= 0.5f || hPx <= 0.5f) return
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }

    var modifier: Modifier = Modifier
        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
        .background(color)

    if (blocking) {
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null
        ) { /* 只为了吃掉点击 */ }
    }

    Box(modifier = modifier)
}

/**
 * 提示卡片：圆角 16dp，标题 titleMedium、正文 bodyMedium、进度、按钮行 + 「跳过引导」。
 * 内容用 verticalScroll 包住并且限高，所以系统字体放大到最大也不会把按钮挤出屏幕。
 */
@Composable
private fun TutorialCard(
    title: String,
    body: String,
    actionHint: String?,
    stepLabel: String?,
    primaryLabel: String,
    secondaryLabel: String?,
    showNavHint: Boolean,
    finishContent: Boolean,
    copied: Boolean,
    maxHeight: Dp,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onSkip: (() -> Unit)?,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (stepLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stepLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (finishContent) {
                Text(
                    text = TutorialSteps.FINISH_INTRO,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = "作者 QQ：${TutorialSteps.AUTHOR_QQ}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "好友验证信息：${TutorialSteps.AUTHOR_QQ_VERIFY}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = TutorialSteps.FINISH_OUTRO,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (actionHint != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (showNavHint) "需要你操作：$actionHint" else actionHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            } else if (showNavHint) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "按上面的说明操作后，引导会自动继续；也可以直接点「下一步」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))

            if (finishContent) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "已复制" else "复制 QQ 号")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(primaryLabel)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (secondaryLabel != null) {
                        OutlinedButton(
                            onClick = onSecondary,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(secondaryLabel)
                        }
                    }
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(primaryLabel)
                    }
                }
                if (onSkip != null) {
                    Spacer(Modifier.height(4.dp))
                    // 铺满整行即可居中：M3 TextButton 内部本来就是 Row(Center)，
                    // 避免使用只在 ColumnScope 里才存在的 Modifier.align。
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("跳过引导")
                    }
                }
            }
        }
    }
}
