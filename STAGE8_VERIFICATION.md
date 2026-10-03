# Stage 8 — 首页真实数据

2026-10-03。用户确认 Stage 7 手机验证通过，授权继续，并沿用完成后自动发布代码和 APK 的约定。
本阶段完成首页摘要，等待手机验证，不进入 Stage 9。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| PRODUCT_SPEC.md | 记录用户确认的本月结余公式 |
| app/src/main/java/com/example/myledger/analysis/FinancialAnalysis.kt | 统一月份汇总、结余和活动支出；精确计算和溢出处理 |
| app/src/main/java/com/example/myledger/analysis/model/MonthSummary.kt | 月份、记录数量、月度摘要、结余和活动支出模型 |
| app/src/main/java/com/example/myledger/ui/home/HomeUiState.kt | 首页加载、错误、摘要、活动名称和最近记录状态 |
| app/src/main/java/com/example/myledger/ui/home/HomeViewModel.kt | Repository Flow → StateFlow；自动刷新与当前月份更新 |
| app/src/main/java/com/example/myledger/ui/home/HomeScreen.kt | 当前月、五项金额、活动支出、最近记录、空状态及快捷入口 |
| app/src/main/java/com/example/myledger/ui/navigation/MyLedgerApp.kt | 首页进入账目编辑、活动管理和明细的导航 |
| app/src/main/java/com/example/myledger/util/MoneyInput.kt | 支持负数及 Long 极值的货币格式化 |
| app/src/main/java/com/example/myledger/util/LedgerDates.kt | 月份展示格式化 |
| app/src/main/res/values/strings.xml | 首页文案、公式、统计说明、空状态和错误提示 |
| app/build.gradle.kts | versionCode 9 / 0.1.0-stage8；无新增依赖 |
| app/src/test/java/com/example/myledger/analysis/MonthSummaryTest.kt | 8 项财务口径、月份边界和溢出测试 |
| app/src/test/java/com/example/myledger/ui/home/HomeViewModelTest.kt | 7 项真实 Room / ViewModel 刷新、排序、月份更新测试 |
| app/src/test/java/com/example/myledger/ui/home/HomeAppTest.kt | 3 项真实 MainActivity 导航、保存、编辑、改名、删除与刷新测试 |
| app/src/test/java/com/example/myledger/ui/home/HomeScreenTest.kt | 3 项空状态、快捷入口、深色大字号、极值、溢出和可编辑性测试 |
| app/src/test/java/com/example/myledger/util/MoneyAndDatesTest.kt | 新增 2 项带符号货币和月份展示测试 |
| app/src/androidTest/java/com/example/myledger/NavigationTest.kt | 首页占位断言改为当前月份断言 |
| README.md、本文件 | 当前阶段、验证结果和手机步骤 |

## 实现与决策

- 用户确认：本月结余 = 普通收入 + 报销到账 − 全部支出。这是本月净收支，允许负数，不是账户余额。
  普通收入不包含报销；日常支出排除关联活动或可报销的支出，同时具备两者的记录只排除一次。
- 所有金额汇总调用统一 FinancialAnalysis，并复用原有 ExpenseAnalyzer / IncomeAnalyzer。
  Composable 只展示结果，不计算财务数据；金额仍使用 Long 分，没有 Float / Double 金额。
- 本月依据设备当前本地自然月和账目的业务日期，包含月初 / 月末，不使用保存时间。
  首页重新进入前台立即检查月份，前台每分钟再检查一次；跨月或切换时区后重新计算。
  快速月份更新会取消旧订阅并避免旧结果覆盖新月份。
- 活动摘要只计本月 EXPENSE，包含可报销活动支出，按金额倒序、同额按 ID 排序；显示活动名称、类型和笔数。
  只关联收入 / 报销而没有本月支出的活动不会产生支出摘要。
- 最近记录取全账本按业务日期倒序、同日按 ID 倒序的最近 5 条；不是仅本月或按保存时间排序。
  显示分类、带符号金额、日期、类型、最多两行备注、活动与可报销标记；点击复用现有同 ID 编辑 / 确认删除。
- 首页单笔 / 多笔保存、最近记录编辑 / 删除和活动改名由 Room Flow 自动更新，不手动维护另一套金额缓存。
  “查看明细”沿用原有明细筛选和导航状态；“管理活动”返回首页。旋转 / 重开从 Room 重新计算。
- 结余先以普通收入减支出，再加报销，避免中间加法溢出而最终结果仍可表示的误报。
  真正的结余溢出只隐藏结余；月度汇总溢出显示“—”和明确提示，活动组分别计算，最近记录继续可编辑。
  不显示错误的 0 或截断金额，不修改原始账目。
- 首页使用数字、分隔线和简单列表。长金额超过行宽一半时改为标签 / 金额上下排列，避免大字号将活动名称挤成窄列。
  原生触摸测试发现短屏空账本右侧活动按钮被记账 FAB 覆盖，故将两处快捷入口放到左侧；保留真实触摸回归。
- 数据库 schema v1、实体字段和原有统计口径保持不变；不需要 migration，没有新增依赖、权限或 Stage 9 统计页面。

## Windows 验证

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，最终 9 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，48 秒；113 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL，37 秒；lint 0 错误、24 条原有警告，设备测试 APK 编译通过 |

新增 23 项测试，Stage 7 的 90 项测试全部保留并通过。
分析测试覆盖不同收支 / 报销 / 活动组合、负结余、空月、闰年二月、跨年月份两端、保存时间与消费日期分离、
中间加法误溢出、真实结余 / 月度 / 活动组溢出、同额排序。
真实 Room / ViewModel 测试覆盖最近 5 条的业务日期排序和分类 / 活动名称、增改删刷新、账目移出 / 移入本月、
改为报销类型、活动改名 / 类型更新、跨月 / 跨年及快速月份切换、重建和溢出下保留账目。
真实 MainActivity 测试覆盖空页导航、短屏单笔 / 多笔保存返回首页、旋转后数据保留、最近账目编辑 / 确认删除、
活动改名及普通收入 / 报销分离；使用触摸点击，不绕过被遮挡按钮。

已查看原生 View.draw(Canvas) 截图：app/build/stage8-ui/home-empty-light.png、home-populated-light-summary.png、
home-dark-large-summary.png、home-dark-large-recent.png、home-overflow-light.png。
浅色真实 MainActivity 为 411×600dp，独立首页为 411×891dp；深色大字号为 1.5 倍字体。
截图捕获 Activity 主窗口，弹窗通过真实 Compose 交互验证。原生 SQLite / 图形没有 mock 或跳过；测试数据按专属标记清理。
设备 androidTest 仅编译，未通过 ADB 执行；实体手机的系统配色、键盘和触摸仍须下方步骤验收。

日志：build/stage8-verification/assembleDebug.log、test.log、checks.log。
报告：app/build/reports/tests/testDebugUnitTest/index.html、app/build/reports/lint-results-debug.html。

APK：31,403,817 字节；0.1.0-stage8 / versionCode 9，Android API 36 及以上。
SHA-256：73d7ed0d0531125dda9e986aa749f13a5f768bfe5a0b2a05baf4feffc119a293。
apksigner 验证通过，签名与旧版本一致，可覆盖安装保留数据；没有 INTERNET 权限。

## 手机验证

从 [Stage 8 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage8)
下载 MyLedger-stage8-debug.apk，直接覆盖安装保留原账目。

1. 首页当前月份正确。本月结余按已确认公式，普通收入和报销到账分开，负数正常显示。
2. 用清楚的测试备注新增本月账目：普通收入 1000、报销到账 50、无活动且不可报销支出 12.34、
   个人活动支出 20、无活动可报销支出 30、公务活动可报销支出 40 元。
   在原有数值上，普通收入应增加 1000、报销 50、日常支出 12.34、全部支出 102.34、结余 947.66 元。
   个人 / 公务活动支出摘要分别增加 20 / 40 元；有活动且可报销的支出不重复扣除。
3. 从首页最近记录编辑 12.34 元测试记录为 18.01，保存返回首页后金额立即更新；
   改日期到上月后，本月支出移除该金额。最近记录按业务日期排序，全账本最近 5 条，不限当前月。
4. “管理活动”改名返回首页，摘要和最近记录名称更新；“查看明细”进入明细并保留之前的日期筛选。
   短屏下点击快捷入口不应打开记账菜单。
5. 首页记一笔 / 记多笔保存返回首页且刷新；确认删除测试账目后摘要更新，其他记录保留。
   清理前核对测试备注，不删除原有账目。
6. 检查空月份、只有往月数据、长备注、负结余、大字号、深浅色、旋转、重开和断网；
   不需要清除原账本来制造空状态。跨月后重新进入首页应自动换到新月。

本阶段完成后停下，等待手机反馈，不自动进入 Stage 9。
