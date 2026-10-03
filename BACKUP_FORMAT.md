# MyLedger 本地备份 v1

备份是 ZIP 中的五个 UTF-8 JSON 文件：manifest.json、transactions.json、activities.json、categories.json、settings.json。它独立于 Room schema，不保存 SQLite 文件。

manifest 包含 appId、backupVersion（1）、appVersion、UTC createdAt、transactionCount、activityCount，以及四个数据文件的 SHA-256。校验用于检测损坏，不提供加密或真实性保证。文件包含财务明文，请自行保管。

所有 Long 字段（金额分、ID、关联 ID、创建/更新时间）使用十进制字符串，避免 JSON 数字经过其他工具后丢失精度。业务日期为 ISO 本地自然日；活动日期和备注允许 null；类型和主题使用枚举名称；布尔字段使用 JSON boolean。分类必须与当前固定分类一致，货币为 CNY。

导入先复制到应用私有缓存，检查结构、版本、校验、数量、类型、金额、日期、重复 ID 和关联完整性，再展示预览。二次确认后替换全部账目、活动和设置，保留原 ID 与审计时间。取消或校验失败不修改账本。Room 使用单个事务；设置写入失败会回滚 Room，提交失败会尝试恢复原设置。两种存储不具备断电时的跨存储原子性。

恢复前通过 AtomicFile 保存一份私有的恢复前副本。页面的“恢复上次替换前的账本”仍需预览和确认；每次替换会更新这份副本，不构成备份历史。卸载应用会删除私有副本，应另外保存 ZIP。

限制：ZIP 文件不超过 20 MiB，JSON 解压总量不超过 50 MiB，账目和活动各不超过 100,000 条。仅接受五个根目录文件，拒绝目录、重复和未知文件，不解压到文件系统。

保存使用系统文件选择器；分享通过仅开放导出缓存目录的 FileProvider 和临时读取授权。应用没有联网权限，文件只在用户选择保存或分享时交给目标应用。
