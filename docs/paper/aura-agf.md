# Aura Graph Format: a typed, port-addressed document model for mathematics

**Technical report — draft for submission. 2026-10-25.**

**Author:** artistso
**Software:** https://github.com/artistso/aura-cal (Apache-2.0)
**Specification:** `docs/SPEC.md` (AGF 0.1, draft) · **Licence of this text:** CC-BY-4.0

---

## Abstract

Mathematical documents are almost always stored as text. LaTeX records typography, MathML records
layout, and proof assistants record terms inside a fixed logic. None of them carries *identity*:
which occurrence of `θ` is the same object, which subtree occupies the exponent slot of a power,
or which rewrite produced the current state. That absence is invisible for a paper and costly for
an authoring tool — a canvas, a stylus surface, a step-by-step solver, or any system that must
attach diagnostics, provenance or verification results to parts of an expression.

We describe **Aura Graph Format (AGF)**, a document model for mathematics in which a document is
a forest of typed vertices connected by *named port bindings*. Leaves may be shared, so `θ` in
`sin²θ + cos²θ` is one object; compound vertices are tree nodes, which keeps substitution and
rendering decidable. Types are declared on leaves and inferred bottom-up through an admissibility
relation that admits widening but rejects ill-typed bindings with stable diagnostic codes. An
operational semantics distinguishes *undefined* from `NaN`, and a rewriting step is required to
preserve the truth of a document over sample environments.

We report on two independent implementations — a Kotlin kernel shipped in an Android application
and a from-scratch Python package — and a committed conformance corpus of 30 documents, 8
malformed cases and 5 rewrite traces. Building the second implementation and the corpus exposed
four defects in the reference: a script-binding precedence error that corrupted every polynomial
(`a^2 + b^2 = c^2` parsed as `a^(2 + b^2 = c^2)`), a two-valued `±` that evaluated to a single
value, and two under-specifications in the format text itself. We argue that this
cross-implementation process, not feature count, is what turns a data model into a candidate
standard, and we state explicitly what our evidence does and does not support: both
implementations share an author, and there is no third-party implementation, no user study and no
external adoption.

---

## 1. Introduction

An expression is a tree; a *document* is a forest of expressions plus everything that a person or
a machine wants to say about them: which symbols are the same, which step produced which line, why
a claim is believed. Serialisation formats for mathematics have generally optimised for the first
half of that sentence.

| format | unit of representation | carries identity | carries types | carries derivation |
|---|---|---|---|---|
| LaTeX [1] | typeset text | no | no | no |
| MathML Presentation [2] | layout tree | no | no | no |
| MathML Content / OpenMath [2, 3] | operator tree | no | informal | no |
| Lean 4 [4], Coq, Isabelle/HOL [5] | typed term in a fixed logic | yes, de Bruijn | yes (dependent) | yes (proof term) |
| **AGF** | typed graph with named ports | **yes (stable ids)** | **yes (lattice + ports)** | **yes (steps)** |

The gap is not accidental: a typesetting format has no reason to name its subexpressions. But it
means that any tool which manipulates mathematics *structurally* must reconstruct structure from
text, and any tool which wants to say something about a subexpression must invent its own
addressing scheme. The consequence is familiar to anyone who has built one: the interesting state
lives in an ad-hoc internal model, and the only interoperable artefact is the rendered result.

Aura was built around the opposite assumption, for a spatial canvas with stylus input, symbolic
rewriting and numeric verification. This report extracts that internal model into a specified
format and evaluates it the only way a format can be evaluated: by writing a second implementation
from the specification, and seeing what breaks.

### Contributions

1. **A document model** (Section 3) in which vertices are mathematical objects with declared ports,
   edges are port bindings, leaves are shareable and compound vertices are tree nodes. It differs
   from a hypergraph (every edge has exactly one source port; compound vertices are single-parent)
   and from an abstract syntax tree (leaves have identity and may be shared).
2. **A typing discipline** (Section 4) with bottom-up inference over a flat lattice and an
   admissibility relation that permits expression widening while rejecting, e.g., a relation in an
   exponent. Violations are reported under stable rule codes.
3. **Operational semantics** (Section 5) with an explicit *undefined*, including the decision that
   `±` is undefined rather than one of its branches.
4. **A soundness criterion for rewriting** (Section 6): a step is admissible only if it preserves
   the truth of the document over sample environments, checked mechanically.
5. **An evaluation by cross-implementation conformance** (Section 7), including the four defects in
   the reference implementation that the process surfaced, and an honest statement of the limits of
   the evidence (Section 9).

## 2. Related work

**Presentation formats.** LaTeX [1] is a macro language for typography; its mathematical content is
implicit in convention. MathML [2] specifies two encodings: a presentation tree for rendering and a
content tree of operators. Content MathML and OpenMath [3] are the closest classical relatives of
AGF: they represent expressions as operator applications rather than layout. Neither defines
per-operator named argument slots with admissible types, nor stable identity for subexpressions.

**Proof assistants.** Lean 4 [4], Coq and Isabelle/HOL [5] represent mathematics as typed terms in a
foundational logic, with unparalleled precision. AGF is deliberately not that: it records a syntax
with types, not a proof-checked derivation in a fixed foundation, and it makes no claim about
truth. A practical consequence is scope — AGF covers informal mathematics (a sketch, a teaching
example, a partially built expression) that a proof assistant would reject as ill-formed.

**Symbolic algebra.** Computer algebra systems, exemplified by SymPy [6], operate on expression
trees with their own internal representations and no shared interchange model. AGF's intended
relationship is a bridge, not a replacement; Section 7 reports a working translation.

**Structured editors.** Editors such as GNU TeXmacs [7] demonstrate that a document can be edited
structurally while remaining typesettable — the same tension AGF addresses, resolved there inside a
single application.

**Typed syntax graphs.** The idea that a program's abstract syntax carries types and can be
addressed by stable identity underlies structured-editing and incremental-compilation literature
generally. AGF's specific instantiation is the port vocabulary: slots are declared per operator
variant, and a slot's admitted types are part of the document's contract rather than a compiler's
internal check.

## 3. The model

### 3.1 Documents

A document is `D = (V, E, τ)` with a finite vertex set `V`, a finite edge set `E ⊆ V × P × V`, and a
type assignment `τ`. A vertex `v = (id, kind, token, variant, type)` where `kind` is one of eight
values (`literal`, `variable`, `operator`, `relation`, `function`, `container`, `set`, `group`),
`token` is canonical LaTeX for the glyph or construct, and `variant` selects behaviour within the
kind. An edge `(s, p, t)` means: the subexpression rooted at `t` occupies port `p` of `s`.

Identity is by `id` and is stable across geometry changes; coordinates are presentation hints and
carry no semantics.

### 3.2 Sharing

Call a vertex *shareable* if its kind is `literal`, `variable` or `set`. A shareable vertex may
appear as the target of several edges; a non-shareable vertex must have at most one incoming edge.

This single rule is what the model is for. In `sin²θ + cos²θ`, both functions are applied to the
*same* `θ`, and a substitution or scope analysis can see that without name resolution. In an
ordinary syntax tree the two occurrences are distinct nodes and sameness is a hypothesis.

The complementary half of the rule matters as much: allowing arbitrary sharing would make
substitution ill-defined and rendering exponential in the presence of `DAG` structure. Restricting
sharing to leaves keeps `D` a forest of trees that happen to share leaves, which is why evaluation,
type inference and rendering are all linear in `|V| + |E|`.

### 3.3 Ports

Ports are declared per `(kind, variant)`. The eleven port names are `left`, `right`, `base`,
`exponent`, `subscript`, `numerator`, `denominator`, `lower`, `upper`, `body`, `arg`. For example
`power` declares `{base, exponent}` (both required, both operand-like), `frac` declares
`{numerator, denominator}`, an `abs` enclosure declares `{arg}`, and a big operator declares
`{lower, upper, body}` where the bounds are optional and admit `Scalar | Variable | Relation` — the
relation case being the conventional index notation `Σ_{n=1}^{10}`.

Declaring ports is what makes connectivity computable: given two vertices, whether they may be
joined is a local check, and a partially built expression has a well-defined set of open ports.

### 3.4 Types

The lattice is flat: `Scalar`, `Variable`, `Vector`, `Matrix`, `Set`, `Expression`, `Relation`,
`Function`, `Operator`, `Unknown`. Leaves carry declared types; compound vertices are inferred
(bottom-up). Notable cases: a power is `Matrix` if either child is, `Set` if either is, and
`Scalar` only if neither child widens; an enclosure is `Scalar` exactly when its operand is; an
operator over a set of scalar children is `Scalar`, otherwise `Expression`.

Admissibility of a binding into a port accepting set `A` is: exact membership; or `Expression ∈ A`
with an operand-like argument (expression widening); or `Variable`/`Scalar` interchange. `Unknown`
never admits. Widening is a deliberate concession to incrementality: a user who has typed `x^` and
not yet the exponent should see a warning, not an error.

## 4. Well-formedness

Seven error rules and one warning:

| code | rule |
|---|---|
| W1 | every edge names existing vertices |
| W2 | at most one binding per `(source, port)` |
| W3 | non-shareable vertices have at most one parent |
| W4 | the graph is acyclic |
| W5 | the port is declared for the source's `(kind, variant)` |
| W6 | the binding is admissible |
| W7 | a `Relation` does not occupy an operand port, except big-operator bounds |
| W8 | *(warning)* every required port is bound |

W8's severity is a design decision, not an oversight: an unbound required port describes an
expression under construction, and treating construction as error would make the format unusable
for its primary use case.

## 5. Operational semantics

A numeric value is either a real number or **undefined**. Undefined arises from division by zero, a
negative even root, a non-finite result, an unbound required port, a cycle, and — deliberately —
from `±`.

The last is worth dwelling on. `x = ±3` denotes a set of two values. An evaluator that returns `3`
is not merely imprecise; it silently discards a root, and any downstream code that consumes values
(bounds checks, plots, verification) will agree with the wrong answer. Our own reference
implementation made exactly this mistake until the second implementation's conformance run caught
it (Section 7.3). `undefined` is not `NaN`: `NaN` is a *value* in IEEE arithmetic that propagates;
undefined is the absence of a value, and a consumer must decide what to do about it.

Relations do not evaluate to numbers. A consumer that needs a truth value evaluates both sides and
compares; the conformance class C5 uses exactly this operation.

## 6. Rewriting soundness

A transformation step is admissible only if it preserves the truth of the document: for every
relation `ρ` in the document and every environment `σ` in a fixed sample set, if both the original
and the transformed document define a truth value for `ρ` under `σ`, they agree.

The criterion is intentionally weak (it samples rather than proves) and intentionally
implementation-neutral (it constrains any step a producer is willing to expose).

At the time of writing it reports no violations: the solver's steps preserve truth on all five
fixture equations across twelve environments each. We therefore present it as a *regression
criterion*, not as a discovery. The failure modes it is designed to exclude are concrete — a step
that adds a term to one side only, a step that swaps the sides of an inequality without flipping
the relation, and constant folding of a two-valued `±` subtree; the last of these did occur in the
reference implementation (Section 7.3, item 2) and was found by conformance rather than by this
criterion, before the criterion was written.

Sampling is not verification, and we do not claim otherwise. Its role is pressure: a producer that
publishes steps must keep them true on a fixed set of environments.

## 7. Implementation and conformance

### 7.1 Two implementations

The **reference** implementation is a Kotlin kernel of 6 704 lines with no Android dependencies,
embedded in an application and exercised by 60 executable specifications that run both in the app
and under Gradle. The **second** implementation is a Python package written from `docs/SPEC.md`
alone, sharing no code (2 155 lines, 55 tests): reader/writer, vocabulary, inference, validation,
evaluator, LaTeX projection, a SymPy bridge and a CLI. It is distributed as `aura-agf` with the
import name `auragf`, because `agf` is already occupied on PyPI by an unrelated project that
installs a top-level `agf` module — a format implementation should not shadow another package's
imports.

### 7.2 Corpus and results

The corpus is generated by the reference implementation (`tools/GenerateConformance.kt`) and
committed, so either side is testable without the other. It contains 30 documents with expected
types, values and LaTeX; 8 hand-authored malformed documents, one per rule; and 5 solver traces for
C5.

| class | checks | Kotlin reference | Python |
|---|---|---|---|
| C1 serialization | 30 | ✓ | 30/30 |
| C2 well-formedness + diagnostics | 43 | ✓ (60 specs) | 43/43 |
| C3 typing | 30 | ✓ | 30/30 |
| C4 evaluation | 30 | ✓ | 30/30 |
| C5 rewriting soundness | 10 | ✓ | 10/10 |
| C6 schema conformance | 30 | schema source | 30/30 |
| §7 LaTeX projection | 30 | production | 30/30 byte-identical |

C6 exists because the schema is a separate artifact from the prose specification: it validates
every corpus document against the *published* schema file rather than a copy, so the two cannot
drift apart without a failure. The published schema and the reference implementation agree today;
nothing in the design guarantees they will tomorrow, which is precisely why the check is in the
corpus rather than in a review habit.

The projection result is the sharpest agreement test: two renderers written independently produce
identical output on all 30 documents, including parenthesisation under precedence.

### 7.3 Defects surfaced by the process

The value of a second implementation is empirical, not rhetorical. Conformance found:

1. **Script-binding precedence (reference, severe).** An unbraced exponent parsed an entire
   relation, so `a^2 + b^2 = c^2` became `a^(2 + b^(2 = c^2))`. Every polynomial, every quadratic,
   and the Pythagorean identity itself were silently corrupted — with type errors as the only
   symptom. Fixed by binding one atom for an unbraced script and a full relation only for a braced
   one.
2. **`±` was evaluated (reference).** `Evaluate` returned the operand of `±`, contradicting §5 of
   the specification and, downstream, letting constant folding turn `±3` into `3`. Fixed; the
   solver now evaluates the operand while keeping both branches.
3. **Enclosure shape was under-specified (specification).** The symbol library ships `|x|` as an
   *operator* with an `arg` port, while the parser emits a *container* with a `body` port. The
   Python port could not decide which to implement, which forced §3.3 and §4.1 to state that both
   shapes exist and that inference is defined on the operand regardless.
4. **Index bounds were under-specified (specification).** `Σ_{n=1}^{10}` puts a *relation* in a
   bound port, but W7 forbade relations in operand positions and the bound ports admitted only
   scalars. The Python port raised the contradiction; the specification now lists
   `Scalar | Variable | Relation` for bounds and exempts them from W7.

Items 1 and 2 would have reached users; items 3 and 4 were specification defects that only a
fresh reading exposes. That is the argument for conformance corpora over prose.

## 8. Evaluation: what is measured, what is not

* **Agreement.** 203/203 conformance checks across seven classes, one of which validates
every corpus document against the published JSON Schema.
* **Robustness.** Input is bounded (2 048 tokens, 96 grouping levels, 192 tree levels) so that
  pathological input raises a diagnostic rather than a stack overflow, and rendering is linear in
  the number of vertices.
* **Interoperability.** A bridge translates documents to SymPy expressions and back; solving
  `2x + 3 = 11` through `sympy.solve` returns 4 (Section 7.1, code in `python/`).
* **Not measured:** adoption, usability, or recognition accuracy. A separate benchmark for the
  stylus classifier is published with its methodology (`docs/benchmarks/`); it reports 38.7%
  top-1 on synthetic templates *and states that this is not a recognition rate* — the samples and
  the classifier's rules share an author, and three of the classifier's declared symbols are
  unreachable for any input.

## 9. Threats to validity

**Single authorship.** Both implementations were written by one person. Agreement therefore
demonstrates that the specification is *decidable by a fresh reading* — it forced two ambiguities
to be resolved — but it cannot detect a shared conceptual error. Only a third-party implementation
can.

**No external adoption.** There is one application, one author, and no third-party document in the
wild. A format without independent producers is a proposal.

**Synthetic conformance.** Many of the 30 documents arise from the same parser that generated the
fixtures. Their LaTeX and types therefore test agreement, not coverage of the space of
mathematical notation.

**Scope.** AGF is a syntax with types. It does not formalise binding, substitution or proof, and
its evaluator is real-valued. Claims about mathematical meaning beyond what §5 defines would be
unsupported.

## 10. Conclusion

We presented AGF, a typed port-addressed document model for mathematics, and evaluated it by
writing a second implementation from its specification. The exercise found a severe parsing defect,
a semantic defect in `±`, and two specification ambiguities — evidence that a format's cost lives
in its edges, and that conformance corpora are the only reliable way to find them.

For AGF to become a standard, three things must happen that this report cannot supply: an
implementation written by someone else, a corpus of real mathematical documents, and a neutral
home with more than one maintainer. The specification is published, licensed permissively, and
versioned with the express purpose of making those three things possible.

## Availability

AGF 0.1 specification: `docs/SPEC.md`. JSON Schema: `docs/aura-graph.schema.json`. Kotlin kernel:
`app/src/main/java/com/example/aura/kernel/`. Python package: `python/`. Conformance corpus:
`docs/conformance/`. Ink benchmark: `docs/benchmarks/`. All code Apache-2.0; specification and this
report CC-BY-4.0.

## References

[1] L. Lamport. *LaTeX: A Document Preparation System*. Addison-Wesley, 1986.

[2] D. Carlisle, P. Ion, R. Miner (eds.). *Mathematical Markup Language (MathML) Version 3.0*.
W3C Recommendation, 21 October 2014.

[3] S. Buswell, O. Caprotti, D. Carlisle, M. Dewar, M. Gaëtan, M. Kohlhase. *The OpenMath Standard,
Version 2.0*. OpenMath Consortium, 2004.

[4] L. de Moura, S. Ullrich. The Lean 4 Theorem Prover and Programming Language. *CADE-28*,
LNCS 12699, Springer, 2021.

[5] T. Nipkow, L. C. Paulson, M. Wenzel. *Isabelle/HOL: A Proof Assistant for Higher-Order Logic*.
LNCS 2283, Springer, 2002.

[6] A. Meurer et al. SymPy: symbolic computing in Python. *PeerJ Computer Science* 3:e103, 2017.

[7] J. van der Hoeven et al. *GNU TeXmacs*. Software, https://www.texmacs.org.
