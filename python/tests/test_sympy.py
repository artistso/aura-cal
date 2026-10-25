"""Interoperability with SymPy (roadmap gate G3)."""

from __future__ import annotations

import pytest

sympy = pytest.importorskip("sympy")

from agf import Document, evaluate, to_latex  # noqa: E402
from agf.sympy_bridge import UnsupportedConstruct, from_sympy, to_sympy  # noqa: E402

#: Documents produced by the reference parser, with the expression they should become.
CASES = [
    ("x^2 + 2x + 1", lambda s: s.Symbol("x") ** 2 + 2 * s.Symbol("x") + 1),
    ("\\frac{6+2}{4}", lambda s: s.Integer(2)),
    ("2 + 3 \\times 4", lambda s: s.Integer(14)),
    ("\\sqrt[3]{27}", lambda s: s.Integer(3)),
    ("|x - 3|", lambda s: s.Abs(s.Symbol("x") - 3)),
    ("5!", lambda s: s.Integer(120)),
    ("\\binom{6}{2}", lambda s: s.Integer(15)),
]


@pytest.mark.parametrize("index", range(len(CASES)))
def test_document_translates_to_sympy(cases, index):
    source, expected = CASES[index]
    case = next(c for c in cases.values() if c["source"] == source)
    document = Document.from_dict(case["graph"])
    expression = to_sympy(document, case["root"])
    assert sympy.simplify(expression - expected(sympy)) == 0, f"{source} -> {expression}"


def test_relation_translates_to_equality(cases):
    case = next(c for c in cases.values() if c["source"] == "2x + 3 = 11")
    document = Document.from_dict(case["graph"])
    relation = to_sympy(document, case["root"])
    assert isinstance(relation, sympy.Equality)
    assert sympy.solve(relation, sympy.Symbol("x")) == [4]


@pytest.mark.parametrize(
    "expression",
    [
        lambda s: s.Symbol("x") ** 2 + 1,
        lambda s: s.Rational(3, 4) * s.Symbol("y"),
        lambda s: s.sin(s.Symbol("t")) + s.cos(s.Symbol("t")),
        lambda s: s.Abs(s.Symbol("z") - 1),
        lambda s: s.sqrt(s.Symbol("a")),
    ],
)
def test_sympy_round_trip(expression):
    original = expression(sympy)
    document, root = from_sympy(original)
    # The document is well formed …
    from agf import validate

    assert validate(document) == []
    # … and returns the same mathematics.
    restored = to_sympy(document, root)
    assert sympy.simplify(restored - original) == 0


def test_round_trip_preserves_shape_for_plotting():
    document, root = from_sympy(sympy.Symbol("x") ** 2 - 3)
    assert document.get(root).variant == "add"
    from agf.evaluate import evaluate as agf_evaluate

    assert agf_evaluate(document, root, {"x": 4.0}) == pytest.approx(13.0)


def test_pm_is_refused_instead_of_guessed(cases):
    case = next(c for c in cases.values() if c["source"] == "x^2 = 9")
    document = Document.from_dict(case["graph"])
    # A ± document has no single SymPy value; the bridge must say so.
    from agf.model import Edge, Vertex

    pm = Vertex(id="pm", kind="operator", token="\\pm", label="Plus-minus",
                type="Operator", variant="pm")
    three = Vertex(id="three", kind="literal", token="3", label="Literal 3", type="Scalar")
    document = Document(nodes=[pm, three], edges=[Edge(id="e1", source="pm",
                                                       port="arg", target="three")])
    with pytest.raises(UnsupportedConstruct):
        to_sympy(document, "pm")


def test_unsupported_sympy_type_is_reported():
    with pytest.raises(UnsupportedConstruct):
        from_sympy(sympy.Matrix([[1, 2], [3, 4]]))
