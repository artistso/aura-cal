"""Effective type inference (SPEC §4.1).

Leaves carry a declared type; compound vertices are inferred bottom-up. A cycle or an unbound
required port makes a vertex infer ``Expression`` rather than fail, so partially built
expressions still type-check.
"""

from __future__ import annotations

from .model import Document, Vertex
from .vocabulary import ENCLOSURE_VARIANTS, SCALARISH


def _enclosure_child_port(kind: str) -> str:
    # Library symbols ship enclosures as operators (`arg`); the parser emits containers (`body`).
    return "arg" if kind == "operator" else "body"


def effective_type(
    doc: Document,
    node_id: str,
    cache: dict[str, str] | None = None,
    _visiting: set[str] | None = None,
) -> str:
    cache = {} if cache is None else cache
    if node_id in cache:
        return cache[node_id]
    node = doc.get(node_id)
    if node is None:
        return "Unknown"
    visiting = set() if _visiting is None else _visiting
    if node_id in visiting:
        return "Expression"

    visiting.add(node_id)
    try:
        computed = _compute(doc, node, cache, visiting)
    finally:
        visiting.discard(node_id)
    cache[node_id] = computed
    return computed


def _child_type(doc: Document, node_id: str, port: str, cache, visiting) -> str | None:
    edge = doc.children().get(node_id, {}).get(port)
    if edge is None:
        return None
    return effective_type(doc, edge.target, cache, visiting)


def _compute(doc: Document, node: Vertex, cache, visiting) -> str:
    kind = node.kind

    if kind in ("literal", "variable", "set"):
        return node.type if node.type != "Unknown" else "Variable"

    if kind == "group":
        return "Expression"

    if kind == "relation":
        return "Relation"

    if kind == "function":
        return "Expression" if _child_type(doc, node.id, "arg", cache, visiting) else "Function"

    if kind in ("container", "operator") and node.variant in ENCLOSURE_VARIANTS:
        port = _enclosure_child_port(kind)
        child = _child_type(doc, node.id, port, cache, visiting)
        return "Scalar" if child in SCALARISH else "Expression"

    if kind == "container":
        return "Expression"

    if kind == "operator":
        if node.variant == "power":
            base = _child_type(doc, node.id, "base", cache, visiting)
            exponent = _child_type(doc, node.id, "exponent", cache, visiting)
            if base is None or exponent is None:
                return "Expression"
            if "Matrix" in (base, exponent):
                return "Matrix"
            if "Set" in (base, exponent):
                return "Set"
            if base in ("Expression", "Vector") or exponent == "Expression":
                return "Expression"
            return "Scalar"

        if node.variant == "frac":
            numerator = _child_type(doc, node.id, "numerator", cache, visiting)
            denominator = _child_type(doc, node.id, "denominator", cache, visiting)
            if "Matrix" in (numerator, denominator):
                return "Expression"
            if "Set" in (numerator, denominator):
                return "Set"
            if (
                numerator is not None
                and denominator is not None
                and numerator != "Expression"
                and denominator != "Expression"
            ):
                return "Scalar"
            return "Expression"

        parts = [
            _child_type(doc, node.id, "left", cache, visiting),
            _child_type(doc, node.id, "right", cache, visiting),
        ]
        if "Matrix" in parts:
            return "Matrix"
        if "Vector" in parts:
            return "Vector"
        if "Set" in parts:
            return "Set"
        if "Relation" in parts:
            return "Relation"
        if all(part in SCALARISH for part in parts):
            return "Scalar"
        return "Expression"

    return "Expression"


def infer_types(doc: Document) -> dict[str, str]:
    """Effective type for every vertex, in document order."""
    cache: dict[str, str] = {}
    return {node.id: effective_type(doc, node.id, cache) for node in doc.nodes}
