
from __future__ import annotations

import time
from typing import Any, Dict

from myread.error.errors import SessionError


class SessionStore:
    def __init__(self):
        self._data: Dict[str, Dict[str, Any]] = {}

    def get_or_create(self, session_id: str, system_prompt: str) -> Dict[str, Any]:
        if not session_id:
            raise SessionError("session_id 不能为空")
        if session_id not in self._data:
            self._data[session_id] = {
                "messages": [{"role": "system", "content": system_prompt}],
                "todos": [],
                "created_at": time.time(),
            }
        return self._data[session_id]

    def exists(self, session_id: str) -> bool:
        return session_id in self._data

    def clear(self, session_id: str) -> None:
        self._data.pop(session_id, None)

    def all_sessions(self) -> Dict[str, Dict[str, Any]]:
        """调试/测试用：查看所有 session。"""
        return self._data