# Stage 2 — Room 数据层

日期：2026-10-03。用户已确认 Stage 1 手机手动验证通过，并授权完成下一阶段后发布 GitHub。
本次仅实现 Stage 2；未开始 Stage 3 财务分析，也未实现真实录入界面。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| app/build.gradle.kts | Room / KSP、Coroutines、Robolectric、schema 导出和 JVM 测试配置；版本 0.1.0-stage2 / versionCode 2 |
| app/src/main/java/com/example/myledger/data/local/entity/TransactionEntity.kt、TransactionType.kt | 账目模型与支出 / 普通收入 / 报销到账类型 |
| app/src/main/java/com/example/myledger/data/local/entity/CategoryEntity.kt | 固定分类模型 |
| app/src/main/java/com/example/myledger/data/local/entity/ActivityEntity.kt、ActivityType.kt | 个人 / 公务科研活动模型 |
| app/src/main/java/com/example/myledger/data/local/dao/TransactionDao.kt、CategoryDao.kt、ActivityDao.kt | suspend 读写及 Flow 查询 |
| app/src/main/java/com/example/myledger/data/local/AppDatabase.kt | 数据库 v1、原子创建与分类初始化 |
| app/src/main/java/com/example/myledger/data/local/DefaultCategories.kt | 产品规定的 17 个分类及永久 ID |
| app/src/main/java/com/example/myledger/data/local/LedgerConverters.kt | 集中的 LocalDate / 枚举存储转换 |
| app/src/main/java/com/example/myledger/data/repository/LedgerRepository.kt | 查询、写入校验、时间戳和批量事务 |
| app/src/main/java/com/example/myledger/LedgerApplication.kt | 应用级数据库 / Repository 单实例，手动装配 |
| app/src/main/java/com/example/myledger/MainActivity.kt | lifecycleScope 异步初始化；Composable 不访问 DAO |
| app/src/main/AndroidManifest.xml、app/src/main/res/xml/backup_rules.xml、data_extraction_rules.xml | 注册 Application，关闭系统自动备份并排除数据库转移 |
| app/schemas/com.example.myledger.data.local.AppDatabase/1.json | 初始 schema 快照，后续 migration 的版本基线 |
| app/src/test/java/com/example/myledger/data/LedgerDatabaseTest.kt | 14 项真实 Room / 原生 SQLite 数据层测试 |
| README.md、STAGE1_VERIFICATION.md、STAGE2_VERIFICATION.md | 阶段状态、验收记录与手机检查步骤 |

## 数据模型与决策

- 金额 amountMinor 是 Long；人民币分为单位，1234 表示 ¥12.34。数据库列为 INTEGER。
- 账目 date 是调用方选择的 LocalDate，保存为数值 epoch day；日期范围包含两端，按业务日期和 ID 倒序。
- createdAt / updatedAt 是 epoch milliseconds；Repository 使用可注入 Clock 写入，编辑保留 createdAt。
- EXPENSE、INCOME、REIMBURSEMENT 存为枚举名称；“报销”分类仅属于 REIMBURSEMENT，不混入普通收入分类。
- 分类固定 ID 1–17，共 11 个支出分类、5 个普通收入分类、1 个报销分类。
- 数据库创建回调在 Room 建库事务内种入分类，完成后才允许 DAO 查询；重开不重新插入。
- 分类外键和活动外键均为 RESTRICT。有账目的活动不能直接删除，账目不会级联丢失。
- Repository 校验正金额、分类与类型匹配、关联活动存在、活动名称和日期范围。
- 单次批量保存使用数据库事务，每条仍是独立 TransactionEntity，没有 BatchEntity。
- 本阶段首次建立数据库，schema 版本为 1，无旧 Room schema 需要迁移；未使用 destructive migration。
- 未新增网络权限、同步字段、DI 框架或分析算法。默认禁止系统把账本自动上传云备份；明确的本地备份恢复留待 Stage 11。

依赖采用稳定版本：[Room 2.8.5](https://developer.android.com/jetpack/androidx/releases/room)、
[KSP 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12)、
[Coroutines 1.11.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0)。
[Robolectric 4.17](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17)
仅用于测试，未打包进 App。基础 Version Catalog 沿用 Stage 1 的沙箱处理，新增版本直接声明在 app/build.gradle.kts。

## Windows 实际验证

沿用 Stage 0 的 JBR 25、项目内 Gradle 缓存、代理和调试签名，SDK 仍是原 Windows SDK。
所有需要的依赖和 Android 测试运行包已实际下载完成。

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | BUILD SUCCESSFUL，最终 29 秒 |
| .\gradlew.bat test | BUILD SUCCESSFUL，最终 27 秒；15 项测试，0 失败、0 错误、0 跳过 |
| .\gradlew.bat lint assembleDebugAndroidTest | BUILD SUCCESSFUL，21 秒；lint 0 错误；设备测试 APK 编译通过 |

14 项数据层测试使用 Robolectric 的 Android API 36 环境和原生 SQLite，覆盖：

1. 首次查询分类完整，反复初始化不重复，报销分类独立。
2. DAO 账目所有字段插入、修改、查询、删除。
3. 超过 Double 精确整数范围的 Long 金额仍精确往返。
4. 业务日期与保存时间独立，编辑保留创建时间。
5. 跨月查询、日期边界、倒序、同日排序、空范围和非法日期范围。
6. 同一 Flow 订阅收到插入、更新、删除后的结果。
7. DAO 批量插入因外键失败时全部回滚。
8. 一次保存 10 条独立账目，非法批次不留下部分数据。
9. 普通收入与报销到账保留不同类型。
10. 非法金额、分类、活动和不存在的编辑目标不写入数据。
11. 活动类型、可空起止日期、备注与 DAO 增删改查。
12. 禁止删除关联活动；解除关联后删除活动，账目仍保留。
13. 活动名称与日期校验、Repository 编辑和删除。
14. 关闭并重开磁盘数据库后账目、活动和 17 个分类保持。

另有原模板的 1 项单元测试通过。Stage 1 导航设备测试只编译，未执行；Stage 2 手机启动仍需用户手动验证。
Lint 保留模板旧依赖、无用颜色、冗余标签、SDK 判断及直接声明版本的建议，没有扩大本阶段清理范围。

首次测试遇到两个环境问题并已处理：

- Robolectric 测试进程不继承 Gradle daemon 的代理属性。在当前终端 JAVA_TOOL_OPTIONS 中传入 HTTP / HTTPS 代理，下载成功。
- 原生运行库加载字体资源时，JDK ZipFileSystem.toRealPath 被当前 Windows 沙箱拒绝。
  使用 Python 解压下载的原始 nativeruntime-dist-compat JAR 到 build/stage2-verification/native-runtime，
  项目内 .gradle-user-home/init.d/stage2-native-runtime.gradle 将测试 classpath 的该资源 JAR 替换为解压目录。
  所有原始资源和原生 SQLite DLL 保留；未修改依赖实现、mock DAO 或跳过测试。
  这两个配置只存在于忽略的本机目录，普通 Windows 环境无需这项解压处理。

日志：build/stage2-verification/assembleDebug.log、test.log、checks.log。
测试报告：app/build/reports/tests/testDebugUnitTest/index.html。
Lint 报告：app/build/reports/lint-results-debug.html。

APK：app/build/outputs/apk/debug/app-debug.apk，48,495,098 字节。
SHA-256：aee8d83f0b9c707d92da549386d795f4a1c65995461846e332a243e99c72a36b。
应用版本：0.1.0-stage2，versionCode 2，minSdk 36。

## 手机手动验证

从 [Stage 2 Release](https://github.com/atoposyz/MyLedger/releases/tag/v0.1.0-stage2)
下载 MyLedger-stage2-debug.apk，覆盖安装 Stage 1，保留原应用数据，然后检查：

1. 首次打开停留几秒，无闪退、无启动卡住。
2. 从最近任务关闭，重新打开；再强制停止后打开，均正常。
3. 断网后启动，正常进入首页，不需要登录。
4. 首页、明细、统计、设置可切换；两种记账入口可进入，返回回到原页面，弹窗可取消。
5. 系统深浅色下文字、图标和弹窗可读；旋转后原有导航仍正常。

本阶段没有新的可录入表单，首页和明细仍为空状态，这是预期行为。
分类与数据库内容暂未展示，增删改查由上述自动测试验收。
用户随后确认认可本阶段以数据层测试验收，并授权 Stage 3 验证通过后自动进入 Stage 4。
本阶段手机检查未另外记录结果。
