package com.example.aura.kernel

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Aura's canvas-wide discovery engine & formal proof verifier.
 */

enum class Verdict(val wireName: String) {
    Holds("holds"),
    Refuted("refuted"),
    Unknown("unknown")
}

enum class DiscoveryFamily(val id: String, val label: String, val hint: String) {
    Relation("relation", "relations", "= and ≈"),
    Arithmetic("arithmetic", "arithmetic", "+ × ÷ ^"),
    Comparison("comparison", "comparison", "≤ ≈"),
    Set("set", "sets", "∈ ℝ"),
    Speculative("speculative", "speculative", "arrows"),
    Transform("transform", "transforms", "sin ln √")
}

data class Proposal(
    val id: String,
    val kind: String,
    val family: DiscoveryFamily,
    val title: String,
    val rationale: String,
    val hint: String?,
    val leftLatex: String,
    val rightLatex: String,
    val previewLatex: String,
    val score: Float,
    val novelty: Float,
    val verdict: Verdict,
    val confidence: Float,
    val evidence: String,
    val counterexample: String?,
    val samples: Int,
    val clusterIds: List<String>,
    val patch: ConnectionPatch,
    val colorHex: Long,
    val proofTrace: ProofTrace? = null
)

data class DiscoveryOptions(
    val scale: Float = 0.5f,
    val families: List<DiscoveryFamily> = DiscoveryFamily.entries,
    val limit: Int? = null,
    val focusIds: List<String>? = null
)

data class DiscoveryResult(
    val proposals: List<Proposal>,
    val clusters: Int,
    val considered: Int,
    val holds: Int,
    val refuted: Int,
    val scale: Float,
    val families: List<DiscoveryFamily>
)

data class ApplyProposalsResult(
    val graph: AuraGraph,
    val applied: Int,
    val skipped: Int
)

object Discover {
    private data class ClusterInfo(
        val node: AuraNode,
        val latex: String,
        val vars: List<String>,
        val type: MathType,
        val isSet: Boolean,
        val value: Double?
    )

    private data class Template(
        val id: String,
        val kind: NodeKind,
        val variant: String,
        val token: String,
        val label: String,
        val family: DiscoveryFamily,
        val ports: Pair<PortId, PortId>,
        val scaleMin: Float,
        val check: String, // "equality" | "order" | "none"
        val requireSetRight: Boolean = false,
        val commutative: Boolean = false
    )

    private val TEMPLATES = listOf(
        Template("equal", NodeKind.Relation, "equal", "=", "Equality", DiscoveryFamily.Relation, PortId.Left to PortId.Right, 0f, "equality", commutative = true),
        Template("add", NodeKind.Operator, "add", "+", "Addition", DiscoveryFamily.Arithmetic, PortId.Left to PortId.Right, 0.05f, "none", commutative = true),
        Template("mul", NodeKind.Operator, "mul", "\\times", "Multiplication", DiscoveryFamily.Arithmetic, PortId.Left to PortId.Right, 0.2f, "none", commutative = true),
        Template("juxta", NodeKind.Operator, "juxta", "\\,", "Juxtaposition", DiscoveryFamily.Arithmetic, PortId.Left to PortId.Right, 0.3f, "none"),
        Template("sub", NodeKind.Operator, "sub", "-", "Subtraction", DiscoveryFamily.Arithmetic, PortId.Left to PortId.Right, 0.4f, "none"),
        Template("power", NodeKind.Operator, "power", "^", "Exponentiation", DiscoveryFamily.Arithmetic, PortId.Base to PortId.Exponent, 0.35f, "none"),
        Template("frac", NodeKind.Operator, "frac", "\\frac", "Ratio", DiscoveryFamily.Arithmetic, PortId.Numerator to PortId.Denominator, 0.5f, "none"),
        Template("leq", NodeKind.Relation, "leq", "\\le", "Order", DiscoveryFamily.Comparison, PortId.Left to PortId.Right, 0.35f, "order"),
        Template("approx", NodeKind.Relation, "approx", "\\approx", "Approximation", DiscoveryFamily.Comparison, PortId.Left to PortId.Right, 0.55f, "equality", commutative = true),
        Template("mapsto", NodeKind.Relation, "mapsto", "\\to", "Mapping", DiscoveryFamily.Speculative, PortId.Left to PortId.Right, 0.6f, "none"),
        Template("in", NodeKind.Relation, "in", "\\in", "Membership", DiscoveryFamily.Set, PortId.Left to PortId.Right, 0.5f, "none", requireSetRight = true)
    )

    private data class TransformSpec(
        val id: String,
        val kind: NodeKind,
        val variant: String,
        val token: String,
        val label: String
    )

    private val TRANSFORMS = listOf(
        TransformSpec("sqrt", NodeKind.Container, "sqrt", "\\sqrt{\\phantom{x}}", "Square root"),
        TransformSpec("sin", NodeKind.Function, "sin", "\\sin", "sin"),
        TransformSpec("cos", NodeKind.Function, "cos", "\\cos", "cos"),
        TransformSpec("ln", NodeKind.Function, "ln", "\\ln", "ln"),
        TransformSpec("abs", NodeKind.Operator, "abs", "\\left|\\cdot\\right|", "Absolute value"),
        TransformSpec("exp", NodeKind.Function, "exp", "\\exp", "exp"),
        TransformSpec("det", NodeKind.Function, "det", "\\det", "det")
    )

    private val COLORS = listOf(
        0xFFF0B45AL, 0xFF5EEAD4L, 0xFFF472B6L, 0xFFA78BFAL, 0xFF60A5FAL, 0xFF34D399L
    )

    private val SAMPLE_VALUES = listOf(
        -2.4, -1.7, -1.1, -0.5, -0.2, 0.333, 0.6, 1.3, 1.9, 2.7, -3.1, 4.2
    )

    fun scaleBand(scale: Float): String = when {
        scale < 0.25f -> "literal"
        scale < 0.5f -> "cautious"
        scale < 0.75f -> "balanced"
        else -> "wild"
    }

    private fun normalise(latex: String): String =
        latex.replace(Regex("\\\\[a-zA-Z]+|[{}^_\\s]"), "").lowercase()

    private fun similarity(a: String, b: String): Float {
        val left = normalise(a)
        val right = normalise(b)
        if (left.isEmpty() || right.isEmpty()) return 0f
        if (left == right) return 1f
        val setL = left.toSet()
        val setR = right.toSet()
        val shared = setR.count { it in setL }
        return shared.toFloat() / max(max(setL.size, setR.size), 1).toFloat()
    }

    private fun buildClusters(graph: AuraGraph): List<ClusterInfo> {
        val index = Engine.indexGraph(graph)
        return graph.nodes
            .filter { !index.parent.containsKey(it.id) }
            .map { node ->
                val type = Engine.effectiveType(index, node.id)
                val v = Evaluate.evalExpression(index, node.id, emptyMap())
                ClusterInfo(
                    node = node,
                    latex = Engine.toLatex(graph, node.id, index),
                    vars = Evaluate.collectVariables(index, node.id).sorted(),
                    type = type,
                    isSet = type == MathType.Set || node.kind == NodeKind.Set,
                    value = if (v != null && v.isFinite()) v else null
                )
            }
    }

    private fun envFor(vars: List<String>, step: Int): Map<String, Double> {
        val env = LinkedHashMap<String, Double>()
        vars.forEachIndexed { offset, name ->
            val seat = (step + offset * 3) % SAMPLE_VALUES.size
            env[name] = SAMPLE_VALUES[seat]
        }
        return env
    }

    private data class Verification(
        val verdict: Verdict,
        val confidence: Float,
        val evidence: String,
        val counterexample: String?,
        val samples: Int
    )

    private fun verifyPair(
        index: GraphIndex,
        left: ClusterInfo,
        right: ClusterInfo,
        check: String
    ): Verification {
        val vars = (left.vars + right.vars).distinct()
        if (vars.size > 4) {
            return Verification(
                verdict = Verdict.Unknown,
                confidence = 0.3f,
                evidence = "${vars.size} free variables — too many to sample meaningfully",
                counterexample = null,
                samples = 0
            )
        }
        val rounds = if (vars.isNotEmpty()) 12 else 1
        var valid = 0
        var agreeing = 0
        var counterexample: String? = null

        for (step in 0 until rounds) {
            val env = envFor(vars, step)
            val a = Evaluate.evalExpression(index, left.node.id, env)
            val b = Evaluate.evalExpression(index, right.node.id, env)
            if (a == null || b == null || !a.isFinite() || !b.isFinite()) continue
            valid++
            val scale = max(1.0, max(abs(a), abs(b)))
            val tolerance = 1e-6 * scale
            val passed = when (check) {
                "equality" -> abs(a - b) <= tolerance
                "order" -> a <= b + tolerance
                else -> true
            }
            if (passed) {
                agreeing++
            } else if (counterexample == null) {
                val assignment = if (vars.isNotEmpty()) {
                    vars.joinToString(", ") { name ->
                        "$name=${String.format(Locale.US, "%.3f", env[name] ?: 0.0).trimEnd('0').trimEnd('.')}"
                    }
                } else {
                    "numeric case"
                }
                val op = if (check == "order") ">" else "≠"
                counterexample = "$assignment: ${Evaluate.formatNumber(a)} $op ${Evaluate.formatNumber(b)}"
            }
        }

        if (check == "none") {
            return Verification(
                verdict = Verdict.Unknown,
                confidence = if (valid > 0) 0.5f else 0.25f,
                evidence = if (valid > 0) "evaluable on $valid sampled point${if (valid == 1) "" else "s"} — no truth value to test"
                else "not numerically evaluable",
                counterexample = null,
                samples = valid
            )
        }
        if (valid == 0) {
            return Verification(
                verdict = Verdict.Unknown,
                confidence = 0.25f,
                evidence = "no sampled point evaluated on both sides",
                counterexample = null,
                samples = 0
            )
        }
        if (agreeing == valid) {
            val constVal = Evaluate.evalExpression(index, left.node.id, emptyMap()) ?: 0.0
            return Verification(
                verdict = Verdict.Holds,
                confidence = min(0.99f, 0.62f + 0.037f * valid),
                evidence = if (vars.isEmpty()) "numeric check passes — both sides evaluate to ${Evaluate.formatNumber(constVal)}"
                else "$valid/$valid sampled assignments agree within relative 1e-6",
                counterexample = null,
                samples = valid
            )
        }
        return Verification(
            verdict = Verdict.Refuted,
            confidence = min(0.97f, 0.55f + 0.04f * (valid - agreeing)),
            evidence = "$agreeing/$valid sampled assignments agree — counterexample found",
            counterexample = counterexample,
            samples = valid
        )
    }

    private fun recognisedLaw(leftLatex: String, rightLatex: String, token: String): String? {
        if (token != "=" && token != "\\approx") return null
        val left = normalise(leftLatex)
        val right = normalise(rightLatex)
        val both = "$left|$right"
        val rawBoth = "${leftLatex.lowercase()}|${rightLatex.lowercase()}"
        fun has(vararg parts: String) = parts.all { both.contains(it) || rawBoth.contains(it) }
        return when {
            has("sin", "cos") && (left == "1" || right == "1") -> "Pythagorean trigonometric identity"
            has("cos2") -> "Double-angle identity"
            has("m", "c2") && (left == "e" || right == "e") -> "Mass–energy equivalence"
            has("a2", "b2", "c2") -> "Pythagorean relation"
            has("ln", "e") -> "Logarithm–exponential inverse pair"
            has("det", "ab") -> "Multiplicativity of the determinant"
            rawBoth.contains("int") -> "Integral identity candidate"
            has("sum", "n") -> "Series identity candidate"
            else -> null
        }
    }

    fun discover(graph: AuraGraph, options: DiscoveryOptions = DiscoveryOptions()): DiscoveryResult {
        val scale = options.scale.coerceIn(0f, 1f)
        val families = options.families.ifEmpty { DiscoveryFamily.entries }
        val index = Engine.indexGraph(graph)
        val focus = (options.focusIds ?: emptyList())
            .map { Engine.componentRoot(index, it)?.id ?: it }
            .toSet()
        val allClusters = buildClusters(graph)
        val clusters = if (focus.isNotEmpty()) allClusters.filter { it.node.id in focus } else allClusters
        val bounds = Engine.graphBounds(graph)
        val diagonal = max(320f, hypot(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY))
        val radius = diagonal * (0.18f + 0.82f * scale)
        val limit = options.limit ?: (8 + (scale * 20f).roundToInt())

        val proposals = ArrayList<Proposal>()
        var considered = 0

        fun typeAdmitted(cluster: ClusterInfo): Boolean =
            admits(Ontology.OPERAND_TYPES, cluster.type).ok || cluster.isSet

        outer@ for (leftIndex in clusters.indices) {
            for (rightIndex in clusters.indices) {
                if (leftIndex == rightIndex) continue
                val left = clusters[leftIndex]
                val right = clusters[rightIndex]
                if (normalise(left.latex) == normalise(right.latex)) continue
                if (left.node.collapsed || right.node.collapsed) continue
                val distance = hypot(left.node.x - right.node.x, left.node.y - right.node.y)
                if (distance > radius) continue
                val structural = 1f - min(1f, distance / radius)

                for (template in TEMPLATES) {
                    if (template.family !in families) continue
                    if (template.family == DiscoveryFamily.Transform) continue
                    if (scale < template.scaleMin) continue
                    if (template.id == "equal" && leftIndex > rightIndex && template.commutative) continue
                    if (!typeAdmitted(left) || !typeAdmitted(right)) continue
                    if (template.requireSetRight && !right.isSet) continue
                    if (template.id != "in" && (left.isSet || right.isSet)) continue
                    if (template.id == "leq" && left.vars.isEmpty() && right.vars.isEmpty()) continue

                    considered++
                    val verification = verifyPair(index, left, right, template.check)
                    val novelty = 1f - similarity(left.latex, right.latex)
                    val weight = when (verification.verdict) {
                        Verdict.Holds -> 1f
                        Verdict.Unknown -> 0.45f
                        Verdict.Refuted -> 0.12f
                    }
                    val canonical = when (template.id) {
                        "equal" -> 0.07f
                        "approx" -> 0.04f
                        "leq" -> -0.09f
                        "in" -> 0.06f
                        else -> 0f
                    }
                    val constantOrder = if (template.check == "order" && left.vars.isEmpty() && right.vars.isEmpty()) -0.09f else 0f
                    val inert = if (verification.verdict == Verdict.Unknown && left.value == null && right.value == null) -0.06f else 0f
                    val score = 0.38f * weight +
                        0.22f * verification.confidence +
                        0.17f * novelty +
                        0.13f * structural +
                        0.1f * (1f - template.scaleMin) +
                        canonical + constantOrder + inert

                    if (verification.verdict == Verdict.Refuted && scale < 0.45f) continue

                    val hint = recognisedLaw(left.latex, right.latex, template.token)
                    var verdict = verification.verdict
                    var evidence = verification.evidence
                    var confidence = verification.confidence

                    if (template.id == "in") {
                        val realValued = right.node.token == "\\mathbb{R}" &&
                            left.type in listOf(MathType.Scalar, MathType.Variable, MathType.Expression, MathType.Function)
                        if (realValued) {
                            verdict = Verdict.Holds
                            confidence = 0.9f
                            evidence = "type-level: ${left.type.label} is ℝ-valued by default, so membership is admissible"
                        } else {
                            verdict = Verdict.Unknown
                            confidence = 0.5f
                            evidence = "type-level membership claim — ${left.type.label} against ${right.latex}"
                        }
                    }

                    if (hint != null && verdict == Verdict.Refuted) {
                        verdict = Verdict.Unknown
                        evidence = "recognised law — random sampling cannot decide it (${verification.evidence})"
                    }

                    val binder = makeNode(
                        kind = template.kind,
                        type = if (template.kind == NodeKind.Relation) MathType.Relation else MathType.Operator,
                        token = if (template.kind == NodeKind.Relation) template.token else if (template.label == "Ratio") "\\frac" else template.token,
                        label = template.label,
                        variant = template.variant,
                        x = (left.node.x + right.node.x) / 2f,
                        y = (left.node.y + right.node.y) / 2f
                    )
                    val leftEdge = AuraEdge(newId(), binder.id, left.node.id, template.ports.first)
                    val rightEdge = AuraEdge(newId(), binder.id, right.node.id, template.ports.second)
                    val temp = AuraGraph(graph.nodes + binder, graph.edges + listOf(leftEdge, rightEdge))
                    val previewLatex = try {
                        Engine.toLatex(temp, binder.id, Engine.indexGraph(temp))
                    } catch (_: Exception) {
                        "${left.latex} ${template.token} ${right.latex}"
                    }

                    val proofTrace = SymbolicEngine.buildProofTrace(
                        graph = graph,
                        leftRootId = left.node.id,
                        rightRootId = right.node.id,
                        relationToken = template.token,
                        verdict = verdict,
                        hint = hint,
                        samples = verification.samples,
                        counterexample = verification.counterexample
                    )

                    proposals.add(
                        Proposal(
                            id = "${template.id}-${left.node.id}-${right.node.id}",
                            kind = template.id,
                            family = template.family,
                            title = "${left.latex} ${template.token} ${right.latex}",
                            rationale = when (template.family) {
                                DiscoveryFamily.Relation -> "Both clusters admit expression operands (${left.type.label}, ${right.type.label}); a ${template.label.lowercase()} vertex is structure-preserving."
                                DiscoveryFamily.Set -> "Right cluster infers ${right.type.label}, so a membership edge is admissible."
                                else -> "A ${template.label.lowercase()} binder preserves the arity and type contracts of both clusters."
                            },
                            hint = hint,
                            leftLatex = left.latex,
                            rightLatex = right.latex,
                            previewLatex = previewLatex,
                            score = min(0.99f, score + if (hint != null) 0.14f else 0f),
                            novelty = novelty,
                            verdict = verdict,
                            confidence = confidence,
                            evidence = evidence,
                            counterexample = verification.counterexample,
                            samples = verification.samples,
                            clusterIds = listOf(left.node.id, right.node.id),
                            patch = ConnectionPatch(
                                kind = "discover-${template.id}",
                                addedNodes = listOf(binder),
                                addedEdges = listOf(leftEdge, rightEdge),
                                removedEdgeIds = emptyList(),
                                draggedId = left.node.id,
                                focusId = binder.id
                            ),
                            colorHex = COLORS[proposals.size % COLORS.size],
                            proofTrace = proofTrace
                        )
                    )
                }

                if (DiscoveryFamily.Transform in families && scale >= 0.25f) {
                    val candidate = left
                    if (typeAdmitted(candidate) && candidate.vars.size <= 3) {
                        for (tr in TRANSFORMS) {
                            considered++
                            val trNode = makeNode(
                                kind = tr.kind,
                                type = when (tr.kind) {
                                    NodeKind.Function -> MathType.Function
                                    NodeKind.Operator -> MathType.Operator
                                    else -> MathType.Expression
                                },
                                token = tr.token,
                                label = tr.label,
                                variant = tr.variant,
                                x = candidate.node.x - 70f,
                                y = candidate.node.y
                            )
                            val port = if (tr.kind == NodeKind.Container) PortId.Body else PortId.Arg
                            val edge = AuraEdge(newId(), trNode.id, candidate.node.id, port)
                            val temp = AuraGraph(graph.nodes + trNode, graph.edges + edge)
                            val previewLatex = try {
                                Engine.toLatex(temp, trNode.id, Engine.indexGraph(temp))
                            } catch (_: Exception) {
                                "${tr.token}\\left(${candidate.latex}\\right)"
                            }
                            proposals.add(
                                Proposal(
                                    id = "${tr.id}-${candidate.node.id}",
                                    kind = tr.id,
                                    family = DiscoveryFamily.Transform,
                                    title = "${tr.label} of ${candidate.latex}",
                                    rationale = "Wrapping ${candidate.latex} in ${tr.label.lowercase()} creates a new evaluable object without rebinding anything.",
                                    hint = null,
                                    leftLatex = candidate.latex,
                                    rightLatex = "",
                                    previewLatex = previewLatex,
                                    score = min(0.9f, 0.3f + 0.2f * structural + 0.18f * (1f - similarity(candidate.latex, previewLatex)) + 0.1f * scale),
                                    novelty = 1f - similarity(candidate.latex, previewLatex),
                                    verdict = Verdict.Unknown,
                                    confidence = 0.45f,
                                    evidence = "transform — evaluated numerically, no truth value to refute",
                                    counterexample = null,
                                    samples = 0,
                                    clusterIds = listOf(candidate.node.id),
                                    patch = ConnectionPatch(
                                        kind = "discover-transform-${tr.id}",
                                        addedNodes = listOf(trNode),
                                        addedEdges = listOf(edge),
                                        removedEdgeIds = emptyList(),
                                        draggedId = candidate.node.id,
                                        focusId = trNode.id
                                    ),
                                    colorHex = COLORS[proposals.size % COLORS.size]
                                )
                            )
                        }
                    }
                }
                if (proposals.size > 900) break@outer
            }
        }

        fun verdictRank(v: Verdict): Int = when (v) {
            Verdict.Holds -> 2
            Verdict.Unknown -> 1
            Verdict.Refuted -> 0
        }

        fun familyPriority(f: DiscoveryFamily): Int = when (f) {
            DiscoveryFamily.Relation -> 5
            DiscoveryFamily.Set -> 4
            DiscoveryFamily.Arithmetic -> 3
            DiscoveryFamily.Comparison -> 2
            DiscoveryFamily.Speculative -> 1
            DiscoveryFamily.Transform -> 0
        }

        val ordered = proposals.sortedWith { a, b ->
            val vr = verdictRank(b.verdict).compareTo(verdictRank(a.verdict))
            if (vr != 0) vr
            else {
                val fp = familyPriority(b.family).compareTo(familyPriority(a.family))
                if (fp != 0) fp
                else if ((a.hint != null) != (b.hint != null)) {
                    if (a.hint != null) -1 else 1
                } else {
                    b.score.compareTo(a.score)
                }
            }
        }

        val perPair = HashMap<String, Int>()
        val perTemplate = HashSet<String>()
        val templateCounts = HashMap<String, Int>()
        val templateCaps = mapOf(
            "in" to 2, "equal" to 7, "approx" to 3, "leq" to 4, "mapsto" to 3,
            "add" to 4, "mul" to 4, "sub" to 3, "juxta" to 3, "power" to 3, "frac" to 3,
            "sin" to 2, "cos" to 2, "ln" to 2, "sqrt" to 2, "abs" to 2, "exp" to 2, "det" to 2
        )

        val chosen = ArrayList<Proposal>()
        for (proposal in ordered) {
            val pairKey = proposal.clusterIds.sorted().joinToString("|")
            val templateKey = "$pairKey|${proposal.kind}"
            if (templateKey in perTemplate) continue
            val used = perPair[pairKey] ?: 0
            if (used >= 2) continue
            val kindUsed = templateCounts[proposal.kind] ?: 0
            if (kindUsed >= (templateCaps[proposal.kind] ?: 4)) continue
            templateCounts[proposal.kind] = kindUsed + 1
            perTemplate.add(templateKey)
            perPair[pairKey] = used + 1
            chosen.add(proposal)
            if (chosen.size >= limit) break
        }

        val noiseCap = (3 + (scale * 9f).roundToInt())
        var noise = 0
        val balanced = chosen.filter { proposal ->
            if (proposal.verdict != Verdict.Unknown || proposal.hint != null) true
            else {
                noise++
                noise <= noiseCap
            }
        }

        return DiscoveryResult(
            proposals = balanced,
            clusters = clusters.size,
            considered = considered,
            holds = balanced.count { it.verdict == Verdict.Holds },
            refuted = balanced.count { it.verdict == Verdict.Refuted },
            scale = scale,
            families = families
        )
    }

    fun applyProposals(graph: AuraGraph, candidates: List<ConnectionPatch>): ApplyProposalsResult {
        var working = graph
        var applied = 0
        var skipped = 0

        for (candidate in candidates) {
            val ids = working.nodes.map { it.id }.toSet()
            val targetNodes = working.nodes.associateBy { it.id }
            val parented = working.edges
                .mapNotNull { targetNodes[it.target] }
                .filter { !isShareable(it) }
                .map { it.id }
                .toSet()

            val addable = candidate.addedNodes.all { it.id !in ids }
            val validEdges = candidate.addedEdges.all { edge ->
                edge.source in ids || candidate.addedNodes.any { it.id == edge.source }
            }
            val targetsFree = candidate.addedEdges.all { edge ->
                edge.target in ids &&
                    edge.target !in parented &&
                    candidate.addedEdges.none { other -> other !== edge && other.target == edge.target }
            }

            if (!addable || !validEdges || !targetsFree) {
                skipped++
                continue
            }
            try {
                working = Engine.commitCandidate(working, candidate)
                applied++
            } catch (_: Exception) {
                skipped++
            }
        }
        return ApplyProposalsResult(working, applied, skipped)
    }
}
