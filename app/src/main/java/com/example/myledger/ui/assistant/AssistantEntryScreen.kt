package com.example.myledger.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.ui.batchentry.*
import com.example.myledger.ui.components.EntryDateDialog

@Composable fun AssistantEntryRoute(onConfigure: () -> Unit, onSaved: (SavedBatch) -> Unit) {
    val app = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(app) { viewModelFactory { initializer { AssistantEntryViewModel(app.integrationSettings, app.assistantEntryService, createSavedStateHandle()) } } }
    val vm: AssistantEntryViewModel = viewModel(factory = factory)
    val batchFactory = remember(app) { viewModelFactory { initializer { BatchEntryViewModel(app.repository, createSavedStateHandle()) } } }
    val batch: BatchEntryViewModel = viewModel(key = "ai-pending-drafts", factory = batchFactory)
    val state by vm.state.collectAsStateWithLifecycle(); val batchState by batch.state.collectAsStateWithLifecycle()
    val currentSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.proposal) {
        state.proposal?.takeIf { it.drafts.isNotEmpty() }?.let { batch.setDefaultDate(state.date); batch.importProposals(it.drafts); vm.reviewed() }
    }
    LaunchedEffect(batchState.saved) { batchState.saved?.let(currentSaved) }
    if (state.reviewing) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("AI 草稿 · 尚未保存\n请核对金额、分类、日期；可修改、删除或关联活动。确认后点击全部保存。",
                    Modifier.fillMaxWidth().padding(16.dp).testTag("ai_pending_notice"), style = MaterialTheme.typography.bodyMedium)
            }
            BatchDailyEntryScreen(batchState, batch::setDefaultDate, batch::setDefaultActivity, batch::setDefaultReimbursable,
                batch::addDraft, batch::removeDraft, batch::setType, batch::setAmount, batch::setCategory, batch::setActivity,
                batch::setReimbursable, batch::setDate, batch::setNote, batch::saveAll, batch::reload, Modifier.weight(1f), showDefaults = false)
        }
    } else AssistantEntryScreen(state, onConfigure, vm::generate, vm::cancel, vm::date)
}

@Composable fun AssistantEntryScreen(state: AssistantEntryUiState, onConfigure: () -> Unit, onGenerate: (String) -> Unit,
    onCancel: () -> Unit, onDate: (java.time.LocalDate) -> Unit) {
    var description by rememberSaveable { mutableStateOf("") }; var dateDialog by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("说一句，先生成草稿", style = MaterialTheme.typography.headlineSmall)
            Text("例如：今晚吃饭32块，打车18.50元。支持同时描述多笔；已有账目不会发送给模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Text("你输入的描述会发送给所配置的 API。AI 可能理解有误，生成后请逐条检查；确认保存前账本不会改变。",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (!state.configured) Text("尚未配置 API；请先保存地址、模型和 key。", Modifier.testTag("ai_entry_unconfigured"))
            else if (!state.allowed) Text("请在连接配置中开启“允许发送记账描述”。")
            TextButton(onConfigure, enabled = !state.busy, modifier = Modifier.testTag("ai_entry_configure")) { Text("连接配置") }
            OutlinedButton({ dateDialog = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("ai_entry_date")) { Text("未写日期时默认：${state.date}") }
            OutlinedTextField(description, { description = it.take(2000) }, enabled = !state.busy,
                label = { Text("想记下什么？") }, minLines = 3, maxLines = 6,
                supportingText = { Text("${description.length}/2000 · 支出、收入、报销到账均可") }, modifier = Modifier.fillMaxWidth().testTag("ai_entry_description"))
            Button({ focus.clearFocus(); keyboard?.hide(); onGenerate(description) },
                enabled = state.ready && state.configured && state.allowed && !state.busy && description.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_entry_generate")) { Text(if (state.busy) "正在生成…" else "生成待确认草稿") }
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onCancel, Modifier.testTag("ai_entry_cancel")) { Text("取消请求") } }
            state.message?.let { Text(it, Modifier.testTag("ai_entry_message")) }
        }
    }
    if (dateDialog) EntryDateDialog(state.date, onDate, { dateDialog = false })
}
