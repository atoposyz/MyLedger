package com.example.myledger.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device key never leaves Android Keystore. Portable exports use a separate password key. */
class LocalBackupCipher(private val key: () -> SecretKey = AndroidBackupKey::getOrCreate) {
    private val magic = "MYLLOC01".toByteArray(Charsets.US_ASCII)
    fun encrypt(zip: ByteArray): ByteArray {
        require(zip.size.toLong() <= BackupCodec.MAX_FILE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        require(cipher.iv.size == 12)
        val header = magic + cipher.iv
        cipher.updateAAD(header)
        return header + cipher.doFinal(zip)
    }
    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size >= 36 && bytes.size.toLong() <= BackupEncryption.MAX_ENCRYPTED_BYTES && bytes.copyOfRange(0, 8).contentEquals(magic))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(8, 20)))
        cipher.updateAAD(bytes.copyOfRange(0, 20))
        return cipher.doFinal(bytes, 20, bytes.size - 20)
    }
}

object AndroidBackupKey {
    private const val ALIAS = "myledger.local.backup.v1"
    @Synchronized fun getOrCreate(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(!store.containsAlias(ALIAS)) { "本机备份密钥不可用" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
