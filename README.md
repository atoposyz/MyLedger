# MyLedger

单用户、本地优先的 Android 个人记账 App。产品范围见
[PRODUCT_SPEC.md](PRODUCT_SPEC.md)，开发规则见 [AGENTS.md](AGENTS.md)，
分阶段计划见 [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)。

当前实现到 Stage 5：具备本地 Room 数据层、统一财务分析层，以及真实的“记一笔”和“记多笔”表单。
首页摘要、明细列表和统计界面尚未实现。

Windows 接手检查结果见 [STAGE0_WINDOWS_VERIFICATION.md](STAGE0_WINDOWS_VERIFICATION.md)。
Stage 0 的 Windows 构建、单元测试和手机手动启动已通过。
Stage 1 的实现与验收记录见 [STAGE1_VERIFICATION.md](STAGE1_VERIFICATION.md)。
Stage 1 手机手动验证已由用户确认通过。
Stage 2 数据模型、测试及手机验证步骤见 [STAGE2_VERIFICATION.md](STAGE2_VERIFICATION.md)。
Stage 3 统计口径与测试见 [STAGE3_VERIFICATION.md](STAGE3_VERIFICATION.md)。
Stage 4 单笔录入、43 项测试及手机步骤见 [STAGE4_VERIFICATION.md](STAGE4_VERIFICATION.md)。
Stage 5 多笔录入、事务保存与手机步骤见 [STAGE5_VERIFICATION.md](STAGE5_VERIFICATION.md)。

## 工程配置

- 单模块 `app`，包名和 applicationId：`com.example.myledger`。
- Kotlin、Jetpack Compose、Material 3、Gradle Kotlin DSL。
- 基础依赖版本集中在 `gradle/libs.versions.toml`，Compose 依赖使用 BOM。
- Navigation Compose 2.10.2 用于 Stage 1 导航，版本暂在 `app/build.gradle.kts` 声明；
  当前 Windows 沙箱无法完成修改 Catalog 后的 Java 访问器编译，详见 Stage 1 验收记录。
- Room 2.8.5、KSP 2.3.12；数据库版本 1，schema JSON 纳入版本控制。
- Lifecycle 2.11.0 用于 ViewModel、SavedStateHandle 和生命周期感知的 StateFlow 收集。
- Robolectric 4.17 用于 JVM 数据库及 Compose 交互测试，不进入 APK。
- 保留模板构建版本：AGP 9.4.1、Gradle 9.6.0、Kotlin Compose 插件 2.2.10。
- `compileSdk` / `targetSdk`：37；`minSdk`：36。
- Gradle daemon 的 JDK 版本由 `gradle/gradle-daemon-jvm.properties` 指定为 25；
  Java 源码及字节码兼容目标保留为 11。
- Material 3 主题跟随系统深浅色，支持动态颜色，提供固定配色的深浅色预览。
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

## Stage 5 手机验证

从 [Stage 5 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage5)
下载 MyLedger-stage5-debug.apk，直接覆盖安装旧版本，保留应用数据，然后：

1. 从统计页进入“记多笔”，依次添加 10 笔金额 1.01、2.01 … 10.01 元，确认合计 55.10 元；
   全部保存后返回统计页，并显示“已保存 10 笔 · 录入合计 ¥55.10”。
2. 先移除初始草稿，顶部日期设为昨天，再添加两笔；确认都继承昨天。
   一笔通过“更多选项”改为前天，另一笔仍是昨天。更改默认值不会修改已有草稿。
3. 第一笔选择“交通”，添加下一笔后确认继承分类；移除一笔，数量与合计随之更新。
4. 一笔有效金额、另一笔 0 或 12.345，全部保存后停留表单并滚到错误行；修正后再保存。
5. 填写多笔后旋转手机，确认金额、分类、备注和逐条选项保留；弹出键盘后能滚动、添加并保存。
6. 检查普通收入 / 报销分类、深浅色和断网保存；同时确认“记一笔”仍正常。

保存成功后账目已经写入本机 Room；明细列表在 Stage 6 实现，现在通过保存提示验证手机流程。
已有活动可在表单选择，活动创建在 Stage 7 实现；新安装时活动列表为空，可选择“不关联活动”。
默认可报销只在默认活动属于公务 / 科研时显示；单笔支出仍可以独立标记可报销。
Windows 自动测试涵盖十笔独立写入、失败零写入、默认值与覆盖、重复保存保护、草稿恢复和财务口径；
不等同于实体手机测试。完成 Stage 5 后停在手机验证，不自动进入 Stage 6。

## 导航检查步骤

安装 `app/build/outputs/apk/debug/app-debug.apk` 后：

1. 依次切换首页、明细、统计、设置，确认页面内容和底部选中项一致。
2. 点击右下角记账按钮，确认显示“记一笔”和“记多笔”；取消或系统返回可关闭弹窗。
3. 分别进入单笔和多笔表单，确认底部导航和 FAB 隐藏。
4. 使用页内返回按钮或系统返回，确认回到进入录入页前的一级页面。
5. 旋转手机，确认当前页面保持；在录入页旋转后仍可返回原页面。
6. 切换系统深浅色，确认文字、图标、底部导航和弹窗可读。

导航交互测试位于 `app/src/androidTest/java/com/example/myledger/NavigationTest.kt`。
有设备或模拟器时可执行 `gradlew.bat connectedDebugAndroidTest`；没有 ADB 时使用上述手动步骤。
