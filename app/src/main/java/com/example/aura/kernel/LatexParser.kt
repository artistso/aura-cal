package com.example.aura.kernel

import kotlin.math.min

/**
 * Tier-2 structural recovery: LaTeX tokens (from handwriting recognition or
 * typed input) are lexed and parsed straight into typed hypergraph vertices and bindings.
 */

class ParseError(message: String) : Exception(message)

enum class TokenType {
    Num, Ident, Cmd, Op, Rel, LParen, RParen, LBrace, RBrace, Caret, Underscore, LBracket, RBracket, Comma, Eof
}

data class LatexToken(val type: TokenType, val value: String)

data class ParseResult(
    val graph: AuraGraph,
    val rootId: String?,
    val normalized: String,
    val nodeCount: Int,
    val edgeCount: Int
)

object LatexParser {
    private val GREEK = mapOf(
        "alpha" to "\\alpha", "beta" to "\\beta", "gamma" to "\\gamma", "delta" to "\\delta",
        "epsilon" to "\\epsilon", "varepsilon" to "\\varepsilon", "zeta" to "\\zeta", "eta" to "\\eta",
        "theta" to "\\theta", "vartheta" to "\\vartheta", "iota" to "\\iota", "kappa" to "\\kappa",
        "lambda" to "\\lambda", "mu" to "\\mu", "nu" to "\\nu", "xi" to "\\xi",
        "pi" to "\\pi", "rho" to "\\rho", "sigma" to "\\sigma", "tau" to "\\tau",
        "upsilon" to "\\upsilon", "phi" to "\\phi", "varphi" to "\\varphi", "chi" to "\\chi",
        "psi" to "\\psi", "omega" to "\\omega",
        "Gamma" to "\\Gamma", "Delta" to "\\Delta", "Theta" to "\\Theta", "Lambda" to "\\Lambda",
        "Xi" to "\\Xi", "Pi" to "\\Pi", "Sigma" to "\\Sigma", "Upsilon" to "\\Upsilon",
        "Phi" to "\\Phi", "Psi" to "\\Psi", "Omega" to "\\Omega"
    )

    private data class OpSpec(val token: String, val variant: String)

    private val RELATION_COMMANDS = mapOf(
        "=" to OpSpec("=", "equal"),
        "\\approx" to OpSpec("\\approx", "approx"),
        "\\neq" to OpSpec("\\neq", "neq"),
        "\\le" to OpSpec("\\le", "leq"),
        "\\leq" to OpSpec("\\le", "leq"),
        "\\ge" to OpSpec("\\ge", "geq"),
        "\\geq" to OpSpec("\\ge", "geq"),
        "\\to" to OpSpec("\\to", "mapsto"),
        "\\mapsto" to OpSpec("\\to", "mapsto"),
        "\\in" to OpSpec("\\in", "in"),
        "\\subset" to OpSpec("\\subset", "subset"),
        "<" to OpSpec("<", "leq"),
        ">" to OpSpec(">", "geq")
    )

    private val ARITHMETIC = mapOf(
        "+" to OpSpec("+", "add"),
        "-" to OpSpec("-", "sub"),
        "*" to OpSpec("\\times", "mul"),
        "\\cdot" to OpSpec("\\times", "mul"),
        "\\times" to OpSpec("\\times", "mul"),
        "\\div" to OpSpec("\\div", "mul")
    )

    private val FUNCTIONS = setOf(
        "sin", "cos", "tan", "sec", "csc", "cot",
        "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
        "ln", "log", "exp"
    )

    private val IGNORED = setOf("left", "right", "displaystyle", "limits", "!", " ", ",")

    fun tokenizeLatex(source: String): List<LatexToken> {
        val tokens = ArrayList<LatexToken>()
        val input = source.replace(Regex("\\s+"), " ").trim()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch == ' ') {
                i++
                continue
            }
            if (ch.isDigit() || (ch == '.' && input.getOrNull(i + 1)?.isDigit() == true)) {
                val sb = StringBuilder()
                while (i < input.length && (input[i].isDigit() || input[i] == '.')) {
                    sb.append(input[i])
                    i++
                }
                tokens.add(LatexToken(TokenType.Num, sb.toString()))
                continue
            }
            if (ch == '\\') {
                val sb = StringBuilder()
                i++
                while (i < input.length && input[i].isLetter()) {
                    sb.append(input[i])
                    i++
                }
                val name = sb.toString()
                if (name.isEmpty()) {
                    val nextChar = input.getOrNull(i)?.toString() ?: ""
                    i++
                    if (nextChar in IGNORED) continue
                    tokens.add(LatexToken(TokenType.Cmd, "\\$nextChar"))
                    continue
                }
                if (name in listOf("mathbb", "mathbf", "mathcal", "operatorname")) {
                    var inner = ""
                    if (input.getOrNull(i) == '{') {
                        val end = input.indexOf('}', i)
                        if (end == -1) throw ParseError("Unclosed \\$name{...}")
                        inner = input.substring(i + 1, end)
                        i = end + 1
                    } else {
                        inner = input.getOrNull(i)?.toString() ?: "R"
                        i++
                    }
                    if (name == "operatorname" && inner in FUNCTIONS) {
                        tokens.add(LatexToken(TokenType.Cmd, "\\$inner"))
                    } else {
                        tokens.add(LatexToken(TokenType.Cmd, "\\$name{$inner}"))
                    }
                    continue
                }
                if (name in listOf("frac", "tfrac", "dfrac")) {
                    tokens.add(LatexToken(TokenType.Cmd, "\\frac"))
                    continue
                }
                if (name == "sqrt" || name in listOf("int", "sum", "prod") || name in FUNCTIONS) {
                    tokens.add(LatexToken(TokenType.Cmd, "\\$name"))
                    continue
                }
                if (name == "pi" || name == "infty" || GREEK.containsKey(name)) {
                    tokens.add(
                        LatexToken(
                            TokenType.Cmd,
                            if (name == "infty") "\\infty" else (GREEK[name] ?: "\\$name")
                        )
                    )
                    continue
                }
                val fullCmd = "\\$name"
                if (RELATION_COMMANDS.containsKey(fullCmd)) {
                    tokens.add(LatexToken(TokenType.Rel, fullCmd))
                    continue
                }
                if (ARITHMETIC.containsKey(fullCmd)) {
                    tokens.add(LatexToken(TokenType.Op, fullCmd))
                    continue
                }
                if (name in IGNORED) continue
                tokens.add(LatexToken(TokenType.Cmd, fullCmd))
                continue
            }
            if (ch.isLetter()) {
                tokens.add(LatexToken(TokenType.Ident, ch.toString()))
                i++
                continue
            }
            when (ch) {
                '^' -> tokens.add(LatexToken(TokenType.Caret, "^"))
                '_' -> tokens.add(LatexToken(TokenType.Underscore, "_"))
                '(' -> tokens.add(LatexToken(TokenType.LParen, "("))
                ')' -> tokens.add(LatexToken(TokenType.RParen, ")"))
                '{' -> tokens.add(LatexToken(TokenType.LBrace, "{"))
                '}' -> tokens.add(LatexToken(TokenType.RBrace, "}"))
                ',' -> tokens.add(LatexToken(TokenType.Comma, ","))
                '[' -> tokens.add(LatexToken(TokenType.LBracket, "["))
                ']' -> tokens.add(LatexToken(TokenType.RBracket, "]"))
                '!', '/' -> tokens.add(LatexToken(TokenType.Op, ch.toString()))
                else -> {
                    val s = ch.toString()
                    when {
                        RELATION_COMMANDS.containsKey(s) -> tokens.add(LatexToken(TokenType.Rel, s))
                        ARITHMETIC.containsKey(s) -> tokens.add(LatexToken(TokenType.Op, s))
                        else -> tokens.add(LatexToken(TokenType.Ident, s))
                    }
                }
            }
            i++
        }
        tokens.add(LatexToken(TokenType.Eof, ""))
        return tokens
    }

    private class GraphBuilder {
        val nodes = ArrayList<AuraNode>()
        val edges = ArrayList<AuraEdge>()
        // Maps leaf key (kind + token) -> shared node so repeated variables in an expression share a leaf vertex
        private val sharedLeaves = LinkedHashMap<String, AuraNode>()

        fun add(
            kind: NodeKind,
            token: String,
            label: String,
            type: MathType,
            variant: String? = null,
            shareVariable: Boolean = true
        ): AuraNode {
            if (shareVariable && kind == NodeKind.Variable) {
                val existing = sharedLeaves[token]
                if (existing != null) return existing
            }
            val node = makeNode(kind = kind, token = token, label = label, type = type, variant = variant)
            nodes.add(node)
            if (shareVariable && kind == NodeKind.Variable) {
                sharedLeaves[token] = node
            }
            return node
        }

        fun bind(parent: AuraNode, port: PortId, child: AuraNode?) {
            if (child == null) return
            edges.add(
                AuraEdge(
                    id = newId(),
                    source = parent.id,
                    target = child.id,
                    port = port,
                    role = port.wireName
                )
            )
        }

        fun build(): AuraGraph = AuraGraph(nodes, edges)
    }

    private class ParserImpl(private val tokens: List<LatexToken>) {
        private var position = 0
        private val builder = GraphBuilder()
        private val pendingFunctionArgs = ArrayList<AuraNode>()

        fun parse(): Pair<AuraGraph, String?> {
            val root = parseRelation()
            return builder.build() to root?.id
        }

        private fun peek(offset: Int = 0): LatexToken =
            tokens[min(position + offset, tokens.size - 1)]

        private fun next(): LatexToken {
            val t = peek()
            position++
            return t
        }

        private fun accept(type: TokenType): Boolean {
            if (peek().type == type) {
                position++
                return true
            }
            return false
        }

        private fun expect(type: TokenType, what: String): LatexToken {
            if (peek().type != type) {
                throw ParseError("Expected $what but found \"${peek().value.ifEmpty { "end of input" }}\".")
            }
            return next()
        }

        private fun braced(block: () -> AuraNode?): AuraNode? {
            return if (accept(TokenType.LBrace)) {
                val node = block()
                expect(TokenType.RBrace, "}")
                node
            } else {
                block()
            }
        }

        private fun startsPrimary(token: LatexToken): Boolean {
            if (token.type == TokenType.Num || token.type == TokenType.Ident ||
                token.type == TokenType.LParen || token.type == TokenType.LBrace
            ) return true
            if (token.type == TokenType.Cmd) {
                return token.value.startsWith("\\")
            }
            return false
        }

        private fun parseRelation(): AuraNode? {
            var left = parseAdditive()
            while (peek().type == TokenType.Rel && left != null) {
                val spec = RELATION_COMMANDS[next().value] ?: continue
                val right = parseAdditive()
                val relation = builder.add(
                    kind = NodeKind.Relation,
                    token = spec.token,
                    label = relationLabel(spec.variant),
                    type = MathType.Relation,
                    variant = spec.variant
                )
                builder.bind(relation, PortId.Left, left)
                builder.bind(relation, PortId.Right, right)
                left = relation
            }
            return left
        }

        private fun parseAdditive(): AuraNode? {
            var left = parseMultiplicative()
            while (peek().type == TokenType.Op && peek().value in listOf("+", "-") && left != null) {
                val spec = ARITHMETIC[next().value]!!
                val right = parseMultiplicative()
                val op = builder.add(
                    kind = NodeKind.Operator,
                    token = spec.token,
                    label = if (spec.variant == "add") "Addition" else "Subtraction",
                    type = MathType.Operator,
                    variant = spec.variant
                )
                builder.bind(op, PortId.Left, left)
                builder.bind(op, PortId.Right, right)
                left = op
            }
            return left
        }

        private fun parseMultiplicative(): AuraNode? {
            var left = parsePower()
            while (true) {
                val token = peek()
                if (token.type == TokenType.Op && token.value == "/" && left != null) {
                    next()
                    val den = parsePower()
                    val frac = builder.add(
                        kind = NodeKind.Operator,
                        token = "\\frac",
                        label = "Fraction",
                        type = MathType.Operator,
                        variant = "frac"
                    )
                    builder.bind(frac, PortId.Numerator, left)
                    builder.bind(frac, PortId.Denominator, den)
                    left = frac
                    continue
                }
                if (token.type == TokenType.Op && token.value in listOf("*", "\\cdot", "\\times", "\\div") && left != null) {
                    val spec = ARITHMETIC[next().value] ?: OpSpec("\\times", "mul")
                    val right = parsePower()
                    val op = builder.add(
                        kind = NodeKind.Operator,
                        token = spec.token,
                        label = "Product",
                        type = MathType.Operator,
                        variant = spec.variant
                    )
                    builder.bind(op, PortId.Left, left)
                    builder.bind(op, PortId.Right, right)
                    left = op
                    continue
                }
                if (startsPrimary(token) && left != null) {
                    val right = parsePower()
                    val op = builder.add(
                        kind = NodeKind.Operator,
                        token = "\\,",
                        label = "Juxtaposition",
                        type = MathType.Operator,
                        variant = "juxta"
                    )
                    builder.bind(op, PortId.Left, left)
                    builder.bind(op, PortId.Right, right)
                    left = op
                    continue
                }
                break
            }
            return left
        }

        private fun attachPendingArguments() {
            if (pendingFunctionArgs.isEmpty()) return
            val pendings = ArrayList(pendingFunctionArgs)
            pendingFunctionArgs.clear()
            if (!startsPrimary(peek())) return
            val arg = parsePower() ?: return
            val target = pendings.last()
            builder.bind(target, PortId.Arg, arg)
        }

        private fun parsePower(): AuraNode? {
            var base = parsePostfix()
            if (peek().type == TokenType.Caret && base != null) {
                next()
                val exponent = braced { parseRelation() } ?: parsePostfix()
                val power = builder.add(
                    kind = NodeKind.Operator,
                    token = "^",
                    label = "Power",
                    type = MathType.Operator,
                    variant = "power"
                )
                builder.bind(power, PortId.Base, base)
                builder.bind(power, PortId.Exponent, exponent)
                base = power
                attachPendingArguments()
            }
            return base
        }

        private fun parsePostfix(): AuraNode? {
            var base = parsePrimary()
            while (base != null && peek().type == TokenType.Op && peek().value == "!") {
                next()
                val fact = builder.add(
                    kind = NodeKind.Operator,
                    token = "!",
                    label = "Factorial",
                    type = MathType.Operator,
                    variant = "fact"
                )
                builder.bind(fact, PortId.Arg, base)
                base = fact
            }
            while (base != null && peek().type == TokenType.Underscore) {
                next()
                val idx = braced { parsePrimary() } ?: parsePrimary()
                if (base.kind in listOf(NodeKind.Literal, NodeKind.Variable, NodeKind.Set, NodeKind.Group)) {
                    builder.bind(base, PortId.Subscript, idx)
                }
            }
            return base
        }

        private fun fromLibrary(entry: SymbolEntry): AuraNode {
            val spec = entry.variant ?: ""
            if (entry.kind == NodeKind.Operator && (entry.token == "\\cup" || entry.token == "\\cap") &&
                (peek().type == TokenType.Underscore || peek().type == TokenType.Caret)
            ) {
                val bigNode = builder.add(
                    kind = NodeKind.Container,
                    token = if (entry.token == "\\cup") "\\bigcup" else "\\bigcap",
                    label = if (entry.token == "\\cup") "Arbitrary union" else "Arbitrary intersection",
                    type = MathType.Expression,
                    variant = if (entry.token == "\\cup") "bigcup" else "bigcap"
                )
                for (marker in listOf(TokenType.Underscore, TokenType.Caret)) {
                    if (peek().type == marker) {
                        next()
                        val bound = braced { parseRelation() }
                        builder.bind(bigNode, if (marker == TokenType.Underscore) PortId.Lower else PortId.Upper, bound)
                    }
                }
                builder.bind(bigNode, PortId.Body, parseMultiplicative())
                return bigNode
            }

            val node = builder.add(
                kind = entry.kind,
                token = entry.token,
                label = entry.label,
                type = entry.type,
                variant = entry.variant,
                shareVariable = false
            )

            if (entry.kind == NodeKind.Function) {
                var exponent: AuraNode? = null
                if (peek().type == TokenType.Caret) {
                    next()
                    exponent = braced { parsePrimary() } ?: parsePrimary()
                }
                var arg: AuraNode? = null
                when {
                    peek().type == TokenType.LParen -> {
                        next()
                        arg = parseRelation()
                        accept(TokenType.RParen)
                    }
                    peek().type == TokenType.LBrace -> {
                        arg = braced { parseRelation() }
                    }
                    startsPrimary(peek()) -> {
                        arg = parsePower()
                    }
                }
                builder.bind(node, PortId.Arg, arg)
                if (exponent == null) return node
                if (arg == null) pendingFunctionArgs.add(node)
                val power = builder.add(
                    kind = NodeKind.Operator,
                    token = "^",
                    label = "Power",
                    type = MathType.Operator,
                    variant = "power"
                )
                builder.bind(power, PortId.Base, node)
                builder.bind(power, PortId.Exponent, exponent)
                return power
            }

            if (entry.kind == NodeKind.Container &&
                (spec in Ontology.ACCENT_VARIANTS || spec in Ontology.MATRIX_VARIANTS || spec in Ontology.ENCLOSURE_VARIANTS)
            ) {
                val body = braced { parseRelation() }
                builder.bind(node, PortId.Body, body)
                return node
            }

            if (entry.kind == NodeKind.Operator && spec == "binom") {
                val upper = braced { parseRelation() }
                val lower = braced { parseRelation() }
                builder.bind(node, PortId.Numerator, upper)
                builder.bind(node, PortId.Denominator, lower)
                return node
            }

            if (entry.kind == NodeKind.Container && spec in Ontology.BIG_VARIANTS) {
                for (marker in listOf(TokenType.Underscore, TokenType.Caret)) {
                    if (peek().type == marker) {
                        next()
                        val bound = braced { parseRelation() } ?: parsePrimary()
                        builder.bind(node, if (marker == TokenType.Underscore) PortId.Lower else PortId.Upper, bound)
                    }
                }
                val body = parseMultiplicative()
                builder.bind(node, PortId.Body, body)
                return node
            }

            if (entry.kind == NodeKind.Operator && spec in listOf("abs", "norm", "floor", "ceil")) {
                val arg = if (peek().type == TokenType.LBrace) braced { parseRelation() } else parsePower()
                builder.bind(node, PortId.Arg, arg)
                return node
            }

            if (entry.kind == NodeKind.Operator && spec in Ontology.PREFIX_VARIANTS) {
                val arg = if (startsPrimary(peek())) parsePower() else null
                builder.bind(node, PortId.Arg, arg)
                return node
            }

            return node
        }

        private fun parsePrimary(): AuraNode? {
            val token = peek()
            if (token.type == TokenType.Op && (token.value == "-" || token.value == "+")) {
                next()
                val operand = parsePower()
                if (token.value == "+") return operand
                val neg = builder.add(
                    kind = NodeKind.Operator,
                    token = "\\neg",
                    label = "Negation",
                    type = MathType.Operator,
                    variant = "neg"
                )
                builder.bind(neg, PortId.Arg, operand)
                return neg
            }
            if (token.type == TokenType.Num) {
                next()
                return builder.add(
                    kind = NodeKind.Literal,
                    token = token.value,
                    label = "Literal ${token.value}",
                    type = MathType.Scalar
                )
            }
            if (token.type == TokenType.LParen) {
                next()
                val inner = parseRelation()
                accept(TokenType.RParen)
                return inner
            }
            if (token.type == TokenType.LBrace) {
                next()
                val inner = parseRelation()
                expect(TokenType.RBrace, "}")
                return inner
            }
            if (token.type == TokenType.Ident) {
                next()
                val ch = token.value
                if (peek().type == TokenType.LParen) {
                    val fn = builder.add(
                        kind = NodeKind.Function,
                        token = ch,
                        label = "Function $ch",
                        type = MathType.Function,
                        variant = ch
                    )
                    next()
                    val arg = parseRelation()
                    accept(TokenType.RParen)
                    builder.bind(fn, PortId.Arg, arg)
                    return fn
                }
                val hasSub = peek().type == TokenType.Underscore
                return builder.add(
                    kind = NodeKind.Variable,
                    token = ch,
                    label = "Variable $ch",
                    type = MathType.Variable,
                    shareVariable = !hasSub
                )
            }
            if (token.type == TokenType.Cmd) {
                next()
                val command = token.value
                if (command == "\\frac") {
                    val num = braced { parseRelation() }
                    val den = braced { parseRelation() }
                    val frac = builder.add(
                        kind = NodeKind.Operator,
                        token = "\\frac",
                        label = "Fraction",
                        type = MathType.Operator,
                        variant = "frac"
                    )
                    builder.bind(frac, PortId.Numerator, num)
                    builder.bind(frac, PortId.Denominator, den)
                    return frac
                }
                if (command == "\\sqrt") {
                    var idx: AuraNode? = null
                    if (accept(TokenType.LBracket)) {
                        idx = parseRelation()
                        accept(TokenType.RBracket)
                    }
                    val body = braced { parseRelation() }
                    val radical = builder.add(
                        kind = NodeKind.Container,
                        token = if (idx != null) "\\sqrt[\\phantom{n}]{\\phantom{x}}" else "\\sqrt{\\phantom{x}}",
                        label = if (idx != null) "nth root" else "Square root",
                        type = MathType.Expression,
                        variant = if (idx != null) "root" else "sqrt"
                    )
                    builder.bind(radical, PortId.Upper, idx)
                    builder.bind(radical, PortId.Body, body)
                    return radical
                }
                if (command in listOf("\\int", "\\sum", "\\prod")) {
                    val container = builder.add(
                        kind = NodeKind.Container,
                        token = command,
                        label = if (command == "\\int") "Integral" else if (command == "\\prod") "Product" else "Summation",
                        type = MathType.Expression,
                        variant = command.removePrefix("\\")
                    )
                    for (marker in listOf(TokenType.Underscore, TokenType.Caret)) {
                        if (peek().type == marker) {
                            next()
                            val bound = braced { parseRelation() } ?: parsePrimary()
                            builder.bind(container, if (marker == TokenType.Underscore) PortId.Lower else PortId.Upper, bound)
                        }
                    }
                    val body = parseMultiplicative()
                    builder.bind(container, PortId.Body, body)
                    return container
                }
                val fnName = command.removePrefix("\\")
                if (fnName in FUNCTIONS) {
                    val fn = builder.add(
                        kind = NodeKind.Function,
                        token = command,
                        label = fnName,
                        type = MathType.Function,
                        variant = fnName
                    )
                    var exponent: AuraNode? = null
                    if (peek().type == TokenType.Caret) {
                        next()
                        exponent = braced { parsePrimary() } ?: parsePrimary()
                    }
                    var arg: AuraNode? = null
                    when {
                        peek().type == TokenType.LParen -> {
                            next()
                            arg = parseRelation()
                            accept(TokenType.RParen)
                        }
                        peek().type == TokenType.LBrace -> {
                            arg = braced { parseRelation() }
                        }
                        startsPrimary(peek()) -> {
                            arg = parsePower()
                        }
                    }
                    builder.bind(fn, PortId.Arg, arg)
                    if (exponent == null) return fn
                    if (arg == null) pendingFunctionArgs.add(fn)
                    val power = builder.add(
                        kind = NodeKind.Operator,
                        token = "^",
                        label = "Power",
                        type = MathType.Operator,
                        variant = "power"
                    )
                    builder.bind(power, PortId.Base, fn)
                    builder.bind(power, PortId.Exponent, exponent)
                    return power
                }
                if (command.startsWith("\\mathbb")) {
                    val inner = command.substringAfter("{", "R").substringBefore("}")
                    return builder.add(
                        kind = NodeKind.Set,
                        token = "\\mathbb{${inner.ifEmpty { "R" }}}",
                        label = "Set $inner",
                        type = MathType.Set
                    )
                }
                if (command == "\\infty") {
                    return builder.add(NodeKind.Literal, "\\infty", "Infinity", MathType.Scalar)
                }
                if (command == "\\pi") {
                    return builder.add(NodeKind.Literal, "\\pi", "π", MathType.Scalar)
                }
                val libEntry = SymbolLibrary.LIBRARY_BY_COMMAND[command]
                if (libEntry != null && libEntry.kind != NodeKind.Variable) {
                    return fromLibrary(libEntry)
                }
                val hasSub = peek().type == TokenType.Underscore
                return builder.add(
                    kind = NodeKind.Variable,
                    token = command,
                    label = fnName,
                    type = MathType.Variable,
                    shareVariable = !hasSub
                )
            }
            next()
            return null
        }

        private fun relationLabel(variant: String): String = when (variant) {
            "equal" -> "Equality"
            "approx" -> "Approximation"
            "mapsto" -> "Mapping"
            "in" -> "Membership"
            "subset" -> "Subset"
            "leq", "geq" -> "Order"
            else -> "Relation"
        }
    }

    fun parseLatex(source: String): ParseResult {
        if (source.isBlank()) throw ParseError("Nothing to parse.")
        val tokens = tokenizeLatex(source)
        val parser = ParserImpl(tokens)
        val (graph, rootId) = parser.parse()
        if (graph.nodes.isEmpty()) throw ParseError("No mathematical tokens were recognised.")
        val index = Engine.indexGraph(graph)
        val normalized = if (rootId != null) Engine.toLatex(graph, rootId, index) else ""
        return ParseResult(
            graph = graph,
            rootId = rootId,
            normalized = normalized,
            nodeCount = graph.nodes.size,
            edgeCount = graph.edges.size
        )
    }
}
