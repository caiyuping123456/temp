# myread：最小 Agent 实现

这是 Agent 方向面试题的实现代码。项目使用 Python 从零实现一个最小 Agent Runtime，不依赖 LangGraph、OpenHands 等 Agent 框架，模型通过 OpenAI-compatible Chat Completions API 调用。

## 功能

- 四步 Agent Loop：接收输入、判断工具调用、执行工具、返回结果。
- 工具注册机制，工具包含名称、描述、JSON Schema 和执行函数。
- 内置 `calculator`、`search`、`weather` 三个工具。
- 按 `session_id` 隔离多轮对话，可在 CLI 中用 `/new` 和 `/list` 切换、查看会话。
- 基础上下文控制：裁剪历史消息、截断过长的工具结果。
- 支持 LLM 重试、工具异常回传、最大执行轮次和运行 Trace。

## 运行

从仓库根目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

Copy-Item .env.example .env
# 在 .env 中填写 OPENAI_API_KEY

python -m myread.main
```

`.env` 放在仓库根目录，不要提交真实密钥。

主要环境变量：

```dotenv
OPENAI_API_KEY=sk-your-key
OPENAI_BASE_URL=https://api.siliconflow.cn/v1
OPENAI_MODEL=THUDM/GLM-4-9B-0414
OPENAI_TEMPERATURE=0.2
OPENAI_TIMEOUT=60
OPENAI_MAX_RETRIES=2

AGENT_MAX_STEPS=8
AGENT_MAX_HISTORY=40
AGENT_KEEP_RECENT=8
AGENT_MAX_OBSERVATION=1500
AGENT_VERBOSE=1
```

## 目录

```text
myread/
├── main.py                 # CLI 入口
├── agent.py                # Agent 主循环
├── config.py               # 环境配置
├── models.py               # LLM 客户端和响应模型
├── core/
│   ├── context.py          # 上下文裁剪
│   ├── session.py          # Session 存储
│   └── trace.py            # 执行记录
├── error/errors.py         # 自定义异常
└── tools/                  # 工具注册与内置工具
```

当前 Session 保存在进程内存中，进程退出后不会自动恢复。
