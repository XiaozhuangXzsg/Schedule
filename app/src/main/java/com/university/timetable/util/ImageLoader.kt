package com.university.timetable.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.InputStream

/**
 * 图片加载工具：把用户相册里选中的图片解码为 Bitmap。
 * 会自动做一次降采样，避免大图导致内存爆掉。
 */
object ImageLoader {

    private const val MAX_DIMENSION = 1440

    private val cache = androidx.collection.LruCache<String, Bitmap>(8)

    fun load(context: Context, uriString: String): Bitmap? {
        cache.get(uriString)?.let { return it }
        val bitmap = runCatching { decode(context, Uri.parse(uriString)) }.getOrNull() ?: return null
        cache.put(uriString, bitmap)
        return bitmap
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        // 先读尺寸
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w <= 0 || h <= 0) return null

        var sample = 1
        while (w / sample > MAX_DIMENSION || h / sample > MAX_DIMENSION) sample *= 2

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val sw = info.size.width
                val sh = info.size.height
                var s = 1
                while (sw / s > MAX_DIMENSION || sh / s > MAX_DIMENSION) s *= 2
                decoder.setTargetSampleSize(s)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }

    private fun openStream(context: Context, uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    /** 读取文档（HTML/CSV）文本内容。 */
    fun readText(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull()

    /** 读取图片为 Bitmap（用于 OCR）。 */
    fun loadForOcr(context: Context, uri: Uri): Bitmap? = load(context, uri.toString())

    @Suppress("unused")
    private fun mediaStoreUri(uri: Uri) = MediaStore.Images.Media.EXTERNAL_CONTENT_URI == uri
}
