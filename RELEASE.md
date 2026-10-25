# Release runbook — Aura (Android)

Practical checklist for shipping a build to **closed testing** and, once the track is clean,
to production. Written for version 3.0 (`versionCode = 3`).

---

## 1. Before you build

| Check | Where | Current value |
|---|---|---|
| `versionCode` increased | `app/build.gradle.kts` | `3` |
| `versionName` updated | `app/build.gradle.kts` | `3.0` |
| Kernel specs green | `gradle testDebugUnitTest` | 60/60 |
| `applicationId` unchanged | `app/build.gradle.kts` | `com.aistudio.auramath.hypxqv` |
| `targetSdk` meets Play policy | `app/build.gradle.kts` | `36` |
| Min SDK | `app/build.gradle.kts` | `24` (Android 7.0) |

**Never change `applicationId`** — Play identifies the listing by it. Changing it creates a new
app and strands your closed testers.

**Never change the signing key.** The upload key lives in `my-upload-key.jks` (or `KEYSTORE_PATH`);
losing it means a key-reset request to Play support.

> If Play Console already reports a `versionCode` of 3 or higher for this app (for example
> because an earlier AI Studio build was uploaded from a different checkout), bump the number
> in `app/build.gradle.kts` before building. Play rejects any upload whose `versionCode` is not
> strictly greater than every build already on the track.

---

## 2. Build the bundle

```bash
export KEYSTORE_PATH=/absolute/path/to/my-upload-key.jks
export STORE_PASSWORD='…'
export KEY_PASSWORD='…'

gradle bundleRelease        # → app/build/outputs/bundle/release/app-release.aab
```

Sanity checks on the artefacts:

```bash
# The bundle must be signed with the upload key and target SDK 36.
bundletool dump manifest --bundle app/build/outputs/bundle/release/app-release.aab \
  | grep -E 'versionCode|versionName|targetSdkVersion'
```

Install the exact binary testers will receive on a physical device:

```bash
bundletool build-apks --bundle app-release.aab --output aura.apks --mode universal
bundletool install-apks --apks aura.apks
```

---

## 3. Upload to closed testing

1. Play Console → **Aura → Testing → Closed testing** → *Create new release*.
2. Upload the `.aab`, keep the Play-generated release notes short and user-facing:

   > Fixed an expression parsing bug that misread powers, absolute values and integrals.
   > Solving `x² = 9` now returns both roots, and answers are evaluated for you.

3. Roll out to the closed track (100% of testers is fine on a closed track).
4. Confirm the tester list still has ≥ 12 testers who have opted in — Google requires a
   minimum number of testers for the production-access application, and the count is measured
   on *opted-in* testers, not invitations.

---

## 4. Data safety and listing answers

These are accurate for the current codebase and worth re-checking any time a dependency is added:

* **Permissions:** the manifest declares **none**. No `INTERNET`, no storage, no location.
* **Data collected:** none. Nothing leaves the device; there is no analytics SDK.
* **Data shared:** none.
* **Data deletion:** uninstalling the app removes the Room database; users can also delete a
  canvas inside the app.
* **Ads / in-app purchases:** none.
* **Content rating:** Everyone (no user-generated content leaves the device, no ads).

If you later add crash reporting, cloud sync or an AI service, these answers must change
*before* the release that introduces it ships.

---

## 5. What testers should try (paste into the closed-testing notes)

1. **Write → LaTeX** and paste each of these, then press *Insert*:
   * `a^2 + b^2 = c^2` — should decompose into one equality with two squared terms, with **no
     red diagnostics**.
   * `\int_0^1 x^2 \, dx` — limits 0 and 1, integrand `x²`, no stray `d` tile.
   * `|x - 3| = 4` — a single absolute-value tile.
   * `\sum_{n=1}^{10} n` — select it and check the inspector; it should evaluate to `55`.
   * `x = 1 )` — should be rejected with an error instead of inserting something wrong.
2. **Solve panel** on `x^2 = 9` → expect `x = ±3`; on `2x + 3 = 11` → expect `x = 4`.
3. **Plot panel** on `y = 2x² - 3` (seeded canvas *Evaluator & plot*).
4. **Verify panel** → *run suite* → expect 60/60.
5. Rotate the device, background and resume — canvases must survive.

Report anything where the rendered mathematics differs from what was typed.

---

## 6. After the release

* Watch **Play Console → Quality → Android vitals** for the first 48 hours (ANR rate, crash
  rate). Anything above ~1% is worth halting for.
* Read the **pre-launch report** screenshots — it exercises your app on real devices and often
  catches layout problems on small screens.
* Keep the `versionCode` monotonic. Every upload — including a fix to a closed track — needs a
  fresh number.

---

## 7. Rollback

If a build misbehaves:

1. Play Console → the affected track → **Halt rollout** (testers keep the previous version).
2. Fix forward with a higher `versionCode`; Google does not allow re-publishing a lower one.
3. If the defect is data-destructive, ship a migration fix before asking testers to reinstall —
   the Room database (`aura_hypergraph.db`) is the only state that matters.

---

## 8. Known limits worth telling testers

* Expressions over 2 048 tokens or 192 levels deep are refused by design (the canvas becomes
  unusable long before that).
* The solver handles linear isolation, fractions, products and roots. It does not factor
  polynomials or solve equation systems.
* Discovery verification is deterministic numeric sampling, not a theorem prover; results are
  labelled *holds*, *refuted* (with a counterexample) or *unverifiable*.
* Everything is local: uninstalling removes all canvases.
