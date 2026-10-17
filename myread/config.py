from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

from dotenv import load_dotenv

PROJECT_ROOT = Path(__file__).resolve().parents[1]


def _to_bool(value: str, default: bool = False) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"} if value else default


@dataclass
class Config:
    # ---- LLM ----
    api_key: str = ""
    base_url: str = "https://api.siliconflow.cn/v1"
    model: str = "THUDM/GLM-4-9B-0414"
    temperature: float = 0.2
    timeout: int = 60
    max_retries: int = 2

    # ---- Agent Loop ----
    max_steps: int = 8
    tool_timeout: int = 10

    # ---- Context / Memory ----
    max_context_tokens: int = 4000
    keep_recent_messages: int = 8
    max_history_messages: int = 40
    max_chars_per_observation: int = 1500
    max_recall_facts: int = 3

    # ---- 存储 ----
    trace_dir: Path = field(default_factory=lambda: PROJECT_ROOT / "logs")
    session_dir: Path = field(default_factory=lambda: PROJECT_ROOT / "sessions")
    docs_root: Path = field(default_factory=lambda: PROJECT_ROOT / "data" / "docs")
    verbose: bool = True

    @classmethod
    def from_env(cls, dotenv: bool = True) -> "Config":
        if dotenv:
            load_dotenv(PROJECT_ROOT / ".env", override=False)   # ← 库函数，环境变量优先
        return cls(
            api_key=os.getenv("OPENAI_API_KEY", ""),
            base_url=os.getenv("OPENAI_BASE_URL", cls.base_url),
            model=os.getenv("OPENAI_MODEL", cls.model),
            temperature=float(os.getenv("OPENAI_TEMPERATURE", str(cls.temperature))),
            timeout=int(os.getenv("OPENAI_TIMEOUT", str(cls.timeout))),
            max_retries=int(os.getenv("OPENAI_MAX_RETRIES", str(cls.max_retries))),
            max_steps=int(os.getenv("AGENT_MAX_STEPS", str(cls.max_steps))),
            tool_timeout=int(os.getenv("AGENT_TOOL_TIMEOUT", str(cls.tool_timeout))),
            max_context_tokens=int(os.getenv("AGENT_MAX_CONTEXT_TOKENS", str(cls.max_context_tokens))),
            keep_recent_messages=int(os.getenv("AGENT_KEEP_RECENT", str(cls.keep_recent_messages))),
            max_history_messages=int(os.getenv("AGENT_MAX_HISTORY", str(cls.max_history_messages))),
            max_chars_per_observation=int(
                os.getenv("AGENT_MAX_OBSERVATION", str(cls.max_chars_per_observation))
            ),
            verbose=_to_bool(os.getenv("AGENT_VERBOSE", "1"), default=True),
        )

    def ensure_api_key(self) -> str:
        if not self.api_key:
            raise RuntimeError(
                "未检测到 OPENAI_API_KEY。请复制 .env.example 为 .env 并填入 Key，"
                "或执行 export OPENAI_API_KEY=sk-xxx。"
            )
        return self.api_key

    def ensure_dirs(self) -> None:
        self.trace_dir.mkdir(parents=True, exist_ok=True)
        self.session_dir.mkdir(parents=True, exist_ok=True)
        self.docs_root.mkdir(parents=True, exist_ok=True)