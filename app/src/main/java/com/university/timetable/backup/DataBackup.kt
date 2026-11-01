package com.university.timetable.backup

import android.content.Context
import android.net.Uri
import com.university.timetable.BuildConfig
import com.university.timetable.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 用户数据备份 / 恢复。
 *
 * 备份内容（都放在应用私有目录里）：
 * - databases/university_timetable.db（以及可能存在的 -wal / -shm）
 * - files/datastore/ui_preferences.preferences_pb（全部设置、主题、昵称等）
 * - files/datastore/ai_settings.preferences_pb（AI 配置）
 * - manifest.json（包名、版本、数据库版本、创建时间）
 *
 * 导出时由用户通过系统文件选择器选择保存位置（SAF），不写入应用缓存目录。
 * 恢复前会自动把「恢复前」的现场再备份一份到 files/backup/，恢复失败也能找回。
 */
object DataBackup {

    const val MANIFEST_SCHEMA = 1
    private const val MAX_BACKUP_BYTES = 200L * 1024 * 1024
    private const val ENTRY_MANIFEST = "manifest.json"
    private const val DIR_DATABASE = "database/"
    private const val DIR_DATASTORE = "datastore/"

    private const val PREFS_UI = "datastore/ui_preferences.preferences_pb"
    private const val PREFS_AI = "datastore/ai_settings.preferences_pb"

    data class Result(val ok: Boolean, val message: String)

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "yaya-timetable-backup-" + stamp + ".zip"
    }

    suspend fun export(context: Context, destination: Uri): Result = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val output = resolver.openOutputStream(destination, "w")
                ?: throw IOException("无法写入所选位置")
            output.use { stream ->
                writeArchive(context, stream)
            }
            Result(true, "数据备份已导出到所选位置。")
        }.getOrElse { error ->
            Result(false, "导出失败：" + (error.message ?: "请换一个保存位置后重试"))
        }
    }

    /**
     * 把当前数据打包写入 [target]（内部使用：导出、恢复前快照）。
     */
    private fun writeArchive(context: Context, target: OutputStream) {
        checkpointDatabase(context)
        val staging = File(context.cacheDir, "backup-staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        try {
            val databaseFiles = copyDatabaseFilesTo(context, File(staging, "database"))
            val datastoreFiles = copyDataStoreFilesTo(context, File(staging, "datastore"))
            val manifest = JSONObject().apply {
                put("schemaVersion", MANIFEST_SCHEMA)
                put("appPackage", context.packageName)
                put("versionName", BuildConfig.VERSION_NAME)
                put("versionCode", BuildConfig.VERSION_CODE.toLong())
                put("databaseVersion", AppDatabase.SCHEMA_VERSION)
                put("createdAt", System.currentTimeMillis())
            }
            val zip = ZipOutputStream(BufferedOutputStream(target))
            zip.use {
                it.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                it.write(manifest.toString().toByteArray(Charsets.UTF_8))
                it.closeEntry()
                databaseFiles.forEach { file ->
                    addEntry(zip, DIR_DATABASE + file.name, file)
                }
                datastoreFiles.forEach { file ->
                    addEntry(zip, DIR_DATASTORE + file.name, file)
                }
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    /** 恢复前把当前现场存到 files/backup/，返回快照文件名。 */
    private fun snapshotBeforeRestore(context: Context): String {
        val dir = File(context.filesDir, "backup")
        if (!dir.exists()) dir.mkdirs()
        // 只保留最近 3 份「恢复前快照」，避免长期占用用户存储空间。
        runCatching {
            dir.listFiles { item -> item.isFile && item.name.startsWith("pre-restore-") }
                ?.sortedByDescending { it.name }
                ?.drop(2)
                ?.forEach { it.delete() }
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "pre-restore-" + stamp + ".zip")
        file.outputStream().use { writeArchive(context, it) }
        return file.name
    }

    suspend fun restore(context: Context, source: Uri): Result = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "backup-import")
        try {
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()
            val manifest = unzip(context, source, staging)
                ?: return@withContext Result(false, "备份文件不完整（缺少 manifest.json），无法恢复。")

            val packageName = manifest.optString("appPackage")
            if (packageName != context.packageName) {
                return@withContext Result(
                    false,
                    "这份备份属于其他应用（" + packageName + "），无法恢复到本应用。"
                )
            }
            val databaseVersion = manifest.optInt("databaseVersion", -1)
            if (databaseVersion > AppDatabase.SCHEMA_VERSION) {
                return@withContext Result(
                    false,
                    "备份来自更新版本的应用（数据库 v" + databaseVersion +
                        "），请先升级应用后再恢复。"
                )
            }
            if (databaseVersion in 1..3) {
                return@withContext Result(
                    false,
                    "备份来自更早的版本（数据库 v" + databaseVersion +
                        "），当前版本无法无损迁移，已取消恢复。"
                )
            }

            val snapshotName = runCatching { snapshotBeforeRestore(context) }
                .getOrElse { error ->
                    return@withContext Result(
                        false,
                        "恢复前自动备份失败：" + (error.message ?: "存储空间不足") + "，已取消恢复。"
                    )
                }

            val databaseDir = File(staging, "database")
            val restoredDb = File(databaseDir, AppDatabase.DB_FILE_NAME)
            if (!restoredDb.exists() || restoredDb.length() <= 0L) {
                return@withContext Result(false, "备份中没有有效的数据库文件，无法恢复。")
            }
            val validation = validateDatabaseFile(restoredDb)
            if (validation != null) {
                return@withContext Result(false, validation)
            }

            val databaseFile = context.getDatabasePath(AppDatabase.DB_FILE_NAME)
            databaseFile.parentFile?.mkdirs()
            // 先把备份数据库落到同目录的临时文件，再关闭 Room、原子改名：
            // 把「数据库文件不存在」的窗口压到最小，改名失败时旧库也不会被截断。
            val stagedDatabase = File(databaseFile.path + ".restore-tmp")
            if (stagedDatabase.exists()) stagedDatabase.delete()
            restoredDb.copyTo(stagedDatabase, overwrite = true)

            // 关键：先关闭 Room，避免覆盖时还有连接在读写。
            AppDatabase.closeForRestore()
            try {
                listOf("", "-wal", "-shm").forEach { suffix ->
                    val stale = File(databaseFile.path + suffix)
                    if (stale.exists()) stale.delete()
                }
                if (!stagedDatabase.renameTo(databaseFile)) {
                    stagedDatabase.copyTo(databaseFile, overwrite = true)
                    stagedDatabase.delete()
                }
                listOf("-wal", "-shm").forEach { suffix ->
                    val extra = File(databaseDir, AppDatabase.DB_FILE_NAME + suffix)
                    if (extra.exists()) extra.copyTo(File(databaseFile.path + suffix), overwrite = true)
                }
            } catch (error: Exception) {
                runCatching { stagedDatabase.delete() }
                throw IOException(
                    "覆盖数据库失败：" + (error.message ?: "存储空间不足") +
                        "。恢复前的数据已自动备份为 " + snapshotName +
                        "，可在「设置 → 数据与备份 → 导入数据恢复」中找回。",
                    error
                )
            }

            copyDataStoreBack(context, File(staging, "datastore"))

            Result(
                true,
                "数据已恢复。恢复前的数据已自动备份为 " + snapshotName +
                    "。应用将自动重启，若没有自动打开请手动启动应用。"
            )
        } catch (error: Exception) {
            Result(false, "恢复失败：" + (error.message ?: "备份文件可能已损坏"))
        } finally {
            staging.deleteRecursively()
        }
    }

    /** 校验备份里的数据库：SQLite 文件头 + user_version 不能高于当前 schema。 */
    private fun validateDatabaseFile(file: File): String? {
        val header = ByteArray(16)
        val read = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrNull() ?: return "备份中的数据库文件无法读取，已取消恢复。"
        if (read < 16 || String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") {
            return "备份中的数据库文件格式不正确，已取消恢复。"
        }
        val userVersion = RandomAccessFile(file, "r").use { access ->
            access.seek(60L)
            val bytes = ByteArray(4)
            access.readFully(bytes)
            ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        }
        if (userVersion > AppDatabase.SCHEMA_VERSION) {
            return "备份中的数据库版本（v" + userVersion + "）高于当前应用，已取消恢复。"
        }
        return null
    }

    private fun checkpointDatabase(context: Context) {
        runCatching {
            AppDatabase.get(context).openHelper.writableDatabase
                .execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        }
    }

    private fun copyDatabaseFilesTo(context: Context, targetDir: File): List<File> {
        targetDir.mkdirs()
        val databaseFile = context.getDatabasePath(AppDatabase.DB_FILE_NAME)
        if (!databaseFile.exists()) return emptyList()
        val result = ArrayList<File>(3)
        listOf("", "-wal", "-shm").forEach { suffix ->
            val source = File(databaseFile.path + suffix)
            if (source.exists()) {
                val target = File(targetDir, AppDatabase.DB_FILE_NAME + suffix)
                source.copyTo(target, overwrite = true)
                result += target
            }
        }
        return result
    }

    private fun copyDataStoreFilesTo(context: Context, targetDir: File): List<File> {
        targetDir.mkdirs()
        val result = ArrayList<File>(2)
        listOf(PREFS_UI, PREFS_AI).forEach { relative ->
            val source = File(context.filesDir, relative)
            if (source.exists()) {
                val target = File(targetDir, source.name)
                source.copyTo(target, overwrite = true)
                result += target
            }
        }
        return result
    }

    private fun copyDataStoreBack(context: Context, sourceDir: File) {
        listOf(PREFS_UI, PREFS_AI).forEach { relative ->
            val source = File(sourceDir, File(relative).name)
            if (source.exists()) {
                val target = File(context.filesDir, relative)
                target.parentFile?.mkdirs()
                // 先写临时文件再改名：即使中途失败，原来的偏好文件也不会被破坏。
                val temp = File(target.path + ".restore-tmp")
                if (temp.exists()) temp.delete()
                source.copyTo(temp, overwrite = true)
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
        }
    }

    private fun addEntry(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().buffered().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun unzip(context: Context, source: Uri, targetDir: File): JSONObject? {
        val input: InputStream = context.contentResolver.openInputStream(source)
            ?: throw IOException("无法读取所选备份文件")
        var total = 0L
        var manifest: JSONObject? = null
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name.contains("..") || name.startsWith("/")) {
                    throw IOException("备份文件包含非法路径")
                }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (name == ENTRY_MANIFEST) {
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > MAX_BACKUP_BYTES) {
                        throw IOException("备份文件超过 200MB，已取消恢复")
                    }
                    manifest = JSONObject(String(bytes, Charsets.UTF_8))
                    zip.closeEntry()
                    continue
                }
                val relative = when {
                    name.startsWith(DIR_DATABASE) -> "database/" + name.removePrefix(DIR_DATABASE)
                    name.startsWith(DIR_DATASTORE) -> "datastore/" + name.removePrefix(DIR_DATASTORE)
                    else -> {
                        zip.closeEntry()
                        continue
                    }
                }
                val target = File(targetDir, relative)
                target.parentFile?.mkdirs()
                // 边读边写，避免把整个数据库一次性读进内存（低内存机型容易 OOM）。
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BACKUP_BYTES) {
                            throw IOException("备份文件超过 200MB，已取消恢复")
                        }
                        out.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
        return manifest
    }
}
