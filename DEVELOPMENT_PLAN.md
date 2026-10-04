# DEVELOPMENT_PLAN.md

# Codex 分阶段开发计划

开发原则：

> 一次只完成一个可验证阶段。上一阶段 build/test 通过后，再进入下一阶段。

---

## Stage 0 — 创建基础工程

目标：

- 新建 Android 项目；
- Kotlin；
- Jetpack Compose；
- Material 3；
- Gradle Kotlin DSL；
- 建立 Version Catalog（如果 Android Studio 模板已经使用则保留）；
- 配置基础主题；
- 创建 PRODUCT_SPEC.md 和 AGENTS.md。

验收：

```text
gradlew.bat assembleDebug
gradlew.bat test
```

全部通过。

---

## Stage 1 — 导航与空页面

实现：

- HomeScreen
- RecordsScreen
- StatisticsScreen
- SettingsScreen
- Bottom Navigation
- 新增 FAB
- FAB 弹出：
  - 记一笔
  - 记多笔

要求：

- 不做真实数据库；
- 不做真实统计；
- 页面只是结构占位；
- 完成导航路径。

验收：

- 四个一级页面切换正常；
- 记一笔、记多笔入口可进入对应占位页；
- 返回导航正常；
- build/test 通过。

---

## Stage 2 — Room 数据层

实现：

- TransactionEntity
- CategoryEntity
- ActivityEntity
- DAO
- AppDatabase
- Repository
- 默认分类初始化

分类：

支出：

- 餐饮
- 交通
- 住宿
- 日用
- 购物
- 娱乐
- 学习科研
- 医疗健康
- 通讯订阅
- 人情社交
- 其他

收入：

- 固定工资
- 项目酬金
- 实验室事务
- 奖学金/奖励
- 报销
- 其他收入

要求：

- amountMinor 使用 Long；
- 业务日期和创建时间概念分离；
- DAO 使用 Flow；
- 不加入网络字段；
- 不加入 BatchEntity。

验收：

- DAO 单元测试；
- 插入、修改、删除、查询正常；
- build/test 通过。

---

## Stage 3 — 统一 FinancialAnalysis

实现：

- ExpenseSummary
- IncomeSummary
- CategoryBreakdown
- PeriodSummary
- ExpenseAnalyzer
- IncomeAnalyzer

统计规则：

```text
普通收入 != 报销到账
全部支出 = 所有 EXPENSE
日常支出 = 全部支出 - 活动支出 - 可报销支出
```

要求：

- UI 不参与统计计算；
- 使用 Long 计算金额；
- 写单元测试覆盖统计口径。

重点测试：

1. 普通收入不包含报销；
2. 活动支出不进入日常支出；
3. 可报销支出不进入日常支出；
4. 普通支出正确进入日常支出；
5. 全部支出包含上述所有 EXPENSE。

---

## Stage 4 — 记一笔

实现真实的单笔记账页面。

字段：

- 类型
- 金额
- 分类
- 活动
- 可报销
- 日期
- 备注

要求：

- 金额键盘友好；
- 默认日期为今天；
- 支出 / 收入切换后分类列表正确变化；
- 报销到账单独处理；
- 保存后返回；
- Room 自动更新数据流。

验收：

- 新增账目可在数据库中查询；
- 金额精确；
- 日期正确；
- build/test 通过。

---

## Stage 5 — 记多笔

实现 BatchDailyEntryScreen。

页面顶部：

- 日期
- 默认活动
- 默认可报销

草稿行：

- 金额
- 分类
- 备注
- 活动
- 可报销

功能：

- 添加一行；
- 删除草稿行；
- 继承日期；
- 默认活动继承；
- 默认可报销继承；
- 可逐条覆盖；
- 显示当前批次合计；
- 全部保存。

要求：

- 使用内存中的 List<TransactionDraft>；
- 一次数据库 transaction 批量插入；
- 不建立 BatchEntity。

验收：

- 一次添加 10 条记录成功；
- 保存后条目独立存在；
- 页面级默认值正确继承；
- build/test 通过。

---

## Stage 6 — 明细页

实现：

- 按日期倒序；
- 按天分组；
- 每日收支摘要；
- 点击进入编辑；
- 删除二次确认；
- 日期范围筛选。

验收：

- 新增、编辑、删除后实时刷新；
- 跨日期分组正确；
- build/test 通过。

---

## Stage 7 — Activity 管理

实现：

- Activity 列表；
- 创建活动；
- 编辑活动；
- PERSONAL / WORK；
- 起止日期；
- 备注。

要求：

- Activity 与 Category 独立；
- 删除有账目的 Activity 时不要直接级联删除账目；
- 应提示并安全处理关联关系。

验收：

- 创建“上海演唱会”并用于多条账目；
- 创建“ISCA 2027”并用于公务支出；
- build/test 通过。

---

## Stage 8 — 首页真实数据

实现：

- 本月结余；
- 普通收入；
- 日常支出；
- 全部支出；
- 活动支出摘要；
- 最近记录。

要求：

- 所有统计调用 FinancialAnalysis；
- 不复制计算逻辑；
- 无数据时有明确空状态。

---

## Stage 9 — 统计页

实现：

```text
[ 日常 ] [ 全部 ]
```

统计内容：

- 月度总额；
- 分类占比；
- 分类排行；
- 月度趋势。

要求：

- 图表简单；
- 不引入重型图表库，除非 Compose 自绘明显不合理；
- 日常/全部切换同时影响所有统计；
- 不能为了好看牺牲可读性。

---

## Stage 10 — 设置与 DataStore

实现：

- 深色模式：跟随系统 / 浅色 / 深色；
- 默认货币：第一版人民币；
- 基础 App 信息；
- 数据导入/导出入口占位。

不实现远程服务器。

---

## Stage 11 — 本地备份与恢复

实现稳定备份格式。

建议：

```text
backup.zip
├── manifest.json
├── transactions.json
├── categories.json
├── activities.json
└── settings.json
```

功能：

- 导出；
- 分享 / 保存文件；
- 选择文件恢复；
- 恢复前显示：
  - 备份时间
  - App 版本
  - 账目数量
- 恢复前二次确认。

要求：

- 备份格式独立于 Room schema；
- 定义 backupVersion；
- 不直接把 SQLite 当长期备份协议。

---

## Stage 12 — UI 收尾

重点：

- 间距；
- 字号；
- 空状态；
- 输入错误提示；
- 深色模式；
- 长备注；
- 大金额；
- 横竖屏基本稳定；
- 键盘遮挡；
- 日期选择体验；
- 记多笔效率。

目标：

> 像一个克制的生产力工具，而不是模板型 App。

---

# 后续版本

## v0.2 — 备份增强

- 自动本地备份；
- WorkManager；
- 备份历史；
- 客户端 AES-GCM 加密。

实现口径：自动任务默认关闭；每天由 WorkManager 调度并避免重复任务；本机保留 7 份密钥加密历史；外部文件提供密码加密导出/恢复并兼容旧 ZIP。恢复和历史删除均需确认。验收需覆盖密码错误、密文篡改、备份失败不清理旧文件、保留数量、关闭调度和 Windows build/test/lint。

## v0.3 — 自建服务器备份

服务器仅保存加密备份文件。

可实现：

```text
PUT /api/backup
GET /api/backups
GET /api/backups/{id}
DELETE /api/backups/{id}
```

不引入实时账本同步。

用户已于 2026-10-04 授权与 v0.4 连续开发，最后联合验收；服务端和 API key 待完成后配置。v0.3 提供配套单用户密文文件 API、HTTPS 配置与部署指南，不自动部署或上传真实账本。

## v0.4 — AI 财务助手

新增第五个一级页面：

```text
首页
明细
统计
助手
设置
```

实现 FinancialTools：

- 查询某日期范围支出；
- 查询分类支出；
- 查询活动总支出；
- 比较月份；
- 查询报销相关数据；
- 查询收入结构。

LLM 只获取必要的聚合结果，不默认上传全部流水。

2026-10-04 实现至 v0.4.0：六类本地查询、两种 API 协议的只读工具调用、汇总权限与查询依据、连接配置和错误重试。v0.3 先独立验证通过，再执行 v0.4；最终 Windows build/test/lint 和 206 项 Android / 6 项服务器测试通过。服务器/API 未配置，部署与联合手机验收见 CONFIGURATION_GUIDE.md 和 V04_PHONE_ACCEPTANCE.md。v0.5 不在本次范围。

## v0.5 — AI 辅助记账

支持：

```text
“今晚吃饭 32 块”
```

转换成待确认 TransactionDraft。

必须经过用户确认后才能写数据库。

2026-10-04 用户已授权 v0.5，并要求阶段通过后进行整体优化；功能与手机验收见 V05_PHONE_ACCEPTANCE.md，开发验证见 V05_VERIFICATION.md。独立描述发送权限、单请求生成草稿、复用既有编辑及事务保存；不增加数据库实体或 AI 写库工具。优化保持现有统计口径。

---

# Codex 建议执行方式

每个阶段给 Codex 一个独立任务。

示例：

```text
请阅读 PRODUCT_SPEC.md、AGENTS.md 和 DEVELOPMENT_PLAN.md。

现在只执行 Stage 2：Room 数据层。

不要提前实现 Stage 3 及之后的功能。
不要引入 Hilt/Koin。
完成后运行：
gradlew.bat assembleDebug
gradlew.bat test

最后汇报：
1. 修改的文件
2. 数据模型设计
3. 测试结果
4. 仍存在的问题
```

不要一次要求 Codex 完成整个 App。
