# AGENTS.md — handoff for the next coding agent

_Written 2026-10-25 by an Arena agent at the owner's request, so a fresh agent session can
pick this project up without replaying four merged PRs worth of context. If anything here
disagrees with the code on `main`, trust the code and update this file in the same PR._

## Who owns this, and how they work

- **Owner/author:** steven owens — GitHub `artistso`, contact `adventuresindrawing@gmail.com`.
  That name/email are the author on the paper, `docs/CITATION.cff`, the SPEC, and the
  `aura-agf` package metadata. steven built the original app in Google AI Studio; the repo's
  early history reflects that.
- **The ambition** is that Aura Graph Format (AGF) could one day be a standard format for
  mathematics (the "mathematicians / NASA" north star). **Honesty rule steven has accepted
  repeatedly: never claim it already is one.** Every public artifact states its limits
  (SPEC §9.1, paper §9, ROADMAP kill conditions, `docs/benchmarks/methodology.md`). Do not
  soften those to make a pitch stronger.
- **Working style:** steven endorses plans briefly ("let's do your recommendations") and
  merges with "Please merge the pull request." Don't merge anything without that ask.
  Give recommendations **with gates/stopping points**, not open-ended roadmaps. The chosen
  strategy is **format-first credibility** (licence + spec → second implementation →
  measured benchmark → citable paper), not app feature work — don't drift back to features
  unless steven redirects.

## What this repo is

Aura is a connection-first mathematical canvas: mathematical objects are typed vertices
with named ports on an Android (Jetpack Compose) canvas; connections are port bindings.
It ships a pure-Kotlin kernel (parser, types, evaluator, symbolic engine, ink recognizer),
defines **Aura Graph Format (AGF 0.1, draft)** as the document format (`docs/SPEC.md` +
`docs/aura-graph.schema.json`), and has an independent second implementation in Python
(`python/`, distribution `aura-agf`, import package `auragf`, console script `agf`).
Android app is in **Google Play closed testing** — anything app-side must stay shippable.

**Read first, in order:** `README.md`, `CHANGELOG.md`, `docs/ROADMAP.md` (gates G0–G7 +
kill conditions), `docs/SPEC.md` §9, `docs/publishing-pypi.md`,
`docs/paper/aura-agf.md` §9 (threats to validity), `RELEASE.md`.

## Verified state as of 2026-10-25 (`main` = `1da3ba5`)

- **Merged PRs:** #1 v2.1 kernel correctness + spec/licence + Python AGF + ink benchmark +
  paper (`bfec0df`); #2 steven owens authorship + PyPI Trusted Publishing workflow
  (`972448e`); #3 author email → Gmail (`382eba2`); #4 Android version bump to 3.0
  (`1da3ba5`). No open PRs, no releases, no tags at handoff time.
- **Separate version lines — do not confuse them** (this exact confusion caused a fix PR):

  | Line | Value | Where |
  |---|---|---|
  | Android app `versionCode` / `versionName` | **3 / "3.0"** | `app/build.gradle.kts` |
  | `aura-agf` Python distribution | **0.1.0** | `python/pyproject.toml` **and** `auragf.__version__` |
  | AGF format | **0.1** | `docs/SPEC.md`, `AGF_VERSION` |
  | JSON export `"version"` / Room schema | **2 / 1** | `SidebarAndDialogs.kt` / `AuraDatabase.kt` — data versions, don't bump with the app |

- **Baselines (reproduce with the commands below):** Kotlin kernel specs **60/60** (338
  library symbols); Python **55 tests** pass; conformance **203/203**
  (C1 30 · C2 8+5+30 · C3 30 · C4 30 · C5 10 · C6 30 · P7 30 — without `jsonschema`
  installed, C6 skips and it's 173/0/1 skipped); seeded ink baseline top-1 **38.7%**,
  top-3 **53.3%**, static coverage 23/26 (`div`, `n`, `z` unreachable — a *real* finding;
  do **not** tune the recognizer against the template corpus to raise the score).
- **CI** (`.github/workflows/android.yml`, name `CI`) is green with 3 jobs: JVM-only kernel
  specs, debug APK assemble, Python conformance. `.github/workflows/publish-pypi.yml` is
  active but **has never run** (see next section).

## Immediate next step: finish the PyPI publish (mid-flight)

Everything code-side is done. **steven has registered on pypi.org and test.pypi.org
(under the Gmail above) and created the pending trusted publishers on both** (confirmed
by him 2026-10-25; not externally verifiable — `aura-agf` is still 404 on PyPI, which is
correct until first publish). The exact values in use:

| Field | PyPI | TestPyPI |
|---|---|---|
| Project name | `aura-agf` | `aura-agf` |
| Owner (GitHub user, **not** email/PyPI name) | `artistso` | `artistso` |
| Repository | `aura-cal` | `aura-cal` |
| Workflow name (filename, not title) | `publish-pypi.yml` | `publish-pypi.yml` |
| Environment name | `pypi` | `testpypi` |

**What remains, in order** (full detail: `docs/publishing-pypi.md`):

1. **TestPyPI dry run — NOT yet run.** steven must click it: GitHub → Actions →
   *Publish to PyPI* → Run workflow → branch `main`, target `testpypi`.
   ⚠️ **You almost certainly cannot dispatch it yourself:** the Arena GitHub integration
   gets `403 Resource not accessible by integration` on `gh workflow run` and on creating
   environments (verified twice). **That 403 is a token-permission limit, not something
   steven did with merges — don't burn time re-diagnosing it.** You *can* read run status
   and, on failure, the annotations API (below). After steven clicks, watch and verify:
   `gh run list --workflow publish-pypi.yml`, then a clean-venv install from TestPyPI.
2. **Real publish:** steven creates a GitHub Release tagged exactly **`aura-agf-v0.1.0`**.
   The workflow refuses to build if tag ≠ `pyproject.toml` ≠ `__version__`. PyPI version
   numbers are **burned forever** once uploaded — never reuse one; if 0.1.0 goes wrong,
   the fix is 0.1.1, not a re-upload. (A 0.1.0 on TestPyPI does *not* burn it on PyPI.)
3. **Post-publish housekeeping:** flip the root README install snippet back to
   `pip install aura-agf` first, mark ROADMAP G2 "published" (gate still needs an
   *outside reviewer* to close — publish ≠ reviewed ≠ adoption), and consider the Zenodo/
   DOI archiving in `docs/paper/how-to-submit.md`.

**Never** ask steven for a PyPI/GitHub password or token, in chat or anywhere. Trusted
Publishing (OIDC) exists precisely so none is needed.

## Also outstanding (after PyPI, in steven's priority order)

- **Paper:** `docs/paper/aura-agf.md` is submission-ready with steven's name on it; not
  submitted. Blocked on: release tag, Zenodo DOI (then add to `docs/CITATION.cff`
  `identifiers:` + paper), arXiv endorsement for a first-time `cs.*` submitter
  (`docs/paper/how-to-submit.md` has the pandoc command + metadata table).
- **ROADMAP gates:** G2 ≈80–85% (publish + one outside reviewer missing); G3 partial
  (SymPy bridge done both ways; OpenMath/MathML interop not started; corpus is 30 cases,
  target ~100 real expressions — grow `CASES` in `tools/GenerateConformance.kt`); G1
  retention needs real testers, not code; G7 ink threshold (≥80% top-1) not met and needs
  real stroke data, not template tuning.
- **Play:** app is 3.0 in code; before any upload confirm Play Console's highest
  `versionCode` on the closed track is < the one being uploaded (AI Studio builds may
  have taken 3 already → bump to 4). `applicationId com.aistudio.auramath.hypxqv` and the
  AI Studio signing configs in `app/build.gradle.kts` must not change. See `RELEASE.md`.

## How to build / test (sandbox has no Android SDK)

Toolchain is **ephemeral** — rebuild it each session (JDK/kotlinc/pylibs locations from the
previous session; adapt if yours differ). Project pins: Kotlin `2.2.10`, AGP `9.1.1`,
Gradle `9.3.1`, `compileSdk 36`, `minSdk 24`, `targetSdk 36`, Java 11 source/target.

```bash
pip3 install --target /tmp/pylibs build twine pytest jsonschema sympy pyyaml
export PYTHONPATH=/tmp/pylibs:/home/user/aura-cal/python/src
python3 -m pytest -q python/tests                       # 55 tests
python3 -m auragf.cli conformance docs/conformance      # 203/203 (needs jsonschema for C6)
cd python && python3 -m build --outdir /tmp/dist && python3 -m twine check /tmp/dist/*

# Kotlin kernel specs — pure JVM, no Android SDK (this is what CI's kernel job does):
kotlinc app/src/main/java/com/example/aura/kernel/*.kt tools/RunKernelSpecs.kt \
  -include-runtime -d build/kernel-specs.jar
java -cp build/kernel-specs.jar kernel.specs.KernelSpecs   # 60/60

# Regenerate conformance corpus (byte-identical thanks to canonical ids n0/e0 — keep it so):
kotlinc app/src/main/java/com/example/aura/kernel/*.kt tools/GenerateConformance.kt \
  -include-runtime -d /tmp/gen.jar && java -cp /tmp/gen.jar conformance.GenerateConformanceKt
```

You **cannot** build the APK/AAB locally (no SDK; `gradle-wrapper.jar` isn't even
committed). CI's *Assemble debug APK* job is the only automated proof the Compose UI
compiles — `kotlinc` alone can't typecheck it, so never claim UI code "builds" from a
sandbox check. CI generates a throwaway `debug.keystore` (the build file points at
`${rootDir}/debug.keystore`, `.gitignore` re-includes the name, but the file was never
committed — that silently failed *every* CI run for months until PR #1 fixed it).

## Gotchas that already cost time (don't rediscover)

- **`gh run view --log-failed` is unreachable from the sandbox** (EOF). Use
  `gh api repos/artistso/aura-cal/check-runs/<id>/annotations --jq '.[] | "\(.annotation_level): \(.message)"'`.
  Failed Gradle builds self-annotate via `.github/scripts/annotate_failure.py`.
- **Kotlin block comments nest** — a `/*.kt` glob inside KDoc is a syntax error
  ("Unclosed comment"). Write `[asterisk]`. No `\(` escape either; use `${`.
- Backticks inside a double-quoted `git commit -m "…"` get command-substituted. Use
  `git commit -F -` with a heredoc.
- Parallel edits to the **same file** race (last writer wins). Edit one file at a time.
- Python deps are never system-wide here: `--target /tmp/pylibs` + `PYTHONPATH`.
- `agf` was **taken on PyPI** by an unrelated package that installs a top-level `agf/`
  module — hence distribution `aura-agf`, import `auragf`, CLI still `agf`. The `agf`
  *document field* and format name are unrelated and untouched.
- The ink benchmark loader parses `InkRecognizer.kt` source for `SPECS`: pass the literal
  file path, not a glob, or you get `tokens=0` and a meaningless run.
- A relation isn't a number: `agf eval` on a relation root reports `true`/`false`,
  not `undefined`.

## Your first 30 minutes (do this before changing anything)

1. `git log --oneline origin/main -8`, `gh pr list --state all --limit 6`,
   `gh run list --limit 5`, `gh run list --workflow publish-pypi.yml`,
   `git ls-remote --tags origin`, `curl -s -o /dev/null -w "%{http_code}" https://pypi.org/pypi/aura-agf/json`
   — establish whether the TestPyPI run / PyPI publish happened while you were away.
2. Re-run the three baselines above (pytest, conformance, kernel specs) and compare to
   55 / 203 / 60. If they differ, that's your first finding — report it, don't bury it.
3. Re-read `docs/ROADMAP.md` gates and report status to steven in those terms.
4. **Ask steven what he'd like next** rather than publishing anything on your own
   initiative. Publishing (PyPI, Play, arXiv) is deliberately one steven-click at a time.

_— previous agent. Good luck; keep the limitation sections honest and the version lines straight._
