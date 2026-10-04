package com.example.myledger.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.assistant.*
import com.example.myledger.util.FinancialResultFormatter
import java.time.LocalDate
import java.time.YearMonth

@Composable fun AssistantRoute(onConfigure: () -> Unit) {
    val app = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(app) { viewModelFactory { initializer { AssistantViewModel(app.integrationSettings, app.financialTools, app.assistantService) } } }
    val vm: AssistantViewModel = viewModel(factory = factory); val state by vm.state.collectAsStateWithLifecycle()
    AssistantScreen(state, onConfigure, vm::ask, vm::retry, vm::cancel, vm::clear, vm::query)
}
@Composable fun AssistantScreen(state: AssistantUiState, onConfigure: () -> Unit, onAsk: (String) -> Unit, onRetry: () -> Unit,
    onCancel: () -> Unit, onClear: () -> Unit, onQuery: (FinancialQuery, String, String, String, String, String) -> Unit) {
    val month = remember { YearMonth.from(LocalDate.now()) }
    var kind by rememberSaveable { mutableStateOf(FinancialQuery.EXPENSE) }
    var start by rememberSaveable { mutableStateOf(month.atDay(1).toString()) }; var end by rememberSaveable { mutableStateOf(month.atEndOfMonth().toString()) }
    var first by rememberSaveable { mutableStateOf(month.minusMonths(1).toString()) }; var second by rememberSaveable { mutableStateOf(month.toString()) }
    var scope by rememberSaveable { mutableStateOf("DAILY") }; var question by remember { mutableStateOf("") }; var clear by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("本地查询", style = MaterialTheme.typography.titleMedium)
            Text("无需 API；与首页、统计页使用同一财务口径。日期区间最多 366 天。", style = MaterialTheme.typography.bodySmall)
            FinancialQuery.entries.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item -> FilterChip(kind == item, { kind = item }, enabled = !state.localBusy,
                    label = { Text(item.label) }, modifier = Modifier.weight(1f).testTag("assistant_kind_${item.name}")) }
            } }
            if (kind == FinancialQuery.COMPARE) {
                QueryInput(first, { first = it }, "前一月份 YYYY-MM", "assistant_first"); QueryInput(second, { second = it }, "后一月份 YYYY-MM", "assistant_second")
            } else {
                QueryInput(start, { start = it }, "开始日期 YYYY-MM-DD", "assistant_start"); QueryInput(end, { end = it }, "结束日期 YYYY-MM-DD", "assistant_end")
            }
            if (kind == FinancialQuery.COMPARE || kind == FinancialQuery.CATEGORY) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAILY" to "日常", "ALL" to "全部").forEach { (value, label) -> FilterChip(scope == value, { scope = value }, label = { Text(label) }) }
            }
            OutlinedButton({ focus.clearFocus(); keyboard?.hide(); onQuery(kind, start, end, scope, first, second) }, enabled = !state.localBusy,
                modifier = Modifier.testTag("assistant_query")) { Text("查询本地汇总") }
            if (state.localBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.localResult?.let { SelectionContainer { Text(FinancialResultFormatter.format(it), modifier = Modifier.testTag("assistant_local_result")) } }
            HorizontalDivider(); Text("AI 财务助手", style = MaterialTheme.typography.titleMedium)
            Text("仅发送你输入的问题和所需汇总（含日期、分类/活动名），不读取逐笔备注，不执行记账。每个问题独立分析；会话只保留本页内存。", style = MaterialTheme.typography.bodySmall)
            if (!state.configured) Text("尚未配置 API；可先使用上面的本地查询。", modifier = Modifier.testTag("assistant_unconfigured"))
            else if (!state.allowed) Text("尚未允许发送必要汇总，请在连接配置中开启。")
            TextButton(onConfigure, modifier = Modifier.testTag("assistant_configure")) { Text("配置 API 与汇总权限") }
            OutlinedTextField(question, { question = it.take(2000) }, label = { Text("例如：本月日常支出主要花在哪里？") }, minLines = 2, maxLines = 5,
                enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("assistant_question"))
            Button({ focus.clearFocus(); keyboard?.hide(); val text = question; question = ""; onAsk(text) },
                enabled = state.ready && state.configured && state.allowed && !state.busy && question.isNotBlank(), modifier = Modifier.testTag("assistant_send")) { Text("发送问题") }
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onCancel, modifier = Modifier.testTag("assistant_cancel")) { Text("取消请求") } }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("assistant_message"))
                if (state.lastQuestion != null) TextButton(onRetry, enabled = !state.busy && state.configured && state.allowed) { Text("重试上次问题") } }
            if (state.turns.isNotEmpty() || state.evidence.isNotEmpty()) TextButton({ clear = true }, modifier = Modifier.testTag("assistant_clear")) { Text("清空会话") }
            state.turns.forEach { turn ->
                HorizontalDivider(); Text(turn.question, style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(turn.answer) }
                Evidence(turn.evidence)
            }
            Evidence(state.evidence)
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("清空本页会话？") }, text = { Text("不会修改账本或 API 配置。") },
        dismissButton = { TextButton({ clear = false }) { Text("取消") } }, confirmButton = { TextButton({ clear = false; onClear() }) { Text("确认清空") } })
}
@Composable private fun QueryInput(value: String, change: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(value, { change(it.take(10)) }, singleLine = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag(tag))
}
@Composable private fun Evidence(rows: List<ToolEvidence>) {
    rows.forEach { row ->
        Text("查询依据 · ${FinancialQuery.entries.firstOrNull { it.function == row.name }?.label ?: "工具请求"}", style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(FinancialResultFormatter.format(row.result), style = MaterialTheme.typography.bodySmall) }
    }
}
