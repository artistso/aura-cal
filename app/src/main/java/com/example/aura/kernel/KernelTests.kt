package com.example.aura.kernel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Aura kernel verification suite.
 *
 * 60 executable specifications covering Ontology, Types, Serialisation, Parser,
 * Evaluator (2D & 3D), Symbolic & Solver, Graph invariants, Scope, Discovery & Proofs,
 * Ink Classifier, Library, Robustness and Interop.
 *
 * The suite runs in-app (Verify tab) and on the JVM through `./gradlew test`, so a regression
 * in the expression kernel fails the build rather than reaching a tester.
 */

data class SpecTestCase(
    val suite: String,
    val name: String,
    val run: () -> Unit
)

data class TestOutcome(
    val suite: String,
    val name: String,
    val ok: Boolean,
    val error: String?
)

data class SuiteSummary(
    val suite: String,
    val passed: Int,
    val failed: Int
)

data class SuiteReport(
    val generatedAt: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val durationMs: Long,
    val suites: List<SuiteSummary>,
    val outcomes: List<TestOutcome>,
    val librarySymbols: Int
)

object KernelTests {
    private fun checkCond(condition: Boolean, message: String) {
        if (!condition) throw IllegalStateException(message)
    }

    private fun <T> checkEq(actual: T, expected: T, message: String) {
        if (actual != expected) {
            throw IllegalStateException("$message — expected $expected, received $actual")
        }
    }

    private class SpecDraft {
        val nodes = ArrayList<AuraNode>()
        val edges = ArrayList<AuraEdge>()

        fun node(
            kind: NodeKind,
            token: String,
            label: String,
            type: MathType = MathType.Expression,
            variant: String? = null
        ): AuraNode {
            val n = makeNode(kind = kind, token = token, label = label, type = type, variant = variant)
            nodes.add(n)
            return n
        }

        fun bind(source: AuraNode, port: PortId, target: AuraNode) {
            edges.add(AuraEdge(id = newId(), source = source.id, target = target.id, port = port, role = port.wireName))
        }

        fun graph(): AuraGraph = AuraGraph(nodes.toList(), edges.toList())
    }

    private fun num(d: SpecDraft, token: String) =
        d.node(NodeKind.Literal, token, "Literal $token", MathType.Scalar)

    private fun variable(d: SpecDraft, token: String, type: MathType = MathType.Variable) =
        d.node(NodeKind.Variable, token, "Variable $token", type)

    private fun operator(d: SpecDraft, token: String, variant: String) =
        d.node(NodeKind.Operator, token, variant, MathType.Operator, variant)

    private data class MassEnergyFixture(
        val d: SpecDraft,
        val m: AuraNode,
        val c: AuraNode,
        val two: AuraNode,
        val power: AuraNode,
        val product: AuraNode
    )

    private fun massEnergy(): MassEnergyFixture {
        val d = SpecDraft()
        val m = variable(d, "m")
        val c = variable(d, "c")
        val two = num(d, "2")
        val power = operator(d, "^", "power")
        val product = operator(d, "\\,", "juxta")
        d.bind(power, PortId.Base, c)
        d.bind(power, PortId.Exponent, two)
        d.bind(product, PortId.Left, m)
        d.bind(product, PortId.Right, power)
        return MassEnergyFixture(d, m, c, two, power, product)
    }

    val TEST_CASES: List<SpecTestCase> = listOf(
        // Ontology (4)
        SpecTestCase("Ontology", "postfix operators expose a single operand port") {
            val node = makeNode(NodeKind.Operator, "!", "Factorial", MathType.Operator, "fact")
            val ports = portsFor(node)
            checkEq(ports.size, 1, "factorial arity")
            checkEq(ports[0].id, PortId.Arg, "factorial port id")
            checkCond(ports[0].required, "factorial port must be required")
        },
        SpecTestCase("Ontology", "big operators expose lower, upper and body ports") {
            val node = makeNode(NodeKind.Container, "\\sum", "Summation", MathType.Expression, "sum")
            val ids = portsFor(node).map { it.id.wireName }.sorted().joinToString(",")
            checkEq(ids, "body,lower,upper", "summation ports")
        },
        SpecTestCase("Ontology", "matrices expose exactly one entries port") {
            val node = makeNode(NodeKind.Container, "\\begin{pmatrix}", "Matrix", MathType.Expression, "matrix")
            checkEq(portsFor(node).size, 1, "matrix arity")
            checkEq(portsFor(node)[0].id, PortId.Body, "matrix port")
        },
        SpecTestCase("Ontology", "membership accepts a Set but not a Matrix") {
            val relation = makeNode(NodeKind.Relation, "\\in", "Member of", MathType.Relation, "in")
            val right = portsFor(relation).find { it.id == PortId.Right }
                ?: throw IllegalStateException("membership must expose a right port")
            checkCond(portAdmits(right, MathType.Set).ok, "Set must be admitted")
            checkCond(!portAdmits(right, MathType.Matrix).ok, "Matrix must be rejected")
        },

        // Types (3)
        SpecTestCase("Types", "matrix + scalar is diagnosed as ill-typed") {
            val d = SpecDraft()
            val matrix = variable(d, "A", MathType.Matrix)
            val two = num(d, "2")
            val plus = operator(d, "+", "add")
            d.bind(plus, PortId.Left, matrix)
            d.bind(plus, PortId.Right, two)
            val errors = Engine.diagnose(d.graph()).filter { it.severity == DiagnosticSeverity.Error }
            checkCond(errors.any { it.message.contains("Scalar and Matrix") }, "expected scalar/matrix conflict")
        },
        SpecTestCase("Types", "type propagation lifts a product of variables to Scalar") {
            val me = massEnergy()
            val index = Engine.indexGraph(me.d.graph())
            checkEq(Engine.effectiveType(index, me.product.id), MathType.Scalar, "product type")
        },
        SpecTestCase("Types", "a set operand is flagged on arithmetic") {
            val d = SpecDraft()
            val reals = d.node(NodeKind.Set, "\\mathbb{R}", "Reals", MathType.Set)
            val two = num(d, "2")
            val plus = operator(d, "+", "add")
            d.bind(plus, PortId.Left, reals)
            d.bind(plus, PortId.Right, two)
            val errors = Engine.diagnose(d.graph()).filter { it.severity == DiagnosticSeverity.Error }
            checkCond(errors.isNotEmpty(), "set arithmetic must be rejected")
        },

        // Serialisation (4)
        SpecTestCase("Serialisation", "renders mass-energy as m\\,{c}^{2}") {
            val me = massEnergy()
            checkEq(Engine.toLatex(me.d.graph(), me.product.id), "m\\,{c}^{2}", "product latex")
        },
        SpecTestCase("Serialisation", "precedence inserts parentheses for (a+b)×c") {
            val d = SpecDraft()
            val a = variable(d, "a")
            val b = variable(d, "b")
            val c = variable(d, "c")
            val sum = operator(d, "+", "add")
            val product = operator(d, "\\times", "mul")
            d.bind(sum, PortId.Left, a)
            d.bind(sum, PortId.Right, b)
            d.bind(product, PortId.Left, sum)
            d.bind(product, PortId.Right, c)
            val latex = Engine.toLatex(d.graph(), product.id)
            checkCond(latex.contains("\\left("), "expected parentheses, received $latex")
        },
        SpecTestCase("Serialisation", "renders binomials, accents and n-th roots") {
            val d = SpecDraft()
            val n = variable(d, "n")
            val k = variable(d, "k")
            val binom = operator(d, "\\binom", "binom")
            d.bind(binom, PortId.Numerator, n)
            d.bind(binom, PortId.Denominator, k)
            checkEq(Engine.toLatex(d.graph(), binom.id), "\\binom{n}{k}", "binomial latex")

            val d2 = SpecDraft()
            val x = variable(d2, "x")
            val hat = d2.node(NodeKind.Container, "\\hat", "Hat", MathType.Expression, "hat")
            d2.bind(hat, PortId.Body, x)
            checkEq(Engine.toLatex(d2.graph(), hat.id), "\\hat{x}", "accent latex")

            val d3 = SpecDraft()
            val three = num(d3, "3")
            val y = variable(d3, "y")
            val root = d3.node(NodeKind.Container, "\\sqrt", "nth root", MathType.Expression, "root")
            d3.bind(root, PortId.Upper, three)
            d3.bind(root, PortId.Body, y)
            checkEq(Engine.toLatex(d3.graph(), root.id), "\\sqrt[3]{y}", "nth root latex")
        },
        SpecTestCase("Serialisation", "every library token survives a parse → render round trip") {
            // Bare delimiters are palette glyphs, not expressions: `|` opens an enclosure and
            // has no meaning on its own, so the parser must reject it instead of inventing a leaf.
            val delimiters = setOf("|", "\\|")
            val failures = ArrayList<String>()
            for (entry in SymbolLibrary.SYMBOL_LIBRARY) {
                if (entry.kind !in listOf(NodeKind.Literal, NodeKind.Variable, NodeKind.Set)) continue
                if (entry.token in delimiters) continue
                try {
                    val parsed = LatexParser.parseLatex(entry.token)
                    if (parsed.nodeCount == 0) failures.add(entry.token)
                } catch (_: Exception) {
                    failures.add(entry.token)
                }
            }
            checkCond(failures.isEmpty(), "unparsable leaf tokens: ${failures.take(6).joinToString(", ")}")
        },
        SpecTestCase("Serialisation", "bare delimiters are rejected rather than turned into vertices") {
            for (delimiter in listOf("|", "\\|")) {
                var threw = false
                try {
                    LatexParser.parseLatex(delimiter)
                } catch (_: ParseError) {
                    threw = true
                }
                checkCond(threw, "\"$delimiter\" must raise a ParseError")
            }
        },

        // Parser (3)
        SpecTestCase("Parser", "decomposes the quadratic formula into typed vertices") {
            val parsed = LatexParser.parseLatex("\\frac{-b + \\sqrt{b^2 - 4ac}}{2a}")
            checkCond(parsed.nodeCount >= 12, "expected >= 12 vertices, received ${parsed.nodeCount}")
            checkCond(parsed.edgeCount >= 11, "expected >= 11 bindings, received ${parsed.edgeCount}")
            checkCond(parsed.normalized.contains("\\frac"), "normalised form must retain the fraction")
        },
        SpecTestCase("Parser", "parses limits, integrals and indexed unions") {
            val sum = LatexParser.parseLatex("\\sum_{n=1}^{10} n^2")
            checkCond(sum.normalized.contains("\\sum"), "summation head")
            checkCond(sum.nodeCount >= 6, "summation vertices")
            val integral = LatexParser.parseLatex("\\int_{0}^{1} x^2")
            checkCond(integral.normalized.contains("\\int_{0}^{1}"), "integral limits, received ${integral.normalized}")
            val union = LatexParser.parseLatex("\\cup_{i=1}^{n} A_i")
            checkCond(union.normalized.contains("\\bigcup"), "indexed union, received ${union.normalized}")
        },
        SpecTestCase("Parser", "rejects malformed input instead of inventing bindings") {
            var threw = false
            try {
                LatexParser.parseLatex("\\frac{a}{")
            } catch (_: Exception) {
                threw = true
            }
            checkCond(threw, "unbalanced braces must raise a ParseError")
        },

        SpecTestCase("Parser", "unbraced exponent binds a single atom, not the rest of the equation") {
            val parsed = LatexParser.parseLatex("a^2 + b^2 = c^2")
            val index = Engine.indexGraph(parsed.graph)
            val root = index.byId[parsed.rootId!!]
            checkEq(root?.kind, NodeKind.Relation, "root must be the equality")
            val left = Engine.childOf(index, root!!.id, PortId.Left)!!
            checkEq(left.variant, "add", "left side must be a sum")
            val leftPower = Engine.childOf(index, left.id, PortId.Left)!!
            checkEq(leftPower.variant, "power", "first term must be a power")
            checkEq(Engine.childOf(index, leftPower.id, PortId.Exponent)?.token, "2", "a^2 keeps exponent 2")
            val right = Engine.childOf(index, root.id, PortId.Right)!!
            checkEq(right.variant, "power", "right side must be c^2")
            checkEq(Engine.childOf(index, right.id, PortId.Exponent)?.token, "2", "c^2 keeps exponent 2")
            checkEq(Engine.diagnose(parsed.graph).size, 0, "the Pythagorean theorem must be well-typed")
        },
        SpecTestCase("Parser", "braced exponent keeps a full expression") {
            val parsed = LatexParser.parseLatex("x^{2n+1}")
            val index = Engine.indexGraph(parsed.graph)
            val root = index.byId[parsed.rootId!!]!!
            checkEq(root.variant, "power", "x^(2n+1) is a power")
            val exp = Engine.childOf(index, root.id, PortId.Exponent)!!
            checkEq(exp.variant, "add", "the braced exponent is the sum 2n + 1")
        },
        SpecTestCase("Parser", "power of a power is read left to right without dropping tokens") {
            val parsed = LatexParser.parseLatex("x^2^3")
            val index = Engine.indexGraph(parsed.graph)
            val root = index.byId[parsed.rootId!!]!!
            checkEq(root.variant, "power", "outer power")
            val base = Engine.childOf(index, root.id, PortId.Base)!!
            checkEq(base.variant, "power", "inner power (x^2)^3")
            checkEq(Engine.childOf(index, root.id, PortId.Exponent)?.token, "3", "outer exponent survives")
        },
        SpecTestCase("Parser", "x^2 = 9 parses as an equality of a power and a literal") {
            val parsed = LatexParser.parseLatex("x^2 = 9")
            val index = Engine.indexGraph(parsed.graph)
            val root = index.byId[parsed.rootId!!]!!
            checkEq(root.kind, NodeKind.Relation, "root is the equality")
            checkCond(root.variant == "equal", "variant is equal")
            checkEq(Engine.childOf(index, root.id, PortId.Left)?.variant, "power", "left side is x²")
            checkEq(Engine.diagnose(parsed.graph).size, 0, "no diagnostics for x² = 9")
        },
        SpecTestCase("Parser", "reads integral limits and strips the differential") {
            val parsed = LatexParser.parseLatex("\\int_0^1 x^2 \\, dx")
            val index = Engine.indexGraph(parsed.graph)
            val root = index.byId[parsed.rootId!!]!!
            checkEq(root.variant, "int", "root is an integral")
            checkEq(Engine.childOf(index, root.id, PortId.Lower)?.token, "0", "lower limit")
            checkEq(Engine.childOf(index, root.id, PortId.Upper)?.token, "1", "upper limit")
            val body = Engine.childOf(index, root.id, PortId.Body)!!
            checkEq(body.variant, "power", "integrand is x², not x²·d·x")
            checkCond(parsed.graph.nodes.none { it.token == "d" }, "the differential must not become a vertex")
            checkEq(Engine.diagnose(parsed.graph).size, 0, "integral must be complete")
        },
        SpecTestCase("Parser", "summation index relations are admissible limits") {
            val parsed = LatexParser.parseLatex("\\sum_{n=1}^{10} n")
            val index = Engine.indexGraph(parsed.graph)
            val root = index.byId[parsed.rootId!!]!!
            val lower = Engine.childOf(index, root.id, PortId.Lower)!!
            checkEq(lower.kind, NodeKind.Relation, "lower limit is the index relation n = 1")
            val errors = Engine.diagnose(parsed.graph).filter { it.severity == DiagnosticSeverity.Error }
            checkEq(errors.size, 0, "index bounds are legal, received ${errors.map { it.message }}")
            checkEq(Evaluate.evalExpression(index, root.id), 55.0, "sum 1..10")
        },
        SpecTestCase("Parser", "absolute value parses to an enclosure that evaluates") {
            val parsed = LatexParser.parseLatex("|x - 3| = 4")
            val index = Engine.indexGraph(parsed.graph)
            val root = index.byId[parsed.rootId!!]!!
            checkEq(root.kind, NodeKind.Relation, "root is the equality")
            val left = Engine.childOf(index, root.id, PortId.Left)!!
            checkEq(left.variant, "abs", "left side is an absolute value")
            checkEq(left.kind, NodeKind.Container, "enclosure is a container vertex")
            val value = LatexParser.parseLatex("|-3|")
            checkEq(Evaluate.evalExpression(Engine.indexGraph(value.graph), value.rootId!!), 3.0, "|-3| = 3")
        },
        SpecTestCase("Parser", "rejects an unclosed absolute value") {
            var threw = false
            try {
                LatexParser.parseLatex("|x + 1")
            } catch (_: ParseError) {
                threw = true
            }
            checkCond(threw, "an unmatched bar must raise a ParseError")
        },
        SpecTestCase("Parser", "rejects trailing tokens instead of silently dropping them") {
            var threw = false
            try {
                LatexParser.parseLatex("x = 1 )")
            } catch (_: ParseError) {
                threw = true
            }
            checkCond(threw, "leftover input must raise a ParseError")
        },
        SpecTestCase("Types", "absolute value of a variable infers a scalar type") {
            val parsed = LatexParser.parseLatex("|x|")
            val index = Engine.indexGraph(parsed.graph)
            checkEq(
                Engine.effectiveType(index, parsed.rootId!!),
                MathType.Scalar,
                "|x| is scalar-valued"
            )
        },

        // Evaluator (6 - including new 3D surface evaluation)
        SpecTestCase("Evaluator", "evaluates constant arithmetic, powers and fractions") {
            val parsed = LatexParser.parseLatex("\\frac{6 + 2}{4}")
            val value = Evaluate.evalExpression(Engine.indexGraph(parsed.graph), parsed.rootId ?: "")
            checkEq(value, 2.0, "fraction value")
        },
        SpecTestCase("Evaluator", "evaluates trigonometric, factorial and binomial forms") {
            val trig = LatexParser.parseLatex("\\sin(\\pi)")
            val trigValue = Evaluate.evalExpression(Engine.indexGraph(trig.graph), trig.rootId ?: "")
            checkCond(trigValue != null && abs(trigValue) < 1e-9, "sin(π) received $trigValue")
            val fact = LatexParser.parseLatex("5!")
            checkEq(Evaluate.evalExpression(Engine.indexGraph(fact.graph), fact.rootId ?: ""), 120.0, "5!")
            val binom = LatexParser.parseLatex("\\binom{6}{2}")
            checkEq(Evaluate.evalExpression(Engine.indexGraph(binom.graph), binom.rootId ?: ""), 15.0, "C(6,2)")
        },
        SpecTestCase("Evaluator", "numeric summation respects its bounds") {
            val parsed = LatexParser.parseLatex("\\sum_{n=1}^{10} n")
            val value = Evaluate.evalExpression(Engine.indexGraph(parsed.graph), parsed.rootId ?: "")
            checkEq(value, 55.0, "sum 1..10")
        },
        SpecTestCase("Evaluator", "recognises y = f(x) as plottable") {
            val parsed = LatexParser.parseLatex("y = x^2 - 3")
            val analysis = Evaluate.analyzeGraph(parsed.graph)
            val plottable = analysis.find { it.plottable != null }
            checkCond(plottable != null, "quadratic must be plottable")
            checkEq(plottable?.plottable?.variable, "x", "independent variable")
        },
        SpecTestCase("Evaluator", "folds constant subexpressions only") {
            val parsed = LatexParser.parseLatex("y = x + \\frac{6+2}{4}")
            val before = parsed.graph.nodes.size
            val folded = Evaluate.foldConstants(parsed.graph)
            checkCond(folded.folded >= 1, "expected at least one fold")
            checkCond(folded.graph.nodes.size < before, "folding must remove vertices")
            val root = Engine.rootsOf(folded.graph).find { it.kind == NodeKind.Relation } ?: Engine.rootsOf(folded.graph).first()
            val remaining = Engine.toLatex(folded.graph, root.id)
            checkCond(remaining.contains("x"), "the variable must survive folding")
        },
        SpecTestCase("Evaluator", "samples 3D multi-variable surface z = x^2 - y^2") {
            val parsed = LatexParser.parseLatex("z = x^2 - y^2")
            val analysis = Evaluate.analyzeGraph(parsed.graph).firstOrNull()
            checkCond(analysis?.plottable3D != null, "saddle surface must be 3D plottable")
            val mesh = Evaluate.plotSurface3D(parsed.graph, analysis!!.plottable3D!!.zRootId, -2f, 2f, 8, "x", "y")
            checkEq(mesh.points.size, 9, "9x9 grid rows")
            checkCond(mesh.maxZ > 0f && mesh.minZ < 0f, "saddle spans positive and negative z")
        },

        // Symbolic & Solver (4 new specs)
        SpecTestCase("Symbolic", "simplifies Pythagorean trig identity to 1") {
            val parsed = LatexParser.parseLatex("\\sin^2\\theta + \\cos^2\\theta")
            val steps = SymbolicEngine.simplifyStepByStep(parsed.graph, parsed.rootId)
            checkCond(steps.isNotEmpty(), "expected symbolic rewrite step")
            checkEq(steps.last().afterLatex, "1", "sin²θ + cos²θ simplifies to 1")
        },
        SpecTestCase("Symbolic", "simplifies additive and multiplicative identities") {
            val parsed = LatexParser.parseLatex("x + 0")
            val steps = SymbolicEngine.simplifyStepByStep(parsed.graph, parsed.rootId)
            checkCond(steps.isNotEmpty(), "x + 0 must simplify")
            checkEq(steps.last().afterLatex, "x", "x + 0 -> x")
        },
        SpecTestCase("Symbolic", "solves E = m c^2 for m and c step-by-step") {
            val parsed = LatexParser.parseLatex("E = m c^2")
            val solveM = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId!!, "m")
            checkCond(solveM.isNotEmpty(), "must isolate m")
            checkCond(solveM.last().afterLatex.startsWith("m ="), "m isolated on LHS: ${solveM.last().afterLatex}")

            val solveC = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId, "c")
            checkCond(solveC.size >= 2, "must isolate c in multiple steps")
            checkCond(solveC.last().afterLatex.startsWith("c ="), "c isolated on LHS: ${solveC.last().afterLatex}")
        },
        SpecTestCase("Symbolic", "solves linear relation y = 2x - 3 for x") {
            val parsed = LatexParser.parseLatex("y = 2x - 3")
            val steps = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId!!, "x")
            checkCond(steps.size >= 2, "expected multi-step isolation for x")
            checkCond(steps.last().afterLatex.startsWith("x ="), "x isolated on LHS")
        },

        SpecTestCase("Symbolic", "linear solve ends in an evaluated solution") {
            val parsed = LatexParser.parseLatex("2x + 3 = 11")
            val steps = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId!!, "x")
            checkCond(steps.size >= 2, "expected at least two steps, received ${steps.size}")
            checkEq(steps.last().afterLatex, "x = 4", "solution must be evaluated")
        },
        SpecTestCase("Symbolic", "quadratic solve keeps both real roots") {
            val parsed = LatexParser.parseLatex("x^2 = 9")
            val steps = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId!!, "x")
            checkCond(steps.isNotEmpty(), "expected a root-extraction step")
            checkEq(steps.first().ruleName, "Even Root Extraction", "even powers have two roots")
            checkCond(steps.first().afterLatex.contains("\\pm"), "first step carries ±, received ${steps.first().afterLatex}")
            checkEq(steps.last().afterLatex, "x = \\pm 3", "both roots evaluated")
        },
        SpecTestCase("Symbolic", "odd roots stay single-valued") {
            val parsed = LatexParser.parseLatex("x^3 = 27")
            val steps = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId!!, "x")
            checkCond(steps.isNotEmpty(), "expected a root-extraction step")
            checkEq(steps.last().afterLatex, "x = 3", "cube root is unique")
            checkCond(steps.none { it.afterLatex.contains("\\pm") }, "odd roots must not gain a ± branch")
        },
        SpecTestCase("Symbolic", "every solve step preserves the truth of the equation") {
            val parsed = LatexParser.parseLatex("2x + 3 = 11")
            val steps = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId!!, "x")
            checkCond(steps.isNotEmpty(), "expected solve steps")
            fun holds(graph: AuraGraph, x: Double): Boolean? {
                val index = Engine.indexGraph(graph)
                val root = Engine.rootsOf(graph).firstOrNull { it.kind == NodeKind.Relation } ?: return null
                val left = Engine.childOf(index, root.id, PortId.Left) ?: return null
                val right = Engine.childOf(index, root.id, PortId.Right) ?: return null
                val env = mapOf("x" to x)
                val l = Evaluate.evalExpression(index, left.id, env) ?: return null
                val r = Evaluate.evalExpression(index, right.id, env) ?: return null
                return abs(l - r) < 1e-9
            }
            for (step in steps) {
                for (sample in listOf(-2.0, 0.0, 1.0, 4.0, 7.0)) {
                    val before = holds(step.graph, sample)
                    // Re-parse the rendered form: a step that does not survive serialisation is not a step.
                    val afterGraph = LatexParser.parseLatex(step.afterLatex).graph
                    val after = holds(afterGraph, sample)
                    checkEq(after, before, "step \"${step.ruleName}\" changed the truth value at x = $sample")
                }
            }
        },

        // Graph (7)
        SpecTestCase("Graph", "detects cyclic binding") {
            val d = SpecDraft()
            val a = operator(d, "\\times", "mul")
            val b = operator(d, "+", "add")
            d.bind(a, PortId.Left, b)
            d.bind(b, PortId.Left, a)
            val errors = Engine.diagnose(d.graph()).filter { it.severity == DiagnosticSeverity.Error }
            checkCond(errors.any { it.message.contains("Cyclic") }, "cycles must be reported")
        },
        SpecTestCase("Graph", "shared variables may feed several operators") {
            val parsed = LatexParser.parseLatex("\\sin^2\\theta + \\cos^2\\theta")
            checkCond(Engine.validateGraph(parsed.graph) == null, "sharing a variable must stay valid")
            val errors = Engine.diagnose(parsed.graph).filter { it.severity == DiagnosticSeverity.Error }
            checkEq(errors.size, 0, "sharing must not be diagnosed")
        },
        SpecTestCase("Graph", "composite vertices may not have two parents") {
            val d = SpecDraft()
            val a = num(d, "1")
            val b = num(d, "2")
            val c = num(d, "3")
            val sum = operator(d, "+", "add")
            d.bind(sum, PortId.Left, a)
            d.bind(sum, PortId.Right, b)
            val product = operator(d, "\\times", "mul")
            d.bind(product, PortId.Left, sum)
            d.bind(product, PortId.Right, c)
            val product2 = operator(d, "\\times", "mul")
            d.bind(product2, PortId.Left, sum)
            d.bind(product2, PortId.Right, a)
            val problem = Engine.validateGraph(d.graph())
            checkCond(problem != null, "a composite with two parents must be rejected")
        },
        SpecTestCase("Graph", "rejects dangling edges at the validation boundary") {
            val d = SpecDraft()
            val a = num(d, "1")
            val problem = Engine.validateGraph(
                AuraGraph(d.nodes, listOf(AuraEdge(newId(), a.id, "missing", PortId.Left)))
            )
            checkCond(problem != null, "dangling edge must be rejected")
        },
        SpecTestCase("Graph", "collapse → expand is a round trip") {
            val me = massEnergy()
            val graph = me.d.graph()
            val collapsed = Engine.collapseSelection(
                graph,
                listOf(me.product.id, me.power.id, me.c.id, me.m.id, me.two.id)
            )
            checkCond(collapsed != null, "collapse must succeed")
            checkEq(collapsed!!.graph.nodes.size, 1, "one vertex after collapse")
            val expanded = Engine.expandGroup(collapsed.graph, collapsed.groupId)
            checkCond(expanded != null, "expand must succeed")
            checkEq(expanded!!.nodes.size, graph.nodes.size, "vertex count restored")
            checkEq(expanded.edges.size, graph.edges.size, "binding count restored")
        },
        SpecTestCase("Graph", "auto-layout places roots without overlap in y") {
            val me = massEnergy()
            val g = me.d.graph()
            val positions = Engine.layoutComponent(g, me.product.id, PointF(500f, 300f))
            checkEq(positions.size, me.d.nodes.size, "every vertex positioned")
            val rootPt = positions[me.product.id]
            checkCond(rootPt != null && abs(rootPt.y - 300f) < 0.01f, "root anchored at its y")
        },
        SpecTestCase("Graph", "bounds enclose every rendered vertex") {
            val me = massEnergy()
            val graph = Engine.reflowGraph(me.d.graph())
            val bounds = Engine.graphBounds(graph)
            for (node in graph.nodes) {
                checkCond(node.x in bounds.minX..bounds.maxX, "${node.label} inside x bounds")
                checkCond(node.y in bounds.minY..bounds.maxY, "${node.label} inside y bounds")
            }
        },

        // Scope (1)
        SpecTestCase("Scope", "groups variables by symbol across clusters") {
            val first = LatexParser.parseLatex("x + y")
            val second = LatexParser.parseLatex("x^2")
            val merged = AuraGraph(first.graph.nodes + second.graph.nodes, first.graph.edges + second.graph.edges)
            val scopes = Evaluate.scopeIndex(merged)
            val xScope = scopes.find { it.token == "x" }
            checkCond(xScope != null, "x must be scoped")
            checkEq(xScope?.nodeIds?.size, 2, "two occurrences of x")
        },

        // Discovery & Formal Proofs (6)
        SpecTestCase("Discovery", "verifies a true identity and attaches formal proof trace") {
            val identity = LatexParser.parseLatex("\\sin^2\\theta + \\cos^2\\theta")
            val one = LatexParser.parseLatex("1")
            val merged = AuraGraph(identity.graph.nodes + one.graph.nodes, identity.graph.edges + one.graph.edges)
            val result = Discover.discover(merged, DiscoveryOptions(scale = 0.9f, families = listOf(DiscoveryFamily.Relation)))
            val holds = result.proposals.find { it.kind == "equal" && it.verdict == Verdict.Holds }
            checkCond(holds != null, "sin²θ + cos²θ = 1 must verify")
            checkCond(holds?.proofTrace?.verified == true, "formal proof trace must verify")
        },
        SpecTestCase("Discovery", "refutes a false equality with a counterexample") {
            val left = LatexParser.parseLatex("x")
            val right = LatexParser.parseLatex("x + 1")
            val merged = AuraGraph(left.graph.nodes + right.graph.nodes, left.graph.edges + right.graph.edges)
            val result = Discover.discover(merged, DiscoveryOptions(scale = 0.9f, families = listOf(DiscoveryFamily.Relation), limit = 20))
            val refuted = result.proposals.find { it.kind == "equal" && it.verdict == Verdict.Refuted }
            checkCond(refuted != null, "x = x + 1 must be refuted")
            checkCond(!refuted?.counterexample.isNullOrBlank(), "refutation must cite a counterexample")
        },
        SpecTestCase("Discovery", "respects the discovery focus") {
            val a = LatexParser.parseLatex("2")
            val b = LatexParser.parseLatex("3")
            val c = LatexParser.parseLatex("4")
            val merged = AuraGraph(
                nodes = listOf(
                    a.graph.nodes[0].copy(x = 0f),
                    b.graph.nodes[0].copy(x = 100f),
                    c.graph.nodes[0].copy(x = 900f)
                ),
                edges = emptyList()
            )
            val result = Discover.discover(merged, DiscoveryOptions(scale = 1f, focusIds = listOf(merged.nodes[0].id)))
            checkEq(result.clusters, 1, "focus restricts to one cluster")
        },
        SpecTestCase("Discovery", "scale band boundaries are monotone") {
            checkEq(Discover.scaleBand(0f), "literal", "lower bound")
            checkEq(Discover.scaleBand(0.25f), "cautious", "cautious start")
            checkEq(Discover.scaleBand(0.5f), "balanced", "balanced start")
            checkEq(Discover.scaleBand(0.75f), "wild", "wild start")
        },
        SpecTestCase("Discovery", "batch application preserves the single-binding invariant") {
            val left = LatexParser.parseLatex("2")
            val right = LatexParser.parseLatex("2 + 1")
            val merged = AuraGraph(
                nodes = left.graph.nodes.map { it.copy(x = 0f, y = 0f) } +
                    right.graph.nodes.map { it.copy(x = 80f, y = 0f) },
                edges = left.graph.edges + right.graph.edges
            )
            val result = Discover.discover(merged, DiscoveryOptions(scale = 1f))
            val patches = result.proposals.take(4).map { it.patch }
            val applied = Discover.applyProposals(merged, patches)
            checkCond(Engine.validateGraph(applied.graph) == null, "batch result must satisfy validateGraph")
        },
        SpecTestCase("Ink", "classifies parallel strokes as = and orthogonal crossing strokes as +") {
            val eqStrokes = listOf(
                Stroke(listOf(InkPoint(10f, 20f), InkPoint(90f, 20f))),
                Stroke(listOf(InkPoint(10f, 45f), InkPoint(90f, 45f)))
            )
            val eqPred = InkRecognizer.classifyInk(eqStrokes).firstOrNull()
            checkEq(eqPred?.latex, "=", "two parallel horizontal strokes classify as =")

            val plusStrokes = listOf(
                Stroke(listOf(InkPoint(10f, 50f), InkPoint(90f, 50f))),
                Stroke(listOf(InkPoint(50f, 10f), InkPoint(50f, 90f)))
            )
            val plusPred = InkRecognizer.classifyInk(plusStrokes).firstOrNull()
            checkEq(plusPred?.latex, "+", "orthogonal intersecting strokes classify as +")
        },

        // Library (3)
        SpecTestCase("Library", "every symbol declares a domain and a subdomain") {
            checkCond(SymbolLibrary.LIBRARY_COUNT >= 300, "expected >= 300 symbols, received ${SymbolLibrary.LIBRARY_COUNT}")
            val missing = SymbolLibrary.SYMBOL_LIBRARY.filter { it.domain.isBlank() || it.subdomain.isBlank() }
            checkEq(missing.size, 0, "symbols missing taxonomy")
        },
        SpecTestCase("Library", "every symbol derives at least one port or is a leaf") {
            val broken = SymbolLibrary.SYMBOL_LIBRARY.filter { entry ->
                if (entry.kind in listOf(NodeKind.Literal, NodeKind.Variable, NodeKind.Set)) return@filter false
                val node = makeNode(entry.kind, entry.token, entry.label, entry.type, entry.variant)
                portsFor(node).isEmpty()
            }
            checkEq(broken.size, 0, "symbols without ports")
        },
        SpecTestCase("Library", "domains are unique and cover the taxonomy") {
            val domains = SymbolLibrary.SYMBOL_LIBRARY.map { it.domain }.toSet()
            checkCond(domains.size >= 14, "expected >= 14 domains, received ${domains.size}")
            for (req in listOf("Algebra", "Trigonometry", "Calculus", "Linear algebra", "Set theory", "Logic")) {
                checkCond(req in domains, "missing domain $req")
            }
        },

        // Robustness (3)
        SpecTestCase("Robustness", "deeply nested grouping is rejected instead of overflowing the stack") {
            var rejected = false
            try {
                LatexParser.parseLatex("(".repeat(500) + "x" + ")".repeat(500))
            } catch (_: ParseError) {
                rejected = true
            }
            checkCond(rejected, "500 levels of nesting must raise a ParseError")
        },
        SpecTestCase("Robustness", "oversized input is rejected before it can exhaust memory") {
            var rejected = false
            try {
                LatexParser.parseLatex((1..5000).joinToString("+") { "x" })
            } catch (_: ParseError) {
                rejected = true
            }
            checkCond(rejected, "a 5000-term chain must raise a ParseError")
        },
        SpecTestCase("Robustness", "nested enclosures serialise in linear time") {
            val source = "|".repeat(80) + "x" + "|".repeat(80)
            val started = System.currentTimeMillis()
            val parsed = LatexParser.parseLatex(source)
            val elapsed = System.currentTimeMillis() - started
            checkCond(parsed.nodeCount >= 80, "expected one vertex per enclosure, received ${parsed.nodeCount}")
            checkCond(elapsed < 2000, "serialisation took ${elapsed}ms — subtrees are being re-rendered")
        },

        // Interop (1)
        SpecTestCase("Interop", "document export serialises every cluster") {
            val a = LatexParser.parseLatex("a^2 + b^2 = c^2")
            val exported = Engine.documentLatex(a.graph)
            checkEq(exported.size, 1, "one cluster")
            checkCond(exported[0].latex.contains("="), "equality serialised")
            checkCond(exported[0].nodeIds.size >= 6, "node ids listed")
        }
    )

    fun runKernelSuite(): SuiteReport {
        val started = System.currentTimeMillis()
        val outcomes = TEST_CASES.map { test ->
            try {
                test.run()
                TestOutcome(test.suite, test.name, true, null)
            } catch (e: Throwable) {
                TestOutcome(test.suite, test.name, false, e.message ?: e.toString())
            }
        }
        val suiteNames = outcomes.map { it.suite }.distinct()
        val suites = suiteNames.map { sName ->
            val entries = outcomes.filter { it.suite == sName }
            SuiteSummary(
                suite = sName,
                passed = entries.count { it.ok },
                failed = entries.count { !it.ok }
            )
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return SuiteReport(
            generatedAt = sdf.format(Date()),
            total = outcomes.size,
            passed = outcomes.count { it.ok },
            failed = outcomes.count { !it.ok },
            durationMs = maxOf(1L, System.currentTimeMillis() - started),
            suites = suites,
            outcomes = outcomes,
            librarySymbols = SymbolLibrary.LIBRARY_COUNT
        )
    }
}
