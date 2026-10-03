# Stage 12 — UI 收尾

2026-10-04。按用户授权完成 Stage 10 → Stage 11 → Stage 12，各阶段检查通过后继续；最后统一发布，手机测试不作为阶段之间的暂停点。

## 最终行为与改动

- Theme.kt 与启动背景统一为克制的蓝灰浅色/深色配色；日期标题、条目背景、边界和间距形成清晰层级。收入与报销金额使用同一强调色，并保留正负号和类型文字。移除闲置模板 Color.kt。
- components/RecordAmountRow.kt 用可用宽度和文字测量决定分类/金额同行或分行；首页最近记录和明细共享。components/AmountText.kt 保持金额一行，超宽时调整字号（最低 12 sp），极窄情况下支持横向查看；不截断金额、不拆开负号和小数。首页与统计的大金额也使用该组件。布局浮点值仅用于测量和字号，财务金额仍是 Long。
- TransactionScreen.kt 选择分类/活动、保存和删除确认前清除输入焦点并收起键盘；BatchDailyEntryScreen.kt 新增行和展开选项时也收起键盘，保持新增行自动滚动。
- EntryDialogs.kt 在高度不足 480 dp 时默认使用稳定 Material 3 日期输入模式，竖屏仍用日历，日期转换继续统一按自然日处理。
- SettingsScreen.kt、BackupScreen.kt 限制最大内容宽度并居中；主题单选行垂直对齐。已有空状态、输入错误、长备注预览、编辑完整备注和安全区域逻辑保留。
- app/build.gradle.kts 更新版本号 13 / 0.1.0-stage12；未增加依赖、数据库字段或统计口径。
- README.md 更新当前功能、下载与验证入口；[PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md) 提供全部功能的一次性手机清单。

## 自动验证

Windows 最终检查均通过：

| 命令 | 结果 |
| --- | --- |
| gradlew.bat assembleDebug | BUILD SUCCESSFUL，10 秒 |
| gradlew.bat test | BUILD SUCCESSFUL，49 秒；164 项，失败/错误/跳过均为 0 |
| gradlew.bat lint | BUILD SUCCESSFUL，22 秒；0 errors、26 warnings |
| gradlew.bat assembleDebugAndroidTest | BUILD SUCCESSFUL，10 秒；仅编译设备测试，不声称已在手机执行 |

lint 提示主要是模板旧资源、Catalog 版本声明和可升级依赖；没有关闭 lint 或忽略错误来通过检查。新增 FinalLayoutTest.kt、FinalAppTest.kt，使用 Android API 36 的 Robolectric 原生绘制：

- 320 dp 窄屏、1.5 倍字体、深色模式：Long 最大金额保持精确单行，分类可读，长备注限两行预览，条目可打开。
- 640 × 360 dp 横屏、1.3 倍字体：长备注表单可滚动、选分类和保存，输入焦点在选择/保存后清除。
- 横屏日期输入确认得到 2026-10-05，不误用保存时刻或时区换算日期。
- 实际 MainActivity 横屏四页导航和生成备份可达；横屏日期弹窗可用；切换竖屏配置并重建 Activity，金额与长备注草稿保留，保存按钮可达。
- 继续运行前面各阶段的真实 Room、DataStore、财务分析和端到端交互回归。

原生截图位于 app/build/stage12-ui；通过截图发现并修复极大金额原先把负号及小数拆行的问题。Dialog 截图使用其自身 Window，避免只捕获 Activity 背景。

## 手机验证范围

自动化检查不替代设备上的系统键盘、文件应用和分享目标联调。最终 APK 保持既有签名，覆盖安装保留数据；手机清单优先外部保存原始备份，再测试恢复，最后可恢复原账本。当前版本仍标记为预发布，待用户整体验收。

APK 检查：applicationId 为 com.example.myledger，versionCode 13，minSdk 36、targetSdk 37，无 INTERNET 权限。apksigner verify 通过，签名证书 SHA-256 为 f715b909f3dfc869b13aa34aa769b71a333978590e5c9705f34ee521b8d047b2，与既有版本一致。文件大小 34,456,763 bytes；APK SHA-256：d70ab385517a6ddc65cd9cc3188688d911eb8db0424394dc53a91d729d910b52。
