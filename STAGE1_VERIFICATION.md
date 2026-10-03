# Stage 1 — 导航与空页面

日期：2026-10-03。Stage 0 已由用户确认手机启动与系统深色模式正常。
本次仅实现 Stage 1，Stage 2 未开始。

## 实现范围

- 首页、明细、统计、设置四个一级页面及底部导航，默认首页。
- 选中项随当前页面变化；重复点击同一项不新增返回栈条目。
- 切换一级页面使用 saveState / restoreState；从其他一级页面系统返回到首页。
- 四个一级页面提供记账 FAB，弹出“记一笔 / 记多笔”选择框。
- 取消、点击框外或系统返回可关闭选择框。
- 两种录入入口进入各自占位页，隐藏底部导航和 FAB。
- 录入页内返回按钮和系统返回均使用 Navigation 返回栈，回到进入前的一级页面。
- Navigation 控制器和弹窗使用可恢复状态，支持 Activity 重建。
- 延续现有 Material 3 深浅色主题，图标随主题着色，内容避开系统栏。
- 页面仅显示空状态，没有数据库、金额、伪造统计、表单或保存逻辑。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| app/build.gradle.kts | 新增 Navigation Compose 2.10.2 |
| app/src/main/java/com/example/myledger/MainActivity.kt | 接入导航应用，保留主题和预览 |
| app/src/main/java/com/example/myledger/ui/navigation/LedgerDestination.kt | 六个静态页面及一级页面列表 |
| app/src/main/java/com/example/myledger/ui/navigation/MyLedgerApp.kt | NavHost、底部导航、FAB、弹窗及返回按钮 |
| app/src/main/java/com/example/myledger/ui/components/EmptyState.kt | 复用简洁空状态内容 |
| app/src/main/java/com/example/myledger/ui/home/HomeScreen.kt | 首页占位 |
| app/src/main/java/com/example/myledger/ui/records/RecordsScreen.kt | 明细占位 |
| app/src/main/java/com/example/myledger/ui/statistics/StatisticsScreen.kt | 统计占位 |
| app/src/main/java/com/example/myledger/ui/settings/SettingsScreen.kt | 设置占位 |
| app/src/main/java/com/example/myledger/ui/transaction/TransactionScreen.kt | 记一笔占位 |
| app/src/main/java/com/example/myledger/ui/batchentry/BatchDailyEntryScreen.kt | 记多笔占位 |
| app/src/main/res/values/strings.xml | 页面标题、空状态及无障碍文案 |
| app/src/main/res/drawable/ic_home.xml、ic_records.xml、ic_statistics.xml、ic_settings.xml、ic_add.xml、ic_arrow_back.xml | 六个小型矢量图标，返回图标支持 RTL 镜像 |
| app/src/androidTest/java/com/example/myledger/NavigationTest.kt | 四项导航设备交互测试 |
| README.md | 更新阶段状态和手动验收步骤 |
| STAGE0_WINDOWS_VERIFICATION.md | 补记用户确认的手机启动与深色模式验证 |
| STAGE1_VERIFICATION.md | 本阶段决策和验证结果 |

## 关键决策

只新增项目要求的 Navigation Compose 依赖，没有增加图标库、序列化插件、依赖注入框架或数据库。
使用六个无参数的静态路由，将导航集中在 ui/navigation，页面本身不持有 NavController。
NavHost 关闭页面切换动画，系统返回由 Navigation 处理，没有自建返回栈或拦截一级页面返回。

Navigation 2.10.2 是检查时的稳定版本，参考 [Android 官方版本说明](https://developer.android.com/jetpack/androidx/releases/navigation)。
基础 Version Catalog 保持原内容。新增导航依赖暂直接声明在 app/build.gradle.kts：
本次修改 Catalog 后，Gradle 生成 Java 访问器时，JDK 关闭 gradle-classloaders-9.6.0.jar
触发当前 Windows 沙箱的 AccessDeniedException，失败发生在应用编译之前。
保留原 Catalog 后工程正常构建；没有降级依赖、关闭校验或改变沙箱权限。
后续沙箱路径问题解决后，可将导航版本移回 Catalog。

## 实际验证

沿用 Stage 0 的 JBR 25、项目内 Gradle 缓存、代理和调试签名配置。
local.properties 仍指向原 Windows SDK。

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL；最终构建 12 秒，4 个任务执行 |
| .\gradlew.bat test | BUILD SUCCESSFUL；20 秒，1 个单元测试，0 失败、0 错误 |
| .\gradlew.bat assembleDebugAndroidTest lint | BUILD SUCCESSFUL；52 秒；设备测试 APK 编译通过，lint 无错误 |

Lint 保留 17 项警告：模板的较旧依赖、未使用颜色、冗余标签和 SDK 判断，以及新增导航版本应使用 Catalog 的建议。
没有为本阶段整体升级模板依赖或扩大清理范围。

APK：app/build/outputs/apk/debug/app-debug.apk，40,017,311 字节。
设备测试 APK：app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk。
日志：build/stage1-verification/assembleDebug.log、test.log、checks.log。
Lint 报告：app/build/reports/lint-results-debug.html。

NavigationTest 的四个测试覆盖：

1. 四个一级页面内容和底部选中项。
2. 两种录入入口、隐藏 FAB、页内返回和系统返回后的原页面。
3. 取消及系统返回关闭选择框，保留当前页面。
4. 在录入页重建 Activity 后保持页面，并返回原一级页面。

上述设备交互测试已编译，尚未执行。用户此前选择手动安装方式，未重新尝试 ADB。
Stage 1 的设备交互验收仍待用户按照 README 手动验证；不能将测试 APK 编译成功等同于设备测试通过。

## 手动验收

安装新的调试 APK，然后确认：四个页面可切换；两种录入占位页可进入；
页内与系统返回回到原页面；弹窗可取消；旋转后页面保持；深浅色可读。
完成手机验证后再进入 Stage 2。
