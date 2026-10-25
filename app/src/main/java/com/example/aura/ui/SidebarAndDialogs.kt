package com.example.aura.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aura.data.DocumentPayload
import com.example.aura.data.DocumentSummary
import com.example.aura.kernel.*
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Aura Left Sidebar, Symbol Library Browser & Dialog, Ink/LaTeX Recovery Dialog, and Export Dialog.
 */

@Composable
fun AuraSidebar(
    graph: AuraGraph,
    documents: List<DocumentSummary>,
    activeId: String,
    busy: Boolean,
    diagnostics: List<Diagnostic>,
    onSwitchDocument: (String) -> Unit,
    onCreateDocument: () -> Unit,
    onAddElement: (String, String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val index = remember(graph) { Engine.indexGraph(graph) }
    val openCount = remember(graph, index) {
        graph.nodes.sumOf { Engine.openPorts(index, it).size }
    }
    val errorCount = remember(diagnostics) { diagnostics.count { it.severity == DiagnosticSeverity.Error } }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AuraSidebarBg)
    ) {
        // Header
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(AuraAmberBright, AuraAmberDeep)))
                    ) {
                        Text(
                            text = "A",
                            fontFamily = AuraDisplayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = AuraAmberDarkText
                        )
                    }
                    Column {
                        Text(
                            text = "Aura",
                            fontFamily = AuraDisplayFontFamily,
                            fontSize = 19.sp,
                            color = AuraText
                        )
                        Text(
                            text = "HYPERGRAPH CANVAS",
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 8.5.sp,
                            color = AuraMuted
                        )
                    }
                }
                AuraPill(
                    text = "synced",
                    dotColor = AuraTeal,
                    textColor = AuraTeal
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val stats = listOf(
                    Triple(graph.nodes.size.toString(), "vertices", AuraText),
                    Triple(graph.edges.size.toString(), "bindings", AuraText),
                    Triple(
                        if (errorCount > 0) errorCount.toString() else openCount.toString(),
                        if (errorCount > 0) "conflicts" else "open ports",
                        if (errorCount > 0) AuraRose else AuraTeal
                    )
                )
                stats.forEach { (valText, label, col) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x06FFFFFF))
                            .border(1.dp, AuraLine, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(valText, fontFamily = AuraMonoFontFamily, fontSize = 13.sp, color = col)
                        Text(label.uppercase(), fontSize = 8.5.sp, color = AuraMuted)
                    }
                }
            }
        }
        AuraHairline()

        // Scrollable Canvases + Compact Symbol Browser
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                EyebrowText("Canvases")
                AuraBtn(
                    onClick = onCreateDocument,
                    enabled = !busy,
                    height = 24.dp,
                    horizontalPadding = 8.dp,
                    testTag = "new_canvas_button"
                ) {
                    AuraIcon(AuraIconName.Plus, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("new", fontSize = 10.sp, color = AuraDim)
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(max = 185.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                documents.forEach { doc ->
                    val active = doc.id == activeId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) AuraAmber.copy(alpha = 0.09f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (active) AuraAmber.copy(alpha = 0.42f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !busy) { onSwitchDocument(doc.id) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuraIcon(
                                AuraIconName.Docs,
                                modifier = Modifier.size(13.dp),
                                tint = if (active) AuraAmber else AuraMuted
                            )
                            Text(
                                text = doc.title,
                                fontSize = 12.sp,
                                color = if (active) AuraText else AuraDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${doc.nodeCount} V · ${doc.transactionCount} tx",
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 9.sp,
                            color = AuraMuted,
                            modifier = Modifier.padding(start = 19.dp, top = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            AuraHairline()
            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                EyebrowText("Symbols")
                AuraBtn(
                    onClick = onOpenLibrary,
                    height = 24.dp,
                    horizontalPadding = 8.dp
                ) {
                    AuraIcon(AuraIconName.Expand, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("all ${SymbolLibrary.LIBRARY_COUNT}", fontSize = 10.sp, color = AuraDim)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                SymbolBrowser(
                    busy = busy,
                    compact = true,
                    onPick = onAddElement
                )
            }
        }

        AuraHairline()
        Box(modifier = Modifier.padding(10.dp)) {
            AuraBtn(
                onClick = onOpenLibrary,
                modifier = Modifier.fillMaxWidth()
            ) {
                AuraIcon(AuraIconName.Docs, tint = AuraAmber, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${SymbolLibrary.LIBRARY_COUNT} symbols · every glyph is a typed vertex",
                    fontSize = 10.5.sp,
                    color = AuraDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                AuraIcon(AuraIconName.Chevron, modifier = Modifier.size(13.dp), tint = AuraMuted)
            }
        }
    }
}

@Composable
fun SymbolBrowser(
    busy: Boolean,
    compact: Boolean = false,
    onPick: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("All") }
    var limit by remember(query, domain, compact) { mutableIntStateOf(if (compact) 40 else 100) }

    val results = remember(query, domain) { SymbolLibrary.filterLibrary(query, domain) }
    val visible = remember(results, limit) { results.take(limit) }
    val grouped = remember(visible) {
        visible.groupBy { "${it.domain} · ${it.subdomain}" }
    }
    val columns = if (compact) 4 else 6

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0x8C020617))
                .border(1.dp, AuraLine, RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp)
        ) {
            AuraIcon(AuraIconName.Marquee, modifier = Modifier.size(14.dp), tint = AuraMuted)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = if (compact) "search symbols…" else "search by name, token, domain or variant…",
                        fontSize = 12.sp,
                        color = AuraMuted,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = AuraSansFontFamily,
                        fontSize = 12.sp,
                        color = AuraText
                    ),
                    cursorBrush = SolidColor(AuraAmber),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                AuraIcon(
                    name = AuraIconName.Close,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { query = "" },
                    tint = AuraMuted
                )
            }
        }

        // Domain Filter Pills
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            AuraPill(
                text = "all ${SymbolLibrary.LIBRARY_COUNT}",
                textColor = if (domain == "All") AuraAmber else AuraDim,
                borderColor = if (domain == "All") AuraAmber.copy(alpha = 0.5f) else AuraLine,
                backgroundColor = if (domain == "All") AuraAmber.copy(alpha = 0.12f) else Color(0x0D94A3B8),
                onClick = { domain = "All" }
            )
            SymbolLibrary.LIBRARY_DOMAINS.forEach { dStat ->
                val active = domain == dStat.domain
                AuraPill(
                    text = "${dStat.domain.lowercase()} ${dStat.count}",
                    textColor = if (active) AuraAmber else AuraDim,
                    borderColor = if (active) AuraAmber.copy(alpha = 0.5f) else AuraLine,
                    backgroundColor = if (active) AuraAmber.copy(alpha = 0.12f) else Color(0x0D94A3B8),
                    onClick = { domain = if (active) "All" else dStat.domain }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (results.isEmpty()) "no matches" else "${visible.size} / ${results.size} symbols",
                fontFamily = AuraMonoFontFamily,
                fontSize = 9.5.sp,
                color = AuraMuted
            )
            Text(
                text = if (results.isNotEmpty()) "${results.map { it.domain }.distinct().size} domains" else "",
                fontFamily = AuraMonoFontFamily,
                fontSize = 9.5.sp,
                color = AuraMuted
            )
        }

        Spacer(Modifier.height(6.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            grouped.forEach { (groupTitle, entries) ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(AuraMuted))
                        Text(
                            text = groupTitle.uppercase(),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = AuraMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(entries.size.toString(), fontFamily = AuraMonoFontFamily, fontSize = 9.sp, color = AuraMuted)
                    }
                    entries.chunked(columns).forEach { rowEntries ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowEntries.forEach { entry ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(Brush.verticalGradient(listOf(Color(0xBF1E293B), Color(0xBF0F172A))))
                                        .border(1.dp, AuraLine, RoundedCornerShape(11.dp))
                                        .clickable(enabled = !busy) { onPick(entry.id, entry.label) }
                                        .padding(horizontal = 4.dp)
                                ) {
                                    MathText(tex = entry.token, fontSize = 13.5.sp, color = AuraText)
                                }
                            }
                            repeat(columns - rowEntries.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (results.size > visible.size) {
                AuraBtn(
                    onClick = { limit += if (compact) 40 else 80 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "show ${min(if (compact) 40 else 80, results.size - visible.size)} more",
                        fontSize = 11.5.sp,
                        color = AuraText
                    )
                }
            }
        }
    }
}

@Composable
fun SymbolLibraryDialog(
    open: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onPick: (String, String) -> Unit
) {
    if (!open) return
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AuraPanel(
            cornerRadius = 22.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        EyebrowText("Symbol library · ${SymbolLibrary.LIBRARY_COUNT} typed vertices")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Every domain, one canvas",
                            fontFamily = AuraDisplayFontFamily,
                            fontSize = 20.sp,
                            color = AuraText
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuraPill(
                            text = "ports derived per variant",
                            leadingIcon = AuraIconName.Spark,
                            textColor = AuraTeal
                        )
                        AuraBtn(
                            onClick = onClose,
                            height = 32.dp,
                            horizontalPadding = 8.dp
                        ) {
                            AuraIcon(AuraIconName.Close)
                        }
                    }
                }
                AuraHairline()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp)
                ) {
                    SymbolBrowser(
                        busy = busy,
                        compact = false,
                        onPick = { id, label ->
                            onPick(id, label)
                            onClose()
                        }
                    )
                }
                AuraHairline()
                Text(
                    text = "Algebra, trigonometry, calculus, linear algebra, set theory, logic, number theory, probability & statistics, geometry & topology, complex analysis, abstract algebra, optimization, special functions, Greek and notation — closed under composition with every port.",
                    fontSize = 10.5.sp,
                    color = AuraMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* InkDialog: Tier 1 Stylus Ink & Tier 2 Structural LaTeX Recovery    */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkDialog(
    open: Boolean,
    onClose: () -> Unit,
    busy: Boolean,
    onInsertNode: (AuraNode) -> Unit,
    onInsertLatex: (String) -> Unit,
    onNotify: (String, Boolean) -> Unit
) {
    if (!open) return

    var tab by remember { mutableStateOf("ink") } // "ink" | "latex"
    var strokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var activePoints by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var predictions by remember { mutableStateOf<List<InkPrediction>>(emptyList()) }
    var chosenIdx by remember { mutableIntStateOf(0) }

    var latexInput by remember { mutableStateOf("E = mc^2") }
    var parsedPreview by remember { mutableStateOf<ParseResult?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(latexInput) {
        try {
            parsedPreview = LatexParser.parseLatex(latexInput)
            parseError = null
        } catch (e: Exception) {
            parsedPreview = null
            parseError = e.message
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AuraPanel(
            cornerRadius = 22.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 760.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        EyebrowText("Recognition pipeline")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Write it, don't type it",
                            fontFamily = AuraDisplayFontFamily,
                            fontSize = 20.sp,
                            color = AuraText
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AuraBtn(
                            onClick = { tab = "ink" },
                            variant = if (tab == "ink") AuraBtnVariant.Active else AuraBtnVariant.Default,
                            height = 30.dp
                        ) {
                            AuraIcon(AuraIconName.Pen, modifier = Modifier.size(12.dp), tint = if (tab == "ink") AuraAmber else AuraDim)
                            Spacer(Modifier.width(4.dp))
                            Text("pen", fontSize = 11.sp, color = AuraText)
                        }
                        AuraBtn(
                            onClick = { tab = "latex" },
                            variant = if (tab == "latex") AuraBtnVariant.Active else AuraBtnVariant.Default,
                            height = 30.dp
                        ) {
                            AuraIcon(AuraIconName.Graph, modifier = Modifier.size(12.dp), tint = if (tab == "latex") AuraAmber else AuraDim)
                            Spacer(Modifier.width(4.dp))
                            Text("structure", fontSize = 11.sp, color = AuraText)
                        }
                        AuraBtn(
                            onClick = onClose,
                            height = 30.dp,
                            horizontalPadding = 8.dp
                        ) {
                            AuraIcon(AuraIconName.Close)
                        }
                    }
                }
                AuraHairline()

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (tab == "ink") {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EyebrowText("Stylus ink surface")
                            Text("${strokes.size} strokes", fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0A0F19))
                                .border(1.dp, AuraLine, RoundedCornerShape(16.dp))
                                .pointerInteropFilter { ev ->
                                    val x = ev.x
                                    val y = ev.y
                                    val p = ev.pressure.coerceIn(0.2f, 1.4f)
                                    val tilt = ev.getAxisValue(android.view.MotionEvent.AXIS_TILT)
                                    when (ev.actionMasked) {
                                        android.view.MotionEvent.ACTION_DOWN -> {
                                            activePoints = listOf(InkPoint(x, y, p, tilt))
                                            true
                                        }
                                        android.view.MotionEvent.ACTION_MOVE -> {
                                            val last = activePoints.lastOrNull()
                                            if (last == null || hypot(x - last.x, y - last.y) >= 2f) {
                                                activePoints = activePoints + InkPoint(x, y, p, tilt)
                                            }
                                            true
                                        }
                                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                            if (activePoints.size > 1) {
                                                val next = strokes + Stroke(activePoints)
                                                strokes = next
                                                predictions = InkRecognizer.classifyInk(next)
                                                chosenIdx = 0
                                            }
                                            activePoints = emptyList()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val step = 22.dp.toPx()
                                var gx = step
                                while (gx < size.width) {
                                    var gy = step
                                    while (gy < size.height) {
                                        drawCircle(Color(0x2E94A3B8), radius = 1f, center = Offset(gx, gy))
                                        gy += step
                                    }
                                    gx += step
                                }
                                val all = if (activePoints.size > 1) strokes + Stroke(activePoints) else strokes
                                for (s in all) {
                                    for (i in 1 until s.points.size) {
                                        val p0 = s.points[i - 1]
                                        val p1 = s.points[i]
                                        drawLine(
                                            color = AuraInkStroke,
                                            start = Offset(p0.x, p0.y),
                                            end = Offset(p1.x, p1.y),
                                            strokeWidth = 3f + p1.pressure * 4f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            if (strokes.isEmpty() && activePoints.isEmpty()) {
                                Text(
                                    text = "draw  =  +  x  1  2  √  ∫  θ  π  ∞",
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = AuraMuted,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AuraBtn(
                                onClick = {
                                    if (strokes.isEmpty()) {
                                        onNotify("Draw a symbol first", true)
                                    } else {
                                        predictions = InkRecognizer.classifyInk(strokes)
                                        chosenIdx = 0
                                    }
                                },
                                variant = AuraBtnVariant.Primary,
                                modifier = Modifier.weight(1f)
                            ) {
                                AuraIcon(AuraIconName.Spark, tint = AuraAmberDarkText, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("recognise strokes", fontWeight = FontWeight.SemiBold, color = AuraAmberDarkText)
                            }
                            AuraBtn(
                                onClick = {
                                    strokes = emptyList()
                                    predictions = emptyList()
                                },
                                enabled = strokes.isNotEmpty()
                            ) {
                                Text("clear", fontSize = 12.sp, color = AuraText)
                            }
                        }

                        if (predictions.isNotEmpty()) {
                            EyebrowText("Recognizer output")
                            predictions.forEachIndexed { idx, pred ->
                                val selected = idx == chosenIdx
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) AuraAmber.copy(alpha = 0.09f) else Color(0x06FFFFFF))
                                        .border(
                                            1.dp,
                                            if (selected) AuraAmber.copy(alpha = 0.55f) else AuraLine,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { chosenIdx = idx }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        MathText(tex = pred.latex, fontSize = 18.sp, color = AuraText)
                                        Text(
                                            text = "${(pred.confidence * 100).roundToInt()}%",
                                            fontFamily = AuraMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = AuraAmber
                                        )
                                    }
                                    Text(
                                        text = "${pred.kind.wireName} · ${pred.type.label}",
                                        fontFamily = AuraMonoFontFamily,
                                        fontSize = 9.5.sp,
                                        color = AuraMuted
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(pred.rationale, fontSize = 10.5.sp, color = AuraDim)
                                }
                            }
                            val chosen = predictions.getOrNull(chosenIdx)
                            if (chosen != null) {
                                AuraBtn(
                                    onClick = {
                                        onInsertNode(
                                            makeNode(
                                                kind = chosen.kind,
                                                token = chosen.latex,
                                                label = chosen.label,
                                                type = chosen.type,
                                                variant = chosen.variant
                                            )
                                        )
                                        onClose()
                                    },
                                    variant = AuraBtnVariant.Primary,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AuraIcon(AuraIconName.Plus, tint = AuraAmberDarkText, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("place ${chosen.symbol} as vertex", fontWeight = FontWeight.SemiBold, color = AuraAmberDarkText)
                                }
                            }
                        }
                    } else {
                        // Tier 2: Structural LaTeX Recovery
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EyebrowText("Tier 2 · Structural recovery")
                            AuraPill(text = "builds typed vertices", textColor = AuraTeal)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x8C020617))
                                .border(1.dp, AuraAmber.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            BasicTextField(
                                value = latexInput,
                                onValueChange = { latexInput = it },
                                textStyle = TextStyle(
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 13.sp,
                                    color = AuraText
                                ),
                                cursorBrush = SolidColor(AuraAmber),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Quick sample pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "E = mc^2",
                                "a^2 + b^2 = c^2",
                                "z = x^2 - y^2",
                                "\\frac{-b + \\sqrt{b^2 - 4ac}}{2a}",
                                "\\int_{0}^{1} x^2",
                                "\\sum_{n=1}^{10} n"
                            ).forEach { sample ->
                                AuraPill(
                                    text = sample,
                                    onClick = { latexInput = sample }
                                )
                            }
                        }

                        if (parseError != null) {
                            Text(
                                text = parseError ?: "",
                                fontSize = 11.5.sp,
                                color = AuraRose,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AuraRose.copy(alpha = 0.08f))
                                    .border(1.dp, AuraRose.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            )
                        }

                        val preview = parsedPreview
                        if (preview != null) {
                            AuraPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    MathBlock(tex = preview.normalized, fontSize = 18.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "${preview.nodeCount} vertices · ${preview.edgeCount} bindings · lossless round-trip",
                                        fontFamily = AuraMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = AuraTeal
                                    )
                                }
                            }

                            AuraBtn(
                                onClick = {
                                    onInsertLatex(latexInput)
                                    onClose()
                                },
                                variant = AuraBtnVariant.Primary,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AuraIcon(AuraIconName.Graph, tint = AuraAmberDarkText, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Materialise ${preview.nodeCount} vertices onto canvas",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AuraAmberDarkText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* ExportDialog: LaTeX, MathML, and Typed Hypergraph JSON Export      */
/* ------------------------------------------------------------------ */

@Composable
fun ExportDialog(
    open: Boolean,
    payload: DocumentPayload,
    expressions: List<SerializedExpression>,
    onClose: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    if (!open) return

    var format by remember { mutableStateOf("latex") } // "latex" | "mathml" | "json"

    val content = remember(format, payload, expressions) {
        val graph = payload.graph
        when (format) {
            "latex" -> {
                val body = expressions.joinToString("\n\n") { "\\[\n  ${it.latex}\n\\]" }
                "% Aura — ${payload.title}\n% typed hypergraph export (${graph.nodes.size} vertices, ${graph.edges.size} bindings)\n\n$body\n"
            }
            "mathml" -> {
                expressions.joinToString("\n\n") { MathFormatter.toMathML(it.latex) }
            }
            else -> {
                val root = JSONObject()
                root.put("format", "aura-typed-hypergraph")
                root.put("version", 2)
                root.put("title", payload.title)
                val vArr = JSONArray()
                for (n in graph.nodes) {
                    val o = JSONObject()
                    o.put("id", n.id)
                    o.put("kind", n.kind.wireName)
                    o.put("type", n.type.label)
                    o.put("token", n.token)
                    o.put("label", n.label)
                    o.put("variant", n.variant)
                    o.put("x", n.x)
                    o.put("y", n.y)
                    vArr.put(o)
                }
                val eArr = JSONArray()
                for (e in graph.edges) {
                    val o = JSONObject()
                    o.put("id", e.id)
                    o.put("source", e.source)
                    o.put("port", e.port.wireName)
                    o.put("target", e.target)
                    eArr.put(o)
                }
                root.put("vertices", vArr)
                root.put("hyperedges", eArr)
                root.toString(2)
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AuraPanel(
            cornerRadius = 22.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f)
                .widthIn(max = 720.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        EyebrowText("Interop")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Export the graph",
                            fontFamily = AuraDisplayFontFamily,
                            fontSize = 20.sp,
                            color = AuraText
                        )
                    }
                    AuraBtn(onClick = onClose, height = 32.dp, horizontalPadding = 8.dp) {
                        AuraIcon(AuraIconName.Close)
                    }
                }
                AuraHairline()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    listOf("latex", "mathml", "json").forEach { fmt ->
                        AuraBtn(
                            onClick = { format = fmt },
                            variant = if (format == fmt) AuraBtnVariant.Active else AuraBtnVariant.Default,
                            height = 28.dp
                        ) {
                            Text(fmt.uppercase(), fontSize = 11.sp, color = AuraText)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    AuraBtn(
                        onClick = { onCopy(content, "${format.uppercase()} copied to clipboard") },
                        variant = AuraBtnVariant.Primary,
                        height = 30.dp
                    ) {
                        AuraIcon(AuraIconName.Copy, tint = AuraAmberDarkText, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copy ${format.uppercase()}", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = AuraAmberDarkText)
                    }
                }
                AuraHairline()

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (format != "json") {
                        expressions.forEach { expr ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x40000000))
                                    .border(1.dp, AuraLine, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                MathBlock(tex = expr.latex, fontSize = 17.sp)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x55000000))
                            .border(1.dp, AuraLine, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = content,
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 11.sp,
                            color = AuraDim
                        )
                    }
                }
                AuraHairline()
                Text(
                    text = "LaTeX and MathML are serialisations. The typed hypergraph is the document of record, so it travels with port and type information.",
                    fontSize = 10.5.sp,
                    color = AuraMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
