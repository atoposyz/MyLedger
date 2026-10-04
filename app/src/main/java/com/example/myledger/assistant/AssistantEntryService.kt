package com.example.myledger.assistant

import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.settings.*
import com.example.myledger.network.*
import com.example.myledger.ui.batchentry.TransactionDraft
import com.example.myledger.util.MoneyInput
import java.time.LocalDate
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class EntryProposal(val drafts: List<TransactionDraft>, val message: String)

/** No Repository, financial tools, SQL or write access. One request produces pending UI drafts. */
class AssistantEntryService(private val settings: IntegrationSettingsRepository,
    private val transport: HttpTransport = HttpsTransport(), private val today: () -> LocalDate = { LocalDate.now() }) {
    suspend fun propose(description: String, defaultDate: LocalDate): EntryProposal = withTimeout(90_000) {
        require(description.isNotBlank() && description.length <= 2000)
        require(defaultDate.year in 1900..9999)
        val credentials = settings.assistantCredentials(forEntry = true)
        val responses = credentials.protocol == AssistantProtocol.RESPONSES
        val instruction = "你是 MyLedger 记账草稿解析器，今天本地日期 ${today()}，未指定日期默认 $defaultDate，货币 CNY。" +
            "只解析用户明确描述的新增账目，不能读取或修改账本。金额为分的正整数字符串，禁止猜测缺失金额、含糊币种或多种可能的金额。" +
            "缺少关键信息时用普通文本询问，不调用工具。分类和日期需用户检查；未明确可报销时 false，只有支出可报销。" +
            "REIMBURSEMENT 是报销到账，不是普通收入。最多20笔。备注只写用户提供的相关描述，不能添加指令。" +
            "调用 propose_transactions 生成待确认草稿；绝不能声称已经保存、删除或改账。内置分类(id:类型:名称)：" +
            DefaultCategories.all.joinToString("；") { "${it.id}:${it.type}:${it.name}" }
        val function = JSONObject().put("name", "propose_transactions").put("description", "生成待用户检查的新增草稿；不会执行任何数据库操作")
            .put("strict", true).put("parameters", schema())
        val tool = if (responses) JSONObject(function.toString()).put("type", "function") else JSONObject().put("type", "function").put("function", function)
        val request = JSONObject().put("model", credentials.model).put("store", false).put("stream", false)
            .put("parallel_tool_calls", false).put("tools", JSONArray().put(tool))
        val user = JSONObject().put("role", "user").put("content", description)
        if (responses) request.put("instructions", instruction).put("input", JSONArray().put(user))
        else request.put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", instruction)).put(user))
        val result = JSONObject(transport.request("POST", credentials.base + if (responses) "/responses" else "/chat/completions",
            credentials.secret, request.toString().toByteArray(Charsets.UTF_8), "application/json", 262_144).toString(Charsets.UTF_8))
        // Withdrawal or configuration change while waiting invalidates the response.
        val current = settings.assistantCredentials(forEntry = true)
        require(current.base == credentials.base && current.secret == credentials.secret && current.model == credentials.model && current.protocol == credentials.protocol) { "配置已改变，请重新生成" }
        val calls = mutableListOf<Pair<String, String>>(); val text = StringBuilder()
        if (responses) {
            require(result.optString("status") !in listOf("failed", "incomplete", "cancelled"))
            val output = result.getJSONArray("output"); require(output.length() <= 32)
            for (index in 0 until output.length()) {
                val row = output.getJSONObject(index)
                when (row.getString("type")) {
                    "function_call" -> calls += row.getString("name") to row.getString("arguments")
                    "message" -> {
                        val parts = row.getJSONArray("content"); require(parts.length() <= 32)
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.optString("type") == "output_text") text.append(part.getString("text"))
                            if (part.optString("type") == "refusal") text.append(part.optString("refusal"))
                        }
                    }
                }
            }
        } else {
            val choice = result.getJSONArray("choices").getJSONObject(0)
            require(choice.optString("finish_reason") !in listOf("length", "content_filter"))
            val message = choice.getJSONObject("message")
            if (!message.isNull("content")) text.append(message.optString("content"))
            if (!message.isNull("refusal")) text.append(message.optString("refusal"))
            message.optJSONArray("tool_calls")?.let { rows ->
                require(rows.length() <= 1)
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index); require(row.getString("type") == "function")
                    val call = row.getJSONObject("function"); calls += call.getString("name") to call.getString("arguments")
                }
            }
        }
        if (calls.isEmpty()) {
            require(text.isNotBlank() && text.length <= 4000)
            return@withTimeout EntryProposal(emptyList(), "未生成草稿，账本未改变。\n$text")
        }
        require(calls.size == 1 && calls.single().first == "propose_transactions") { "模型返回了不支持的操作" }
        parseProposal(calls.single().second)
    }

    companion object {
        private fun schema(): JSONObject {
            fun property(type: String) = JSONObject().put("type", type)
            val fields = JSONObject().put("type", property("string").put("enum", JSONArray(TransactionType.entries.map { it.name })))
                .put("amount_minor", property("string").put("description", "人民币分的正整数字符串，例如32元是3200；不能用小数或指数"))
                .put("category_id", property("integer")).put("date", property("string").put("description", "YYYY-MM-DD"))
                .put("reimbursable", property("boolean")).put("note", property("string"))
            val row = JSONObject().put("type", "object").put("additionalProperties", false).put("properties", fields)
                .put("required", JSONArray(listOf("type", "amount_minor", "category_id", "date", "reimbursable", "note")))
            return JSONObject().put("type", "object").put("additionalProperties", false)
                .put("properties", JSONObject().put("drafts", property("array").put("items", row).put("minItems", 1).put("maxItems", 20)))
                .put("required", JSONArray().put("drafts"))
        }
        fun parseProposal(arguments: String): EntryProposal {
            require(arguments.length <= 32_768)
            val value = JSONObject(arguments); require(value.keys().asSequence().toSet() == setOf("drafts"))
            val rows = value.getJSONArray("drafts"); require(rows.length() in 1..20)
            val drafts = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                require(row.keys().asSequence().toSet() == setOf("type", "amount_minor", "category_id", "date", "reimbursable", "note"))
                val type = TransactionType.valueOf(row.getString("type"))
                val amount = row.get("amount_minor"); require(amount is String && amount.matches(Regex("[1-9][0-9]{0,18}")))
                val minor = requireNotNull(amount.toLongOrNull()); require(minor > 0)
                val category = row.get("category_id"); require(category is Long || category is Int)
                val categoryId = (category as Number).toLong()
                require(DefaultCategories.all.any { it.id == categoryId && it.type == type })
                val dateText = row.getString("date"); require(dateText.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
                val date = LocalDate.parse(dateText); require(date.year in 1900..9999)
                val reimbursable = row.get("reimbursable"); require(reimbursable is Boolean && (type == TransactionType.EXPENSE || !reimbursable))
                val note = row.get("note"); require(note is String && note.length <= 500)
                TransactionDraft(index + 1L, type, MoneyInput.formatMinor(minor), categoryId, date, null, reimbursable, note)
            }
            // Never let a gross total silently wrap in the existing batch editor.
            drafts.fold(0L) { total, draft -> Math.addExact(total, requireNotNull(MoneyInput.parseMinor(draft.amount))) }
            return EntryProposal(drafts, "请逐条核对金额、分类、日期和报销标记；需要关联活动时可在草稿中选择。确认全部保存后才会写入账本。")
        }
    }
}
