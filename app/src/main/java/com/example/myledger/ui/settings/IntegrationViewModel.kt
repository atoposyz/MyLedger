package com.example.myledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.data.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntegrationUiState(val settings: IntegrationSettings = IntegrationSettings(), val ready: Boolean = false,
    val busy: Boolean = false, val message: String? = null)
class IntegrationViewModel(private val repository: IntegrationSettingsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(IntegrationUiState()); val state = mutable.asStateFlow()
    init { viewModelScope.launch {
        try { repository.settings.collect { value -> mutable.update { it.copy(settings = value, ready = true) } } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(message = "读取配置失败，请重新打开页面") } }
    } }
    private fun operation(secret: CharArray? = null, action: suspend () -> Unit) {
        if (!state.value.ready || state.value.busy) { secret?.fill('\u0000'); return }
        mutable.update { it.copy(busy = true, message = null) }
        val job = viewModelScope.launch {
            try { action(); mutable.update { it.copy(message = "配置已保存") } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(message = "保存失败。请检查 HTTPS 地址、密钥和模型；换地址需重新填写密钥。") } }
            finally { mutable.update { it.copy(busy = false) } }
        }; job.invokeOnCompletion { secret?.fill('\u0000') }
    }
    fun server(url: String, secret: CharArray?) = operation(secret) { repository.saveServer(url, secret?.let(::String)) }
    fun assistant(url: String, model: String, protocol: AssistantProtocol, secret: CharArray?) = operation(secret) {
        repository.saveAssistant(url, model, protocol, secret?.let(::String))
    }
    fun allow(enabled: Boolean) = operation { repository.allowAggregates(enabled) }
    fun clearServer() = operation { repository.clearServer() }
    fun clearAssistant() = operation { repository.clearAssistant() }
}
