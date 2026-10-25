"""Well-formedness rules W1–W7 (SPEC §5).

A document that violates W1–W4 is structurally broken. W5–W7 mean the document is well-formed
but ill-typed at some edge. An unbound *required* port is reported at warning severity: the
expression is incomplete, not malformed.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from .model import Document
from .types import effective_type
from .vocabulary import SHAREABLE_KINDS, admits, declares_port, ports_for


class Severity(str, Enum):
    ERROR = "error"
    WARNING = "warning"
    INFO = "info"


@dataclass(frozen=True)
class Diagnostic:
    code: str
    severity: Severity
    message: str
    node_id: str | None = None
    edge_id: str | None = None

    def __str__(self) -> str:  # pragma: no cover - presentation only
        where = f" [{self.node_id}]" if self.node_id else ""
        return f"{self.code} {self.severity.value}: {self.message}{where}"


class Rule(str, Enum):
    W1 = "W1"  # dangling reference
    W2 = "W2"  # duplicate port binding
    W3 = "W3"  # non-shareable vertex with several parents
    W4 = "W4"  # cycle
    W5 = "W5"  # undeclared port
    W6 = "W6"  # inadmissible binding
    W7 = "W7"  # relation in an operand position
    W8 = "W8"  # unbound required port (warning)


def validate(doc: Document, *, include_warnings: bool = True) -> list[Diagnostic]:
    """All diagnostics for ``doc``, in a deterministic order."""
    diagnostics: list[Diagnostic] = []
    ids = {node.id for node in doc.nodes}
    by_id = doc.by_id

    # -- W1: every edge names existing vertices -------------------------------
    live_edges = []
    for edge in doc.edges:
        if edge.source not in ids or edge.target not in ids:
            diagnostics.append(
                Diagnostic(
                    Rule.W1.value,
                    Severity.ERROR,
                    f"edge {edge.id!r} references a vertex that does not exist "
                    f"(source={edge.source!r}, target={edge.target!r}).",
                    node_id=edge.source if edge.source in ids else None,
                    edge_id=edge.id,
                )
            )
        else:
            live_edges.append(edge)

    # -- W2: one binding per (source, port) -----------------------------------
    seen_ports: dict[tuple[str, str], str] = {}
    for edge in live_edges:
        key = (edge.source, edge.port)
        if key in seen_ports:
            diagnostics.append(
                Diagnostic(
                    Rule.W2.value,
                    Severity.ERROR,
                    f"port {edge.port!r} of {edge.source!r} is bound more than once "
                    f"(edges {seen_ports[key]!r} and {edge.id!r}).",
                    node_id=edge.source,
                    edge_id=edge.id,
                )
            )
        else:
            seen_ports[key] = edge.id

    # -- W3: non-shareable vertices have at most one parent -------------------
    incoming: dict[str, list[str]] = {}
    for edge in live_edges:
        incoming.setdefault(edge.target, []).append(edge.id)
    for node in doc.nodes:
        parents = incoming.get(node.id, [])
        if len(parents) > 1 and node.kind not in SHAREABLE_KINDS:
            diagnostics.append(
                Diagnostic(
                    Rule.W3.value,
                    Severity.ERROR,
                    f"{node.label or node.id} ({node.kind}) is bound in {len(parents)} places; "
                    "only literal, variable and set vertices may be shared.",
                    node_id=node.id,
                    edge_id=parents[1],
                )
            )

    # -- W4: acyclic ----------------------------------------------------------
    slots = doc.children()
    state: dict[str, int] = {}

    def visit(start: str) -> bool:
        # Iterative DFS: a long chain must not exhaust the interpreter stack either.
        stack = [(start, iter(slots.get(start, {}).values()))]
        state[start] = 1
        while stack:
            node_id, children = stack[-1]
            advanced = False
            for edge in children:
                target_state = state.get(edge.target, 0)
                if target_state == 1:
                    return True
                if target_state == 0:
                    state[edge.target] = 1
                    stack.append((edge.target, iter(slots.get(edge.target, {}).values())))
                    advanced = True
                    break
            if not advanced:
                state[node_id] = 2
                stack.pop()
        return False

    reported_cycles: set[str] = set()
    for node in doc.nodes:
        if state.get(node.id, 0) == 0 and visit(node.id):
            if node.id not in reported_cycles:
                reported_cycles.add(node.id)
                diagnostics.append(
                    Diagnostic(
                        Rule.W4.value,
                        Severity.ERROR,
                        "cyclic binding detected — the expression cannot be evaluated.",
                        node_id=node.id,
                    )
                )

    # -- W5, W6, W7 and unbound ports ----------------------------------------
    type_cache: dict[str, str] = {}
    for node in doc.nodes:
        bound = slots.get(node.id, {})
        declared = ports_for(node.kind, node.variant)
        declared_ids = {port.id for port in declared}

        for port in declared:
            if port.required and port.id not in bound:
                if include_warnings:
                    diagnostics.append(
                        Diagnostic(
                            Rule.W8.value,
                            Severity.WARNING,
                            f"{node.label or node.id}: {port.label.lower()} is still open.",
                            node_id=node.id,
                        )
                    )

        for edge in bound.values():
            if edge.id not in {e.id for e in live_edges}:
                continue
            if not declares_port(node.kind, node.variant, edge.port):
                diagnostics.append(
                    Diagnostic(
                        Rule.W5.value,
                        Severity.ERROR,
                        f"{node.label or node.id} ({node.kind}"
                        f"{'/' + node.variant if node.variant else ''}) declares no "
                        f"{edge.port!r} port.",
                        node_id=node.id,
                        edge_id=edge.id,
                    )
                )
                continue

            port_def = next((p for p in declared if p.id == edge.port), None)
            child = by_id.get(edge.target)
            if port_def is None or child is None:
                continue
            child_type = effective_type(doc, child.id, type_cache)
            if not admits(port_def.accepts, child_type).ok:
                diagnostics.append(
                    Diagnostic(
                        Rule.W6.value,
                        Severity.ERROR,
                        f"{child.label or child.id} has type {child_type}, but "
                        f"{edge.port} accepts {' | '.join(sorted(port_def.accepts))}.",
                        node_id=node.id,
                        edge_id=edge.id,
                    )
                )
            is_bound_port = edge.port in ("lower", "upper")
            if child_type == "Relation" and not is_bound_port:
                diagnostics.append(
                    Diagnostic(
                        Rule.W7.value,
                        Severity.ERROR,
                        "a relation cannot be nested directly inside another expression — "
                        "wrap it in a group.",
                        node_id=node.id,
                        edge_id=edge.id,
                    )
                )

        # Semantic rules the reference implementation also enforces (SPEC §5, SHOULD).
        if node.kind == "operator" and node.variant in ("add", "sub", "mul", "frac"):
            ports = ("numerator", "denominator") if node.variant == "frac" else ("left", "right")
            child_types = [
                effective_type(doc, bound[p].target, type_cache) for p in ports if p in bound
            ]
            if "Set" in child_types:
                diagnostics.append(
                    Diagnostic(
                        Rule.W6.value,
                        Severity.ERROR,
                        "set arithmetic is not defined for this operator; use set operations.",
                        node_id=node.id,
                    )
                )
            elif "Matrix" in child_types and any(t in ("Scalar", "Variable") for t in child_types):
                diagnostics.append(
                    Diagnostic(
                        Rule.W6.value,
                        Severity.ERROR,
                        "scalar and matrix cannot be combined element-wise here — ill-typed.",
                        node_id=node.id,
                    )
                )
        if node.kind == "operator" and node.variant == "power" and "exponent" in bound:
            exponent_type = effective_type(doc, bound["exponent"].target, type_cache)
            if exponent_type in ("Set", "Relation"):
                diagnostics.append(
                    Diagnostic(
                        Rule.W6.value,
                        Severity.ERROR,
                        f"exponent expects a scalar, received {exponent_type}.",
                        node_id=node.id,
                    )
                )

    return diagnostics


def errors(doc: Document) -> list[Diagnostic]:
    """Errors only — the predicate most integrations want."""
    return [d for d in validate(doc, include_warnings=False) if d.severity is Severity.ERROR]
