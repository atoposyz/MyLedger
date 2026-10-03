# Stage 5 — 记多笔

2026-10-03。用户授权继续下一阶段，至需要手机测试时停止。
完成 Stage 5 并发布代码与 APK；不进入 Stage 6。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| app/src/main/java/com/example/myledger/ui/batchentry/BatchDailyEntryScreen.kt | 替换占位页；生命周期感知 Route；默认值、LazyColumn 草稿增删、类型 / 金额 / 分类 / 备注、逐条选项、合计及全部保存 |
| app/src/main/java/com/example/myledger/ui/batchentry/BatchEntryUiState.kt | 页面默认值、TransactionDraft、保存回执、统一的录入合计与溢出处理 |
| app/src/main/java/com/example/myledger/ui/batchentry/BatchEntryViewModel.kt | Repository Flow、默认值继承、字段覆盖、SavedStateHandle、整批校验与事务保存 |
| app/src/main/java/com/example/myledger/ui/components/EntryDialogs.kt | 提取现有分类 / 活动选择及日期弹窗，单笔和多笔共用 |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionScreen.kt | 复用弹窗，保持单笔录入行为 |
| app/src/main/java/com/example/myledger/ui/navigation/MyLedgerApp.kt | 接入多笔表单，成功保存后返回原页并提示数量和合计 |
| app/src/main/res/values/strings.xml | 默认值、草稿、合计、错误与保存回执文案 |
| app/build.gradle.kts | versionCode 5 / 0.1.0-stage5；不新增依赖 |
| app/src/test/java/com/example/myledger/ui/batchentry/BatchEntryViewModelTest.kt | 9 项 Room / ViewModel / SavedState 集成测试 |
| app/src/test/java/com/example/myledger/ui/batchentry/BatchEntryScreenTest.kt | 2 项 Compose 控件测试，深浅色原生渲染截图 |
| app/src/test/java/com/example/myledger/ui/batchentry/BatchEntryAppTest.kt | 2 项真实 MainActivity → 多笔表单 → Room 测试 |
| app/src/androidTest/java/com/example/myledger/NavigationTest.kt | 导航设备测试适配真实多笔表单 |
| README.md、STAGE5_VERIFICATION.md | 当前阶段与手机验证步骤 |

## 实现与决策

- 页面只维护 List<TransactionDraft>，不创建 BatchEntity，不修改数据库 schema v1。
- 每笔支持支出、普通收入和报销到账。分类随类型变化，收入和报销不保留可报销标记。
- 初始有一条今天的支出草稿。修改顶部默认值只影响之后新增的草稿，页面明确说明这一点。
  新增草稿继承当前默认日期 / 活动，继承上一条的类型与分类；金额、备注重新留空。
- 默认活动为 WORK 时显示默认可报销开关，新增支出可继承；切换默认活动清除默认可报销。
  每笔支出可独立覆盖活动和可报销，也可覆盖业务日期。
- 金额、分类、备注直接显示；日期、活动和可报销放在“更多选项”，收起时仍显示日期与已设活动 / 标记。
  LazyColumn 使用稳定草稿 ID；添加后滚到新草稿，校验失败后滚到第一个错误行。
- 金额解析复用无浮点的 MoneyInput。录入合计累加所有类型的有效金额，是输入合计，非净收支；
  空白和错误金额暂不计入但会阻止保存。Math.addExact 检查溢出，溢出时提示并禁止保存。
- ViewModel 先校验全部草稿，再同步锁定保存与编辑，防止重复点击。
  一次调用既有 Repository.addTransactions()，由 Room.withTransaction 批量插入独立 TransactionEntity。
  任意一笔保存失败整批零写入，保留全部草稿以便纠正后重试；成功后才返回原页并显示回执。
- SavedStateHandle 保存基本类型和 ArrayList<Bundle>，保留默认值、草稿、顺序与完成状态。
  测试覆盖 Bundle 的实际 Parcel 往返、Activity 重建与已完成状态恢复，恢复后不会再次保存。
- 沿用 Compose → ViewModel → Repository → Room，不在 Composable 中计算合计或访问 DAO。
  取消异常继续传播，没有新依赖、实验性生产 API、网络功能或演示账目 / 活动。

## Windows 实际验证

沿用现有 Windows JBR 25 / SDK / Gradle 缓存以及原始 Robolectric 原生 SQLite / 图形资源。

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，最终 12 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，最终 37 秒；56 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL，26 秒；lint 0 错误、24 警告；设备测试 APK 编译通过 |

新增 13 项测试：9 项 ViewModel / Room、2 项 Compose 表单、2 项实际 MainActivity。
覆盖一次保存十条独立记录、重复点击保护、默认值继承与逐条覆盖、WORK 默认开关、
无效金额整批阻止、失效活动零写入及纠正重试、混合类型与 FinancialAnalysis 财务口径、
合计溢出、删除与空批次、Parcel 草稿恢复、完成状态恢复、多笔控件交互和返回原标签页。
实际 MainActivity 测试在 411×600dp 短屏运行，十笔真实界面输入后准确写入 55.10 元；
另一测试验证无效金额自动滚到错误行、重建恢复、移除错误草稿后成功保存。
原有 Stage 2 / 3 / 4 的 43 项测试也全部通过。

已查看 app/build/stage5-ui/defaults-light.png、row-dark.png、total-dark.png，文字、控件和保存按钮可读。
截图使用原生 View.draw(Canvas)，测试使用 Robolectric API 36；不是实体手机测试。
测试数据仅位于隔离测试数据库；MainActivity 测试只清理自己创建的记录。
设备 androidTest 仅编译，未通过 ADB 执行。lint 剩余 24 条警告与 Stage 4 一致，无新增错误或警告。

日志：build/stage5-verification/assembleDebug.log、test.log、checks.log。
报告：app/build/reports/tests/testDebugUnitTest/index.html、app/build/reports/lint-results-debug.html。

APK：app/build/outputs/apk/debug/app-debug.apk，31,296,765 字节。
SHA-256：9b757deb6e505d4ad08d15275e68c570901fae4ffcba8c4bff6153fda83ae4e8。
版本：0.1.0-stage5 / versionCode 5，Android API 36 及以上。
apksigner 验证通过，签名与旧版本一致，可以覆盖安装；无 INTERNET 权限。

## 手机需要验证

从 [Stage 5 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage5)
下载 MyLedger-stage5-debug.apk，覆盖安装保留旧数据。

1. 从统计页进入记多笔，依次添加 10 笔金额 1.01、2.01 … 10.01 元，合计 55.10 元。
   全部保存后回到统计页，提示“已保存 10 笔 · 录入合计 ¥55.10”。
2. 移除初始草稿，顶部日期设为昨天，再添加两笔，确认两笔都是昨天。
   一笔通过更多选项改为前天，另一笔仍是昨天；更改顶部默认值不改已有草稿。
3. 第一笔选择交通，再添加下一笔，确认继承分类；移除一笔后数量与合计更新。
4. 一笔填有效金额，另一笔填 0 或 12.345，全部保存时停留表单并显示错误行。
   纠正后再次保存，提示数量和合计正确。
5. 多笔填写中旋转手机，检查草稿、类型、日期、分类和备注保留；键盘下能滚动、增删和保存。
6. 检查普通收入 / 报销分类、日期取消、深浅色与断网保存，同时确认记一笔仍正常。

活动管理在 Stage 7 开放；新安装暂无活动，默认选择不关联活动，WORK 默认值已通过自动测试。
明细列表在 Stage 6 开放，当前通过保存回执检查手机流程；独立条目、日期与事务已由自动测试验证。
至此到达需要手机测试的位置，等待用户反馈，不继续 Stage 6。
