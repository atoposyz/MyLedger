package com.example.myledger.analysis

import com.example.myledger.analysis.model.IncomeSummary
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType

object IncomeAnalyzer {
    fun summarize(rows: List<TransactionEntity>): IncomeSummary {
        var ordinary = 0L
        var reimbursement = 0L
        rows.forEach {
            when (it.type) {
                TransactionType.INCOME -> ordinary = Math.addExact(ordinary, it.amountMinor)
                TransactionType.REIMBURSEMENT -> reimbursement = Math.addExact(reimbursement, it.amountMinor)
                TransactionType.EXPENSE -> Unit
            }
        }
        return IncomeSummary(ordinary, reimbursement)
    }
}
