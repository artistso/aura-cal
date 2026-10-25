package com.example.aura.kernel

import java.util.Locale
import kotlin.math.*

/**
 * Aura's numeric & multi-variable plotting layer over the typed hypergraph.
 * Supports:
 * 1. Port-walking numeric evaluation with environment bindings & parameter sliders
 * 2. Constant subexpression folding ("simplify")
 * 3. Single-variable 2D trace sampling & Multi-variable 3D surface mesh sampling (z = f(x, y))
 * 4. Variable scope halo indexing across clusters
 */

data class PlotTarget2D(
    val yRootId: String,
    val yLatex: String,
    val variable: String,
    val parameters: List<String> = emptyList()
)

data class PlotTarget3D(
    val zRootId: String,
    val zLatex: String,
    val xVar: String,
    val yVar: String,
    val parameters: List<String> = emptyList()
)

data class ClusterAnalysis(
    val rootId: String,
    val nodeIds: List<String>,
    val latex: String,
    val variables: List<String>,
    val value: Double?,
    val plottable: PlotTarget2D?,
    val plottable3D: PlotTarget3D? = null,
    val isRelation: Boolean
)

data class SamplePoint2D(val x: Float, val y: Float)

data class SurfacePoint3D(val x: Float, val y: Float, val z: Float)

data class SurfaceMesh3D(
    val gridSize: Int,
    val points: List<List<SurfacePoint3D?>>,
    val minZ: Float,
    val maxZ: Float
)

data class FoldResult(
    val graph: AuraGraph,
    val folded: Int,
    val summaries: List<String>
)

data class ScopeEntry(
    val token: String,
    val colorHex: Long,
    val nodeIds: List<String>,
    val clusterIds: List<String>
)

object Evaluate {
    private val SCOPE_COLORS = listOf(
        0xFFF0B45AL,
        0xFF5EEAD4L,
        0xFFF472B6L,
        0xFFA78BFAL,
        0xFF60A5FAL,
        0xFF34D399L,
        0xFFFB7185L,
        0xFFFBBF24L
    )

    fun parseLiteral(token: String): Double? {
        val text = token.trim()
        if (text.matches(Regex("^[+-]?\\d+(\\.\\d+)?$"))) return text.toDoubleOrNull()
        if (text.matches(Regex("^[+-]?\\.\\d+$"))) return text.toDoubleOrNull()
        if (text == "\\pi") return Math.PI
        if (text == "e") return Math.E
        if (text == "\\varphi") return (1.0 + sqrt(5.0)) / 2.0
        if (text == "\\sqrt{2}") return sqrt(2.0)
        if (text == "\\tau") return 2.0 * Math.PI
        val fracMatch = Regex("^\\\\[td]?frac\\{([^}]+)\\}\\{([^}]+)\\}$").find(text)
        if (fracMatch != null) {
            val num = parseLiteral(fracMatch.groupValues[1])
            val den = parseLiteral(fracMatch.groupValues[2])
            if (num != null && den != null && den != 0.0) return num / den
        }
        return null
    }

    fun formatNumber(value: Double): String {
        if (!value.isFinite()) return "\\infty"
        val r = value.roundToLong()
        if (abs(value - r.toDouble()) < 1e-9 && abs(value) < 1e12) {
            return r.toString()
        }
        return String.format(Locale.US, "%.5g", value).trimEnd('0').trimEnd('.')
    }

    private fun evalUnary(key: String, value: Double): Double? = when (key) {
        "sin" -> sin(value)
        "cos" -> cos(value)
        "tan" -> tan(value)
        "sec" -> 1.0 / cos(value)
        "csc" -> 1.0 / sin(value)
        "cot" -> 1.0 / tan(value)
        "sinc" -> if (value == 0.0) 1.0 else sin(value) / value
        "arcsin", "asin" -> asin(value)
        "arccos", "acos" -> acos(value)
        "arctan", "atan" -> atan(value)
        "sinh" -> sinh(value)
        "cosh" -> cosh(value)
        "tanh" -> tanh(value)
        "sech" -> 1.0 / cosh(value)
        "csch" -> 1.0 / sinh(value)
        "coth" -> 1.0 / tanh(value)
        "arsinh", "asinh" -> ln(value + sqrt(value * value + 1.0))
        "arcosh", "acosh" -> if (value >= 1.0) ln(value + sqrt(value * value - 1.0)) else null
        "artanh", "atanh" -> if (abs(value) < 1.0) 0.5 * ln((1.0 + value) / (1.0 - value)) else null
        "ln" -> if (value > 0.0) ln(value) else null
        "log", "log10" -> if (value > 0.0) log10(value) else null
        "log2" -> if (value > 0.0) ln(value) / ln(2.0) else null
        "exp" -> exp(value)
        "sqrt" -> if (value >= 0.0) sqrt(value) else null
        "abs" -> abs(value)
        "floor" -> floor(value)
        "ceil" -> ceil(value)
        "tr", "re" -> value
        "im" -> 0.0
        else -> null
    }

    private fun factorial(value: Double): Double? {
        val n = value.roundToInt()
        if (abs(value - n) > 1e-9 || n < 0 || n > 170) return null
        var res = 1.0
        for (i in 2..n) res *= i.toDouble()
        return res
    }

    fun evalExpression(
        index: GraphIndex,
        nodeId: String,
        env: Map<String, Double> = emptyMap(),
        guard: Set<String> = emptySet()
    ): Double? {
        val node = index.byId[nodeId] ?: return null
        if (guard.contains(nodeId)) return null
        val next = LinkedHashSet(guard).apply { add(nodeId) }

        fun child(port: PortId): Double? {
            val bound = Engine.childOf(index, nodeId, port) ?: return null
            return evalExpression(index, bound.id, env, next)
        }

        val baseValue: Double? = when (node.kind) {
            NodeKind.Literal -> parseLiteral(node.token)
            NodeKind.Variable -> {
                if (node.token == "e" && !env.containsKey("e")) Math.E
                else env[node.token] ?: env[node.label]
            }
            NodeKind.Set, NodeKind.Group, NodeKind.Relation -> null
            NodeKind.Function -> {
                val arg = child(PortId.Arg) ?: return null
                val key = (node.variant ?: node.token.replace("\\", ""))
                    .replace(Regex("^operatorname\\{?|\\}?$"), "")
                    .lowercase()
                evalUnary(key, arg)
            }
            NodeKind.Container -> {
                val v = node.variant ?: ""
                when {
                    v in Ontology.ACCENT_VARIANTS -> child(PortId.Body)
                    v == "sqrt" -> {
                        val b = child(PortId.Body)
                        if (b == null || b < 0.0) null else sqrt(b)
                    }
                    v == "root" -> {
                        val b = child(PortId.Body) ?: return null
                        val idx = child(PortId.Upper) ?: 2.0
                        if (b < 0.0 && idx.roundToInt() % 2 == 0) null
                        else sign(b) * abs(b).pow(1.0 / idx)
                    }
                    v in Ontology.ENCLOSURE_VARIANTS -> {
                        val b = child(PortId.Body) ?: return null
                        when (v) {
                            "floor" -> floor(b)
                            "ceil" -> ceil(b)
                            else -> abs(b)
                        }
                    }
                    v in Ontology.BIG_VARIANTS -> {
                        val bodyNode = Engine.childOf(index, nodeId, PortId.Body) ?: return null
                        if (v != "sum" && v != "prod") return null
                        val lowerNode = Engine.childOf(index, nodeId, PortId.Lower)
                        val upperNode = Engine.childOf(index, nodeId, PortId.Upper)
                        var indexVar = "n"
                        var start: Double? = null
                        if (lowerNode != null) {
                            if (lowerNode.kind == NodeKind.Relation && lowerNode.variant == "equal") {
                                val bound = Engine.childOf(index, lowerNode.id, PortId.Left)
                                val rhs = Engine.childOf(index, lowerNode.id, PortId.Right)
                                if (bound?.kind == NodeKind.Variable) indexVar = bound.token
                                start = if (rhs != null) evalExpression(index, rhs.id, env) else null
                            } else {
                                start = evalExpression(index, lowerNode.id, env)
                            }
                        }
                        val end = if (upperNode != null) evalExpression(index, upperNode.id, env) else null
                        if (start == null || end == null) {
                            evalExpression(index, bodyNode.id, env)
                        } else {
                            val sInt = start.roundToInt()
                            val eInt = end.roundToInt()
                            if (abs(start - sInt) > 1e-9 || abs(end - eInt) > 1e-9 || eInt - sInt > 2000) null
                            else {
                                var total = if (v == "sum") 0.0 else 1.0
                                var ok = true
                                for (step in sInt..eInt) {
                                    val nextEnv = LinkedHashMap(env).apply { put(indexVar, step.toDouble()) }
                                    val term = evalExpression(index, bodyNode.id, nextEnv)
                                    if (term == null || !term.isFinite()) {
                                        ok = false
                                        break
                                    }
                                    total = if (v == "sum") total + term else total * term
                                }
                                if (ok) total else null
                            }
                        }
                    }
                    else -> null
                }
            }
            NodeKind.Operator -> {
                val v = node.variant
                when {
                    v != null && v in Ontology.ENCLOSURE_VARIANTS -> {
                        val arg = child(PortId.Arg) ?: child(PortId.Body) ?: return null
                        when (v) {
                            "floor" -> floor(arg)
                            "ceil" -> ceil(arg)
                            else -> abs(arg)
                        }
                    }
                    v == "power" -> {
                        val base = child(PortId.Base)
                        val exp = child(PortId.Exponent)
                        if (base == null || exp == null) null
                        else {
                            val res = base.pow(exp)
                            if (res.isFinite()) res else null
                        }
                    }
                    v == "frac" -> {
                        val num = child(PortId.Numerator)
                        val den = child(PortId.Denominator)
                        if (num == null || den == null || den == 0.0) null else num / den
                    }
                    v == "binom" -> {
                        val upper = child(PortId.Numerator)
                        val lower = child(PortId.Denominator)
                        if (upper == null || lower == null) null
                        else {
                            val uf = factorial(upper)
                            val lf = factorial(lower)
                            val df = factorial(upper - lower)
                            if (uf == null || lf == null || df == null) null else uf / (lf * df)
                        }
                    }
                    v != null && v in Ontology.POSTFIX_VARIANTS -> {
                        val arg = child(PortId.Arg) ?: return null
                        when (v) {
                            "fact" -> factorial(arg)
                            "degree" -> (arg * 180.0) / Math.PI
                            else -> arg
                        }
                    }
                    v != null && v in Ontology.PREFIX_VARIANTS -> {
                        // `\pm` denotes a set of values, not a value: it is undefined, not its operand.
                        if (v == "pm" || v == "mp") return null
                        val arg = child(PortId.Arg) ?: return null
                        if (v == "neg") -arg else arg
                    }
                    else -> {
                        val left = child(PortId.Left)
                        val right = child(PortId.Right)
                        if (left == null || right == null) null
                        else when (v) {
                            "add" -> left + right
                            "sub" -> left - right
                            "mul", "juxta", "div" -> if (v == "div") (if (right == 0.0) null else left / right) else left * right
                            else -> null
                        }
                    }
                }
            }
        }

        if (baseValue == null) return null
        // Handle leaf exponent port if attached directly to a variable or literal
        if (node.kind == NodeKind.Literal || node.kind == NodeKind.Variable) {
            val leafExp = child(PortId.Exponent)
            if (leafExp != null) {
                val p = baseValue.pow(leafExp)
                return if (p.isFinite()) p else null
            }
        }
        return baseValue
    }

    fun collectVariables(
        index: GraphIndex,
        nodeId: String,
        out: MutableSet<String> = LinkedHashSet(),
        guard: Set<String> = emptySet()
    ): Set<String> {
        val node = index.byId[nodeId] ?: return out
        if (guard.contains(nodeId)) return out
        val next = LinkedHashSet(guard).apply { add(nodeId) }
        if (node.kind == NodeKind.Variable) {
            if (node.token != "e") out.add(node.token)
        }
        val slots = index.children[nodeId] ?: return out
        // If this is a summation/product with an index relation `n = 1`, exclude the bound index variable
        var boundIndexVar: String? = null
        if (node.kind == NodeKind.Container && (node.variant == "sum" || node.variant == "prod")) {
            val lower = Engine.childOf(index, nodeId, PortId.Lower)
            if (lower != null && lower.kind == NodeKind.Relation && lower.variant == "equal") {
                val lhs = Engine.childOf(index, lower.id, PortId.Left)
                if (lhs?.kind == NodeKind.Variable) boundIndexVar = lhs.token
            }
        }
        for ((port, edge) in slots) {
            if (boundIndexVar != null && port == PortId.Lower) continue
            val subVars = collectVariables(index, edge.target, LinkedHashSet(), next)
            for (v in subVars) {
                if (v != boundIndexVar) out.add(v)
            }
        }
        return out
    }

    fun analyzeCluster(
        graph: AuraGraph,
        rootId: String,
        index: GraphIndex = Engine.indexGraph(graph)
    ): ClusterAnalysis? {
        val root = index.byId[rootId] ?: return null
        val variables = collectVariables(index, rootId).sorted()
        val latex = Engine.toLatex(graph, rootId, index)
        var value: Double? = null
        var plottable2D: PlotTarget2D? = null
        var plottable3D: PlotTarget3D? = null
        val isRelation = root.kind == NodeKind.Relation

        val preferredX = listOf("x", "t", "\\theta", "n", "a", "r")
        val preferredY = listOf("y", "u", "\\varphi", "b", "c")

        fun pickPrimaryVars(vars: List<String>): Pair<String, List<String>> {
            val primary = preferredX.firstOrNull { it in vars } ?: vars.first()
            return primary to vars.filter { it != primary }
        }

        if (isRelation && root.variant == "equal") {
            val left = Engine.childOf(index, rootId, PortId.Left)
            val right = Engine.childOf(index, rootId, PortId.Right)
            if (left != null && right != null) {
                val leftVars = collectVariables(index, left.id).toList()
                val rightVars = collectVariables(index, right.id).toList()
                val leftLone = left.kind == NodeKind.Variable && left.token !in rightVars
                val rightLone = right.kind == NodeKind.Variable && right.token !in leftVars

                if (leftLone && rightVars.isNotEmpty()) {
                    val (indep, params) = pickPrimaryVars(rightVars)
                    plottable2D = PlotTarget2D(right.id, Engine.toLatex(graph, right.id, index), indep, params)
                    if (rightVars.size >= 2) {
                        val xV = preferredX.firstOrNull { it in rightVars } ?: rightVars[0]
                        val rem = rightVars.filter { it != xV }
                        val yV = preferredY.firstOrNull { it in rem } ?: rem.first()
                        plottable3D = PlotTarget3D(right.id, Engine.toLatex(graph, right.id, index), xV, yV, rem.filter { it != yV })
                    }
                } else if (rightLone && leftVars.isNotEmpty()) {
                    val (indep, params) = pickPrimaryVars(leftVars)
                    plottable2D = PlotTarget2D(left.id, Engine.toLatex(graph, left.id, index), indep, params)
                    if (leftVars.size >= 2) {
                        val xV = preferredX.firstOrNull { it in leftVars } ?: leftVars[0]
                        val rem = leftVars.filter { it != xV }
                        val yV = preferredY.firstOrNull { it in rem } ?: rem.first()
                        plottable3D = PlotTarget3D(left.id, Engine.toLatex(graph, left.id, index), xV, yV, rem.filter { it != yV })
                    }
                } else if (rightVars.size == 1 && leftVars.isEmpty()) {
                    plottable2D = PlotTarget2D(right.id, Engine.toLatex(graph, right.id, index), rightVars[0])
                } else if (leftVars.size == 1 && rightVars.isEmpty()) {
                    plottable2D = PlotTarget2D(left.id, Engine.toLatex(graph, left.id, index), leftVars[0])
                }
            }
        } else if (variables.isNotEmpty()) {
            val (indep, params) = pickPrimaryVars(variables)
            plottable2D = PlotTarget2D(rootId, latex, indep, params)
            if (variables.size >= 2) {
                val xV = preferredX.firstOrNull { it in variables } ?: variables[0]
                val rem = variables.filter { it != xV }
                val yV = preferredY.firstOrNull { it in rem } ?: rem.first()
                plottable3D = PlotTarget3D(rootId, latex, xV, yV, rem.filter { it != yV })
            }
        }

        if (variables.isEmpty()) {
            value = evalExpression(index, rootId, emptyMap())
        }

        return ClusterAnalysis(
            rootId = rootId,
            nodeIds = Engine.subtreeIds(index, rootId).toList(),
            latex = latex,
            variables = variables,
            value = value,
            plottable = plottable2D,
            plottable3D = plottable3D,
            isRelation = isRelation
        )
    }

    fun analyzeGraph(graph: AuraGraph): List<ClusterAnalysis> {
        val index = Engine.indexGraph(graph)
        return graph.nodes
            .filter { !index.parent.containsKey(it.id) }
            .mapNotNull { analyzeCluster(graph, it.id, index) }
    }

    fun plotSamples(
        graph: AuraGraph,
        exprRootId: String,
        min: Float,
        max: Float,
        count: Int = 240,
        variable: String = "x",
        extraEnv: Map<String, Double> = emptyMap()
    ): List<SamplePoint2D> {
        val index = Engine.indexGraph(graph)
        val points = ArrayList<SamplePoint2D>(count + 1)
        for (step in 0..count) {
            val x = min + ((max - min) * step) / count.toFloat()
            val env = LinkedHashMap(extraEnv).apply { put(variable, x.toDouble()) }
            val y = evalExpression(index, exprRootId, env)
            if (y != null && y.isFinite()) {
                points.add(SamplePoint2D(x, y.toFloat()))
            }
        }
        return points
    }

    fun plotSurface3D(
        graph: AuraGraph,
        exprRootId: String,
        min: Float = -4f,
        max: Float = 4f,
        gridSize: Int = 20,
        xVar: String = "x",
        yVar: String = "y",
        extraEnv: Map<String, Double> = emptyMap()
    ): SurfaceMesh3D {
        val index = Engine.indexGraph(graph)
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        val rows = ArrayList<List<SurfacePoint3D?>>(gridSize + 1)
        for (i in 0..gridSize) {
            val x = min + ((max - min) * i) / gridSize.toFloat()
            val row = ArrayList<SurfacePoint3D?>(gridSize + 1)
            for (j in 0..gridSize) {
                val y = min + ((max - min) * j) / gridSize.toFloat()
                val env = LinkedHashMap(extraEnv).apply {
                    put(xVar, x.toDouble())
                    put(yVar, y.toDouble())
                }
                val z = evalExpression(index, exprRootId, env)
                if (z != null && z.isFinite() && abs(z) < 1e4) {
                    val zf = z.toFloat()
                    minZ = min(minZ, zf)
                    maxZ = max(maxZ, zf)
                    row.add(SurfacePoint3D(x, y, zf))
                } else {
                    row.add(null)
                }
            }
            rows.add(row)
        }
        if (!minZ.isFinite() || !maxZ.isFinite()) {
            minZ = -1f
            maxZ = 1f
        } else if (abs(maxZ - minZ) < 1e-4f) {
            minZ -= 1f
            maxZ += 1f
        }
        return SurfaceMesh3D(gridSize = gridSize, points = rows, minZ = minZ, maxZ = maxZ)
    }

    fun sampleRange(points: List<SamplePoint2D>): Pair<Float, Float> {
        if (points.isEmpty()) return -1f to 1f
        val sorted = points.map { it.y }.sorted()
        val low = sorted[(sorted.size * 0.02).toInt().coerceIn(0, sorted.lastIndex)]
        val high = sorted[(sorted.size * 0.98).toInt().coerceIn(0, sorted.lastIndex)]
        var minV = min(low, high)
        var maxV = max(low, high)
        if (abs(maxV - minV) < 1e-5f) {
            minV -= 1f
            maxV += 1f
        }
        val pad = (maxV - minV) * 0.12f
        return (minV - pad) to (maxV + pad)
    }

    /* ------------------------------------------------------------------ */
    /* Constant folding (the "simplify" graph mutation)                   */
    /* ------------------------------------------------------------------ */

    fun foldConstants(graph: AuraGraph): FoldResult {
        val index = Engine.indexGraph(graph)
        val values = LinkedHashMap<String, Double?>()
        for (node in graph.nodes) {
            values[node.id] = evalExpression(index, node.id, emptyMap())
        }
        fun isSignAmbiguous(id: String): Boolean {
            val node = index.byId[id] ?: return false
            if (node.variant == "pm" || node.variant == "mp") return true
            return false
        }

        val chosen = ArrayList<String>()
        for (node in graph.nodes) {
            if (node.kind in listOf(NodeKind.Literal, NodeKind.Relation, NodeKind.Set, NodeKind.Group)) continue
            val value = values[node.id] ?: continue
            val subtree = Engine.subtreeIds(index, node.id)
            if (subtree.size < 2) continue
            // `\pm 3` denotes two values; folding it to 3 would silently drop a root.
            if (subtree.any { isSignAmbiguous(it) }) continue
            var ancestor = index.parent[node.id]
            var shadowed = false
            while (ancestor != null) {
                if (values[ancestor.source] != null) {
                    shadowed = true
                    break
                }
                ancestor = index.parent[ancestor.source]
            }
            if (!shadowed) chosen.add(node.id)
        }
        if (chosen.isEmpty()) return FoldResult(graph, 0, emptyList())

        val remove = LinkedHashSet<String>()
        val addedNodes = ArrayList<AuraNode>()
        val addedEdges = ArrayList<AuraEdge>()
        val summaries = ArrayList<String>()

        for (id in chosen) {
            val node = index.byId[id] ?: continue
            val value = values[id] ?: continue
            val token = formatNumber(value)
            summaries.add("${Engine.toLatex(graph, id, index)} \\to $token")
            val literal = makeNode(
                kind = NodeKind.Literal,
                type = MathType.Scalar,
                token = token,
                label = "Literal $token",
                x = node.x,
                y = node.y
            )
            addedNodes.add(literal)
            val parentEdge = index.parent[id]
            if (parentEdge != null) {
                addedEdges.add(
                    AuraEdge(
                        id = newId(),
                        source = parentEdge.source,
                        target = literal.id,
                        port = parentEdge.port,
                        role = parentEdge.role
                    )
                )
            }
            remove.addAll(Engine.subtreeIds(index, id))
        }

        // Keep shared leaves that are still referenced outside `remove`
        val survivingEdges = graph.edges.filter { it.source !in remove && it.target !in remove } + addedEdges
        val referencedTargets = survivingEdges.map { it.target }.toSet()
        val survivingNodes = graph.nodes.filter { it.id !in remove || it.id in referencedTargets } + addedNodes

        return FoldResult(
            graph = AuraGraph(survivingNodes, survivingEdges),
            folded = addedNodes.size,
            summaries = summaries
        )
    }

    /* ------------------------------------------------------------------ */
    /* Variable Scope Halos                                               */
    /* ------------------------------------------------------------------ */

    fun scopeIndex(graph: AuraGraph): List<ScopeEntry> {
        val index = Engine.indexGraph(graph)
        val byToken = LinkedHashMap<String, MutableList<String>>()
        for (node in graph.nodes) {
            if (node.kind != NodeKind.Variable || node.collapsed) continue
            byToken.getOrPut(node.token) { ArrayList() }.add(node.id)
        }
        return byToken.entries
            .sortedBy { it.key }
            .mapIndexed { offset, (token, nodeIds) ->
                ScopeEntry(
                    token = token,
                    colorHex = SCOPE_COLORS[offset % SCOPE_COLORS.size],
                    nodeIds = nodeIds,
                    clusterIds = nodeIds.map { Engine.componentRoot(index, it)?.id ?: it }.distinct()
                )
            }
    }

    fun scopeByNode(graph: AuraGraph): Map<String, Pair<Long, String>> {
        val out = LinkedHashMap<String, Pair<Long, String>>()
        for (entry in scopeIndex(graph)) {
            for (nodeId in entry.nodeIds) {
                out[nodeId] = entry.colorHex to entry.token
            }
        }
        return out
    }
}
