# Stage 3 — 统一 FinancialAnalysis

2026-10-03。用户授权 Stage 3 验证通过后直接进入 Stage 4。

## 实现与文件

- analysis/model/ExpenseSummary.kt、IncomeSummary.kt、CategoryBreakdown.kt、PeriodSummary.kt：整数金额结果模型和 DAILY / ALL 口径。
- analysis/ExpenseAnalyzer.kt：全部、日常、活动、可报销支出，以及按同一口径过滤的分类汇总与排行。
- analysis/IncomeAnalyzer.kt：普通收入与报销到账分别统计。
- analysis/FinancialAnalysis.kt：统一的日期范围计算、Repository 查询和 Flow 汇总入口。
- app/src/test/java/com/example/myledger/analysis/FinancialAnalysisTest.kt：12 项统计测试。
- app/build.gradle.kts：版本 0.1.0-stage3 / versionCode 3；README.md 与本验收记录。

以上 analysis 路径位于 app/src/main/java/com/example/myledger/。
没有修改数据库 schema、UI 或新增依赖。

## 口径

普通收入只包括 INCOME，REIMBURSEMENT 单独计入 reimbursementMinor。
全部支出包括所有 EXPENSE；日常支出只包括 activityId 为空且 reimbursable 为 false 的 EXPENSE。
一笔同时关联活动且可报销的支出只排除一次。活动与可报销两个独立小计允许重叠，不能用二者直接相加来反推日常支出。
DAILY / ALL 分类汇总与总额使用相同筛选条件；排行按金额降序、分类 ID 升序。
日期范围包含起止两天，基于业务日期，不使用创建时间。
全部金额使用 Long 和 Math.addExact，溢出明确失败，避免产生错误负金额。
本阶段不定义尚未确认的“结余”公式，不实现首页或统计页面。

## Windows 验证

沿用 Stage 2 的 Windows JBR / SDK、项目内缓存、代理与原生 SQLite 测试资源。

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，14 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，24 秒；27 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint | BUILD SUCCESSFUL，14 秒；0 错误 |

12 项新增测试覆盖五项规定口径、活动与可报销重叠、两种分类汇总、跨月日期边界、空数据、大金额、溢出和非法日期范围。
原 14 项数据库测试和 1 项模板测试也通过。
日志保存在 build/stage3-verification。

修正了 Stage 2 的本机测试资源加载脚本：用惰性的 classpath.filter 保留 Gradle 任务依赖。
原脚本过早枚举 classpath 文件，导致新增普通 JVM 测试读取了未更新的应用 runtime JAR。
修正后 bundleDebugClassesToRuntimeJar 正常执行，所有测试通过；脚本仍位于忽略的本机缓存中，不进入 APK。

Stage 3 主要由自动测试验收，界面无变化。用户已授权无需等待本阶段单独手机检查，继续 Stage 4。
