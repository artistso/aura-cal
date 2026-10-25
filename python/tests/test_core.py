"""Unit tests for the Python implementation, independent of the corpus."""

from __future__ import annotations

import pytest

from agf import Document, Edge, Vertex, evaluate, to_latex, validate
from agf.evaluate import evaluate_relation, parse_literal
from agf.types import effective_type
from agf.validate import Severity
from agf.vocabulary import admits, ports_for


def build(*specs, edges=()) -> tuple[Document, dict[str, str]]:
    """Build a document from (key, kind, token, variant, type) tuples."""
    nodes = []
    ids = {}
    for key, kind, token, variant, type_ in specs:
        vertex = Vertex(id=key, kind=kind, token=token, label=key, type=type_, variant=variant)
        nodes.append(vertex)
        ids[key] = vertex.id
    return Document(nodes=nodes, edges=[Edge(id=f"e{i}", source=s, port=p, target=t)
                                        for i, (s, p, t) in enumerate(edges)]), ids


# --------------------------------------------------------------------------- vocabulary


def test_ports_are_declared_per_variant():
    assert [p.id for p in ports_for("operator", "frac")] == ["numerator", "denominator"]
    assert [p.id for p in ports_for("operator", "power")] == ["base", "exponent"]
    assert [p.id for p in ports_for("operator", "abs")] == ["arg"]
    assert [p.id for p in ports_for("container", "sum")] == ["lower", "upper", "body"]
    assert [p.id for p in ports_for("literal", None)] == ["exponent", "subscript"]
    assert ports_for("set", None) == ()


def test_admissibility_widening_rules():
    operand = frozenset({"Scalar", "Variable", "Vector", "Matrix", "Expression"})
    assert admits(operand, "Scalar").exact
    assert admits(operand, "Relation").ok is False
    assert admits(frozenset({"Set"}), "Scalar").ok is False
    assert admits(frozenset({"Variable"}), "Scalar").ok
    assert admits(frozenset({"Scalar"}), "Expression").ok
    assert admits(operand, "Unknown").ok is False


# --------------------------------------------------------------------------- typing


def test_power_of_scalars_is_scalar():
    doc, ids = build(
        ("p", "operator", "^", "power", "Operator"),
        ("x", "variable", "x", None, "Variable"),
        ("two", "literal", "2", None, "Scalar"),
        edges=[("p", "base", "x"), ("p", "exponent", "two")],
    )
    assert effective_type(doc, "p") == "Scalar"


def test_unbound_power_infers_expression():
    doc, _ = build(("p", "operator", "^", "power", "Operator"),
                   ("x", "variable", "x", None, "Variable"),
                   edges=[("p", "base", "x")])
    assert effective_type(doc, "p") == "Expression"


def test_relation_into_operator_is_relation_typed():
    doc, _ = build(
        ("add", "operator", "+", "add", "Operator"),
        ("eq", "relation", "=", "equal", "Relation"),
        ("x", "variable", "x", None, "Variable"),
        edges=[("add", "left", "eq"), ("add", "right", "x")],
    )
    assert effective_type(doc, "add") == "Relation"


def test_enclosure_of_variable_is_scalar():
    doc, _ = build(
        ("abs", "operator", "|", "abs", "Operator"),
        ("x", "variable", "x", None, "Variable"),
        edges=[("abs", "arg", "x")],
    )
    assert effective_type(doc, "abs") == "Scalar"


# --------------------------------------------------------------------------- validation


def test_clean_document_has_no_diagnostics():
    doc, _ = build(
        ("add", "operator", "+", "add", "Operator"),
        ("x", "variable", "x", None, "Variable"),
        ("one", "literal", "1", None, "Scalar"),
        edges=[("add", "left", "x"), ("add", "right", "one")],
    )
    assert validate(doc) == []


def test_unbound_required_port_is_a_warning_not_an_error():
    doc, _ = build(("frac", "operator", "\\frac", "frac", "Operator"),
                   ("x", "variable", "x", None, "Variable"),
                   edges=[("frac", "numerator", "x")])
    diagnostics = validate(doc)
    assert [d.code for d in diagnostics] == ["W8"]
    assert diagnostics[0].severity is Severity.WARNING
    assert [d for d in validate(doc, include_warnings=False)] == []


def test_cycle_is_reported_without_recursion_error():
    doc, _ = build(
        ("a", "variable", "a", None, "Variable"),
        ("b", "variable", "b", None, "Variable"),
        edges=[("a", "exponent", "b"), ("b", "exponent", "a")],
    )
    assert "W4" in {d.code for d in validate(doc)}


def test_deep_chain_does_not_overflow():
    nodes = [Vertex(id=f"n{i}", kind="variable", token="x", label="x", type="Variable")
             for i in range(2000)]
    edges = [Edge(id=f"e{i}", source=f"n{i}", port="exponent", target=f"n{i + 1}")
             for i in range(1999)]
    document = Document(nodes=nodes, edges=edges)
    # 2000 nested exponents is a cycle-free chain; validation must terminate.
    codes = {d.code for d in validate(document)}
    assert "W4" not in codes


# --------------------------------------------------------------------------- evaluation


@pytest.mark.parametrize(
    "token,expected",
    [
        ("2", 2.0),
        ("2.5", 2.5),
        (".25", 0.25),
        ("\\pi", 3.141592653589793),
        ("\\tau", 6.283185307179586),
        ("\\varphi", 1.618033988749895),
        ("\\sqrt{2}", 1.4142135623730951),
        ("\\frac{1}{2}", 0.5),
        ("x", None),
    ],
)
def test_parse_literal(token, expected):
    actual = parse_literal(token)
    if expected is None:
        assert actual is None
    else:
        assert actual == pytest.approx(expected)


def test_sum_with_index_relation():
    doc, _ = build(
        ("sum", "container", "\\sum", "sum", "Expression"),
        ("rel", "relation", "=", "equal", "Relation"),
        ("n", "variable", "n", None, "Variable"),
        ("one", "literal", "1", None, "Scalar"),
        ("ten", "literal", "10", None, "Scalar"),
        ("body", "variable", "n", None, "Variable"),
        edges=[("sum", "lower", "rel"), ("sum", "upper", "ten"),
               ("rel", "left", "n"), ("rel", "right", "one"), ("sum", "body", "body")],
    )
    assert evaluate(doc, "sum") == pytest.approx(55.0)


def test_division_by_zero_is_undefined_not_infinity():
    doc, _ = build(
        ("frac", "operator", "\\frac", "frac", "Operator"),
        ("one", "literal", "1", None, "Scalar"),
        ("zero", "literal", "0", None, "Scalar"),
        edges=[("frac", "numerator", "one"), ("frac", "denominator", "zero")],
    )
    assert evaluate(doc, "frac") is None


def test_pm_is_undefined():
    doc, _ = build(
        ("pm", "operator", "\\pm", "pm", "Operator"),
        ("three", "literal", "3", None, "Scalar"),
        edges=[("pm", "arg", "three")],
    )
    assert evaluate(doc, "pm") is None


def test_relations_evaluate_to_truth_values():
    doc, _ = build(
        ("eq", "relation", "=", "equal", "Relation"),
        ("add", "operator", "+", "add", "Operator"),
        ("x", "variable", "x", None, "Variable"),
        ("two", "literal", "2", None, "Scalar"),
        ("ten", "literal", "10", None, "Scalar"),
        edges=[("eq", "left", "add"), ("eq", "right", "ten"),
               ("add", "left", "x"), ("add", "right", "two")],
    )
    assert evaluate_relation(doc, "eq", {"x": 8.0}) is True
    assert evaluate_relation(doc, "eq", {"x": 1.0}) is False
    assert evaluate_relation(doc, "eq", {}) is None


# --------------------------------------------------------------------------- projection


def test_latex_precedence_keeps_subtraction_grouped():
    doc, _ = build(
        ("sub", "operator", "-", "sub", "Operator"),
        ("x", "variable", "x", None, "Variable"),
        ("inner", "operator", "-", "sub", "Operator"),
        ("a", "variable", "a", None, "Variable"),
        ("b", "variable", "b", None, "Variable"),
        edges=[("sub", "left", "x"), ("sub", "right", "inner"),
               ("inner", "left", "a"), ("inner", "right", "b")],
    )
    assert to_latex(doc, "sub") == "x - \\left(a - b\\right)"
