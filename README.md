# MyLedger

单用户、本地优先的 Android 个人记账 App。产品范围见
[PRODUCT_SPEC.md](PRODUCT_SPEC.md)，开发规则见 [AGENTS.md](AGENTS.md)，
分阶段计划见 [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)。

当前实现到 Stage 2：保留 Stage 1 导航与占位页，新增本地 Room 数据层、默认分类和 Repository。
真实记账界面与财务统计尚未实现。

Windows 接手检查结果见 [STAGE0_WINDOWS_VERIFICATION.md](STAGE0_WINDOWS_VERIFICATION.md)。
Stage 0 的 Windows 构建、单元测试和手机手动启动已通过。
Stage 1 的实现与验收记录见 [STAGE1_VERIFICATION.md](STAGE1_VERIFICATION.md)。
Stage 1 手机手动验证已由用户确认通过。
Stage 2 数据模型、测试及手机验证步骤见 [STAGE2_VERIFICATION.md](STAGE2_VERIFICATION.md)。

## 工程配置

- 单模块 `app`，包名和 applicationId：`com.example.myledger`。
- Kotlin、Jetpack Compose、Material 3、Gradle Kotlin DSL。
- 基础依赖版本集中在 `gradle/libs.versions.toml`，Compose 依赖使用 BOM。
- Navigation Compose 2.10.2 用于 Stage 1 导航，版本暂在 `app/build.gradle.kts` 声明；
  当前 Windows 沙箱无法完成修改 Catalog 后的 Java 访问器编译，详见 Stage 1 验收记录。
- Room 2.8.5、KSP 2.3.12；数据库版本 1，schema JSON 纳入版本控制。
- Robolectric 4.17 仅用于 JVM 数据库测试，不进入 APK。
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

## Stage 2 手机验证

从 GitHub Release 下载 Stage 2 APK，直接覆盖安装已安装的 Stage 1，然后：

1. 首次打开、退出并重新打开，确认没有闪退或卡在启动页。
2. 离线打开，确认不需要网络或登录。
3. 按下面的导航检查步骤确认原有功能正常。

Stage 2 是数据层阶段，界面仍为占位页；暂时不能在手机上新增账目或查看分类。
数据库增删改查、初始化、批量回滚、Flow 更新、金额与日期精度、重开持久化由
`LedgerDatabaseTest` 在 Windows JVM 上运行真实 Room / 原生 SQLite 验证。
手机验证用于检查安装、启动与导航回归。

## 导航检查步骤

安装 `app/build/outputs/apk/debug/app-debug.apk` 后：

1. 依次切换首页、明细、统计、设置，确认页面内容和底部选中项一致。
2. 点击右下角记账按钮，确认显示“记一笔”和“记多笔”；取消或系统返回可关闭弹窗。
3. 分别进入两种录入占位页，确认底部导航和 FAB 隐藏。
4. 使用页内返回按钮或系统返回，确认回到进入录入页前的一级页面。
5. 旋转手机，确认当前页面保持；在录入页旋转后仍可返回原页面。
6. 切换系统深浅色，确认文字、图标、底部导航和弹窗可读。

导航交互测试位于 `app/src/androidTest/java/com/example/myledger/NavigationTest.kt`。
有设备或模拟器时可执行 `gradlew.bat connectedDebugAndroidTest`；没有 ADB 时使用上述手动步骤。
