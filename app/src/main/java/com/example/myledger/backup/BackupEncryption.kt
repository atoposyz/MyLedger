package com.example.myledger.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupPasswordRequired(val fileName: String) : Exception()
class BackupUnlockException : Exception("密码错误或备份已损坏")

/** Portable envelope v1: authenticated header + AES-256-GCM encrypted v1 ZIP. */
object BackupEncryption {
    private val magic = "MYLENC01".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    private const val HEADER_SIZE = 40
    const val MAX_ENCRYPTED_BYTES = BackupCodec.MAX_FILE_BYTES + HEADER_SIZE + 16
    fun isEncrypted(bytes: ByteArray) = bytes.size >= magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)
    fun validatePassword(password: CharArray) { require(password.size in 8..128) { "密码需为 8–128 个字符" } }
    fun encrypt(zip: ByteArray, password: CharArray): ByteArray {
        validatePassword(password); require(zip.size.toLong() <= BackupCodec.MAX_FILE_BYTES)
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(magic).putInt(ITERATIONS).put(salt).put(nonce).array()
        val keyBytes = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(zip)
        } finally { keyBytes.fill(0) }
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size >= HEADER_SIZE + 16 && bytes.size.toLong() <= MAX_ENCRYPTED_BYTES && isEncrypted(bytes)) { "加密文件格式无效" }
        require(password.isNotEmpty() && password.size <= 128)
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).apply { position(8) }
        require(buffer.int == ITERATIONS) { "加密参数不受支持" }
        val salt = ByteArray(16).also(buffer::get)
        val nonce = ByteArray(12).also(buffer::get)
        val keyBytes = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return cipher.doFinal(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE)
        } catch (_: AEADBadTagException) { throw BackupUnlockException() }
        finally { keyBytes.fill(0) }
    }
    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun readBounded(input: InputStream, limit: Long = MAX_ENCRYPTED_BYTES): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var size = 0L
        while (true) {
            val read = input.read(buffer); if (read < 0) break
            size += read; require(size <= limit) { "备份文件过大" }; output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
