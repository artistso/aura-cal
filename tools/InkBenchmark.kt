package benchmark

import com.example.aura.kernel.InkPoint
import com.example.aura.kernel.InkRecognizer
import com.example.aura.kernel.Stroke
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tier-1 ink benchmark: a reproducible harness, a closed-set baseline and a coverage audit.
 *
 *   kotlinc app/src/main/java/com/example/aura/kernel/InkRecognizer.kt \
 *           app/src/main/java/com/example/aura/kernel/Ontology.kt \
 *           tools/InkBenchmark.kt -include-runtime -d /tmp/ink.jar
 *   java -cp /tmp/ink.jar benchmark.InkBenchmarkKt
 *
 * Writes docs/benchmarks/ink-baseline.json and appends the results table to
 * docs/benchmarks/ink-baseline.md.
 *
 * READ docs/benchmarks/methodology.md BEFORE quoting a number from this file. The samples are
 * synthetic, generated from the author's own glyph templates: the score measures self-consistency
 * and robustness to perturbation, NOT accuracy on human handwriting.
 */

// --------------------------------------------------------------------------- stroke synthesis

private typealias Poly = List<Pair<Float, Float>>

/** Samples a polyline into stroke points with the given spacing. */
private fun sample(points: Poly, spacing: Float): Poly {
    val out = ArrayList<Pair<Float, Float>>()
    for (i in 0 until points.size - 1) {
        val (x0, y0) = points[i]
        val (x1, y1) = points[i + 1]
        val dx = x1 - x0
        val dy = y1 - y0
        val length = kotlin.math.hypot(dx, dy)
        val steps = maxOf(1, (length / spacing).toInt())
        for (s in 0 until steps) {
            val t = s.toFloat() / steps
            out.add(x0 + dx * t to y0 + dy * t)
        }
    }
    out.add(points.last())
    return out
}

private fun line(x0: Float, y0: Float, x1: Float, y1: Float): Poly =
    sample(listOf(x0 to y0, x1 to y1), 3.5f)

private fun poly(vararg points: Pair<Float, Float>): Poly = sample(points.toList(), 3.5f)

/** Arc in degrees, y grows downward. */
private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, startDeg: Float, endDeg: Float): Poly {
    val steps = maxOf(8, ((kotlin.math.abs(endDeg - startDeg) / 6f).toInt()))
    return (0..steps).map { i ->
        val t = startDeg + (endDeg - startDeg) * i / steps
        val rad = t * PI.toFloat() / 180f
        (cx + rx * cos(rad)) to (cy + ry * sin(rad))
    }
}

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): Poly = arc(cx, cy, rx, ry, 0f, 358f)

private fun dot(x: Float, y: Float): Poly = listOf(x to y, x + 1.2f to y + 1.2f)

// --------------------------------------------------------------------------- glyph templates

/** Each glyph is a list of strokes drawn in a 0..100 box. */
private val TEMPLATES: Map<String, List<Poly>> = mapOf(
    "=" to listOf(line(15f, 38f, 85f, 38f), line(15f, 62f, 85f, 62f)),
    "-" to listOf(line(15f, 50f, 85f, 50f)),
    "+" to listOf(line(15f, 50f, 85f, 50f), line(50f, 15f, 50f, 85f)),
    "mul" to listOf(line(22f, 22f, 78f, 78f), line(78f, 22f, 22f, 78f)),
    "div" to listOf(line(15f, 50f, 85f, 50f), dot(50f, 26f), dot(50f, 74f)),
    "x" to listOf(line(20f, 20f, 80f, 80f), line(80f, 20f, 20f, 80f)),
    "y" to listOf(line(22f, 28f, 50f, 68f), line(78f, 28f, 26f, 88f)),
    "z" to listOf(poly(20f to 25f, 80f to 25f, 20f to 75f, 80f to 75f)),
    "a" to listOf(ellipse(42f, 58f, 18f, 22f), line(62f, 32f, 62f, 80f)),
    "n" to listOf(line(25f, 30f, 25f, 80f), poly(25f to 45f, 45f to 30f, 70f to 45f, 70f to 80f)),
    "1" to listOf(line(50f, 15f, 50f, 85f)),
    "2" to listOf(
        arc(45f, 34f, 22f, 15f, 190f, 350f),
        poly(64f to 40f, 24f to 82f),
        line(24f, 82f, 82f, 82f)
    ),
    "3" to listOf(arc(48f, 32f, 18f, 14f, 200f, 350f), arc(48f, 68f, 20f, 16f, 190f, 20f)),
    "0" to listOf(ellipse(50f, 50f, 22f, 30f)),
    "sqrt" to listOf(poly(12f to 58f, 28f to 80f, 52f to 16f, 90f to 16f)),
    "int" to listOf(poly(66f to 12f, 54f to 22f, 48f to 40f, 50f to 62f, 44f to 80f, 32f to 90f)),
    "sum" to listOf(poly(25f to 18f, 80f to 18f, 45f to 50f, 80f to 82f, 25f to 82f)),
    "theta" to listOf(ellipse(50f, 50f, 20f, 30f), line(20f, 50f, 80f, 50f)),
    "pi" to listOf(line(15f, 30f, 85f, 30f), line(36f, 30f, 32f, 82f), line(68f, 30f, 72f, 82f)),
    "alpha" to listOf(arc(44f, 54f, 16f, 22f, 20f, 330f), line(58f, 44f, 78f, 80f)),
    "infty" to listOf(arc(32f, 50f, 17f, 15f, 0f, 358f) + arc(68f, 50f, 17f, 15f, 358f, 0f)),
    "in" to listOf(arc(56f, 50f, 24f, 34f, 55f, 305f), line(30f, 50f, 74f, 50f)),
    "mapsto" to listOf(line(14f, 50f, 82f, 50f), poly(64f to 34f, 82f to 50f, 64f to 66f)),
    "leq" to listOf(poly(58f to 22f, 26f to 50f, 58f to 72f), line(22f, 88f, 80f, 88f)),
    "power" to listOf(poly(25f to 70f, 50f to 24f, 76f to 70f)),
    "reals" to listOf(
        line(30f, 20f, 30f, 83f),
        poly(30f to 20f, 62f to 26f, 64f to 46f, 32f to 50f),
        poly(32f to 50f, 66f to 60f, 78f to 83f)
    )
)

// --------------------------------------------------------------------------- perturbation

private data class Variant(val scale: Float, val rotationDeg: Float, val jitter: Float, val spacing: Float)

private fun transform(strokes: List<Poly>, variant: Variant, rng: Random): List<Stroke> {
    val cosR = cos(variant.rotationDeg * PI.toFloat() / 180f)
    val sinR = sin(variant.rotationDeg * PI.toFloat() / 180f)
    return strokes.map { stroke ->
        val points = stroke.map { (x, y) ->
            val cx = (x - 50f) * variant.scale
            val cy = (y - 50f) * variant.scale
            val rx = cx * cosR - cy * sinR + 50f
            val ry = cx * sinR + cy * cosR + 50f
            val jx = rx + (rng.nextFloat() - 0.5f) * 2f * variant.jitter
            val jy = ry + (rng.nextFloat() - 0.5f) * 2f * variant.jitter
            InkPoint(jx, jy)
        }
        Stroke(resample(points, variant.spacing))
    }
}

private fun resample(points: List<InkPoint>, spacing: Float): List<InkPoint> {
    if (points.size < 2 || spacing <= 0f) return points
    val out = ArrayList<InkPoint>()
    var carry = 0f
    out.add(points.first())
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val segment = kotlin.math.hypot(b.x - a.x, b.y - a.y)
        carry += segment
        if (carry >= spacing) {
            out.add(b)
            carry = 0f
        }
    }
    if (out.last() != points.last()) out.add(points.last())
    return out
}

// --------------------------------------------------------------------------- benchmark

private val SYMBOLS = TEMPLATES.keys.sorted()

private class Tally {
    var trials = 0
    var top1 = 0
    var top3 = 0
    var confidenceSum = 0.0
}

private fun keyOf(prediction: com.example.aura.kernel.InkPrediction): String =
    // Map the emitted symbol back to the template key.
    when (prediction.symbol) {
        "−" -> "-"
        "×" -> "mul"
        "÷" -> "div"
        "√" -> "sqrt"
        "∫" -> "int"
        "∑" -> "sum"
        "θ" -> "theta"
        "π" -> "pi"
        "α" -> "alpha"
        "∞" -> "infty"
        "∈" -> "in"
        "→" -> "mapsto"
        "≤" -> "leq"
        "ℝ" -> "reals"
        else -> prediction.symbol
    }

/**
 * Loads a real-stroke corpus: one sample per line, `label<TAB>stroke;stroke` where a stroke is
 * `x,y;x,y;...`. Blank lines and `#` comments are ignored. This is what makes the harness useful
 * on captured handwriting instead of only on templates.
 */
private fun loadExternal(path: String): Map<String, List<List<Stroke>>> {
    val out = LinkedHashMap<String, MutableList<List<Stroke>>>()
    File(path).forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val parts = line.split('\t')
        if (parts.size < 2) return@forEachLine
        val label = parts[0].trim()
        val strokes = parts[1].split('|').map { strokeText ->
            val points = strokeText.split(';').mapNotNull { point ->
                val xy = point.split(',')
                if (xy.size != 2) return@mapNotNull null
                val x = xy[0].trim().toFloatOrNull()
                val y = xy[1].trim().toFloatOrNull()
                if (x == null || y == null) null else InkPoint(x, y)
            }
            Stroke(points)
        }.filter { it.points.size > 1 }
        if (strokes.isNotEmpty()) out.getOrPut(label) { ArrayList() }.add(strokes)
    }
    return out
}

private fun benchmarkExternal(samples: Map<String, List<List<Stroke>>>, report: StringBuilder) {
    var trials = 0
    var top1 = 0
    var top3 = 0
    report.appendLine("## External corpus")
    report.appendLine()
    report.appendLine("| glyph | samples | top-1 | top-3 |")
    report.appendLine("|---|---|---|---|")
    for ((label, entries) in samples) {
        var symbolTop1 = 0
        var symbolTop3 = 0
        for (strokes in entries) {
            val predictions = InkRecognizer.classifyInk(strokes)
            val top = predictions.firstOrNull() ?: continue
            trials++
            if (keyOf(top) == label) {
                top1++
                symbolTop1++
            }
            if (predictions.take(3).any { keyOf(it) == label }) {
                top3++
                symbolTop3++
            }
        }
        report.appendLine("| `$label` | ${entries.size} | $symbolTop1 | $symbolTop3 |")
    }
    report.appendLine()
    report.appendLine("**External corpus totals:** top-1 ${pct(top1, trials)}, top-3 ${pct(top3, trials)}.")
    report.appendLine()
}

fun main(args: Array<String>) {
    val externalPath = args.firstOrNull { it.startsWith("--external=") }?.substringAfter('=')
    val rng = Random(20261025)
    val perSymbol = LinkedHashMap<String, Tally>()
    val perSymbolTop3Only = LinkedHashMap<String, Tally>()
    val confusions = HashMap<String, Int>()
    val emitted = LinkedHashSet<String>()
    val byLevel = sortedMapOf<String, Tally>()

    val levels = listOf(
        "clean" to Variant(1.0f, 0f, 0.6f, 3.0f),
        "tilted" to Variant(1.0f, 0f, 1.5f, 3.0f),
        "rotated" to Variant(1.0f, 12f, 1.5f, 3.0f),
        "resized" to Variant(0.62f, 0f, 1.2f, 3.0f),
        "sloppy" to Variant(1.18f, -8f, 3.2f, 4.5f)
    )

    for (key in SYMBOLS) {
        val template = TEMPLATES.getValue(key)
        perSymbol[key] = Tally()
        perSymbolTop3Only[key] = Tally()
        for ((levelName, variant) in levels) {
            val levelTally = byLevel.getOrPut(levelName) { Tally() }
            // Four deterministic draws per level, with different scale jitter.
            repeat(4) { draw ->
                val jittered = variant.copy(scale = variant.scale * (0.92f + draw * 0.05f))
                val strokes = transform(template, jittered, rng)
                val predictions = InkRecognizer.classifyInk(strokes)
                val top = predictions.firstOrNull() ?: continue
                val predictedKey = keyOf(top)
                emitted.add(predictedKey)

                val tally = perSymbol.getValue(key)
                tally.trials++
                tally.confidenceSum += top.confidence
                if (predictedKey == key) tally.top1++
                if (predictions.take(3).any { keyOf(it) == key }) tally.top3++

                levelTally.trials++
                if (predictedKey == key) levelTally.top1++

                if (predictedKey != key) {
                    val confusionKey = "$key→$predictedKey"
                    confusions[confusionKey] = (confusions[confusionKey] ?: 0) + 1
                }
            }
        }
    }

    val totalTrials = perSymbol.values.sumOf { it.trials }
    val totalTop1 = perSymbol.values.sumOf { it.top1 }
    val totalTop3 = perSymbol.values.sumOf { it.top3 }

    // ---- report -----------------------------------------------------------
    val report = StringBuilder()
    report.appendLine("# Tier-1 ink benchmark — baseline")
    report.appendLine()
    report.appendLine("<!-- generated by tools/InkBenchmark.kt — do not edit by hand -->")
    report.appendLine()
    report.appendLine("Synthetic, closed-set, ${SYMBOLS.size} glyphs, $totalTrials trials, seed 20261025.")
    report.appendLine()
    report.appendLine("> **Read [methodology.md](methodology.md) first.** These samples are generated from the")
    report.appendLine("> author's own glyph templates. The numbers measure *self-consistency and robustness*, not")
    report.appendLine("> accuracy on human handwriting. Do not quote them as a recognition rate.")
    report.appendLine()
    report.appendLine("## Headline")
    report.appendLine()
    report.appendLine("| metric | value |")
    report.appendLine("|---|---|")
    report.appendLine("| top-1 accuracy (closed set) | ${pct(totalTop1, totalTrials)} |")
    report.appendLine("| top-3 accuracy (closed set) | ${pct(totalTop3, totalTrials)} |")
    report.appendLine("| glyphs recognised top-1 at least once | ${perSymbol.count { it.value.top1 > 0 }}/${SYMBOLS.size} |")
    report.appendLine("| glyphs never emitted in this corpus | ${(SYMBOLS - emitted).size} |")
    report.appendLine()
    report.appendLine(
        "\"Never emitted in this corpus\" is a property of the *samples*. For the stronger claim — a " +
            "glyph the classifier *cannot* emit for any input — see methodology.md § coverage audit."
    )
    report.appendLine()
    report.appendLine("## Robustness by perturbation")
    report.appendLine()
    report.appendLine("| level | top-1 |")
    report.appendLine("|---|---|")
    for ((level, tally) in byLevel) {
        report.appendLine("| $level | ${pct(tally.top1, tally.trials)} |")
    }
    report.appendLine()
    report.appendLine("## Per glyph")
    report.appendLine()
    report.appendLine("| glyph | top-1 | top-3 | mean confidence |")
    report.appendLine("|---|---|---|---|")
    for ((key, tally) in perSymbol) {
        val confidence = if (tally.trials > 0) tally.confidenceSum / tally.trials else 0.0
        report.appendLine(
            "| `$key` | ${pct(tally.top1, tally.trials)} | ${pct(tally.top3, tally.trials)} | " +
                String.format("%.2f", confidence) + " |"
        )
    }
    report.appendLine()
    report.appendLine("## Coverage audit")
    report.appendLine()
    val never = SYMBOLS - emitted
    if (never.isEmpty()) {
        report.appendLine("Every declared symbol was emitted at least once.")
    } else {
        report.appendLine("Declared by `InkRecognizer.SPECS` but **never emitted** in any trial:")
        report.appendLine()
        report.appendLine(never.joinToString(", ") { "`$it`" })
        report.appendLine()
        report.appendLine(
            "A symbol that can never be the top prediction is unreachable through the ink path, " +
                "regardless of how well it is drawn. This is a specification gap in the classifier, " +
                "not a property of the benchmark data."
        )
    }
    report.appendLine()
    report.appendLine("## Most frequent confusions")
    report.appendLine()
    report.appendLine("| expected → predicted | count |")
    report.appendLine("|---|---|")
    confusions.entries.sortedByDescending { it.value }.take(12).forEach { (pair, count) ->
        report.appendLine("| `$pair` | $count |")
    }

    if (externalPath != null) {
        benchmarkExternal(loadExternal(externalPath), report)
    }

    val outDir = File("docs/benchmarks")
    outDir.mkdirs()
    File(outDir, "ink-baseline.md").writeText(report.toString())

    val json = StringBuilder()
    json.appendLine("{")
    json.appendLine("  \"generated_by\": \"tools/InkBenchmark.kt\",")
    json.appendLine("  \"seed\": 20261025,")
    json.appendLine("  \"declared_symbols\": ${SYMBOLS.size},")
    json.appendLine("  \"trials\": $totalTrials,")
    json.appendLine("  \"top1\": $totalTop1,")
    json.appendLine("  \"top3\": $totalTop3,")
    json.appendLine("  \"never_emitted\": [${(SYMBOLS - emitted).joinToString(", ") { "\"$it\"" }}],")
    json.appendLine("  \"per_symbol\": {")
    val entries = perSymbol.entries.toList()
    entries.forEachIndexed { index, (key, tally) ->
        json.append("    \"$key\": {\"trials\": ${tally.trials}, \"top1\": ${tally.top1}, \"top3\": ${tally.top3}}")
        json.appendLine(if (index == entries.size - 1) "" else ",")
    }
    json.appendLine("  },")
    json.appendLine("  \"by_level\": {")
    val levelEntries = byLevel.entries.toList()
    levelEntries.forEachIndexed { index, (level, tally) ->
        json.append("    \"$level\": {\"trials\": ${tally.trials}, \"top1\": ${tally.top1}}")
        json.appendLine(if (index == levelEntries.size - 1) "" else ",")
    }
    json.appendLine("  },")
    json.appendLine("  \"confusions\": {")
    val confusionEntries = confusions.entries.sortedByDescending { it.value }
    confusionEntries.forEachIndexed { index, (pair, count) ->
        json.append("    \"$pair\": $count")
        json.appendLine(if (index == confusionEntries.size - 1) "" else ",")
    }
    json.appendLine("  }")
    json.appendLine("}")
    File(outDir, "ink-baseline.json").writeText(json.toString())

    println(report)
    println("wrote ${outDir.absolutePath}/ink-baseline.{md,json}")
}

private fun pct(part: Int, total: Int): String {
    if (total == 0) return "n/a"
    return String.format("%.1f%% (%d/%d)", 100.0 * part / total, part, total)
}
