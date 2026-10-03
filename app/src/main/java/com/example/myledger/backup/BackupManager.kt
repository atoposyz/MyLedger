package com.example.myledger.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.data.settings.SettingsRepository
import com.example.myledger.util.AppVersion
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PreparedBackup(val fileName: String, val metadata: BackupMetadata)

class BackupManager(context: Context, private val ledger: LedgerRepository, private val settings: SettingsRepository) {
    private val application = context.applicationContext
    private val resolver = application.contentResolver
    private val exports = File(application.cacheDir, "backups/exports")
    private val staging = File(application.cacheDir, "backups/staging")
    private val safety = File(application.filesDir, "backups/pre-restore.zip")
    private val mutex = Mutex()
    val hasSafetyCopy get() = safety.isFile
    private suspend fun payload(): BackupPayload {
        val snapshot = ledger.snapshot()
        return BackupPayload(BackupMetadata(BackupCodec.VERSION, AppVersion.name(application), Instant.now(), snapshot.transactions.size, snapshot.activities.size), snapshot, settings.current())
    }
    suspend fun prepareExport(): PreparedBackup = mutex.withLock { withContext(Dispatchers.IO) {
        exports.mkdirs()
        val file = File(exports, "export-${UUID.randomUUID()}.zip")
        try {
            val payload = payload(); file.outputStream().use { BackupCodec.write(payload, it) }
            require(file.length() <= BackupCodec.MAX_FILE_BYTES) { "备份过大" }
            PreparedBackup(file.name, payload.metadata)
        } catch (error: Exception) { file.delete(); throw error }
    } }
    fun exportFile(name: String): File = child(exports, name, "export").also { require(it.isFile) { "请重新生成备份" } }
    suspend fun readExportPreview(name: String): PreparedBackup = withContext(Dispatchers.IO) {
        PreparedBackup(name, exportFile(name).inputStream().use { BackupCodec.read(it) }.metadata)
    }
    suspend fun saveExport(name: String, uri: Uri) = withContext(Dispatchers.IO) {
        val file = exportFile(name)
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开目标文件" }.use { output -> file.inputStream().use { it.copyTo(output) } }
    }
    suspend fun prepareImport(uri: Uri): PreparedBackup = mutex.withLock { withContext(Dispatchers.IO) {
        requireNotNull(resolver.openInputStream(uri)) { "无法读取文件" }.use { stage(it) }
    } }
    suspend fun prepareSafetyImport(): PreparedBackup = mutex.withLock { withContext(Dispatchers.IO) { safety.inputStream().use { stage(it) } } }
    private fun stage(input: InputStream): PreparedBackup {
        staging.mkdirs()
        val file = File(staging, "import-${UUID.randomUUID()}.zip")
        try {
            file.outputStream().use { output ->
                val buffer = ByteArray(8192); var size = 0L
                while (true) { val read = input.read(buffer); if (read < 0) break; size += read; require(size <= BackupCodec.MAX_FILE_BYTES) { "备份文件过大" }; output.write(buffer, 0, read) }
            }
            return PreparedBackup(file.name, file.inputStream().use { BackupCodec.read(it) }.metadata)
        } catch (error: Exception) { file.delete(); throw error }
    }
    suspend fun readPreview(name: String): PreparedBackup = withContext(Dispatchers.IO) {
        val file = child(staging, name, "import"); PreparedBackup(name, file.inputStream().use { BackupCodec.read(it) }.metadata)
    }
    suspend fun restore(name: String): BackupMetadata = mutex.withLock { withContext(Dispatchers.IO) {
        val imported = child(staging, name, "import").inputStream().use { BackupCodec.read(it) }
        val before = payload()
        safety.parentFile?.mkdirs()
        val atomic = AtomicFile(safety)
        val output = atomic.startWrite()
        try { BackupCodec.write(before, output); output.flush(); require(output.channel.size() <= BackupCodec.MAX_FILE_BYTES) { "恢复前副本过大" }; atomic.finishWrite(output)
        } catch (error: Exception) { atomic.failWrite(output); throw error }
        withContext(NonCancellable) {
            var settingsChanged = false
            try {
                ledger.replaceLedger(imported.ledger) { settings.update(imported.settings); settingsChanged = true }
            } catch (error: Exception) {
                if (settingsChanged) runCatching { settings.update(before.settings) }
                throw error
            }
        }
        imported.metadata
    } }
    fun discardImport(name: String) { child(staging, name, "import").delete() }
    private fun child(directory: File, name: String, prefix: String): File {
        require(name.matches(Regex("$prefix-[0-9a-fA-F-]{36}\\.zip"))) { "备份文件名无效" }
        return File(directory, name)
    }
}
