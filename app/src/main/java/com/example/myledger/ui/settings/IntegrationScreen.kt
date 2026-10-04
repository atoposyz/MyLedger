package com.example.myledger.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.data.settings.AssistantProtocol

@Composable fun IntegrationRoute(includeAssistant: Boolean = false) {
    val app = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(app) { viewModelFactory { initializer { IntegrationViewModel(app.integrationSettings) } } }
    val vm: IntegrationViewModel = viewModel(factory = factory); val state by vm.state.collectAsStateWithLifecycle()
    IntegrationScreen(state, vm::server, vm::assistant, vm::allow, vm::clearServer, vm::clearAssistant, includeAssistant, vm::allowEntry)
}
@Composable fun IntegrationScreen(state: IntegrationUiState, onServer: (String, CharArray?) -> Unit,
    onAssistant: (String, String, AssistantProtocol, CharArray?) -> Unit, onAllow: (Boolean) -> Unit,
    onClearServer: () -> Unit, onClearAssistant: () -> Unit, includeAssistant: Boolean = false, onAllowEntry: (Boolean) -> Unit = {}) {
    val config = state.settings
    var server by rememberSaveable(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var token by remember { mutableStateOf("") }
    var api by rememberSaveable(config.apiBase) { mutableStateOf(config.apiBase) }
    var model by rememberSaveable(config.model) { mutableStateOf(config.model) }
    var protocol by rememberSaveable(config.protocol) { mutableStateOf(config.protocol) }
    var key by remember { mutableStateOf("") }
    var clear by remember { mutableStateOf<String?>(null) }
    val enabled = state.ready && !state.busy
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("自建备份服务器", style = MaterialTheme.typography.titleMedium)
            Text("填写 HTTPS 根地址，例如 https://backup.example.com。服务器只保存密码加密文件；备份密码不上传、不保存。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(server, { server = it.take(2048) }, enabled = enabled, label = { Text("服务器地址") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("config_server"))
            SecretInput(token, { token = it }, "备份访问令牌", "config_token", enabled)
            Text(if (config.hasServerToken) "已保存令牌；留空沿用。更换地址需重新输入。" else "尚未保存令牌", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { val secret = token.takeIf { it.isNotEmpty() }?.toCharArray(); token = ""; onServer(server, secret) },
                enabled = enabled && server.isNotBlank() && (token.isNotBlank() || config.hasServerToken), modifier = Modifier.testTag("config_save_server")) { Text("保存服务器配置") }
            if (config.hasServerToken) TextButton(onClick = { clear = "server" }, enabled = enabled) { Text("清除服务器配置") }
            if (includeAssistant) {
                HorizontalDivider(); Text("财务助手 API", style = MaterialTheme.typography.titleMedium)
                Text("填写包含版本路径的 API 基础地址（例如 https://api.example.com/v1），以及支持工具调用的模型。key 仅保存在本机加密配置中，不进入账本备份。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(api, { api = it.take(2048) }, enabled = enabled, label = { Text("API 基础地址") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("config_api"))
                OutlinedTextField(model, { model = it.take(128) }, enabled = enabled, label = { Text("模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("config_model"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantProtocol.entries.forEach { item -> FilterChip(protocol == item, { protocol = item }, enabled = enabled,
                        label = { Text(if (item == AssistantProtocol.CHAT_COMPLETIONS) "Chat Completions" else "Responses") }) }
                }
                SecretInput(key, { key = it }, "API key", "config_key", enabled)
                Text(if (config.hasApiKey) "已保存 key；留空沿用。更换地址需重新输入。" else "尚未保存 API key", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { val secret = key.takeIf { it.isNotEmpty() }?.toCharArray(); key = ""; onAssistant(api, model, protocol, secret) },
                    enabled = enabled && api.isNotBlank() && model.isNotBlank() && (key.isNotBlank() || config.hasApiKey), modifier = Modifier.testTag("config_save_api")) { Text("保存助手配置") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("允许发送必要的财务汇总", modifier = Modifier.weight(1f))
                    Switch(config.allowAggregates, onAllow, enabled = enabled, modifier = Modifier.testTag("config_allow"))
                }
                Text("仅在发送问题时联网。问题、查询日期、分类/活动名称与聚合金额会发给你配置的服务；不会发送逐笔流水或备注。关闭后不能发送新问题。财务分析保持只读。", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("允许发送记账描述", modifier = Modifier.weight(1f))
                    Switch(config.allowEntryText, onAllowEntry, enabled = enabled, modifier = Modifier.testTag("config_allow_entry"))
                }
                Text("仅在点击生成草稿时发送你输入的描述、参考日期和内置分类，不读取已有账目或活动。AI 只生成草稿，逐条检查并确认后才能保存。此权限与财务汇总独立。", style = MaterialTheme.typography.bodySmall)
                if (config.hasApiKey) TextButton(onClick = { clear = "assistant" }, enabled = enabled) { Text("清除助手配置") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, modifier = Modifier.testTag("config_message")) }
        }
    }
    clear?.let { selected -> AlertDialog(onDismissRequest = { clear = null }, title = { Text("清除本机连接配置？") }, text = { Text("不会删除账目或服务器上的备份。") },
        dismissButton = { TextButton(onClick = { clear = null }) { Text("取消") } },
        confirmButton = { TextButton(onClick = { clear = null; if (selected == "server") onClearServer() else onClearAssistant() }) { Text("确认清除") } }) }
}
@Composable private fun SecretInput(value: String, change: (String) -> Unit, label: String, tag: String, enabled: Boolean) {
    OutlinedTextField(value, { if (it.length <= 4096) change(it) }, enabled = enabled, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag(tag))
}
