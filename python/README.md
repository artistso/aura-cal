# `agf` — Aura Graph Format for Python

An independent, from-scratch implementation of [AGF 0.1](../docs/SPEC.md): read, write,
validate, type-check, evaluate and translate typed mathematical documents. It shares no code
with the reference Kotlin implementation — that is the point.

```bash
pip install -e ".[sympy,test]"
python -m pytest
```

```python
from agf import load, validate, evaluate, to_latex

doc = load("canvas.agf.json")           # or agf.loads(json_text)
for diagnostic in validate(doc):
    print(diagnostic)                   # W6 error: ... has type Relation, but exponent accepts ...
for root in doc.roots():
    print(to_latex(doc, root.id), "->", evaluate(doc, root.id))
```

## Command line

```bash
agf validate canvas.agf.json            # W1–W8 diagnostics, exit 1 on errors
agf show canvas.agf.json                # LaTeX and value for every component
agf eval canvas.agf.json --env x=4      # numbers, and true/false for relations
agf latex canvas.agf.json
agf sympy canvas.agf.json               # translate to a SymPy expression
agf conformance                         # run SPEC §9 classes against docs/conformance
```

## What is implemented

| SPEC | Status |
|---|---|
| §2 data model, §8 codec (incl. unknown-member preservation) | yes |
| §3 vocabulary: 8 kinds, 11 ports, variant-driven port sets | yes |
| §4 type lattice and admissibility with expression widening | yes |
| §5 well-formedness W1–W7 + W8 (unbound required port, warning) | yes |
| §6 reference evaluation, including *undefined* vs `NaN` | yes |
| §7 LaTeX projection | yes (byte-identical to the reference on the corpus) |
| §9 C1–C5 conformance | yes |
| SymPy bridge (`to_sympy` / `from_sympy`) | yes, optional extra |

Not implemented: LaTeX *parsing* (that lives in the reference implementation today; a shared
parser is the obvious next step for interoperability), ink recognition, and any layout logic.

## Conformance results

```text
PASS  C1 round-trip          30/30
PASS  C2 diagnostics         8/8
PASS  C2 severity            5/5
PASS  C2 well-formedness     30/30
PASS  C3 typing              30/30
PASS  C4 evaluation          30/30
PASS  C5 rewrite soundness   9/9
PASS  P7 latex projection    30/30

172 passed, 0 failed
```

The corpus (`../docs/conformance/`) is generated from the reference implementation by
`../tools/GenerateConformance.kt` and committed, so these results are reproducible without a
JVM.

## Honest caveat

Both implementations were written by the same author. This exercises the specification (it has
already forced two ambiguities to be resolved in the text) but it cannot catch a *shared*
conceptual mistake. Independent third-party implementations are what would turn this into
evidence of interoperability rather than evidence of self-consistency.

## SymPy bridge

```python
from agf import load
from agf.sympy_bridge import to_sympy

doc = load("canvas.agf.json")
expr = to_sympy(doc, doc.roots()[0].id)      # Eq(2*x + 3, 11)
import sympy
print(sympy.solve(expr, sympy.Symbol("x")))  # [4]
```

`±` is refused rather than guessed (it denotes a set of values), and unsupported constructs
raise `UnsupportedConstruct` instead of silently producing something wrong.
