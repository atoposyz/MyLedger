# MyLedger

单用户、本地优先的 Android 个人记账 App。产品范围见
[PRODUCT_SPEC.md](PRODUCT_SPEC.md)，开发规则见 [AGENTS.md](AGENTS.md)，
分阶段计划见 [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)。

当前实现到 Stage 4：具备本地 Room 数据层、统一财务分析层，以及真实的“记一笔”表单。
首页摘要、明细列表、统计界面和“记多笔”尚未实现。

Windows 接手检查结果见 [STAGE0_WINDOWS_VERIFICATION.md](STAGE0_WINDOWS_VERIFICATION.md)。
Stage 0 的 Windows 构建、单元测试和手机手动启动已通过。
Stage 1 的实现与验收记录见 [STAGE1_VERIFICATION.md](STAGE1_VERIFICATION.md)。
Stage 1 手机手动验证已由用户确认通过。
Stage 2 数据模型、测试及手机验证步骤见 [STAGE2_VERIFICATION.md](STAGE2_VERIFICATION.md)。
Stage 3 统计口径与测试见 [STAGE3_VERIFICATION.md](STAGE3_VERIFICATION.md)。
Stage 4 单笔录入、43 项测试及手机步骤见 [STAGE4_VERIFICATION.md](STAGE4_VERIFICATION.md)。

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

## Stage 4 手机验证

从 [Stage 4 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage4)
下载 MyLedger-stage4-debug.apk，直接覆盖安装旧版本，保留应用数据，然后：

1. 在任意一级页面点击记账按钮 → 记一笔，输入支出 12.34 元，选“餐饮”、改为昨天、填写备注后保存。
   确认返回原页面，并显示包含 ¥12.34、餐饮和所选日期的“已保存”提示。
2. 分别录入普通收入和报销到账，确认普通收入有五个收入分类，“报销”仅有报销分类。
3. 尝试空金额、0、12.345、超出整数范围的金额，确认显示错误，不退出表单。
4. 勾选支出的“可报销”，切换收入或报销后确认该开关隐藏；填写中旋转手机，确认草稿保留。
5. 弹出键盘后仍能滚动到备注和保存按钮；检查日期弹窗取消、深浅色和离线保存。

保存成功后账目已经写入本机 Room；明细列表在 Stage 6 实现，现在通过保存提示验证手机流程。
已有活动可在表单选择，活动创建在 Stage 7 实现；新安装时活动列表为空，可选择“不关联活动”。
Windows 上的 43 项测试包含真实 MainActivity → 表单 → ViewModel → Repository → Room 保存、
返回原页、金额精度、日期、草稿恢复、数据库重开持久化和财务口径；不等同于实体手机测试。

## 导航检查步骤

安装 `app/build/outputs/apk/debug/app-debug.apk` 后：

1. 依次切换首页、明细、统计、设置，确认页面内容和底部选中项一致。
2. 点击右下角记账按钮，确认显示“记一笔”和“记多笔”；取消或系统返回可关闭弹窗。
3. 分别进入单笔表单和多笔占位页，确认底部导航和 FAB 隐藏。
4. 使用页内返回按钮或系统返回，确认回到进入录入页前的一级页面。
5. 旋转手机，确认当前页面保持；在录入页旋转后仍可返回原页面。
6. 切换系统深浅色，确认文字、图标、底部导航和弹窗可读。

导航交互测试位于 `app/src/androidTest/java/com/example/myledger/NavigationTest.kt`。
有设备或模拟器时可执行 `gradlew.bat connectedDebugAndroidTest`；没有 ADB 时使用上述手动步骤。
