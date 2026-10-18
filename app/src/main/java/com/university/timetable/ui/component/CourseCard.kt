package com.university.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.ui.theme.CoursePalette
import com.university.timetable.ui.theme.desaturated
import com.university.timetable.util.SemesterWeeks

/**
 * 课程卡左侧色条的几何。
 *
 * 抽出来的原因：周视图的「当前时间轴条」左端圆球要和这条色条**同宽、同 x** 对齐
 * （看起来像色条那一列的顶端封口）。如果两边各写一份数字，改一处就会错位，
 * 所以让卡片自己暴露这组尺寸，对齐方直接引用。
 */
internal object CourseCardMetrics {

    /** 色条距卡片左边的距离。 */
    fun barStart(compact: Boolean): Dp = if (compact) 4.dp else 6.dp

    /** 色条宽度。 */
    fun barWidth(compact: Boolean): Dp = if (compact) 3.dp else 4.dp
}

/**
 * 课程卡片。
 *
 * - [compact] = true 时用于周视图网格（课程名 + 教师 + 地点，尽量完整显示不省略）
 * - 简洁模式（prefs.simpleMode）会进一步精简为极简色块
 * - [isPast] 表示已结束（置灰），[isNow] 表示正在进行（描边高亮）
 * - [onLongClick] 用于周视图「长按课程编辑」
 */
@Composable
fun CourseCard(
    block: ClassBlock,
    prefs: UiPreferences,
    dark: Boolean,
    isPast: Boolean = false,
    isNow: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val (courseContainer, courseContent) = CoursePalette.colorsFor(block.courseId, block.color, dark)
    // 已结束置灰：与今日页的 CourseListRow 用同一套处理，避免两处观感不一致
    val container = if (isPast) courseContainer.desaturated(0.9f) else courseContainer
    val content = if (isPast) courseContent.desaturated(0.78f) else courseContent
    val simple = prefs.simpleMode
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    val haptics = rememberSoftHaptics()

    val base = modifier
        .clip(shape)
        .background(container)
        .then(
            if (isNow) {
                Modifier.border(1.6.dp, MaterialTheme.colorScheme.primary, shape)
            } else Modifier
        )
        .then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = {
                        // 柔性反馈：长按进入编辑，给一记「按键感」，让用户确认长按已生效。
                        if (onLongClick != null) haptics.confirm()
                        onLongClick?.invoke()
                    }
                )
            } else Modifier
        )
        .alpha(if (isPast) 0.85f else 1f)

    if (simple) {
        // 极简模式：只保留课程名与地点
        Column(
            modifier = base.padding(horizontal = if (compact) 6.dp else 12.dp, vertical = if (compact) 5.dp else 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = block.name,
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 10.5.sp else 14.sp,
                lineHeight = if (compact) 12.sp else 18.sp,
                maxLines = if (compact) 3 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (prefs.showLocation && !block.location.isNullOrBlank()) {
                Text(
                    text = block.location.orEmpty(),
                    color = content.copy(alpha = 0.72f),
                    fontSize = if (compact) 9.sp else 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    Row(modifier = base) {
        // 左侧色条
        Box(
            modifier = Modifier
                .padding(vertical = if (compact) 7.dp else 10.dp, horizontal = 0.dp)
                .padding(start = CourseCardMetrics.barStart(compact))
                .width(CourseCardMetrics.barWidth(compact))
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(content.copy(alpha = 0.85f))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    horizontal = if (compact) 6.dp else 12.dp,
                    vertical = if (compact) 6.dp else 10.dp
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = block.name,
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 10.5.sp else 15.sp,
                lineHeight = if (compact) 12.5.sp else 19.sp,
                maxLines = if (compact) 3 else 2,
                overflow = TextOverflow.Ellipsis
            )

            val infoLine = buildList {
                if (!compact) add("${block.startTime}-${block.endTime}")
                if (prefs.showWeekTag && block.weekParity != 0) {
                    add(com.university.timetable.data.entity.WeekParity.label(block.weekParity))
                }
                if (prefs.showTeacher && !block.teacher.isNullOrBlank()) add(block.teacher.orEmpty())
            }.joinToString(" · ")

            if (infoLine.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = infoLine,
                    color = content.copy(alpha = 0.78f),
                    fontSize = if (compact) 8.5.sp else 11.sp,
                    // 周视图里教师名要尽量显示完整，因此允许折行而不是截断
                    maxLines = if (compact) 2 else 1,
                    lineHeight = if (compact) 10.5.sp else 14.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (prefs.showLocation && !block.location.isNullOrBlank()) {
                Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
                Row(verticalAlignment = Alignment.Top) {
                    if (!compact) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = content.copy(alpha = 0.7f),
                            modifier = Modifier.width(11.dp).height(11.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        text = block.location.orEmpty(),
                        color = content.copy(alpha = 0.78f),
                        fontSize = if (compact) 8.5.sp else 11.sp,
                        // 教室名不截断，允许折行完整展示
                        maxLines = if (compact) 3 else 2,
                        lineHeight = if (compact) 10.5.sp else 14.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 空状态占位。 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🌤",
            fontSize = 34.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = com.university.timetable.ui.theme.LocalTimetableColors.current.mutedText,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** 区块标题。 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = com.university.timetable.ui.theme.LocalTimetableColors.current.mutedText
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 课程类型标签。 */
@Composable
fun CategoryChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Suppress("unused")
@Composable
private fun TeacherRow(teacher: String, color: Color, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Group,
            contentDescription = null,
            tint = color,
            modifier = Modifier.width(11.dp).height(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(text = teacher, color = color, fontSize = if (compact) 8.5.sp else 11.sp)
    }
}

@Suppress("unused")
private fun dayName(day: Int) = SemesterWeeks.dayLabel(day)
