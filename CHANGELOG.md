# Changelog

All notable changes to the Android client. Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased] — format

### Added

* **`python/` — an independent implementation of AGF 0.1.** Reader/writer (with unknown-member
  preservation), the full vocabulary and port table, type inference, well-formedness rules
  W1–W7 plus W8, the reference evaluator (`undefined` ≠ `NaN`), the LaTeX projection, a SymPy
  bridge (`to_sympy` / `from_sympy`) and a CLI (`agf validate|show|eval|latex|sympy|conformance`).
  55 tests, including 172 conformance checks against the Kotlin corpus.
* **`docs/conformance/`** — 30 generated cases (graph, types, value, LaTeX), 5 rewrite traces for
  the C5 soundness class, and 8 hand-authored malformed documents, one per well-formedness rule.
  Regenerate with `tools/GenerateConformance.kt`.
* `docs/SPEC.md` §9 now reports conformance per class for both implementations, and states the
  caveat plainly: agreement between two implementations by one author catches ambiguity, not
  shared conceptual error.

* **`docs/benchmarks/` — a reproducible ink benchmark.** 26 glyphs, 5 perturbation levels,
  seeded so runs are diffable; `tools/InkBenchmark.kt` writes `ink-baseline.{md,json}` and accepts
  an external `strokes.tsv` so the same harness can score real captured handwriting.
  Baseline: 38.7% top-1 / 53.3% top-3 on synthetic templates — published with the explicit
  warning that it is **not** a recognition rate, plus the methodology and the reasons.
  The coverage audit (source-level, independent of the data) found that `÷`, `n` and `z` are
  declared but **unreachable**: no input can ever produce them.

* **`docs/paper/` — a submission-ready technical report** ("Aura Graph Format: a typed,
  port-addressed document model for mathematics"): model, typing, semantics, a rewriting
  soundness criterion, the conformance evaluation, and a named threats-to-validity section that
  states the single-authorship and no-adoption limits. `how-to-submit.md` covers tagging,
  Zenodo DOI, pandoc conversion and arXiv metadata.

### Fixed — CI could not build the app, and nobody could see why

* **`assembleDebug` failed on every CI run since the repository was imported.** The debug signing
  config points at `${rootDir}/debug.keystore`; `.gitignore` re-includes that name but the file was
  never committed, because AI Studio creates it locally. CI now generates a throwaway debug key.
  `assembleDebug` passing is the first actual compile check of the Compose UI: `kotlinc` cannot
  typecheck it without the Android artifacts, so a broken screen used to be undetectable until
  someone opened Android Studio.
* **The kernel gate no longer needs the Android SDK.** `tools/RunKernelSpecs.kt` runs the same 60
  specs on a plain JVM and exits non-zero on failure. Previously "Set up Android SDK failed" and
  "the kernel is broken" produced identical red checks.
* Failed builds now annotate themselves with the lines that carry the cause
  (`.github/scripts/annotate_failure.py`), because GitHub's log download is not reachable from
  every environment and a red check that says only "failed" is not a diagnosis.

### Changed

* The reference evaluator now returns *undefined* for `±` (`SPEC` §6). It previously returned the
  operand, which silently turned a two-valued result into one.

## [2.1] — 2026-10-25

Correctness and stability release for closed testing. The headline is a parser rewrite: several
common expressions used to decompose into the wrong hypergraph, which made the canvas, the
solver and the plot disagree with the mathematics the user typed.

### Fixed — expression kernel

* **Unbraced exponents swallowed the rest of the expression.** `a^2 + b^2 = c^2` parsed as
  `a^(2 + b^(2 = c^2))`, and `x^2 = 9` parsed as `x^(2 = 9)`. Symptoms: the Pythagorean
  theorem, every polynomial and every quadratic reported type errors and could not be solved.
  An unbraced script now binds exactly one atom; a braced script still accepts a full
  expression.
* **`|x|` was tokenised as three vertices.** Absolute value and norm now parse into a single
  enclosure vertex with a `Body` port, evaluate numerically, and infer a scalar type, so
  `|x - 3| = 4` is well-typed. Unmatched bars raise a clear error.
* **Integral differentials became variables.** `\int_0^1 x^2 \, dx` used to bind
  `x² · d · x` as its integrand and left the body reported as open. The trailing `d⟨var⟩` is
  now stripped, the orphaned `d` vertex is pruned, and the limits are read as `0` and `1`.
* **Summation index relations were diagnosed as ill-typed.** `\sum_{n=1}^{10} n` raised
  "Equality has type Relation, but lower accepts Scalar | Variable". Limit ports now accept
  index relations, and relation-valued bounds are exempt from the nested-relation rule.
* **Absolute value / floor / ceiling symbols from the library never evaluated** because the
  evaluator only handled enclosure *containers*, while the library ships them as operators.
  Both shapes now evaluate.
* **Chained powers dropped tokens.** `x^2^3` lost the trailing exponent; scripts now chain.
* **Trailing input was silently ignored.** `x = 1 )` used to parse as `x = 1`. Leftover tokens
  now raise a `ParseError`, which the insert dialog shows instead of committing wrong math.

### Fixed — solver

* **Quadratic equations produced no steps.** `x^2 = 9` now returns `x = ±3` instead of nothing,
  via an *Even Root Extraction* rule that keeps both real branches. Odd roots
  (`x^3 = 27` → `x = 3`) stay single-valued.
* **Solutions were left unevaluated.** `2x + 3 = 11` used to end at `x = (11 - 3) / 2`; solving
  now appends an *Evaluate Solution* step, so the answer is `x = 4`.
* **`±` results could be folded away.** Constant folding refuses to collapse a sign-ambiguous
  subtree, which previously turned `±3` into `3`.

### Fixed — stability

* **Stack overflow on pathological input.** Deeply nested grouping and very long operator
  chains overflowed the JVM stack. `StackOverflowError` is an `Error`, so the UI's
  `catch (Exception)` did not intercept it and the app died. Input is now bounded
  (2 048 tokens, 96 grouping levels, 192 tree levels) and rejected with an explanatory message.
* **Exponential rendering of nested enclosures.** `toLatex` rendered the operands of enclosure,
  accent and big-operator vertices twice, doubling work per nesting level: 20 nested `|…|`
  took ~1 s and 200 never finished. Each vertex now renders once (200 nested enclosures:
  ~130 ms including JVM start).
* **Kernel failures can no longer take the app down.** The mutation, discovery and LaTeX
  preview paths catch `Throwable` and surface a toast instead.

### Testing

* Kernel specification suite grown from 42 to **60 specs**, executed both in-app (Verify tab)
  and by `./gradlew testDebugUnitTest`. New coverage: parser precedence, braced exponents,
  power chaining, integral limits and differential stripping, absolute value, index bounds,
  trailing-token rejection, generic solve steps, step truth-preservation, and robustness
  against pathological input.
* Added GitHub Actions CI (`.github/workflows/android.yml`): unit tests plus a debug assemble
  on every push and pull request.

### Release engineering

* `versionCode` 1 → **2**, `versionName` 2.0 → **2.1**.
* ProGuard/R8 keep rules written and documented (shrinking still disabled).
* `.gitignore` now excludes `*.jks`, `*.keystore` and `keystore.properties`.
* Added README, CHANGELOG and RELEASE documentation.

## [2.0] — initial Android release

Native Jetpack Compose port of the Aura web prototype: typed hypergraph canvas, ghost-path
binding, 338-symbol library, Tier‑1 ink classification and Tier‑2 LaTeX structural recovery,
symbolic rewriting and step-by-step solving, 2D/3D plotting, discovery with numeric
verification, and on-device persistence with exact undo/redo.
