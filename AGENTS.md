# AGENTS.md

# Codex Development Rules

本文件用于约束 Codex 在本项目中的开发行为。

## 1. 项目目标

这是一个轻量、单用户、本地优先的 Android 个人记账 App。

请优先：

- 简单；
- 可维护；
- 可测试；
- 低依赖；
- 清晰的数据模型；
- 稳定的用户体验。

不要主动扩大产品范围。

---

## 2. 技术栈

必须使用：

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- DataStore
- Kotlin Coroutines
- Flow / StateFlow
- ViewModel
- Repository pattern
- Gradle Kotlin DSL

除非明确要求，否则不要增加：

- Hilt
- Koin
- RxJava
- Flutter
- React Native
- WebView UI
- 多模块架构
- Clean Architecture 模板
- 网络后端 SDK
- LLM SDK

---

## 3. 架构

保持以下主路径：

```text
Compose UI
    ↓
ViewModel
    ↓
Repository
    ↓
Room
```

统计和财务口径放在独立的轻量 analysis 层：

```text
Repository
    ↓
FinancialAnalysis
```

首页、统计页、未来 AI 助手必须共享同一套统计口径。

禁止在 Composable 中实现业务统计逻辑。

---

## 4. 推荐目录

```text
app/src/main/java/<package>/
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   └── entity/
│   └── repository/
├── analysis/
│   ├── ExpenseAnalyzer.kt
│   ├── IncomeAnalyzer.kt
│   └── model/
├── ui/
│   ├── home/
│   ├── records/
│   ├── transaction/
│   ├── batchentry/
│   ├── statistics/
│   ├── settings/
│   ├── navigation/
│   └── theme/
├── backup/
└── util/
```

v0.1 不创建 assistant 包，除非只是定义非常薄的可扩展接口且确实必要。

---

## 5. 金额规则

所有金额必须使用 Long 保存最小货币单位。

人民币示例：

```text
¥12.34 -> 1234
```

禁止在数据库、Repository 或业务逻辑中使用 Float / Double 表示金额。

格式化只允许发生在 UI 或专用 formatter 中。

---

## 6. 数据模型约束

核心账目模型至少支持：

```text
TransactionEntity
- id
- type
- amountMinor
- categoryId
- activityId?
- reimbursable
- note?
- date / timestamp
- createdAt
- updatedAt
```

TransactionType 至少区分：

- EXPENSE
- INCOME
- REIMBURSEMENT

ActivityEntity 至少支持：

- id
- name
- type
- startDate?
- endDate?
- note?

ActivityType：

- PERSONAL
- WORK

不要添加多设备同步字段：

- syncState
- remoteId
- tombstone
- conflictVersion

除非未来明确进入同步功能开发。

---

## 7. 财务统计规则

必须由统一分析层实现：

### 普通收入

不包含 REIMBURSEMENT。

### 全部支出

所有 EXPENSE。

### 日常支出

默认排除：

- 活动支出；
- 可报销支出。

如果产品规范后续更新，以 PRODUCT_SPEC.md 为准。

禁止首页和统计页各写一套独立算法。

---

## 8. 记多笔

“记多笔”不得创建独立 BatchEntity。

UI 使用草稿模型，例如：

```text
TransactionDraft
```

点击全部保存后，使用单个数据库事务批量插入。

页面级默认值：

- 日期
- 默认活动
- 默认可报销

应当能被单条草稿覆盖。

---

## 9. UI 原则

- Material 3；
- 简单；
- 功能导向；
- 少动画；
- 少装饰；
- 不使用复杂渐变；
- 不使用营销型卡片堆叠；
- 保证深色模式可用；
- 不牺牲可读性追求视觉效果。

如果产品需求不明确，选择更简单的实现。

---

## 10. State 管理

Composable 不直接访问 DAO。

推荐：

```text
DAO Flow
 ↓
Repository
 ↓
ViewModel
 ↓
StateFlow
 ↓
collectAsStateWithLifecycle()
```

异步数据库操作使用 coroutine。

避免 GlobalScope。

---

## 11. 日期与时间

业务统计主要按本地自然日/月处理。

不要依赖字符串比较日期。

日期转换逻辑集中封装。

避免把“保存时刻”错误地当作“消费日期”。

记多笔时，页面选择的日期应作为每条记录的业务日期。

---

## 12. Room Migration

每次修改数据库 schema 时：

1. 明确提升数据库版本；
2. 提供 migration；
3. 禁止在正式开发中依赖 destructive migration；
4. 为重要 migration 写测试或至少提供可重复验证方式。

---

## 13. 测试

每个阶段完成后至少执行：

```text
./gradlew assembleDebug
./gradlew test
```

Windows 下：

```text
gradlew.bat assembleDebug
gradlew.bat test
```

如果有 lint 配置，再执行：

```text
gradlew.bat lint
```

不得在 build 失败时继续大规模增加功能。

优先修复当前阶段问题。

---

## 14. 修改原则

每次任务：

1. 先阅读 PRODUCT_SPEC.md；
2. 检查现有实现；
3. 尽量小范围修改；
4. 不重写正常工作的模块；
5. 不擅自增加依赖；
6. 不擅自更改数据模型语义；
7. 修改完成后 build/test；
8. 汇报改动文件、关键决策和验证结果。

---

## 15. 禁止行为

不要：

- 为简单功能引入复杂框架；
- 自动加入账户系统；
- 自动加入网络同步；
- 自动加入广告；
- 自动加入分析/追踪 SDK；
- 自动加入登录；
- 自动加入 AI；
- 把财务数据上传到第三方服务；
- 在未经确认的情况下改变统计口径；
- 使用 Float/Double 存金额；
- 为“记多笔”设计新的数据库批次实体；
- 直接让未来 LLM 执行数据库修改。

---

## 16. AI 扩展原则

未来 AI 助手应通过工具接口读取经过结构化计算的账本数据。

推荐：

```text
AssistantService
 ↓
FinancialTools
 ↓
FinancialAnalysis / Repository
 ↓
Room
```

不要设计：

```text
LLM -> raw SQL
```

AI 如果建议添加、修改或删除财务记录，必须先生成待确认操作，再由用户确认。

---

## 17. 完成标准

一个任务只有同时满足以下条件才算完成：

- 功能符合 PRODUCT_SPEC.md；
- UI 可正常交互；
- 无明显 crash；
- assembleDebug 通过；
- 单元测试通过；
- 没有为了当前任务引入不必要的复杂度。

## Modern Android API Policy

This is a new Android project with no legacy compatibility burden.

Prefer current stable Android and Jetpack APIs recommended by the
official Android documentation.

When multiple implementations are possible:

1. Prefer the modern stable Android / Jetpack approach.
2. Avoid deprecated or legacy APIs unless required for minSdk compatibility.
3. Do not use Alpha, Beta, Experimental, or @Experimental APIs by default.
4. Experimental APIs may only be introduced when they provide a clear
   benefit to a core feature and no reasonable stable alternative exists.
5. Do not introduce compatibility abstractions merely to support obsolete
   Android development patterns.
6. Respect the project's minSdk while taking advantage of newer APIs through
   AndroidX or version-gated platform APIs where appropriate.

For example, prefer modern approaches such as:
- Jetpack Compose and Material 3
- edge-to-edge layouts
- lifecycle-aware StateFlow collection
- modern Navigation Compose APIs
- predictive-back-compatible navigation
- Activity Result APIs
- DataStore instead of SharedPreferences
- Room + Flow
- WorkManager for persistent background work
- adaptive layouts rather than fixed screen assumptions

Do not use a newer API merely because it is newer.
Prefer APIs that reduce complexity, improve UX, security, correctness,
or maintainability.
