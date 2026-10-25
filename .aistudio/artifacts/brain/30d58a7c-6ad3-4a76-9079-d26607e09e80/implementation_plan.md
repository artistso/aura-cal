# Aura — Connection-First Mathematical Hypergraph Canvas (Android Edition)

This plan ports the existing **Aura** mathematical workspace into a native Jetpack Compose Android application—preserving 100% of your current visual aesthetic, obsidian-and-amber palette, and typed hypergraph kernel—while supercharging it with stylus pressure-sensitive ink recognition, symbolic algebraic solving, interactive 2D/3D multi-variable plotting with parameter sliders, and a step-by-step formal proof assistant.

### User Review & Critical Decisions

> [!IMPORTANT]
> Please review the confirmed architectural and product decisions below. Click **Proceed** when you are ready to build, or share any adjustments you would like to make to the plan.

- **Confirmed Platform & UI Architecture**: Native Jetpack Compose application faithfully reproducing Aura's obsidian-slate canvas, translucent glass panels, glowing amber/teal/rose node tiles, cluster envelopes, minimap, and responsive tablet/phone drawers without changing the existing graphics or layout language.
- **Confirmed Persistence Strategy**: On-device Room SQLite database storing documents, typed hypergraph vertices, port-bound hyperedges, discovery logs, and full before/after transaction snapshots for exact multi-step undo/redo.
- **Confirmed Handwriting & Structural Recovery**: Pure on-device recognition pipeline combining an upgraded geometric/feature-vector multi-stroke classifier with native Android stylus (`MotionEvent` pressure/tilt) capture and deterministic Tier-2 LaTeX-to-hypergraph structural recovery.
- **Confirmed Engine Enhancements**:
  1. **Upgraded Tier-1 Ink & S Pen Support**: Pressure-modulated stroke rendering, palm rejection, and an expanded on-device mathematical symbol classifier (30+ core handwritten glyphs + multi-stroke operators, Greek symbols, radicals, and integrals).
  2. **Symbolic Algebra & Step-by-Step Solver**: Rule-based hypergraph rewriting engine supporting algebraic simplification (identity elimination, like-term collection, power/log laws, trig identities) and step-by-step variable isolation with inspectable derivation steps.
  3. **Multi-Variable 2D/3D Plotting & Parameter Sliders**: Interactive 2D function/relation plotting and 3D isometric surface/contour visualization ($z = f(x, y)$), paired with live parameter sliders for scrubbing free variables in real time.
  4. **Expanded Proof Assistant & Formal Verification**: Equational rewrite proof generator alongside the 12-round deterministic numeric verifier, producing step-by-step symbolic justifications for verified identities and expanding the executable kernel specification suite beyond the original 36 specs.

---

### 1. Overview & Core Concept

- **What It Does**: Aura treats mathematics as a spatial universe of connectable objects rather than static text strings. Every number, variable, operator, relation, function, integral, and set is a **typed vertex** ($V$) on an infinite pan-and-zoom canvas, and every connection is a directed **port binding** ($E$) governed by a formal type lattice ($\tau$). Dragging vertices reveals live **homeomorphic ghost paths** with mathematical previews; running **Auto-Connect** discovers and verifies relationships across the entire canvas; and dedicated panes provide type inspection, symbolic derivation, interactive 2D/3D plotting, and executable kernel verification.
- **Target Audience / Persona**: Mathematicians, physicists, engineers, educators, and students using Android tablets (with S Pen / USI styluses) or phones who want a tactile, error-preventing mathematical scratchpad and symbolic laboratory.
- **Key Value**: Combines the freedom of handwritten ink and direct spatial manipulation with the rigor of a typed compiler, symbolic algebra engine, and automated theorem/identity verifier—all operating offline at 60–120 fps on Android.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Infinite Hypergraph Manipulation & Ghost Binding**: Users pan/zoom across the dot-grid canvas (seeded with all 5 original Aura workspaces: *Energy–mass equivalence*, *Pythagorean identity*, *Integral & type sandbox*, *Evaluator & plot*, and *Discovery playground*). Dragging any vertex or subtree snaps to alignment guides and computes local, type-admissible **Ghost Paths** with curved dashed bezier arcs and floating LaTeX preview chips. Tapping a ghost path or chip commits the binding and reflows the expression tree.
  2. **Stylus Ink & Structural LaTeX Recovery**: Users draw directly on the canvas using the Pen tool or open the *Write it, don't type it* dialog. Strokes respond to stylus pressure and tilt, classify into ranked typed vertex predictions with confidence bars, or escalate to Tier-2 structural LaTeX recovery to decompose complex nested expressions into connected hypergraph vertices in one reversible transaction.
  3. **Symbolic Simplification & Step-by-Step Solving**: Selecting a cluster opens the floating selection bar or Inspector where users can **Fold Constants**, **Simplify Symbolically**, or **Solve for Variable**. A new *Derivation Trace* view shows each rewrite rule applied (e.g., *Distributive Law*, *Pythagorean Identity*, *Subtract $b$ from both sides*) and lets users commit any intermediate or final step to the canvas.
  4. **2D/3D Interactive Plotting with Parameter Sliders**: Opening the **Plot** tab detects single-variable functions ($y = f(x)$) for 2D traces with crosshair scrubbing, two-variable expressions ($z = f(x, y)$) for interactive 3D rotatable surface meshes and contour maps, and extra free variables (e.g., $\omega, \varphi, k$) to automatically generate live parameter sliders.
  5. **Auto-Connect Discovery & Formal Proof Assistant**: Adjusting the **Connection Scale** slider (`literal` $\to$ `cautious` $\to$ `balanced` $\to$ `wild`) and running **Auto-Connect** probes candidate bindings across clusters. Each proposal displays its verdict (`holds true`, `refuted` with a concrete counterexample, or `unverifiable`), plus a **Proof / Justification** drawer showing the formal algebraic rewrite chain when an identity holds symbolically.

- **Visual Identity & Theme (Preserving Exact Aura Graphics)**:
  - *Aesthetic Direction*: **Obsidian Precision Laboratory**—preserving the exact dark slate atmosphere, warm amber highlights, and luminous teal verification accents from your prototype.
  - *Color Palette & Mood*:
    - `--bg`: `#060910` (Deep Obsidian Canvas) & `#070B12` (Stage Background with dual radial amber/teal glow and 32px/128px dot grid)
    - `--panel`: `rgba(14, 20, 30, 0.94)` with `1px solid rgba(148, 163, 184, 0.16)` hairline borders and subtle elevation shadows
    - `--text`: `#E8EEF8` (Primary Off-White), `--dim`: `#94A3B8` (Slate Secondary), `--muted`: `#64748B` (Eyebrow & Metadata)
    - `--amber`: `#F0B45A` / `#E2A144` (Primary CTA gradient, selected vertex glow, open required ports, active tabs)
    - `--teal`: `#5EEAD4` (Coherent status pills, verified `holds true` badges, relation nodes, occupied port indicators)
    - `--rose`: `#FB7185` (Type conflict diagnostics, `refuted` counterexample badges, destructive actions)
    - `--violet`: `#A78BFA` (Operators & symbolic rewrite badges), `--pink`: `#F472B6` (Functions), `--blue`: `#60A5FA` (Sets)
  - *Typography & Hierarchy*:
    - **Display / Mathematical Serif**: Local bundled serif font (`Playfair Display` / `EB Garamond` via `font-util`, matching `"Iowan Old Style", "Palatino Linotype", serif`) for headers, canvas titles, and mathematical symbols.
    - **Technical Monospace**: Local bundled `JetBrains Mono` / `Roboto Mono` for vertex/edge telemetry (`22 V · 15 E · 7 clusters`), port labels, confidence scores, and counterexamples.
    - **UI Sans**: Clean geometric sans-serif (`Inter` / `Plus Jakarta Sans`) for controls, inspector diagnostics, and rationale copy, with strict `0.18em` uppercase eyebrow labels.
  - *Component Styling & Layout*:
    - Adaptive workspace layout: persistent top telemetry header (`56.dp`), collapsible/docked Left Sidebar (`Canvases` + `338-Symbol Browser`), central interactive `CanvasStage` with floating HUDs (top-left Graph Summary, top-right Tool Palette, bottom-left Minimap, bottom-right Zoom Pill, contextual Selection Bar, bottom Ghost Candidate Carousel), and collapsible/docked Right Drawer with 5 tabs (`inspect`, `solve`, `plot`, `connect`, `verify`).

- **Interactive Feedback & Motion**:
  - Animated marching-ants dashed SVG/Canvas bezier paths for Ghost Candidates (`900ms` linear phase loop).
  - Variable scope halos that illuminate across all clusters sharing the same symbol token on hover or selection.
  - Snapping alignment guides (horizontal and vertical teal laser hairlines) when dragging vertices near existing row/column centers.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native Custom Mathematical Typesetting & Node Renderer in Jetpack Compose**
  - *Chosen Approach*: Build a dedicated native `MathText` and `CanvasStage` renderer in Jetpack Compose that lays out LaTeX fractions, superscripts/subscripts, radicals, big operators ($\int, \sum, \prod, \lim$), matrices, accents, and Greek/operator glyphs directly on native Compose `Canvas` and custom layouts, paired with full LaTeX/MathML/JSON export.
  - *Why*: Eliminates WebView overhead inside individual draggable vertex tiles, guarantees 60–120 fps panning/zooming with viewport culling, and preserves the exact look of Aura's 52px rounded gradient node tiles and port anchors.
  - *Alternatives Considered*: Embedding hundreds of miniature WebViews for KaTeX tiles (rejected due to severe Android memory/scroll jank during multi-node drag operations).

- **Decision 2: Hybrid Symbolic + Numeric Engine Architecture**
  - *Chosen Approach*: Keep the existing port-walking numeric evaluator (`evaluate.ts`) and deterministic 12-sample discovery verifier (`discover.ts`) intact, and layer a pure functional **Symbolic Rewrite & Proof Engine** on top of the same `AuraGraph` data structure.
  - *Why*: Preserves 100% backward compatibility with all 36 existing kernel specs while enabling exact algebraic simplification (e.g., $x + 0 \to x$, $\sin^2\theta + \cos^2\theta \to 1$, $\frac{a^2 - b^2}{a - b} \to a + b$), step-by-step equation solving, and formal proof traces for discovered identities.
  - *Alternatives Considered*: External CAS network APIs (rejected because offline, deterministic, zero-latency execution is a core pillar of Aura).

- **Decision 3: On-Device Room Persistence with Snapshot Transactions**
  - *Chosen Approach*: Replicate the PostgreSQL Drizzle schema (`documents`, `nodes`, `edges`, `transactions`, `discoveries`) in an Android Room SQLite database with JSON-serialized `AuraGraph` before/after snapshots inside `@Transaction` DAO methods.
  - *Why*: Guarantees atomic, lossless multi-step Undo/Redo across app restarts and instant switching between the 5 seeded canvases and user-created workspaces.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                          AURA WORKSPACE (Jetpack Compose)                         │
│  ┌─────────────────────────────────────────────────────────────────────────────┐  │
│  │ Top Telemetry Bar: Canvas Title │ Coherent/Ill-Typed Pill │ Undo/Redo/Tools │  │
│  └─────────────────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────┐ ┌───────────────────────────────────┐ ┌──────────────────┐  │
│  │   Left Sidebar   │ │           CanvasStage             │ │   Right Drawer   │  │
│  │  (Docked/Modal)  │ │  • Dot-Grid & Radial Glow Shader  │ │  (5 Mode Tabs)   │  │
│  │                  │ │  • Cluster Envelopes & Labels     │ │                  │  │
│  │ • Canvas Switcher│ │  • Bezier Port Edges & Ghost Paths│ │ 1. Inspector     │  │
│  │ • Stats Summary  │ │  • Draggable Vertex Tiles + Halos │ │ 2. Symbolic/Solve│  │
│  │ • 338-Symbol     │ │  • Stylus Pressure Ink Overlay    │ │ 3. 2D/3D Plotter │  │
│  │   Domain Browser │ │  • Minimap, Guides & SelectionBar │ │ 4. Auto-Connect  │  │
│  │                  │ │  • Ghost Candidate Bottom Chips   │ │ 5. Verify Suite  │  │
│  └────────┬─────────┘ └─────────────────┬─────────────────┘ └────────┬─────────┘  │
│           │                             │                            │            │
│           ▼                             ▼                            ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────────┐  │
│  │                     Modal Dialogs & Overlays                                │  │
│  │   • Ink & LaTeX Recovery Dialog   • Full 338-Symbol Library   • Export UI   │  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
└─────────────────────────────────────────┼─────────────────────────────────────────┘
                                          │ StateFlow / Intent Actions
                                          ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                         AURA KERNEL & ENHANCEMENT ENGINES                         │
│  ┌─────────────────────┐  ┌─────────────────────────┐  ┌───────────────────────┐  │
│  │  Ontology & Engine  │  │ Symbolic & Proof Engine │  │ Evaluator & 2D/3D Plot│  │
│  │ • 10-Type Lattice   │  │ • Pattern Rule Rewriter │  │ • Port-Walking Eval   │  │
│  │ • 338 Symbol Specs  │  │ • Like-Term Collector   │  │ • Constant Folding    │  │
│  │ • Type Propagation  │  │ • Trig/Log/Power Laws   │  │ • 2D Adaptive Sampler │  │
│  │ • Ghost Candidates  │  │ • Variable Isolation    │  │ • 3D Surface Grid &   │  │
│  │ • Auto-Layout/Reflow│  │ • Formal Proof Trace    │  │   Parameter Sliders   │  │
│  └──────────┬──────────┘  └────────────┬────────────┘  └───────────┬───────────┘  │
│             │                          │                           │              │
│  ┌──────────┴──────────────────────────┴───────────────────────────┴───────────┐  │
│  │  Discovery Engine (Scale 0..1)  │  Stylus Ink Classifier & LaTeX Parser     │  │
│  │  • 11 Templates & 7 Transforms  │  • Pressure/Tilt Geometric Classifier     │  │
│  │  • 12-Round Sampling + Symbolic │  • Recursive Descent LaTeX → Hypergraph   │  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
└─────────────────────────────────────────┼─────────────────────────────────────────┘
                                          │ Repository Pattern (Coroutines / Flow)
                                          ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                       ROOM SQLITE PERSISTENCE LAYER                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │  documents   │  │    nodes     │  │    edges     │  │ transactions & logs   │  │
│  │ (5 Seeded +  │  │ (Typed V +   │  │ (Directed E  │  │ (Before/After Graph   │  │
│  │  User Docs)  │  │  Collapsed)  │  │  Port Binds) │  │  Snapshots, Undo/Redo)│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └───────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - **`AuraGraph` (`nodes: List<AuraNode>`, `edges: List<AuraEdge>`)**: Immutable in-memory hypergraph model. `AuraNode` holds `id`, `kind` (`literal`, `variable`, `operator`, `relation`, `function`, `container`, `set`, `group`), `type` (`Scalar`, `Variable`, `Vector`, `Matrix`, `Set`, `Expression`, `Relation`, `Function`, `Operator`, `Unknown`), `token`, `label`, `variant`, coordinates `(x, y)`, `collapsed`, and `meta`.
  - **Symbolic & Proof Models**:
    - `RewriteStep(ruleName, description, beforeLatex, afterLatex, afterGraph)` for step-by-step algebraic simplification and equation solving.
    - `ProofTrace(verdict, method: Symbolic | NumericSampling | TypeLevel, steps: List<RewriteStep>, sampleAgreement, counterexample)` attached to every `Proposal` in the Discovery Engine.
  - **Multi-Variable Plot & Parameter State**:
    - `PlotAnalysis` extended to classify clusters into `SingleVar2D(yRootId, xVar)`, `Implicit2D(leftId, rightId, xVar, yVar)`, or `Surface3D(zRootId, xVar, yVar)`, plus `freeParameters: List<String>` mapped to a reactive `parameterValues: Map<String, Float>` state (defaulting to `1.0f`, adjustable via sliders in `[-10, 10]`).

- **Interactive Component & State Mapping**:
  - **`CanvasStage` Gestures & Stylus Handling**:
    - Pointer tool: Dragging background pans; pinch-to-zoom scales between `0.28x` and `2.4x` around the focal point; dragging a vertex or selected cluster moves its entire subtree, snaps to stationary vertex centers within `8dp`, and throttles `findCandidates` to every `45ms`.
    - Marquee tool: Dragging a selection box highlights all enclosed vertices and reveals the floating `SelectionBar` (`collapse`, `fold`, `simplify`, `solve`, `zoom`, `delete`).
    - Draw tool (with S Pen / Stylus support): Reads `MotionEvent.AXIS_PRESSURE` and `AXIS_TILT` to render variable-width amber-cream ink ribbons (`#F6D7A8`), classifies multi-stroke gestures on-device into top-3 typed predictions, and places the chosen vertex at the stroke centroid.
  - **`SymbolicSolvePanel` / Inspector Integration**:
    - Displays applicable algebraic rewrite rules for the selected cluster, a **Simplify Step-by-Step** timeline, and a **Solve for [Variable]** selector when a relation (`=`) with one or more variables is selected. Clicking **Apply Step** commits that transformed hypergraph as a reversible Room transaction.
  - **`PlotPanel` (2D & 3D + Sliders)**:
    - Renders interactive 2D curves with nice-tick gridlines, asymptote clamping, and touch-scrubbing coordinates; switches to an interactive 3D isometric wireframe/shaded surface with azimuth/elevation drag rotation when a 2-variable expression is selected; and provides live sliders for any remaining free variables.
  - **`VerificationPanel` (Extended Executable Specs)**:
    - Runs all original 36 kernel specifications plus new specifications covering symbolic simplification, step-by-step equation solving, 3D surface evaluation, and formal proof verification, reporting pass/fail counts and execution time in milliseconds.
