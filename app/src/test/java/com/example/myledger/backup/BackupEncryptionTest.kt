package com.example.myledger.backup

import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class BackupEncryptionTest {
    private val password get() = "remember-这份备份-2026".toCharArray()
    private val plain = "账本测试：1234分，业务日期2026-02-29，备注不应出现在密文中。".repeat(20).toByteArray()
    @Test fun passwordEncryptionRoundTripsAndUsesFreshSaltAndNonce() {
        val first = BackupEncryption.encrypt(plain, password)
        val second = BackupEncryption.encrypt(plain, password)
        assertTrue(BackupEncryption.isEncrypted(first)); assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, BackupEncryption.decrypt(first, password))
        assertArrayEquals(plain, BackupEncryption.decrypt(second, password))
        assertFalse(first.toString(Charsets.UTF_8).contains("备注不应出现在密文中"))
    }
    @Test fun wrongPasswordAndModifiedSaltNoncePayloadOrTagNeverReturnPlaintext() {
        val encrypted = BackupEncryption.encrypt(plain, password)
        assertThrows(BackupUnlockException::class.java) { BackupEncryption.decrypt(encrypted, "wrong-password".toCharArray()) }
        for (position in listOf(12, 28, 40, encrypted.lastIndex)) {
            val modified = encrypted.clone().apply { this[position] = (this[position].toInt() xor 1).toByte() }
            assertThrows(BackupUnlockException::class.java) { BackupEncryption.decrypt(modified, password) }
        }
    }
    @Test fun truncatedUnknownVersionAndUnboundedKdfParametersAreRejected() {
        val encrypted = BackupEncryption.encrypt(plain, password)
        for (size in listOf(0, 7, 39, 40, 55, encrypted.size - 1)) {
            assertThrows(Exception::class.java) { BackupEncryption.decrypt(encrypted.copyOf(size), password) }
        }
        assertThrows(IllegalArgumentException::class.java) { BackupEncryption.decrypt(encrypted.clone().apply { this[7] = '2'.code.toByte() }, password) }
        val alteredIterations = encrypted.clone().apply { ByteBuffer.wrap(this).putInt(8, Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { BackupEncryption.decrypt(alteredIterations, password) }
    }
    @Test fun passwordLengthAndFileReadLimitsAreEnforcedBeforeEncryption() {
        for (length in listOf(0, 7, 129)) assertThrows(IllegalArgumentException::class.java) { BackupEncryption.encrypt(plain, CharArray(length) { 'a' }) }
        assertThrows(IllegalArgumentException::class.java) { BackupEncryption.readBounded(ByteArray(17).inputStream(), 16) }
        assertEquals(16, BackupEncryption.readBounded(ByteArray(16).inputStream(), 16).size)
    }
    @Test fun deviceCipherIsAuthenticatedAndCannotBeReadWithAnotherDeviceKey() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = LocalBackupCipher { key }; val encrypted = cipher.encrypt(plain)
        assertArrayEquals(plain, cipher.decrypt(encrypted)); assertFalse(encrypted.contentEquals(cipher.encrypt(plain)))
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(encrypted.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }) }
        val another = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(AEADBadTagException::class.java) { LocalBackupCipher { another }.decrypt(encrypted) }
    }
}
