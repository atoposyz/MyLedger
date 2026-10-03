# Stage 9 — 支出统计页

2026-10-03。用户授权继续 Stage 9，沿用完成后自动发布代码和 APK 的约定。
本阶段完成统计页，等待手机验证，不进入 Stage 10。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| app/src/main/java/com/example/myledger/analysis/ExpenseAnalyzer.kt | 共用日常 / 全部筛选，精确计算指定口径总额 |
| app/src/main/java/com/example/myledger/analysis/FinancialAnalysis.kt | 统一当前月总额、分类排行与最近 6 个月趋势；分别处理各月溢出 |
| app/src/main/java/com/example/myledger/analysis/model/StatisticsSummary.kt | 指定月份 / 口径的总额、分类、各月支出及趋势最高值 |
| app/src/main/java/com/example/myledger/ui/statistics/StatisticsUiState.kt | 口径、月份、分类名称、统计结果、加载 / 错误状态 |
| app/src/main/java/com/example/myledger/ui/statistics/StatisticsViewModel.kt | Repository Flow → StateFlow，切换联动、保存口径、增改删刷新与跨月更新 |
| app/src/main/java/com/example/myledger/ui/statistics/StatisticsScreen.kt | 日常 / 全部、总额、分类占比 / 排行、月度趋势、空状态与重试 |
| app/src/main/java/com/example/myledger/util/StatisticsFormatter.kt | 百分比和图形比例格式化，避免 Long 乘法溢出 |
| app/src/main/java/com/example/myledger/ui/navigation/MyLedgerApp.kt | 统计占位页接入真实 Route |
| app/src/main/res/values/strings.xml | 口径、统计说明、空状态和溢出文案 |
| app/build.gradle.kts | versionCode 10 / 0.1.0-stage9；无新增依赖 |
| app/src/test/java/com/example/myledger/analysis/StatisticsSummaryTest.kt | 8 项统一口径、排序、月份边界、空月和溢出测试 |
| app/src/test/java/com/example/myledger/ui/statistics/StatisticsViewModelTest.kt | 6 项真实 Room / ViewModel 保存口径、实时刷新和跨年更新测试 |
| app/src/test/java/com/example/myledger/ui/statistics/StatisticsScreenTest.kt | 3 项真实 Compose 日常 / 全部联动、深浅色、大字号与溢出测试 |
| app/src/test/java/com/example/myledger/ui/statistics/StatisticsAppTest.kt | 2 项真实 MainActivity 导航、旋转、录入、编辑 / 删除后的统计刷新测试 |
| app/src/test/java/com/example/myledger/util/StatisticsFormatterTest.kt | 3 项零分母、四舍五入、极小占比和 Long 最大值测试 |
| app/src/androidTest/java/com/example/myledger/NavigationTest.kt | 统计页占位断言替换为真实月份断言 |
| README.md、本文件 | 当前阶段、实现决策和手机验证步骤 |

## 实现与决策

- 默认“日常”，排除有关联活动或可报销标记的支出；“全部”包括所有 EXPENSE。
  同时有关联活动和可报销标记的支出只排除一次。普通收入和报销到账均不计入支出统计。
  正常金额下，统计页两个总额分别与首页日常支出 / 全部支出一致。
- 所有统计由 FinancialAnalysis 计算，复用 ExpenseAnalyzer 既有日常口径和分类聚合。
  没有在 Composable 中筛选交易、求和、分类排行或计算月度趋势。
- 分类占比和排行合并为一张列表：按金额倒序、同额按稳定分类 ID 排序，显示排名、名称、精确金额、占比、笔数。
  百分比以本月当前口径总额为分母，四舍五入到一位小数；极小正占比显示小于 0.1%，不误报为 0。
  四舍五入后的百分比可能不合计为 100%，页面有说明。
- 趋势为包含本月的最近 6 个本地自然月，按业务日期而非保存时间，按月正序排列。
  月初 / 月末均包含，缺少支出的月份显示 0；显示完整年月和金额，不只显示无标注的图形。
  所有可用月份的横条按同一最高值缩放。切换口径后，总额、分类、占比、排行和全部趋势一起更新。
- 图形采用原生 Compose 布局绘制的单色横条；没有图表依赖、饼图配色、复杂动画或装饰卡片。
  横条不重复朗读，分类行 / 月份行合并语义，金额和百分比可由无障碍服务读取。
- 口径使用 SavedStateHandle 保存，旋转和一级页面切换后保留；没有提前实现 Stage 10 的 DataStore 设置。
  冷启动默认日常；进程恢复可恢复导航保存的口径。月份按当前设备日期重新计算，不恢复过期月份。
  重新进入前台检查月份，前台每分钟再检查一次；快速口径 / 月份切换取消旧订阅，旧结果不覆盖新状态。
- 插入、编辑金额 / 分类 / 日期、修改活动关联或可报销标记、删除由 Room Flow 自动更新。
  在统计页打开单笔 / 多笔录入，保存返回统计页并保留所选口径。
- 金额始终为 Long 分，使用精确加法。指定口径独立计算：全部溢出时，可用的日常仍正常显示；
  收入汇总溢出不会影响支出统计。单个历史月份溢出也不阻止当前月或其他月份展示。
  当前月溢出显示“—”和提示，暂不展示误导性的分类占比 / 排行；其他可用趋势保留，仍可切换口径。
- BigDecimal 只用于专用 formatter 的显示比例，Float 只用于横条宽度；没有用浮点数保存或计算财务金额。
- 数据库 schema v1、实体、首页结余公式和既有统计口径保持不变，没有 migration、新增依赖或网络权限。

## Windows 验证

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，18 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，44 秒；135 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL，36 秒；lint 0 错误、24 条原有警告，设备测试 APK 编译通过 |

新增 22 项测试，原有 113 项全部保留并通过。
分析测试覆盖与首页共同口径、活动 / 可报销重叠排除、收入 / 报销排除、分类汇总和同额排序、
最近 6 月月初 / 月末、跨年、闰年二月、业务日期与保存时间分离、缺月补零、最大金额和各类溢出。
真实 Room / ViewModel 测试覆盖默认 / 恢复 / 无效口径、增改删刷新、分类更新、跨月移入趋势、
活动与可报销变化、快速口径和跨年切换、全部溢出切回日常，以及删除异常账目后恢复。
真实 MainActivity 测试覆盖首页与统计数值一致、所有口径联动、旋转及切换页面保留口径、
明细编辑 / 确认删除后统计刷新、600dp 短屏单笔和多笔触摸保存返回统计页并更新当前月趋势。

已查看原生 View.draw(Canvas) 截图：app/build/stage9-ui/statistics-daily-light.png、statistics-all-dark.png、
statistics-trend-light.png、statistics-app-short-dark.png、statistics-maximum-dark-large.png、statistics-overflow-dark-large.png。
真实 MainActivity 为 411×600dp，独立页面为 411×891dp；大字号场景为 1.5 倍字体。
截图捕获 Activity 主窗口；弹窗通过真实 Compose 交互断言验证。原生 SQLite / 图形没有 mock 或跳过，测试数据按专属标记清理。
androidTest 仅编译，未通过 ADB 执行；实体手机的系统动态颜色、键盘、返回手势和触摸仍需手动验证。

日志：build/stage9-verification/assembleDebug.log、test.log、checks.log。
报告：app/build/reports/tests/testDebugUnitTest/index.html、app/build/reports/lint-results-debug.html。

APK：31,404,458 字节；0.1.0-stage9 / versionCode 10，Android API 36 及以上。
SHA-256：4ff8db9fa1cae81a8d6595a5faed703e1c020ae7e3717c883f14e5e060557185。
apksigner 验证通过，签名与旧版本一致，可覆盖安装保留数据；没有 INTERNET 权限。

## 手机验证

从 [Stage 9 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage9)
下载 MyLedger-stage9-debug.apk，直接覆盖安装，保留原账目。

1. 打开统计：默认日常，本月支出应等于首页日常支出；切换全部应等于首页全部支出。
   增加普通收入或报销到账不应增加统计页支出。
2. 用清楚的测试备注增加本月餐饮 100、交通 300、关联活动的餐饮 200、无活动可报销交通 400 元。
   空账本的日常应为 400：交通 300 / 75%，餐饮 100 / 25%；全部应为 1000：交通 700 / 70%，餐饮 300 / 30%。
   有原有数据时检查增量：日常 +400、全部 +1000；占比结合原账本重新计算，不必清空账本。
3. 上月新增无活动不可报销支出 20、关联活动支出 50。上月趋势日常增加 20、全部增加 70，本月总额不增加。
   趋势包括本月共 6 个自然月，缺月为 0；各月有完整年月 / 金额，横条使用同一最高值缩放。
4. 在明细把测试餐饮 100 改为 150，两种口径的本月总额都增加 50；分类金额、占比、排行和本月趋势同步变化。
   将此笔日期改到上月，本月移除 150、上月趋势增加 150；删除测试账目前核对备注，确认后相应统计更新。
5. 在统计页记一笔 / 记多笔，保存后返回统计页并刷新；切换页面、旋转后所选日常 / 全部保持。
6. 检查空月、只有活动支出、只有收入、大字号、长金额、系统深浅色、重开和断网。
   不需要清除原数据来制造空状态。切换口径应同时更新全部数字和图形；极小正占比显示小于 0.1%。

本阶段完成后停下，等待手机反馈，不自动进入 Stage 10。
