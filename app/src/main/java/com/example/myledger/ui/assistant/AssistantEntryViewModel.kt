package com.example.myledger.ui.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.assistant.*
import com.example.myledger.data.settings.IntegrationSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AssistantEntryUiState(val ready: Boolean = false, val configured: Boolean = false, val allowed: Boolean = false,
    val busy: Boolean = false, val reviewing: Boolean = false, val message: String? = null, val proposal: EntryProposal? = null,
    val date: LocalDate = LocalDate.now())
class AssistantEntryViewModel(private val settings: IntegrationSettingsRepository, private val service: AssistantEntryService,
    private val savedState: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(AssistantEntryUiState(reviewing = savedState["reviewing"] ?: false,
        date = savedState.get<Long>("date")?.let(LocalDate::ofEpochDay) ?: LocalDate.now()))
    val state = mutable.asStateFlow()
    private var job: Job? = null; private var version = 0
    init { viewModelScope.launch {
        try { settings.settings.collect { config ->
            mutable.update { it.copy(ready = true, configured = config.hasApiKey && config.apiBase.isNotBlank() && config.model.isNotBlank(), allowed = config.allowEntryText) }
            if (!config.allowEntryText && state.value.busy) cancel()
        } } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(message = "读取配置失败，请重新打开页面") } }
    } }
    fun date(value: LocalDate) { if (!state.value.busy) { savedState["date"] = value.toEpochDay(); mutable.update { it.copy(date = value) } } }
    fun generate(description: String) {
        val current = state.value
        if (!current.ready || !current.configured || !current.allowed || current.busy || current.reviewing || description.isBlank() || description.length > 2000) return
        val request = ++version
        mutable.update { it.copy(busy = true, message = null, proposal = null) }
        job = viewModelScope.launch {
            try {
                val result = service.propose(description, current.date)
                if (version == request) mutable.update { it.copy(busy = false, message = result.message, proposal = result) }
            } catch (error: CancellationException) {
                if (version == request) mutable.update { it.copy(busy = false, message = "请求已取消；没有保存任何账目") }
                throw error
            } catch (_: Exception) {
                if (version == request) mutable.update { it.copy(busy = false, message = "未能生成有效草稿。请检查网络、API 配置或补充明确的金额和日期，再重试；账本没有改变。") }
            }
        }
    }
    fun reviewed() { savedState["reviewing"] = true; mutable.update { it.copy(reviewing = true, proposal = null) } }
    fun cancel() { ++version; job?.cancel(); job = null; mutable.update { it.copy(busy = false, proposal = null, message = "请求已取消；没有保存任何账目") } }
}
