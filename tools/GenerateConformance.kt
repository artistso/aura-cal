package conformance

import com.example.aura.kernel.*
import java.io.File

/**
 * Generates the AGF conformance corpus from the reference (Kotlin) implementation.
 *
 * The corpus is committed, so the Python implementation is tested against frozen
 * expectations rather than against Kotlin at test time.
 *
 *   kotlinc app/src/main/java/com/example/aura/kernel/[asterisk].kt tools/GenerateConformance.kt
 *       -include-runtime -d /tmp/gen.jar
 *   java -cp /tmp/gen.jar conformance.GenerateConformanceKt
 *
 * Writes docs/conformance/cases.json and docs/conformance/rewrites.json.
 */

private class Json {
    val sb = StringBuilder()

    fun str(value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    fun field(name: String, value: String, comma: Boolean = true) {
        str(name); sb.append(": "); str(value); if (comma) sb.append(", ")
    }

    fun fieldNum(name: String, value: Double, comma: Boolean = true) {
        str(name); sb.append(": "); sb.append(value); if (comma) sb.append(", ")
    }

    fun fieldNull(name: String, comma: Boolean = true) {
        str(name); sb.append(": null"); if (comma) sb.append(", ")
    }
}

private fun encodeGraph(graph: AuraGraph): String {
    val sb = StringBuilder()
    sb.append("{\"nodes\": [")
    graph.nodes.forEachIndexed { i, n ->
        if (i > 0) sb.append(", ")
        sb.append("{\"id\": "); Json().also { it.str(n.id); sb.append(it.sb) }
        sb.append(", \"kind\": \"").append(n.kind.wireName)
        sb.append("\", \"type\": \"").append(n.type.name)
        sb.append("\", \"token\": "); Json().also { it.str(n.token); sb.append(it.sb) }
        sb.append(", \"label\": "); Json().also { it.str(n.label); sb.append(it.sb) }
        if (n.variant != null) {
            sb.append(", \"variant\": "); Json().also { it.str(n.variant!!); sb.append(it.sb) }
        }
        sb.append("}")
    }
    sb.append("], \"edges\": [")
    graph.edges.forEachIndexed { i, e ->
        if (i > 0) sb.append(", ")
        sb.append("{\"id\": \"").append(e.id).append("\"")
        sb.append(", \"source\": \"").append(e.source).append("\"")
        sb.append(", \"port\": \"").append(e.port.wireName).append("\"")
        sb.append(", \"target\": \"").append(e.target).append("\"")
        sb.append(", \"role\": \"").append(e.role).append("\"}")
    }
    sb.append("]}")
    return sb.toString()
}

private fun typesOf(graph: AuraGraph): Map<String, String> {
    val index = Engine.indexGraph(graph)
    val cache = LinkedHashMap<String, MathType>()
    return graph.nodes.associate { it.id to Engine.effectiveType(index, it.id, cache).name }
}

private fun valueOf(graph: AuraGraph, rootId: String?): Double? {
    rootId ?: return null
    val index = Engine.indexGraph(graph)
    val value = Evaluate.evalExpression(index, rootId) ?: return null
    return if (value.isFinite()) value else null
}

private val CASES = listOf(
    "a^2 + b^2 = c^2",
    "\\frac{6+2}{4}",
    "2 + 3 \\times 4",
    "E = mc^2",
    "\\sin^2\\theta + \\cos^2\\theta = 1",
    "\\sqrt{x^2 + 1}",
    "2x + 3 = 11",
    "x^2 = 9",
    "\\int_0^1 x^2 \\, dx",
    "\\sum_{n=1}^{10} n",
    "|x - 3| = 4",
    "\\frac{a}{b} = c",
    "3(x + 2) = 15",
    "y = 2x + 1",
    "z = x^2 + y^2",
    "\\ln(e^x) = x",
    "e^{i\\pi} + 1 = 0",
    "\\sqrt[3]{27}",
    "5!",
    "\\binom{6}{2}",
    "\\sin(\\pi)",
    "\\mathbb{R}",
    "x + 0",
    "\\frac{-b + \\sqrt{b^2 - 4ac}}{2a}",
    "x^{2n+1}",
    "x^2 + 2x + 1",
    "|x - 3|",
    "2^10",
    "|-3|",
    "1 \\in \\mathbb{R}"
)

private val SOLVE_CASES = listOf(
    "2x + 3 = 11" to "x",
    "x^2 = 9" to "x",
    "x^3 = 27" to "x",
    "3x = 12" to "x",
    "\\frac{a}{b} = c" to "a"
)

fun main() {
    val outDir = File("docs/conformance")
    outDir.mkdirs()

    // ---- cases.json -------------------------------------------------------
    val cases = StringBuilder()
    cases.append("{\n  \"agf\": \"0.1\",\n")
    cases.append("  \"generated_by\": \"tools/GenerateConformance.kt (reference Kotlin implementation)\",\n")
    cases.append("  \"cases\": [\n")
    CASES.forEachIndexed { caseIdx, source ->
        val parsed = LatexParser.parseLatex(source)
        val diagnostics = Engine.diagnose(parsed.graph)
        val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.Error }
        val value = valueOf(parsed.graph, parsed.rootId)

        cases.append("    {\n")
        cases.append("      \"name\": \"case_${caseIdx}\",\n")
        cases.append("      \"source\": "); Json().also { it.str(source); cases.append(it.sb) }; cases.append(",\n")
        cases.append("      \"root\": "); if (parsed.rootId == null) cases.append("null") else Json().also { it.str(parsed.rootId!!); cases.append(it.sb) }; cases.append(",\n")
        cases.append("      \"graph\": ").append(encodeGraph(parsed.graph)).append(",\n")
        cases.append("      \"types\": {")
        typesOf(parsed.graph).entries.forEachIndexed { i, e ->
            if (i > 0) cases.append(", ")
            Json().also { it.str(e.key); cases.append(it.sb) }
            cases.append(": \"").append(e.value).append("\"")
        }
        cases.append("},\n")
        cases.append("      \"value\": "); if (value == null) cases.append("null") else cases.append(value); cases.append(",\n")
        cases.append("      \"latex\": "); Json().also { it.str(parsed.normalized); cases.append(it.sb) }; cases.append(",\n")
        cases.append("      \"error_count\": ").append(errorCount).append("\n")
        cases.append("    }").append(if (caseIdx == CASES.size - 1) "\n" else ",\n")
    }
    cases.append("  ]\n}\n")
    File(outDir, "cases.json").writeText(cases.toString())

    // ---- rewrites.json ----------------------------------------------------
    val rewrites = StringBuilder()
    rewrites.append("{\n  \"agf\": \"0.1\",\n")
    rewrites.append("  \"generated_by\": \"tools/GenerateConformance.kt (reference Kotlin implementation)\",\n")
    rewrites.append("  \"note\": \"Each step replaces the previous graph. Both sides of the relation must keep the same truth value over sample environments (SPEC §9 C5).\",\n")
    rewrites.append("  \"cases\": [\n")
    SOLVE_CASES.forEachIndexed { idx, (source, variable) ->
        val parsed = LatexParser.parseLatex(source)
        val steps = SymbolicEngine.solveForVariable(parsed.graph, parsed.rootId!!, variable)
        rewrites.append("    {\n")
        rewrites.append("      \"name\": \"solve_$idx\",\n")
        rewrites.append("      \"source\": "); Json().also { it.str(source); rewrites.append(it.sb) }; rewrites.append(",\n")
        rewrites.append("      \"variable\": \"").append(variable).append("\",\n")
        rewrites.append("      \"initial\": ").append(encodeGraph(parsed.graph)).append(",\n")
        rewrites.append("      \"final_latex\": "); Json().also {
            it.str(steps.lastOrNull()?.afterLatex ?: parsed.normalized); rewrites.append(it.sb)
        }; rewrites.append(",\n")
        rewrites.append("      \"steps\": [\n")
        steps.forEachIndexed { sIdx, step ->
            rewrites.append("        {\"rule\": "); Json().also { it.str(step.ruleName); rewrites.append(it.sb) }
            rewrites.append(", \"after_latex\": "); Json().also { it.str(step.afterLatex); rewrites.append(it.sb) }
            rewrites.append(", \"graph\": ").append(encodeGraph(step.graph)).append("}")
            rewrites.append(if (sIdx == steps.size - 1) "\n" else ",\n")
        }
        rewrites.append("      ]\n")
        rewrites.append("    }").append(if (idx == SOLVE_CASES.size - 1) "\n" else ",\n")
    }
    rewrites.append("  ]\n}\n")
    File(outDir, "rewrites.json").writeText(rewrites.toString())

    println("wrote ${CASES.size} cases and ${SOLVE_CASES.size} rewrite traces to ${outDir.absolutePath}")
}
