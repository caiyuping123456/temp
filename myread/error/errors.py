"""异常体系：区分「可以重试」和「必须终止」，让主循环能做出不同决策。"""
from __future__ import annotations


class AgentError(Exception):
    """所有自定义异常的基类。"""

    retryable = False


class LLMError(AgentError):
    """LLM 调用失败（网络抖动、鉴权失败、返回结构异常等）。"""

    retryable = True

    def __init__(self, message: str, *, retryable: bool = True, detail: str = ""):
        super().__init__(message)
        self.retryable = retryable
        self.detail = detail


class ToolError(AgentError):
    """工具执行失败。注意：绝大多数工具错误不杀主循环，而是转成观察值让 LLM 自纠错。"""


class ParseError(AgentError):
    """LLM 输出解析失败。通常兜底为「当作最终答案」或「要求重新输出格式」。"""


class SessionError(AgentError):
    """会话不存在 / 会话隔离被破坏。"""


class MaxStepsExceeded(AgentError):
    """单次对话超过 max_steps 仍未收敛。"""

    retryable = False
