package com.example.myledger.data.local

import com.example.myledger.data.local.entity.CategoryEntity
import com.example.myledger.data.local.entity.TransactionType

object DefaultCategories {
    // IDs are persistent keys. Never derive them from list position or translated names.
    val all: List<CategoryEntity> = listOf(
        CategoryEntity(1, "餐饮", TransactionType.EXPENSE, 0),
        CategoryEntity(2, "交通", TransactionType.EXPENSE, 1),
        CategoryEntity(3, "住宿", TransactionType.EXPENSE, 2),
        CategoryEntity(4, "日用", TransactionType.EXPENSE, 3),
        CategoryEntity(5, "购物", TransactionType.EXPENSE, 4),
        CategoryEntity(6, "娱乐", TransactionType.EXPENSE, 5),
        CategoryEntity(7, "学习科研", TransactionType.EXPENSE, 6),
        CategoryEntity(8, "医疗健康", TransactionType.EXPENSE, 7),
        CategoryEntity(9, "通讯订阅", TransactionType.EXPENSE, 8),
        CategoryEntity(10, "人情社交", TransactionType.EXPENSE, 9),
        CategoryEntity(11, "其他", TransactionType.EXPENSE, 10),
        CategoryEntity(12, "固定工资", TransactionType.INCOME, 0),
        CategoryEntity(13, "项目酬金", TransactionType.INCOME, 1),
        CategoryEntity(14, "实验室事务", TransactionType.INCOME, 2),
        CategoryEntity(15, "奖学金/奖励", TransactionType.INCOME, 3),
        CategoryEntity(16, "报销", TransactionType.REIMBURSEMENT, 0),
        CategoryEntity(17, "其他收入", TransactionType.INCOME, 4),
    )
}
