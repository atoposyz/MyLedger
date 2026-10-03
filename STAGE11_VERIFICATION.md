# Stage 11 — 本地备份与恢复

2026-10-04，按用户要求连续开发到 Stage 12，手机验收统一安排在最后。

## 实现

- 新增 backup/BackupCodec.kt、BackupManager.kt、ui/backup/BackupViewModel.kt、BackupScreen.kt；设置进入备份页面。通过系统选择器保存/读取 ZIP，FileProvider 分享。
- LedgerSnapshot.kt 校验数据，Repository 与两个 DAO 提供一致快照和单个 Room 事务替换。保留 ID、金额分、业务日期、关联和审计时间，数据库仍为版本 1，未改变 schema。
- 导入先私有暂存与校验，显示时间、版本、账目/活动数量，二次确认才替换。恢复前保存一份可再次确认恢复的私有副本。格式与限制见 [BACKUP_FORMAT.md](BACKUP_FORMAT.md)。
- 保存、读取、校验、写入失败有明确提示。操作中禁用重复点击及返回；预览和确认支持旋转。关闭系统自动备份路径，应用无联网权限。

## 验证

- Windows gradlew.bat assembleDebug 通过（13 秒）；gradlew.bat test 通过（49 秒），160 项测试，无跳过。
- 新增 19 项：格式往返/Long 最大金额/长备注/闰日/损坏与无效关联/ZIP 结构及限制，真实 Room + DataStore 恢复与回滚，系统文件选择器回调、content URI 分享授权、恢复确认取消、旋转、恢复前副本及首页刷新。
- Windows JVM 的 FileProvider 路径分隔符由测试专用 WindowsFileProviderPaths 适配，保留真实 Provider、读写流与 URI 授权；Android 实现未改动。
- 修正旧日期测试跨午夜后遇到日历“今天”语义提示的匹配方式，保留真实日期点击与结果断言。
- gradlew.bat lint 通过（23 秒），0 errors、26 warnings（主要为既有模板资源及版本 Catalog 建议）；分享标题改用 stringResource，修复配置变化后的资源读取问题。系统文件应用、分享目标以及真实键盘交互由最终手机清单补充。

## 限制

备份未加密，无远程备份、自动备份或历史列表；私有恢复前副本不能代替外部保存的备份。Room 与 DataStore 的跨存储断电一致性不作保证；正常写入异常会回滚或补偿恢复。

文件交互采用 [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) 和 [FileProvider](https://developer.android.com/training/secure-file-sharing/share-file)。
