# Stage 7 — 活动管理

2026-10-03。用户授权开始 Stage 7，并沿用完成后自动发布代码和 APK 的约定。
本次完成活动管理，等待手机验证，不进入 Stage 8。
用户认为 Stage 6.1 视觉效果不符合预期；本阶段不继续做视觉改版，整体美化留待 UI 收尾。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| app/src/main/java/com/example/myledger/ui/activity/ActivityUiState.kt | 列表、草稿与编辑 / 删除状态；可选日期校验 |
| app/src/main/java/com/example/myledger/ui/activity/ActivityListViewModel.kt | Repository Flow → StateFlow，列表更新与重试 |
| app/src/main/java/com/example/myledger/ui/activity/ActivityListScreen.kt | 活动列表、新建入口、名称 / 类型 / 可选日期 / 备注展示 |
| app/src/main/java/com/example/myledger/ui/activity/ActivityEditorViewModel.kt | 新建、同 ID 编辑、SavedStateHandle 草稿恢复、关联数量检查和确认删除 |
| app/src/main/java/com/example/myledger/ui/activity/ActivityEditorScreen.kt | 活动表单、稳定日期控件、可清除日期、校验、删除提示与确认 |
| app/src/main/java/com/example/myledger/data/local/dao/TransactionDao.kt | 按活动统计关联记录数量，覆盖全部日期和类型 |
| app/src/main/java/com/example/myledger/data/repository/LedgerRepository.kt | 有明确结果的安全删除接口，单个数据库事务中重新检查关联数量 |
| app/src/main/java/com/example/myledger/ui/settings/SettingsScreen.kt | 活动管理入口，保留主题跟随系统说明 |
| app/src/main/java/com/example/myledger/ui/navigation/LedgerDestination.kt | 活动列表、新建、Long ID 编辑路由 |
| app/src/main/java/com/example/myledger/ui/navigation/MyLedgerApp.kt | 活动导航和保存 / 删除回执；进入表单时清除旧回执，避免遮挡按钮 |
| app/src/main/java/com/example/myledger/ui/batchentry/BatchDailyEntryScreen.kt | 打开选择弹窗与保存时清除焦点、收起键盘 |
| app/src/main/res/values/strings.xml | 活动表单、校验、关联保护、确认与回执文案 |
| app/build.gradle.kts | versionCode 8 / 0.1.0-stage7；无新增依赖 |
| app/src/test/java/com/example/myledger/ui/activity/ActivityViewModelTest.kt | 10 项真实 Room / ViewModel 测试 |
| app/src/test/java/com/example/myledger/ui/activity/ActivityAppTest.kt | 3 项真实 MainActivity 创建、编辑、记账关联、改名、删除保护与确认测试；深浅色截图 |
| app/src/test/java/com/example/myledger/ui/records/RecordsAppTest.kt | 增加短屏日期编辑后的触摸保存回归测试 |
| app/src/androidTest/java/com/example/myledger/NavigationTest.kt | 设置页面导航断言改为活动管理入口 |
| README.md、本文件 | 当前阶段、实现决策、验证和手机检查步骤 |

## 实现与决策

- 活动入口为“设置 → 活动管理”，保留四个一级页面。活动列表和表单隐藏底部导航及记账 FAB，返回路径正常。
- 复用已有 ActivityEntity / ActivityType：名称必填，PERSONAL / WORK，起止日期、备注选填。
  名称和备注保存时去除首尾空白；空备注存 null。允许只设一个日期、同一天或全部留空，结束不能早于开始。
  活动日期是说明信息，不额外限制账目的业务日期。
- 活动列表使用 DAO Flow → Repository → ViewModel → StateFlow → collectAsStateWithLifecycle()。
  编辑活动保持原 ID，不重写已有账目。活动改名会通过既有 Flow 更新明细和单笔 / 多笔的活动选择。
- 编辑首次加载原值，之后恢复草稿不覆盖未保存输入；取消不写入。缺失活动显示提示，过期编辑失败而不会变成新增。
  保存与删除互斥，完成状态保存以避免重复提交；日期选择弹窗及删除确认支持旋转恢复。
- 删除有关联账目的活动时显示所有关联记录数量并阻止删除，不自动解除关联或级联删除账目。
  自动解除关联可能改变日常支出的归属，故由用户在明细逐条修改关联后再删除。
- 没有关联记录的活动须二次确认。最终删除在单个 Room 事务中再次检查关联数量；
  如确认前新增关联账目，则改为保护提示，保持活动、账目和统计不变。既有 RESTRICT 外键继续保护数据。
- 单笔、批量默认活动及单条覆盖直接复用既有选择和保存流程。WORK 默认可报销只用于新增草稿，已有草稿不被覆盖。
- 修复实际页面测试发现的回执遮挡：连续创建活动后立即进入多笔录入，旧“已保存活动”提示曾遮住底部保存按钮。
  回执现在只出现在返回后的一级页面或活动列表；进入表单时清掉当前及随后排队的旧回执。
  回归测试以真实触摸完成保存，并验证表单里没有旧回执。
- 数据库 schema v1、实体字段和统计口径保持不变，没有 migration、destructive fallback、新依赖或 Stage 8 首页功能。

## Windows 验证

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，最终 9 秒；代码修改后的编译构建也已通过 |
| .\gradlew.bat test | BUILD SUCCESSFUL，52 秒；90 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL，38 秒；lint 0 错误、24 条原有警告，设备测试 APK 编译通过 |

新增 14 项回归测试，原有 76 项全部保留并通过。
600dp 下新增账目后编辑金额 / 日期再触摸保存的回归也通过；保留原有无障碍保存用例。
当前这条触摸路径已有自动验证，实体手机的键盘、手势和日期弹窗仍须按下方步骤验收。

10 项活动 ViewModel 测试覆盖：创建和重复保存保护、可选字段与日期校验、同 ID 编辑且账目不变、
新建 / 编辑草稿恢复、保存完成恢复、取消编辑、全部类型 / 跨日期关联计数、阻止关联删除、
确认后出现新关联的保护、确认弹窗和删除完成恢复、缺失 / 过期编辑、列表外部增改删自动更新。

3 项 MainActivity 测试覆盖：空名称与逆序日期校验、日期清除、旋转恢复、创建与编辑 / 取消；
通过 UI 创建“上海演唱会”和“ISCA 2027”，单笔关联个人活动，多笔继承 WORK 可报销并逐条覆盖为个人活动；
验证 4 个独立账目、总额 77.34 元、可报销 35.00 元、日常支出 0、改名后明细更新且账目实体不变；
关联删除被阻止、在明细主动解除关联后取消 / 确认删除、确认旋转恢复，保留其他活动及原账目。

已查看原生 View.draw(Canvas) 截图：app/build/stage7-ui/activities-light.png、activities-dark.png、
activity-editor-light.png、activity-editor-dark.png。浅色表单为 411×891dp，深色列表及表单为 411×600dp。
弹窗通过真实 Compose 交互断言验证；这些截图只捕获 Activity 主窗口，不将其当成弹窗截图。
测试使用原生 SQLite / 图形资源；测试数据按专属标记清理，不删除其他记录。
设备 androidTest 仅编译，未通过 ADB 执行；手机触摸、键盘和系统动态配色仍须手动验收。

日志：build/stage7-verification/assembleDebug.log、test.log、checks.log。
报告：app/build/reports/tests/testDebugUnitTest/index.html、app/build/reports/lint-results-debug.html。

APK：31,481,733 字节；0.1.0-stage7 / versionCode 8，Android API 36 及以上。
SHA-256：06b1b7da7f67932b49a108804126ae87cfa9c40bda56a2f87a7bb4ea09d4023a。
apksigner 验证通过，签名与旧版本一致，可覆盖安装保留数据；没有 INTERNET 权限。

## 手机验证

从 [Stage 7 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage7)
下载 MyLedger-stage7-debug.apk，直接覆盖安装保留数据。

1. 设置 → 活动管理，创建“上海演唱会”（个人计划）和“ISCA 2027”（公务 / 科研）。
2. 检查空名称、起止日期逆序不能保存；单个日期、同日或留空可保存，日期可清除，备注可编辑。
3. 记一笔关联“上海演唱会”；记多笔默认“ISCA 2027”、默认可报销，再新增几条。
   在某条更多选项中覆盖活动与可报销，保存后检查明细的关联和标记。默认值不会追溯修改已有草稿。
4. 活动改名后明细和选择列表同步更新；取消编辑不保存，编辑中旋转草稿保留。
5. 删除有关联账目的活动应提示数量并阻止删除，原账目不变。空活动删除先取消、再确认，只有确认后才消失。
   如需删除关联活动，先在明细手动修改每笔关联，再回来删除。
6. 创建 / 编辑活动后立即打开录入页，检查旧提示不遮挡保存；重点检查更多选项展开、键盘、日期弹窗和旋转后的保存点击。
   系统深浅色及断网下均应正常；原有账目仍在。

本阶段完成后停下，等待手机反馈，不自动进入 Stage 8。
