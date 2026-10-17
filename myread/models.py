import json
import time
from dataclasses import dataclass, field
from typing import Dict, Any, List, Optional

import requests

from myread.error.errors import LLMError


@dataclass
class ToolCall:
    id: str
    name: str
    arguments: Dict[str, Any]

@dataclass
class LLMResponse:
    content: str = ""
    model: str = ""
    usage: Dict[str,int] = field(default_factory=dict)
    finish_reason: str = ""
    elapsed_ms: int = 0
    raw: Dict[str,Any] = field(default_factory=dict)
    tool_calls: List[ToolCall] = field(default_factory=list)

    @property
    def total_tokens(self) -> int:
        return int(self.usage.get("total_tokens",0) or 0)
    @property
    def has_tool_calls(self) -> bool:
        return bool(self.tool_calls)

def _normalize_endpoint(base_url: str) -> str:
    base = (base_url or "").strip().rstrip("/")
    if not base:
        raise LLMError("base_url cannot be empty")
    if base.endswith("/chat/completions"):
        return base
    return base + "/chat/completions"

class ModelClient:

    def __init__(self,
                 api_key: str,
                 base_url: str,
                 model: str,
                 temperature: float = 0.2,
                 timeout: int = 60,
                 max_retries: int = 2) -> None:
        self.api_key = api_key
        self.endpoint = _normalize_endpoint(base_url)
        self.model = model
        self.temperature = temperature
        self.timeout = timeout
        self.max_retries = max_retries

    def chat(self,
             messages: List[Dict],
             temperature: Optional[float] = None,
             tools: Optional[List[Dict]] = None,
             **extra: Any
             ) -> LLMResponse:
        payload: Dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature if temperature is None else temperature,
            "stream": False,
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"
        payload.update(extra)
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        last_error: Optional[LLMError] = None
        for attempt in range(self.max_retries + 1):
            started = time.time()
            try:
                resp = requests.post(self.endpoint, json=payload, headers=headers, timeout=self.timeout)
            except requests.Timeout as exc:
                last_error = LLMError(f"请求超时：{exc}", retryable=True)
            except requests.RequestException as exc:
                last_error = LLMError(f"网络异常：{exc}", retryable=True)
            else:
                elapsed = int((time.time() - started) * 1000)
                result = self._handle_response(resp, elapsed)
                if result is not None:
                    return result
                last_error = LLMError(self._last_message or "请求失败", retryable=self._last_retryable)
            if attempt < self.max_retries and (last_error is None or last_error.retryable):
                time.sleep(min(0.6 * (2 ** attempt), 5))
                continue
            break
        raise last_error

    _last_message: str = ""
    _last_retryable: bool = True

    def _handle_response(self, resp, elapsed: int) -> Optional[LLMResponse]:
        status = resp.status_code
        try:
            data = resp.json()
        except ValueError:
            self._last_message = f"响应不是合法 JSON（HTTP {status}）"
            self._last_retryable = status >= 500 or status == 429
            return None

        if status != 200:
            self._last_message = f"HTTP {status}：{self._extract_error(data) or resp.text[:200]}"
            self._last_retryable = status >= 500 or status == 429
            return None

        choices = data.get("choices") or []
        if not choices:
            self._last_message = "返回体缺少 choices"
            self._last_retryable = False
            return None

        choice = choices[0]
        message = choice.get("message", {}) or {}
        content = str(message.get("content") or "").strip()

        # 解析 tool_calls：arguments 是 JSON 字符串
        tool_calls: List[ToolCall] = []
        for tc in (message.get("tool_calls") or []):
            fn = tc.get("function", {}) or {}
            raw_args = fn.get("arguments", "{}")
            try:
                args = json.loads(raw_args) if isinstance(raw_args, str) else (raw_args or {})
            except json.JSONDecodeError:
                args = {"__parse_error__": raw_args}
            tool_calls.append(ToolCall(
                id=tc.get("id", ""),
                name=fn.get("name", ""),
                arguments=args,
            ))

        usage = data.get("usage") or {}
        return LLMResponse(
            content=content,
            model=data.get("model", self.model),
            usage={
                "prompt_tokens": int(usage.get("prompt_tokens", 0) or 0),
                "completion_tokens": int(usage.get("completion_tokens", 0) or 0),
                "total_tokens": int(usage.get("total_tokens", 0) or 0),
            },
            finish_reason=str(choice.get("finish_reason", "")),
            elapsed_ms=elapsed,
            raw=data,
            tool_calls=tool_calls,
        )

    @staticmethod
    def _extract_error(data: Any) -> str:
        if isinstance(data, dict):
            err = data.get("error")
            if isinstance(err, dict):
                return str(err.get("message") or err)
            if isinstance(err, str):
                return err
        return ""

class MockClient:
    """离线客户端：按脚本返回 LLMResponse，支持 tool_calls。"""

    def __init__(self, responses: Optional[List[LLMResponse]] = None, model: str = "mock-model"):
        self.responses = list(responses or [])
        self.model = model
        self.calls: List[List[Dict]] = []

    def chat(self, messages: List[Dict], **_: Any) -> LLMResponse:
        self.calls.append(list(messages))
        if self.responses:
            resp = self.responses.pop(0)
            resp.model = self.model
            return resp
        return LLMResponse(content="（Mock 默认回复）", model=self.model, finish_reason="stop")

def build_client(config, mock: bool = False, responses=None):
    if mock:
        return MockClient(responses=responses, model=f"mock/{config.model}")
    return ModelClient(
        api_key=config.api_key, base_url=config.base_url, model=config.model,
        temperature=config.temperature, timeout=config.timeout, max_retries=config.max_retries,
    )




