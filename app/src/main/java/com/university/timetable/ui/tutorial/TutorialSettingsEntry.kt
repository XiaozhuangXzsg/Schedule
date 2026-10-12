package com.university.timetable.ui.tutorial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 设置页的「新手引导」入口行。
 *
 * 说明：`SettingsScreen.kt` 里的 `SettingsRow(icon, title, subtitle, onClick)` 是**私有**的，
 * 我在不改动该文件的前提下无法复用它，因此这里给出一个视觉上对齐的等价实现。
 *
 * 如果接线时你更想复用自己那套 `SettingsRow`，直接把这一行放进设置分组即可，本文件可以不用：
 * ```
 * SettingsRow(
 *     icon = Icons.Filled.School,
 *     title = "新手引导",
 *     subtitle = "重新观看导入与常用功能的分步引导",
 *     onClick = { tutorial.restart() }
 * )
 * ```
 * 两种方式都行，任选其一，别同时加（会出现两行）。
 *
 * 注意：这里**没有**挂 `tutorialAnchor` —— 设置页是引导过程中被高亮的页面，
 * 如果在这个入口上再挂锚点会和高亮逻辑互相干扰。锚点只需要加在需求列出的那 7 个位置。
 */
@Composable
fun TutorialSettingsRow(
    onOpenTutorial: () -> Unit,
    icon: ImageVector = Icons.Filled.School,
    title: String = "新手引导",
    subtitle: String = "重新观看导入与常用功能的分步引导"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenTutorial)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
