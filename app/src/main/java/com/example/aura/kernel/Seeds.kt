package com.example.aura.kernel

/**
 * All 5 original Aura seeded canvases plus EMPTY_DOCUMENT.
 */

data class SeedDocument(
    val title: String,
    val subtitle: String,
    val graph: AuraGraph
)

object Seeds {
    private class Draft {
        val nodes = ArrayList<AuraNode>()
        val edges = ArrayList<AuraEdge>()

        fun add(
            kind: NodeKind,
            token: String,
            label: String,
            type: MathType = MathType.Expression,
            variant: String? = null,
            x: Float = 0f,
            y: Float = 0f
        ): AuraNode {
            val node = makeNode(kind = kind, token = token, label = label, type = type, variant = variant, x = x, y = y)
            nodes.add(node)
            return node
        }

        fun bind(parent: AuraNode, port: PortId, child: AuraNode): AuraEdge {
            val edge = AuraEdge(id = newId(), source = parent.id, target = child.id, port = port, role = port.wireName)
            edges.add(edge)
            return edge
        }

        fun finish(rootId: String, anchor: PointF): AuraGraph {
            val g = AuraGraph(nodes.toList(), edges.toList())
            val positions = Engine.layoutComponent(g, rootId, anchor, Engine.indexGraph(g))
            return AuraGraph(
                nodes = nodes.map { n ->
                    val pt = positions[n.id]
                    if (pt != null) n.copy(x = pt.x, y = pt.y) else n
                },
                edges = edges.toList()
            )
        }
    }

    val SEED_DOCUMENTS: List<SeedDocument> = listOf(
        SeedDocument(
            title = "Energy–mass equivalence",
            subtitle = "Drag E toward the mc² cluster and watch the ghost paths resolve.",
            graph = run {
                val d = Draft()
                d.add(NodeKind.Variable, "E", "Energy E", MathType.Variable, x = 300f, y = 300f)
                val mass = d.add(NodeKind.Variable, "m", "Mass m", MathType.Variable)
                val speed = d.add(NodeKind.Variable, "c", "Speed c", MathType.Variable)
                val two = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val power = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val product = d.add(NodeKind.Operator, "\\,", "Juxtaposition", MathType.Operator, "juxta")

                d.bind(power, PortId.Base, speed)
                d.bind(power, PortId.Exponent, two)
                d.bind(product, PortId.Left, mass)
                d.bind(product, PortId.Right, power)

                d.add(NodeKind.Relation, "\\to", "Mapping", MathType.Relation, "mapsto", x = 300f, y = 452f)
                d.add(NodeKind.Container, "\\sqrt{\\phantom{x}}", "Square root", MathType.Expression, "sqrt", x = 470f, y = 452f)

                d.finish(product.id, PointF(760f, 300f))
            }
        ),
        SeedDocument(
            title = "Pythagorean identity",
            subtitle = "A committed relation. Select a vertex to inspect ports, types, symbolic steps, and 3D plot.",
            graph = run {
                val d = Draft()
                val a = d.add(NodeKind.Variable, "a", "Variable a", MathType.Variable)
                val b = d.add(NodeKind.Variable, "b", "Variable b", MathType.Variable)
                val c = d.add(NodeKind.Variable, "c", "Variable c", MathType.Variable)
                val twoA = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val twoB = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val twoC = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val powA = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val powB = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val powC = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val add = d.add(NodeKind.Operator, "+", "Addition", MathType.Operator, "add")
                val equal = d.add(NodeKind.Relation, "=", "Equality", MathType.Relation, "equal")

                d.bind(powA, PortId.Base, a)
                d.bind(powA, PortId.Exponent, twoA)
                d.bind(powB, PortId.Base, b)
                d.bind(powB, PortId.Exponent, twoB)
                d.bind(powC, PortId.Base, c)
                d.bind(powC, PortId.Exponent, twoC)
                d.bind(add, PortId.Left, powA)
                d.bind(add, PortId.Right, powB)
                d.bind(equal, PortId.Left, add)
                d.bind(equal, PortId.Right, powC)

                d.add(NodeKind.Container, "\\int", "Integral", MathType.Expression, "int", x = 360f, y = 520f)
                d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar, x = 520f, y = 520f)

                d.finish(equal.id, PointF(680f, 300f))
            }
        ),
        SeedDocument(
            title = "Integral & type sandbox",
            subtitle = "Limits bind into the ∫ container. Try binding the matrix A into + with scalar 2.",
            graph = run {
                val d = Draft()
                val zero = d.add(NodeKind.Literal, "0", "Literal 0", MathType.Scalar)
                val one = d.add(NodeKind.Literal, "1", "Literal 1", MathType.Scalar)
                val x = d.add(NodeKind.Variable, "x", "Variable x", MathType.Variable)
                val twoUpper = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val twoLower = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val dx = d.add(NodeKind.Variable, "d", "Differential d", MathType.Variable)
                val body = d.add(NodeKind.Operator, "\\times", "Product", MathType.Operator, "mul")
                val power = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val frac = d.add(NodeKind.Operator, "\\frac", "Fraction", MathType.Operator, "frac")
                val integral = d.add(NodeKind.Container, "\\int", "Integral", MathType.Expression, "int")

                d.bind(power, PortId.Base, x)
                d.bind(power, PortId.Exponent, twoUpper)
                d.bind(frac, PortId.Numerator, power)
                d.bind(frac, PortId.Denominator, twoLower)
                d.bind(body, PortId.Left, frac)
                d.bind(body, PortId.Right, dx)
                d.bind(integral, PortId.Lower, zero)
                d.bind(integral, PortId.Upper, one)
                d.bind(integral, PortId.Body, body)

                d.add(NodeKind.Operator, "+", "Addition", MathType.Operator, "add", x = 480f, y = 520f)
                d.add(NodeKind.Variable, "A", "Matrix A", MathType.Matrix, x = 620f, y = 520f)
                d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar, x = 750f, y = 520f)
                d.add(NodeKind.Set, "\\mathbb{R}", "ℝ", MathType.Set, x = 350f, y = 440f)

                d.finish(integral.id, PointF(640f, 280f))
            }
        ),
        SeedDocument(
            title = "Evaluator & plot",
            subtitle = "Fold the constant cluster, then open the plot tab to sample y = 2x² − 3 and sin x straight from the graph.",
            graph = run {
                val d = Draft()
                val y = d.add(NodeKind.Variable, "y", "Variable y", MathType.Variable)
                val x1 = d.add(NodeKind.Variable, "x", "Variable x", MathType.Variable)
                val two = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val three = d.add(NodeKind.Literal, "3", "Literal 3", MathType.Scalar)
                val twoExp = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val power = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val product = d.add(NodeKind.Operator, "\\,", "Juxtaposition", MathType.Operator, "juxta")
                val minus = d.add(NodeKind.Operator, "-", "Subtraction", MathType.Operator, "sub")
                val equal = d.add(NodeKind.Relation, "=", "Equality", MathType.Relation, "equal")

                d.bind(power, PortId.Base, x1)
                d.bind(power, PortId.Exponent, twoExp)
                d.bind(product, PortId.Left, two)
                d.bind(product, PortId.Right, power)
                d.bind(minus, PortId.Left, product)
                d.bind(minus, PortId.Right, three)
                d.bind(equal, PortId.Left, y)
                d.bind(equal, PortId.Right, minus)

                val sin = d.add(NodeKind.Function, "\\sin", "sin", MathType.Function, "sin")
                val x2 = d.add(NodeKind.Variable, "x", "Variable x", MathType.Variable)
                d.bind(sin, PortId.Arg, x2)

                val six = d.add(NodeKind.Literal, "6", "Literal 6", MathType.Scalar)
                val twoC = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val four = d.add(NodeKind.Literal, "4", "Literal 4", MathType.Scalar)
                val plus = d.add(NodeKind.Operator, "+", "Addition", MathType.Operator, "add")
                val frac = d.add(NodeKind.Operator, "\\frac", "Fraction", MathType.Operator, "frac")
                d.bind(plus, PortId.Left, six)
                d.bind(plus, PortId.Right, twoC)
                d.bind(frac, PortId.Numerator, plus)
                d.bind(frac, PortId.Denominator, four)

                val relation = d.finish(equal.id, PointF(580f, 270f))
                val sinLayout = d.finish(sin.id, PointF(380f, 450f))
                val fracLayout = d.finish(frac.id, PointF(720f, 450f))

                val merged = LinkedHashMap<String, PointF>()
                for (node in relation.nodes + sinLayout.nodes + fracLayout.nodes) {
                    if (!merged.containsKey(node.id)) merged[node.id] = PointF(node.x, node.y)
                }
                AuraGraph(
                    nodes = d.nodes.map { n ->
                        val pt = merged[n.id]
                        if (pt != null) n.copy(x = pt.x, y = pt.y) else n
                    },
                    edges = d.edges.toList()
                )
            }
        ),
        SeedDocument(
            title = "Discovery playground",
            subtitle = "Seven free clusters. Press Auto-connect and let the engine find, test and rank bindings that hold true.",
            graph = run {
                val d = Draft()
                val layouts = ArrayList<Pair<String, PointF>>()

                // Cluster 1: sin²θ + cos²θ
                val theta = d.add(NodeKind.Variable, "\\theta", "Angle θ", MathType.Variable)
                val twoA = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val twoB = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val sin = d.add(NodeKind.Function, "\\sin", "sin", MathType.Function, "sin")
                val cos = d.add(NodeKind.Function, "\\cos", "cos", MathType.Function, "cos")
                val sinSq = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val cosSq = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val identity = d.add(NodeKind.Operator, "+", "Addition", MathType.Operator, "add")

                d.bind(sin, PortId.Arg, theta)
                d.bind(cos, PortId.Arg, theta)
                d.bind(sinSq, PortId.Base, sin)
                d.bind(sinSq, PortId.Exponent, twoA)
                d.bind(cosSq, PortId.Base, cos)
                d.bind(cosSq, PortId.Exponent, twoB)
                d.bind(identity, PortId.Left, sinSq)
                d.bind(identity, PortId.Right, cosSq)
                layouts.add(identity.id to PointF(480f, 230f))

                // Cluster 2: 1
                val one = d.add(NodeKind.Literal, "1", "Literal 1", MathType.Scalar)
                layouts.add(one.id to PointF(840f, 230f))

                // Cluster 3: (6 + 2) / 4
                val six = d.add(NodeKind.Literal, "6", "Literal 6", MathType.Scalar)
                val twoC = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val four = d.add(NodeKind.Literal, "4", "Literal 4", MathType.Scalar)
                val plus = d.add(NodeKind.Operator, "+", "Addition", MathType.Operator, "add")
                val quotient = d.add(NodeKind.Operator, "\\frac", "Fraction", MathType.Operator, "frac")
                d.bind(plus, PortId.Left, six)
                d.bind(plus, PortId.Right, twoC)
                d.bind(quotient, PortId.Numerator, plus)
                d.bind(quotient, PortId.Denominator, four)
                layouts.add(quotient.id to PointF(480f, 430f))

                // Cluster 4: 2
                val twoD = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                layouts.add(twoD.id to PointF(720f, 430f))

                // Cluster 5: E
                val energy = d.add(NodeKind.Variable, "E", "Energy E", MathType.Variable)
                layouts.add(energy.id to PointF(280f, 610f))

                // Cluster 6: m c²
                val mass = d.add(NodeKind.Variable, "m", "Mass m", MathType.Variable)
                val speed = d.add(NodeKind.Variable, "c", "Speed c", MathType.Variable)
                val twoE = d.add(NodeKind.Literal, "2", "Literal 2", MathType.Scalar)
                val cSquared = d.add(NodeKind.Operator, "^", "Power", MathType.Operator, "power")
                val mcSquared = d.add(NodeKind.Operator, "\\,", "Juxtaposition", MathType.Operator, "juxta")
                d.bind(cSquared, PortId.Base, speed)
                d.bind(cSquared, PortId.Exponent, twoE)
                d.bind(mcSquared, PortId.Left, mass)
                d.bind(mcSquared, PortId.Right, cSquared)
                layouts.add(mcSquared.id to PointF(650f, 610f))

                // Cluster 7: ℝ
                val reals = d.add(NodeKind.Set, "\\mathbb{R}", "ℝ", MathType.Set)
                layouts.add(reals.id to PointF(960f, 610f))

                val positions = LinkedHashMap<String, PointF>()
                for ((rootId, anchor) in layouts) {
                    val g = d.finish(rootId, anchor)
                    val compIds = Engine.componentIds(g, rootId)
                    for (node in g.nodes) {
                        if (node.id in compIds) {
                            positions[node.id] = PointF(node.x, node.y)
                        }
                    }
                }
                AuraGraph(
                    nodes = d.nodes.map { n ->
                        val pt = positions[n.id]
                        if (pt != null) n.copy(x = pt.x, y = pt.y) else n
                    },
                    edges = d.edges.toList()
                )
            }
        )
    )

    fun emptyDocument(title: String): SeedDocument {
        val x = makeNode(
            kind = NodeKind.Variable,
            token = "x",
            label = "Variable x",
            type = MathType.Variable,
            x = 520f,
            y = 300f
        )
        return SeedDocument(
            title = title,
            subtitle = "An empty infinite canvas. Add elements or write an expression with the pen.",
            graph = AuraGraph(nodes = listOf(x), edges = emptyList())
        )
    }
}
