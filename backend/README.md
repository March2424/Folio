> **已停用的独立后端实验。** 当前 Android App 的 Agent 已改为端内 Java 模块，直接使用客户端填写的模型 API Key 和模型名称。无需运行本目录、无需填写后端地址或 Agent Token。当前使用说明见项目根目录 `AGENT_README.md`。以下保留历史说明及数据，供参考。

# Memory Agent V1

Java 17+ / Spring Boot 3.5.16 / H2 文件数据库。独立 Maven 工程，不加入 Android Gradle 构建，不把服务器放进 APK。

## 启动

需要 JDK 17–25、Maven 3.6.3+。首次构建需要访问 Maven Central。

```sh
cd backend
cp .env.example .env
# 编辑 .env，填写 AGENT_TOKEN、LLM_API_KEY 和 LLM_MODEL
./run.sh
```

`run.sh` 会读取本地 `.env`。macOS 未设置 Java/Maven 时，尝试使用 Android Studio 的 JDK 和 IntelliJ 的 Maven。也可使用标准命令 `mvn spring-boot:run`，但这时需要自行导出环境变量。`.env` 不进入版本控制。

配置项：

| 变量 | 含义 |
| --- | --- |
| `AGENT_TOKEN` | 至少16位的私人访问令牌；可用 `openssl rand -hex 24` 生成。App 的“连接”中填写同一令牌 |
| `LLM_BASE_URL` | 供应商的兼容 API 基址，通常以 `/v1` 结尾；程序追加 `/chat/completions` |
| `LLM_API_KEY` | 模型供应商密钥，只保存在后端 |
| `LLM_MODEL` | 供应商实际支持的模型名，不预设模型 |
| `AGENT_BIND_ADDRESS` | 默认 `127.0.0.1`；局域网调试可设 `0.0.0.0` |
| `AGENT_PORT` | 默认 `8080` |
| `AGENT_DB_URL` | 默认 `jdbc:h2:file:./data/memory-agent`，相对于启动工作目录 |
| `AGENT_DB_PASSWORD` | 可选数据库访问密码；不是数据库文件加密设置 |

模型需支持 **Chat Completions、原生 function/tool calling、中文和指令生成 JSON**。不要求供应商提供 JSON mode，不发送 `response_format`。目前不是适配任意供应商原生接口的 SDK。未填写模型配置时，仍能保存/查看/删除原文；整理和问答会明确提示配置缺失，没有伪造的演示回答。

数据存储在 `backend/data/`（使用 `run.sh` 启动时）。停止服务后可备份整个目录。不要删除此目录以“重启”服务。

## 手机体验

1. Android 首页档案网格下方点“记忆对话 ↗”，或模块中的“功能 → 记忆对话 · 关于我”。
2. “连接”保存后端地址及 **AGENT_TOKEN**，点“检查连接”。手机端不填写 LLM_API_KEY。
3. “材料”粘贴文字，或导入 UTF-8 `.txt` / `.md`，填写标题，保存并整理。第一版正文最多24000字符、文件最多200KB；暂不解析 PDF/Word/图片/录音。
4. 也可以手动选择记忆册中的真实记录上传文字部分（每批最多10条）。示例数据不上传；封面、图片、录音文件和排版字段不上传。
5. “关于我”查看带原文证据的候选特征。确认后才参与问答；“这不代表我”或撤回后不再参考。模型归纳的推断即使确认，也仍带 `INFERRED` 标记。
6. “对话”提问；回答中的 `[1]` 等来源编号对应可点击的原文入口。

模拟器地址：`http://10.0.2.2:8080`。

真机 USB 调试可使用：

```sh
adb reverse tcp:8080 tcp:8080
```

然后 App 地址填 `http://127.0.0.1:8080`。局域网方式需要后端监听 `0.0.0.0`，App 填电脑局域网 IP。只有 debug APK 允许 HTTP；release 使用 HTTPS 反向代理。访问令牌由 Android Keystore 中的 AES 密钥加密保存在私有 SharedPreferences，且排除系统备份/迁移。

## 数据与行为

- **原文、摘要、特征分开存储。** 先持久化原文，再调用整理接口。模型失败后，原文保持 `PENDING`，在列表中重试。
- 长材料每4000字符整理一次，各片段摘要组合保留。暂不生成月度/阶段摘要；不递归压缩摘要，不自动删除原文。
- 特征最多20条/材料；只保留能够在对应片段中逐字匹配的原文证据。所有特征初始为 `CANDIDATE`，仅 `CONFIRMED` 进入回答上下文。拒绝的特征保留状态以供后端检查，App 隐藏。
- 书摘/歌词/他人文字与自述在提示词中分开约束。证据匹配能排除虚构引用，但模型仍可能误读语义，因此需要用户确认。
- 同一记录 ID 和相同内容重复上传不产生副本、不重复整理。内容修改会使该记录的摘要、特征失效，重新整理；发生并发修改时，旧版本的分析不能覆盖新内容。
- **遗忘只影响排序。** 检索相关性优先，小幅结合最近回看、入库时间和“请替我记住”标记；老记忆仍可被精确关键词检索。没有实现自动物理删除、定时遗忘或完整的清晰/沉睡状态机。
- 关键词检索支持中文双字片段、日期范围及类型。没有 embeddings、向量库或微调；含蓄表达的召回能力有限。日期工具使用真实日历校验。
- 对话先检索最多5段记忆，附带最多30条已确认特征；必要时模型再调用检索/原文工具。工具最多6次、模型最多4轮；原文可按4000字符分页阅读。
- 模型返回的来源ID必须来自本轮实际提供的记忆。未知引用会被拒绝；返回 App 前将引用转换为按 sources 顺序排列的 `[1]` 等编号。引用存在并不证明模型的每句话都正确，仍需结合原文查看。
- 同一会话仅回传最近10条消息。界面保存最近10段对话用于当前页面/旋转恢复，不在本地长期保存聊天。
- 修改原文、整理完成、确认/撤回特征或删除材料时，V1 保守清空全部后端对话，防止派生文本继续引用失效资料。生成中如资料变化，会返回409请用户重问。
- 删除材料会同时删除其原文、摘要、特征及后端对话。**手机本地原记录保留**。手机编辑/删除本地记录暂不自动同步到后端；再次手动上传会更新同ID，远端删除需在“材料”中操作。此前已返回给其他客户端的内容不能远程撤回。

## API

除 `GET /health` 外，都要求 `Authorization: Bearer <AGENT_TOKEN>`。未配置至少16位令牌时返回503，令牌错误返回401。错误使用 `{ "message": "…" }`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/health` | 基本存活检查，不代表模型连接可用 |
| POST | `/api/memories` | 新增/按ID更新文字记忆 |
| POST | `/api/materials` | multipart 文件上传，字段 `file`，仅 UTF-8 txt/md |
| GET | `/api/memories` | 最近100条标题、摘要、状态，不含原文 |
| GET | `/api/memories/{id}` | 读取原文、摘要和元数据 |
| POST | `/api/memories/{id}/analyze` | 生成摘要和候选特征，可重试 |
| PATCH | `/api/memories/{id}/pin` | `{ "pinned": true }` |
| DELETE | `/api/memories/{id}` | 删除原文及派生资料 |
| GET | `/api/profile` | 所有候选/确认/拒绝特征及证据 |
| PATCH | `/api/profile/{id}` | `{ "status": "CONFIRMED" }` 或 `REJECTED` |
| POST | `/api/chat` | `{ "sessionId": "可选", "message": "问题" }` |

JSON 记忆示例：

```json
{
  "id": "my-reading-20261023",
  "title": "关于阅读的自述",
  "kind": "MATERIAL",
  "occurredOn": "2026-10-23",
  "content": "我喜欢在安静的地方阅读。最近开始把自己的想法写在书摘旁边。"
}
```

`id` 可省略自动生成；`occurredOn` 可省略，未知时间保持未知。`kind` 为 `MATERIAL / BOOK / MUSIC / MOVIE / TEXT / VOICE / FOOTPRINT`。

## 验证

```sh
mvn test
mvn package
java -jar target/memory-agent-0.1.0.jar
```

最后一条命令需要环境变量已导出；jar 不自动读取 `.env`。

测试使用独立内存数据库与可编排的模型替身，覆盖上传/提取/确认/问答/删除、无来源特征丢弃、虚构引用拒绝、失败保留原文、重复整理、中文与日期检索、并发更新失效及鉴权。另外使用本机 HTTP stub 验证真实 Java HTTP 客户端的请求/响应协议与错误脱敏，并启动随机端口的完整 Spring Boot 服务，验证 HTTP 上传、鉴权、读取、无模型配置错误及删除。测试不会向模型供应商发送用户资料或产生模型费用。

## V1 边界

这是单用户、自托管原型：一个后端令牌对应一个记忆空间。没有账号、多租户、限流配额、生产运维、媒体理解、后台队列、流式输出、自动记住聊天结论、人格诊断或自动写入生活经历。不要把同一个实例/令牌提供给不同用户。LLM 文本请求会离开后端发送至配置的供应商，上传前请确认其数据使用政策；服务本身不记录提示词和模型密钥。

源码核心：`MemoryStore`（存储与失效）、`MemoryAnalysis`（摘要和特征）、`MemorySearch`（检索排序）、`MemoryAgent`（工具循环）、`CompatibleLlm`（外部 API）。后续可独立替换检索、模型适配器和存储，不必改 Android 的基本流程。
