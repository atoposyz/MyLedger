package com.example.myledger.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.myledger.network.ServiceAddress
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.integrations by preferencesDataStore("integrations")
enum class AssistantProtocol { CHAT_COMPLETIONS, RESPONSES }
data class IntegrationSettings(val serverUrl: String = "", val hasServerToken: Boolean = false,
    val apiBase: String = "", val model: String = "", val protocol: AssistantProtocol = AssistantProtocol.CHAT_COMPLETIONS,
    val hasApiKey: Boolean = false, val allowAggregates: Boolean = false, val allowEntryText: Boolean = false)
class ServiceCredentials(val base: String, val secret: String, val model: String = "",
    val protocol: AssistantProtocol = AssistantProtocol.CHAT_COMPLETIONS)

/** Separate device key and DataStore; credentials never enter ledger backups or SavedState. */
class CredentialCipher(private val key: () -> SecretKey = ::integrationKey) {
    private val aad = "MyLedger integrations v1".toByteArray()
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        require(cipher.iv.size == 12); cipher.updateAAD(aad)
        val plain = value.toByteArray(Charsets.UTF_8)
        return try { Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP) } finally { plain.fill(0) }
    }
    fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP); require(bytes.size in 28..8192)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))); cipher.updateAAD(aad)
        val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
        return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
    }
}
@Synchronized private fun integrationKey(): SecretKey {
    val alias = "myledger.integrations.v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    check(!store.containsAlias(alias)) { "配置密钥不可用，请清除配置后重新填写" }
    return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    }.generateKey()
}

class IntegrationSettingsRepository(private val store: DataStore<Preferences>, private val cipher: CredentialCipher = CredentialCipher()) {
    private val server = stringPreferencesKey("server_url"); private val token = stringPreferencesKey("server_token_encrypted")
    private val api = stringPreferencesKey("api_base"); private val model = stringPreferencesKey("api_model")
    private val protocol = stringPreferencesKey("api_protocol"); private val key = stringPreferencesKey("api_key_encrypted")
    private val aggregates = booleanPreferencesKey("allow_aggregates")
    private val entryText = booleanPreferencesKey("allow_entry_text")
    val settings = store.data.map { IntegrationSettings(it[server].orEmpty(), !it[token].isNullOrEmpty(), it[api].orEmpty(), it[model].orEmpty(),
        AssistantProtocol.entries.firstOrNull { entry -> entry.name == it[protocol] } ?: AssistantProtocol.CHAT_COMPLETIONS,
        !it[key].isNullOrEmpty(), it[aggregates] ?: false, it[entryText] ?: false) }
    suspend fun current() = settings.first()
    suspend fun saveServer(url: String, secret: String?) = withContext(Dispatchers.IO) {
        val base = ServiceAddress.normalize(url); secret?.let(ServiceAddress::secret)
        val encrypted = secret?.let(cipher::encrypt)
        store.edit { require(encrypted != null || it[server] == base && it[token] != null) { "更换服务器地址时请重新填写令牌" }
            it[server] = base; if (encrypted != null) it[token] = encrypted }
    }
    suspend fun saveAssistant(url: String, modelName: String, apiProtocol: AssistantProtocol, secret: String?) = withContext(Dispatchers.IO) {
        val base = ServiceAddress.normalize(url); require(modelName.matches(Regex("[a-zA-Z0-9._:/-]{1,128}"))) { "请输入有效模型名称" }
        secret?.let(ServiceAddress::secret); val encrypted = secret?.let(cipher::encrypt)
        store.edit { require(encrypted != null || it[api] == base && it[key] != null) { "更换 API 地址时请重新填写 key" }
            it[api] = base; it[model] = modelName; it[protocol] = apiProtocol.name; if (encrypted != null) it[key] = encrypted }
    }
    suspend fun serverCredentials(): ServiceCredentials = withContext(Dispatchers.IO) {
        val value = store.data.first(); ServiceCredentials(ServiceAddress.normalize(value[server].orEmpty()), cipher.decrypt(requireNotNull(value[token]) { "请先配置服务器" }))
    }
    suspend fun assistantCredentials(forEntry: Boolean = false): ServiceCredentials = withContext(Dispatchers.IO) {
        val value = store.data.first(); require(value[if (forEntry) entryText else aggregates] == true) { if (forEntry) "请先允许发送记账描述" else "请先允许发送必要的汇总" }
        ServiceCredentials(ServiceAddress.normalize(value[api].orEmpty()), cipher.decrypt(requireNotNull(value[key]) { "请先配置 API key" }),
            requireNotNull(value[model]), AssistantProtocol.valueOf(requireNotNull(value[protocol])))
    }
    suspend fun allowAggregates(allow: Boolean) { store.edit { it[aggregates] = allow } }
    suspend fun allowEntryText(allow: Boolean) { store.edit { it[entryText] = allow } }
    suspend fun clearServer() { store.edit { it.remove(server); it.remove(token) } }
    suspend fun clearAssistant() { store.edit { it.remove(api); it.remove(key); it.remove(model); it.remove(protocol); it.remove(aggregates); it.remove(entryText) } }
    companion object { fun create(context: Context) = IntegrationSettingsRepository(context.applicationContext.integrations) }
}
