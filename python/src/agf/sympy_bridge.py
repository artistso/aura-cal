"""Bridge between AGF documents and SymPy (SPEC §10, roadmap gate G3).

This is the interoperability surface that matters for adoption: it lets a researcher move a
document from Aura into the CAS ecosystem they already use, and bring a SymPy expression back
into a canvas. Unsupported constructs raise :class:`UnsupportedConstruct` rather than guessing.
"""

from __future__ import annotations

from typing import Mapping

from .evaluate import evaluate, parse_literal
from .model import Document, Edge, Vertex
from .vocabulary import ACCENT_VARIANTS, ENCLOSURE_VARIANTS

_FUNCTION_NAMES = {
    "sin": "sin", "cos": "cos", "tan": "tan", "sec": "sec", "csc": "csc", "cot": "cot",
    "arcsin": "asin", "arccos": "acos", "arctan": "atan", "asin": "asin", "acos": "acos",
    "atan": "atan", "sinh": "sinh", "cosh": "cosh", "tanh": "tanh",
    "ln": "log", "log": "log", "log10": "log", "exp": "exp", "sqrt": "sqrt",
    "abs": "Abs", "floor": "floor", "ceil": "ceiling", "sinc": "sinc",
}


class UnsupportedConstruct(NotImplementedError):
    """Raised when a document or expression uses something the bridge does not model."""


def _require_sympy():
    try:
        import sympy  # noqa: F401
    except ImportError as exc:  # pragma: no cover - depends on the environment
        raise UnsupportedConstruct(
            "SymPy is not installed; pip install 'agf[sympy]'"
        ) from exc
    import sympy

    return sympy


def to_sympy(doc: Document, node_id: str, env: Mapping[str, float] | None = None):
    """Build a SymPy expression for the subtree rooted at ``node_id``."""
    sympy = _require_sympy()
    env = env or {}
    slots = doc.children().get(node_id, {})
    node = doc.get(node_id)
    if node is None:
        raise UnsupportedConstruct(f"no vertex {node_id!r}")

    def child(port: str):
        edge = slots.get(port)
        if edge is None:
            raise UnsupportedConstruct(f"{node.id}: port {port!r} is unbound")
        return to_sympy(doc, edge.target, env)

    kind, variant = node.kind, node.variant

    if kind == "literal":
        value = parse_literal(node.token)
        if value is None:
            raise UnsupportedConstruct(f"literal {node.token!r} has no numeric value")
        if abs(value - round(value)) < 1e-12:
            return sympy.Integer(int(round(value)))
        return sympy.Float(value)

    if kind == "variable":
        if node.token in env:
            return sympy.Float(env[node.token])
        return sympy.Symbol(node.token)

    if kind == "relation":
        left, right = child("left"), child("right")
        mapping = {
            "equal": sympy.Eq, "neq": sympy.Ne, "leq": sympy.Le, "geq": sympy.Ge,
            "approx": sympy.Eq,
        }
        if variant not in mapping:
            raise UnsupportedConstruct(f"relation {variant!r} has no SymPy equivalent")
        return mapping[variant](left, right)

    if kind == "function":
        key = _FUNCTION_NAMES.get((variant or node.token.replace("\\", "")).lower())
        if key is None:
            raise UnsupportedConstruct(f"function {variant!r} is not mapped")
        return getattr(sympy, key)(child("arg"))

    if kind == "container":
        if variant in ACCENT_VARIANTS:
            return child("body")
        if variant == "sqrt":
            return sympy.sqrt(child("body"))
        if variant == "root":
            index = sympy.Integer(2) if "upper" not in slots else child("upper")
            return sympy.Pow(child("body"), sympy.Integer(1) / index)
        if variant in ENCLOSURE_VARIANTS:
            body = child("body")
            return {
                "abs": sympy.Abs,
                "norm": sympy.Abs,
                "floor": sympy.floor,
                "ceil": sympy.ceiling,
            }[variant](body)
        if variant in ("sum", "prod"):
            body = child("body")
            if body.free_symbols:
                (symbol,) = tuple(body.free_symbols)
            else:
                symbol = sympy.Symbol("n")
            lower, upper = child("lower"), child("upper")
            if variant == "sum":
                return sympy.summation(body, (symbol, lower, upper))
            return sympy.Product(body, (symbol, lower, upper)).doit()
        raise UnsupportedConstruct(f"container {variant!r} is not mapped")

    if kind == "operator":
        if variant in ENCLOSURE_VARIANTS:
            argument = child("arg") if "arg" in slots else child("body")
            return {
                "abs": sympy.Abs,
                "norm": sympy.Abs,
                "floor": sympy.floor,
                "ceil": sympy.ceiling,
            }[variant](argument)
        if variant == "add":
            return child("left") + child("right")
        if variant == "sub":
            return child("left") - child("right")
        if variant in ("mul", "juxta"):
            return child("left") * child("right")
        if variant == "div":
            return child("left") / child("right")
        if variant == "power":
            return sympy.Pow(child("base"), child("exponent"))
        if variant == "frac":
            return child("numerator") / child("denominator")
        if variant == "binom":
            return sympy.binomial(child("numerator"), child("denominator"))
        if variant == "neg":
            return -child("arg")
        if variant == "fact":
            return sympy.factorial(child("arg"))
        if variant in ("pm", "mp"):
            raise UnsupportedConstruct("`±` denotes a set of values, not a SymPy expression")

    raise UnsupportedConstruct(f"{kind}/{variant} is not mapped")


class _Builder:
    """Accumulates vertices and edges; ids are assigned in insertion order."""

    def __init__(self) -> None:
        self.nodes: list[Vertex] = []
        self.edges: list[Edge] = []

    def add(self, kind: str, token: str, label: str, type_: str = "Expression", variant=None) -> Vertex:
        node = Vertex(
            id=f"n{len(self.nodes) + 1}",
            kind=kind,
            token=token,
            label=label,
            type=type_,
            variant=variant,
        )
        self.nodes.append(node)
        return node

    def bind(self, parent: Vertex, port: str, child: Vertex) -> None:
        self.edges.append(
            Edge(id=f"e{len(self.edges) + 1}", source=parent.id, port=port, target=child.id)
        )

    def document(self) -> Document:
        return Document(nodes=self.nodes, edges=self.edges)


def from_sympy(expression) -> tuple[Document, str]:
    """Convert a SymPy expression into an AGF document. Returns ``(document, root_id)``."""
    sympy = _require_sympy()
    builder = _Builder()

    def build(expr) -> Vertex:
        if isinstance(expr, sympy.Integer):
            return builder.add("literal", str(int(expr)), f"Literal {int(expr)}", "Scalar")
        if isinstance(expr, sympy.Rational):
            frac = builder.add("operator", "\\frac", "Fraction", "Operator", "frac")
            builder.bind(frac, "numerator", build(sympy.Integer(expr.p)))
            builder.bind(frac, "denominator", build(sympy.Integer(expr.q)))
            return frac
        if isinstance(expr, sympy.Float):
            return builder.add("literal", repr(float(expr)), f"Literal {float(expr)}", "Scalar")
        if isinstance(expr, sympy.Symbol):
            return builder.add("variable", expr.name, f"Variable {expr.name}", "Variable")
        if isinstance(expr, sympy.Pow):
            base, exponent = expr.as_base_exp()
            if exponent == sympy.Rational(1, 2):
                radical = builder.add("container", "\\sqrt{\\phantom{x}}", "Square root",
                                      "Expression", "sqrt")
                builder.bind(radical, "body", build(base))
                return radical
            power = builder.add("operator", "^", "Power", "Operator", "power")
            builder.bind(power, "base", build(base))
            builder.bind(power, "exponent", build(exponent))
            return power
        if isinstance(expr, sympy.Add):
            terms = list(expr.args)
            acc = build(terms[0])
            for term in terms[1:]:
                addition = builder.add("operator", "+", "Addition", "Operator", "add")
                builder.bind(addition, "left", acc)
                builder.bind(addition, "right", build(term))
                acc = addition
            return acc
        if isinstance(expr, sympy.Mul):
            factors = list(expr.args)
            acc = build(factors[0])
            for factor in factors[1:]:
                product = builder.add("operator", "\\times", "Product", "Operator", "mul")
                builder.bind(product, "left", acc)
                builder.bind(product, "right", build(factor))
                acc = product
            return acc
        if isinstance(expr, sympy.Abs):
            enclosure = builder.add("operator", "\\left|\\cdot\\right|", "Absolute value",
                                    "Operator", "abs")
            builder.bind(enclosure, "arg", build(expr.args[0]))
            return enclosure
        if isinstance(expr, sympy.factorial):
            fact = builder.add("operator", "!", "Factorial", "Operator", "fact")
            builder.bind(fact, "arg", build(expr.args[0]))
            return fact
        if isinstance(expr, sympy.Equality | sympy.StrictLessThan | sympy.LessThan
                      | sympy.StrictGreaterThan | sympy.GreaterThan | sympy.Unequality):
            tokens = {
                sympy.Equality: ("=", "equal"), sympy.Unequality: ("\\neq", "neq"),
                sympy.StrictLessThan: ("<", "leq"), sympy.LessThan: ("\\le", "leq"),
                sympy.StrictGreaterThan: (">", "geq"), sympy.GreaterThan: ("\\ge", "geq"),
            }
            token, variant = tokens[type(expr)]
            relation = builder.add("relation", token, "Relation", "Relation", variant)
            lhs, rhs = expr.args
            builder.bind(relation, "left", build(lhs))
            builder.bind(relation, "right", build(rhs))
            return relation

        # Named functions: sin, log, exp, ...
        inverse = {value: key for key, value in _FUNCTION_NAMES.items()}
        name = type(expr).__name__.lower()
        if type(expr) in (sympy.sin, sympy.cos, sympy.tan, sympy.exp, sympy.log,
                          sympy.sinh, sympy.cosh, sympy.tanh, sympy.asin, sympy.acos,
                          sympy.atan, sympy.floor, sympy.ceiling):
            function = builder.add("function", "\\" + name, name, "Function", name)
            builder.bind(function, "arg", build(expr.args[0]))
            return function
        raise UnsupportedConstruct(f"SymPy type {type(expr).__name__} is not mapped ({inverse})")

    root = build(expression)
    return builder.document(), root.id


def value_of(doc: Document, node_id: str, env: Mapping[str, float] | None = None) -> float | None:
    """Convenience: AGF value under ``env`` (``None`` when undefined)."""
    return evaluate(doc, node_id, env)
