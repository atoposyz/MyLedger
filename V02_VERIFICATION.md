# v0.2 — 备份增强

2026-10-04。用户已确认 Stage 12 手机验收通过并授权继续后续功能。本次完成 DEVELOPMENT_PLAN.md 的 v0.2，后续服务器/AI 不在本次范围。

## 实现与决策

- LocalBackupScheduler.kt：唯一每日 WorkManager 任务，UPDATE 避免重复，低电量/存储不足延后；默认关闭，开启先创建一份，关闭取消调度，启动时对照 DataStore 恢复调度。Worker 检查开关，失败有限重试并记录状态。
- LocalBackupCipher.kt：Android Keystore 非导出 AES-256 密钥，本机历史用 GCM 完整性保护，不默默替换不可用旧密钥。
- LocalBackupHistory.kt：最近 7 份历史，原子写新文件与索引后才清理旧文件；恢复校验完整密文及内容，删除只针对选中备份；密码导出重新加密选中历史快照。
- BackupEncryption.kt：密码导出整个既有 ZIP，PBKDF2-HMAC-SHA256（600000 次）+ AES-256-GCM，新随机盐/nonce，头部作 AAD，严格限制文件大小和 KDF 参数；格式见 [BACKUP_FORMAT.md](BACKUP_FORMAT.md)。不保存用户密码。
- BackupManager.kt：保留普通 ZIP，增加加密导出、私有密文暂存与解密、历史恢复/导出。错误密码、篡改和无效内容不进入账本替换；恢复仍使用既有预览/二次确认与恢复前副本。
- SettingsRepository.kt：自动开关和最后结果持久化，独立于跨设备备份的主题设置；BackupViewModel.kt 统一状态、错误和重试，完成后清空传入密码数组。
- BackupScreen.kt：输入区域位于页面内，可滚动并处理键盘；旋转保留文件引用，清空密码。历史恢复和删除均需确认。MainActivity/LedgerApplication 接入调度。
- 只新增计划要求的稳定 WorkManager 2.12.0 和测试配套依赖；数据库 schema/版本仍为 1，Long 金额及财务口径未改动。

## 验证

Windows Android Studio JBR / SDK 环境实测：

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat assembleDebug` | 通过，13 秒 |
| `.\gradlew.bat test` | 通过，59 秒；180 项，失败/错误/跳过均为 0 |
| `.\gradlew.bat lint` | 通过，24 秒；0 errors、28 warnings |
| `.\gradlew.bat assembleDebugAndroidTest` | 通过，9 秒；仅编译设备测试，未连接手机执行 |

日志位于 `build/v02-verification`，单元测试与 lint 报告位于 `app/build/reports`。

发布 APK：包名 `com.example.myledger`，versionName `0.2.0`，versionCode `14`，minSdk 36，targetSdk 37，大小 35,305,782 字节。`apksigner verify --print-certs` 通过，签名证书与 Stage 12 相同，可覆盖安装。最终 manifest 没有 INTERNET 权限。

- APK SHA-256：`465e25b67d5aeec5094306af6f2c8bc24219512c458dfd7139bab288018272d1`
- 签名证书 SHA-256：`f715b909f3dfc869b13aa34aa769b71a333978590e5c9705f34ee521b8d047b2`

新测试覆盖密码往返、新 salt/nonce、错误密码、头部/密文/tag 篡改、截断和 KDF/大小限制；真实 Room/DataStore 的历史保留、并发写入、旧快照导出与恢复、失败保护、旧 ZIP、开关与主题独立；实际 Worker 的关闭检查、成功与失败重试；调度唯一与取消；ViewModel 失败不启用、密码数组清空和状态重建；原生 Compose 加密保存/选择/错误密码/旋转/确认恢复、输入校验、历史动作确认。

本地 JVM 用测试生成的真实 AES 密钥注入 LocalBackupCipher，验证完整加密、文件和数据库流程；没有伪造账目计算或跳过失败测试。真实 Android Keystore 另有 BackupKeystoreTest 设备测试，构建时编译，手机手动“立即本地备份→预览恢复”验证实际密钥读写。

密码输入 AlertDialog 在原生 JVM 页面测试中出现持续布局而无法空闲，最终改为普通页面内表单；保持密码遮蔽及校验，完整页面测试通过。原生截图位于 app/build/v02-ui。

## 手机验证与边界

[V02_PHONE_ACCEPTANCE.md](V02_PHONE_ACCEPTANCE.md) 覆盖本版新功能。真实系统文件应用、分享目标、Keystore 及隔日调度仍需用户手机验证；自动任务可能受系统省电策略延后。

本机备份随卸载消失，换机使用外部密码加密文件；普通 ZIP、Room 数据库及私有恢复前副本不承诺被本功能整体加密。内层 backupVersion 保持 1，兼容 Stage 11/12 ZIP。

API 与参数依据：[WorkManager 稳定版](https://developer.android.com/jetpack/androidx/releases/work)、[Android Cryptography](https://developer.android.com/privacy-and-security/cryptography)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[OWASP PBKDF2 参数](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2)。
