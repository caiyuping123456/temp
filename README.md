# 从零实现一个最小可用 Agent

这是一个不依赖 LangGraph、OpenHands、OpenClaw、PI 等 Agent 框架的最小 Agent 实现。核心 Runtime 使用 Python 自行实现，LLM 通过 OpenAI-compatible Chat Completions API 调用，当前接入 SiliconFlow。

> GitHub 仓库地址：待发布后替换为实际链接。  
> 当前版本是 CLI 版本，重点展示 Agent Loop、工具调用、Session 隔离和基础上下文管理。

---

## 1. 实现状态

| 能力 | 状态 | 说明 |
|---|---|---|
| 四步 Agent Loop | 已实现 | 接收输入 → 判断工具/回复 → 执行工具 → 判断继续/结束 |
| 真实 LLM API | 已实现 | SiliconFlow OpenAI-compatible 接口 |
| 工具注册机制 | 已实现 | 名称、描述、JSON Schema、执行函数 |
| calculator | 已实现 | 使用 AST 安全计算数学表达式 |
| search | 已实现 | Mock 知识库搜索 |
| weather | 已实现 | Mock 城市天气查询 |
| Session 隔离 | 已实现 | 按 `session_id` 保存独立消息历史 |
| 多轮追问 | 已实现 | 同一 Session 保留历史消息和工具结果 |
| 最大轮次限制 | 已实现 | `AGENT_MAX_STEPS` |
| 基础上下文压缩 | 已实现 | 工具结果截断 + 消息条数裁剪 |
| 异常处理 | 已实现 | LLM 重试、工具错误回灌、最大轮次异常 |
| 执行 Trace | 已实现 | Tracer 内存记录 + CLI 事件输出 |
| 自动化测试 | 待补充 | README 中提供测试用例设计和手工回归方式 |
| Session 持久化 | 待补充 | 当前 Session 只保存在进程内存中 |

---

## 2. 快速开始

### 2.1 环境要求

- Python 3.12+
- 一个可用的 OpenAI-compatible API Key
- 当前默认模型：`THUDM/GLM-4-9B-0414`
- 当前默认 Base URL：`https://api.siliconflow.cn/v1`

### 2.2 安装依赖

```powershell
cd D:\Py_Project\AgentTest

python -m venv .venv
.\.venv\Scripts\Activate.ps1

pip install -r requirements.txt
```

当前项目依赖很少。如果没有 `requirements.txt`，可以直接安装：

```powershell
pip install requests python-dotenv
```

### 2.3 配置环境变量

在项目根目录创建 `.env`：

```dotenv
OPENAI_API_KEY=sk-your-key
OPENAI_BASE_URL=https://api.siliconflow.cn/v1
OPENAI_MODEL=THUDM/GLM-4-9B-0414
OPENAI_TEMPERATURE=0.2
OPENAI_TIMEOUT=60
OPENAI_MAX_RETRIES=2

AGENT_MAX_STEPS=8
AGENT_MAX_CONTEXT_TOKENS=4000
AGENT_MAX_HISTORY=40
AGENT_KEEP_RECENT=8
AGENT_MAX_OBSERVATION=1500
AGENT_VERBOSE=1
```

注意：

- `.env` 包含真实密钥，不要提交到 GitHub。
- 如果使用其他 OpenAI-compatible 服务，只需要替换 `OPENAI_BASE_URL` 和 `OPENAI_MODEL`。
- `AGENT_MAX_CONTEXT_TOKENS` 是预留配置；当前版本实际生效的上下文控制是消息条数裁剪和单条工具结果截断，尚未接入 tokenizer 精确计数。

### 2.4 启动 CLI

```powershell
python -m myread.main
```

看到类似输出即表示启动成功：

```text
========================================
  Agent CLI（真实 LLM API）
  输入问题开始对话
  /new <id>  切换到指定 session（新窗口）
  /list      列出已有 session
  /exit      退出
========================================
```

### 2.5 真实 API 连通性验证

可以在 Python 中直接验证模型是否可用：

```powershell
python -c "from myread.config import Config; from myread.models import build_client; c=Config.from_env(); r=build_client(c); print(r.chat([{'role':'user','content':'只回复 OK'}]))"
```

如果返回 `LLMResponse` 且 `content` 为 `OK`，说明 API Key、Base URL 和模型配置正常。开发时不要把完整 API Key 写入 README、日志或提交记录。

---

## 3. CLI 与多 Session 使用

### 3.1 基本命令

| 命令 | 作用 |
|---|---|
| `/new <id>` | 创建或切换到指定 Session |
| `/new` | 自动创建新 Session |
| `/list` | 列出当前进程中的 Session |
| `/exit` / `/quit` | 退出程序 |

### 3.2 用户 A 的两个窗口示例

在同一个 CLI 进程中，可以用不同 `session_id` 模拟两个窗口：

```text
/new user-a-window-1
北京今天天气怎么样？

/new user-a-window-2
帮我写一段本周周报，并记住这个要求。

/new user-a-window-1
那上海呢？
```

`user-a-window-1` 和 `user-a-window-2` 的消息历史相互独立。切回窗口 1 后，Agent 仍然能看到窗口 1 之前的用户输入、工具调用和工具结果；窗口 2 的内容不会混入窗口 1。

### 3.3 当前 Session 的边界

- Session 数据保存在 `SessionStore` 的内存字典中。
- 进程退出后 Session 会丢失，重新启动不会自动恢复。
- 如果需要跨进程恢复，可以把 `SessionStore` 替换为 JSON、SQLite 或 Redis 存储。
- 当前版本没有 Web UI；“窗口”通过 CLI 的 Session ID 模拟。

---

## 4. 系统设计

### 4.1 目录结构

```text
AgentTest/
├── .env                       # 本地密钥配置，不提交
├── myread/
│   ├── agent.py               # Agent Runtime：四步主循环
│   ├── config.py              # 环境变量与运行参数
│   ├── main.py                # CLI 入口
│   ├── models.py              # LLM 客户端、响应与 ToolCall 数据模型
│   ├── core/
│   │   ├── context.py         # 工具结果截断、消息裁剪
│   │   ├── session.py         # Session 存储与隔离
│   │   └── trace.py           # 内存执行日志
│   ├── error/
│   │   └── errors.py          # 自定义异常
│   └── tools/
│       ├── __init__.py        # 工具注册表导出
│       ├── builtin.py         # calculator / search / weather
│       └── registry.py        # 工具注册机制与 OpenAI Schema 转换
└── README.md
```

### 4.2 四步主循环

`AgentRuntime.run()` 的核心流程如下：

1. **接收用户输入**  
   将用户输入追加到当前 Session 的 `messages`，并记录 `user_input` 事件。

2. **判断直接回复还是调用工具**  
   将当前 Session 的消息和工具 Schema 一起发送给 LLM。  
   LLM 返回 `content` 和/或 `tool_calls`。

3. **调用工具**  
   如果有 `tool_calls`：
   - 将 assistant 的 `tool_calls` 原样追加到消息历史；
   - 按工具名查找注册表；
   - 解析参数并执行工具；
   - 将结果作为 `role=tool` 的消息追加到历史；
   - 记录工具名称、参数、结果和耗时。

4. **判断继续 Loop 还是返回结果**  
   - 如果没有 `tool_calls`，把 `content` 作为最终答案返回；
   - 如果仍有 `tool_calls`，继续下一轮 LLM 调用；
   - 超过 `max_steps` 则抛出 `MaxStepsExceeded`。

对应伪代码：

```python
session.messages.append({"role": "user", "content": user_input})

for step in range(max_steps):
    response = llm.chat(session.messages, tools=tool_schemas)

    if not response.tool_calls:
        session.messages.append({"role": "assistant", "content": response.content})
        return response.content

    session.messages.append(assistant_message_with_tool_calls(response))

    for tool_call in response.tool_calls:
        result = execute_tool(tool_call)
        session.messages.append({"role": "tool", "content": result})

raise MaxStepsExceeded(...)
```

### 4.3 LLM 输出解析

`models.py` 将 OpenAI-compatible 响应解析为 `LLMResponse`：

- `content`：模型返回的文本，可能是最终答案，也可能是调用工具前的说明。
- `tool_calls`：工具名、调用 ID 和参数。
- `finish_reason`：结束原因。
- `usage`：token 使用量。
- `elapsed_ms`：请求耗时。

工具参数在 API 中通常是 JSON 字符串，因此解析时会执行：

```python
args = json.loads(raw_arguments)
```

如果 JSON 解析失败，不直接让程序崩溃，而是把 `__parse_error__` 放入参数，由工具执行层转成可观察的错误信息，再交还给 LLM 自纠错。

关于“思考过程”：

- 当前实现保留模型返回的 `content`，并在 `llm_response` Trace 中记录；
- 不伪造、不强行提取隐藏 Chain-of-Thought；
- 对可展示的思考/说明、工具调用和最终答案统一由 `content`、`tool_calls`、`finish_reason` 解析；
- 如果模型在调用工具前返回了说明文本，该文本会随 assistant 消息进入上下文。

### 4.4 工具注册机制

每个工具由以下字段组成：

```python
Tool(
    name="calculator",
    description="计算数学表达式",
    parameters={...},  # JSON Schema
    func=calculator,
)
```

注册后通过 `to_openai_schema()` 转换为 OpenAI tools 格式：

```json
{
  "type": "function",
  "function": {
    "name": "calculator",
    "description": "计算数学表达式，支持 + - * / ** % // 和括号。",
    "parameters": {
      "type": "object",
      "properties": {
        "expression": {"type": "string"}
      },
      "required": ["expression"]
    }
  }
}
```

LLM 只看到工具名称、描述和参数 Schema，具体调用哪个工具由模型自主决定。Runtime 不硬编码“什么问题必须调用什么工具”。

### 4.5 内置工具

| 工具 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `calculator` | `expression` | 计算结果 | 基于 Python AST，只允许数字和受控运算符 |
| `search` | `query` | 知识库片段 | Mock 搜索，不访问外部网络 |
| `weather` | `city` | 天气文本 | Mock 城市天气 |

`calculator` 不使用 `eval()`，而是先解析 AST，再只处理数字常量、二元运算和一元运算，避免执行任意代码。

---

## 5. Session 管理

### 5.1 数据结构

每个 Session 至少包含：

```python
{
    "messages": [
        {"role": "system", "content": "..."},
        {"role": "user", "content": "..."},
        {"role": "assistant", "content": "..."},
        {"role": "tool", "content": "..."},
    ],
    "todos": [],
    "created_at": 0.0,
}
```

`SessionStore` 以 `session_id` 为 key：

```python
session = sessions.get_or_create(session_id, system_prompt)
```

因此：

- 相同 `session_id` 会复用同一个消息列表；
- 不同 `session_id` 的消息不会混用；
- CLI 的 `/new user-a-window-1` 和 `/new user-a-window-2` 分别对应两个独立 Session；
- `/new <已有 id>` 可以切回已有 Session 继续对话。

### 5.2 为什么这样设计

把“会话状态”和“单次 Agent 执行”分开：

- Session 负责长期保存消息；
- Agent Runtime 负责一次用户请求内的循环；
- Tool 不直接修改 Session，只返回结果；
- 这样后续替换存储、增加 Web 窗口或加入长期记忆时，不需要重写主循环。

---

## 6. Context 管理

### 6.1 当前策略

当前版本采用三层基础控制：

1. **最大 Agent 轮次**  
   `AGENT_MAX_STEPS` 限制单次用户请求中 LLM → Tool → LLM 的最大循环次数，防止无限调用。

2. **单条工具结果截断**  
   `AGENT_MAX_OBSERVATION` 限制单个工具结果的最大字符数。  
   例如搜索结果很长时，只保留前若干字符，并标记“已截断”。

3. **消息历史裁剪**  
   `AGENT_MAX_HISTORY` 和 `AGENT_KEEP_RECENT` 控制历史消息数量。  
   超过上限时保留 system 消息和最近若干条非 system 消息。

### 6.2 Memory 的召回时机

这里的 Memory 指同一 Session 内的对话记忆，不是向量数据库：

- 用户每次输入时，`AgentRuntime.run()` 先根据 `session_id` 取出该 Session；
- 取出后，Session 中已有的消息会立即参与本轮第一次 LLM 调用；
- 每一轮工具调用后，新的 assistant tool_calls 和 tool 结果会继续追加到同一个消息列表；
- 因此下一次 LLM 调用能够看到本轮之前的工具结果；
- 用户下一次输入时，也会再次召回该 Session 的历史。

召回顺序是：

```text
system prompt
→ 早期 user / assistant / tool 消息
→ 最近 user / assistant / tool 消息
→ 当前 user 输入
→ 当前轮 tool 结果
```

### 6.3 Memory 的放置方式

放入上下文的信息：

- 用户输入；
- 模型可展示的 `content`；
- assistant 的 `tool_calls`；
- 工具执行结果；
- 必要的系统提示词。

不放入上下文的信息：

- Tracer 的完整内部日志；
- API Key；
- 其他 Session 的消息；
- 与当前问题无关的调试信息。

这样做的原因是：工具结果通常比模型文本更长，也更容易挤占上下文；而 assistant 的 tool_calls 和对应 tool 结果必须成对保留，否则后续模型无法正确理解调用链。

### 6.4 追问支持

纯对话追问：

```text
用户：北京天气怎么样？
Agent：北京晴，25°C，微风。
用户：那上海呢？
```

同一 Session 中保留了上一轮 weather 工具结果，模型可以结合上下文理解“那上海呢”是在继续问天气。

带工具的追问：

```text
用户：帮我算 2 + 3 * 4。
Agent：14。
用户：把刚才的结果再乘以 2。
```

同一 Session 中保留了上一轮用户输入和最终答案，模型可以继续调用 calculator，也可以在简单情况下直接基于历史计算。

### 6.5 当前压缩的局限

- 裁剪依据是消息条数，不是精确 token 数；
- 没有做 LLM 摘要；
- 被裁剪的旧消息不会进入长期记忆；
- `AGENT_MAX_CONTEXT_TOKENS` 目前只是配置预留项；
- 生产环境建议增加：tokenizer 计数、旧消息摘要、事实抽取、按相关性召回。

---

## 7. 异常处理与 Trace

### 7.1 LLM 异常

`ModelClient` 处理以下情况：

- 请求超时；
- 网络异常；
- HTTP 非 200；
- 响应不是合法 JSON；
- 响应缺少 `choices`；
- 429 和 5xx 可重试；
- 重试次数由 `OPENAI_MAX_RETRIES` 控制。

### 7.2 工具异常

工具执行层不会因为单次工具错误直接终止整个 Agent：

- 未知工具：返回“未知工具”；
- 参数 JSON 解析失败：返回解析错误；
- 参数不匹配：返回 `TypeError` 信息；
- 工具内部异常：返回异常类型和消息。

这些错误会作为 `role=tool` 的观察结果交还给 LLM，让模型有机会修正参数或换一种方式回答。

### 7.3 最大轮次异常

超过 `AGENT_MAX_STEPS` 时抛出：

```python
MaxStepsExceeded("超过最大轮次 ...，未得到最终答案")
```

CLI 捕获后打印错误，不会导致整个进程退出。

### 7.4 Trace

当前 `Tracer` 记录：

- `user_input`
- `llm_response`
- `tool_call`
- `max_steps_exceeded`

每条记录包含时间戳、工具参数、结果、耗时等信息。CLI 通过 `on_event` 实时输出模型决策和工具执行摘要。

当前 Trace 主要保存在内存中；`Config.trace_dir` 是后续接入文件落盘的预留配置。若用于生产演示，建议将 Trace 写入 JSONL 文件，并继续遵守“不记录密钥”的原则。

---

## 8. 测试用例设计

当前版本可以通过 CLI 手工回归，也可以使用 `MockClient` 构造确定性的 LLM 响应。以下用例覆盖主要需求。

### 8.1 基础对话

| 编号 | 输入 | 预期 |
|---|---|---|
| T01 | `你好` | 不调用工具，直接回复 |
| T02 | `请用一句话解释什么是 Agent` | 不调用工具，直接回复 |

### 8.2 工具调用

| 编号 | 输入 | 预期 |
|---|---|---|
| T03 | `计算 (10 - 4) / 2` | 调用 `calculator`，结果为 `3.0` |
| T04 | `LangChain 是什么？` | 调用 `search`，返回 Mock 知识库结果 |
| T05 | `北京天气怎么样？` | 调用 `weather`，返回北京天气 |
| T06 | `计算 1 / 0` | 工具返回计算错误，Agent 能解释失败原因 |

### 8.3 Session 隔离

| 编号 | 操作 | 预期 |
|---|---|---|
| T07 | `/new user-a-window-1` 后查询北京天气 | 窗口 1 保存北京天气上下文 |
| T08 | `/new user-a-window-2` 后询问周报 | 窗口 2 不包含窗口 1 的消息 |
| T09 | `/new user-a-window-1` 后问“那上海呢？” | 能基于窗口 1 历史继续追问 |
| T10 | `/list` | 两个 Session 独立列出，消息数量互不影响 |

### 8.4 上下文与轮次

| 编号 | 操作 | 预期 |
|---|---|---|
| T11 | 设置 `AGENT_MAX_STEPS=1` 后发起需要工具的请求 | 抛出 `MaxStepsExceeded` |
| T12 | 设置很小的 `AGENT_MAX_OBSERVATION`，调用 search | 工具结果被截断并带截断标记 |
| T13 | 设置很小的 `AGENT_MAX_HISTORY`，连续多轮对话 | 仅保留 system 和最近消息 |
| T14 | 同一 Session 连续追问 | 能使用之前的用户输入和工具结果 |

### 8.5 工具错误与解析

| 编号 | 操作 | 预期 |
|---|---|---|
| T15 | 使用 `MockClient` 返回未知工具名 | 返回未知工具观察值，不崩溃 |
| T16 | 使用 `MockClient` 返回非法 JSON 参数 | 返回参数解析错误观察值 |
| T17 | 使用 `MockClient` 返回缺少 `choices` 的响应 | LLM 层报错并按配置重试/终止 |

### 8.6 Mock 示例

可以用 `MockClient` 避免测试时消耗真实 API 配额：

```python
from myread.agent import AgentRuntime
from myread.config import Config
from myread.core.session import SessionStore
from myread.models import MockClient, LLMResponse, ToolCall

responses = [
    LLMResponse(
        tool_calls=[
            ToolCall(
                id="call-1",
                name="calculator",
                arguments={"expression": "2 + 3 * 4"},
            )
        ],
        finish_reason="tool_calls",
    ),
    LLMResponse(content="结果是 14", finish_reason="stop"),
]

runtime = AgentRuntime(
    MockClient(responses),
    Config(max_steps=4),
    SessionStore(),
)

print(runtime.run("test-session", "算一下 2 + 3 * 4"))
```

预期输出：

```text
结果是 14
```

> 说明：当前仓库尚未加入 `pytest` 测试文件，上表是可直接转成自动化测试的用例设计。真实 API 测试应使用独立测试 Key，并避免把密钥写入测试代码。

---

## 9. AI Prompt 与问题解决记录

### 9.1 协作边界

按项目作者说明：

- **AI 辅助部分**：搭建 Python 项目骨架、数据模型、配置加载、工具注册表、基础 Session 结构、README 初稿等；
- **本人自主实现/重点调整部分**：`AgentRuntime.run()` 的四步主循环、工具调用后的消息回填、最大轮次控制、上下文裁剪策略和异常处理流程；
- 项目没有使用 LangGraph、OpenHands、OpenClaw、PI 等 Agent 框架完成主流程；
- 真实 LLM 调用和工具执行均由本项目代码直接完成。

这样划分的原因是：框架代码可以借助 AI 快速搭建，但 Agent 的核心状态机、上下文取舍和错误恢复必须能由开发者自己解释和修改。

### 9.2 开发 Prompt 记录（整理版）

#### Prompt 1：项目脚手架

```text
请使用 Python 3.12，从零搭建一个最小 Agent 项目，不依赖任何 Agent 框架。
要求：
1. 使用 OpenAI-compatible Chat Completions API；
2. 实现工具注册机制，每个工具包含 name、description、JSON Schema；
3. 先搭建 config、models、tools、session、context、trace 等基础模块；
4. Agent 主循环由我自己实现，你只提供接口和伪代码，不要直接替我完成核心循环；
5. 代码需要能读取 .env，并且不把 API Key 写入日志。
```

#### Prompt 2：工具调用解析

```text
LLM 返回的 tool_calls.arguments 是 JSON 字符串。
请帮我设计解析逻辑：
- 正常 JSON 转成 dict；
- 解析失败时不要直接崩溃，而是返回可观察的错误；
- assistant 的 tool_calls 消息和 role=tool 的结果消息必须成对追加到消息历史；
- 请说明为什么要保留 tool_call_id。
```

#### Prompt 3：Session 隔离

```text
请设计一个最小 SessionStore：
- 用户 A 可以同时有 window-1 和 window-2；
- 每个窗口有独立的 messages；
- 切换窗口后能继续之前的对话；
- 先使用内存存储，接口要方便以后替换成 SQLite 或 Redis。
```

#### Prompt 4：上下文压缩

```text
请给出一个基础上下文压缩方案，不要求复杂摘要：
- 限制单次 Agent 最大循环轮次；
- 工具结果过长时截断；
- 历史消息过长时保留 system 和最近消息；
- 说明哪些信息应该放入 context，哪些不应该放入 context。
```

#### Prompt 5：README

```text
请根据当前代码生成 README，必须包含：
运行方式、系统设计、四步 Loop、工具注册、Session 隔离、
memory 的召回时机与放置方式、异常处理、Trace、测试用例，
以及 AI Prompt 与问题解决记录。
不要虚构代码中不存在的功能。
```
---

## 10. Agent 后端 Redis 设计（问答补充）

### 10.1 问题

传统后端架构中，后端是最后一层，Redis 的核心任务通常是给高频请求做加速。但在 Agent 时代，后端不再是最后一层，大部分业务逻辑被 Agent 处理了。在这种情况下，后端 + Agent 框架的 Redis 要如何设计？

### 10.2 回答

传统后端把 Redis 当缓存或者分布式锁来用，本质上就是在程序和数据库之间加一层缓冲来提高速度，利用的是内存读写快的特性。到了 AI 时代，Agent 逐渐接管业务层，Redis 的角色就从加速变成了 AI 的状态管理，要考虑的是怎么用 Redis 来优化 AI 的状态，比如对话存储优化、检查点、Agent 通信这些。关键区别在于，传统缓存丢了可以从数据库重建，Agent 的状态丢了就是对话中断，所以它不再是旁路的缓冲，而是运行时的状态底座。
