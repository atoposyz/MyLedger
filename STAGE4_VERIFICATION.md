# Stage 4 — 记一笔

2026-10-03。按用户授权，在 Stage 3 构建、测试、lint 通过并发布后自动继续 Stage 4。
本次完成真实单笔记账，不进入 Stage 5。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| app/build.gradle.kts | Lifecycle Compose / SavedStateHandle 2.11.0；Compose JVM 测试；版本 0.1.0-stage4 / versionCode 4 |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionScreen.kt | 三种类型、金额、分类、活动、可报销、Material 3 日期弹窗、备注、保存与错误提示；Route 手动创建 ViewModel |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionUiState.kt | 表单、数据列表、加载 / 保存状态及保存回执 |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionViewModel.kt | Repository Flow、SavedStateHandle 草稿恢复、输入校验、真实保存和重复点击保护 |
| app/src/main/java/com/example/myledger/ui/navigation/MyLedgerApp.kt | 接入表单，保存后返回原标签页并显示保存回执 |
| app/src/main/java/com/example/myledger/util/MoneyInput.kt | 无浮点的金额解析与两位小数格式化 |
| app/src/main/java/com/example/myledger/util/LedgerDates.kt | Material DatePicker UTC 日历日期转换，避免时区偏移 |
| app/src/main/res/values/strings.xml | 表单文案；修正首页 / 明细占位文案，避免已保存后仍声称没有账目 |
| app/src/test/java/com/example/myledger/util/MoneyAndDatesTest.kt | 四项金额与日期测试 |
| app/src/test/java/com/example/myledger/ui/transaction/TransactionViewModelTest.kt | 七项 ViewModel 与真实 Room 集成测试 |
| app/src/test/java/com/example/myledger/ui/transaction/TransactionScreenTest.kt | 三项 Compose 控件交互测试与深浅色渲染 |
| app/src/test/java/com/example/myledger/ui/transaction/TransactionAppTest.kt | 两项实际 MainActivity → Navigation → 表单 → Room 测试 |
| app/src/androidTest/java/com/example/myledger/NavigationTest.kt | 导航设备测试适配真实表单与新占位文案 |
| README.md、STAGE2_VERIFICATION.md、STAGE4_VERIFICATION.md | 当前阶段、授权记录与手机验证步骤 |

## 实现与决策

单笔录入遵循 Compose → ViewModel → Repository → Room，Composable 不访问 DAO。
使用 collectAsStateWithLifecycle() 收集 StateFlow，手动 ViewModel factory 创建 SavedStateHandle。
数据从 Repository Flow 读取，字段用 SavedStateHandle 保存基本类型，支持 Activity 重建与保存状态恢复。

金额只按十进制字符串转换为 Long 分，不使用 Float / Double；支持 12、12.34、12.、.5 等常见输入，
拒绝零、负数、超过两位小数、科学计数法、非数字和溢出。输入错误时自动滚回金额字段显示提示。
输入过程中允许暂存不完整数字，点击保存时统一校验。

默认消费日期是本地今天。Material DatePicker 的 millis 按 UTC 日历日转换为 LocalDate；
保存使用所选日期，不用保存时刻代替消费日期。
支出显示 11 个分类，普通收入显示 5 个，报销到账只显示“报销”。
切换类型清除不兼容分类；切到收入或报销时清除并隐藏可报销标记。
活动与分类独立，可以选择已有活动或不关联；本阶段不创建演示活动或活动管理页。

保存前锁住编辑和保存按钮，ViewModel 同步设置 isSaving，防止重复点击产生多笔。
Room 成功插入返回 ID 后才生成回执、返回原页并显示“已保存：类型 ¥金额 · 分类 · 日期”。
失败保留草稿、恢复可编辑状态，允许纠正关联活动后重试。协程取消继续传播，不吞掉 CancellationException。

沿用数据库 schema v1，没有改表、迁移或 destructive migration。
保留首页、明细、统计及记多笔占位，不提前实现列表、编辑、统计 UI 或批量输入。
依赖只新增项目需要的 Lifecycle 组件和测试依赖，没有 DI、网络、登录或 LLM。

依据 [Lifecycle 稳定版本说明](https://developer.android.com/jetpack/androidx/releases/lifecycle)
使用 2.11.0；现有 [Material 3 1.4.0](https://developer.android.com/jetpack/androidx/releases/compose-material3)
中的 DatePicker / DatePickerDialog 已稳定，本阶段生产代码无 Experimental API。

## Windows 实际验证

沿用 Stage 2 / 3 的 Windows JBR 25、SDK、缓存与原始原生 SQLite 测试资源。

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，最终 11 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，最终 34 秒；43 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL，14 秒；lint 0 错误、24 警告；设备测试 APK 编译通过 |

新增 16 项测试：

- 4 项纯 JVM 测试：金额精确解析、非法金额、Long 最大值与溢出、跨时区 / 夏令时 / 闰日。
- 7 项 Robolectric ViewModel + 原生 SQLite 测试：默认日期和分类切换、无效金额不写入、
  全字段保存与重复点击、草稿 / 已保存状态恢复、报销与 FinancialAnalysis 联动、关联失效后的纠正重试、磁盘数据库重开。
- 3 项 Compose 表单测试：金额 / 分类 / 活动 / 可报销 / 日期 / 备注 / 保存交互、收入与报销分类切换、错误显示与日期弹窗取消。
- 2 项实际 MainActivity 测试：保存一笔支出写入 Room 后返回统计标签页并显示回执；
  非法金额停留表单、短屏自动显示错误、重建 Activity 后保留收入草稿，最终写入 Room 并返回明细标签页。

原有 12 项财务分析、14 项数据库和 1 项模板测试也全部通过。
Compose 和 MainActivity 测试实际在 Windows Robolectric API 36 环境运行，使用原生 SQLite / 图形运行库。
它们不是实体手机或 ADB 测试。androidTest 仅编译，未在手机运行。
测试记录只位于独立测试数据库；MainActivity 测试清理自己创建的记录，不清空数据库。

已查看固定配色的表单深浅色 PNG，确认字段与按钮可读、无裁切。
截图位于 app/build/stage4-ui/form-light.png、form-dark.png。
Robolectric 不适用 PixelCopy 等待，截图直接用原生 View.draw(Canvas) 渲染；交互断言完整执行。
日期测试点击 Material DatePicker 提供的完整无障碍日期文本，不依赖可视的单个数字。
Compose lint 提示已通过使用 LocalResources 获取保存回执资源修复，没有 suppress / baseline。
剩余警告是原模板旧依赖、无用资源、SDK 判断、冗余标签和直接声明依赖版本的建议。

日志：build/stage4-verification/assembleDebug.log、test.log、checks.log。
测试报告：app/build/reports/tests/testDebugUnitTest/index.html。
Lint 报告：app/build/reports/lint-results-debug.html。

APK：app/build/outputs/apk/debug/app-debug.apk，30,996,985 字节。
SHA-256：75e556b552c4c22e8bd3c232eb3dc2de51e7eeb7d657b0e5ae03caa97d644977。
版本：0.1.0-stage4 / versionCode 4，Android API 36 及以上。
apksigner 验证通过，签名与之前的 Stage 1 / 2 一致；APK 无 INTERNET 权限，可直接覆盖安装。

## 手机需要验证

从 [Stage 4 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage4)
下载 MyLedger-stage4-debug.apk，覆盖安装保留数据。

1. 从统计页进入记一笔，记支出 12.34 元、餐饮、昨天、任意备注后保存。
   确认回到统计页，回执的金额、分类和日期与输入一致。
2. 记普通收入 88.50 元，选择项目酬金；另记报销到账 60.01 元，确认两种类型的分类不同。
3. 尝试空金额、0、12.345、超大金额，确认错误可见、停留表单，可以修改后重新保存。
4. 支出开启可报销；切换收入 / 报销时开关隐藏。填写金额、分类、日期和备注后旋转，草稿保持。
5. 日期弹窗能确认和取消；键盘出现后能滚动到备注 / 保存；深浅色和断网保存正常。

新安装时活动为空，可选择“不关联活动”；活动创建在 Stage 7 提供。
明细列表在 Stage 6 提供，所以手机当前通过保存回执检查录入流程；持久化及写入字段已由自动测试验证。
本阶段手机结果待用户确认，不自动继续 Stage 5。
