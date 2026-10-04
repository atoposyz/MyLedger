package com.example.myledger.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.data.settings.*
import com.example.myledger.network.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RemoteBackupTest {
    private lateinit var database: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var manager: BackupManager
    private lateinit var settings: IntegrationSettingsRepository
    private lateinit var remote: RemoteBackupRepository
    private lateinit var directory: File
    private val job = SupervisorJob()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var cipherBytes = ByteArray(0)
    private var fail = false
    private var tamper = false
    private val paths = mutableListOf<String>()
    private val id = "00000000-0000-0000-0000-000000000001"
    private fun metadata() = JSONObject().put("id", id).put("createdAt", "2026-10-04T00:00:00Z")
        .put("sizeBytes", cipherBytes.size).put("sha256", RemoteBackupRepository.digest(cipherBytes))
    @Before fun setup() {
        val base = RuntimeEnvironment.getApplication(); directory = File(base.cacheDir, "remote-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() } }
        val scope = CoroutineScope(job + Dispatchers.IO)
        settings = IntegrationSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(directory, "integrations.preferences_pb") }, CredentialCipher { key })
        val theme = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(directory, "theme.preferences_pb") })
        database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).addCallback(AppDatabase.seedCategories).build(); ledger = LedgerRepository(database)
        manager = BackupManager(context, ledger, theme, LocalBackupCipher { key })
        remote = RemoteBackupRepository(settings, manager, HttpTransport { method, url, token, body, type, _ ->
            assertEquals("token-12345678901234567890123456789012", token); paths += "$method $url"
            if (fail) throw ServiceException("认证失败，请检查密钥或令牌")
            when (method) {
                "PUT" -> { assertEquals("application/octet-stream", type); cipherBytes = requireNotNull(body).copyOf(); metadata().toString().toByteArray() }
                "GET" -> if (url.endsWith("/api/backups")) JSONObject().put("backups", JSONArray().put(metadata())).toString().toByteArray()
                    else cipherBytes.copyOf().also { if (tamper) it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                else -> "{\"deleted\":true}".toByteArray()
            }
        })
    }
    @After fun close() = runBlocking { database.close(); job.cancelAndJoin() }
    private suspend fun configured() { settings.saveServer("https://backup.example.com/", "token-12345678901234567890123456789012") }
    private suspend fun add(amount: Long) = ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amountMinor = amount, categoryId = 1,
        date = LocalDate.of(2026, 10, 4), note = "Never upload plain ledger note"))
    @Test fun remoteUploadIsEncryptedAndDownloadOnlyStagesUntilConfirmed() = runBlocking {
        configured(); add(Long.MAX_VALUE); val original = ledger.snapshot()
        val entry = remote.upload("Backup-password".toCharArray())
        assertTrue(BackupEncryption.isEncrypted(cipherBytes)); assertFalse(cipherBytes.toString(Charsets.UTF_8).contains("Never upload"))
        assertEquals(original, BackupCodec.read(BackupEncryption.decrypt(cipherBytes, "Backup-password".toCharArray()).inputStream()).ledger)
        assertEquals(entry, remote.list().single()); add(1); val before = ledger.snapshot()
        val locked = remote.download(entry); assertEquals(before, ledger.snapshot())
        assertThrows(BackupUnlockException::class.java) { runBlocking { manager.unlockImport(locked, "Wrong-password".toCharArray()) } }
        assertEquals(before, ledger.snapshot()); val preview = manager.unlockImport(locked, "Backup-password".toCharArray())
        assertEquals(before, ledger.snapshot()); manager.restore(preview.fileName); assertEquals(original, ledger.snapshot())
        remote.delete(entry); assertTrue(paths.last().startsWith("DELETE https://backup.example.com/api/backups/"))
    }
    @Test fun tamperedDownloadAndNetworkFailuresNeverChangeLedgerOrCreatePreview() = runBlocking {
        configured(); add(100); val entry = remote.upload("Backup-password".toCharArray()); val before = ledger.snapshot()
        tamper = true; assertThrows(IllegalArgumentException::class.java) { runBlocking { remote.download(entry) } }
        fail = true; assertThrows(ServiceException::class.java) { runBlocking { remote.upload("Backup-password".toCharArray()) } }
        assertEquals(before, ledger.snapshot()); assertEquals(0, File(directory, "cache/backups/exports").listFiles()?.size ?: 0)
    }
    @Test fun configurationSecretsAreEncryptedAndChangingAddressRequiresNewSecret() = runBlocking {
        configured(); settings.saveAssistant("https://api.example.com/v1", "my-model", AssistantProtocol.RESPONSES, "test-key-private")
        settings.allowAggregates(true)
        val stored = File(directory, "integrations.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(stored.contains("test-key-private")); assertFalse(stored.contains("token-123456"))
        assertEquals("test-key-private", settings.assistantCredentials().secret)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { settings.saveServer("https://other.example.com", null) } }
        assertEquals("https://backup.example.com", settings.current().serverUrl)
        settings.saveServer("https://backup.example.com", null); settings.clearServer(); settings.clearAssistant()
        assertFalse(settings.current().hasApiKey); assertFalse(settings.current().hasServerToken); assertFalse(settings.current().allowAggregates)
    }
    @Test fun noConfigurationOrConsentProducesNoNetworkTraffic() = runBlocking {
        assertThrows(Exception::class.java) { runBlocking { remote.list() } }; assertTrue(paths.isEmpty())
        settings.saveAssistant("https://api.example.com/v1", "my-model", AssistantProtocol.CHAT_COMPLETIONS, "test-key")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { settings.assistantCredentials() } }
        assertTrue(paths.isEmpty())
    }
    @Test fun endpointValidationRejectsCleartextCredentialsQueriesAndDotPaths() {
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com?q=key", "https://example.com/#x", "https://example.com/a/../b", "https://example.com/%2e").forEach {
            assertThrows(Exception::class.java) { ServiceAddress.normalize(it) }
        }
        assertEquals("https://example.com/v1", ServiceAddress.normalize(" https://example.com/v1/ "))
        assertThrows(IllegalArgumentException::class.java) { ServiceAddress.secret("key\nsecret") }
    }
}
