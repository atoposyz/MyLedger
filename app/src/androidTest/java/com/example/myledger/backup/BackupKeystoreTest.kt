package com.example.myledger.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupKeystoreTest {
    @Test fun realAndroidKeystoreKeyIsNonExportableAndReadsAcrossCipherInstances() {
        val key = AndroidBackupKey.getOrCreate(); assertNull(key.encoded)
        val payload = "Keystore backup round-trip without modifying the ledger".toByteArray()
        val encrypted = LocalBackupCipher().encrypt(payload)
        assertArrayEquals(payload, LocalBackupCipher().decrypt(encrypted))
    }
}
