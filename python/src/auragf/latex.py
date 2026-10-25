"""LaTeX projection (SPEC §7, informative).

The projection is lossy and human-facing: two documents that denote the same mathematics may
render differently. The conformance corpus nevertheless compares this implementation against the
reference one, because a projection that disagrees in *precedence* is a bug in one of them.
"""

from __future__ import annotations

from .model import Document
from .vocabulary import (
    ACCENT_VARIANTS,
    BIG_VARIANTS,
    ENCLOSURE_VARIANTS,
    MATRIX_VARIANTS,
    POSTFIX_VARIANTS,
    PREFIX_VARIANTS,
)

_SQUARE = "\\square"

_OP_LATEX = {"add": "+", "sub": "-", "mul": "\\times", "juxta": "\\,"}

_BIG_SIGNS = {
    "sum": "\\sum", "prod": "\\prod", "coprod": "\\coprod",
    "int": "\\int", "iint": "\\iint", "iiint": "\\iiint", "oint": "\\oint",
    "lim": "\\lim", "limsup": "\\limsup", "liminf": "\\liminf",
    "sup": "\\sup", "inf": "\\inf", "max": "\\max", "min": "\\min",
    "bigcup": "\\bigcup", "bigcap": "\\bigcap", "bigoplus": "\\bigoplus",
    "bigotimes": "\\bigotimes", "det": "\\det", "gcd": "\\gcd",
    "argmin": "\\arg\\min", "argmax": "\\arg\\max", "span": "\\operatorname{span}",
}

_PREFIX_MARKERS = {
    "neg": "\\neg", "forall": "\\forall", "exists": "\\exists",
    "nabla": "\\nabla", "partial": "\\partial",
}


def _precedence(node) -> int:
    if node.kind == "relation":
        return 1
    if node.kind == "operator":
        variant = node.variant
        if variant in POSTFIX_VARIANTS or variant in PREFIX_VARIANTS:
            return 6
        if variant in ("binom", "frac"):
            return 6
        if variant in ("add", "sub"):
            return 2
        if variant in ("mul", "juxta"):
            return 3
        if variant == "power":
            return 5
        return 3
    if node.kind == "container" and node.variant in ("int", "sum"):
        return 2
    return 6


def to_latex(doc: Document, root_id: str) -> str:
    """LaTeX for the expression rooted at ``root_id``."""
    return _render(doc, root_id, frozenset(), 0)


def _render(doc: Document, node_id: str, visiting: frozenset[str], parent_precedence: int) -> str:
    node = doc.get(node_id)
    if node is None:
        return _SQUARE
    if node_id in visiting:
        return _SQUARE
    if node.kind == "group":
        inner = node.collapsed_latex or node.token
        return f"\\left({inner}\\right)"

    nxt = visiting | {node_id}
    slots = doc.children().get(node_id, {})

    def slot(port: str, precedence: int = 0) -> str:
        edge = slots.get(port)
        if edge is None:
            return _SQUARE
        return _render(doc, edge.target, nxt, precedence)

    def node_at(port: str):
        edge = slots.get(port)
        return None if edge is None else doc.get(edge.target)

    def suffix(base: str) -> str:
        text = base
        if "subscript" in slots:
            text += "_{" + _render(doc, slots["subscript"].target, nxt, 0) + "}"
        if "exponent" in slots:
            text += "^{" + _render(doc, slots["exponent"].target, nxt, 0) + "}"
        return text

    variant = node.variant or ""
    prec = 6
    tex = ""

    if node.kind == "operator" and not (
        variant in POSTFIX_VARIANTS
        or variant in PREFIX_VARIANTS
        or variant in ENCLOSURE_VARIANTS
        or variant in ("binom", "pm", "mp")
    ):
        if variant == "power":
            prec = 5
            tex = "{" + slot("base", 6) + "}^{" + slot("exponent", 6) + "}"
        elif variant == "frac":
            prec = 6
            tex = "\\frac{" + slot("numerator") + "}{" + slot("denominator") + "}"
        else:
            prec = _precedence(node)
            left = slot("left", prec)
            right = slot("right", prec + 1)
            if variant == "juxta":
                tex = f"{left}\\,{right}"
            else:
                operator = _OP_LATEX.get(variant, node.token)
                tex = f"{left} {operator} {right}"
    elif node.kind == "relation":
        prec = 1
        tex = f"{slot('left', 2)} {node.token} {slot('right', 2)}"
    elif node.kind == "function":
        prec = 6
        if "arg" in slots:
            tex = node.token + "\\left(" + _render(doc, slots["arg"].target, nxt, 0) + "\\right)"
        else:
            tex = node.token + "\\left(" + _SQUARE + "\\right)"
    elif node.kind == "container" and not (
        variant in ENCLOSURE_VARIANTS
        or variant in ACCENT_VARIANTS
        or variant in MATRIX_VARIANTS
        or variant in BIG_VARIANTS
        or variant == "root"
    ):
        if variant == "sqrt":
            tex = "\\sqrt{" + slot("body") + "}"
        else:
            tex = "\\left(" + slot("body") + "\\right)"
    else:
        tex = suffix(node.token if node.token else _SQUARE)

    # Specialised shapes. Each replaces `tex` entirely.
    if node.kind == "operator" and variant in POSTFIX_VARIANTS:
        argument = node_at("arg")
        inner = _render(doc, argument.id, nxt, 6) if argument else _SQUARE
        wrapped = (
            f"\\left({inner}\\right)"
            if argument is not None and _precedence(argument) < 6
            else inner
        )
        tex = {
            "transpose": f"{{{wrapped}}}^{{\\top}}",
            "inverse": f"{{{wrapped}}}^{{-1}}",
            "conj": f"\\overline{{{wrapped}}}",
            "prime": f"{{{wrapped}}}^{{\\prime}}",
            "doubleprime": f"{{{wrapped}}}^{{\\prime\\prime}}",
            "degree": f"{{{wrapped}}}^{{\\circ}}",
            "complement": f"{{{wrapped}}}^{{c}}",
        }.get(variant, f"{wrapped}!")
        prec = 6
    elif node.kind == "operator" and variant in ("pm", "mp") and (
        "left" in slots or "right" in slots
    ):
        tex = f"{slot('left', 2)} {node.token} {slot('right', 3)}"
        prec = 2
    elif node.kind == "operator" and variant in PREFIX_VARIANTS:
        argument = node_at("arg")
        inner = _render(doc, argument.id, nxt, 6) if argument else _SQUARE
        marker = _PREFIX_MARKERS.get(variant, node.token)
        tex = f"{marker} {inner}"
        prec = 6
    elif node.kind == "operator" and variant == "binom":
        tex = "\\binom{" + slot("numerator") + "}{" + slot("denominator") + "}"
        prec = 6
    elif node.kind == "operator" and variant in ("floor", "ceil"):
        argument = node_at("arg")
        inner = _render(doc, argument.id, nxt, 0) if argument else _SQUARE
        tex = f"\\lfloor {inner} \\rfloor" if variant == "floor" else f"\\lceil {inner} \\rceil"
        prec = 6
    elif node.kind == "operator" and variant in ("abs", "norm"):
        argument = node_at("arg")
        inner = _render(doc, argument.id, nxt, 0) if argument else _SQUARE
        tex = f"\\left|{inner}\\right|" if variant == "abs" else f"\\left\\|{inner}\\right\\|"
        prec = 6
    elif node.kind == "container" and variant in ACCENT_VARIANTS:
        tex = "\\" + variant + "{" + slot("body") + "}"
        prec = 6
    elif node.kind == "container" and variant in MATRIX_VARIANTS:
        tex = "\\begin{" + variant + "}{" + slot("body") + "}\\end{" + variant + "}"
        prec = 6
    elif node.kind == "container" and variant in ENCLOSURE_VARIANTS:
        inner = slot("body")
        tex = {
            "abs": f"\\left|{inner}\\right|",
            "norm": f"\\left\\|{inner}\\right\\|",
            "floor": f"\\lfloor {inner} \\rfloor",
        }.get(variant, f"\\lceil {inner} \\rceil")
        prec = 6
    elif node.kind == "container" and variant == "root":
        index_node = node_at("upper")
        if index_node is not None:
            tex = "\\sqrt[" + _render(doc, index_node.id, nxt, 0) + "]{" + slot("body") + "}"
        else:
            tex = "\\sqrt{" + slot("body") + "}"
        prec = 6
    elif node.kind == "container" and variant in BIG_VARIANTS:
        sign = _BIG_SIGNS.get(variant, node.token)
        limits = ""
        if "lower" in slots:
            limits += "_{" + _render(doc, slots["lower"].target, nxt, 0) + "}"
        if "upper" in slots:
            limits += "^{" + _render(doc, slots["upper"].target, nxt, 0) + "}"
        body = node_at("body")
        rendered_body = " " + _render(doc, body.id, nxt, 2) if body is not None else ""
        tex = f"{sign}{limits}{rendered_body}"
        prec = 2

    return f"\\left({tex}\\right)" if prec < parent_precedence else tex
