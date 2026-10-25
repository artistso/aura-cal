# Aura — a connection-first mathematical canvas

Aura treats mathematics as a **space of connectable objects**, not as text. Every number,
variable, operator, relation, function, integral and set is a typed vertex on an infinite
pan-and-zoom canvas, and every connection is a port binding governed by a type lattice.
LaTeX, MathML and JSON are *serialisations* of that graph — never the source of truth.

Android client, Jetpack Compose, fully offline. 338-symbol library, symbolic solving,
2D/3D plotting, stylus ink recognition and an executable kernel specification suite.

**Status:** in closed testing on Google Play (2.1).
[![CI](https://github.com/artistso/aura-cal/actions/workflows/android.yml/badge.svg)](https://github.com/artistso/aura-cal/actions/workflows/android.yml)

Aura also defines a document format — **Aura Graph Format (AGF, draft 0.1)** — so the mathematics
it produces is not trapped in this app:

* [`docs/SPEC.md`](docs/SPEC.md) — the format: data model, types, well-formedness, reference
  evaluation semantics, conformance classes, and an honest section on what would make it a
  standard.
* [`docs/aura-graph.schema.json`](docs/aura-graph.schema.json) — machine-checkable JSON Schema.
* [`docs/ROADMAP.md`](docs/ROADMAP.md) — gates toward adoption, each with a kill condition.
* [`docs/CITATION.cff`](docs/CITATION.cff) — how to cite the software or the format.
* [`docs/benchmarks/`](docs/benchmarks/) — the ink benchmark: methodology, seeded baseline and a
  reachability audit that found three symbols the classifier can never emit.
* [`docs/paper/aura-agf.md`](docs/paper/aura-agf.md) — a technical report on the model, its
  semantics and the conformance evidence, including what the evidence does *not* establish.

AGF is a **draft**, and it now has two implementations that agree: the Kotlin kernel and an
independent Python package ([`python/`](python/)), which passes all six conformance classes plus
the LaTeX projection (203 checks). Treat it as a proposal, not yet a standard.

Nothing below needs an Android SDK. The kernel specs are plain Kotlin; this is how CI runs them.

```bash
# 1. Kernel specifications — 60 specs, a JDK and kotlinc, nothing else
kotlinc app/src/main/java/com/example/aura/kernel/*.kt tools/RunKernelSpecs.kt \
  -include-runtime -d build/kernel-specs.jar
java -cp build/kernel-specs.jar kernel.specs.KernelSpecs

# 2. The independent Python implementation — 55 tests
pip install "aura-agf[test]"            # or: cd python && pip install -e ".[sympy,test]"
python -m pytest python/tests

# 3. Cross-implementation conformance — 203 checks
python -m auragf.cli conformance docs/conformance
```

```python
from auragf import load, validate, evaluate
doc = load("canvas.agf.json")
print([str(d) for d in validate(doc)], evaluate(doc, doc.roots()[0].id))
```

---

## What you can do

| Flow | Where |
|---|---|
| Pan, zoom and drag typed vertices; ghost paths preview legal bindings | canvas |
| Draw with a stylus or type LaTeX and decompose it into a hypergraph | **Write** |
| Inspect ports, types and diagnostics for a cluster | right drawer → `inspect` |
| Fold constants, simplify symbolically, solve for a variable, step by step | right drawer → `solve` |
| Plot `y = f(x)` and rotate `z = f(x, y)` surfaces with parameter sliders | right drawer → `plot` |
| Discover connections across the canvas and verify them numerically | right drawer → `connect` |
| Run the kernel specification suite and read its report | right drawer → `verify` |
| Browse 338 symbols across 16 mathematical domains | **Symbols** |
| Export LaTeX / MathML / JSON | **Export** |

Everything is persisted on device (Room + SQLite). Every mutation stores before/after graph
snapshots inside one transaction, so undo and redo are exact rather than approximate.

---

## Architecture

```
app/src/main/java/com/example/
├── aura/kernel/          pure Kotlin, no Android imports — the whole mathematical core
│   ├── Ontology.kt       type lattice, port declarations, admissibility, symbol geometry
│   ├── SymbolLibrary.kt  338 symbol blueprints across 16 domains
│   ├── Engine.kt         indexing, type inference, diagnostics, LaTeX rendering,
│   │                     expression layout, connection candidates, collapse/expand
│   ├── LatexParser.kt    LaTeX → typed hypergraph (fractions, powers, radicals, limits,
│   │                     big operators, enclosures, matrices, function application)
│   ├── Evaluate.kt       numeric evaluation, constant folding, 2D/3D sampling, scopes
│   ├── SymbolicEngine.kt rewrite rules, step-by-step solver, proof traces
│   ├── Discover.kt       canvas-wide connection discovery and verification
│   ├── InkRecognizer.kt  geometric multi-stroke classifier (Tier‑1 ink)
│   ├── Seeds.kt          the five seeded canvases
│   └── KernelTests.kt    60 executable specifications
├── aura/data/            Room entities, DAO, repository, snapshot transactions
├── aura/ui/              Compose workspace, canvas stage, panels, dialogs
└── ui/theme/             obsidian/amber design language, bundled fonts
```

The kernel is deliberately free of Android dependencies: it compiles and runs on a plain
JVM, which is what makes the specification suite usable as a build gate.

---

## Build and test

Requirements: JDK 21, Android SDK with platform 36.

```bash
# Android Studio: open the project and run the `app` configuration.

# Command line (the repository does not vendor a gradle-wrapper.jar — see note below):
gradle testDebugUnitTest     # runs the 60 kernel specifications
gradle assembleDebug         # builds an installable APK
gradle bundleRelease         # builds the Play Store AAB
```

> **Note on the Gradle wrapper.** `gradle/wrapper/gradle-wrapper.properties` pins Gradle
> 9.3.1, but `gradle/wrapper/gradle-wrapper.jar` is not checked in. Use Android Studio, a
> local Gradle 9.3.1 install, or `gradle wrapper` once to generate the jar. CI uses
> `gradle/actions/setup-gradle` with the same version.

Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` from the
environment (falling back to `my-upload-key.jks` in the repository root). Never commit a
release keystore: `.gitignore` excludes `*.jks` and `*.keystore`.

A **debug** build needs `debug.keystore` in the repository root, because
`app/build.gradle.kts` points the debug signing config at it. AI Studio creates that file for
you; a fresh clone does not have it, and the build fails with
`Keystore file '…/debug.keystore' not found for signing config 'debugConfig'`. Generate one:

```bash
keytool -genkeypair -v -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

CI does exactly this, with a throwaway key. The debug key is not the upload key and never
touches the Play Store.

### The kernel specification suite

`KernelTests.kt` holds 60 executable specifications. They are available in two places:

* **in the app** — open the right drawer → `verify` → *run suite*;
* **in CI and on the command line** — `./gradlew testDebugUnitTest`, via
  `app/src/test/java/com/example/ExampleUnitTest.kt`;
* **without the Android SDK at all** — `tools/RunKernelSpecs.kt`, which is how CI runs the
  gate: `kotlinc app/src/main/java/com/example/aura/kernel/*.kt tools/RunKernelSpecs.kt
  -include-runtime -d build/kernel-specs.jar` then `java -cp build/kernel-specs.jar
  kernel.specs.KernelSpecs`.

The suite changed real behaviour; it is not decorative. It currently pins, among others:

* unbraced exponents bind a single atom (`a^2 + b^2 = c^2` is the Pythagorean theorem, not
  `a^(2 + b^(2 = c^2))`);
* braced exponents still accept full relations (`x^{2n+1}`);
* integrals read their limits and drop the differential (`\int_0^1 x^2 \, dx` has integrand
  `x²`, not `x²·d·x`);
* summation index relations (`\sum_{n=1}^{10} n`) are admissible limits and evaluate to 55;
* `|x - 3|` is an enclosure vertex that evaluates;
* every solve step preserves the truth of the equation on sample values;
* pathological input is rejected with a `ParseError` instead of overflowing the stack.

---

## Design language

Obsidian canvas (`#060910`) with amber, teal, rose and violet accents; Playfair Display for
mathematics, JetBrains Mono for telemetry, Plus Jakarta Sans for UI. Panels are translucent
glass with hairline borders; ghost candidates animate as marching dashed beziers.

---

## Known limitations

* Canvas, ink and rendering are tuned for phones and tablets; very dense canvases (thousands
  of vertices) are not a supported target.
* Expression input is capped at 2 048 tokens and 192 levels of nesting; deeper input is
  rejected with an explanatory error rather than risking the UI thread.
* The solver covers linear isolation, fractions, products, roots/even-root branches and
  constants. General polynomial factoring and systems of equations are not implemented.
* Discovery verification is numeric sampling (12 deterministic rounds), not a theorem prover;
  recognised laws that sampling cannot decide are labelled *unverifiable* instead of guessed.
* No cloud sync, accounts or telemetry. Everything stays on the device.

---

## Licence and provenance

Code is licensed **Apache-2.0** ([`LICENSE`](LICENSE)); the specification under `docs/` is
CC-BY-4.0. Apache-2.0 was chosen over MIT deliberately: it adds an explicit patent grant, which
matters for anything that institutions and companies are expected to adopt.

Built as a native port of the Aura web prototype (see
`public/assets/aistudio/aura-mathematical-graph-editor__2__e12efca7.zip`). The typed
hypergraph model, palette and interaction language are shared between the two.
