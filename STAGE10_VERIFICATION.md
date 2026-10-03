# Stage 10 — 设置与 DataStore

用户授权连续完成 Stage 10–12，最后统一手机验收。本阶段通过后继续 Stage 11。

- 新增 data/settings/AppSettings.kt、SettingsRepository.kt、ui/settings/SettingsViewModel.kt；设置页面改为真实设置。
- 采用 [官方稳定 DataStore 1.2.1](https://developer.android.com/jetpack/androidx/releases/datastore)，应用级单实例，主题跟随系统 / 浅色 / 深色持久化。
- MainActivity 生命周期感知收集设置，主题和系统栏明暗随之更新。人民币固定为 CNY，显示实际应用版本和本地账本说明。
- 活动管理保留；备份 / 恢复入口在本阶段占位，Stage 11 接入。版本 0.1.0-stage10 / versionCode 11。
- 通过 PackageManager 读取版本，无需 BuildConfig 生成 Java；解决 Windows 沙箱 Java 编译缓存访问限制。
- 新增 6 项测试：真实 DataStore 默认值、磁盘重开、未知主题回退；双观察者刷新、读 / 写失败与重试；真实 MainActivity 主题选择、旋转与导航。
- .\gradlew.bat assembleDebug：通过，20 秒；test：通过，50 秒，141 项、0 失败 / 错误 / 跳过；lint：通过，40 秒。
- 查看 app/build/stage10-ui/settings-light.png、settings-dark.png，411×600dp，系统深色下可强制浅色 / 深色。
- 数据库 schema v1、账目、财务口径不变；仅增加计划要求的 DataStore 依赖。

日志位于 build/stage10-verification。本阶段不要求手机验收；最终 APK 和完整手机清单随 Stage 12 发布。
