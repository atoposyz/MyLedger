# Stage 6.1 — 明细可读性调整

2026-10-03。用户反馈不同天、不同条目区分度低，并允许用克制的颜色区分。
立即调整 Stage 6 明细展示；没有进入 Stage 7。

## 改动

| 文件 | 改动 |
| --- | --- |
| app/src/main/java/com/example/myledger/ui/records/RecordsScreen.kt | 日期使用 primaryContainer / onPrimaryContainer 的主题色底衬，日期加粗；日期组之间增加 24dp 留白。每笔使用 surfaceContainerLow 底色、1dp outlineVariant 边框、8dp 间距和 12dp 内边距；分类与金额加粗，类型使用次级文字色 |
| app/src/test/java/com/example/myledger/ui/records/RecordsScreenTest.kt | 复用既有交互测试，增加同一多日列表切换深色主题的截图；保留长备注、大金额、摘要和筛选断言 |
| app/build.gradle.kts | versionCode 7、versionName 0.1.0-stage6.1；无新增依赖 |
| README.md、本文件 | 新 APK 下载入口、调整说明与手机验证步骤 |

使用统一 Material 3 色彩角色，跟随现有系统深浅色和动态配色，没有随机日期颜色、渐变或阴影。
保留整条记录的点击区域、日期 heading 语义和独立 LazyColumn 条目。
统计口径、日期分组算法、Repository、数据库 schema v1 和已有账目均未修改。

## Windows 验证

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，16 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，41 秒；76 项测试，0 失败 / 错误 / 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL，19 秒；lint 0 错误、24 条原有警告；设备测试 APK 编译通过，未运行实体设备测试 |

查看原生渲染截图 app/build/stage6-ui/records-light.png、records-dark.png、records-dark-large.png：
两个日期组边界清楚；普通收入、报销、支出与金额可读，长备注截断和大金额展示正常。
固定主题截图用于验证深浅色；手机系统动态配色效果仍需手动检查。
Stage 6 既有 Robolectric 短屏触摸注入限制见 STAGE6_VERIFICATION.md，不能将自动测试视为手机验收。

日志位于 build/stage6-readability-verification/assembleDebug.log、test.log、checks.log。
报告位于 app/build/reports/tests/testDebugUnitTest/index.html、app/build/reports/lint-results-debug.html。

APK：30,985,706 字节；Android API 36 及以上。
SHA-256：24ad14e3fbb18e3ff13b2f14ef4bef3cee290552c94b8b3ba7d60d972456e704。
apksigner 验证通过，签名与已发布版本一致，可覆盖安装保留数据；没有 INTERNET 权限。

## 手机验证

从 [Stage 6.1 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage6.1)
下载 MyLedger-stage6.1-debug.apk，直接覆盖安装。

1. 打开有至少两天、多条账目的明细，检查日期色块、组间留白和单条边框是否容易辨认。
2. 切换系统深浅色，检查日期、金额、备注和边框；系统动态配色也应保持清楚、克制。
3. 点击条目，确认仍可编辑；保存后列表更新，旧账目和原有汇总正常。

尚未完成的 Stage 6 手机检查继续按 README.md 执行。本次等待手机反馈，不自动进入 Stage 7。
