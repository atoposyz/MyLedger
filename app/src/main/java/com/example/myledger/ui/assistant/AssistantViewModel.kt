package com.example.myledger.ui.assistant

import androidx.lifecycle.*
import com.example.myledger.assistant.*
import com.example.myledger.data.settings.IntegrationSettingsRepository
import com.example.myledger.network.ServiceException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class AssistantTurn(val question: String, val answer: String, val evidence: List<ToolEvidence>)
data class AssistantUiState(val configured: Boolean = false, val allowed: Boolean = false, val ready: Boolean = false,
    val busy: Boolean = false, val message: String? = null, val localResult: String? = null, val localBusy: Boolean = false,
    val turns: List<AssistantTurn> = emptyList(), val evidence: List<ToolEvidence> = emptyList(), val lastQuestion: String? = null)
class AssistantViewModel(private val config: IntegrationSettingsRepository, private val tools: FinancialTools,
    private val service: AssistantService) : ViewModel() {
    private val mutable = MutableStateFlow(AssistantUiState()); val state = mutable.asStateFlow()
    private var request: Job? = null
    private var requestVersion = 0
    init { viewModelScope.launch {
        try { config.settings.collect { settings ->
            mutable.update { it.copy(configured = settings.hasApiKey && settings.apiBase.isNotBlank() && settings.model.isNotBlank(), allowed = settings.allowAggregates, ready = true) }
            if (!settings.allowAggregates || !settings.hasApiKey) cancel()
        } } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(message = "无法读取配置，请重新打开页面") } }
    } }
    fun query(kind: FinancialQuery, start: String, end: String, scope: String, first: String, second: String) {
        if (state.value.localBusy) return
        mutable.update { it.copy(localBusy = true) }
        viewModelScope.launch {
            try {
                val args = if (kind == FinancialQuery.COMPARE) JSONObject().put("first_month", first).put("second_month", second).put("scope", scope)
                else JSONObject().put("start", start).put("end", end).apply {
                    if (kind == FinancialQuery.CATEGORY) put("scope", scope)
                    if (kind == FinancialQuery.ACTIVITY) put("activity_id", JSONObject.NULL)
                    if (kind == FinancialQuery.INCOME) put("include_balance", true)
                }
                val result = tools.execute(kind.function, args.toString()); mutable.update { it.copy(localResult = result) }
            } finally { mutable.update { it.copy(localBusy = false) } }
        }
    }
    fun ask(question: String) {
        val text = question.trim(); if (state.value.busy || !state.value.ready) return
        if (!state.value.configured || !state.value.allowed) { mutable.update { it.copy(message = "请先配置 API 并允许发送必要汇总") }; return }
        if (text.isBlank() || text.length > 2000) { mutable.update { it.copy(message = "请输入 1–2000 个字符的问题") }; return }
        val version = ++requestVersion
        mutable.update { it.copy(busy = true, message = null, evidence = emptyList(), lastQuestion = text) }
        request = viewModelScope.launch {
            try {
                val answer = service.ask(text) { evidence -> if (version == requestVersion) mutable.update { it.copy(evidence = evidence) } }
                if (version == requestVersion) mutable.update { it.copy(turns = (it.turns + AssistantTurn(text, answer.answer, answer.evidence)).takeLast(8), evidence = emptyList()) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == requestVersion) mutable.update { it.copy(message = if (error is ServiceException) error.message else "请求失败，请检查网络、API 地址/key、模型工具调用支持后重试") } }
            finally { if (version == requestVersion) mutable.update { it.copy(busy = false) } }
        }
    }
    fun retry() { state.value.lastQuestion?.let(::ask) }
    fun cancel() { requestVersion++; request?.cancel(); request = null; mutable.update { it.copy(busy = false) } }
    fun clear() { cancel(); mutable.update { it.copy(turns = emptyList(), evidence = emptyList(), lastQuestion = null, message = null) } }
}
