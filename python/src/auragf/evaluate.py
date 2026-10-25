"""Reference evaluation semantics (SPEC §6).

``None`` means *undefined*: the document does not denote a number under this environment. It is
deliberately distinct from ``NaN``. In particular ``±`` is undefined — it denotes a set of
values, not a value — and folding it would silently drop a root.
"""

from __future__ import annotations

import math
import re
from typing import Mapping

from .model import Document
from .vocabulary import BIG_VARIANTS, ENCLOSURE_VARIANTS, POSTFIX_VARIANTS, PREFIX_VARIANTS

_NUMBER = re.compile(r"^[+-]?\d+(\.\d+)?$")
_LEADING_DOT = re.compile(r"^[+-]?\.\d+$")
_FRACTION_TOKEN = re.compile(r"^\\[td]?frac\{([^}]+)\}\{([^}]+)\}$")
_OPERATOR_NAME = re.compile(r"^operatorname\{?|\}?$")

_MAX_ITERATIONS = 2000


def parse_literal(token: str) -> float | None:
    """Numeric value of a literal token, or ``None`` when it is not a number."""
    text = token.strip()
    if _NUMBER.match(text) or _LEADING_DOT.match(text):
        return float(text)
    if text == "\\pi":
        return math.pi
    if text == "e":
        return math.e
    if text == "\\varphi":
        return (1.0 + math.sqrt(5.0)) / 2.0
    if text == "\\sqrt{2}":
        return math.sqrt(2.0)
    if text == "\\tau":
        return 2.0 * math.pi
    match = _FRACTION_TOKEN.match(text)
    if match:
        numerator = parse_literal(match.group(1))
        denominator = parse_literal(match.group(2))
        if numerator is not None and denominator not in (None, 0.0):
            return numerator / denominator
    return None


def _factorial(value: float) -> float | None:
    if abs(value - round(value)) > 1e-9:
        return None
    n = int(round(value))
    if n < 0 or n > 170:
        return None
    return float(math.factorial(n))


def _unary(key: str, value: float) -> float | None:
    try:
        if key == "sin":
            return math.sin(value)
        if key == "cos":
            return math.cos(value)
        if key == "tan":
            return math.tan(value)
        if key == "sec":
            return 1.0 / math.cos(value)
        if key == "csc":
            return 1.0 / math.sin(value)
        if key == "cot":
            return 1.0 / math.tan(value)
        if key == "sinc":
            return 1.0 if value == 0.0 else math.sin(value) / value
        if key in ("arcsin", "asin"):
            return math.asin(value)
        if key in ("arccos", "acos"):
            return math.acos(value)
        if key in ("arctan", "atan"):
            return math.atan(value)
        if key == "sinh":
            return math.sinh(value)
        if key == "cosh":
            return math.cosh(value)
        if key == "tanh":
            return math.tanh(value)
        if key == "sech":
            return 1.0 / math.cosh(value)
        if key == "csch":
            return 1.0 / math.sinh(value)
        if key == "coth":
            return 1.0 / math.tanh(value)
        if key in ("arsinh", "asinh"):
            return math.log(value + math.sqrt(value * value + 1.0))
        if key in ("arcosh", "acosh"):
            return math.log(value + math.sqrt(value * value - 1.0)) if value >= 1.0 else None
        if key in ("artanh", "atanh"):
            return 0.5 * math.log((1.0 + value) / (1.0 - value)) if abs(value) < 1.0 else None
        if key == "ln":
            return math.log(value) if value > 0.0 else None
        if key in ("log", "log10"):
            return math.log10(value) if value > 0.0 else None
        if key == "log2":
            return math.log(value) / math.log(2.0) if value > 0.0 else None
        if key == "exp":
            return math.exp(value)
        if key == "sqrt":
            return math.sqrt(value) if value >= 0.0 else None
        if key == "abs":
            return abs(value)
        if key == "floor":
            return float(math.floor(value))
        if key == "ceil":
            return float(math.ceil(value))
        if key in ("tr", "re"):
            return value
        if key == "im":
            return 0.0
    except (ValueError, OverflowError, ZeroDivisionError):
        return None
    return None


def _finite(value: float | None) -> float | None:
    if value is None:
        return None
    return value if math.isfinite(value) else None


def _enclosure(variant: str, value: float) -> float:
    if variant == "floor":
        return float(math.floor(value))
    if variant == "ceil":
        return float(math.ceil(value))
    return abs(value)


def evaluate(
    doc: Document,
    node_id: str,
    env: Mapping[str, float] | None = None,
    _guard: frozenset[str] = frozenset(),
) -> float | None:
    """Numeric value of the expression rooted at ``node_id``, or ``None`` if undefined."""
    env = {} if env is None else env
    node = doc.get(node_id)
    if node is None or node_id in _guard:
        return None
    guard = _guard | {node_id}
    slots = doc.children().get(node_id, {})

    def child(port: str) -> float | None:
        edge = slots.get(port)
        return None if edge is None else evaluate(doc, edge.target, env, guard)

    def child_node(port: str):
        edge = slots.get(port)
        return None if edge is None else doc.get(edge.target)

    kind = node.kind
    variant = node.variant

    if kind == "literal":
        base = parse_literal(node.token)
    elif kind == "variable":
        if node.token == "e" and "e" not in env:
            base = math.e
        elif node.token in env:
            base = env[node.token]
        else:
            base = env.get(node.label)
    elif kind in ("set", "group", "relation"):
        base = None
    elif kind == "function":
        argument = child("arg")
        if argument is None:
            return None
        key = _OPERATOR_NAME.sub("", variant or node.token.replace("\\", "")).lower()
        base = _unary(key, argument)
    elif kind == "container":
        if variant in PREFIX_VARIANTS | POSTFIX_VARIANTS:  # pragma: no cover - defensive
            base = None
        elif variant in ("hat", "bar", "vec", "dot", "ddot", "tilde", "overline",
                         "underline", "check", "widehat", "widetilde"):
            base = child("body")
        elif variant == "sqrt":
            radicand = child("body")
            base = None if radicand is None or radicand < 0.0 else math.sqrt(radicand)
        elif variant == "root":
            radicand = child("body")
            if radicand is None:
                base = None
            else:
                index = child("upper")
                index = 2.0 if index is None else index
                if index == 0.0:
                    base = None
                elif radicand < 0.0 and round(index) % 2 == 0:
                    base = None
                else:
                    magnitude = abs(radicand) ** (1.0 / index)
                    base = magnitude if radicand >= 0 else -magnitude
        elif variant in ENCLOSURE_VARIANTS:
            body = child("body")
            base = None if body is None else _enclosure(variant, body)
        elif variant in BIG_VARIANTS:
            base = _evaluate_big(doc, node_id, variant, env, guard)
        else:
            base = None
    elif kind == "operator":
        if variant in ENCLOSURE_VARIANTS:
            argument = child("arg")
            if argument is None:
                argument = child("body")
            base = None if argument is None else _enclosure(variant, argument)
        elif variant == "power":
            root = child("base")
            exponent = child("exponent")
            base = None if root is None or exponent is None else _finite(root**exponent)
        elif variant == "frac":
            numerator = child("numerator")
            denominator = child("denominator")
            base = None if numerator is None or denominator in (None, 0.0) else numerator / denominator
        elif variant == "binom":
            upper = child("numerator")
            lower = child("denominator")
            if upper is None or lower is None:
                base = None
            else:
                uf, lf, df = _factorial(upper), _factorial(lower), _factorial(upper - lower)
                base = None if None in (uf, lf, df) else uf / (lf * df)
        elif variant in POSTFIX_VARIANTS:
            argument = child("arg")
            if argument is None:
                base = None
            elif variant == "fact":
                base = _factorial(argument)
            elif variant == "degree":
                base = argument * 180.0 / math.pi
            else:
                base = argument
        elif variant in PREFIX_VARIANTS:
            argument = child("arg")
            # `±` is a value set, not a value: undefined by design (SPEC §6).
            if variant in ("pm", "mp") or argument is None:
                base = None
            else:
                base = -argument if variant == "neg" else argument
        else:
            left = child("left")
            right = child("right")
            if left is None or right is None:
                base = None
            elif variant == "add":
                base = left + right
            elif variant == "sub":
                base = left - right
            elif variant in ("mul", "juxta"):
                base = left * right
            elif variant == "div":
                base = None if right == 0.0 else left / right
            else:
                base = None
    else:
        base = None

    if base is None:
        return None

    # A leaf may carry its own exponent (SPEC §3.3).
    if kind in ("literal", "variable"):
        leaf_exponent = child("exponent")
        if leaf_exponent is not None:
            return _finite(base**leaf_exponent)
    return base


def _evaluate_big(doc: Document, node_id: str, variant: str, env, guard) -> float | None:
    slots = doc.children().get(node_id, {})
    if "body" not in slots or variant not in ("sum", "prod"):
        return None
    body_id = slots["body"].target
    lower_node = doc.get(slots["lower"].target) if "lower" in slots else None
    upper_node = doc.get(slots["upper"].target) if "upper" in slots else None

    index_var = "n"
    start: float | None = None
    if lower_node is not None:
        if lower_node.kind == "relation" and lower_node.variant == "equal":
            bound = doc.child(lower_node.id, "left")
            rhs = doc.child(lower_node.id, "right")
            if bound is not None and bound.kind == "variable":
                index_var = bound.token
            start = None if rhs is None else evaluate(doc, rhs.id, env, guard)
        else:
            start = evaluate(doc, lower_node.id, env, guard)
    end = None if upper_node is None else evaluate(doc, upper_node.id, env, guard)

    if start is None or end is None:
        return evaluate(doc, body_id, env, guard)

    start_int = int(round(start))
    end_int = int(round(end))
    if abs(start - start_int) > 1e-9 or abs(end - end_int) > 1e-9:
        return None
    if end_int - start_int > _MAX_ITERATIONS:
        return None

    total = 0.0 if variant == "sum" else 1.0
    for step in range(start_int, end_int + 1):
        term = evaluate(doc, body_id, {**env, index_var: float(step)}, guard)
        if term is None or not math.isfinite(term):
            return None
        total = total + term if variant == "sum" else total * term
    return total


def evaluate_relation(
    doc: Document, node_id: str, env: Mapping[str, float] | None = None
) -> bool | None:
    """Truth value of a relation over ``env``, or ``None`` when either side is undefined."""
    node = doc.get(node_id)
    if node is None or node.kind != "relation":
        return None
    left = doc.child(node_id, "left")
    right = doc.child(node_id, "right")
    if left is None or right is None:
        return None
    lhs = evaluate(doc, left.id, env)
    rhs = evaluate(doc, right.id, env)
    if lhs is None or rhs is None:
        return None
    variant = node.variant or ""
    if variant in ("equal", "approx"):
        return abs(lhs - rhs) <= 1e-9 * max(1.0, abs(lhs), abs(rhs))
    if variant == "neq":
        return abs(lhs - rhs) > 1e-9
    if variant in ("leq", "le"):
        return lhs <= rhs + 1e-12
    if variant in ("geq", "ge"):
        return lhs >= rhs - 1e-12
    return None
