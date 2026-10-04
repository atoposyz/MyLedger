package com.example.myledger.network

import com.example.myledger.backup.BackupEncryption
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ServiceException(message: String) : Exception(message)

object ServiceAddress {
    fun normalize(value: String): String {
        val text = value.trim().trimEnd('/')
        val uri = try { URI(text) } catch (_: Exception) { throw ServiceException("地址格式无效") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.port in -1..65535 && uri.port != 0 &&
            uri.normalize() == uri && !uri.rawPath.contains('%') && !uri.rawPath.contains("//")) { "请输入 HTTPS 地址，不含账号、参数或片段" }
        return text
    }
    fun secret(value: String) { require(value.length in 1..4096 && value.all { it.code in 33..126 }) { "密钥格式无效（不能有空格或换行）" } }
}

/** Injectable at the HTTP boundary; production always uses platform TLS validation. */
fun interface HttpTransport {
    suspend fun request(method: String, url: String, token: String, body: ByteArray?, contentType: String, limit: Long): ByteArray
}

class HttpsTransport : HttpTransport {
    override suspend fun request(method: String, url: String, token: String, body: ByteArray?, contentType: String, limit: Long): ByteArray = withContext(Dispatchers.IO) {
        val address = ServiceAddress.normalize(url); ServiceAddress.secret(token)
        val connection = URI(address).toURL().openConnection() as HttpsURLConnection
        try {
            ensureActive()
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 45_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json, application/octet-stream")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw ServiceException(when (status) {
                401, 403 -> "认证失败，请检查密钥或令牌"
                404 -> "接口或备份不存在，请检查地址"
                413 -> "文件超过服务器限制"
                429 -> "请求过于频繁，请稍后重试"
                in 300..399 -> "服务器重定向已拒绝，请填写最终 HTTPS 地址"
                else -> "服务请求失败（HTTP $status）"
            })
            require(connection.contentLengthLong <= limit) { "服务响应过大" }
            val bytes = connection.inputStream.use { BackupEncryption.readBounded(it, limit) }
            ensureActive(); bytes
        } finally { connection.disconnect() }
    }
}
