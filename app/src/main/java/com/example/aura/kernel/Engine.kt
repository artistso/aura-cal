package com.example.aura.kernel

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Aura graph kernel: indexing, type propagation, diagnostics, LaTeX serialisation,
 * deterministic expression layout, homeomorphic connection candidates, and collapse/expand.
 */

data class GraphIndex(
    val byId: Map<String, AuraNode>,
    val children: Map<String, Map<PortId, AuraEdge>>,
    val parent: Map<String, AuraEdge>,
    val edgeById: Map<String, AuraEdge>
)

data class ComponentCluster(
    val rootId: String,
    val nodeIds: List<String>
)

data class SerializedExpression(
    val rootId: String,
    val latex: String,
    val nodeIds: List<String>
)

data class GraphBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
)

object Engine {
    private const val GAP = 14f
    private const val TIGHT = 8f

    fun indexGraph(graph: AuraGraph): GraphIndex {
        val byId = graph.nodes.associateBy { it.id }
        val children = LinkedHashMap<String, MutableMap<PortId, AuraEdge>>()
        val parent = LinkedHashMap<String, AuraEdge>()
        val edgeById = LinkedHashMap<String, AuraEdge>()

        for (edge in graph.edges) {
            edgeById[edge.id] = edge
            if (!byId.containsKey(edge.source) || !byId.containsKey(edge.target)) continue
            val slots = children.getOrPut(edge.source) { LinkedHashMap() }
            slots[edge.port] = edge
            parent[edge.target] = edge
        }
        return GraphIndex(byId, children, parent, edgeById)
    }

    fun childOf(index: GraphIndex, nodeId: String, port: PortId): AuraNode? {
        val edge = index.children[nodeId]?.get(port) ?: return null
        return index.byId[edge.target]
    }

    fun openPorts(index: GraphIndex, node: AuraNode): List<PortDef> {
        val occupied = index.children[node.id]
        return portsFor(node).filter { occupied?.containsKey(it.id) != true }
    }

    fun subtreeIds(
        index: GraphIndex,
        nodeId: String,
        visiting: MutableSet<String> = LinkedHashSet()
    ): Set<String> {
        val out = LinkedHashSet<String>()
        if (!visiting.add(nodeId)) return out
        out.add(nodeId)
        val slots = index.children[nodeId]
        if (slots != null) {
            for (edge in slots.values) {
                out.addAll(subtreeIds(index, edge.target, visiting))
            }
        }
        return out
    }

    fun componentIds(graph: AuraGraph, startId: String): Set<String> {
        val seen = LinkedHashSet<String>()
        seen.add(startId)
        val queue = ArrayDeque<String>()
        queue.add(startId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (edge in graph.edges) {
                val other = when {
                    edge.source == current -> edge.target
                    edge.target == current -> edge.source
                    else -> null
                }
                if (other != null && seen.add(other)) {
                    queue.add(other)
                }
            }
        }
        return seen
    }

    fun componentRoot(index: GraphIndex, nodeId: String): AuraNode? {
        var current = index.byId[nodeId]
        val guard = LinkedHashSet<String>()
        while (current != null) {
            if (!guard.add(current.id)) return current
            val incoming = index.parent[current.id] ?: return current
            val next = index.byId[incoming.source] ?: return current
            current = next
        }
        return null
    }

    fun rootsOf(graph: AuraGraph): List<AuraNode> {
        val index = indexGraph(graph)
        return graph.nodes.filter { !index.parent.containsKey(it.id) }
    }

    fun componentsOf(graph: AuraGraph): List<ComponentCluster> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<ComponentCluster>()
        val index = indexGraph(graph)
        for (node in graph.nodes) {
            if (seen.contains(node.id)) continue
            val ids = componentIds(graph, node.id)
            seen.addAll(ids)
            // Find the non-parented root inside this connected component
            val root = ids.mapNotNull { index.byId[it] }
                .firstOrNull { !index.parent.containsKey(it.id) }
                ?: componentRoot(index, node.id)
                ?: node
            out.add(ComponentCluster(root.id, ids.toList()))
        }
        return out.sortedBy { index.byId[it.rootId]?.x ?: 0f }
    }

    /* ------------------------------------------------------------------ */
    /* LaTeX serialisation                                                */
    /* ------------------------------------------------------------------ */

    private fun precedenceOf(node: AuraNode): Int {
        if (node.kind == NodeKind.Relation) return 1
        if (node.kind == NodeKind.Operator) {
            val v = node.variant
            if (v != null && (v in Ontology.POSTFIX_VARIANTS || v in Ontology.PREFIX_VARIANTS)) return 6
            if (v == "binom" || v == "frac") return 6
            if (v == "add" || v == "sub") return 2
            if (v == "mul" || v == "juxta") return 3
            if (v == "power") return 5
            return 3
        }
        if (node.kind == NodeKind.Container && (node.variant == "int" || node.variant == "sum")) return 2
        return 6
    }

    private val OP_LATEX = mapOf(
        "add" to "+",
        "sub" to "-",
        "mul" to "\\times",
        "juxta" to "\\,"
    )

    fun toLatex(graph: AuraGraph, rootId: String, index: GraphIndex = indexGraph(graph)): String {
        return renderLatex(rootId, index, LinkedHashSet(), 0)
    }

    private fun renderLatex(
        nodeId: String,
        index: GraphIndex,
        visiting: Set<String>,
        parentPrecedence: Int
    ): String {
        val node = index.byId[nodeId] ?: return "\\square"
        if (visiting.contains(nodeId)) return "\\square"
        if (node.kind == NodeKind.Group) {
            val inner = node.collapsedLatex ?: node.token
            return "\\left($inner\\right)"
        }
        val next = LinkedHashSet(visiting).apply { add(nodeId) }

        fun slot(port: PortId, prec: Int = 0, fallback: String = "\\square"): String {
            val child = childOf(index, nodeId, port)
            return if (child != null) renderLatex(child.id, index, next, prec) else fallback
        }

        fun suffix(base: String): String {
            val exp = childOf(index, nodeId, PortId.Exponent)
            val sub = childOf(index, nodeId, PortId.Subscript)
            var tex = base
            if (sub != null) tex += "_{${renderLatex(sub.id, index, next, 0)}}"
            if (exp != null) tex += "^{${renderLatex(exp.id, index, next, 0)}}"
            return tex
        }

        var prec = 6
        var tex = when (node.kind) {
            NodeKind.Operator -> {
                when (node.variant) {
                    "power" -> {
                        prec = 5
                        "{${slot(PortId.Base, 6)}}^{${slot(PortId.Exponent, 6)}}"
                    }
                    "frac" -> {
                        prec = 6
                        "\\frac{${slot(PortId.Numerator)}}{${slot(PortId.Denominator)}}"
                    }
                    else -> {
                        prec = precedenceOf(node)
                        val left = slot(PortId.Left, prec)
                        val right = slot(PortId.Right, prec + 1)
                        if (node.variant == "juxta") "$left\\,$right"
                        else "$left ${OP_LATEX[node.variant ?: "add"] ?: node.token} $right"
                    }
                }
            }
            NodeKind.Relation -> {
                prec = 1
                val left = slot(PortId.Left, 2)
                val right = slot(PortId.Right, 2)
                "$left ${node.token} $right"
            }
            NodeKind.Function -> {
                prec = 6
                val arg = childOf(index, nodeId, PortId.Arg)
                val name = node.token
                if (arg != null) "$name\\left(${renderLatex(arg.id, index, next, 0)}\\right)"
                else "$name\\left(\\square\\right)"
            }
            NodeKind.Container -> {
                when (node.variant) {
                    "sqrt" -> "\\sqrt{${slot(PortId.Body)}}"
                    "int", "sum" -> {
                        prec = 2
                        val lower = childOf(index, nodeId, PortId.Lower)
                        val upper = childOf(index, nodeId, PortId.Upper)
                        val limits = buildString {
                            if (lower != null) append("_{${renderLatex(lower.id, index, next, 0)}}")
                            if (upper != null) append("^{${renderLatex(upper.id, index, next, 0)}}")
                        }
                        val sign = if (node.variant == "int") "\\int" else "\\sum"
                        "$sign$limits ${slot(PortId.Body, 2)}"
                    }
                    else -> "\\left(${slot(PortId.Body)}\\right)"
                }
            }
            else -> suffix(node.token.ifEmpty { "\\square" })
        }

        val variant = node.variant ?: ""
        if (node.kind == NodeKind.Operator && variant in Ontology.POSTFIX_VARIANTS) {
            val arg = childOf(index, nodeId, PortId.Arg)
            val inner = if (arg != null) renderLatex(arg.id, index, next, 6) else "\\square"
            val wrapped = if (arg != null && precedenceOf(arg) < 6) "\\left($inner\\right)" else inner
            tex = when (variant) {
                "transpose" -> "{${wrapped}}^{\\top}"
                "inverse" -> "{${wrapped}}^{-1}"
                "conj" -> "\\overline{${wrapped}}"
                "prime" -> "{${wrapped}}^{\\prime}"
                "doubleprime" -> "{${wrapped}}^{\\prime\\prime}"
                "degree" -> "{${wrapped}}^{\\circ}"
                "complement" -> "{${wrapped}}^{c}"
                else -> "${wrapped}!"
            }
            prec = 6
        } else if (node.kind == NodeKind.Operator && variant in Ontology.PREFIX_VARIANTS) {
            val arg = childOf(index, nodeId, PortId.Arg)
            val inner = if (arg != null) renderLatex(arg.id, index, next, 6) else "\\square"
            val marker = mapOf(
                "neg" to "\\neg",
                "forall" to "\\forall",
                "exists" to "\\exists",
                "nabla" to "\\nabla",
                "partial" to "\\partial"
            )[variant] ?: node.token
            tex = "$marker $inner"
            prec = 6
        } else if (node.kind == NodeKind.Operator && variant == "binom") {
            tex = "\\binom{${slot(PortId.Numerator)}}{${slot(PortId.Denominator)}}"
            prec = 6
        } else if (node.kind == NodeKind.Operator && (variant == "floor" || variant == "ceil")) {
            val arg = childOf(index, nodeId, PortId.Arg)
            val inner = if (arg != null) renderLatex(arg.id, index, next, 0) else "\\square"
            tex = if (variant == "floor") "\\lfloor $inner \\rfloor" else "\\lceil $inner \\rceil"
            prec = 6
        } else if (node.kind == NodeKind.Operator && (variant == "abs" || variant == "norm")) {
            val arg = childOf(index, nodeId, PortId.Arg)
            val inner = if (arg != null) renderLatex(arg.id, index, next, 0) else "\\square"
            tex = if (variant == "abs") "\\left|$inner\\right|" else "\\left\\|$inner\\right\\|"
            prec = 6
        } else if (node.kind == NodeKind.Operator && (variant == "pm" || variant == "mp")) {
            tex = "${slot(PortId.Left, 2)} ${node.token} ${slot(PortId.Right, 3)}"
            prec = 2
        } else if (node.kind == NodeKind.Container && variant in Ontology.ACCENT_VARIANTS) {
            tex = "\\$variant{${slot(PortId.Body)}}"
            prec = 6
        } else if (node.kind == NodeKind.Container && variant in Ontology.MATRIX_VARIANTS) {
            tex = "\\begin{$variant}{${slot(PortId.Body)}}\\end{$variant}"
            prec = 6
        } else if (node.kind == NodeKind.Container && variant in Ontology.ENCLOSURE_VARIANTS) {
            val inner = slot(PortId.Body)
            tex = when (variant) {
                "abs" -> "\\left|$inner\\right|"
                "norm" -> "\\left\\|$inner\\right\\|"
                "floor" -> "\\lfloor $inner \\rfloor"
                else -> "\\lceil $inner \\rceil"
            }
            prec = 6
        } else if (node.kind == NodeKind.Container && variant == "root") {
            val indexNode = childOf(index, nodeId, PortId.Upper)
            tex = if (indexNode != null) {
                "\\sqrt[${renderLatex(indexNode.id, index, next, 0)}]{${slot(PortId.Body)}}"
            } else {
                "\\sqrt{${slot(PortId.Body)}}"
            }
            prec = 6
        } else if (node.kind == NodeKind.Container && variant in Ontology.BIG_VARIANTS) {
            val lower = childOf(index, nodeId, PortId.Lower)
            val upper = childOf(index, nodeId, PortId.Upper)
            val signMap = mapOf(
                "sum" to "\\sum", "prod" to "\\prod", "coprod" to "\\coprod",
                "int" to "\\int", "iint" to "\\iint", "iiint" to "\\iiint", "oint" to "\\oint",
                "lim" to "\\lim", "limsup" to "\\limsup", "liminf" to "\\liminf",
                "sup" to "\\sup", "inf" to "\\inf", "max" to "\\max", "min" to "\\min",
                "bigcup" to "\\bigcup", "bigcap" to "\\bigcap", "bigoplus" to "\\bigoplus",
                "bigotimes" to "\\bigotimes", "det" to "\\det", "gcd" to "\\gcd",
                "argmin" to "\\arg\\min", "argmax" to "\\arg\\max", "span" to "\\operatorname{span}"
            )
            val sign = signMap[variant] ?: node.token
            val limits = buildString {
                if (lower != null) append("_{${renderLatex(lower.id, index, next, 0)}}")
                if (upper != null) append("^{${renderLatex(upper.id, index, next, 0)}}")
            }
            val body = childOf(index, nodeId, PortId.Body)
            tex = "$sign$limits${if (body != null) " ${renderLatex(body.id, index, next, 2)}" else ""}"
            prec = 2
        }

        return if (prec < parentPrecedence) "\\left($tex\\right)" else tex
    }

    fun documentLatex(graph: AuraGraph): List<SerializedExpression> {
        val index = indexGraph(graph)
        return componentsOf(graph).map { comp ->
            SerializedExpression(
                rootId = comp.rootId,
                latex = toLatex(graph, comp.rootId, index),
                nodeIds = comp.nodeIds
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /* Type propagation & diagnostics                                     */
    /* ------------------------------------------------------------------ */

    fun effectiveType(
        index: GraphIndex,
        nodeId: String,
        cache: MutableMap<String, MathType> = LinkedHashMap()
    ): MathType {
        cache[nodeId]?.let { return it }
        val node = index.byId[nodeId] ?: return MathType.Unknown
        cache[nodeId] = node.type
        val computed = computeType(index, node, cache)
        cache[nodeId] = computed
        return computed
    }

    private fun childType(
        index: GraphIndex,
        nodeId: String,
        port: PortId,
        cache: MutableMap<String, MathType>
    ): MathType? {
        val child = childOf(index, nodeId, port) ?: return null
        return effectiveType(index, child.id, cache)
    }

    private fun computeType(
        index: GraphIndex,
        node: AuraNode,
        cache: MutableMap<String, MathType>
    ): MathType {
        return when (node.kind) {
            NodeKind.Literal, NodeKind.Variable, NodeKind.Set ->
                if (node.type != MathType.Unknown) node.type else MathType.Variable
            NodeKind.Group -> MathType.Expression
            NodeKind.Relation -> MathType.Relation
            NodeKind.Function -> {
                val arg = childType(index, node.id, PortId.Arg, cache)
                if (arg != null) MathType.Expression else MathType.Function
            }
            NodeKind.Container -> MathType.Expression
            NodeKind.Operator -> {
                if (node.variant == "power") {
                    val base = childType(index, node.id, PortId.Base, cache)
                    val exp = childType(index, node.id, PortId.Exponent, cache)
                    if (base == null || exp == null) return MathType.Expression
                    if (base == MathType.Matrix || exp == MathType.Matrix) return MathType.Matrix
                    if (base == MathType.Set || exp == MathType.Set) return MathType.Set
                    if (base == MathType.Expression || exp == MathType.Expression || base == MathType.Vector) {
                        return MathType.Expression
                    }
                    return MathType.Scalar
                }
                if (node.variant == "frac") {
                    val num = childType(index, node.id, PortId.Numerator, cache)
                    val den = childType(index, node.id, PortId.Denominator, cache)
                    if (num == MathType.Matrix || den == MathType.Matrix) return MathType.Expression
                    if (num == MathType.Set || den == MathType.Set) return MathType.Set
                    if (num != null && den != null && num != MathType.Expression && den != MathType.Expression) {
                        return MathType.Scalar
                    }
                    return MathType.Expression
                }
                val parts = listOf(PortId.Left, PortId.Right).map { childType(index, node.id, it, cache) }
                when {
                    parts.any { it == MathType.Matrix } -> MathType.Matrix
                    parts.any { it == MathType.Vector } -> MathType.Vector
                    parts.any { it == MathType.Set } -> MathType.Set
                    parts.any { it == MathType.Relation } -> MathType.Relation
                    parts.all { it == MathType.Scalar || it == MathType.Variable } -> MathType.Scalar
                    else -> MathType.Expression
                }
            }
        }
    }

    fun diagnose(graph: AuraGraph): List<Diagnostic> {
        val index = indexGraph(graph)
        val out = ArrayList<Diagnostic>()
        val cache = LinkedHashMap<String, MathType>()

        for (node in graph.nodes) {
            val incoming = graph.edges.filter { it.target == node.id }
            if (incoming.size > 1 && !isShareable(node)) {
                out.add(
                    Diagnostic(
                        nodeId = node.id,
                        edgeId = incoming.getOrNull(1)?.id,
                        severity = DiagnosticSeverity.Error,
                        message = "${node.label} is bound in ${incoming.size} places — a vertex can only serve one port."
                    )
                )
            }
        }

        // Cycle detection
        val state = LinkedHashMap<String, Int>()
        fun visit(id: String): Boolean {
            val status = state[id] ?: 0
            if (status == 1) return true
            if (status == 2) return false
            state[id] = 1
            val slots = index.children[id]
            if (slots != null) {
                for (edge in slots.values) {
                    if (visit(edge.target)) {
                        state[id] = 2
                        return true
                    }
                }
            }
            state[id] = 2
            return false
        }

        for (node in graph.nodes) {
            if (visit(node.id)) {
                out.add(
                    Diagnostic(
                        nodeId = node.id,
                        severity = DiagnosticSeverity.Error,
                        message = "Cyclic binding detected — the expression cannot be evaluated."
                    )
                )
            }
        }

        for (node in graph.nodes) {
            val portDefs = portsFor(node)
            val slots = index.children[node.id]
            for (port in portDefs) {
                if (port.required && slots?.containsKey(port.id) != true) {
                    out.add(
                        Diagnostic(
                            nodeId = node.id,
                            severity = DiagnosticSeverity.Warning,
                            message = "${node.label}: ${port.label.lowercase()} is still open."
                        )
                    )
                }
            }
            for ((portId, edge) in slots ?: emptyMap()) {
                val def = findPort(node, portId) ?: continue
                val child = index.byId[edge.target] ?: continue
                val cType = effectiveType(index, child.id, cache)
                if (!admits(def.accepts, cType).ok) {
                    out.add(
                        Diagnostic(
                            nodeId = node.id,
                            edgeId = edge.id,
                            severity = DiagnosticSeverity.Error,
                            message = "${child.label} has type ${cType.label}, but ${portId.wireName} accepts ${def.accepts.joinToString(" | ") { it.label }}."
                        )
                    )
                }
                if (cType == MathType.Relation) {
                    out.add(
                        Diagnostic(
                            nodeId = node.id,
                            edgeId = edge.id,
                            severity = DiagnosticSeverity.Error,
                            message = "A relation cannot be nested directly inside another expression — wrap it in a group."
                        )
                    )
                }
            }
            if (node.kind == NodeKind.Operator && node.variant in listOf("add", "sub", "mul", "frac")) {
                val types = if (node.variant == "frac") {
                    listOfNotNull(
                        childType(index, node.id, PortId.Numerator, cache),
                        childType(index, node.id, PortId.Denominator, cache)
                    )
                } else {
                    listOfNotNull(
                        childType(index, node.id, PortId.Left, cache),
                        childType(index, node.id, PortId.Right, cache)
                    )
                }
                if (types.any { it == MathType.Set }) {
                    out.add(
                        Diagnostic(
                            nodeId = node.id,
                            severity = DiagnosticSeverity.Error,
                            message = "Set arithmetic is not defined for this operator. Use set operations instead."
                        )
                    )
                } else if (MathType.Matrix in types && types.any { it == MathType.Scalar || it == MathType.Variable }) {
                    out.add(
                        Diagnostic(
                            nodeId = node.id,
                            severity = DiagnosticSeverity.Error,
                            message = "Scalar and Matrix cannot be combined element-wise here — the operation is ill-typed."
                        )
                    )
                }
            }
            if (node.kind == NodeKind.Operator && node.variant == "power") {
                val exponent = childType(index, node.id, PortId.Exponent, cache)
                if (exponent == MathType.Set || exponent == MathType.Relation) {
                    out.add(
                        Diagnostic(
                            nodeId = node.id,
                            severity = DiagnosticSeverity.Error,
                            message = "Exponent expects a Scalar, received ${exponent.label}."
                        )
                    )
                }
            }
            if (node.kind == NodeKind.Relation) {
                val left = childType(index, node.id, PortId.Left, cache)
                val right = childType(index, node.id, PortId.Right, cache)
                if (node.variant == "in" || node.variant == "subset") {
                    if (right != null && right != MathType.Set && right != MathType.Expression) {
                        out.add(
                            Diagnostic(
                                nodeId = node.id,
                                severity = DiagnosticSeverity.Error,
                                message = "Membership needs a Set on the right, received ${right.label}."
                            )
                        )
                    }
                } else if ((left == MathType.Set && right != null && right != MathType.Set) ||
                    (right == MathType.Set && left != null && left != MathType.Set)
                ) {
                    out.add(
                        Diagnostic(
                            nodeId = node.id,
                            severity = DiagnosticSeverity.Warning,
                            message = "Comparing a Set with a scalar expression — check the intended relation."
                        )
                    )
                }
            }
        }
        return out
    }

    /* ------------------------------------------------------------------ */
    /* Layout: expression tiles arranged from graph structure              */
    /* ------------------------------------------------------------------ */

    private data class Metrics(
        val width: Float,
        val ownX: Float,
        val positions: Map<String, PointF>
    )

    private val EMPTY_METRICS = Metrics(0f, 0f, emptyMap())

    private fun measure(nodeId: String, index: GraphIndex, seen: Set<String>): Metrics {
        val node = index.byId[nodeId] ?: return EMPTY_METRICS
        if (seen.contains(nodeId)) return EMPTY_METRICS
        val width = estimateWidth(node)
        val nextSeen = LinkedHashSet(seen).apply { add(nodeId) }

        fun sub(port: PortId): Metrics {
            val edge = index.children[nodeId]?.get(port) ?: return EMPTY_METRICS
            return measure(edge.target, index, nextSeen)
        }

        val positions = LinkedHashMap<String, PointF>()
        fun put(m: Metrics, dx: Float, dy: Float) {
            for ((id, pt) in m.positions) {
                positions[id] = PointF(pt.x + dx, pt.y + dy)
            }
        }
        fun own(x: Float, y: Float = 0f) {
            positions[nodeId] = PointF(x, y)
        }

        val v = node.variant ?: ""
        if (node.kind == NodeKind.Operator && v in Ontology.POSTFIX_VARIANTS) {
            val arg = sub(PortId.Arg)
            own(arg.width + 10f, 0f)
            put(arg, 0f, 0f)
            return Metrics(arg.width + 18f, arg.width + 10f, positions)
        }
        if (node.kind == NodeKind.Operator && v in Ontology.PREFIX_VARIANTS) {
            val arg = sub(PortId.Arg)
            own(6f, 0f)
            put(arg, 16f, 0f)
            return Metrics(16f + arg.width, 6f, positions)
        }
        if (node.kind == NodeKind.Operator && v == "binom") {
            val upper = sub(PortId.Numerator)
            val lower = sub(PortId.Denominator)
            val total = max(max(upper.width, lower.width), 40f)
            own(total / 2f, 0f)
            put(upper, (total - upper.width) / 2f, -28f)
            put(lower, (total - lower.width) / 2f, 28f)
            return Metrics(total, total / 2f, positions)
        }
        if (node.kind == NodeKind.Container && v in Ontology.ACCENT_VARIANTS) {
            val body = sub(PortId.Body)
            own(14f, 0f)
            put(body, 22f, 0f)
            return Metrics(body.width + 22f, 14f, positions)
        }
        if (node.kind == NodeKind.Container && (v in Ontology.MATRIX_VARIANTS || v in Ontology.ENCLOSURE_VARIANTS)) {
            val body = sub(PortId.Body)
            own(width / 2f, 0f)
            put(body, width + TIGHT, 0f)
            return Metrics(width + TIGHT + body.width, width / 2f, positions)
        }

        return when (node.kind) {
            NodeKind.Operator -> {
                if (v == "frac") {
                    val num = sub(PortId.Numerator)
                    val den = sub(PortId.Denominator)
                    val total = max(max(num.width, den.width), 52f)
                    own(total / 2f, 0f)
                    put(num, (total - num.width) / 2f, -44f)
                    put(den, (total - den.width) / 2f, 44f)
                    Metrics(total, total / 2f, positions)
                } else if (v == "power") {
                    val base = sub(PortId.Base)
                    val exp = sub(PortId.Exponent)
                    val expScale = 0.78f
                    val expWidth = max(exp.width, 20f) * expScale
                    own(base.width + 8f, -12f)
                    put(base, 0f, 0f)
                    put(exp, base.width + 12f, -36f)
                    Metrics(base.width + 12f + expWidth, base.width + 8f, positions)
                } else {
                    val left = sub(PortId.Left)
                    val right = sub(PortId.Right)
                    val total = left.width + GAP + width + GAP + right.width
                    own(left.width + GAP + width / 2f, 0f)
                    put(left, 0f, 0f)
                    put(right, left.width + GAP + width + GAP, 0f)
                    Metrics(total, left.width + GAP + width / 2f, positions)
                }
            }
            NodeKind.Relation -> {
                val left = sub(PortId.Left)
                val right = sub(PortId.Right)
                val total = left.width + GAP + width + GAP + right.width
                own(left.width + GAP + width / 2f, 0f)
                put(left, 0f, 0f)
                put(right, left.width + GAP + width + GAP, 0f)
                Metrics(total, left.width + GAP + width / 2f, positions)
            }
            NodeKind.Function -> {
                val arg = sub(PortId.Arg)
                own(width / 2f, 0f)
                put(arg, width + TIGHT, 0f)
                Metrics(width + TIGHT + arg.width, width / 2f, positions)
            }
            NodeKind.Container -> {
                if (v in Ontology.BIG_VARIANTS) {
                    val lower = sub(PortId.Lower)
                    val upper = sub(PortId.Upper)
                    val body = sub(PortId.Body)
                    val limits = max(max(lower.width, upper.width), 22f)
                    own(width / 2f, 0f)
                    put(upper, width + 2f, -38f)
                    put(lower, width + 2f, 34f)
                    put(body, width + limits - 12f + GAP, 0f)
                    Metrics(
                        width = max(width + limits + GAP + body.width - 12f, width + limits),
                        ownX = width / 2f,
                        positions = positions
                    )
                } else {
                    val body = sub(PortId.Body)
                    own(width / 2f, 0f)
                    put(body, width + TIGHT, 0f)
                    Metrics(width + TIGHT + body.width, width / 2f, positions)
                }
            }
            else -> {
                val exp = sub(PortId.Exponent)
                val ssub = sub(PortId.Subscript)
                val expWidth = if (exp.width > 0f) exp.width * 0.78f else 0f
                val subWidth = if (ssub.width > 0f) ssub.width * 0.7f else 0f
                own(width / 2f, 0f)
                if (exp.width > 0f) put(exp, width + 2f, -34f)
                if (ssub.width > 0f) put(ssub, width + 2f, 30f)
                Metrics(width + max(expWidth, subWidth), width / 2f, positions)
            }
        }
    }

    fun layoutComponent(
        graph: AuraGraph,
        rootId: String,
        anchor: PointF,
        index: GraphIndex = indexGraph(graph)
    ): Map<String, PointF> {
        val metrics = measure(rootId, index, emptySet())
        val dx = anchor.x - metrics.ownX
        val dy = anchor.y
        val out = LinkedHashMap<String, PointF>()
        for ((id, pt) in metrics.positions) {
            out[id] = PointF((pt.x + dx).roundToInt().toFloat(), (pt.y + dy).roundToInt().toFloat())
        }
        return out
    }

    fun reflowGraph(graph: AuraGraph, anchorFor: ((String) -> PointF)? = null): AuraGraph {
        val index = indexGraph(graph)
        val positions = LinkedHashMap<String, PointF>()
        for (component in componentsOf(graph)) {
            val root = index.byId[component.rootId] ?: continue
            val anchor = anchorFor?.invoke(component.rootId) ?: PointF(root.x, root.y)
            positions.putAll(layoutComponent(graph, component.rootId, anchor, index))
        }
        return graph.copy(
            nodes = graph.nodes.map { node ->
                val pt = positions[node.id]
                if (pt != null) node.copy(x = pt.x, y = pt.y) else node
            }
        )
    }

    fun portAnchor(node: AuraNode, port: PortDef): PointF {
        val w = estimateWidth(node)
        val h = tileHeight()
        return when (port.side) {
            PortSide.Left -> PointF(node.x - w / 2f, node.y)
            PortSide.Right -> PointF(node.x + w / 2f, node.y)
            PortSide.Top -> PointF(node.x, node.y - h / 2f)
            PortSide.Bottom -> PointF(node.x, node.y + h / 2f)
            PortSide.TopRight -> PointF(node.x + w / 2f - 4f, node.y - h / 2f + 6f)
            PortSide.BottomRight -> PointF(node.x + w / 2f - 4f, node.y + h / 2f - 6f)
            PortSide.RightTop -> PointF(node.x + w / 2f, node.y - 12f)
            PortSide.RightBottom -> PointF(node.x + w / 2f, node.y + 12f)
        }
    }

    fun outAnchor(node: AuraNode): PointF = PointF(node.x + estimateWidth(node) / 2f, node.y)

    /* ------------------------------------------------------------------ */
    /* Homeomorphic Connection Candidates                                 */
    /* ------------------------------------------------------------------ */

    private fun withChanges(
        graph: AuraGraph,
        addedNodes: List<AuraNode>,
        addedEdges: List<AuraEdge>,
        removedEdgeIds: List<String>
    ): AuraGraph {
        val removed = removedEdgeIds.toSet()
        return AuraGraph(
            nodes = graph.nodes + addedNodes,
            edges = graph.edges.filter { it.id !in removed } + addedEdges
        )
    }

    private data class RelationTemplate(
        val variant: String,
        val token: String,
        val label: String,
        val title: String,
        val weight: Float,
        val kind: NodeKind
    )

    private val RELATION_TEMPLATES = listOf(
        RelationTemplate("equal", "=", "Equality", "Relate as equality", 1.0f, NodeKind.Relation),
        RelationTemplate("mapsto", "\\to", "Mapping", "Map one onto the other", 0.84f, NodeKind.Relation),
        RelationTemplate("juxta", "\\,", "Juxtaposition", "Bind as a product", 0.72f, NodeKind.Operator)
    )

    fun findCandidates(
        graph: AuraGraph,
        draggedId: String,
        pointer: PointF,
        radius: Float = 380f,
        limit: Int = 4
    ): List<GhostPath> {
        val index = indexGraph(graph)
        val dragged = index.byId[draggedId] ?: return emptyList()
        val subtree = subtreeIds(index, draggedId)
        val draggedType = effectiveType(index, draggedId)
        val draggedParent = index.parent[draggedId]

        val near = graph.nodes.filter { node ->
            node.id !in subtree && hypot(node.x - pointer.x, node.y - pointer.y) <= radius
        }

        val candidates = ArrayList<GhostPath>()

        // 1. Fill an open port of a nearby binder
        for (target in near) {
            val targetRoot = componentRoot(index, target.id) ?: continue
            for (portDef in portsFor(target)) {
                if (index.children[target.id]?.containsKey(portDef.id) == true) continue
                val admission = admits(portDef.accepts, draggedType)
                if (!admission.ok) continue
                val edge = AuraEdge(
                    id = newId(),
                    source = target.id,
                    target = draggedId,
                    port = portDef.id,
                    role = portDef.id.wireName
                )
                val removed = if (draggedParent != null) listOf(draggedParent.id) else emptyList()
                val temp = withChanges(graph, emptyList(), listOf(edge), removed)
                val tempIndex = indexGraph(temp)
                val root = componentRoot(tempIndex, target.id) ?: targetRoot
                val anchor = portAnchor(target, portDef)
                val dist = hypot(target.x - pointer.x, target.y - pointer.y)
                val proximity = max(0f, 1f - dist / radius)
                val score = min(
                    0.99f,
                    0.5f * proximity + 0.3f * (if (admission.exact) 1f else 0.72f) + 0.2f * (if (portDef.required) 1f else 0.78f)
                )
                candidates.add(
                    GhostPath(
                        colorHex = Ontology.GHOST_COLORS[0],
                        title = "Bind into ${portDef.label.lowercase()} of ${target.label}",
                        rationale = "${target.label} exposes a ${if (portDef.required) "required" else "optional"} ${portDef.label.lowercase()} port accepting ${portDef.accepts.joinToString(" | ") { it.label }}; ${dragged.label} infers ${draggedType.label}.",
                        score = score,
                        kind = "port-fill",
                        draggedId = draggedId,
                        targetId = target.id,
                        targetPort = portDef.id,
                        anchor = anchor,
                        previewLatex = toLatex(temp, root.id, tempIndex),
                        addedNodes = emptyList(),
                        addedEdges = listOf(edge),
                        removedEdgeIds = removed,
                        focusId = root.id
                    )
                )
            }
        }

        // 2. New relational binder joining two expression roots
        if (draggedParent == null && isOperandLike(dragged, draggedType)) {
            val roots = LinkedHashMap<String, AuraNode>()
            for (target in near) {
                val root = componentRoot(index, target.id)
                if (root != null && root.id != draggedId && root.id !in subtree) {
                    roots[root.id] = root
                }
            }
            for (root in roots.values) {
                val rootType = effectiveType(index, root.id)
                if (!isOperandLike(root, rootType)) continue
                if (!admits(Ontology.OPERAND_TYPES, draggedType).ok ||
                    !admits(Ontology.OPERAND_TYPES, rootType).ok ||
                    draggedType == MathType.Set ||
                    rootType == MathType.Set
                ) continue

                for (template in RELATION_TEMPLATES) {
                    val binder = makeNode(
                        kind = template.kind,
                        type = if (template.kind == NodeKind.Relation) MathType.Relation else MathType.Operator,
                        token = template.token,
                        label = template.label,
                        variant = template.variant,
                        x = (dragged.x + root.x) / 2f,
                        y = (dragged.y + root.y) / 2f
                    )
                    val edgeLeft = AuraEdge(newId(), binder.id, draggedId, PortId.Left)
                    val edgeRight = AuraEdge(newId(), binder.id, root.id, PortId.Right)
                    val temp = withChanges(graph, listOf(binder), listOf(edgeLeft, edgeRight), emptyList())
                    val dist = hypot(root.x - pointer.x, root.y - pointer.y)
                    val proximity = max(0f, 1f - dist / radius)
                    val score = min(0.98f, 0.48f * proximity + 0.3f + 0.2f * template.weight)
                    candidates.add(
                        GhostPath(
                            colorHex = Ontology.GHOST_COLORS[0],
                            title = "${template.title}: ${dragged.label} ${template.label.lowercase()} ${root.label}",
                            rationale = "Inserting a ${template.label.lowercase()} vertex is structure-preserving: both sides infer expression-compatible types (${draggedType.label}, ${rootType.label}).",
                            score = score,
                            kind = if (template.variant == "juxta") "product" else "relation",
                            draggedId = draggedId,
                            targetId = root.id,
                            targetPort = PortId.Left,
                            anchor = PointF(root.x, root.y - 44f),
                            previewLatex = toLatex(temp, binder.id, indexGraph(temp)),
                            addedNodes = listOf(binder),
                            addedEdges = listOf(edgeLeft, edgeRight),
                            removedEdgeIds = emptyList(),
                            focusId = binder.id
                        )
                    )
                }
            }
        }

        // 3. The dragged vertex wraps a nearby root in its own open port
        if (dragged.kind in listOf(NodeKind.Function, NodeKind.Container, NodeKind.Relation, NodeKind.Operator)) {
            val open = openPorts(index, dragged).firstOrNull { portDef ->
                portDef.required && near.any { target ->
                    val root = componentRoot(index, target.id)
                    root != null && root.id !in subtree && !index.parent.containsKey(root.id) &&
                        admits(portDef.accepts, effectiveType(index, root.id)).ok
                }
            }
            if (open != null) {
                val targets = LinkedHashMap<String, AuraNode>()
                for (target in near) {
                    val root = componentRoot(index, target.id)
                    if (root != null && root.id != draggedId && root.id !in subtree && !index.parent.containsKey(root.id)) {
                        targets[root.id] = root
                    }
                }
                for (root in targets.values) {
                    val rType = effectiveType(index, root.id)
                    if (!admits(open.accepts, rType).ok) continue
                    if (!isOperandLike(root, rType)) continue
                    val edge = AuraEdge(newId(), draggedId, root.id, open.id)
                    val temp = withChanges(graph, emptyList(), listOf(edge), emptyList())
                    val tempIndex = indexGraph(temp)
                    val dist = hypot(root.x - pointer.x, root.y - pointer.y)
                    val proximity = max(0f, 1f - dist / radius)
                    val anchor = PointF(root.x - estimateWidth(root) / 2f, root.y)
                    val focusId = componentRoot(index, draggedId)?.id ?: draggedId
                    candidates.add(
                        GhostPath(
                            colorHex = Ontology.GHOST_COLORS[0],
                            title = "Apply ${dragged.label} to ${root.label}",
                            rationale = "${dragged.label} has an open ${open.label.lowercase()} port accepting ${open.accepts.joinToString(" | ") { it.label }}.",
                            score = min(0.97f, 0.5f * proximity + 0.5f),
                            kind = "wrap",
                            draggedId = draggedId,
                            targetId = root.id,
                            targetPort = PortId.Left,
                            anchor = anchor,
                            previewLatex = toLatex(temp, componentRoot(tempIndex, draggedId)?.id ?: draggedId, tempIndex),
                            addedNodes = emptyList(),
                            addedEdges = listOf(edge),
                            removedEdgeIds = emptyList(),
                            focusId = focusId
                        )
                    )
                }
            }
        }

        val deduped = LinkedHashMap<String, GhostPath>()
        for (c in candidates) {
            val key = "${c.targetId}:${c.targetPort.wireName}:${c.addedNodes.joinToString(",") { it.variant ?: "" }}"
            val existing = deduped[key]
            if (existing == null || existing.score < c.score) {
                deduped[key] = c
            }
        }
        return deduped.values
            .sortedByDescending { it.score }
            .take(limit)
            .mapIndexed { idx, c ->
                c.copy(colorHex = Ontology.GHOST_COLORS[idx % Ontology.GHOST_COLORS.size])
            }
    }

    fun commitCandidate(graph: AuraGraph, candidate: ConnectionPatch): AuraGraph {
        val merged = withChanges(graph, candidate.addedNodes, candidate.addedEdges, candidate.removedEdgeIds)
        val index = indexGraph(merged)
        val root = componentRoot(index, candidate.focusId) ?: index.byId[candidate.focusId] ?: return merged
        val anchor = PointF(root.x, root.y)
        val positions = layoutComponent(merged, root.id, anchor, index)
        return merged.copy(
            nodes = merged.nodes.map { node ->
                val pt = positions[node.id]
                if (pt != null) node.copy(x = pt.x, y = pt.y) else node
            }
        )
    }

    /* ------------------------------------------------------------------ */
    /* Collapse / Expand & Validation                                     */
    /* ------------------------------------------------------------------ */

    data class CollapseOutcome(val graph: AuraGraph, val groupId: String)

    fun collapseSelection(graph: AuraGraph, ids: List<String>): CollapseOutcome? {
        val selected = ids.toSet()
        val nodes = graph.nodes.filter { it.id in selected }
        if (nodes.isEmpty()) return null
        val index = indexGraph(graph)
        val rootId = nodes.mapNotNull { componentRoot(index, it.id)?.id }
            .firstOrNull { it in selected } ?: nodes.first().id
        val latex = toLatex(graph, rootId, index)
        val innerEdges = graph.edges.filter { it.source in selected && it.target in selected }
        val boundaryEdges = graph.edges.filter { (it.source in selected) != (it.target in selected) }
        val centerX = nodes.map { it.x }.average().toFloat()
        val centerY = nodes.map { it.y }.average().toFloat()
        val group = AuraNode(
            id = newId(),
            kind = NodeKind.Group,
            type = MathType.Expression,
            token = latex,
            label = "⟨${nodes.size} vertices⟩",
            variant = null,
            x = centerX,
            y = centerY,
            collapsed = true,
            collapsedLatex = latex,
            collapsedPayload = CollapsedPayload(nodes, innerEdges)
        )
        val remainingNodes = graph.nodes.filter { it.id !in selected }
        val edges = boundaryEdges.map { edge ->
            edge.copy(
                source = if (edge.source in selected) group.id else edge.source,
                target = if (edge.target in selected) group.id else edge.target
            )
        }
        return CollapseOutcome(AuraGraph(remainingNodes + group, edges), group.id)
    }

    fun expandGroup(graph: AuraGraph, groupId: String): AuraGraph? {
        val group = graph.nodes.find { it.id == groupId } ?: return null
        if (!group.collapsed) return null
        val payload = group.collapsedPayload ?: return null
        val incoming = graph.edges.filter { it.source == groupId || it.target == groupId }
        val others = graph.edges.filter { it.source != groupId && it.target != groupId }
        val rootId = payload.nodes.firstOrNull { n -> payload.edges.none { it.target == n.id } }?.id
        val anchor = if (rootId != null) payload.nodes.find { it.id == rootId } else null
        val shiftX = if (anchor != null) group.x - anchor.x else 0f
        val shiftY = if (anchor != null) group.y - anchor.y else 0f
        val shifted = payload.nodes.map { it.copy(x = it.x + shiftX, y = it.y + shiftY) }
        val rebound = incoming.map { edge ->
            edge.copy(
                source = if (edge.source == groupId) (rootId ?: edge.source) else edge.source,
                target = if (edge.target == groupId) (rootId ?: edge.target) else edge.target
            )
        }
        return AuraGraph(
            nodes = graph.nodes.filter { it.id != groupId } + shifted,
            edges = others + payload.edges + rebound
        )
    }

    fun graphBounds(graph: AuraGraph): GraphBounds {
        if (graph.nodes.isEmpty()) return GraphBounds(0f, 0f, 800f, 600f)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (node in graph.nodes) {
            val w = estimateWidth(node)
            val h = tileHeight()
            minX = min(minX, node.x - w / 2f)
            maxX = max(maxX, node.x + w / 2f)
            minY = min(minY, node.y - h / 2f)
            maxY = max(maxY, node.y + h / 2f)
        }
        return GraphBounds(minX, minY, maxX, maxY)
    }

    fun validateGraph(graph: AuraGraph): String? {
        val ids = HashSet<String>()
        for (node in graph.nodes) {
            if (!ids.add(node.id)) return "Duplicate vertex id ${node.id}"
            if (!node.x.isFinite() || !node.y.isFinite()) return "Vertex ${node.id} has an invalid position"
        }
        val edgeIds = HashSet<String>()
        val bindings = HashMap<String, Int>()
        for (edge in graph.edges) {
            if (!edgeIds.add(edge.id)) return "Duplicate edge id ${edge.id}"
            if (!ids.contains(edge.source) || !ids.contains(edge.target)) {
                return "Edge ${edge.id} references a missing vertex"
            }
            bindings[edge.target] = (bindings[edge.target] ?: 0) + 1
        }
        val byId = graph.nodes.associateBy { it.id }
        for ((target, count) in bindings) {
            val node = byId[target]
            if (node != null && count > 1 && !isShareable(node)) {
                return "${node.label} is bound to $count ports; composite vertices may have one parent"
            }
        }
        return null
    }
}
