# Stage 0 Windows 接手检查

检查日期：2026-10-03。仅检查 Stage 0，没有开始 Stage 1。

## 当前结果

Windows 沙箱内代理网络可用，缺失依赖已完成下载。
`gradlew.bat assembleDebug` 和 `gradlew.bat test` 均已实际执行并通过。

- APK：`app/build/outputs/apk/debug/app-debug.apk`，29,533,546 字节。
- 单元测试：ExampleUnitTest，1 个测试，0 失败、0 错误、0 跳过。
- 未执行设备测试、模拟器启动或 UI 运行验证。
- 最新构建日志：`build/windows-verification/assembleDebug.log`。
- 最新测试日志：`build/windows-verification/test.log`。
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。

## 现有实现

- 已阅读 AGENTS.md、PRODUCT_SPEC.md、DEVELOPMENT_PLAN.md。
- 单模块 app，Kotlin、Compose、Material 3、Gradle Kotlin DSL 和 Version Catalog 已配置。
- MainActivity 仅显示应用名称，使用 edge-to-edge 和 Scaffold 内容内边距。
- 基础主题支持系统深浅色、动态颜色以及固定配色的深浅色预览。
- 没有导航、数据库或真实记账功能，符合 Stage 0 范围。
- 单元测试仍为模板的加法测试，没有添加业务测试或业务功能。
- 当前目录没有 .git，无法通过 git status 核对版本控制差异。

## Windows 环境

| 项目 | 实际检查结果 |
| --- | --- |
| 系统 | Windows 11 amd64；使用 Windows PowerShell 和 gradlew.bat |
| Android Studio | 已安装；product-info.json 标识 2026.2.1 / AI-262.9437.185.2621.16467767 |
| Studio JDK | JBR 25.0.3，已用于构建和测试 |
| 默认 PATH Java | Oracle JDK 23.0.1；本次显式设置 JAVA_HOME 使用 JBR 25 |
| Gradle | Wrapper JAR 存在；Gradle 9.6.0 可运行，工程要求 daemon 使用 Java 25 |
| Android SDK | local.properties 已恢复为原来的 Windows SDK 路径 |
| 平台 | android-37.0；API 37.0、PreviewSdkInt=0；android.jar 存在 |
| Build Tools | 36.0.0；aapt2.exe、apksigner.bat、zipalign.exe 存在 |
| SDK 许可 | android-sdk-license 存在；临时 SDK 安装 Platform Tools 时许可检查成功 |
| Platform Tools / Emulator | 原 SDK 的 adb.exe、emulator.exe 存在，未完成设备运行验证 |
| SDK Command-line Tools | 原 SDK 未安装 cmdline-tools，不影响本次构建与测试 |
| 依赖 | 所需 AAPT2、JUnit 等依赖已下载到项目内的 Gradle 缓存 |

## 网络实测与修复

本次执行环境提供 HTTP_PROXY / HTTPS_PROXY / ALL_PROXY，指向本地代理。
使用 JBR 25 的 Java HttpClient，显式使用 HTTPS_PROXY，并保持正常 TLS 校验，实测：

| 请求 | 结果 |
| --- | --- |
| Google Maven：aapt2-9.4.1-15978811.pom | HTTP 200，1,096 字节 |
| Maven Central：junit-4.13.2.pom | HTTP 200，27,018 字节 |
| Gradle：gradle-9.6.0-bin.zip.sha256 | HTTP 200，64 字节 |

PowerShell Invoke-WebRequest 和系统 curl 使用 Windows TLS 时出现 SEC_E_NO_CREDENTIALS，
但 Java 的代理请求成功，不能再将其概括为“沙箱网络完全不可用”。
Gradle 直接连接仍报 Permission denied: getsockopt；为本机 Gradle 配置 Java 的
http.proxyHost/http.proxyPort/https.proxyHost/https.proxyPort 后，依赖下载成功。
代理配置保存在被忽略的 `.gradle-user-home/gradle.properties`，没有修改工程仓库地址或依赖版本。

## 沙箱文件权限处理

当前沙箱中的 Java user.home 默认被解析为 C:\，因此使用项目内缓存与 Android 用户目录：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $PWD 'build/android-user-home'
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$env:GRADLE_USER_HOME"
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

网络与依赖解析修复后，AGP 默认调试签名流程在 debug.keystore.lock 上报拒绝访问。
Java 诊断确认：工作区文件可以读取、写入和加锁，但 Path.toRealPath 报 AccessDeniedException；
JDK 17、23、25 的诊断结果一致。检查 AGP 已缓存源码后，定位到默认签名的锁文件规范化流程。

使用 JBR keytool 在 `build/android-user-home/sandbox-debug.keystore` 创建仅用于本地调试的签名文件，
通过本机 Gradle 的 android.injected.signing.* 属性使用该文件，避免默认签名创建流程。
相关配置只保存在 `.gradle-user-home/gradle.properties`；没有跳过签名验证或任何构建、测试任务。

诊断期间曾复制平台、Build Tools 和许可到 `build/android-sdk`，临时设置 local.properties 指向该目录。
该 SDK 副本自动下载了 Platform Tools 37.0.1。首次完成 Kotlin 编译、APK 打包及单元测试时使用了此副本：

- assembleDebug：BUILD SUCCESSFUL，32 秒；11 个任务执行、25 个任务已是最新。
- test：BUILD SUCCESSFUL，23 秒；6 个任务执行、18 个任务已是最新，testDebugUnitTest 实际执行。

随后恢复了原 local.properties，并重新运行两个原始命令：

- assembleDebug：BUILD SUCCESSFUL，9 秒；36 个任务已是最新。
- test：BUILD SUCCESSFUL，9 秒；24 个任务已是最新。

最终 local.properties 仍指向 `C:\Users\Xuanfly\AppData\Local\Android\Sdk`。
没有修改原 SDK、系统环境、应用代码、依赖版本或工程构建脚本；所有本机缓存、签名和诊断文件均位于被忽略的目录。

## 修改与验证边界

本次更新 README.md 和本检查报告，纠正之前的网络阻塞结论，记录实际成功结果。
此前 .gitignore 已忽略 `.gradle-user-home`、`.android` 和根目录 build。

Stage 0 的构建与单元测试验收已通过。随后用户手动安装并确认：启动后显示 MyLedger，深色模式与系统一致。
设备自动化测试未执行；后续 Stage 1 的状态单独记录在 STAGE1_VERIFICATION.md。

## ADB 设备验证（2026-10-03）

已使用原 Windows SDK 的 platform-tools/adb.exe 尝试：

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.myledger/.MainActivity
```

三个命令均在 ADB 初始化阶段退出：`Cannot mkdir '\.android': Permission denied`。
将 ANDROID_SDK_HOME 和 ANDROID_USER_HOME 指向工作区没有解决此版本 ADB 的目录解析问题。
尝试连接已有 ADB 服务的 127.0.0.1:5037，返回 ConnectionRefusedError / WinError 10061。
未得到设备列表，APK 未完成安装，MainActivity 未完成启动；不能据此判定应用运行失败。
需要普通 Windows 终端启动 ADB 服务并连接设备后继续验证。
