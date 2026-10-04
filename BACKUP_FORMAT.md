# MyLedger 本地备份 v1

备份是 ZIP 中的五个 UTF-8 JSON 文件：manifest.json、transactions.json、activities.json、categories.json、settings.json。它独立于 Room schema，不保存 SQLite 文件。

manifest 包含 appId、backupVersion（1）、appVersion、UTC createdAt、transactionCount、activityCount，以及四个数据文件的 SHA-256。校验用于检测损坏，不提供加密或真实性保证。文件包含财务明文，请自行保管。

所有 Long 字段（金额分、ID、关联 ID、创建/更新时间）使用十进制字符串，避免 JSON 数字经过其他工具后丢失精度。业务日期为 ISO 本地自然日；活动日期和备注允许 null；类型和主题使用枚举名称；布尔字段使用 JSON boolean。分类必须与当前固定分类一致，货币为 CNY。

导入先复制到应用私有缓存，检查结构、版本、校验、数量、类型、金额、日期、重复 ID 和关联完整性，再展示预览。二次确认后替换全部账目、活动和设置，保留原 ID 与审计时间。取消或校验失败不修改账本。Room 使用单个事务；设置写入失败会回滚 Room，提交失败会尝试恢复原设置。两种存储不具备断电时的跨存储原子性。

恢复前通过 AtomicFile 保存一份私有的恢复前副本。页面的“恢复上次替换前的账本”仍需预览和确认；每次替换会更新这份副本，不构成备份历史。卸载应用会删除私有副本，应另外保存 ZIP。

限制：ZIP 文件不超过 20 MiB，JSON 解压总量不超过 50 MiB，账目和活动各不超过 100,000 条。仅接受五个根目录文件，拒绝目录、重复和未知文件，不解压到文件系统。

保存使用系统文件选择器；分享通过仅开放导出缓存目录的 FileProvider 和临时读取授权。v0.1/v0.2 没有联网权限；v0.3 起增加 HTTPS 服务器备份，用户配置后可手动上传密码密文，仍不自动上传或同步账本。

## v0.2 加密外层

内部 ZIP 的 backupVersion 仍为 1。密码加密导出以 .enc 保存：8 bytes ASCII `MYLENC01`、4 bytes 大端迭代次数（600000）、16 bytes 随机 salt、12 bytes 随机 GCM nonce，随后为 AES-256-GCM 密文及 16 bytes tag。整个 40-byte 头部作为 AAD，防止头部被篡改；AES key 通过 PBKDF2-HMAC-SHA256 派生。每次加密使用 SecureRandom 生成新 salt/nonce，禁止接受任意迭代次数。密码限制 8–128 个 UTF-16 code units，内容不裁剪、不转换，不写入 DataStore/SavedStateHandle；操作结束清空用于派生的字符数组。

解密必须完整验证 GCM tag，才把内部 ZIP 交给原有校验器。错误密码或篡改均拒绝。选择的加密文件先复制到私有暂存区，因此输入密码期间原始文件变动或旋转不影响该副本；解密成功后仍须预览、二次确认。旋转保留文件引用，不保留密码。

本机历史使用不同标识 `MYLLOC01`，后接 12-byte nonce 及 GCM 密文/tag；密钥保存在 Android Keystore，nonce 由 Cipher 加密初始化生成。此格式仅供当前安装读取，不直接分享。历史“加密导出”先校验/解密本机文件，再用用户密码重新加密成可跨设备的 `MYLENC01` 格式。

历史目录 index.json 只保存文件名、时间、版本、数量、大小等显示元数据。新密文和索引原子落盘成功后才清理过期历史/中断临时文件，保留最近 7 份；新写入失败保留已有索引和文件。按生成顺序保留，避免系统时间回拨影响清理。

本机任务开关、状态和密钥不加入跨设备 ZIP 设置；恢复仅恢复原来的主题/人民币设置。Room、原有私有恢复前副本及导入暂存仍使用应用私有存储，本功能不宣称为整个应用数据库提供加密。

v0.3/v0.4 的服务器地址/令牌、AI 地址/模型/key、汇总权限也不进入此 ZIP 或加密外层。服务器仅保存 MYLENC01 密文及文件 ID/时间/大小/SHA-256 元数据；客户端完整校验下载文件后才解密和预览，不能仅凭服务器返回的成功状态恢复。服务器 API 与部署见 [server/README.md](server/README.md)。

依据：[Android Cryptography](https://developer.android.com/privacy-and-security/cryptography)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[OWASP PBKDF2 参数](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2)。
