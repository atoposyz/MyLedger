package com.example.myledger.backup

import android.net.Uri
import com.example.myledger.data.settings.IntegrationSettingsRepository
import com.example.myledger.network.HttpTransport
import com.example.myledger.network.HttpsTransport
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONObject

data class RemoteBackup(val id: String, val createdAt: Instant, val sizeBytes: Long, val sha256: String)
class RemoteBackupRepository(private val settings: IntegrationSettingsRepository, private val manager: BackupManager,
    private val transport: HttpTransport = HttpsTransport()) {
    private fun item(value: JSONObject): RemoteBackup {
        val id = value.getString("id"); require(id.matches(Regex("[0-9a-f-]{36}")))
        val size = value.getLong("sizeBytes"); require(size in 56..BackupEncryption.MAX_ENCRYPTED_BYTES)
        val hash = value.getString("sha256"); require(hash.matches(Regex("[0-9a-f]{64}")))
        return RemoteBackup(id, Instant.parse(value.getString("createdAt")), size, hash)
    }
    suspend fun list(): List<RemoteBackup> {
        val credentials = settings.serverCredentials()
        val bytes = transport.request("GET", "${credentials.base}/api/backups", credentials.secret, null, "application/json", 65_536)
        val rows = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("backups"); require(rows.length() <= 100)
        return (0 until rows.length()).map { item(rows.getJSONObject(it)) }.also { require(it.map(RemoteBackup::id).distinct().size == it.size) }
    }
    suspend fun upload(password: CharArray): RemoteBackup {
        val credentials = settings.serverCredentials()
        val exported = manager.prepareEncryptedExport(password)
        val file = manager.exportFile(exported.fileName)
        try {
            val bytes = file.inputStream().use { BackupEncryption.readBounded(it) }; require(BackupEncryption.isEncrypted(bytes))
            val response = transport.request("PUT", "${credentials.base}/api/backup", credentials.secret, bytes, "application/octet-stream", 4096)
            return item(JSONObject(response.toString(Charsets.UTF_8))).also { require(it.sizeBytes == bytes.size.toLong() && it.sha256 == digest(bytes)) { "服务器未确认完整文件" } }
        } finally { file.delete(); java.io.File(file.parentFile, "${file.name}.json").delete() }
    }
    suspend fun download(entry: RemoteBackup): String {
        require(entry.id.matches(Regex("[0-9a-f-]{36}")))
        val credentials = settings.serverCredentials()
        val bytes = transport.request("GET", "${credentials.base}/api/backups/${entry.id}", credentials.secret, null, "application/octet-stream", BackupEncryption.MAX_ENCRYPTED_BYTES)
        require(bytes.size.toLong() == entry.sizeBytes && digest(bytes) == entry.sha256 && BackupEncryption.isEncrypted(bytes)) { "下载文件校验失败" }
        return manager.stageEncryptedImport(bytes)
    }
    suspend fun delete(entry: RemoteBackup) {
        require(entry.id.matches(Regex("[0-9a-f-]{36}")))
        val credentials = settings.serverCredentials()
        transport.request("DELETE", "${credentials.base}/api/backups/${entry.id}", credentials.secret, null, "application/json", 4096)
    }
    companion object { fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
}
