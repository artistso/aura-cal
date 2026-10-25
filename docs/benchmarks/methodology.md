# Ink benchmark — methodology and limitations

A benchmark is only worth publishing if its limits are stated as loudly as its score. Read this
before quoting any number from [`ink-baseline.md`](ink-baseline.md).

## What is being measured

Tier-1 recognition in `InkRecognizer` is a **rule-based geometric classifier**, not a learned
model. It extracts features from each stroke — angle, straightness, closure, bounding box,
direction changes, presence of a radical check or a caret peak — and returns a ranked list of
candidate symbols with confidences. It can therefore only ever emit symbols that appear in one of
its rules.

`tools/InkBenchmark.kt` measures how often that classifier returns the intended symbol as its top
candidate, on a fixed, seeded corpus.

## How the corpus is produced

* **Templates.** 26 glyphs, each defined as one to three polylines in a 0–100 box, drawn by the
  author of the classifier. These are the shapes the classifier is *supposed* to handle.
* **Perturbation.** Every template is sampled four times per level with the following:
  * `clean` — no rotation, ±0.6 units of jitter;
  * `tilted` — ±1.5 units of jitter;
  * `rotated` — 12° rotation, ±1.5 units jitter;
  * `resized` — all coordinates scaled to 62%, ±1.2 units jitter;
  * `sloppy` — 118% scale, −8° rotation, ±3.2 units jitter, coarser sampling.
* **Seed.** `20261025`; the generator is a `kotlin.random.Random`, so runs are reproducible.

That is 26 glyphs × 5 levels × 4 draws = **520 trials**.

## Why the headline number must not be quoted as an accuracy rate

The templates were written by the same person who wrote the classifier's rules. Two consequences:

1. **Optimistic.** Where a template happens to match a rule's thresholds, the score is inflated.
2. **Pessimistic.** Where a template is a poor drawing of the glyph (the author's `∫` is too
   straight; the author's `y` is a plain V), the classifier is blamed for a bad sample. `int`
   scoring 0% top-1 is partly this: a straight, tall stroke is correctly read as `1` by a rule
   that fires before the tall-curve rule.

So 38.7% top-1 is **not** a claim about handwriting in the real world. It is a *baseline on
templates*, useful because it is fixed, seeded and reproducible: any change to the classifier can
be measured against it, and any regression is visible in a diff.

It also correctly shows that the classifier's *published description* ("26+ mathematical symbols")
overstates what it can do. That is a product-communication fix as much as an engineering one.

## Coverage audit (the robust finding)

Per-glyph scores depend on the templates. **Reachability does not.** A symbol can only be returned
if some rule in `InkRecognizer` names it, which is a property of the source, not of the data:

```bash
# symbols the classifier declares
sed -n '/private val SPECS/,/^    )/p' \
  app/src/main/java/com/example/aura/kernel/InkRecognizer.kt \
  | grep -o '^        "[^"]*"' | tr -d ' "' | sort -u | wc -l        # 26

# symbols any rule can emit
grep -o 'predict("[^"]*"' app/src/main/java/com/example/aura/kernel/InkRecognizer.kt \
  | sed 's/predict("//; s/"//' | sort -u | wc -l                    # 23
```

**`div` (÷), `n` and `z` are declared but unreachable.** No input, however well drawn, can produce
them through the ink path. This is a defect in the classifier's vocabulary, independent of the
benchmark, and it is the reason this harness was worth building.

## What we deliberately did not do

**We did not tune the classifier against this corpus.** Lowering a threshold until `3` matches the
author's `3` template would raise the headline number while teaching the classifier nothing about
human strokes, and the improvement would not generalise. Tuning requires real captured data —
see below.

## Replacing the baseline with real handwriting

The harness accepts an external corpus, so this benchmark can be run on real strokes without
touching the templates:

```bash
java -cp ink.jar benchmark.InkBenchmarkKt --external=strokes.tsv
```

Format — one sample per line, `label<TAB>stroke|stroke`, each stroke `x;y` points separated by `;`:

```text
# label    strokes (x,y;x,y;… | next stroke)
x         22,24;31,33;44,47;57,60|71,20;60,34;48,49;37,63;26,78
2         15,30;20,22;30,18;44,20;56,30;50,42;36,54;24,68;20,78;26,84;45,86;70,86
```

A capture screen that dumps strokes in this format is the obvious next step: it turns this
document into a real evaluation instead of a proxy. **Until that data exists, no recognition
accuracy claim should be made in the app's Play listing, README or press materials.**

## Reproducing

```bash
kotlinc app/src/main/java/com/example/aura/kernel/InkRecognizer.kt \
        app/src/main/java/com/example/aura/kernel/Ontology.kt \
        tools/InkBenchmark.kt -include-runtime -d /tmp/ink.jar
java -cp /tmp/ink.jar benchmark.InkBenchmarkKt
```

Both `ink-baseline.md` and `ink-baseline.json` are rewritten by that command; the JSON is the
machine-readable form for CI trend tracking.

## Work items this benchmark justifies

1. Remove or implement the three unreachable symbols (`div`, `n`, `z`) — correctness, not tuning.
2. Capture ~50 real samples per glyph from 3–5 people and re-run with `--external`. That is a
   weekend of work and it converts this baseline into a defensible number.
3. Only then consider a learned model; a template benchmark cannot justify one.
