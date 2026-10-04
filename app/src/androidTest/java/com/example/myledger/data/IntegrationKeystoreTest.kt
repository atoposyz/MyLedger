package com.example.myledger.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myledger.data.settings.CredentialCipher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegrationKeystoreTest {
    @Test fun deviceConfigurationCipherReadsAcrossInstancesAndRejectsChangedCiphertext() {
        val secret = "instrumentation-fixture-not-a-real-key"
        val encrypted = CredentialCipher().encrypt(secret)
        assertFalse(encrypted.contains(secret)); assertEquals(secret, CredentialCipher().decrypt(encrypted))
        val bytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { CredentialCipher().decrypt(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)) }
    }
}
