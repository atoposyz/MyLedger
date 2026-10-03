package com.example.myledger.backup

import android.app.Application
import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.LedgerSnapshot
import com.example.myledger.data.settings.*
import java.io.*
import java.security.MessageDigest
import java.time.*
import java.util.zip.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupCodecTest {
    private val ledger = LedgerSnapshot(listOf(
        TransactionEntity(1, TransactionType.EXPENSE, Long.MAX_VALUE, 1, LocalDate.of(2024, 2, 29), 7, true, "Unicode 备注\n".repeat(1000), 100, 90),
        TransactionEntity(2, TransactionType.INCOME, 1234, 12, LocalDate.of(2026, 12, 31), note = null),
        TransactionEntity(3, TransactionType.REIMBURSEMENT, 5678, 16, LocalDate.of(2027, 1, 1), activityId = 7),
    ), listOf(ActivityEntity(7, "科研出差", ActivityType.WORK, LocalDate.of(2024, 2, 29), null, "活动备注")), DefaultCategories.all)
    private val payload = BackupPayload(BackupMetadata(1, "0.1.0-test", Instant.parse("2026-10-03T12:00:00Z"), 3, 1), ledger, AppSettings(ThemeMode.DARK))
    private fun encode(value: BackupPayload = payload): ByteArray = ByteArrayOutputStream().also { BackupCodec.write(value, it) }.toByteArray()
    private fun unpack(bytes: ByteArray = encode()): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes(); zip.closeEntry() } }
        return entries
    }
    private fun pack(entries: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, value) -> zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry() } }
        return bytes.toByteArray()
    }
    private fun modified(name: String, value: String, updateChecksum: Boolean = true): ByteArray {
        val entries = unpack(); entries[name] = value.toByteArray()
        if (name != "manifest.json" && updateChecksum) {
            val manifest = JSONObject(String(entries.getValue("manifest.json")))
            manifest.getJSONObject("checksums").put(name, MessageDigest.getInstance("SHA-256").digest(entries.getValue(name)).joinToString("") { "%02x".format(it) })
            entries["manifest.json"] = manifest.toString().toByteArray()
        }
        return pack(entries)
    }
    private fun rejected(bytes: ByteArray) { assertThrows(BackupFormatException::class.java) { BackupCodec.read(ByteArrayInputStream(bytes)) } }

    @Test fun roundTripPreservesExactLongDatesIdsTypesNullableFieldsSettingsAndUnicode() {
        assertEquals(payload, BackupCodec.read(ByteArrayInputStream(encode())))
        val json = JSONArray(String(unpack().getValue("transactions.json")))
        assertEquals(Long.MAX_VALUE.toString(), json.getJSONObject(0).get("amountMinor"))
    }
    @Test fun emptyLedgerIsAValidReplaceableBackup() {
        val empty = payload.copy(metadata = payload.metadata.copy(transactionCount = 0, activityCount = 0), ledger = LedgerSnapshot(emptyList(), emptyList(), DefaultCategories.all))
        assertEquals(empty, BackupCodec.read(ByteArrayInputStream(encode(empty))))
    }
    @Test fun wrongAppUnknownVersionCountMismatchAndChecksumMismatchAreRejected() {
        for ((key, value) in listOf("appId" to "other.app", "backupVersion" to 2, "transactionCount" to 99)) {
            val manifest = JSONObject(String(unpack().getValue("manifest.json"))).put(key, value)
            rejected(modified("manifest.json", manifest.toString()))
        }
        rejected(modified("settings.json", "{}", updateChecksum = false))
    }
    @Test fun fractionalNumericOverflowNegativeAndMissingMoneyFieldsAreRejectedEvenWithValidChecksums() {
        for (value in listOf<Any>(1.23, 1234, "1.23", "9223372036854775808", "-1", "0", "NaN")) {
            val rows = JSONArray(String(unpack().getValue("transactions.json"))); rows.getJSONObject(0).put("amountMinor", value)
            rejected(modified("transactions.json", rows.toString()))
        }
        val rows = JSONArray(String(unpack().getValue("transactions.json"))); rows.getJSONObject(0).remove("amountMinor")
        rejected(modified("transactions.json", rows.toString()))
    }
    @Test fun invalidReferencesDuplicateIdsWrongTypesBadDatesAndSettingsAreRejected() {
        for ((key, value) in listOf("activityId" to "999", "categoryId" to "12", "type" to "UNKNOWN", "date" to "2024-02-30", "reimbursable" to "true")) {
            val rows = JSONArray(String(unpack().getValue("transactions.json"))); rows.getJSONObject(0).put(key, value)
            rejected(modified("transactions.json", rows.toString()))
        }
        val rows = JSONArray(String(unpack().getValue("transactions.json"))); rows.getJSONObject(1).put("id", "1")
        rejected(modified("transactions.json", rows.toString()))
        for ((key, value) in listOf("currency" to "USD", "themeMode" to "unknown")) {
            val settings = JSONObject(String(unpack().getValue("settings.json"))).put(key, value)
            rejected(modified("settings.json", settings.toString()))
        }
    }
    @Test fun missingUnknownTraversalAndDuplicateZipEntriesAreRejectedWithoutExtraction() {
        rejected(pack(unpack().also { it.remove("activities.json") }))
        rejected(pack(unpack().also { it["../outside.txt"] = "bad".toByteArray() }))
        rejected(pack(unpack().also { it["extra.json"] = "{}".toByteArray() }))
        val bytes = encode(); val from = "settings.json".toByteArray(); val to = "manifest.json".toByteArray()
        for (index in 0..bytes.size - from.size) if (from.indices.all { bytes[index + it] == from[it] }) to.copyInto(bytes, index)
        rejected(bytes)
    }
    @Test fun nonZipAndTruncatedArchivesAreRejected() {
        rejected("not a backup".toByteArray()); rejected(encode().copyOf(80))
    }
    @Test fun contentLimitRejectsOversizedExpandedData() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.read(ByteArrayInputStream(encode()), maxContentBytes = 100) }
    }
    @Test fun codecLeavesCallerOwnedOutputOpenForAtomicFileCommit() {
        var closed = false
        val bytes = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        BackupCodec.write(payload, bytes); assertFalse(closed)
        assertEquals(payload, BackupCodec.read(ByteArrayInputStream(bytes.toByteArray())))
    }
}
