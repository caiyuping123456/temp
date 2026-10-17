"""工具注册机制：每个工具包含名称、描述、参数 Schema。"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Dict, List


@dataclass
class Tool:
    name: str
    description: str
    parameters: Dict[str, Any]   # JSON Schema
    func: Callable

    def to_openai_schema(self) -> Dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            },
        }


TOOL_REGISTRY: Dict[str, Tool] = {}


def register_tool(name: str, description: str, parameters: Dict[str, Any]):
    """装饰器：把函数登记到全局注册表。"""
    def decorator(func: Callable) -> Callable:
        if name in TOOL_REGISTRY:
            raise ValueError(f"工具名重复：{name}")
        TOOL_REGISTRY[name] = Tool(name, description, parameters, func)
        return func
    return decorator


def get_tool_schemas() -> List[Dict[str, Any]]:
    """给 LLM 看的工具列表（OpenAI tools 格式）。"""
    return [t.to_openai_schema() for t in TOOL_REGISTRY.values()]


def get_tool(name: str) -> Tool | None:
    return TOOL_REGISTRY.get(name)