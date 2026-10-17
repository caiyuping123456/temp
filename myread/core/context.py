
from __future__ import annotations

from typing import Any, Dict, List


def truncate_observation(content: str, max_chars: int) -> str:
    """单条工具结果过长时截断，防止一次工具输出撑爆 context。"""
    if len(content) <= max_chars:
        return content
    return content[:max_chars] + f"...（已截断，原长度 {len(content)}）"


def trim_messages(
    messages: List[Dict[str, Any]],
    max_history: int,
    keep_recent: int,
) -> List[Dict[str, Any]]:
    """超过 max_history 时裁剪：保留 system + 最近 keep_recent 条。"""
    if len(messages) <= max_history:
        return messages

    system_msgs = [m for m in messages if m.get("role") == "system"]
    non_system = [m for m in messages if m.get("role") != "system"]
    recent = non_system[-keep_recent:]

    return system_msgs + recent