# Stage 6 — 明细页、编辑与删除

2026-10-03。用户确认 Stage 5 手机验证“没问题”，授权进入下一阶段。
本次完成 Stage 6 并发布代码和 APK；等待手机反馈，不进入 Stage 7。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| app/src/main/java/com/example/myledger/ui/records/RecordsScreen.kt | 真实明细 Route、按日分组 LazyColumn、每日摘要、记录展示、空状态、范围筛选弹窗 |
| app/src/main/java/com/example/myledger/ui/records/RecordsUiState.kt | 日期范围、记录行与日期分组；RecordsGrouping 调用统一 FinancialAnalysis |
| app/src/main/java/com/example/myledger/ui/records/RecordsViewModel.kt | 合并 Repository Flow，后台分组，SavedStateHandle 筛选恢复、错误重试 |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionViewModel.kt | 增加编辑模式、原记录加载、同 ID 更新、确认删除、缺失记录和完成状态处理 |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionUiState.kt | 编辑 ID、记录缺失、删除中 / 失败 / 完成状态 |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionScreen.kt | 复用单笔表单查看与编辑；保存修改 / 删除按钮、删除确认、日期弹窗前收起键盘 |
| app/src/main/java/com/example/myledger/ui/navigation/MyLedgerApp.kt | 明细入口、带 Long ID 的编辑路由、更新和删除回执、返回明细 |
| app/src/main/java/com/example/myledger/ui/navigation/LedgerDestination.kt | 编辑页面标题及路由 |
| app/src/main/java/com/example/myledger/util/LedgerDates.kt | 集中提供按当前 Locale 显示日期和星期的 formatter |
| app/src/main/res/values/strings.xml | 明细、筛选、编辑、确认、错误及回执文案 |
| app/build.gradle.kts | versionCode 6 / 0.1.0-stage6；不新增依赖 |
| app/src/test/java/com/example/myledger/ui/records/RecordsGroupingTest.kt | 5 项纯 JVM 日期分组、财务口径、字段、溢出与日期范围测试 |
| app/src/test/java/com/example/myledger/ui/records/RecordsViewModelTest.kt | 3 项真实 Room Flow 更新、范围恢复与活动名称更新测试 |
| app/src/test/java/com/example/myledger/ui/records/RecordsScreenTest.kt | 2 项 Compose 展示 / 筛选控件测试、深浅色与大金额 / 长备注渲染 |
| app/src/test/java/com/example/myledger/ui/records/RecordsAppTest.kt | 4 项真实 MainActivity 新增 / 编辑 / 筛选 / 删除 / 恢复测试 |
| app/src/test/java/com/example/myledger/ui/transaction/TransactionViewModelTest.kt | 增加 6 项编辑、取消、缺失记录、恢复、同 ID 更新与删除测试 |
| app/src/androidTest/java/com/example/myledger/NavigationTest.kt | 明细导航检查改为真实筛选入口，避免依赖空数据库 |
| README.md、STAGE5_VERIFICATION.md、STAGE6_VERIFICATION.md | 当前阶段、上一阶段手机反馈、改动和验证步骤 |

## 实现与决策

- 明细默认显示全部账目，以 LocalDate、ID 倒序分组；不依赖日期字符串比较。
  每笔显示分类、金额符号、类型、备注、已有活动和可报销标记。备注最多两行，完整内容在编辑表单查看。
- 日摘要调用同一 FinancialAnalysis.summarize()：全部支出、普通收入、报销到账分开显示。
  普通收入不含报销；可报销及活动支出仍包含在全部支出中。没有在 Composable 复制统计逻辑。
- 日期范围两端都包含，默认不筛选。开始 / 结束由稳定 Material 日期弹窗选择，
  结束早于开始时禁用应用并显示错误；取消不应用，清除恢复全部；筛选随 Activity 重建恢复。
- Repository / DAO Flow → RecordsViewModel → StateFlow → collectAsStateWithLifecycle()。
  分组和摘要在 Dispatchers.Default 执行；插入、编辑、删除和活动改名自动更新列表，无手动刷新副本。
- 合计发生 Long 溢出时只替换当天摘要为提示，保留所有账目供检查和编辑，其他日期仍正常显示。
- 点击记录进入复用的单笔表单。首次加载原字段，恢复草稿时不再用数据库原值覆盖；
  编辑加载完成前禁用输入。金额继续复用 MoneyInput，日期使用所选业务日。
- 保存修改调用既有 Repository.updateTransaction()，保持原 ID 和 createdAt，只更新 updatedAt 和编辑字段。
  类型变化联动分类和可报销；返回而未保存不写入数据库。原记录缺失或被并发删除时不意外新增记录。
- 删除只有在确认弹窗点击确认后才调用 Repository；取消不写入，失败可重试。
  保存与删除互相锁定，重复点击不重复执行；完成状态可恢复，成功后才返回明细并显示回执。
- 沿用数据库 schema v1、现有 DAO 和 Repository，无迁移、destructive fallback、新依赖或 Stage 7 活动管理。

## Windows 实际验证

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，最终生产代码构建 11 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，最终 40 秒；76 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL；lint 0 错误、24 条原有警告；设备测试 APK 编译通过 |

新增 20 项测试，原有 Stage 2 / 3 / 4 / 5 的 56 项也全部通过。
覆盖跨月 / 跨年倒序、同日 ID 排序、普通收入和报销分离、活动 / 可报销支出、
大金额溢出保留明细、完整备注与字段、范围校验与含端点查询、快速切换范围、恢复筛选、
Room 插入 / 编辑跨日 / 删除后的自动分组与汇总、活动名称自动刷新、编辑同 ID 和创建时间、
无效编辑不写入、取消保留原值、编辑草稿与完成状态恢复、缺失记录不变成新增、仅删除选定记录。

真实 MainActivity 用例验证：单笔保存后明细出现，编辑金额和日期后重新分组且不重复；
筛选含两端、旋转保留和清除；删除取消保留、确认弹窗旋转恢复及确认后仅移除目标记录。

测试在 Windows Robolectric API 36、原生 SQLite / 图形资源下运行，不等同于实体手机。
日期编辑完整触摸流程使用 411×891dp；筛选、删除及旋转用例使用 411×600dp。
Robolectric 在 600dp 下日期弹窗关闭后的保存触摸注入没有触发点击，因此另保留 600dp 同流程的
无障碍 OnClick 用例，仍验证真实页面 → ViewModel → Repository → Room → 明细的更新结果。
没有 mock 保存或跳过数据断言；短屏 / 键盘下日期弹窗后的触摸保存仍须手机验收。
日期控件对“今天”可能添加文字前缀，测试按完整日期文本的子串定位，不依赖可视的单个数字。
LazyColumn 筛选按钮可能随列表滚出视口，测试先滚回顶部再定位。

已查看 app/build/stage6-ui/records-light.png、records-dark-large.png，确认分类、金额、摘要及长备注可读。
截图使用原生 View.draw(Canvas)；没有 PixelCopy 等待或模拟渲染替代交互断言。
测试数据库隔离，MainActivity 用例只清理自己创建的记录。设备 androidTest 仅编译，未通过 ADB 执行。

日志：build/stage6-verification/assembleDebug.log、test.log、checks.log。
报告：app/build/reports/tests/testDebugUnitTest/index.html、app/build/reports/lint-results-debug.html。
数据库 schema 与 Stage 5 一致；生产代码没有 Float / Double 金额、Experimental API 或 GlobalScope。

APK：app/build/outputs/apk/debug/app-debug.apk，31,482,429 字节。
SHA-256：00286b5126666110853a5c912c7f0a2b9f25e4915bb857e7a1c3937b29830d1d。
版本：0.1.0-stage6 / versionCode 6，Android API 36 及以上。
apksigner 验证通过，签名与旧版本一致，可以覆盖安装保留数据；无 INTERNET 权限。

## 手机需要验证

从 [Stage 6 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage6)
下载 MyLedger-stage6-debug.apk，覆盖安装旧版本。

1. 明细应显示之前保存的单笔和多笔账目，按日期倒序分组；当天普通收入与报销到账分别汇总。
2. 新增支出 12.34 元应立即出现。点击该条，把金额改为 18.01 元、日期改为昨天并保存：
   仍是一条记录，移到昨天分组，日报摘要更新。重点检查键盘弹出、日期弹窗关闭后的保存点击。
3. 编辑后直接返回，原记录不变；编辑中旋转，金额、分类、日期与备注草稿保留。
4. 删除时先取消，记录保留；再次删除并确认，仅该条记录消失，摘要更新。
5. 筛选一天或两天，范围两端都包含；清除显示全部，旋转后筛选保持；取消弹窗不改变筛选。
6. 检查跨月记录、长备注、大金额、深浅色与离线使用；单笔、多笔保存后的独立条目均可在明细查到。

首页摘要、统计界面和活动管理仍待后续阶段。本次到此停下，等待手机反馈，不自动继续 Stage 7。
