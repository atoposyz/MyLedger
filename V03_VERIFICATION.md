# v0.3 自建服务器备份

2026-10-04，按用户授权完成后继续 v0.4，最终一起手机验收。数据库仍为 v1，现有金额/统计与本地备份语义不变。

实现文件：network/HttpTransport.kt、data/settings/IntegrationSettingsRepository.kt、backup/RemoteBackupRepository.kt、ui/backup/RemoteBackup*.kt、ui/settings/Integration*.kt；连接 LedgerApplication、SettingsScreen、导航与 manifest。只增加 INTERNET 权限，不新增依赖。HTTP 禁用，使用平台 TLS 校验，不跟随重定向，响应大小/超时有界。

服务器位于 server/backup_server.py，标准库单用户 API，只在 loopback 监听，提供 Caddy/systemd 示例。认证为独立随机令牌，索引只含密文文件元数据，默认保留 7 份；提交新密文成功才清理旧份。配置和账本备份隔离；令牌加密存储，更换服务器地址必须重新输入。下载后校验 SHA-256，密码与 GCM 校验后进入现有预览/确认恢复。

Windows `assembleDebug` 通过（22 秒），`test` 通过（1 分钟；191 项，失败/错误/跳过均为 0），`lint` 通过（26 秒；0 errors、29 warnings）。Python 真实 HTTP API 测试 6 项通过，覆盖全接口认证、普通 ZIP/本机密文/无效参数/大小/路径拒绝、密文往返、保留与删除、篡改、重启索引和孤立文件。

Android 测试使用真实 Room、DataStore 与 AES 加密，HTTP 边界测试验证上传仅密文、下载只暂存、错误密码/网络/校验失败不改账本、确认恢复、密钥加密持久化及更换地址规则。原生 Compose 测试覆盖未配置、恢复和删除确认。另有真实 HTTPS socket 测试，使用公开的 localhost 测试证书验证证书信任、认证、重定向/错误体保护和响应上限；生产没有关闭 TLS 验证。

用户服务器未配置，公网域名/证书/部署和手机真实网络待联合验收。API 部署与契约见 [server/README.md](server/README.md)。本版不自动远程备份或实时同步。
