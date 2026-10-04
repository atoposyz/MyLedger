package com.example.myledger.backup

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class HistoryBackup(val fileName: String, val metadata: BackupMetadata, val sizeBytes: Long)

/** Manager serializes access. Commit the index before pruning any previous copy. */
class LocalBackupHistory(private val directory: File, private val cipher: LocalBackupCipher) {
    companion object { const val RETAIN_COUNT = 7 }
    private val index = File(directory, "index.json")
    fun list(): List<HistoryBackup> {
        if (!index.isFile) return emptyList()
        val bytes = index.inputStream().use { BackupEncryption.readBounded(it, 16_384) }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("version") == 1)
        val rows = root.getJSONArray("backups"); require(rows.length() <= RETAIN_COUNT)
        return (0 until rows.length()).map { number ->
            val row = rows.getJSONObject(number)
            val name = row.getString("fileName"); file(name)
            val metadata = metadataFromJson(row.getJSONObject("metadata"))
            val size = row.getLong("sizeBytes"); require(size in 36..BackupEncryption.MAX_ENCRYPTED_BYTES)
            HistoryBackup(name, metadata, size)
        }.also { require(it.map(HistoryBackup::fileName).distinct().size == it.size) }
    }
    fun create(payload: BackupPayload): HistoryBackup {
        val previous = list()
        val zip = ByteArrayOutputStream().also { BackupCodec.write(payload, it) }.toByteArray()
        val bytes = try { cipher.encrypt(zip) } finally { zip.fill(0) }
        val entry = HistoryBackup("history-${UUID.randomUUID()}.enc", payload.metadata, bytes.size.toLong())
        val target = file(entry.fileName)
        try {
            writeAtomic(target, bytes)
            writeIndex((listOf(entry) + previous).take(RETAIN_COUNT))
        } catch (error: Exception) { target.delete(); throw error }
        val retained = (listOf(entry) + previous).take(RETAIN_COUNT).map { it.fileName }.toSet()
        // Clear expired copies and interrupted writes only after the new index is durable.
        directory.listFiles()?.filter {
            it.name.matches(Regex("history-[0-9a-fA-F-]{36}\\.enc(\\.new)?")) && it.name !in retained
        }?.forEach { it.delete() }
        return entry
    }
    fun read(name: String): BackupPayload {
        val entry = list().single { it.fileName == name }
        val target = file(name); require(target.length() == entry.sizeBytes)
        val encrypted = target.inputStream().use { BackupEncryption.readBounded(it) }
        val plain = cipher.decrypt(encrypted)
        return try { BackupCodec.read(plain.inputStream()).also { require(it.metadata == entry.metadata) } } finally { plain.fill(0) }
    }
    fun delete(name: String) {
        val current = list(); require(current.any { it.fileName == name })
        writeIndex(current.filterNot { it.fileName == name }); file(name).delete()
    }
    private fun file(name: String): File {
        require(name.matches(Regex("history-[0-9a-fA-F-]{36}\\.enc")))
        return File(directory, name)
    }
    private fun writeIndex(entries: List<HistoryBackup>) {
        val root = JSONObject().put("version", 1).put("backups", JSONArray().apply {
            entries.forEach { put(JSONObject().put("fileName", it.fileName).put("metadata", it.metadata.toJson()).put("sizeBytes", it.sizeBytes)) }
        })
        writeAtomic(index, root.toString().toByteArray(Charsets.UTF_8))
    }
}

internal fun BackupMetadata.toJson(): JSONObject = JSONObject().put("backupVersion", backupVersion).put("appVersion", appVersion)
    .put("createdAt", createdAt.toString()).put("transactionCount", transactionCount).put("activityCount", activityCount)
internal fun metadataFromJson(row: JSONObject): BackupMetadata = BackupMetadata(row.getInt("backupVersion"), row.getString("appVersion"),
    Instant.parse(row.getString("createdAt")), row.getInt("transactionCount"), row.getInt("activityCount")).also {
    require(it.backupVersion == BackupCodec.VERSION && it.appVersion.isNotBlank() && it.transactionCount in 0..100_000 && it.activityCount in 0..100_000)
}
internal fun writeAtomic(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file); val output = atomic.startWrite()
    try { output.write(bytes); output.flush(); atomic.finishWrite(output) }
    catch (error: Exception) { atomic.failWrite(output); throw error }
}
