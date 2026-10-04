package com.example.myledger.util

import org.json.JSONArray
import org.json.JSONObject

object FinancialResultFormatter {
    private val labels = mapOf("start" to "开始", "end" to "结束", "first_month" to "前一月份", "second_month" to "后一月份", "scope" to "口径",
        "all_expense_minor" to "全部支出", "daily_expense_minor" to "日常支出", "activity_expense_minor" to "活动支出", "reimbursable_minor" to "可报销支出",
        "ordinary_income_minor" to "普通收入", "reimbursement_received_minor" to "报销到账", "unmatched_difference_minor" to "垫付与到账差额（未匹配）",
        "first_minor" to "前一月份支出", "second_minor" to "后一月份支出", "change_minor" to "支出变化", "cash_balance_minor" to "净收支（普通收入＋报销－全部支出）", "error" to "查询提示", "rule" to "口径说明")
    fun format(result: String): String = try {
        val data = JSONObject(result); val lines = mutableListOf<String>()
        data.keys().forEach { key ->
            labels[key]?.let { label ->
                val value = data.getString(key)
                lines += "$label：" + when { key.endsWith("_minor") -> MoneyInput.formatCurrencyMinor(value.toLong())
                    key == "scope" -> if (value == "DAILY") "日常" else "全部"; else -> value }
            }
            if (key == "categories" || key == "activities") {
                val rows = data.getJSONArray(key)
                if (rows.length() == 0) lines += "此范围内无相关记录"
                for (number in 0 until rows.length()) { val row = rows.getJSONObject(number)
                    lines += "${row.getString("name")}：${MoneyInput.formatCurrencyMinor(row.getString("amount_minor").toLong())}（${row.getInt("count")} 笔）" }
            }
        }
        if (data.optBoolean("truncated")) lines += "仅显示前 30 个活动，请缩小范围或指定活动查询"
        lines.joinToString("\n")
    } catch (_: Exception) { "汇总结果无法显示，请重新查询" }
}
