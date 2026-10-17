import ast
import operator

from myread.tools.registry import register_tool

_OPS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.Pow: operator.pow,
    ast.USub: operator.neg,
    ast.Mod: operator.mod,
    ast.FloorDiv: operator.floordiv,
}


def _eval_node(node: ast.AST):
    if isinstance(node, ast.Constant):
        if not isinstance(node.value, (int, float)):
            raise ValueError("只支持数字常量")
        return node.value
    if isinstance(node, ast.BinOp):
        op = _OPS.get(type(node.op))
        if not op:
            raise ValueError(f"不支持的运算符：{type(node.op).__name__}")
        return op(_eval_node(node.left), _eval_node(node.right))
    if isinstance(node, ast.UnaryOp):
        op = _OPS.get(type(node.op))
        if not op:
            raise ValueError(f"不支持的一元运算符：{type(node.op).__name__}")
        return op(_eval_node(node.operand))
    raise ValueError(f"不支持的表达式节点：{type(node).__name__}")


@register_tool(
    name="calculator",
    description="计算数学表达式，支持 + - * / ** % // 和括号。",
    parameters={
        "type": "object",
        "properties": {
            "expression": {
                "type": "string",
                "description": '数学表达式，例如 "2 + 3 * 4" 或 "(10 - 4) / 2"',
            }
        },
        "required": ["expression"],
    },
)
def calculator(expression: str) -> str:
    try:
        tree = ast.parse(expression, mode="eval")
        return str(_eval_node(tree.body))
    except Exception as e:
        return f"计算失败：{e}"


_MOCK_DB = {
    "langchain": "LangChain 是用于构建 LLM 应用的框架，支持链、工具、Agent 等。",
    "python": "Python 是一种广泛使用的高级编程语言，语法简洁，生态丰富。",
    "agent": "Agent 是能自主决策、调用工具、循环执行的 LLM 应用形态。",
    "redis": "Redis 是内存数据库，常用于缓存、session 存储和消息队列。",
}


@register_tool(
    name="search",
    description="搜索关键词，返回知识库中的相关条目（模拟搜索）。",
    parameters={
        "type": "object",
        "properties": {
            "query": {"type": "string", "description": "搜索关键词，例如 \"LangChain\""}
        },
        "required": ["query"],
    },
)
def search(query: str) -> str:
    q = query.lower()
    hits = [v for k, v in _MOCK_DB.items() if k in q or q in k]
    if hits:
        return "\n".join(hits)
    return f"未找到「{query}」的相关结果。"


_WEATHER_DB = {
    "北京": "晴，25°C，微风",
    "上海": "多云，28°C，东南风 3 级",
    "广州": "雷阵雨，30°C，湿度 85%",
    "深圳": "阴，29°C，无持续风向",
    "杭州": "小雨，22°C，东北风 2 级",
    "成都": "阴，24°C，微风",
}


@register_tool(
    name="weather",
    description="查询指定城市的天气情况（模拟数据）。",
    parameters={
        "type": "object",
        "properties": {
            "city": {"type": "string", "description": '城市名称，例如 "北京"'}
        },
        "required": ["city"],
    },
)
def weather(city: str) -> str:
    return _WEATHER_DB.get(city, f"{city}：暂无天气数据（模拟）")