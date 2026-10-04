package com.example.myledger.assistant

import com.example.myledger.analysis.*
import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.repository.LedgerRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class FinancialQuery(val function: String, val label: String) {
    EXPENSE("get_expense_summary", "支出汇总"), CATEGORY("get_category_expenses", "分类支出"),
    ACTIVITY("get_activity_totals", "活动支出"), COMPARE("compare_months", "月份对比"),
    REIMBURSEMENT("get_reimbursement_summary", "报销汇总"), INCOME("get_income_structure", "收入结构")
}
data class ToolEvidence(val name: String, val arguments: String, val result: String)

/** Only these six aggregate tools are reachable. No SQL, transactions, notes, or mutation API. */
class FinancialTools(private val repository: LedgerRepository) {
    fun definitions(responses: Boolean): JSONArray = JSONArray().apply {
        FinancialQuery.entries.forEach { query ->
            val properties = JSONObject()
            fun field(name: String, type: Any, description: String) { properties.put(name, JSONObject().put("type", type).put("description", description)) }
            if (query == FinancialQuery.COMPARE) {
                field("first_month", "string", "First month YYYY-MM"); field("second_month", "string", "Second month YYYY-MM")
            } else { field("start", "string", "Inclusive local date YYYY-MM-DD"); field("end", "string", "Inclusive local date YYYY-MM-DD, at most 366 days") }
            if (query == FinancialQuery.CATEGORY || query == FinancialQuery.COMPARE) properties.put("scope", JSONObject().put("type", "string").put("enum", JSONArray(listOf("DAILY", "ALL"))))
            if (query == FinancialQuery.ACTIVITY) field("activity_id", JSONArray(listOf("integer", "null")), "null returns up to 30 activity totals; integer selects an activity")
            if (query == FinancialQuery.INCOME) field("include_balance", "boolean", "true only when net cash flow/balance is requested; computed locally as ordinary income + reimbursement - all expenses")
            val parameters = JSONObject().put("type", "object").put("properties", properties)
                .put("required", JSONArray(properties.keys().asSequence().toList())).put("additionalProperties", false)
            val function = JSONObject().put("name", query.function).put("description", "Read-only ${query.label}; CNY minor amounts returned as exact integer strings, no raw records")
                .put("parameters", parameters).put("strict", true)
            put(if (responses) function.put("type", "function") else JSONObject().put("type", "function").put("function", function))
        }
    }
    suspend fun execute(name: String, arguments: String): String = withContext(Dispatchers.Default) {
        try {
            require(arguments.length <= 2048)
            val query = requireNotNull(FinancialQuery.entries.firstOrNull { it.function == name })
            val args = JSONObject(arguments)
            val allowed = if (query == FinancialQuery.COMPARE) setOf("first_month", "second_month", "scope") else
                setOf("start", "end") + when(query) { FinancialQuery.CATEGORY -> setOf("scope"); FinancialQuery.ACTIVITY -> setOf("activity_id"); FinancialQuery.INCOME -> setOf("include_balance"); else -> emptySet() }
            require(args.keys().asSequence().toSet() == allowed)
            fun scope() = ExpenseScope.valueOf(args.getString("scope"))
            fun month(key: String) = YearMonth.parse(args.getString(key)).also { require(it.year in 1900..9999) }
            val result = JSONObject().put("currency", "CNY").put("amount_unit", "minor_integer_string")
            if (query == FinancialQuery.COMPARE) {
                val first = month("first_month"); val second = month("second_month"); val expenseScope = scope()
                val snapshot = repository.analysisSnapshot(listOf(first.atDay(1)..first.atEndOfMonth(), second.atDay(1)..second.atEndOfMonth()))
                fun total(month: YearMonth) = ExpenseAnalyzer.total(snapshot.transactions.filter { YearMonth.from(it.date) == month }, expenseScope)
                val a = total(first); val b = total(second)
                result.put("first_month", first.toString()).put("second_month", second.toString()).put("scope", expenseScope.name)
                    .put("first_minor", a.toString()).put("second_minor", b.toString()).put("change_minor", Math.subtractExact(b, a).toString())
            } else {
                val start = LocalDate.parse(args.getString("start")); val end = LocalDate.parse(args.getString("end"))
                require(start.year in 1900..9999 && end.year in 1900..9999 && ChronoUnit.DAYS.between(start, end) in 0..365)
                val snapshot = repository.analysisSnapshot(listOf(start..end))
                val rows = snapshot.transactions
                result.put("start", start.toString()).put("end", end.toString())
                fun amount(key: String, value: Long) { result.put(key, value.toString()) }
                when (query) {
                    FinancialQuery.EXPENSE -> {
                        val summary = ExpenseAnalyzer.summarize(rows)
                        amount("all_expense_minor", summary.totalMinor); amount("daily_expense_minor", summary.dailyMinor)
                        amount("activity_expense_minor", summary.activityMinor); amount("reimbursable_minor", summary.reimbursableMinor)
                        result.put("rule", "日常支出排除活动和可报销支出的并集；活动与可报销小计可能重叠。")
                    }
                    FinancialQuery.CATEGORY -> {
                        val selected = scope(); result.put("scope", selected.name)
                        val categories = snapshot.categories.associateBy { it.id }
                        result.put("categories", JSONArray(ExpenseAnalyzer.categoryBreakdown(rows, selected).map {
                            JSONObject().put("name", categories[it.categoryId]?.name.orEmpty()).put("amount_minor", it.amountMinor.toString()).put("count", it.transactionCount)
                        }))
                    }
                    FinancialQuery.ACTIVITY -> {
                        val selected = if (args.isNull("activity_id")) null else args.get("activity_id").let { value ->
                            require(value is Int || value is Long); (value as Number).toLong().also { require(it > 0) }
                        }
                        val activities = snapshot.activities.associateBy { it.id }
                        if (selected != null) require(selected in activities)
                        val groups = rows.filter { it.type == TransactionType.EXPENSE && it.activityId != null && (selected == null || it.activityId == selected) }
                            .groupBy { requireNotNull(it.activityId) }.entries.sortedBy { it.key }
                        result.put("activities", JSONArray(groups.take(30).map { (id, items) ->
                            JSONObject().put("activity_id", id).put("name", activities[id]?.name.orEmpty().take(80))
                                .put("type", activities[id]?.type?.name).put("amount_minor", ExpenseAnalyzer.total(items, ExpenseScope.ALL).toString()).put("count", items.size)
                        })).put("truncated", groups.size > 30)
                    }
                    FinancialQuery.REIMBURSEMENT -> {
                        val expense = ExpenseAnalyzer.summarize(rows); val income = IncomeAnalyzer.summarize(rows)
                        amount("reimbursable_minor", expense.reimbursableMinor); amount("reimbursement_received_minor", income.reimbursementMinor)
                        amount("unmatched_difference_minor", Math.subtractExact(expense.reimbursableMinor, income.reimbursementMinor))
                        result.put("rule", "未做逐笔报销匹配；差额不是已确认的待报销余额。")
                    }
                    FinancialQuery.INCOME -> {
                        val income = IncomeAnalyzer.summarize(rows); amount("ordinary_income_minor", income.ordinaryMinor)
                        amount("reimbursement_received_minor", income.reimbursementMinor)
                        require(args.get("include_balance") is Boolean)
                        if (args.getBoolean("include_balance")) amount("cash_balance_minor", FinancialAnalysis.cashBalanceMinor(FinancialAnalysis.summarize(rows, start, end)))
                        val categories = snapshot.categories.associateBy { it.id }
                        result.put("categories", JSONArray(IncomeAnalyzer.categoryBreakdown(rows).map {
                            JSONObject().put("name", categories[it.categoryId]?.name.orEmpty()).put("amount_minor", it.amountMinor.toString()).put("count", it.transactionCount)
                        })).put("rule", "普通收入不包含报销到账。")
                    }
                    else -> error("Unsupported query")
                }
            }
            result.toString().also { require(it.length <= 16_384) }
        } catch (error: CancellationException) { throw error }
        catch (_: ArithmeticException) { JSONObject().put("error", "金额合计超出 Long 范围，无法可靠汇总").toString() }
        catch (_: Exception) { JSONObject().put("error", "查询无效或数据读取失败；仅支持六个只读工具，日期区间最多 366 天").toString() }
    }
}
