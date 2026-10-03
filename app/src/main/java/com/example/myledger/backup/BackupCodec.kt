package com.example.myledger.backup

import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.LedgerSnapshot
import com.example.myledger.data.settings.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate
import java.security.MessageDigest
import java.util.zip.*
import org.json.*

data class BackupMetadata(val backupVersion: Int, val appVersion: String, val createdAt: Instant, val transactionCount: Int, val activityCount: Int)
data class BackupPayload(val metadata: BackupMetadata, val ledger: LedgerSnapshot, val settings: AppSettings)
class BackupFormatException(message: String) : IOException(message)

object BackupCodec {
    const val VERSION = 1
    const val MAX_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_CONTENT_BYTES = 50L * 1024 * 1024
    private val dataNames = setOf("transactions.json", "activities.json", "categories.json", "settings.json")
    private val allNames = dataNames + "manifest.json"

    fun write(payload: BackupPayload, output: OutputStream) {
        payload.ledger.validate()
        require(payload.metadata.backupVersion == VERSION && payload.metadata.transactionCount == payload.ledger.transactions.size && payload.metadata.activityCount == payload.ledger.activities.size)
        val data = linkedMapOf(
            "transactions.json" to JSONArray().apply { payload.ledger.transactions.forEach { row -> put(JSONObject().apply {
                put("id", row.id.toString()); put("type", row.type.name); put("amountMinor", row.amountMinor.toString()); put("categoryId", row.categoryId.toString())
                put("activityId", row.activityId?.toString() ?: JSONObject.NULL); put("reimbursable", row.reimbursable); put("note", row.note ?: JSONObject.NULL)
                put("date", row.date.toString()); put("createdAt", row.createdAt.toString()); put("updatedAt", row.updatedAt.toString())
            }) } }.bytes(),
            "activities.json" to JSONArray().apply { payload.ledger.activities.forEach { row -> put(JSONObject().apply {
                put("id", row.id.toString()); put("name", row.name); put("type", row.type.name); put("startDate", row.startDate?.toString() ?: JSONObject.NULL)
                put("endDate", row.endDate?.toString() ?: JSONObject.NULL); put("note", row.note ?: JSONObject.NULL)
            }) } }.bytes(),
            "categories.json" to JSONArray().apply { payload.ledger.categories.forEach { row -> put(JSONObject().apply {
                put("id", row.id.toString()); put("name", row.name); put("type", row.type.name); put("sortOrder", row.sortOrder)
            }) } }.bytes(),
            "settings.json" to JSONObject().apply { put("themeMode", payload.settings.themeMode.name); put("currency", payload.settings.currency) }.bytes(),
        )
        val manifest = JSONObject().apply {
            put("appId", "com.example.myledger"); put("backupVersion", VERSION); put("appVersion", payload.metadata.appVersion); put("createdAt", payload.metadata.createdAt.toString())
            put("transactionCount", payload.metadata.transactionCount); put("activityCount", payload.metadata.activityCount)
            put("checksums", JSONObject().apply { data.forEach { (name, bytes) -> put(name, digest(bytes)) } })
        }.bytes()
        require(data.values.sumOf { it.size.toLong() } + manifest.size <= MAX_CONTENT_BYTES) { "备份过大" }
        // Caller owns the stream (including AtomicFile's finishWrite / failWrite).
        ZipOutputStream(object : FilterOutputStream(output) { override fun close() { flush() } }).use { zip ->
            (linkedMapOf("manifest.json" to manifest) + data).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
    }

    fun read(input: InputStream, maxContentBytes: Long = MAX_CONTENT_BYTES): BackupPayload {
        try {
            val entries = mutableMapOf<String, ByteArray>()
            var count = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in allNames && entry.name !in entries) { "备份文件结构无效" }
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val length = zip.read(buffer); if (length < 0) break
                        count += length; require(count <= maxContentBytes) { "备份解压后过大" }; output.write(buffer, 0, length)
                    }
                    entries[entry.name] = output.toByteArray(); zip.closeEntry()
                }
            }
            require(entries.keys == allNames) { "备份文件不完整" }
            val manifest = parse(entries.getValue("manifest.json")) as? JSONObject ?: error("备份清单无效")
            require(manifest.text("appId") == "com.example.myledger") { "不是 MyLedger 备份" }
            require(manifest.integer("backupVersion") == VERSION) { "备份版本不受支持" }
            val checksums = manifest.getJSONObject("checksums")
            dataNames.forEach { name -> require(checksums.text(name) == digest(entries.getValue(name))) { "备份校验失败" } }
            fun array(name: String): JSONArray = parse(entries.getValue(name)) as? JSONArray ?: error("备份列表无效")
            val transactionArray = array("transactions.json"); val activityArray = array("activities.json"); val categoryArray = array("categories.json")
            require(transactionArray.length() <= 100_000 && activityArray.length() <= 100_000 && categoryArray.length() == 17) { "备份记录数量无效" }
            val transactions = (0 until transactionArray.length()).map { index -> transactionArray.getJSONObject(index).let { row ->
                TransactionEntity(row.long("id"), TransactionType.valueOf(row.text("type")), row.long("amountMinor"), row.long("categoryId"), LocalDate.parse(row.text("date")),
                    row.optionalText("activityId")?.let(::exactLong), row.get("reimbursable") as? Boolean ?: error("报销标记无效"), row.optionalText("note"), row.long("createdAt"), row.long("updatedAt"))
            } }
            val activities = (0 until activityArray.length()).map { index -> activityArray.getJSONObject(index).let { row ->
                ActivityEntity(row.long("id"), row.text("name"), ActivityType.valueOf(row.text("type")), row.optionalText("startDate")?.let(LocalDate::parse),
                    row.optionalText("endDate")?.let(LocalDate::parse), row.optionalText("note"))
            } }
            val categories = (0 until categoryArray.length()).map { index -> categoryArray.getJSONObject(index).let { row ->
                CategoryEntity(row.long("id"), row.text("name"), TransactionType.valueOf(row.text("type")), row.integer("sortOrder"))
            } }
            val settingsJson = parse(entries.getValue("settings.json")) as? JSONObject ?: error("设置无效")
            require(settingsJson.text("currency") == "CNY") { "备份货币不受支持" }
            val settings = AppSettings(ThemeMode.valueOf(settingsJson.text("themeMode")))
            val metadata = BackupMetadata(VERSION, manifest.text("appVersion"), Instant.parse(manifest.text("createdAt")), manifest.integer("transactionCount"), manifest.integer("activityCount"))
            require(metadata.appVersion.isNotBlank() && metadata.transactionCount == transactions.size && metadata.activityCount == activities.size) { "备份清单与内容不一致" }
            return BackupPayload(metadata, LedgerSnapshot(transactions, activities, categories).also { it.validate() }, settings)
        } catch (error: Exception) {
            if (error is BackupFormatException) throw error
            throw BackupFormatException(error.message ?: "备份无效")
        }
    }

    private fun Any.bytes() = toString().toByteArray(Charsets.UTF_8)
    private fun parse(bytes: ByteArray): Any {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val tokener = JSONTokener(text)
        val value = tokener.nextValue(); require(tokener.nextClean() == '\u0000') { "备份 JSON 有多余内容" }; return value
    }
    private fun JSONObject.text(key: String) = get(key) as? String ?: error("字段 $key 必须为文本")
    private fun JSONObject.optionalText(key: String): String? { require(has(key)); return if (isNull(key)) null else text(key) }
    private fun exactLong(value: String): Long { require(value.matches(Regex("-?(0|[1-9][0-9]*)"))); return value.toLong() }
    private fun JSONObject.long(key: String) = exactLong(text(key))
    private fun JSONObject.integer(key: String): Int { val value = get(key); require(value is Number && value.toString().matches(Regex("[0-9]+"))); return value.toString().toInt() }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
