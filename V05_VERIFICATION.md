# v0.5 AI 辅助记账与整体优化

日期：2026-10-04。接续 v0.4，不新增数据库表、网络/LLM SDK 或后台 AI 请求。

## 草稿流程

AssistantEntryService 只拥有配置及 HTTP transport，没有 Repository、财务查询或写入工具。一次请求发送用户描述、默认日期、今天日期和固定分类目录；不会附加已有流水/备注/活动。支持现有 Chat Completions 与 Responses 协议，服务需要支持函数工具调用。两个数据发送权限在独立 DataStore 中默认关闭，密钥仍使用 Keystore 加密；不随备份迁移。

propose_transactions 只返回 TransactionDraft。所有字段在本地校验；金额先用整数分字符串转换 Long，再由专用 formatter 展示为元。不接受小数/指数分、未知/错配分类、无效日期、超长备注、额外字段、未知工具或合计溢出；一笔无效则整组失败。没有草稿的模型回答会明确标注账本未改变。解析最多20笔，不把结果自动加入账本。

检查页复用 BatchEntryViewModel / BatchDailyEntryScreen：逐条编辑类型、金额、分类、活动、可报销、日期、备注或删除。仅点击全部保存才调用既有 Repository.addTransactions 单个事务；SavedStateHandle 保存草稿和回执，重复点击/旋转不会重复插入。生成取消、权限撤回、错误响应不写库。只读财务分析保留六类工具。

## 整体优化

- 首页统计按本月查询，最近记录使用日期/ID 倒序 LIMIT 5；去掉全历史排序。统计页查询所选月份往前共6个月，切换日常/全部仍使用同一个分析层。
- 助手聚合查询只获取所需日期范围；跨多年月份比较分别读取两个单月，不加载中间历史；查询快照仍处于单个 Room 事务。账本备份继续使用完整 snapshot。
- 相同账目 Flow 结果去重，避免不相关历史变更触发再次汇总；明细 LazyColumn 为日期/账目分别指定 contentType。
- 明细增加今天/本月/上月快捷筛选；统计增加上月/下月/回到本月，历史选择保存后不因页面恢复跳回当前月。
- 蓝灰色的余额区与收支分区、统一条目背景和间距、标题/操作同排、主题设置分组。顶部固定“记账”入口支持记一笔/记多笔/AI草稿，不遮挡列表；主要页面减少为悬浮按钮预留的空白。保存提示条使用实测高度占用页面空间，避免遮住按钮或拦截点击。
- AI 草稿页直接呈现条目，省去不适用的批次默认值区域；新增手动条目仍继承生成页默认日期。

未提供 FPS 或加速百分比；验证的是查询范围、金额结果和交互回归，不把桌面测试替代真实手机的滚动体验。

## 改动文件

核心：assistant/AssistantEntryService.kt，ui/assistant/AssistantEntryViewModel.kt、AssistantEntryScreen.kt，integration 设置 Repository/ViewModel/Screen，BatchEntryViewModel 与导航。
优化：TransactionDao、LedgerRepository、FinancialTools，Home/Statistics 的 ViewModel/Screen、RecordsScreen、SettingsScreen。版本17 / 0.5.0，Room版本1及备份格式不变。
新增与扩展测试覆盖协议、独立权限、取消旧请求、未知工具、非法参数、Long精度、待确认与恢复、历史月份、快捷筛选以及10000条无关历史下的范围隔离。

## 验证

最终 Windows 实际执行：

| 命令 | 结果 |
| --- | --- |
| .\gradlew.bat assembleDebug | 通过，10秒 |
| .\gradlew.bat test | 通过，222项测试，0失败/错误/跳过，75秒 |
| .\gradlew.bat lint | 通过，0错误，28条提示，49秒 |
| .\gradlew.bat assembleDebugAndroidTest | 测试APK编译通过，14秒；未通过ADB在手机运行 |
| python -m unittest discover -s server -p 'test_*.py' -v | 6项通过，3.427秒 |

阶段功能本身先独立 assembleDebug / 216项test / lint 通过，再进行整体优化。最终原生Compose检查包括横竖屏、深浅色、大字体、大金额、实际导航和描述输入重建；截图保存在本机 app/build 下对应 ui 目录，已人工查看。此前首页操作回归发现保存提示覆盖入口，现已修复并全量通过。活动测试改为等待明细列表出现，不依赖已滚出可见区域的筛选节点。

最终日志：build/v05-final-assemble.log、v05-final-test.log、v05-final-lint.log、v05-final-android-test-build.log、v05-final-server-test.log。清理源码末尾空行后再次 assembleDebug 通过（build/v05-release-assemble.log）；源码差异通过 git diff --check；APK通过apksigner验证，签名与v0.4一致。

APK SHA-256：0f11b264f107ca690081a42fe01c99c71d8464c8625a9aa2ed11403ea141e3c5；35,993,101字节。签名证书SHA-256：f715b909f3dfc869b13aa34aa769b71a333978590e5c9705f34ee521b8d047b2。

实际 API 和服务器尚未配置，未发起真实付费API请求或上传真实账本。手机验收见 V05_PHONE_ACCEPTANCE.md，配置见 CONFIGURATION_GUIDE.md。

实现参考：[函数调用](https://developers.openai.com/api/docs/guides/function-calling)、[Compose性能建议](https://developer.android.com/develop/ui/compose/performance/bestpractices)。无新增 experimental API。
