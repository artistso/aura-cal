"""Conformance against the corpus produced by the reference Kotlin implementation (SPEC §9)."""

from __future__ import annotations

import pytest

from auragf import Document, dumps, evaluate, infer_types, loads, to_latex, validate
from auragf.conformance import run
from auragf.model import Edge, Vertex
from auragf.validate import Severity

CHECKS = (
    "C1 round-trip",
    "C2 well-formedness",
    "C2 diagnostics",
    "C2 severity",
    "C3 typing",
    "C4 evaluation",
    "C5 rewrite soundness",
    "P7 latex projection",
)


def test_full_corpus_passes(corpus_dir):
    report = run(corpus_dir)
    assert report.failed == 0, "\n" + report.render()


@pytest.mark.parametrize("check", CHECKS)
def test_each_conformance_class_passes(corpus_dir, check):
    report = run(corpus_dir)
    passed, total = report.by_check().get(check, (0, 0))
    assert total > 0, f"{check} did not run"
    assert passed == total, "\n" + report.render()


def test_valid_documents_have_no_errors(cases):
    for name, case in cases.items():
        document = Document.from_dict(case["graph"])
        errors = [d for d in validate(document, include_warnings=False)
                  if d.severity is Severity.ERROR]
        assert errors == [], f"{name}: {[str(e) for e in errors]}"


def test_types_match_reference(cases):
    for name, case in cases.items():
        document = Document.from_dict(case["graph"])
        inferred = infer_types(document)
        for node_id, expected in case["types"].items():
            assert inferred[node_id] == expected, f"{name}:{node_id}"


def test_evaluation_matches_reference(cases):
    for name, case in cases.items():
        if case.get("root") is None:
            continue
        document = Document.from_dict(case["graph"])
        actual = evaluate(document, case["root"])
        expected = case.get("value")
        if expected is None:
            assert actual is None, f"{name}: expected undefined, received {actual}"
        else:
            assert actual is not None and abs(actual - expected) <= 1e-9 * max(1.0, abs(expected))


def test_unknown_members_survive_round_trip():
    """SPEC §8: forward compatibility — a newer producer's members must not be dropped."""
    document = loads(
        """
        {
          "agf": "0.1",
          "producer": {"name": "future-aura", "version": "9.0"},
          "nodes": [
            {"id": "x", "kind": "variable", "token": "x", "label": "Variable x",
             "type": "Variable", "provenance": {"stroke": 3}}
          ],
          "edges": []
        }
        """
    )
    again = loads(dumps(document))
    assert again.extra["producer"] == {"name": "future-aura", "version": "9.0"}
    assert again.nodes[0].extra["provenance"] == {"stroke": 3}


def test_document_rejects_unknown_kind_and_vertex():
    with pytest.raises(ValueError):
        Vertex(id="a", kind="widget", token="?", label="?")
    with pytest.raises(ValueError):
        Edge(id="e", source="a", target="b", port="sideways")


def test_group_and_root_handling():
    document = Document(
        nodes=[
            Vertex(id="eq", kind="relation", token="=", label="Equality",
                   type="Relation", variant="equal"),
            Vertex(id="l", kind="variable", token="x", label="Variable x", type="Variable"),
            Vertex(id="r", kind="literal", token="1", label="Literal 1", type="Scalar"),
        ],
        edges=[
            Edge(id="e1", source="eq", port="left", target="l"),
            Edge(id="e2", source="eq", port="right", target="r"),
        ],
    )
    roots = document.roots()
    assert [r.id for r in roots] == ["eq"]
    assert to_latex(document, "eq") == "x = 1"
