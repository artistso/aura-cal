# Aura Graph Format (AGF) — v0.1 (draft)

**Status:** draft for external review. This document specifies a data format. It is *not yet a
standard*: a format becomes a standard when at least two independent implementations agree and
a community depends on it. Today there is one implementation (this repository) and zero external
implementations. Section 9 defines what would have to be true for this to change.

- Media type: `application/vnd.aura.graph+json; agf=0.1`
- Reference implementation: this repository, `app/src/main/java/com/example/aura/kernel/`
- Conformance suite: `KernelTests.kt` (60 executable specifications) + `docs/conformance/`
- Licence: Apache-2.0 (code) · CC-BY-4.0 (this document)

The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

---

## 1. Scope

AGF describes a **mathematical document as a typed, port-bound hypergraph**. A document is a set
of vertices (mathematical objects) and edges (bindings of a vertex's *port* to another vertex).
Types, ports and provenance are part of the document; text is a projection of it.

AGF is designed for:

* authoring surfaces where a human manipulates mathematical objects directly (canvas, stylus,
  drag-and-drop), not just typed input;
* tooling that needs to know *which* subtree occupies a *named slot* of an operator, without
  re-deriving it from a syntax tree or from layout;
* attaching machine-checkable claims (types, diagnostics, rewrite steps, verification verdicts)
  to a stable document identity.

### Non-goals

* **Rendering.** AGF does not mandate glyph shaping, fonts or layout algorithms. `x`/`y` are
  presentation hints (section 2.4), never semantics.
* **Semantic completeness.** AGF does not attempt to encode every mathematical concept in a
  formal logic. It is a *syntax with types*, not a proof language. See section 10 for how it
  relates to OpenMath, MathML and proof assistants.
* **Evaluation strategy.** Section 6 defines the reference evaluation semantics so that
  implementations agree on numbers, but an AGF consumer MAY ignore evaluation entirely.

---

## 2. Data model

A document is a pair `D = (V, E)` plus document metadata.

### 2.1 Vertex

| member | type | required | meaning |
|---|---|---|---|
| `id` | string | MUST | Stable identifier, unique within the document. UUID recommended. |
| `kind` | string | MUST | One of the eight kinds in §3.1. |
| `token` | string | MUST | Canonical LaTeX for the glyph or construct (`"x"`, `"+"`, `"\int"`). |
| `label` | string | MUST | Human-readable name, for accessibility and inspectors. |
| `type` | string | SHOULD | Declared mathematical type (§4). Absent means `Expression`. |
| `variant` | string | MAY | Behavioural discriminator that selects ports and semantics (§3.2). |
| `x`, `y` | number | MAY | World coordinates of the tile, in canvas points (§2.4). |
| `collapsed` | boolean | MAY | Presentation state: subtree summarised as one tile. Default `false`. |
| `collapsedLatex` | string | MAY | The summary glyph shown while collapsed. |
| `collapsedPayload` | document | MAY | The retained subtree, so collapsing is lossless. |

A vertex's identity MUST NOT change when its geometry changes. Implementations MUST NOT key
semantics on `x`/`y`.

### 2.2 Edge (port binding)

| member | type | required | meaning |
|---|---|---|---|
| `id` | string | MUST | Unique within the document. |
| `source` | string | MUST | Vertex that *owns* the port. |
| `port` | string | MUST | Port identifier from §3.3. |
| `target` | string | MUST | Vertex filling that port. |
| `role` | string | MAY | Human-readable wiring label. Defaults to the port name; purely informative. |

An edge `(s, p, t)` means "the subexpression rooted at `t` occupies port `p` of `s`". Direction
is therefore always parent → child; there are no undirected or many-to-many edges in v0.1.

### 2.3 Root and forest

A document MAY contain several disjoint components. Each component has exactly one **root**: a
vertex with no incoming edge. Consumers MUST treat a document as a forest and MUST NOT assume a
single root (a canvas holds many independent expressions).

### 2.4 Geometry

`x`/`y` are in canvas points with the origin at the document's notional top-left. They exist so
that a spatial authoring surface can round-trip. Consumers that only care about mathematics
MUST ignore them; producers that re-layout a document MAY rewrite them freely.

---

## 3. Vocabulary

### 3.1 Kinds

| wire name | meaning | shareable |
|---|---|---|
| `literal` | a number or constant (`2`, `\pi`, `\infty`) | yes |
| `variable` | a symbol bound by scope (`x`, `\theta`) | yes |
| `set` | a named set (`\mathbb{R}`) | yes |
| `operator` | a function-like symbol with fixed slots (`+`, `\frac`, `^`, `\int` is a container) | no |
| `relation` | a predicate over two operands (`=`, `\le`, `\in`) | no |
| `function` | a named function applied to an argument (`\sin`) | no |
| `container` | a construct that encloses an expression (`\sqrt`, `\int`, `\sum`, `|·|`, matrices) | no |
| `group` | a collapsed subexpression standing as one vertex | no |

*Shareable* means one vertex MAY appear under several parents — this is what lets `θ` feed both
`sin²θ` and `cos²θ` as the **same** vertex. Non-shareable vertices are tree nodes and MUST have
at most one incoming edge.

### 3.2 Variants

`variant` selects behaviour within a kind. It is an open string namespace: unknown variants MUST
be preserved on round-trip and MAY be treated as opaque. The reference implementation defines,
among others:

* operators: `add`, `sub`, `mul`, `juxta`, `frac`, `power`, `binom`, `neg`, `fact`, `pm`,
  postfix forms (`transpose`, `inverse`, `conj`, `prime`, `doubleprime`, `degree`, `complement`),
  enclosures as operators (`abs`, `norm`, `floor`, `ceil`), prefix forms (`forall`, `exists`,
  `nabla`, `partial`);
* containers: `sqrt`, `root`, `abs`, `norm`, `floor`, `ceil`, accents (`hat`, `bar`, `vec`, …),
  matrices (`matrix`, `bmatrix`, `vmatrix`, `cases`), big operators (`sum`, `prod`, `int`,
  `lim`, `bigcup`, …).

### 3.3 Ports

| wire name | owner | accepted types |
|---|---|---|
| `left`, `right` | binary operators, relations, `\pm` | operand-like |
| `base`, `exponent` | `power`, and any leaf (leaf exponents) | operand-like / scalar-ish |
| `subscript` | leaves, groups | `Scalar`, `Variable` |
| `numerator`, `denominator` | `frac`, `binom` | operand-like |
| `lower`, `upper` | big operators, `root` | `Scalar`, `Variable`, `Relation` (index bounds) |
| `body` | containers, accents, matrices, enclosures | operand-like |
| `arg` | functions, prefix/postfix operators, enclosures as operators | operand-like |

Port sets are derived from `(kind, variant)` and are **normative**: `docs/conformance/ports.json`
lists the expected port set for every library symbol. A `required` port left unbound is not a
malformed document; it is an **incomplete expression** and MUST be reported as a warning (§5).

---

## 4. Types

The type lattice is flat in v0.1 (no subtyping relation other than the widening rules in §4.2):

`Scalar`, `Variable`, `Vector`, `Matrix`, `Set`, `Expression`, `Relation`, `Function`,
`Operator`, `Unknown`.

### 4.1 Inference

Leaves carry a declared type. Compound vertices get an **effective type**:

| vertex | effective type |
|---|---|
| `literal`, `variable`, `set` | declared type, default `Variable` |
| `group` | `Expression` |
| `relation` | `Relation` |
| `function` | `Expression` if `arg` is bound, else `Function` |
| enclosure variant (`abs`, `norm`, `floor`, `ceil`) | `Scalar` if its operand is `Scalar`/`Variable`, else `Expression` |
| other containers | `Expression` |
| `power` | `Matrix` if either child is `Matrix`; `Set` if either is `Set`; `Expression` if base is `Expression`/`Vector`; else `Scalar` |
| `frac` | `Expression` if either child is `Matrix`; `Set` if either is `Set`; `Scalar` if both bounds are non-`Expression`; else `Expression` |
| other operators | `Matrix` > `Vector` > `Set` > `Relation` (first that any child has); `Scalar` if every bound child is `Scalar`/`Variable`; else `Expression` |

An unbound child makes the "every" test fail, so partially built vertices infer `Expression`.

### 4.2 Admissibility (widening)

A port with accepted-type set `A` admits a value of type `τ` iff any of:

1. `τ ∈ A` — **exact** admission;
2. `Expression ∈ A` and `τ` is operand-like (`Scalar`, `Variable`, `Vector`, `Matrix`,
   `Expression`) — expression widening;
3. `Variable ∈ A` and `τ = Scalar`;
4. `Scalar ∈ A` and `τ ∈ {Variable, Expression}`.

Otherwise the binding is **rejected** and the document is ill-typed at that edge. `Unknown`
never admits.

---

## 5. Well-formedness

A conforming **validator** MUST report violations of W1–W7 with a stable code, a vertex id and,
where applicable, an edge id. Severity: W1–W4 and W6–W7 are errors; an unbound required port is
a warning (incomplete, not malformed).

| code | rule |
|---|---|
| W1 | Every edge `source`/`target` MUST name an existing vertex. |
| W2 | A `(source, port)` pair MUST be bound at most once. |
| W3 | A non-shareable vertex (§3.1) MUST have at most one incoming edge. |
| W4 | The graph MUST be acyclic. |
| W5 | An edge's `port` MUST be declared for the source's `(kind, variant)`. |
| W6 | The binding MUST be admissible (§4.2). |
| W7 | A `Relation`-typed vertex MUST NOT occupy an operand port, except `lower`/`upper` of a big operator (index bounds such as `\sum_{n=1}^{10}`). |

Additional semantically checked rules in the reference implementation (SHOULD):

* `Set` arithmetic and mixed `Scalar`/`Matrix` element-wise operations are ill-typed.
* A `power` exponent of type `Set` or `Relation` is ill-typed.

---

## 6. Evaluation (reference semantics)

An implementation that evaluates MUST agree with these rules, or return *undefined*.

* `lit`/`var` → numeric value from the environment; `\pi`, `e`, `\varphi`, `\tau`, `\infty`
  have their usual constants.
* `frac` → `num/den`; undefined when `den = 0`.
* `power` → `base^exp`; undefined when the result is not finite or the exponent is not a
  real number.
* `add`, `sub`, `mul`, `juxta` (implicit multiplication) → the obvious arithmetic.
* functions → the named real function of `arg`.
* `sqrt` → undefined for negative radicands; `root` → real root for odd index, undefined for
  even index over a negative body.
* enclosures → `|·|`, `‖·‖` (reference: Euclidean norm of a scalar), `⌊·⌋`, `⌈·⌉`.
* big operators → require a finite integral `lower`/`upper` with `upper − lower ≤ 2000`; the
  index variable of `\sum` is taken from a `lower` relation `k = a`, otherwise `n`.
* `pm` (`±`) → **undefined**: it denotes a value set, not a value.
* relations → not numbers; a consumer that needs a truth value evaluates both sides and
  compares.
* a cycle or an unbound required port → undefined.

`undefined` is not an error: it means "this document does not denote a number under this
environment". AGF distinguishes it from `NaN` deliberately.

---

## 7. Text projection (informative)

`toLatex` is a *projection*: a lossy, human-facing rendering of a document. It is normative
enough to be tested (the conformance suite round-trips it), but no implementation is required to
produce byte-identical LaTeX.

Precedence used by the reference renderer, loosest first: relation `1`, additive `2`,
multiplicative `3`, power `5`, atomic `6`. A child is parenthesised when its precedence is lower
than its parent's requirement. Bindings render as `{base}^{exp}`, `\frac{num}{den}`,
`\sqrt[3]{y}`, `\int_{a}^{b} body`, `\left|x\right|`, `\begin{pmatrix}…\end{pmatrix}`.

Parsing LaTeX into AGF is a **conversion**, not a definition: `\frac{a+b}{2}` and
`\frac{a + b}{2}` denote the same document, and the projection is stable only up to
re-serialisation.

---

## 8. Serialization

```json
{
  "agf": "0.1",
  "nodes": [
    { "id": "n1", "kind": "operator", "type": "Operator", "variant": "frac",
      "token": "\\frac", "label": "Fraction", "x": 240.0, "y": 180.0 }
  ],
  "edges": [
    { "id": "e1", "source": "n1", "port": "numerator", "target": "n2", "role": "numerator" }
  ]
}
```

* Encoding MUST be UTF-8 JSON.
* `nodes` and `edges` MUST be present (MAY be empty arrays).
* `agf` MAY appear; when absent, `0.1` is assumed.
* Unknown members MUST be preserved by round-tripping readers and ignored by others
  (forward compatibility).
* Order of arrays is not significant.

---

## 9. Conformance

| class | requirement |
|---|---|
| **C1 Serialization** | Decode and re-encode a fixture document without loss of any member in §2.1/§2.2. |
| **C2 Well-formedness** | Report the correct W-code for each malformed fixture, and no errors for valid ones. |
| **C3 Typing** | Report the effective type of each vertex in `docs/conformance/types.json`. |
| **C4 Evaluation** | Return the specified value (or `undefined`) for each case in `docs/conformance/eval.json`. |
| **C5 Rewriting soundness** | Any transformation step an implementation exposes MUST preserve the truth of the document over sample environments. |

A claim of "AGF 0.1 conformant" MUST state which classes are implemented.

Two implementations exist today, and the corpus in `docs/conformance/` is the contract between
them. It is generated by the Kotlin reference implementation (`tools/GenerateConformance.kt`)
and committed, so either side can be checked without the other.

| class | Kotlin reference | Python (`python/`, independent) |
|---|---|---|
| C1 serialization | ✓ in-app + `gradle test` | ✓ 30/30 |
| C2 well-formedness | ✓ 60 specs | ✓ 43/43 checks |
| C3 typing | ✓ | ✓ 30/30 |
| C4 evaluation | ✓ | ✓ 30/30 |
| C5 rewriting soundness | ✓ (spec suite) | ✓ 9/9 traces, 12 environments each |
| §7 LaTeX projection | ✓ (produces the fixtures) | ✓ byte-identical 30/30 |

Run the Python side with `python -m agf.cli conformance`, or `pytest` inside `python/`.

> **Caveat.** Both implementations share an author. Cross-implementation agreement catches
> specification ambiguity — it has already forced two of them to be resolved — but not a shared
> conceptual error. Third-party implementations remain the real test (see §9.1).

### 9.1 What would make this a standard

In rough order of necessity:

1. **A second, independent implementation** — a Python reader/writer now exists (`python/`) and
   passes C1–C5. It closes the "one implementation" objection but not the "one author" one:
   a third implementation, written by someone with no access to this reasoning, is still the
   real test.
2. **Interoperability evidence** — round-trip with OpenMath/MathML on a corpus of real
   expressions, and a bridge to SymPy so a researcher can move between Aura and a CAS.
3. **External documents** — people other than the author publishing AGF files.
4. **A neutral home** — the spec under a permissive licence in a place with more than one
   maintainer, ideally a W3C Community Group or an equivalent venue, with a versioning policy.
5. **A paper** describing the model and the evaluation, so the format is citable.

---

## 10. Relationship to existing formats

| format | shape | carries types/ports | typical use |
|---|---|---|---|
| LaTeX | presentation text | no (implicit, by convention) | typesetting, authoring |
| MathML Presentation | layout tree | no | web rendering |
| MathML Content / OpenMath | operator tree | limited (no port typing) | symbolic interchange |
| Lean / Coq terms | typed tree, proof-checked | yes, in a fixed logic | formal verification |
| **AGF** | **typed graph with named ports** | **yes** | **spatial authoring, tooling, provenance** |

AGF is closest to a *typed abstract syntax graph*: like an AST, but identity-bearing,
shareable at the leaves, and independently addressable so that types, diagnostics and rewrite
steps can be attached to stable ids. It does not replace LaTeX (it projects to it) or a proof
assistant (it can target one).

---

## 11. Versioning

* `MAJOR` — a change that makes previously valid documents invalid, or changes the meaning of an
  existing member.
* `MINOR` — additive, optional to implement; readers MUST ignore unknown members and MUST
  preserve them when round-tripping.
* The vocabulary in §3 is open: new variants and new `kind` values are MINOR changes, and an
  implementation that encounters an unknown variant MUST treat it as opaque rather than fail.

## Appendix A — Reference examples

`a^2 + b^2 = c^2` (root: relation `=`):

```json
{ "agf": "0.1",
  "nodes": [
    { "id": "eq",  "kind": "relation", "type": "Relation", "variant": "equal", "token": "=", "label": "Equality" },
    { "id": "sum", "kind": "operator", "type": "Operator", "variant": "add",   "token": "+", "label": "Addition" },
    { "id": "pa",  "kind": "operator", "type": "Operator", "variant": "power", "token": "^", "label": "Power" },
    { "id": "a",   "kind": "variable", "type": "Variable", "token": "a", "label": "Variable a" },
    { "id": "two", "kind": "literal",  "type": "Scalar",   "token": "2", "label": "Literal 2" }
  ],
  "edges": [
    { "id": "e1", "source": "eq",  "port": "left",     "target": "sum" },
    { "id": "e2", "source": "eq",  "port": "right",    "target": "pc" },
    { "id": "e3", "source": "sum", "port": "left",     "target": "pa" },
    { "id": "e4", "source": "pa",  "port": "base",     "target": "a" },
    { "id": "e5", "source": "pa",  "port": "exponent", "target": "two" }
  ]
}
```

(Ellipsis: `pc`, `pb`, `b`, `c` and their decimals follow the same shape; see
`docs/conformance/` for the complete fixtures.)

Note that `two` is one vertex serving `a^2`, `b^2` and `c^2` only if an implementation chooses
to share literals — sharing is permitted, not required. `a`, `b`, `c` are distinct.
