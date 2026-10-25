"""The AGF document model and JSON codec (SPEC §2, §8).

Unknown members are preserved on round-trip, as the specification requires for forward
compatibility: a document written by a newer producer must survive an older reader.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

from .vocabulary import KINDS, PORT_NAMES, TYPES

AGF_VERSION = "0.1"

_KNOWN_VERTEX_FIELDS = {
    "id", "kind", "token", "label", "type", "variant", "x", "y",
    "collapsed", "collapsedLatex", "collapsedPayload",
}
_KNOWN_EDGE_FIELDS = {"id", "source", "port", "target", "role"}
_KNOWN_DOC_FIELDS = {"agf", "nodes", "edges"}


class AgfError(ValueError):
    """Raised when a document cannot be decoded at all (as opposed to being ill-formed)."""


@dataclass
class Vertex:
    """A mathematical object occupying a node of the document graph."""

    id: str
    kind: str
    token: str
    label: str = ""
    type: str = "Expression"
    variant: str | None = None
    x: float = 0.0
    y: float = 0.0
    collapsed: bool = False
    collapsed_latex: str | None = None
    collapsed_payload: "Document | None" = None
    extra: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.kind not in KINDS:
            raise AgfError(f"vertex {self.id!r}: unknown kind {self.kind!r}")
        if self.type not in TYPES:
            raise AgfError(f"vertex {self.id!r}: unknown type {self.type!r}")

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Vertex":
        if "id" not in data or "kind" not in data:
            raise AgfError("vertex is missing 'id' or 'kind'")
        payload = data.get("collapsedPayload")
        return cls(
            id=str(data["id"]),
            kind=str(data["kind"]),
            token=str(data.get("token", "")),
            label=str(data.get("label", "")),
            type=str(data.get("type") or "Expression"),
            variant=data.get("variant"),
            x=float(data.get("x", 0.0)),
            y=float(data.get("y", 0.0)),
            collapsed=bool(data.get("collapsed", False)),
            collapsed_latex=data.get("collapsedLatex"),
            collapsed_payload=cls._decode_payload(payload),
            extra={k: v for k, v in data.items() if k not in _KNOWN_VERTEX_FIELDS},
        )

    @staticmethod
    def _decode_payload(payload: Any) -> "Document | None":
        # The reference producer stores the retained subtree as an escaped JSON *string*;
        # a fresh producer may inline the object. Accept both.
        if payload in (None, "", {}):
            return None
        if isinstance(payload, str):
            return loads(payload)
        if isinstance(payload, dict):
            return Document.from_dict(payload)
        return None

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "id": self.id,
            "kind": self.kind,
            "type": self.type,
            "token": self.token,
            "label": self.label,
        }
        if self.variant is not None:
            out["variant"] = self.variant
        out["x"] = self.x
        out["y"] = self.y
        out["collapsed"] = self.collapsed
        if self.collapsed_latex is not None:
            out["collapsedLatex"] = self.collapsed_latex
        if self.collapsed_payload is not None:
            out["collapsedPayload"] = self.collapsed_payload.to_dict()
        out.update(self.extra)
        return out


@dataclass
class Edge:
    """A port binding: the subexpression rooted at ``target`` fills ``port`` of ``source``."""

    id: str
    source: str
    target: str
    port: str
    role: str | None = None
    extra: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.port not in PORT_NAMES:
            raise AgfError(f"edge {self.id!r}: unknown port {self.port!r}")

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Edge":
        for required in ("id", "source", "port", "target"):
            if required not in data:
                raise AgfError(f"edge is missing {required!r}: {data!r}")
        return cls(
            id=str(data["id"]),
            source=str(data["source"]),
            target=str(data["target"]),
            port=str(data["port"]),
            role=data.get("role"),
            extra={k: v for k, v in data.items() if k not in _KNOWN_EDGE_FIELDS},
        )

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "id": self.id,
            "source": self.source,
            "port": self.port,
            "target": self.target,
        }
        if self.role is not None:
            out["role"] = self.role
        out.update(self.extra)
        return out


@dataclass
class Document:
    """A forest of expressions sharing one coordinate space."""

    nodes: list[Vertex] = field(default_factory=list)
    edges: list[Edge] = field(default_factory=list)
    agf: str = AGF_VERSION
    extra: dict[str, Any] = field(default_factory=dict)

    # -- construction ------------------------------------------------------

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Document":
        if not isinstance(data, dict):
            raise AgfError("document must be a JSON object")
        nodes = [Vertex.from_dict(n) for n in data.get("nodes", [])]
        edges = [Edge.from_dict(e) for e in data.get("edges", [])]
        seen: set[str] = set()
        for node in nodes:
            if node.id in seen:
                raise AgfError(f"duplicate vertex id {node.id!r}")
            seen.add(node.id)
        return cls(
            nodes=nodes,
            edges=edges,
            agf=str(data.get("agf", AGF_VERSION)),
            extra={k: v for k, v in data.items() if k not in _KNOWN_DOC_FIELDS},
        )

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {"agf": self.agf}
        out["nodes"] = [n.to_dict() for n in self.nodes]
        out["edges"] = [e.to_dict() for e in self.edges]
        out.update(self.extra)
        return out

    # -- indexing ----------------------------------------------------------

    def vertex(self, node_id: str) -> Vertex:
        for node in self.nodes:
            if node.id == node_id:
                return node
        raise KeyError(node_id)

    def get(self, node_id: str) -> Vertex | None:
        for node in self.nodes:
            if node.id == node_id:
                return node
        return None

    @property
    def by_id(self) -> dict[str, Vertex]:
        return {n.id: n for n in self.nodes}

    def children(self) -> dict[str, dict[str, Edge]]:
        """``parent id -> {port: edge}``. First binding wins for duplicated ports."""
        slots: dict[str, dict[str, Edge]] = {}
        for edge in self.edges:
            slots.setdefault(edge.source, {}).setdefault(edge.port, edge)
        return slots

    def parents(self) -> dict[str, Edge]:
        """``child id -> incoming edge`` (first incoming edge, in document order)."""
        incoming: dict[str, Edge] = {}
        for edge in self.edges:
            incoming.setdefault(edge.target, edge)
        return incoming

    def child(self, node_id: str, port: str) -> Vertex | None:
        edge = self.children().get(node_id, {}).get(port)
        return self.get(edge.target) if edge else None

    def roots(self) -> list[Vertex]:
        """Vertices with no incoming edge — one per component (SPEC §2.3)."""
        incoming = self.parents()
        return [n for n in self.nodes if n.id not in incoming]

    def component_ids(self, start: str) -> set[str]:
        seen = {start}
        queue = [start]
        adjacency: dict[str, set[str]] = {}
        for edge in self.edges:
            adjacency.setdefault(edge.source, set()).add(edge.target)
            adjacency.setdefault(edge.target, set()).add(edge.source)
        while queue:
            current = queue.pop()
            for nxt in adjacency.get(current, ()):
                if nxt not in seen:
                    seen.add(nxt)
                    queue.append(nxt)
        return seen

    def subtree_ids(self, root_id: str) -> set[str]:
        slots = self.children()
        seen: set[str] = set()
        stack = [root_id]
        while stack:
            current = stack.pop()
            if current in seen:
                continue
            seen.add(current)
            for edge in slots.get(current, {}).values():
                stack.append(edge.target)
        return seen

    def with_payload(self, collapsed: Iterable[str], summary: Vertex) -> "Document":
        """Return a copy with ``collapsed`` detached into ``summary``'s payload."""
        ids = set(collapsed)
        return Document(
            nodes=[n for n in self.nodes if n.id not in ids] + [summary],
            edges=[e for e in self.edges if e.source not in ids and e.target not in ids],
            agf=self.agf,
            extra=dict(self.extra),
        )


# --------------------------------------------------------------------------- codec


def loads(text: str) -> Document:
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:  # pragma: no cover - passthrough of stdlib error
        raise AgfError(f"document is not valid JSON: {exc}") from exc
    return Document.from_dict(data)


def dumps(document: Document, *, indent: int | None = 2) -> str:
    return json.dumps(document.to_dict(), ensure_ascii=False, indent=indent)


def load(path: str | Path) -> Document:
    return loads(Path(path).read_text(encoding="utf-8"))


def dump(document: Document, path: str | Path) -> None:
    Path(path).write_text(dumps(document), encoding="utf-8")
