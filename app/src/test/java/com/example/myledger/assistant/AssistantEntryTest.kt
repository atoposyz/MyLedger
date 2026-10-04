package com.example.myledger.assistant

import android.app.Application
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.settings.*
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.network.HttpTransport
import com.example.myledger.ui.batchentry.BatchEntryViewModel
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AssistantEntryTest {
    private lateinit var settings: IntegrationSettingsRepository
    private val job = SupervisorJob()
    private val today = LocalDate.of(2026, 10, 4)
    @Before fun setup() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        settings = IntegrationSettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
            File(RuntimeEnvironment.getApplication().cacheDir, "entry-${UUID.randomUUID()}.preferences_pb") }, CredentialCipher { key })
    }
    @After fun close() = runBlocking { job.cancelAndJoin() }
    private fun row(amount: String = "3200") = JSONObject().put("type", "EXPENSE").put("amount_minor", amount).put("category_id", 1)
        .put("date", "2026-10-04").put("reimbursable", false).put("note", "晚饭")
    private fun args(row: JSONObject = row()) = JSONObject().put("drafts", JSONArray().put(row))
    private fun chat(arguments: JSONObject = args(), name: String = "propose_transactions") = JSONObject().put("choices", JSONArray().put(JSONObject()
        .put("finish_reason", "tool_calls").put("message", JSONObject().put("tool_calls", JSONArray().put(JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", name).put("arguments", arguments.toString())))))))
    private suspend fun configure(protocol: AssistantProtocol = AssistantProtocol.CHAT_COMPLETIONS) {
        settings.saveAssistant("https://api.example.com/v1", "fixture-model", protocol, "fixture-key"); settings.allowEntryText(true)
    }
    @Test fun chatOnlySendsDescriptionDateAndStaticCatalogWithoutReadPermission() = runBlocking {
        configure(); assertFalse(settings.current().allowAggregates)
        val service = AssistantEntryService(settings, HttpTransport { method, url, key, body, _, _ ->
            assertEquals("POST", method); assertTrue(url.endsWith("/chat/completions")); assertEquals("fixture-key", key)
            val request = JSONObject(requireNotNull(body).toString(Charsets.UTF_8))
            assertFalse(request.getBoolean("store")); assertFalse(request.toString().contains("get_expense_summary"))
            assertEquals("今晚吃饭32块", request.getJSONArray("messages").getJSONObject(1).getString("content"))
            assertTrue(request.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getBoolean("strict"))
            chat().toString().toByteArray()
        }, { today })
        val result = service.propose("今晚吃饭32块", today)
        assertEquals("32.00", result.drafts.single().amount); assertEquals(1L, result.drafts.single().categoryId)
        assertNull(result.drafts.single().activityId)
    }
    @Test fun responsesSupportsMultipleDraftsAndKeepsLongPrecision() = runBlocking {
        configure(AssistantProtocol.RESPONSES)
        val arguments = args(row("9007199254740993")); arguments.getJSONArray("drafts").put(row("1850"))
        val service = AssistantEntryService(settings, HttpTransport { _, url, _, body, _, _ ->
            assertTrue(url.endsWith("/responses")); val request = JSONObject(requireNotNull(body).toString(Charsets.UTF_8))
            assertEquals("function", request.getJSONArray("tools").getJSONObject(0).getString("type"))
            JSONObject().put("status", "completed").put("output", JSONArray().put(JSONObject().put("type", "function_call")
                .put("name", "propose_transactions").put("arguments", arguments.toString()))).toString().toByteArray()
        }, { today })
        val result = service.propose("两笔", today)
        assertEquals(2, result.drafts.size); assertEquals("90071992547409.93", result.drafts.first().amount)
    }
    @Test fun permissionIsIndependentDefaultsOffAndClearRemovesBothPermissions() = runBlocking {
        assertFalse(settings.current().allowEntryText)
        settings.saveAssistant("https://api.example.com/v1", "fixture", AssistantProtocol.CHAT_COMPLETIONS, "fixture")
        settings.allowAggregates(true)
        var requests = 0
        val service = AssistantEntryService(settings, HttpTransport { _, _, _, _, _, _ -> requests++; chat().toString().toByteArray() })
        try { service.propose("晚饭32", today); fail("must require entry consent") } catch (_: IllegalArgumentException) { }
        assertEquals(0, requests); settings.allowEntryText(true); settings.clearAssistant()
        assertFalse(settings.current().allowEntryText); assertFalse(settings.current().allowAggregates)
    }
    @Test fun withdrawalDuringRequestRejectsGeneratedDrafts() = runBlocking {
        configure()
        val service = AssistantEntryService(settings, HttpTransport { _, _, _, _, _, _ ->
            settings.allowEntryText(false); chat().toString().toByteArray()
        })
        try { service.propose("晚饭32", today); fail("withdrawn response must be rejected") } catch (_: IllegalArgumentException) { }
    }
    @Test fun clarificationTextProducesNoDrafts() = runBlocking {
        configure()
        val service = AssistantEntryService(settings, HttpTransport { _, _, _, _, _, _ ->
            JSONObject().put("choices", JSONArray().put(JSONObject().put("finish_reason", "stop").put("message", JSONObject().put("content", "晚饭花了多少元？")))).toString().toByteArray()
        })
        val result = service.propose("晚饭", today); assertTrue(result.drafts.isEmpty()); assertTrue(result.message.endsWith("晚饭花了多少元？")); assertTrue(result.message.startsWith("未生成草稿"))
    }
    @Test fun extraOperationsInvalidMoneyDatesCategoriesAndOverflowAreRejectedAsWholeProposal() {
        val bad = listOf(row("0"), row("1.5"), row("1e2"), row("9223372036854775808"), row().put("amount_minor", 3200),
            row().put("category_id", 12), row().put("category_id", 1.5), row().put("date", "2026-02-30"), row().put("sql", "DELETE FROM transactions"),
            row().put("note", "x".repeat(501)), row().put("type", "INCOME").put("category_id", 12).put("reimbursable", true))
        bad.forEach { value -> try { AssistantEntryService.parseProposal(args(value).toString()); fail("invalid draft accepted: $value") } catch (_: Exception) { } }
        val overflow = args(row(Long.MAX_VALUE.toString())).apply { getJSONArray("drafts").put(row("1")) }
        try { AssistantEntryService.parseProposal(overflow.toString()); fail("overflow accepted") } catch (_: ArithmeticException) { }
        try { AssistantEntryService.parseProposal(JSONObject().put("drafts", JSONArray()).toString()); fail("empty accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun unknownMutationToolIsNeverExecuted() = runBlocking {
        configure()
        val service = AssistantEntryService(settings, HttpTransport { _, _, _, _, _, _ -> chat(name = "delete_transactions").toString().toByteArray() })
        try { service.propose("删账", today); fail("unsupported tool accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun cancellationCannotInstallOldResponseOverASecondRequest() {
        runBlocking { configure() }
        val oldResponse = CompletableDeferred<Unit>(); var requests = 0
        val service = AssistantEntryService(settings, HttpTransport { _, _, _, _, _, _ ->
            requests++; if (requests == 1) oldResponse.await()
            chat().toString().toByteArray()
        })
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val vm = androidx.lifecycle.ViewModelProvider.create(store, object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST") return com.example.myledger.ui.assistant.AssistantEntryViewModel(settings, service, SavedStateHandle()) as T
                }
            })[com.example.myledger.ui.assistant.AssistantEntryViewModel::class.java]
            await { vm.state.value.allowed }; vm.generate("第一笔32元"); await { requests == 1 }
            vm.cancel(); assertFalse(vm.state.value.busy); assertNull(vm.state.value.proposal)
            vm.generate("第二笔32元"); await { vm.state.value.proposal != null }
            oldResponse.complete(Unit); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, requests); assertFalse(vm.state.value.busy); assertEquals("32.00", vm.state.value.proposal!!.drafts.single().amount)
        } finally { store.clear(); shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test fun pendingDraftsRestoreAllowEditsAndOnlySaveOnExplicitConfirmationOnce() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).addCallback(AppDatabase.seedCategories).build()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val ledger = LedgerRepository(db)
            fun snapshot() = runBlocking { ledger.snapshot() }
            val handle = SavedStateHandle()
            fun create(key: String): BatchEntryViewModel = androidx.lifecycle.ViewModelProvider.create(store, object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST") return BatchEntryViewModel(ledger, handle, today) as T
                }
            })[key, BatchEntryViewModel::class.java]
            val first = create("first"); await { !first.state.value.isLoading }; first.importProposals(AssistantEntryService.parseProposal(args().toString()).drafts)
            assertTrue(snapshot().transactions.isEmpty())
            first.setAmount(1, "33.25"); first.setDate(1, today.minusDays(1)); first.setCategory(1, 2)
            val restored = create("restored")
            await { !restored.state.value.isLoading }; assertEquals("33.25", restored.state.value.drafts.single().amount)
            assertTrue(snapshot().transactions.isEmpty()); restored.saveAll(); restored.saveAll()
            await { restored.state.value.saved != null }; val saved = snapshot().transactions.single()
            assertEquals(3325L, saved.amountMinor); assertEquals(2L, saved.categoryId); assertEquals(today.minusDays(1), saved.date)
            val receipt = create("receipt"); receipt.saveAll(); assertEquals(1, snapshot().transactions.size)
        } finally { store.clear(); shadowOf(Looper.getMainLooper()).idle(); db.close() }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) { shadowOf(Looper.getMainLooper()).idle(); if (System.nanoTime() > deadline) fail("Timed out"); Thread.sleep(10) }
    }
}
