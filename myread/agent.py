"""Agent Runtime：四步主循环。"""
from __future__ import annotations

import json
import time
from typing import Any, Dict

from myread.config import Config
from myread.core.context import truncate_observation, trim_messages
from myread.core.session import SessionStore
from myread.core.trace import Tracer
from myread.error.errors import MaxStepsExceeded
from myread.tools import get_tool_schemas, get_tool

SYSTEM_PROMPT = (
    "你是一个助手，可以算数（calculator）、搜索知识（search）、查天气（weather）。"
    "需要时调用工具，拿到结果后直接回答用户。不要编造工具结果。"
)


class AgentRuntime:
    def __init__(self, client, config: Config, sessions: SessionStore | None = None,
                 on_event=None):
        self.client = client
        self.config = config
        self.sessions = sessions or SessionStore()
        self.on_event = on_event or (lambda event, payload: None)

    def run(self, session_id: str, user_input: str) -> str:
        session = self.sessions.get_or_create(session_id, SYSTEM_PROMPT)
        tracer = Tracer(session_id)

        # 1
        session["messages"].append({"role": "user", "content": user_input})
        tracer.log("user_input", {"content": user_input})
        self.on_event("user_input", {"content": user_input})

        # loop
        for step in range(self.config.max_steps):
            resp = self.client.chat(
                messages=session["messages"],
                tools=get_tool_schemas(),
            )
            tracer.log("llm_response", {
                "content": resp.content,
                "finish_reason": resp.finish_reason,
                "tool_calls": [{"name": tc.name, "args": tc.arguments} for tc in resp.tool_calls],
                "tokens": resp.total_tokens,
                "elapsed_ms": resp.elapsed_ms,
            })
            self.on_event("llm_response", {
                "content": resp.content,
                "tool_calls": [{"name": tc.name, "args": tc.arguments} for tc in resp.tool_calls],
            })

            # 4
            if not resp.has_tool_calls:
                session["messages"].append({"role": "assistant", "content": resp.content})
                self._compress(session)
                return resp.content or "（无回复）"

            # 记录 assistant 消息（含 tool_calls）
            session["messages"].append({
                "role": "assistant",
                "content": resp.content,
                "tool_calls": [
                    {"id": tc.id, "type": "function",
                     "function": {"name": tc.name,
                                  "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}
                    for tc in resp.tool_calls
                ],
            })

            # 3
            for tc in resp.tool_calls:
                started = time.time()
                result = self._execute_tool(tc, session)
                result = truncate_observation(result, self.config.max_chars_per_observation)
                session["messages"].append({
                    "role": "tool",
                    "tool_call_id": tc.id,
                    "content": result,
                })
                elapsed_ms = int((time.time() - started) * 1000)
                tracer.log("tool_call", {
                    "name": tc.name,
                    "args": tc.arguments,
                    "result": result,
                    "elapsed_ms": elapsed_ms,
                })
                self.on_event("tool_call", {
                    "name": tc.name,
                    "args": tc.arguments,
                    "result": result,
                    "elapsed_ms": elapsed_ms,
                })

        # 超过 max_steps 仍未收敛
        tracer.log("max_steps_exceeded", {"steps": self.config.max_steps})
        raise MaxStepsExceeded(f"超过最大轮次 {self.config.max_steps}，未得到最终答案")

    def _execute_tool(self, tc, session: Dict[str, Any]) -> str:
        tool = get_tool(tc.name)
        if tool is None:
            return f"未知工具：{tc.name}"
        if "__parse_error__" in tc.arguments:
            return f"参数解析失败：{tc.arguments['__parse_error__']}"
        try:
            return str(tool.func(**tc.arguments))
        except TypeError as e:
            return f"参数不匹配：{e}"
        except Exception as e:
            return f"工具执行失败：{type(e).__name__}: {e}"

    def _compress(self, session: Dict[str, Any]) -> None:
        session["messages"] = trim_messages(
            session["messages"],
            max_history=self.config.max_history_messages,
            keep_recent=self.config.keep_recent_messages,
        )