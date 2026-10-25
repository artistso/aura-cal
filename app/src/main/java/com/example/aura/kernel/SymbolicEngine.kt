package com.example.aura.kernel

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Aura Symbolic Algebra, Step-by-Step Equation Solver, and Formal Proof Assistant.
 * Operates directly over the typed hypergraph and produces inspectable RewriteSteps.
 */

data class RewriteStep(
    val ruleName: String,
    val description: String,
    val beforeLatex: String,
    val afterLatex: String,
    val graph: AuraGraph,
    val focusRootId: String
)

enum class ProofMethod(val label: String) {
    SymbolicRewrite("Symbolic Equational Proof"),
    NumericSampling("12-Point Deterministic Sampling"),
    TypeLevel("Type-Lattice Admissibility")
}

data class ProofTrace(
    val method: ProofMethod,
    val verified: Boolean,
    val steps: List<String>,
    val summary: String
)

object SymbolicEngine {

    private fun normalizeTex(tex: String): String =
        tex.replace("\\left(", "(")
            .replace("\\right)", ")")
            .replace("\\,", "")
            .replace(" ", "")
            .replace("{", "")
            .replace("}", "")

    /**
     * Generates a step-by-step algebraic simplification sequence for the cluster rooted at [rootId]
     * (or the first simplify-eligible cluster on the canvas if [rootId] is null).
     */
    fun simplifyStepByStep(graph: AuraGraph, rootId: String? = null): List<RewriteStep> {
        val steps = ArrayList<RewriteStep>()
        var currentGraph = graph
        val index0 = Engine.indexGraph(currentGraph)
        val targets = if (rootId != null) {
            listOfNotNull(Engine.componentRoot(index0, rootId)?.id ?: rootId)
        } else {
            Engine.componentsOf(currentGraph).map { it.rootId }
        }

        for (targetRootId in targets) {
            var workingRootId = targetRootId
            for (pass in 0 until 6) {
                val idx = Engine.indexGraph(currentGraph)
                val root = idx.byId[workingRootId] ?: break
                val beforeTex = Engine.toLatex(currentGraph, root.id, idx)

                // Pass 1: Constant subexpression folding inside the cluster
                val fold = Evaluate.foldConstants(currentGraph)
                if (fold.folded > 0) {
                    val newIdx = Engine.indexGraph(fold.graph)
                    val nextRoot = newIdx.byId[workingRootId]
                        ?: Engine.componentsOf(fold.graph).firstOrNull()?.let { newIdx.byId[it.rootId] }
                    if (nextRoot != null) {
                        val afterTex = Engine.toLatex(fold.graph, nextRoot.id, newIdx)
                        if (afterTex != beforeTex) {
                            currentGraph = Engine.reflowGraph(fold.graph)
                            workingRootId = nextRoot.id
                            steps.add(
                                RewriteStep(
                                    ruleName = "Constant Folding",
                                    description = "Evaluated constant subexpression (${fold.summaries.firstOrNull() ?: ""})",
                                    beforeLatex = beforeTex,
                                    afterLatex = afterTex,
                                    graph = currentGraph,
                                    focusRootId = workingRootId
                                )
                            )
                            continue
                        }
                    }
                }

                // Pass 2: Algebraic pattern rewrites on the cluster's LaTeX AST
                val rewrite = applyAlgebraicRule(beforeTex)
                if (rewrite != null && rewrite.afterLatex != beforeTex) {
                    val replaced = replaceClusterWithLatex(currentGraph, workingRootId, rewrite.afterLatex)
                    if (replaced != null) {
                        currentGraph = replaced.first
                        workingRootId = replaced.second
                        val renderedAfter = Engine.toLatex(currentGraph, workingRootId)
                        steps.add(
                            RewriteStep(
                                ruleName = rewrite.ruleName,
                                description = rewrite.description,
                                beforeLatex = beforeTex,
                                afterLatex = renderedAfter,
                                graph = currentGraph,
                                focusRootId = workingRootId
                            )
                        )
                        continue
                    }
                }
                break
            }
        }
        return steps
    }

    private data class RuleMatch(
        val ruleName: String,
        val description: String,
        val afterLatex: String
    )

    private fun applyAlgebraicRule(latex: String): RuleMatch? {
        val compact = normalizeTex(latex)

        // 1. Pythagorean Trigonometric Identity: sin^2(t) + cos^2(t) -> 1
        val hasSinSq = compact.contains("\\sin^2") || Regex("\\\\sin\\([^)]+\\)\\^2").containsMatchIn(compact)
        val hasCosSq = compact.contains("\\cos^2") || Regex("\\\\cos\\([^)]+\\)\\^2").containsMatchIn(compact)
        if (hasSinSq && hasCosSq && compact.contains("+")) {
            if (compact.startsWith("\\sin") || compact.startsWith("\\cos")) {
                return RuleMatch(
                    ruleName = "Pythagorean Identity",
                    description = "Applied \\sin^2\\theta + \\cos^2\\theta = 1",
                    afterLatex = "1"
                )
            }
        }

        // 2. Additive Identity: X + 0 -> X or 0 + X -> X
        val addZeroRight = Regex("^(.+)\\+0$").find(compact)
        if (addZeroRight != null) {
            val base = latex.substringBeforeLast("+").trim()
            return RuleMatch("Additive Identity", "Eliminated + 0", base)
        }
        val addZeroLeft = Regex("^0\\+(.+)$").find(compact)
        if (addZeroLeft != null) {
            val base = latex.substringAfter("+").trim()
            return RuleMatch("Additive Identity", "Eliminated 0 +", base)
        }

        // 3. Multiplicative Identity: X * 1 -> X or 1 * X -> X
        if (latex.endsWith(" \\times 1") || latex.endsWith("\\,1")) {
            val base = latex.removeSuffix(" \\times 1").removeSuffix("\\,1").trim()
            return RuleMatch("Multiplicative Identity", "Simplified multiplication by 1", base)
        }
        if (latex.startsWith("1 \\times ") || latex.startsWith("1\\,")) {
            val base = latex.removePrefix("1 \\times ").removePrefix("1\\,").trim()
            return RuleMatch("Multiplicative Identity", "Simplified multiplication by 1", base)
        }

        // 4. Multiplication by Zero: X * 0 -> 0
        if (latex.endsWith(" \\times 0") || latex.startsWith("0 \\times ") ||
            latex.endsWith("\\,0") || latex.startsWith("0\\,")
        ) {
            return RuleMatch("Zero Property of Multiplication", "Any expression multiplied by 0 vanishes", "0")
        }

        // 5. Self-Subtraction: X - X -> 0
        if (latex.contains(" - ")) {
            val parts = latex.split(" - ")
            if (parts.size == 2 && normalizeTex(parts[0]) == normalizeTex(parts[1])) {
                return RuleMatch("Additive Inverse", "Cancelled identical terms ${parts[0]} - ${parts[1]}", "0")
            }
        }

        // 6. Like-Term Addition: X + X -> 2 X
        if (latex.contains(" + ")) {
            val parts = latex.split(" + ")
            if (parts.size == 2 && normalizeTex(parts[0]) == normalizeTex(parts[1])) {
                return RuleMatch("Collect Like Terms", "Combined ${parts[0]} + ${parts[1]} into 2${parts[0]}", "2 ${parts[0]}")
            }
        }

        // 7. Self-Division: \frac{X}{X} -> 1
        val fracMatch = Regex("^\\\\frac\\{(.+)\\}\\{(.+)\\}$").find(latex.trim())
        if (fracMatch != null) {
            val num = fracMatch.groupValues[1]
            val den = fracMatch.groupValues[2]
            if (normalizeTex(num) == normalizeTex(den) && normalizeTex(den) != "0") {
                return RuleMatch("Multiplicative Inverse", "Cancelled common factor in numerator and denominator", "1")
            }
            if (normalizeTex(den) == "1") {
                return RuleMatch("Division by Unity", "Simplified fraction with denominator 1", num)
            }
        }

        // 8. Power Laws: X^1 -> X, X^0 -> 1
        val powMatch = Regex("^\\{(.+)\\}\\^\\{(0|1)\\}$").find(latex.trim())
        if (powMatch != null) {
            val base = powMatch.groupValues[1]
            val exp = powMatch.groupValues[2]
            return if (exp == "0") {
                RuleMatch("Zero Exponent Law", "Any non-zero base to the 0th power equals 1", "1")
            } else {
                RuleMatch("Unit Exponent Law", "Simplified exponent 1", base)
            }
        }

        // 9. Logarithm-Exponential Inverse: \ln(e^x) -> x
        val lnExp = Regex("^\\\\ln\\\\left\\(\\{e\\}\\^\\{(.+)\\}\\\\right\\)$").find(latex.trim())
        if (lnExp != null) {
            return RuleMatch("Logarithm–Exponential Inverse", "Applied \\ln(e^u) = u", lnExp.groupValues[1])
        }

        return null
    }

    /**
     * Step-by-Step Symbolic Equation Solver:
     * Given a relation `LHS = RHS` on the canvas and a target variable [targetVar],
     * constructs a sequence of valid algebraic isolation steps ending in `targetVar = ...`.
     */
    fun solveForVariable(
        graph: AuraGraph,
        relationRootId: String,
        targetVar: String
    ): List<RewriteStep> {
        val index = Engine.indexGraph(graph)
        val root = index.byId[relationRootId] ?: return emptyList()
        if (root.kind != NodeKind.Relation || root.variant != "equal") return emptyList()

        val steps = ArrayList<RewriteStep>()
        var currentGraph = graph
        var currentRootId = relationRootId

        for (stepIdx in 0 until 6) {
            val idx = Engine.indexGraph(currentGraph)
            val rel = idx.byId[currentRootId] ?: break
            var left = Engine.childOf(idx, rel.id, PortId.Left) ?: break
            var right = Engine.childOf(idx, rel.id, PortId.Right) ?: break

            val leftVars = Evaluate.collectVariables(idx, left.id)
            val rightVars = Evaluate.collectVariables(idx, right.id)
            val beforeTex = Engine.toLatex(currentGraph, rel.id, idx)

            // Already isolated: targetVar = RHS (where targetVar is not in RHS)
            if (left.kind == NodeKind.Variable && left.token == targetVar && targetVar !in rightVars) {
                break
            }

            // Step A: If targetVar is only on the right side, swap sides (Symmetric Property of Equality)
            if (targetVar !in leftVars && targetVar in rightVars) {
                val leftTex = Engine.toLatex(currentGraph, left.id, idx)
                val rightTex = Engine.toLatex(currentGraph, right.id, idx)
                val swappedLatex = "$rightTex = $leftTex"
                val replaced = replaceClusterWithLatex(currentGraph, currentRootId, swappedLatex) ?: break
                currentGraph = replaced.first
                currentRootId = replaced.second
                steps.add(
                    RewriteStep(
                        ruleName = "Symmetric Property of Equality",
                        description = "Swapped sides to place $targetVar on the left-hand side",
                        beforeLatex = beforeTex,
                        afterLatex = Engine.toLatex(currentGraph, currentRootId),
                        graph = currentGraph,
                        focusRootId = currentRootId
                    )
                )
                continue
            }

            val rhsTex = Engine.toLatex(currentGraph, right.id, idx)

            // Step B: Unwrap outer operator on LHS containing targetVar
            if (left.kind == NodeKind.Operator) {
                val v = left.variant
                val lChild = Engine.childOf(idx, left.id, PortId.Left)
                val rChild = Engine.childOf(idx, left.id, PortId.Right)

                if (v == "add" && lChild != null && rChild != null) {
                    val inLeft = targetVar in Evaluate.collectVariables(idx, lChild.id)
                    val keepTex = Engine.toLatex(currentGraph, if (inLeft) lChild.id else rChild.id, idx)
                    val moveTex = Engine.toLatex(currentGraph, if (inLeft) rChild.id else lChild.id, idx)
                    val nextLatex = "$keepTex = ($rhsTex) - ($moveTex)"
                    val replaced = replaceClusterWithLatex(currentGraph, currentRootId, nextLatex) ?: break
                    currentGraph = replaced.first
                    currentRootId = replaced.second
                    steps.add(
                        RewriteStep(
                            ruleName = "Subtraction Property of Equality",
                            description = "Subtract $moveTex from both sides",
                            beforeLatex = beforeTex,
                            afterLatex = Engine.toLatex(currentGraph, currentRootId),
                            graph = currentGraph,
                            focusRootId = currentRootId
                        )
                    )
                    continue
                }

                if (v == "sub" && lChild != null && rChild != null) {
                    val inLeft = targetVar in Evaluate.collectVariables(idx, lChild.id)
                    val lTex = Engine.toLatex(currentGraph, lChild.id, idx)
                    val rTex = Engine.toLatex(currentGraph, rChild.id, idx)
                    val (rule, desc, nextLatex) = if (inLeft) {
                        Triple(
                            "Addition Property of Equality",
                            "Add $rTex to both sides",
                            "$lTex = ($rhsTex) + ($rTex)"
                        )
                    } else {
                        Triple(
                            "Rearrange Subtraction",
                            "Isolate subtracted term $rTex",
                            "$rTex = ($lTex) - ($rhsTex)"
                        )
                    }
                    val replaced = replaceClusterWithLatex(currentGraph, currentRootId, nextLatex) ?: break
                    currentGraph = replaced.first
                    currentRootId = replaced.second
                    steps.add(
                        RewriteStep(
                            ruleName = rule,
                            description = desc,
                            beforeLatex = beforeTex,
                            afterLatex = Engine.toLatex(currentGraph, currentRootId),
                            graph = currentGraph,
                            focusRootId = currentRootId
                        )
                    )
                    continue
                }

                if ((v == "mul" || v == "juxta") && lChild != null && rChild != null) {
                    val inLeft = targetVar in Evaluate.collectVariables(idx, lChild.id)
                    val keepTex = Engine.toLatex(currentGraph, if (inLeft) lChild.id else rChild.id, idx)
                    val divTex = Engine.toLatex(currentGraph, if (inLeft) rChild.id else lChild.id, idx)
                    val nextLatex = "$keepTex = \\frac{$rhsTex}{$divTex}"
                    val replaced = replaceClusterWithLatex(currentGraph, currentRootId, nextLatex) ?: break
                    currentGraph = replaced.first
                    currentRootId = replaced.second
                    steps.add(
                        RewriteStep(
                            ruleName = "Division Property of Equality",
                            description = "Divide both sides by $divTex",
                            beforeLatex = beforeTex,
                            afterLatex = Engine.toLatex(currentGraph, currentRootId),
                            graph = currentGraph,
                            focusRootId = currentRootId
                        )
                    )
                    continue
                }

                if (v == "frac") {
                    val num = Engine.childOf(idx, left.id, PortId.Numerator)
                    val den = Engine.childOf(idx, left.id, PortId.Denominator)
                    if (num != null && den != null) {
                        val inNum = targetVar in Evaluate.collectVariables(idx, num.id)
                        val numTex = Engine.toLatex(currentGraph, num.id, idx)
                        val denTex = Engine.toLatex(currentGraph, den.id, idx)
                        val (rule, desc, nextLatex) = if (inNum) {
                            Triple(
                                "Multiplication Property of Equality",
                                "Multiply both sides by denominator $denTex",
                                "$numTex = ($rhsTex) \\times ($denTex)"
                            )
                        } else {
                            Triple(
                                "Cross-Multiply Denominator",
                                "Multiply by $denTex and divide by $rhsTex",
                                "$denTex = \\frac{$numTex}{$rhsTex}"
                            )
                        }
                        val replaced = replaceClusterWithLatex(currentGraph, currentRootId, nextLatex) ?: break
                        currentGraph = replaced.first
                        currentRootId = replaced.second
                        steps.add(
                            RewriteStep(
                                ruleName = rule,
                                description = desc,
                                beforeLatex = beforeTex,
                                afterLatex = Engine.toLatex(currentGraph, currentRootId),
                                graph = currentGraph,
                                focusRootId = currentRootId
                            )
                        )
                        continue
                    }
                }

                if (v == "power") {
                    val base = Engine.childOf(idx, left.id, PortId.Base)
                    val exp = Engine.childOf(idx, left.id, PortId.Exponent)
                    if (base != null && exp != null) {
                        val baseTex = Engine.toLatex(currentGraph, base.id, idx)
                        val expTex = Engine.toLatex(currentGraph, exp.id, idx)
                        if (targetVar in Evaluate.collectVariables(idx, base.id)) {
                            // x^(2k) = c has two real branches; writing only the principal root
                            // would silently drop one of them.
                            val degree = Evaluate.evalExpression(idx, exp.id)
                            val evenDegree = degree != null &&
                                abs(degree - degree.roundToInt()) < 1e-9 &&
                                degree.roundToInt() % 2 == 0 && degree >= 2
                            val sign = if (evenDegree) "\\pm " else ""
                            val nextLatex = if (expTex == "2") {
                                "$baseTex = $sign\\sqrt{$rhsTex}"
                            } else {
                                "$baseTex = $sign\\sqrt[$expTex]{$rhsTex}"
                            }
                            val replaced = replaceClusterWithLatex(currentGraph, currentRootId, nextLatex) ?: break
                            currentGraph = replaced.first
                            currentRootId = replaced.second
                            steps.add(
                                RewriteStep(
                                    ruleName = if (evenDegree) "Even Root Extraction" else "Principal Root Extraction",
                                    description = if (evenDegree) {
                                        "Take the ${if (expTex == "2") "square" else "${expTex}-th"} root of both sides — an even power has two real roots"
                                    } else {
                                        "Take the ${if (expTex == "2") "square" else "${expTex}-th"} root of both sides"
                                    },
                                    beforeLatex = beforeTex,
                                    afterLatex = Engine.toLatex(currentGraph, currentRootId),
                                    graph = currentGraph,
                                    focusRootId = currentRootId
                                )
                            )
                            continue
                        }
                    }
                }
            }

            if (left.kind == NodeKind.Container && left.variant == "sqrt") {
                val body = Engine.childOf(idx, left.id, PortId.Body)
                if (body != null) {
                    val bodyTex = Engine.toLatex(currentGraph, body.id, idx)
                    val nextLatex = "$bodyTex = ($rhsTex)^2"
                    val replaced = replaceClusterWithLatex(currentGraph, currentRootId, nextLatex) ?: break
                    currentGraph = replaced.first
                    currentRootId = replaced.second
                    steps.add(
                        RewriteStep(
                            ruleName = "Square Both Sides",
                            description = "Eliminate radical by squaring both sides",
                            beforeLatex = beforeTex,
                            afterLatex = Engine.toLatex(currentGraph, currentRootId),
                            graph = currentGraph,
                            focusRootId = currentRootId
                        )
                    )
                    continue
                }
            }

            break
        }
        collapseSolution(currentGraph, currentRootId, targetVar)?.let { steps.add(it) }
        return steps
    }

    /**
     * Once the target variable is isolated, evaluates the right-hand side so the solve ends in
     * a value (`x = 4`) instead of an unevaluated expression (`x = \frac{11 - 3}{2}`).
     * A sign-ambiguous (±) result keeps both branches.
     */
    private fun collapseSolution(
        graph: AuraGraph,
        rootId: String,
        targetVar: String
    ): RewriteStep? {
        val index = Engine.indexGraph(graph)
        val root = index.byId[rootId] ?: return null
        if (root.kind != NodeKind.Relation || root.variant != "equal") return null
        val left = Engine.childOf(index, root.id, PortId.Left) ?: return null
        val right = Engine.childOf(index, root.id, PortId.Right) ?: return null
        if (left.kind != NodeKind.Variable || left.token != targetVar) return null

        // `±` is not a value (it denotes a set of values), so an even-root solution cannot be
        // evaluated as a whole. Evaluate the *operand* and render both branches.
        val signNode = if (right.variant == "pm" || right.variant == "mp") right else null
        val signOperand = if (signNode != null) {
            Engine.childOf(index, signNode.id, PortId.Arg)
        } else null
        val value = if (signNode != null) {
            signOperand?.let { Evaluate.evalExpression(index, it.id) } ?: return null
        } else {
            Evaluate.evalExpression(index, right.id) ?: return null
        }
        if (!value.isFinite()) return null
        val signAmbiguous = signNode != null
        val rendered = Evaluate.formatNumber(if (signAmbiguous) abs(value) else value)
        val target = if (signAmbiguous) "$targetVar = \\pm $rendered" else "$targetVar = $rendered"
        val beforeTex = Engine.toLatex(graph, root.id, index)
        if (target.replace(" ", "") == beforeTex.replace(" ", "")) return null
        val replaced = replaceClusterWithLatex(graph, rootId, target) ?: return null
        return RewriteStep(
            ruleName = "Evaluate Solution",
            description = if (signAmbiguous) {
                "Evaluated both real roots to ±$rendered"
            } else {
                "Evaluated the isolated expression to $rendered"
            },
            beforeLatex = beforeTex,
            afterLatex = Engine.toLatex(replaced.first, replaced.second),
            graph = replaced.first,
            focusRootId = replaced.second
        )
    }

    /**
     * Formal Proof Trace Generator for Discovery Proposals:
     * Produces a structured proof trace explaining why a proposed connection holds or is refuted.
     */
    fun buildProofTrace(
        graph: AuraGraph,
        leftRootId: String,
        rightRootId: String,
        relationToken: String,
        verdict: Verdict,
        hint: String?,
        samples: Int,
        counterexample: String?
    ): ProofTrace {
        val idx = Engine.indexGraph(graph)
        val leftTex = Engine.toLatex(graph, leftRootId, idx)
        val rightTex = Engine.toLatex(graph, rightRootId, idx)

        if (relationToken == "\\in") {
            val lType = Engine.effectiveType(idx, leftRootId)
            return ProofTrace(
                method = ProofMethod.TypeLevel,
                verified = verdict == Verdict.Holds,
                steps = listOf(
                    "1. Infer type of LHS cluster ($leftTex) ⊢ ${lType.label}",
                    "2. Inspect Set target on RHS ($rightTex)",
                    if (verdict == Verdict.Holds) "3. By default real-valued widening, ${lType.label} ⊆ ℝ holds."
                    else "3. Set membership requires explicit domain declaration."
                ),
                summary = if (verdict == Verdict.Holds) "Verified by Type-Lattice Admissibility" else "Type-level domain claim"
            )
        }

        // Check if symbolic simplification reduces LHS and RHS to identical canonical forms
        val leftSteps = simplifyStepByStep(graph, leftRootId)
        val rightSteps = simplifyStepByStep(graph, rightRootId)
        val finalLeft = leftSteps.lastOrNull()?.afterLatex ?: leftTex
        val finalRight = rightSteps.lastOrNull()?.afterLatex ?: rightTex

        if (verdict == Verdict.Holds && normalizeTex(finalLeft) == normalizeTex(finalRight)) {
            val proofLines = ArrayList<String>()
            proofLines.add("Goal: Prove $leftTex $relationToken $rightTex")
            leftSteps.forEachIndexed { i, s ->
                proofLines.add("Step ${i + 1} (${s.ruleName}): ${s.beforeLatex} ↦ ${s.afterLatex}")
            }
            rightSteps.forEachIndexed { i, s ->
                proofLines.add("Step ${leftSteps.size + i + 1} (${s.ruleName}): ${s.beforeLatex} ↦ ${s.afterLatex}")
            }
            proofLines.add("Q.E.D. Both sides reduce to canonical form $finalLeft ∎")
            return ProofTrace(
                method = ProofMethod.SymbolicRewrite,
                verified = true,
                steps = proofLines,
                summary = "Formal equational proof (${leftSteps.size + rightSteps.size + 1} steps)"
            )
        }

        if (hint != null) {
            return ProofTrace(
                method = ProofMethod.SymbolicRewrite,
                verified = verdict == Verdict.Holds,
                steps = listOf(
                    "1. Pattern-matched structural signature against mathematical law catalog",
                    "2. Identified canonical law: $hint",
                    if (verdict == Verdict.Holds) "3. Verified across all $samples deterministic sample evaluations (tolerance 1e-6) ∎"
                    else "3. Symbolic law contains free physical/structural constants not fixed by numeric sampling."
                ),
                summary = "Recognised Law: $hint"
            )
        }

        if (verdict == Verdict.Refuted && counterexample != null) {
            return ProofTrace(
                method = ProofMethod.NumericSampling,
                verified = false,
                steps = listOf(
                    "1. Evaluated LHS ($leftTex) and RHS ($rightTex) over deterministic sample table",
                    "2. Witness counterexample found at $counterexample",
                    "3. Universal claim refuted by counterexample ⊥"
                ),
                summary = "Refuted with witness ($counterexample)"
            )
        }

        return ProofTrace(
            method = ProofMethod.NumericSampling,
            verified = verdict == Verdict.Holds,
            steps = listOf(
                "1. Collected free variables across ($leftTex) and ($rightTex)",
                "2. Evaluated $samples deterministic assignments within relative tolerance 1e-6",
                if (verdict == Verdict.Holds) "3. Agreement holds on all $samples/12 sample points."
                else "3. Non-boolean operator or insufficient numeric constraints to decide."
            ),
            summary = if (verdict == Verdict.Holds) "Verified on $samples/$samples sample points" else "Exploratory candidate"
        )
    }

    private fun replaceClusterWithLatex(
        graph: AuraGraph,
        oldRootId: String,
        newLatex: String
    ): Pair<AuraGraph, String>? {
        return try {
            val idx = Engine.indexGraph(graph)
            val oldRoot = idx.byId[oldRootId] ?: return null
            val anchor = PointF(oldRoot.x, oldRoot.y)
            val oldIds = Engine.componentIds(graph, oldRootId)
            val parsed = LatexParser.parseLatex(newLatex)
            val newRootId = parsed.rootId ?: return null
            val parsedIdx = Engine.indexGraph(parsed.graph)
            val layout = Engine.layoutComponent(parsed.graph, newRootId, anchor, parsedIdx)
            val positionedNewNodes = parsed.graph.nodes.map { n ->
                val pt = layout[n.id]
                if (pt != null) n.copy(x = pt.x, y = pt.y) else n
            }
            val remainingNodes = graph.nodes.filter { it.id !in oldIds }
            val remainingEdges = graph.edges.filter { it.source !in oldIds && it.target !in oldIds }
            AuraGraph(remainingNodes + positionedNewNodes, remainingEdges + parsed.graph.edges) to newRootId
        } catch (_: Exception) {
            null
        }
    }
}
