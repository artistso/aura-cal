"""Aura Graph Format (AGF) 0.1 — reference reader/writer, validator and evaluator.

An independent Python implementation of ``docs/SPEC.md``. It exists so that the format has
more than one reader: a specification with a single implementation is a product format, not a
standard.

Quick start::

    from agf import Document, load, validate, evaluate

    doc = load("canvas.agf.json")
    for diagnostic in validate(doc):
        print(diagnostic.code, diagnostic.message)
    for root in doc.roots():
        print(doc.to_latex(root.id), "->", evaluate(doc, root.id))
"""

from .model import Document, Edge, Vertex, load, loads, dump, dumps
from .types import effective_type, infer_types
from .validate import Diagnostic, Severity, validate
from .evaluate import evaluate, evaluate_relation
from .latex import to_latex
from .vocabulary import KINDS, PORTS, SHAREABLE_KINDS

__version__ = "0.1.0"
AGF_VERSION = "0.1"

__all__ = [
    "AGF_VERSION",
    "Diagnostic",
    "Document",
    "Edge",
    "KINDS",
    "PORTS",
    "SHAREABLE_KINDS",
    "Severity",
    "Vertex",
    "dump",
    "dumps",
    "effective_type",
    "evaluate",
    "evaluate_relation",
    "infer_types",
    "load",
    "loads",
    "to_latex",
    "validate",
]
