package com.example.myledger.assistant

import android.app.Application
import android.os.Looper
import androidx.lifecycle.*
import com.example.myledger.ui.assistant.AssistantViewModel
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.data.settings.*
import com.example.myledger.network.*
import com.example.myledger.util.FinancialResultFormatter
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FinancialAssistantTest {
    private lateinit var database: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var tools: FinancialTools
    private lateinit var settings: IntegrationSettingsRepository
    private val job = SupervisorJob()
    private val date = LocalDate.of(2026, 10, 4)
    private val args get() = JSONObject().put("start", "2026-10-01").put("end", "2026-10-31")
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).addCallback(AppDatabase.seedCategories).build()
        ledger = LedgerRepository(database); tools = FinancialTools(ledger)
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        settings = IntegrationSettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
            File(context.cacheDir, "assistant-${UUID.randomUUID()}.preferences_pb") }, CredentialCipher { key })
    }
    @After fun close() = runBlocking { database.close(); job.cancelAndJoin() }
    private suspend fun add(type: TransactionType, amount: Long, category: Long, activity: Long? = null, reimbursable: Boolean = false, day: LocalDate = date) =
        ledger.addTransaction(TransactionEntity(type = type, amountMinor = amount, categoryId = category, date = day, activityId = activity,
            reimbursable = reimbursable, note = "PRIVATE-NOTE-DO-NOT-SEND"))
    private suspend fun seed() {
        val activity = ledger.addActivity(ActivityEntity(name = "科研出差", type = ActivityType.WORK, note = "PRIVATE-ACTIVITY-NOTE"))
        add(TransactionType.EXPENSE, 100, 1); add(TransactionType.EXPENSE, 200, 1, activity, true)
        add(TransactionType.EXPENSE, 300, 2, null, true); add(TransactionType.INCOME, 1000, 12); add(TransactionType.REIMBURSEMENT, 800, 16)
        add(TransactionType.EXPENSE, 50, 1, day = date.minusMonths(1))
    }
    private suspend fun execute(kind: FinancialQuery, arguments: JSONObject = args) = JSONObject(tools.execute(kind.function, arguments.toString()))
    private suspend fun configure(protocol: AssistantProtocol = AssistantProtocol.CHAT_COMPLETIONS) {
        settings.saveAssistant("https://api.example.com/v1", "test-model", protocol, "test-key-not-for-real-service"); settings.allowAggregates(true)
    }
    @Test fun allSixToolsShareFinancialRulesAndNeverReturnPrivateNotesOrRawRows() = runBlocking {
        seed(); val original = ledger.snapshot()
        val expense = execute(FinancialQuery.EXPENSE); assertEquals("600", expense.getString("all_expense_minor")); assertEquals("100", expense.getString("daily_expense_minor"))
        val category = execute(FinancialQuery.CATEGORY, args.put("scope", "DAILY")); assertEquals("100", category.getJSONArray("categories").getJSONObject(0).getString("amount_minor"))
        val activity = execute(FinancialQuery.ACTIVITY, args.put("activity_id", JSONObject.NULL)); assertEquals("200", activity.getJSONArray("activities").getJSONObject(0).getString("amount_minor"))
        val compare = execute(FinancialQuery.COMPARE, JSONObject().put("first_month", "2026-09").put("second_month", "2026-10").put("scope", "DAILY")); assertEquals("50", compare.getString("change_minor"))
        val reimbursement = execute(FinancialQuery.REIMBURSEMENT); assertEquals("-300", reimbursement.getString("unmatched_difference_minor")); assertTrue(FinancialResultFormatter.format(reimbursement.toString()).contains("−¥3.00"))
        val income = execute(FinancialQuery.INCOME, args.put("include_balance", true)); assertEquals("1000", income.getString("ordinary_income_minor")); assertEquals("800", income.getString("reimbursement_received_minor"))
        assertEquals("1200", income.getString("cash_balance_minor"))
        assertFalse(execute(FinancialQuery.INCOME, args.put("include_balance", false)).has("cash_balance_minor"))
        listOf(expense, category, activity, compare, reimbursement, income).forEach { value ->
            assertFalse(value.toString().contains("PRIVATE")); assertFalse(value.has("transactions")); assertFalse(value.has("note"))
        }; assertEquals(original, ledger.snapshot())
    }
    @Test fun invalidDatesUnknownToolsExtraParametersAndSqlCannotReachMutation() = runBlocking {
        seed(); val original = ledger.snapshot()
        listOf(args.put("start", "2026-02-30"), args.put("start", "2025-01-01"), args.put("start", "2026-11-01"), args.put("sql", "DELETE FROM transactions")).forEach {
            assertTrue(execute(FinancialQuery.EXPENSE, it).has("error"))
        }
        assertTrue(JSONObject(tools.execute("delete_transactions", "{}")).has("error")); assertEquals(original, ledger.snapshot())
    }
    @Test fun integerPrecisionOverflowAndLeapDatesAreHandledExplicitly() = runBlocking {
        add(TransactionType.EXPENSE, Long.MAX_VALUE, 1, day = LocalDate.of(2024, 2, 29))
        val leap = JSONObject().put("start", "2024-02-29").put("end", "2024-02-29")
        assertEquals(Long.MAX_VALUE.toString(), execute(FinancialQuery.EXPENSE, leap).getString("daily_expense_minor"))
        add(TransactionType.EXPENSE, 1, 1, day = LocalDate.of(2024, 2, 29)); assertTrue(execute(FinancialQuery.EXPENSE, leap).getString("error").contains("Long"))
    }
    private fun chatCall(name: String, arguments: JSONObject = args, id: String = "call-1") = JSONObject().put("choices", JSONArray().put(JSONObject().put("finish_reason", "tool_calls")
        .put("message", JSONObject().put("role", "assistant").put("content", JSONObject.NULL).put("tool_calls", JSONArray().put(
            JSONObject().put("id", id).put("type", "function").put("function", JSONObject().put("name", name).put("arguments", arguments.toString())))))))
    private fun chatAnswer() = JSONObject().put("choices", JSONArray().put(JSONObject().put("finish_reason", "stop")
        .put("message", JSONObject().put("role", "assistant").put("content", "本月日常支出 ¥1.00，依据查询结果。"))))
    @Test fun chatCompletionsToolRoundTripSendsOnlyRequestedAggregateWithEvidence() = runBlocking {
        seed(); configure(); val original = ledger.snapshot(); val requests = mutableListOf<JSONObject>()
        val service = AssistantService(settings, tools, HttpTransport { method, url, token, body, _, _ ->
            assertEquals("POST", method); assertTrue(url.endsWith("/v1/chat/completions")); assertEquals("test-key-not-for-real-service", token)
            val value = JSONObject(requireNotNull(body).toString(Charsets.UTF_8)); requests += value
            assertFalse(value.getBoolean("store")); assertFalse(value.toString().contains("PRIVATE"))
            (if (requests.size == 1) chatCall("get_expense_summary") else chatAnswer()).toString().toByteArray()
        }, { date })
        val reply = service.ask("本月支出？"); assertEquals(1, reply.evidence.size); assertEquals(2, requests.size)
        val messages = requests.last().getJSONArray("messages"); val output = messages.getJSONObject(messages.length() - 1)
        assertEquals("tool", output.getString("role")); assertEquals("call-1", output.getString("tool_call_id"))
        assertEquals("100", JSONObject(output.getString("content")).getString("daily_expense_minor")); assertEquals(original, ledger.snapshot())
        assertEquals(6, requests.first().getJSONArray("tools").length())
    }
    @Test fun responsesToolRoundTripPreservesOutputItemsAndUsesNoServerConversationState() = runBlocking {
        seed(); configure(AssistantProtocol.RESPONSES); val requests = mutableListOf<JSONObject>()
        val service = AssistantService(settings, tools, HttpTransport { _, url, _, body, _, _ ->
            assertTrue(url.endsWith("/responses")); requests += JSONObject(requireNotNull(body).toString(Charsets.UTF_8))
            val output = if (requests.size == 1) JSONArray().put(JSONObject().put("type", "reasoning").put("id", "reason-1").put("summary", JSONArray()))
                .put(JSONObject().put("type", "function_call").put("call_id", "r-call-1").put("name", "get_expense_summary").put("arguments", args.toString()))
            else JSONArray().put(JSONObject().put("type", "message").put("role", "assistant").put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", "日常支出 ¥1.00"))))
            JSONObject().put("status", "completed").put("output", output).toString().toByteArray()
        }, { date })
        assertEquals(1, service.ask("本月日常支出").evidence.size)
        val input = requests.last().getJSONArray("input"); assertEquals("function_call_output", input.getJSONObject(input.length() - 1).getString("type"))
        assertEquals("r-call-1", input.getJSONObject(input.length() - 1).getString("call_id")); assertFalse(requests.last().has("previous_response_id"))
        assertEquals("function", requests.first().getJSONArray("tools").getJSONObject(0).getString("type"))
    }
    @Test fun missingConfigurationOrPermissionNeverContactsService() = runBlocking {
        var calls = 0; val service = AssistantService(settings, tools, HttpTransport { _, _, _, _, _, _ -> calls++; error("must not connect") })
        assertThrows(Exception::class.java) { runBlocking { service.ask("本月支出") } }; configure(); settings.allowAggregates(false)
        assertThrows(Exception::class.java) { runBlocking { service.ask("本月支出") } }; assertEquals(0, calls)
    }
    @Test fun revokingPermissionBetweenToolRoundsPreventsAggregateUpload() = runBlocking {
        seed(); configure(); var calls = 0
        val service = AssistantService(settings, tools, HttpTransport { _, _, _, _, _, _ -> calls++; chatCall("get_expense_summary").toString().toByteArray() })
        assertThrows(Exception::class.java) { runBlocking { service.ask("本月支出") { runBlocking { settings.allowAggregates(false) } } } }
        assertEquals(1, calls)
    }
    @Test fun maliciousMutationToolReturnsOnlyErrorAndNeverChangesDatabase() = runBlocking {
        seed(); configure(); val original = ledger.snapshot(); var calls = 0
        val service = AssistantService(settings, tools, HttpTransport { _, _, _, body, _, _ ->
            calls++; if (calls == 2) assertTrue(requireNotNull(body).toString(Charsets.UTF_8).contains("error"))
            (if (calls == 1) chatCall("execute_sql", JSONObject().put("sql", "DELETE FROM transactions")) else chatAnswer()).toString().toByteArray()
        })
        assertTrue(JSONObject(service.ask("删除账目").evidence.single().result).has("error")); assertEquals(original, ledger.snapshot())
    }
    @Test fun repeatedToolCallIdsAndEndlessToolsFailWithinBoundedRequests() = runBlocking {
        seed(); configure(); var calls = 0
        val service = AssistantService(settings, tools, HttpTransport { _, _, _, _, _, _ -> calls++; chatCall("get_expense_summary").toString().toByteArray() })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { service.ask("继续查") } }; assertEquals(2, calls)
        calls = 0
        val looping = AssistantService(settings, tools, HttpTransport { _, _, _, _, _, _ -> calls++; chatCall("get_expense_summary", id = "call-$calls").toString().toByteArray() })
        assertThrows(ServiceException::class.java) { runBlocking { looping.ask("继续查") } }; assertEquals(5, calls)
    }
    @Test fun cancellationDoesNotSendToolOutputsAndServiceFailureDoesNotChangeLedger() = runBlocking {
        seed(); configure(); val original = ledger.snapshot(); val arrived = CompletableDeferred<Unit>(); var calls = 0
        val service = AssistantService(settings, tools, HttpTransport { _, _, _, _, _, _ -> calls++; arrived.complete(Unit); awaitCancellation() })
        val task = launch { service.ask("本月支出") }; arrived.await(); task.cancelAndJoin(); assertEquals(1, calls); assertEquals(original, ledger.snapshot())
    }
    private suspend fun awaitState(condition: () -> Boolean) = withTimeout(15_000) {
        while (!condition()) { shadowOf(Looper.getMainLooper()).idle(); delay(10) }
    }
    private fun viewModel(store: ViewModelStore, service: AssistantService) = ViewModelProvider.create(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AssistantViewModel(settings, tools, service) as T
    })[AssistantViewModel::class.java]
    @Test fun viewModelCanRetryFailedRequestAndClearConversationWithoutChangingLedger() = runBlocking {
        seed(); configure(); val original = ledger.snapshot(); var count = 0; val store = ViewModelStore()
        val service = AssistantService(settings, tools, HttpTransport { _, _, _, _, _, _ ->
            count++; if (count == 1) throw ServiceException("认证失败") else chatAnswer().toString().toByteArray() })
        try {
            val vm = viewModel(store, service); awaitState { vm.state.value.ready && vm.state.value.allowed }
            vm.ask("本月支出"); awaitState { !vm.state.value.busy && vm.state.value.message != null }; assertEquals("认证失败", vm.state.value.message)
            vm.retry(); awaitState { vm.state.value.turns.size == 1 }; assertEquals(2, count)
            vm.clear(); assertTrue(vm.state.value.turns.isEmpty()); assertNull(vm.state.value.lastQuestion); assertEquals(original, ledger.snapshot())
        } finally { store.clear(); shadowOf(Looper.getMainLooper()).idle() }
    }
    @Test fun cancellingThenStartingNewQuestionCannotReceiveOldResultOrResetBusyFlag() = runBlocking {
        configure(); val store = ViewModelStore(); val first = CompletableDeferred<Unit>(); val second = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>(); var count = 0
        val service = AssistantService(settings, tools, HttpTransport { _, _, _, _, _, _ ->
            count++; if (count == 1) { first.complete(Unit); awaitCancellation() }
            else { second.complete(Unit); finish.await(); chatAnswer().toString().toByteArray() } })
        try {
            val vm = viewModel(store, service); awaitState { vm.state.value.ready && vm.state.value.allowed }
            vm.ask("旧问题"); awaitState { first.isCompleted }; vm.cancel(); vm.ask("新问题"); awaitState { second.isCompleted }
            assertTrue(vm.state.value.busy); finish.complete(Unit); awaitState { vm.state.value.turns.size == 1 }
            assertEquals("新问题", vm.state.value.turns.single().question); assertFalse(vm.state.value.busy)
        } finally { store.clear(); shadowOf(Looper.getMainLooper()).idle() }
    }
}
