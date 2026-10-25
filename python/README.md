# Aura Graph Format for Python (`aura-agf`)

An independent, from-scratch implementation of [AGF 0.1](../docs/SPEC.md): read, write,
validate, type-check, evaluate and translate typed mathematical documents. It shares no code
with the reference Kotlin implementation — that is the point.

```bash
pip install aura-agf            # the import package is `auragf`, the command is `agf`
pip install "aura-agf[sympy]"   # + the SymPy bridge
```

The distribution is called `aura-agf` rather than `agf` because `agf` is already published on
PyPI by an unrelated project (and installs a top-level `agf` module). A format implementation
that shadows someone else's import is not a good citizen.

```python
from auragf import load, validate, evaluate, to_latex

doc = load("canvas.agf.json")           # or auragf.loads(json_text)
for diagnostic in validate(doc):
    print(diagnostic)                   # W6 error: ... has type Relation, but exponent accepts ...
for root in doc.roots():
    print(to_latex(doc, root.id), "->", evaluate(doc, root.id))
```

## Command line

The console script is `agf` (from the `aura-agf` distribution):

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
| §9 C1–C6 conformance | yes (C6 needs the `test` extra) |
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
PASS  C5 rewrite soundness   10/10
PASS  C6 schema              30/30
PASS  P7 latex projection    30/30

203 passed, 0 failed
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
from auragf import load
from auragf.sympy_bridge import to_sympy

doc = load("canvas.agf.json")
expr = to_sympy(doc, doc.roots()[0].id)      # Eq(2*x + 3, 11)
import sympy
print(sympy.solve(expr, sympy.Symbol("x")))  # [4]
```

`±` is refused rather than guessed (it denotes a set of values), and unsupported constructs
raise `UnsupportedConstruct` instead of silently producing something wrong.

## Publishing (maintainers)

The name `aura-agf` is unclaimed on PyPI; `agf` is not, and cannot be used. Do a dry run first so a
mistake lands on TestPyPI rather than on the real index — a deleted release keeps its version
number forever and cannot be re-uploaded.

```bash
cd python
python -m build                                   # writes dist/aura_agf-0.1.0.{whl,tar.gz}
python -m twine check dist/*                      # metadata renders (README, licence links)
python -m twine upload --repository testpypi dist/*
pip install --index-url https://test.pypi.org/simple/ \
            --extra-index-url https://pypi.org/simple/ aura-agf   # verify
python -m twine upload dist/*                     # for real
```

Then bump `version` in `pyproject.toml`, tag the commit `agf-0.1`, and attach the sdist to the
GitHub release so the paper can cite an immutable artifact as well as the package.
