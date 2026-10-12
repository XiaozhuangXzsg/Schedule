package com.university.timetable.importer

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 基于 Google ML Kit 的课表截图识别（完全免费、离线、中文支持好）。
 *
 * 识别出的文本行会交给 [ScheduleTextParser] 做结构化解析，
 * 因此截图中只要包含「课程名 / 星期 / 周次 / 地点」等要素即可。
 */
object OcrScheduleParser {

    /** 懒加载识别器：ML Kit 首次使用会按需下载中文模型。 */
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 识别图片并解析为课程列表。
     * @param onProgress 可选的进度回调（文案）
     */
    suspend fun parse(
        bitmap: Bitmap,
        sourceLabel: String = "课表截图",
        onProgress: (String) -> Unit = {}
    ): ImportResult {
        onProgress("正在识别图片文字…")
        val text = recognize(bitmap)

        onProgress("正在解析课表结构…")
        val result = ScheduleTextParser.parse(text, sourceLabel)

        val warnings = result.warnings.toMutableList()
        if (result.isEmpty) {
            warnings += "识别到的文字如下，可复制后手动校正：${text.take(200)}"
        }
        return result.copy(warnings = warnings)
    }

    /** 仅做文字识别，返回整段文本（分组按行）。 */
    suspend fun recognize(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result: Text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return result.textBlocks.joinToString("\n") { block ->
            block.lines.joinToString("\n") { it.text }
        }
    }
}
