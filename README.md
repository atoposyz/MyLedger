# MyLedger

单用户、本地优先的 Android 个人记账 App。产品范围见
[PRODUCT_SPEC.md](PRODUCT_SPEC.md)，开发规则见 [AGENTS.md](AGENTS.md)，
分阶段计划见 [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)。

当前实现到 v0.5：具备本地 Room 数据层、统一财务分析层、单笔 / 多笔录入、
按天分组的明细、日期筛选、编辑和删除、活动管理、首页摘要、日常 / 全部支出统计，
以及 DataStore 主题设置、ZIP / 密码加密文件导出、分享、恢复，
每日自动本地备份和最近 7 份密钥加密历史、自建服务器加密备份，
以及第五个“助手”页面（本地汇总查询、API 只读财务分析、需确认的 AI 记账草稿）。
统计页支持选择月份、日常/全部联动的分类占比与排行及六个月趋势。
首页/统计/助手按需查询历史，明细提供今天/本月/上月筛选，顶部固定记账入口不遮挡列表。

下载 [v0.5.0 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.5.0)
中的 MyLedger-v0.5.0-debug.apk 覆盖安装。服务器/API 配置见
[CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md)，联合手机步骤见
[V05_PHONE_ACCEPTANCE.md](V05_PHONE_ACCEPTANCE.md)，服务器/只读助手步骤见 [V04_PHONE_ACCEPTANCE.md](V04_PHONE_ACCEPTANCE.md)，完整旧功能清单见
[PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md)。Stage 12 已由用户确认通过；v0.2–v0.5 待联合手机验收。

服务器/API 暂不配置也可离线记账、本地备份和本地查询。API 仅在用户发送问题或生成草稿时调用，分别需要汇总权限/描述权限；
分析不发送逐笔流水或备注，记账只发送本次输入，只有用户确认草稿才写入本地账本。密钥加密保存在本机，
不随账本备份迁移。备份服务代码与部署模板见 [server](server/README.md)。

Windows 接手检查结果见 [STAGE0_WINDOWS_VERIFICATION.md](STAGE0_WINDOWS_VERIFICATION.md)。
Stage 0 的 Windows 构建、单元测试和手机手动启动已通过。
Stage 1 的实现与验收记录见 [STAGE1_VERIFICATION.md](STAGE1_VERIFICATION.md)。
Stage 1 手机手动验证已由用户确认通过。
Stage 2 数据模型、测试及手机验证步骤见 [STAGE2_VERIFICATION.md](STAGE2_VERIFICATION.md)。
Stage 3 统计口径与测试见 [STAGE3_VERIFICATION.md](STAGE3_VERIFICATION.md)。
Stage 4 单笔录入、43 项测试及手机步骤见 [STAGE4_VERIFICATION.md](STAGE4_VERIFICATION.md)。
Stage 5 多笔录入、事务保存与手机步骤见 [STAGE5_VERIFICATION.md](STAGE5_VERIFICATION.md)。
Stage 5 手机验证已由用户确认通过。
Stage 6 明细、编辑、删除与筛选见 [STAGE6_VERIFICATION.md](STAGE6_VERIFICATION.md)。
Stage 6.1 加强日期与条目区分，改动和验证见 [STAGE6_READABILITY_VERIFICATION.md](STAGE6_READABILITY_VERIFICATION.md)。
Stage 7 活动管理与手机步骤见 [STAGE7_VERIFICATION.md](STAGE7_VERIFICATION.md)。
Stage 7 手机验证已由用户确认通过。
Stage 8 首页、统计口径与手机步骤见 [STAGE8_VERIFICATION.md](STAGE8_VERIFICATION.md)。
Stage 9 统计页、联动口径与手机步骤见 [STAGE9_VERIFICATION.md](STAGE9_VERIFICATION.md)。
Stage 10 主题持久化见 [STAGE10_VERIFICATION.md](STAGE10_VERIFICATION.md)。
Stage 11 本地备份见 [STAGE11_VERIFICATION.md](STAGE11_VERIFICATION.md) 和 [备份格式](BACKUP_FORMAT.md)。
Stage 12 UI 收尾与最终测试结果见 [STAGE12_VERIFICATION.md](STAGE12_VERIFICATION.md)。
v0.2 备份增强与验证结果见 [V02_VERIFICATION.md](V02_VERIFICATION.md)。
v0.5 草稿与整体优化见 [V05_VERIFICATION.md](V05_VERIFICATION.md)。
v0.3 服务器备份见 [V03_VERIFICATION.md](V03_VERIFICATION.md)，v0.4 助手见 [V04_VERIFICATION.md](V04_VERIFICATION.md)。

## 工程配置

- 单模块 `app`，包名和 applicationId：`com.example.myledger`。
- Kotlin、Jetpack Compose、Material 3、Gradle Kotlin DSL。
- 基础依赖版本集中在 `gradle/libs.versions.toml`，Compose 依赖使用 BOM。
- Navigation Compose 2.10.2 用于 Stage 1 导航，版本暂在 `app/build.gradle.kts` 声明；
  当前 Windows 沙箱无法完成修改 Catalog 后的 Java 访问器编译，详见 Stage 1 验收记录。
- Room 2.8.5、KSP 2.3.12；数据库版本 1，schema JSON 纳入版本控制。
- Lifecycle 2.11.0 用于 ViewModel、SavedStateHandle 和生命周期感知的 StateFlow 收集。
- DataStore Preferences 1.2.1 保存主题设置。
- WorkManager 2.12.0 持久化每日备份调度；默认关闭，可在备份页面开启。
- Robolectric 4.17 用于 JVM 数据库及 Compose 交互测试，不进入 APK。
- 保留模板构建版本：AGP 9.4.1、Gradle 9.6.0、Kotlin Compose 插件 2.2.10。
- `compileSdk` / `targetSdk`：37；`minSdk`：36。
- Gradle daemon 的 JDK 版本由 `gradle/gradle-daemon-jvm.properties` 指定为 25；
  Java 源码及字节码兼容目标保留为 11。
- Material 3 使用克制的蓝灰固定配色，可选择跟随系统 / 浅色 / 深色，重启后保持设置。
- Activity 使用 edge-to-edge，Compose 使用 Scaffold 处理内容安全区域。

## 构建与测试

在 Windows 项目目录中运行，需要可用的 JDK、Android SDK 和依赖缓存或下载网络：

```bat
gradlew.bat assembleDebug
gradlew.bat test
```

PowerShell 中使用 `./gradlew.bat`。

本机 Android Studio 自带 JBR 25，可在 Windows PowerShell 中运行：

```powershell
cd 'C:\Users\Xuanfly\AndroidStudioProjects\MyLedger'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

上述 `JAVA_HOME` 设置只影响当前终端。两个命令都输出 `BUILD SUCCESSFUL`
才算通过本阶段的构建与测试验收。

在 WSL 中进行原生 Linux 构建时，需要 Linux JDK 和 Linux Android SDK，
设置 `JAVA_HOME`，并让本地 `local.properties` 中的 `sdk.dir` 指向 Linux SDK：

```sh
./gradlew assembleDebug
./gradlew test
```

当前 `local.properties` 是 Android Studio 生成的 Windows 本地配置。
Windows SDK 的构建工具是 Windows 可执行文件，不能直接当作 Linux SDK 使用。
若从 WSL 使用 Windows 构建环境，可在 WSL Windows 互操作正常时运行：

```sh
cmd.exe /d /c "gradlew.bat assembleDebug"
cmd.exe /d /c "gradlew.bat test"
```

`local.properties` 不应纳入版本控制。`test` 运行本地单元测试，
不会执行需要设备或模拟器的 `androidTest`。

## Stage 9 手机验证

从 [Stage 9 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage9)
下载 MyLedger-stage9-debug.apk，直接覆盖安装旧版本，保留应用数据。

1. 统计默认“日常”，本月总额应等于首页日常支出；切换“全部”后应等于首页全部支出。
   普通收入和报销到账都不增加统计页支出。
2. 用测试备注录入本月餐饮 100、交通 300、关联活动的餐饮 200、无活动可报销交通 400 元。
   空账本的日常总额应为 400：交通 300 / 75%，餐饮 100 / 25%；全部应为 1000：交通 700 / 70%，餐饮 300 / 30%。
   已有账本请检查增量：日常 +400、全部 +1000；占比会按原有数据一起计算，不必清空账本。
3. 在上月增加无活动不可报销支出 20、活动支出 50。上月趋势日常增加 20、全部增加 70；
   趋势包含本月共 6 个自然月，缺少支出的月份显示 0，所有月份使用同一最高值缩放。
4. 在明细将本月餐饮 100 改为 150，日常和全部总额均增加 50，分类金额、占比、排行和本月趋势同步更新。
   改日期到上月后，应从本月移到上月；确认删除测试账目后，相应统计更新，其他记录不变。
5. 在统计页记一笔 / 记多笔，保存应回到统计页并刷新；切换一级页面或旋转后所选口径保留。
6. 检查空月、只有活动支出、大字号、深浅色、长金额、断网和重开；切换口径应同时更新全部统计。
   比例可能因四舍五入不合计为 100%；极小正占比显示小于 0.1%。

Windows 自动验证不等同于实体手机测试。本次等待手机反馈，不自动进入 Stage 10。

## 首页回归检查

1. 首页月份应为当前月。本月结余 = 普通收入 + 报销到账 − 全部支出，允许负数；这是本月净收支，不是账户余额。
2. 用可识别的测试备注新增本月账目：普通收入 1000、报销到账 50、无活动且不可报销支出 12.34、
   个人活动支出 20、无活动可报销支出 30、公务活动可报销支出 40 元。
   首页应在原有数值上分别增加：普通收入 1000、报销 50、日常支出 12.34、全部支出 102.34、结余 947.66 元。
   活动摘要分别增加 20 / 40 元；活动与可报销同时存在的支出只从日常支出排除一次。
3. 从首页最近记录点开 12.34 元测试账目，改为 18.01 并保存，应返回首页且各项随之更新；
   将日期改到上月后，本月日常 / 全部支出应移除该金额。最近记录仍按业务日期排序，不限于本月。
4. 首页“管理活动”改名后返回，活动摘要和最近记录名称应更新；“查看明细”应进入明细，保留已有日期筛选。
5. 从首页记一笔 / 记多笔，保存后返回首页且立即刷新；删除测试记录前先检查备注，确认删除后摘要更新。
6. 检查空月份、只有往月记录、负结余、长备注、大字号、深浅色、旋转、重新打开和断网下的显示与点击。
   没有新数据时不要清除旧账本来制造空状态。跨月后重新进入首页应更新月份。

本月结余、普通收入、报销到账与支出口径仍沿用 Stage 8 的用户确认规则。

## 活动管理回归检查

本版同时保留 Stage 7 的活动管理功能，可以继续检查：

1. 设置 → 活动管理 → 创建活动，新增“上海演唱会”，选择个人计划；起止日期和备注可以留空。
2. 创建“ISCA 2027”，选择公务 / 科研，设置起止日期和备注。空白名称不能保存，结束早于开始时不能保存；日期可清除。
3. 记一笔时关联“上海演唱会”；记多笔时选择“ISCA 2027”为默认活动、开启默认可报销，再添加几笔。
   默认值只影响之后新增的草稿；单条“更多选项”可覆盖活动和可报销。保存后在明细检查各笔关联。
4. 回到活动管理改名，明细和录入选择列表应同步更新；取消编辑不写入，编辑中旋转应保留草稿。
5. 删除有关联账目的活动应提示数量并阻止删除，账目保留；删除空活动应先确认，取消后保留。
   如确需删除关联活动，先在明细逐条修改关联，再回来删除。
6. 创建活动后立即打开记一笔 / 记多笔，确认旧保存提示不会遮挡按钮；展开活动选项、输入备注后仍能点击保存。
   切换深浅色、旋转手机、断网后检查上述流程。

活动改名和删除仍遵循 Stage 7 的关联保护规则。

## 明细与记账回归检查

本版同时保留 Stage 6 功能，可继续验证：

1. 打开明细页，确认以前保存的单笔和多笔账目都在，按日期倒序分组；同一天普通收入和报销到账分别汇总。
2. 新记支出 12.34 元后应立即出现在明细中；点击该记录，把金额改为 18.01 元、日期改为昨天并保存，
   确认仍是一条记录，已移到昨天分组，每日汇总随之更新。
3. 编辑另一条记录后直接返回，原记录不变；编辑中旋转手机，确认草稿保留。
4. 点击删除账目，再取消，记录仍在；再次删除并确认，仅该条记录消失，汇总更新。
5. 用日期范围筛选一天或两天，确认范围两端都包含；清除筛选显示全部，旋转后筛选保留。
6. 检查跨月记录、长备注、大金额、深浅色及键盘下保存 / 删除；断网后单笔和多笔录入仍正常。
7. 检查日期的主题色分组头、组间留白和每条记录的底色 / 边框是否容易区分；切换系统深浅色后也应清楚。

明细默认显示全部账目；支出显示负号，普通收入与报销到账显示正号并标明类型。
列表备注最多显示两行，完整备注可以在编辑表单查看。删除前必须确认，删除后没有回收站。
活动可在设置 → 活动管理中创建，随后在表单选择；新安装时活动列表为空，可选择“不关联活动”。
首页摘要、明细每日收支和统计页使用统一 FinancialAnalysis 口径。

## 导航检查步骤

安装 `app/build/outputs/apk/debug/app-debug.apk` 后：

1. 依次切换首页、明细、统计、助手、设置，确认页面内容和底部选中项一致。
2. 在首页、明细、统计或设置点击右下角记账按钮，确认显示“记一笔”和“记多笔”；取消或系统返回可关闭弹窗。助手页不显示记账 FAB。
3. 分别进入单笔和多笔表单，确认底部导航和 FAB 隐藏。
4. 使用页内返回按钮或系统返回，确认回到进入录入页前的一级页面。
5. 旋转手机，确认当前页面保持；在录入页旋转后仍可返回原页面。
6. 切换系统深浅色，确认文字、图标、底部导航和弹窗可读。

导航交互测试位于 `app/src/androidTest/java/com/example/myledger/NavigationTest.kt`。
有设备或模拟器时可执行 `gradlew.bat connectedDebugAndroidTest`；没有 ADB 时使用上述手动步骤。
