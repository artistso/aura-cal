# Roadmap

`docs/SPEC.md` sets the north star: **AGF is the mathematical object format that outlives the
app.** An app can be copied; a format can become a standard. Everything below is ordered by what
would make that true, not by what is fun to build.

The honest problem: Aura is currently a beautiful, correct, single-implementation Android canvas
with **one user (the author), one platform and zero external adopters**. That is a normal starting
point, but it means the next milestones are about *evidence*, not features.

---

## Gates

Each gate has a pass condition and a kill condition. Killed gates are not failures — they are
information that reorders everything else.

### G0 — Shipped, installable 2.1 (done when the PR is merged and uploaded)

* **Pass:** 2.1 is on the closed track; kernel specs green in CI; testers' notes reflected below.
* **Kill:** if the parser/solver fixes are not on testers' devices, nothing else matters.

### G1 — Retention: do 10 real people come back?

* **Pass:** ≥ 10 closed testers, each using Aura on **two separate days** with a real expression
  of their own (not a seeded canvas). Measured by asking, not by telemetry (the app has none).
* **Kill signal:** testers open it, admire it, and never return. If that is the result, the market
  is "a demo", and the right response is to change *who* it is for — most likely the
  authoring surface for people who already write structured math (teaching, problem sets), where
  repeated use is forced by a workflow rather than by desire.

### G2 — A second implementation (the standard gate)

* **Status:** the Python package exists (`python/`), is written independently of the Kotlin code,
  and passes C1–C5 plus the LaTeX projection (172/172 checks). **Not yet published to PyPI, and
  written by the same author** — so the gate is 80% closed, not closed.
* **Pass:** published on PyPI, and reviewed by at least one person who did not write it.
* **Kill signal:** the spec turns out to be under-specified for someone writing it fresh. That is
  the single most useful thing that can happen before the format hardens — it is why this gate
  exists before any outreach.
* **Why Python:** it is what mathematicians, physicists and scientists actually script in. A format
  that only one Android app reads has no network effect.

### G3 — Interop with the tools researchers already use

* **Status:** `agf → SymPy` and `from_sympy → agf` round-trips work and are tested
  (`python/tests/test_sympy.py`), including solving `2x + 3 = 11` through the bridge. OpenMath
  and MathML are not done, and the corpus is 30 expressions rather than ~100 real ones.
* **Pass:** `agf ↔ OpenMath/MathML` round-trips on a corpus of ~100 real expressions (from
  textbooks, papers, problem sets), with a published failure list.
* **Kill signal:** the typed-port model turns out to be inexpressible in OpenMath without loss.
  Then the pitch is narrower: AGF is for *authoring*, not interchange.

### G4 — External documents

* **Pass:** at least 3 AGF files committed by people who are not the author, in repos, issues or
  datasets.
* **Kill signal:** everyone prefers LaTeX/the screenshot. Then the differentiator is not the
  format but the interaction, and the roadmap should pivot to the teaching workflow.

### G5 — Credibility

* **Pass:** a citable paper (technical report on arXiv is sufficient) describing the typed
  hypergraph, the port-admissibility model and the rewriting soundness test; and an AGF corpus
  released with a DOI (Zenodo or equivalent).
* **Why papers, not logos:** NASA and working mathematicians do not adopt tools because a startup
  says so. They adopt what their colleagues cite, what runs on their data, and what does not lock
  their work in. A citable paper plus a neutral licence is how a one-person project becomes
  citable itself.

### G6 — Neutral home

* **Pass:** the specification lives somewhere with more than one maintainer and an explicit
  versioning policy (a W3C Community Group is the realistic venue for a data format).
* **Kill signal:** no second maintainer materialises from G2–G4. Then keep AGF as a documented
  product format and stop calling it a standard — there is no shame in that, and it is more
  honest.

---

## Now (2.1, this PR)

* Correctness of the expression kernel — done, see `CHANGELOG.md`.
* Stability bounds, CI, release runbook — done.
* Format, schema and licence published — done (`docs/SPEC.md`, `docs/aura-graph.schema.json`).

### G7 — Ink is measured, not asserted

* **Status:** a reproducible harness exists (`tools/InkBenchmark.kt`), a seeded baseline is
  published (`docs/benchmarks/`), and the audit found three symbols the classifier can never
  emit. Top-1 on the synthetic template corpus is 38.7% — a proxy, not a recognition rate.
* **Pass:** ≥ 80% top-1 on **real captured strokes** (3–5 writers, ~50 samples/glyph) and the
  reachability defects closed. No accuracy claim in the listing until this gate passes.
* **Kill signal:** real-handwriting accuracy stays below ~60% after collecting data. Then ink is
  a convenience, not a feature: demote it to "pen input for simple operators" and stop
  advertising recognition.

## Next (2.2 — pick one, not three)

**G2 landed in this repository** (`python/`, independent of the Kotlin kernel, passing C1–C5).
The remaining G2 work is unglamorous and decisive: publish it, get one outside reviewer, and
grow the corpus from 30 synthetic cases to ~100 real expressions.

Alternatives, if tester feedback says otherwise:

* **Symbolic coverage** — factoring, expansion, systems of equations. This is what makes the
  "solve" panel trustworthy to a mathematician, and it is a large, well-understood body of work.
  Only worth it if G1 passes and testers ask for it.
* **Structured input** (typing/nesting as a first-class flow) — cheaper than ink, and ink is
  currently the weakest link in the pipeline for anything past simple glyphs.

## Later

* Desktop / web client sharing the kernel (Kotlin Multiplatform) — only after G2 proves anyone
  else wants the documents.
* Ink: real HMER accuracy, evaluated on a public benchmark. Today's classifier is geometric and
  honest, but it will not satisfy a researcher.
* Collaboration and provenance views (derivation history as a document citizens). This is where
  AGF beats LaTeX for teaching; it is not a launch feature.

## Explicit non-goals

* A general computer algebra system. Bridge to SymPy instead of reimplementing it.
* Cloud accounts, sync or telemetry before G2. Every one of them contradicts the current
  Data-safety answers and adds a failure surface for no learning.
* Chasing NASA directly. Institutions adopt what their staff already use and cite; the path runs
  through G2–G5, not through an email.
