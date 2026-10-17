
from __future__ import annotations

import time
from typing import Any, Dict, List


class Tracer:
    def __init__(self, session_id: str):
        self.session_id = session_id
        self.records: List[Dict[str, Any]] = []

    def log(self, event: str, payload: Dict[str, Any]) -> None:
        self.records.append({"ts": time.time(), "event": event, **payload})

    def dump(self) -> List[Dict[str, Any]]:
        """调试/测试用：返回所有记录。"""
        return self.records