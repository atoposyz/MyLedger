package com.example.myledger.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.data.settings.SettingsRepository
import com.example.myledger.util.AppVersion
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreparedBackup(val fileName: String, val metadata: BackupMetadata, val encrypted: Boolean = false)

class BackupManager(context: Context, private val ledger: LedgerRepository, private val settings: SettingsRepository,
    localCipher: LocalBackupCipher = LocalBackupCipher()) {
    private val application = context.applicationContext
    private val resolver = application.contentResolver
    private val exports = File(application.cacheDir, "backups/exports")
    private val staging = File(application.cacheDir, "backups/staging")
    private val safety = File(application.filesDir, "backups/pre-restore.zip")
    private val mutex = Mutex()
    private val localHistory = LocalBackupHistory(File(application.filesDir, "backups/history"), localCipher)
    private val _history = MutableStateFlow<List<HistoryBackup>>(emptyList())
    val history = _history.asStateFlow()
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
    suspend fun prepareEncryptedExport(password: CharArray, historyName: String? = null): PreparedBackup = mutex.withLock { withContext(Dispatchers.IO) {
        BackupEncryption.validatePassword(password)
        val payload = if (historyName == null) payload() else localHistory.read(historyName)
        val zip = ByteArrayOutputStream().also { BackupCodec.write(payload, it) }.toByteArray()
        val encrypted = try { BackupEncryption.encrypt(zip, password) } finally { zip.fill(0) }
        val file = File(exports, "export-${UUID.randomUUID()}.enc")
        try {
            writeAtomic(file, encrypted)
            writeAtomic(File(exports, "${file.name}.json"), payload.metadata.toJson().toString().toByteArray(Charsets.UTF_8))
            PreparedBackup(file.name, payload.metadata, encrypted = true)
        } catch (error: Exception) { file.delete(); File(exports, "${file.name}.json").delete(); throw error }
    } }
    fun exportFile(name: String): File = child(exports, name, "export").also { require(it.isFile) { "请重新生成备份" } }
    suspend fun readExportPreview(name: String): PreparedBackup = withContext(Dispatchers.IO) {
        val file = exportFile(name)
        if (name.endsWith(".enc")) {
            val bytes = File(exports, "$name.json").inputStream().use { BackupEncryption.readBounded(it, 4096) }
            PreparedBackup(name, metadataFromJson(org.json.JSONObject(bytes.toString(Charsets.UTF_8))), encrypted = true)
        } else PreparedBackup(name, file.inputStream().use { BackupCodec.read(it) }.metadata)
    }
    suspend fun saveExport(name: String, uri: Uri) = withContext(Dispatchers.IO) {
        val file = exportFile(name)
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开目标文件" }.use { output -> file.inputStream().use { it.copyTo(output) } }
    }
    suspend fun prepareImport(uri: Uri, password: CharArray? = null): PreparedBackup = mutex.withLock { withContext(Dispatchers.IO) {
        val bytes = requireNotNull(resolver.openInputStream(uri)) { "无法读取文件" }.use { BackupEncryption.readBounded(it) }
        if (BackupEncryption.isEncrypted(bytes)) {
            if (password == null) {
                val locked = File(staging, "locked-${UUID.randomUUID()}.enc")
                writeAtomic(locked, bytes); throw BackupPasswordRequired(locked.name)
            }
            val plain = BackupEncryption.decrypt(bytes, password)
            try { stage(plain.inputStream()) } finally { plain.fill(0) }
        } else { require(bytes.size.toLong() <= BackupCodec.MAX_FILE_BYTES); stage(bytes.inputStream()) }
    } }
    fun hasLockedImport(name: String) = child(staging, name, "locked").isFile
    suspend fun stageEncryptedImport(bytes: ByteArray): String = mutex.withLock { withContext(Dispatchers.IO) {
        require(BackupEncryption.isEncrypted(bytes) && bytes.size.toLong() in 56..BackupEncryption.MAX_ENCRYPTED_BYTES)
        val file = File(staging, "locked-${UUID.randomUUID()}.enc"); writeAtomic(file, bytes); file.name
    } }
    fun discardLockedImport(name: String) { child(staging, name, "locked").delete() }
    suspend fun unlockImport(name: String, password: CharArray): PreparedBackup = mutex.withLock { withContext(Dispatchers.IO) {
        val file = child(staging, name, "locked")
        val encrypted = file.inputStream().use { BackupEncryption.readBounded(it) }
        val plain = BackupEncryption.decrypt(encrypted, password)
        try { stage(plain.inputStream()).also { file.delete() } } finally { plain.fill(0) }
    } }
    suspend fun refreshHistory() = mutex.withLock { withContext(Dispatchers.IO) { _history.value = localHistory.list() } }
    suspend fun createLocalBackup(): HistoryBackup = mutex.withLock { withContext(Dispatchers.IO) {
        val entry = localHistory.create(payload()); _history.value = localHistory.list()
        settings.recordBackupResult(entry.metadata.createdAt.toEpochMilli()); entry
    } }
    suspend fun prepareHistoryImport(name: String): PreparedBackup = mutex.withLock { withContext(Dispatchers.IO) {
        val payload = localHistory.read(name)
        val bytes = ByteArrayOutputStream().also { BackupCodec.write(payload, it) }.toByteArray()
        try { stage(bytes.inputStream()) } finally { bytes.fill(0) }
    } }
    suspend fun deleteHistory(name: String) = mutex.withLock { withContext(Dispatchers.IO) {
        localHistory.delete(name); _history.value = localHistory.list()
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
        require(name.matches(Regex("$prefix-[0-9a-fA-F-]{36}\\.(zip|enc)"))) { "备份文件名无效" }
        return File(directory, name)
    }
}
