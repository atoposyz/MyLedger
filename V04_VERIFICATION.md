# v0.4 只读财务助手与 v0.3 联合交付

2026-10-04，按用户“直接做到 v0.4，服务器与 API key 完成后配置”的授权完成。v0.3 独立 build/test/lint 通过后开始 v0.4，记录见 [V03_VERIFICATION.md](V03_VERIFICATION.md)。本次没有连接用户服务器或真实付费 API，没有上传真实财务数据。

## 改动与口径

- assistant/FinancialTools.kt：六个白名单工具，仅返回日期/分类/活动聚合结果，不提供记录、备注、SQL 或写库接口。查询参数校验，日期范围最多 366 天，活动最多 30 项，结果有界。金额由 ExpenseAnalyzer/IncomeAnalyzer/FinancialAnalysis 使用 Long 精确计算；溢出明确失败，JSON 以整数字符串保留精度。净收支直接复用 FinancialAnalysis.cashBalanceMinor。
- analysis/IncomeAnalyzer.kt：增加普通收入分类结构，排除报销；其余原有统计未改。util/FinancialResultFormatter.kt 在显示层格式化分金额，支持负差额。
- assistant/AssistantService.kt：支持 Chat Completions 与 Responses 的函数调用循环，最多 5 轮/8 次工具调用，重复 ID/未知工具/无效参数受限；问题独立，请求不包含旧聊天，store=false，不使用服务端 conversation ID。Responses 保留本轮返回项并关联 function_call_output。
- ui/assistant：第五个一级页面，本地查询无需配置/网络；AI 发送需要配置与汇总权限，显示查询依据、错误重试、取消和确认清空。ViewModel 的请求版本避免旧请求覆盖新请求状态。聊天只在内存，不进 SavedState/备份。
- 配置页接入两种协议、模型、API key 和默认关闭的汇总权限；每轮发送重新检查权限及同一配置，避免用户更换地址后发送前一服务的工具结果。凭据使用独立 Keystore 加密存 DataStore，不导出、不回填、不存未保存输入。客户端使用现有平台 HTTPS 传输，未增加任何依赖或 LLM SDK。
- LedgerApplication、导航、图标、版本更新为 0.4.0（16）；助手页隐藏记账 FAB，避免遮挡提问输入。账本 Room schema 仍为 v1。服务器备份仍是手动上传加密文件，不是同步。v0.5 写账未实现。

## 验证

最终 Windows Android Studio JBR / SDK 实测：

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat assembleDebug` | 通过，13 秒 |
| `.\gradlew.bat test` | 通过，1 分 3 秒；206 项，失败/错误/跳过均为 0 |
| `.\gradlew.bat lint` | 通过，28 秒；0 errors、28 warnings |
| `.\gradlew.bat assembleDebugAndroidTest` | 通过，9 秒；设备测试仅编译，未运行 |
| `python -m unittest discover -s server -p test_*.py -v` | 6 项通过，3.339 秒 |

日志为 `build/v04-*.log`，单元测试/lint 报告在 `app/build/reports`。最终 APK 包名 `com.example.myledger`，versionName `0.4.0`，versionCode `16`，minSdk 36，targetSdk 37，大小 35,649,933 字节。

- APK SHA-256：`2b1e9f53451e9de0f848dac14e42142e2250e5fb7497c567d1fc708bc972fb45`
- 签名证书 SHA-256：`f715b909f3dfc869b13aa34aa769b71a333978590e5c9705f34ee521b8d047b2`；apksigner 验证通过，与 Stage 12 / v0.2 一致，可覆盖安装。

真实 Room/DataStore 测试覆盖六种聚合、排除重叠、收入与报销、负差额、净收支、Long.MAX_VALUE 与溢出、闰日、参数/SQL/范围拒绝；两种 API 请求结构与工具结果往返、缺配置/权限无请求、撤回权限阻止结果上传、恶意写账工具、重复/无限工具调用、取消、ViewModel 重试/清空与新旧请求隔离。API 协议边界使用固定服务响应，不冒充真实供应商实测。

原生 Compose 真正启动 MainActivity 验证第五导航页、真实账目离线查询、配置往返、旋转保留非秘密输入并清空 key/token；横屏页面验证发送、查询依据和清空确认。截图在 app/build/v04-ui，逐张检查。

v0.3 的真实 HTTPS socket 测试与 Python HTTP 服务测试继续回归。真实设备另有 BackupKeystoreTest 和 IntegrationKeystoreTest；仅编译，未连接 ADB 执行，手机保存配置/重新打开与本地备份将验证实际 Keystore。

## 待手机与配置实测

未配置真实服务器与 API key，因此公网 HTTPS、服务器部署、供应商模型权限/额度与真实模型回答质量待 [联合验收](V04_PHONE_ACCEPTANCE.md)。完整配置操作见 [CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md)。请求取消不能撤回已经发送的 API 内容；API 文本解释应与显示的本地工具依据核对。

使用 OpenAI Docs 技能核对 [官方函数调用](https://developers.openai.com/api/docs/guides/function-calling) 与 [API reference](https://developers.openai.com/api/reference/overview)；普通 HTTPS、Caddy、服务器契约见 v0.3 和 server 文档。
