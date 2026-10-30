package com.university.timetable.ui.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.data.University
import com.university.timetable.data.UniversityDirectory
import com.university.timetable.ui.component.GlassRadius
import com.university.timetable.ui.component.GlassSurface
import com.university.timetable.ui.theme.UniversityTimetableTheme
import com.university.timetable.util.PinyinIndex

/**
 * 选择学校：按拼音首字母分组的全国高校目录，顶部有搜索栏，兰州大学置顶。
 *
 * 选中后带着该校入口 URL 打开 [LzuImportActivity] 的内置浏览器。
 */
class UniversityPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            UniversityTimetableTheme {
                UniversityPickerScreen(
                    onBack = { finish() },
                    onPick = { university ->
                        startActivity(
                            LzuImportActivity.intent(
                                context = this,
                                startUrl = university.url,
                                universityName = university.name
                            )
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, UniversityPickerActivity::class.java)
    }
}

@Composable
private fun UniversityPickerScreen(
    onBack: () -> Unit,
    onPick: (University) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val all = remember { UniversityDirectory.all(context) }
    val pinned = remember { UniversityDirectory.pinned(context) }

    val results = remember(query, all) {
        if (query.isBlank()) {
            all.filter { it.name != UniversityDirectory.PINNED_NAME }
        } else {
            all.filter { it.matches(query) }
        }
    }
    val sections = remember(results) { groupSections(results) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        PickerHeader(total = all.size, onBack = onBack)

        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(10.dp))

        if (all.isEmpty()) {
            Text(
                text = "高校目录读取失败，请重装应用后再试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp
            )
        ) {
            if (query.isBlank() && pinned != null) {
                item(key = "pinned") {
                    PinnedCard(
                        university = pinned,
                        onClick = { onPick(pinned) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            sections.forEach { (letter, schools) ->
                item(key = "header-$letter") {
                    SectionHeader(letter = letter, count = schools.size)
                }
                items(schools, key = { it.name }) { school ->
                    UniversityRow(university = school, onClick = { onPick(school) })
                }
            }

            if (results.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有找到「${query.trim()}」相关的学校",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(total: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "返回",
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "选择学校",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "全国 $total 所本科高校 · 先选校，再在内置浏览器里导入",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(GlassRadius.Small + 6.dp),
        placeholder = {
            Text(
                text = "搜索学校名、省份或拼音首字母",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun PinnedCard(university: University, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassRadius.Small + 4.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(GlassRadius.Small + 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = university.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "常用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = university.province,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "置顶",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SectionHeader(letter: Char, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = letter.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UniversityRow(university: University, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassRadius.Small))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = university.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = university.province,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "进入",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 按首字母分组，顺序严格按 [PinyinIndex.letters]，`#` 与其它字母追加在后面。 */
private fun groupSections(list: List<University>): List<Pair<Char, List<University>>> {
    if (list.isEmpty()) return emptyList()
    val grouped = list.groupBy { it.initial }
    val order = PinyinIndex.letters + PinyinIndex.FALLBACK
    val known = order.mapNotNull { letter -> grouped[letter]?.let { letter to it } }
    val extra = grouped.keys
        .filter { it !in order }
        .sorted()
        .mapNotNull { letter -> grouped[letter]?.let { letter to it } }
    return known + extra
}
