"""CLI 入口：用户持续提问，同一 session 保持上下文。"""
from __future__ import annotations

import sys

from myread.agent import AgentRuntime
from myread.config import Config
from myread.core.session import SessionStore
from myread.models import build_client


BANNER = """\
========================================
  Agent CLI（真实 LLM API）
  输入问题开始对话
  /new <id>  切换到指定 session（新窗口）
  /list      列出已有 session
  /exit      退出
========================================
"""


def make_on_event():
    """把 Agent 内部过程打印到终端。"""
    def on_event(event: str, payload: dict):
        if event == "llm_response":
            tool_calls = payload.get("tool_calls") or []
            if tool_calls:
                names = ", ".join(tc["name"] for tc in tool_calls)
                print(f"  [模型决定调用工具] {names}")
        elif event == "tool_call":
            args = payload.get("args", {})
            result = str(payload.get("result", ""))[:80]
            ms = payload.get("elapsed_ms", 0)
            print(f"  [执行工具] {payload['name']}({args}) → {result}  ({ms}ms)")
    return on_event


def main():
    config = Config.from_env()
    try:
        config.ensure_api_key()
    except RuntimeError as e:
        print(f"[配置错误] {e}")
        sys.exit(1)

    client = build_client(config, mock=False)
    store = SessionStore()
    runtime = AgentRuntime(client, config, sessions=store, on_event=make_on_event())

    session_id = "default"
    print(BANNER)
    print(f"[当前 session] {session_id}")
    print(f"[模型] {config.model}\n")

    while True:
        try:
            user_input = input("你 > ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n再见。")
            break

        if not user_input:
            continue

        if user_input in ("/exit", "/quit"):
            print("再见。")
            break

        if user_input == "/list":
            sessions = store.all_sessions()
            if not sessions:
                print("[提示] 还没有任何 session")
            for sid, data in sessions.items():
                mark = " ← 当前" if sid == session_id else ""
                print(f"  - {sid}（{len(data['messages'])} 条消息，"
                      f"{len(data['todos'])} 条待办）{mark}")
            continue

        if user_input.startswith("/new"):
            parts = user_input.split(maxsplit=1)
            session_id = parts[1] if len(parts) > 1 else f"session_{len(store.all_sessions()) + 1}"
            print(f"[切换 session] {session_id}\n")
            continue

        print()
        try:
            answer = runtime.run(session_id, user_input)
        except Exception as e:
            print(f"[错误] {type(e).__name__}: {e}\n")
            continue

        print(f"\nAgent > {answer}\n")


if __name__ == "__main__":
    main()