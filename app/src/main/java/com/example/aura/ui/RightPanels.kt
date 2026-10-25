package com.example.aura.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.data.DiscoveryEntity
import com.example.aura.data.HistoryEntry
import com.example.aura.kernel.*
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.*

/**
 * Aura Right Drawer Panels:
 * 1. InspectorPanel (Type inspector, ports, scope halos, diagnostics, transaction history)
 * 2. SymbolicSolvePanel (Algebraic simplification & step-by-step equation isolation)
 * 3. PlotPanel (2D function traces + 3D interactive isometric surfaces + live parameter sliders)
 * 4. DiscoveryPanel (Auto-Connect scale slider, family filters, verified proposals, and Formal Proof Traces)
 * 5. VerificationPanel (42 executable specifications report)
 */

@Composable
fun InspectorPanel(
    graph: AuraGraph,
    selected: List<String>,
    diagnostics: List<Diagnostic>,
    history: List<HistoryEntry>,
    busy: Boolean,
    onDelete: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: (String) -> Unit,
    onReflow: () -> Unit,
    onSimplify: () -> Unit,
    onOpenSymbolicSolve: () -> Unit,
    onCopy: (String, String) -> Unit,
    onSelect: (List<String>) -> Unit
) {
    val index = remember(graph) { Engine.indexGraph(graph) }
    val expressions = remember(graph) { Engine.documentLatex(graph) }
    val selectedNodes = remember(graph, selected) { graph.nodes.filter { it.id in selected } }
    val primary = if (selectedNodes.size == 1) selectedNodes[0] else null
    val primaryDiagnostics = remember(diagnostics, selected) { diagnostics.filter { it.nodeId in selected } }
    val allErrors = remember(diagnostics) { diagnostics.filter { it.severity == DiagnosticSeverity.Error } }
    val allWarnings = remember(diagnostics) { diagnostics.filter { it.severity == DiagnosticSeverity.Warning } }

    val primaryRoot = remember(primary, index) {
        if (primary != null) Engine.componentRoot(index, primary.id) else null
    }
    val analysis = remember(primary, graph, index) {
        if (primary != null) Evaluate.analyzeCluster(graph, primary.id, index) else null
    }
    val scopes = remember(graph) { Evaluate.scopeIndex(graph) }
    val primaryExpression = remember(primaryRoot, expressions) {
        if (primaryRoot != null) expressions.find { it.rootId == primaryRoot.id } else null
    }
    val openCount = remember(graph, index) {
        graph.nodes.sumOf { Engine.openPorts(index, it).size }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                EyebrowText("Type inspector")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        primary != null -> primary.label
                        selectedNodes.size > 1 -> "${selectedNodes.size} vertices selected"
                        else -> "Graph overview"
                    },
                    fontFamily = AuraDisplayFontFamily,
                    fontSize = 17.sp,
                    color = AuraText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AuraPill(
                text = "$openCount open",
                leadingIcon = AuraIconName.Port
            )
        }
        AuraHairline()

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            if (primary != null) {
                // Primary Vertex Card
                AuraPanel(cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x33000000))
                                .padding(vertical = 18.dp, horizontal = 12.dp)
                        ) {
                            MathText(
                                tex = primary.token.ifEmpty { primary.label },
                                fontSize = 24.sp,
                                color = AuraText
                            )
                        }
                        AuraHairline()
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(10.dp)
                            ) {
                                EyebrowText("Inferred type τ")
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = Engine.effectiveType(index, primary.id).label,
                                    fontSize = 13.sp,
                                    color = AuraAmber
                                )
                            }
                            Box(Modifier.width(1.dp).height(46.dp).background(AuraLine))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(10.dp)
                            ) {
                                EyebrowText("Declared kind")
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = nodeDisplayKind(primary),
                                    fontSize = 13.sp,
                                    color = AuraText
                                )
                            }
                        }
                        AuraHairline()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            AuraPill(text = "arity ${arityOf(primary)}")
                            AuraPill(text = "id ${primary.id.take(8)}")
                            if (primary.collapsed) {
                                AuraPill(text = "collapsed", textColor = AuraTeal)
                            }
                            if (primary.variant != null) {
                                AuraPill(text = "variant:${primary.variant}")
                            }
                            if (analysis?.value != null) {
                                AuraPill(
                                    text = "= ${Evaluate.formatNumber(analysis.value)}",
                                    textColor = AuraTeal,
                                    borderColor = AuraTeal.copy(alpha = 0.4f),
                                    backgroundColor = AuraTeal.copy(alpha = 0.1f)
                                )
                            }
                            if (analysis != null && analysis.variables.isNotEmpty()) {
                                AuraPill(
                                    text = "${analysis.variables.size} free var${if (analysis.variables.size == 1) "" else "s"}",
                                    textColor = AuraViolet
                                )
                            }
                        }
                    }
                }

                // Ports & Admissibility
                val ports = portsFor(primary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Ports & admissibility")
                        Text(ports.size.toString(), fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                    }
                    if (ports.isEmpty()) {
                        Text(
                            text = "This vertex exposes no ports; it can still be bound into another vertex.",
                            fontSize = 12.sp,
                            color = AuraMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x08FFFFFF))
                                .border(1.dp, AuraLine, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        )
                    }
                    ports.forEach { portDef ->
                        val child = Engine.childOf(index, primary.id, portDef.id)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (child != null) Color(0x08FFFFFF) else AuraAmber.copy(alpha = 0.05f))
                                .border(
                                    1.dp,
                                    if (child != null) AuraLine else AuraAmber.copy(alpha = 0.28f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = portDef.label + if (portDef.required) " *" else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AuraText
                                )
                                Text(
                                    text = if (child != null) "bound" else "open",
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 9.5.sp,
                                    color = if (child != null) AuraTeal else AuraAmber
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Text("accepts", fontSize = 10.sp, color = AuraMuted)
                                portDef.accepts.forEach { t ->
                                    Text(
                                        text = t.label,
                                        fontFamily = AuraMonoFontFamily,
                                        fontSize = 9.sp,
                                        color = AuraDim,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x33000000))
                                            .border(1.dp, AuraLine, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (child != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable { onSelect(listOf(child.id)) }
                                ) {
                                    AuraIcon(AuraIconName.Bind, tint = AuraTeal, modifier = Modifier.size(12.dp))
                                    MathText(tex = child.token.ifEmpty { child.label }, fontSize = 13.sp, color = AuraTeal)
                                }
                            } else {
                                Text(portDef.hint, fontSize = 10.5.sp, color = AuraAmber.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                // Variable Scope
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Variable scope")
                        Text("${scopes.size} symbols", fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                    }
                    scopes.forEach { entry ->
                        val active = primary.kind == NodeKind.Variable && primary.token == entry.token
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Color(0x14FFFFFF) else Color(0x06FFFFFF))
                                .border(1.dp, if (active) AuraLineStrong else AuraLine, RoundedCornerShape(12.dp))
                                .clickable { onSelect(entry.nodeIds) }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(entry.colorHex))
                            )
                            MathText(tex = entry.token, fontSize = 14.sp, color = AuraText)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${entry.nodeIds.size} × ${entry.clusterIds.size} cluster${if (entry.clusterIds.size == 1) "" else "s"}",
                                fontFamily = AuraMonoFontFamily,
                                fontSize = 9.5.sp,
                                color = AuraMuted
                            )
                        }
                    }
                }

                // Diagnostics for Primary
                if (primaryDiagnostics.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EyebrowText("Diagnostics")
                        primaryDiagnostics.forEach { d ->
                            val isErr = d.severity == DiagnosticSeverity.Error
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isErr) AuraRose.copy(alpha = 0.08f) else AuraAmber.copy(alpha = 0.06f))
                                    .border(1.dp, if (isErr) AuraRose.copy(alpha = 0.35f) else AuraAmber.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                AuraIcon(
                                    name = if (isErr) AuraIconName.Error else AuraIconName.Warning,
                                    tint = if (isErr) AuraRose else AuraAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(d.message, fontSize = 11.5.sp, color = if (isErr) AuraRose else AuraAmber)
                            }
                        }
                    }
                }

                // Serialised Expression
                if (primaryExpression != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EyebrowText("Serialised expression")
                            AuraBtn(
                                onClick = { onCopy(primaryExpression.latex, "LaTeX copied") },
                                height = 24.dp,
                                horizontalPadding = 8.dp
                            ) {
                                AuraIcon(AuraIconName.Copy, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("copy", fontSize = 10.sp, color = AuraDim)
                            }
                        }
                        AuraPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                MathBlock(tex = primaryExpression.latex, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                AuraHairline()
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = primaryExpression.latex,
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 9.5.sp,
                                    color = AuraMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (primary.collapsed) {
                            AuraBtn(
                                onClick = { onExpand(primary.id) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                AuraIcon(AuraIconName.Expand, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("expand", fontSize = 12.sp, color = AuraText)
                            }
                        } else {
                            AuraBtn(
                                onClick = onCollapse,
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                AuraIcon(AuraIconName.Collapse, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("collapse", fontSize = 12.sp, color = AuraText)
                            }
                        }
                        AuraBtn(
                            onClick = onReflow,
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            AuraIcon(AuraIconName.Layout, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("auto-layout", fontSize = 12.sp, color = AuraText)
                        }
                    }
                    AuraBtn(
                        onClick = onSimplify,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AuraIcon(AuraIconName.Spark, modifier = Modifier.size(14.dp), tint = AuraAmber)
                        Spacer(Modifier.width(6.dp))
                        Text("fold constants", fontSize = 12.sp, color = AuraText)
                    }
                    AuraBtn(
                        onClick = onOpenSymbolicSolve,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AuraIcon(AuraIconName.Solve, modifier = Modifier.size(14.dp), tint = AuraViolet)
                        Spacer(Modifier.width(6.dp))
                        Text("symbolic simplify & solve", fontSize = 12.sp, color = AuraViolet)
                    }
                    AuraBtn(
                        onClick = onDelete,
                        variant = AuraBtnVariant.Danger,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AuraIcon(AuraIconName.Trash, modifier = Modifier.size(14.dp), tint = AuraRose)
                        Spacer(Modifier.width(6.dp))
                        Text("delete vertex", fontSize = 12.sp, color = AuraRose)
                    }
                }
            } else if (selectedNodes.size > 1) {
                // Multi-Selection Inspector
                AuraPanel(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        EyebrowText("Selection")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            selectedNodes.forEach { n ->
                                AuraBtn(
                                    onClick = { onSelect(listOf(n.id)) },
                                    height = 36.dp
                                ) {
                                    MathText(tex = n.token.ifEmpty { n.label }, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AuraBtn(onClick = onCollapse, enabled = !busy, modifier = Modifier.weight(1f)) {
                        AuraIcon(AuraIconName.Collapse, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("collapse all", fontSize = 12.sp, color = AuraText)
                    }
                    AuraBtn(onClick = onReflow, enabled = !busy, modifier = Modifier.weight(1f)) {
                        AuraIcon(AuraIconName.Layout, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("auto-layout", fontSize = 12.sp, color = AuraText)
                    }
                }
                AuraBtn(onClick = onSimplify, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    AuraIcon(AuraIconName.Spark, modifier = Modifier.size(14.dp), tint = AuraAmber)
                    Spacer(Modifier.width(6.dp))
                    Text("fold constants", fontSize = 12.sp, color = AuraText)
                }
                AuraBtn(
                    onClick = onDelete,
                    variant = AuraBtnVariant.Danger,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AuraIcon(AuraIconName.Trash, modifier = Modifier.size(14.dp), tint = AuraRose)
                    Spacer(Modifier.width(6.dp))
                    Text("delete ${selectedNodes.size} vertices", fontSize = 12.sp, color = AuraRose)
                }
            } else {
                // Graph Overview (Nothing selected)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Derived expressions")
                        Text(expressions.size.toString(), fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                    }
                    expressions.forEachIndexed { idx, expr ->
                        AuraPanel(
                            cornerRadius = 14.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(expr.nodeIds) }
                        ) {
                            Column(modifier = Modifier.padding(11.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        MathBlock(tex = expr.latex, fontSize = 15.sp)
                                    }
                                    AuraBtn(
                                        onClick = { onCopy(expr.latex, "LaTeX copied") },
                                        height = 24.dp,
                                        horizontalPadding = 6.dp
                                    ) {
                                        AuraIcon(AuraIconName.Copy, modifier = Modifier.size(11.dp))
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "cluster ${idx + 1} · ${expr.nodeIds.size} vertices",
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 9.5.sp,
                                    color = AuraMuted
                                )
                            }
                        }
                    }
                }

                // Coherence summary
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Coherence")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AuraPill(
                                text = "${allErrors.size} errors",
                                textColor = if (allErrors.isNotEmpty()) AuraRose else AuraTeal
                            )
                            AuraPill(text = "${allWarnings.size} open", textColor = AuraAmber)
                        }
                    }
                    if (diagnostics.isEmpty()) {
                        Text(
                            text = "Every bound port is type-coherent. Drag a vertex to explore new bindings.",
                            fontSize = 12.sp,
                            color = AuraTeal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AuraTeal.copy(alpha = 0.06f))
                                .border(1.dp, AuraTeal.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        )
                    } else {
                        diagnostics.take(6).forEach { entry ->
                            val isErr = entry.severity == DiagnosticSeverity.Error
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isErr) AuraRose.copy(alpha = 0.06f) else Color(0x06FFFFFF))
                                    .border(1.dp, if (isErr) AuraRose.copy(alpha = 0.3f) else AuraLine, RoundedCornerShape(12.dp))
                                    .clickable { onSelect(listOf(entry.nodeId)) }
                                    .padding(10.dp)
                            ) {
                                AuraIcon(
                                    name = if (isErr) AuraIconName.Error else AuraIconName.Warning,
                                    tint = if (isErr) AuraRose else AuraAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(entry.message, fontSize = 11.5.sp, color = if (isErr) AuraRose else AuraDim)
                            }
                        }
                    }
                }

                // Graph Transactions History
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Graph transactions")
                        AuraIcon(AuraIconName.History, modifier = Modifier.size(13.dp), tint = AuraMuted)
                    }
                    if (history.isEmpty()) {
                        Text("No mutations recorded yet.", fontSize = 11.5.sp, color = AuraMuted)
                    } else {
                        history.take(10).forEach { tx ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (tx.undone) AuraMuted else AuraTeal)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.summary,
                                        fontSize = 11.sp,
                                        color = if (tx.undone) AuraMuted else AuraText,
                                        textDecoration = if (tx.undone) TextDecoration.LineThrough else TextDecoration.None,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "#${tx.seq} · ${tx.action}${if (tx.undone) " · undone" else ""}",
                                        fontFamily = AuraMonoFontFamily,
                                        fontSize = 9.sp,
                                        color = AuraMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* NEW: Symbolic Algebra & Step-by-Step Equation Solver Panel         */
/* ------------------------------------------------------------------ */

@Composable
fun SymbolicSolvePanel(
    graph: AuraGraph,
    selected: List<String>,
    busy: Boolean,
    onApplyStep: (RewriteStep) -> Unit,
    onFoldConstants: () -> Unit,
    onSelect: (List<String>) -> Unit
) {
    val index = remember(graph) { Engine.indexGraph(graph) }
    val clusters = remember(graph) { Evaluate.analyzeGraph(graph) }
    var pickedRootId by remember { mutableStateOf<String?>(null) }

    val selectedRootId = remember(selected, index) {
        selected.firstOrNull()?.let { Engine.componentRoot(index, it)?.id ?: it }
    }
    val activeCluster = remember(clusters, pickedRootId, selectedRootId) {
        clusters.find { it.rootId == (selectedRootId ?: pickedRootId) } ?: clusters.firstOrNull()
    }

    var targetVar by remember(activeCluster?.rootId) {
        mutableStateOf(activeCluster?.variables?.firstOrNull() ?: "x")
    }

    val simplifySteps = remember(graph, activeCluster?.rootId) {
        SymbolicEngine.simplifyStepByStep(graph, activeCluster?.rootId)
    }

    val solveSteps = remember(graph, activeCluster?.rootId, targetVar) {
        if (activeCluster != null && activeCluster.isRelation && targetVar.isNotBlank()) {
            SymbolicEngine.solveForVariable(graph, activeCluster.rootId, targetVar)
        } else emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                EyebrowText("Symbolic CAS & Solver")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Step-by-step derivation",
                    fontFamily = AuraDisplayFontFamily,
                    fontSize = 17.sp,
                    color = AuraText
                )
            }
            AuraPill(
                text = "${simplifySteps.size + solveSteps.size} steps",
                textColor = AuraViolet,
                borderColor = AuraViolet.copy(alpha = 0.4f)
            )
        }
        AuraHairline()

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            if (clusters.isNotEmpty()) {
                EyebrowText("Target cluster")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    clusters.forEach { c ->
                        val isCurrent = activeCluster?.rootId == c.rootId
                        AuraPill(
                            text = MathFormatter.simplifySymbols(c.latex),
                            textColor = if (isCurrent) AuraAmber else AuraDim,
                            borderColor = if (isCurrent) AuraAmber.copy(alpha = 0.55f) else AuraLine,
                            backgroundColor = if (isCurrent) AuraAmber.copy(alpha = 0.12f) else Color(0x0D94A3B8),
                            onClick = {
                                pickedRootId = c.rootId
                                onSelect(c.nodeIds)
                            }
                        )
                    }
                }
            }

            if (activeCluster != null) {
                AuraPanel(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        EyebrowText("Active expression")
                        Spacer(Modifier.height(6.dp))
                        MathBlock(tex = activeCluster.latex, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AuraPill(text = "${activeCluster.nodeIds.size} vertices")
                            if (activeCluster.value != null) {
                                AuraPill(text = "= ${Evaluate.formatNumber(activeCluster.value)}", textColor = AuraTeal)
                            }
                            if (activeCluster.isRelation) {
                                AuraPill(text = "equation", textColor = AuraTeal)
                            }
                        }
                    }
                }

                // Algebraic Simplification Steps
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Algebraic simplification")
                        AuraBtn(
                            onClick = onFoldConstants,
                            enabled = !busy,
                            height = 26.dp,
                            horizontalPadding = 8.dp
                        ) {
                            AuraIcon(AuraIconName.Spark, modifier = Modifier.size(12.dp), tint = AuraAmber)
                            Spacer(Modifier.width(4.dp))
                            Text("fold constants", fontSize = 10.sp, color = AuraAmber)
                        }
                    }

                    if (simplifySteps.isEmpty()) {
                        Text(
                            text = "This cluster is already in canonical simplified form.",
                            fontSize = 11.5.sp,
                            color = AuraMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x06FFFFFF))
                                .border(1.dp, AuraLine, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        )
                    } else {
                        simplifySteps.forEachIndexed { idx, step ->
                            DerivationStepCard(
                                stepNumber = idx + 1,
                                step = step,
                                busy = busy,
                                onApply = { onApplyStep(step) }
                            )
                        }
                    }
                }

                // Step-by-Step Equation Isolation (when cluster is a relation with variables)
                if (activeCluster.isRelation && activeCluster.variables.isNotEmpty()) {
                    AuraHairline()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EyebrowText("Isolate variable (Step-by-Step Solver)")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Solve for:", fontSize = 11.5.sp, color = AuraDim)
                            activeCluster.variables.forEach { v ->
                                val chosen = v == targetVar
                                AuraPill(
                                    text = MathFormatter.simplifySymbols(v),
                                    textColor = if (chosen) AuraAmber else AuraDim,
                                    borderColor = if (chosen) AuraAmber else AuraLine,
                                    backgroundColor = if (chosen) AuraAmber.copy(alpha = 0.15f) else Color(0x0D94A3B8),
                                    onClick = { targetVar = v }
                                )
                            }
                        }

                        if (solveSteps.isEmpty()) {
                            Text(
                                text = "${MathFormatter.simplifySymbols(targetVar)} is already isolated on the left-hand side.",
                                fontSize = 11.5.sp,
                                color = AuraTeal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AuraTeal.copy(alpha = 0.06f))
                                    .border(1.dp, AuraTeal.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            )
                        } else {
                            solveSteps.forEachIndexed { idx, step ->
                                DerivationStepCard(
                                    stepNumber = idx + 1,
                                    step = step,
                                    busy = busy,
                                    onApply = { onApplyStep(step) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DerivationStepCard(
    stepNumber: Int,
    step: RewriteStep,
    busy: Boolean,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x09FFFFFF))
            .border(1.dp, AuraViolet.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AuraPill(
                text = "STEP $stepNumber",
                textColor = AuraViolet,
                borderColor = AuraViolet.copy(alpha = 0.4f),
                backgroundColor = AuraViolet.copy(alpha = 0.12f)
            )
            Text(
                text = step.ruleName,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuraText,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(step.description, fontSize = 10.5.sp, color = AuraDim)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x40000000))
                .padding(8.dp)
        ) {
            MathBlock(tex = step.afterLatex, fontSize = 15.sp, color = AuraTeal)
        }
        Spacer(Modifier.height(6.dp))
        AuraBtn(
            onClick = onApply,
            variant = AuraBtnVariant.Primary,
            enabled = !busy,
            height = 28.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            AuraIcon(AuraIconName.Check, modifier = Modifier.size(12.dp), tint = AuraAmberDarkText)
            Spacer(Modifier.width(5.dp))
            Text("commit step to canvas", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AuraAmberDarkText)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Upgraded PlotPanel: 2D Traces + 3D Interactive Surface + Sliders   */
/* ------------------------------------------------------------------ */

@Composable
fun PlotPanel(
    graph: AuraGraph,
    onSelect: (List<String>) -> Unit
) {
    val analyses = remember(graph) { Evaluate.analyzeGraph(graph) }
    val plottable = remember(analyses) { analyses.filter { it.plottable != null || it.plottable3D != null } }
    val constants = remember(analyses) { analyses.filter { it.value != null } }

    var pickedRoot by remember { mutableStateOf<String?>(null) }
    var domainMin by remember { mutableFloatStateOf(-6f) }
    var domainMax by remember { mutableFloatStateOf(6f) }
    var hoverPoint by remember { mutableStateOf<SamplePoint2D?>(null) }
    var plotMode3D by remember { mutableStateOf(false) }

    // 3D Surface rotation angles
    var yaw by remember { mutableFloatStateOf(0.68f) }
    var pitch by remember { mutableFloatStateOf(0.52f) }

    // Interactive parameter values for extra free variables
    var paramValues by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    val active = plottable.find { it.rootId == pickedRoot } ?: plottable.firstOrNull()
    val target2D = active?.plottable
    val target3D = active?.plottable3D

    LaunchedEffect(active?.rootId) {
        if (target3D != null && target2D == null) plotMode3D = true
        else if (target3D == null) plotMode3D = false
    }

    val activeParams = if (plotMode3D && target3D != null) target3D.parameters else (target2D?.parameters ?: emptyList())
    val extraEnv = remember(paramValues, activeParams) {
        activeParams.associateWith { (paramValues[it] ?: 1.0f).toDouble() }
    }

    val samples2D = remember(graph, target2D, domainMin, domainMax, extraEnv) {
        if (target2D != null) {
            Evaluate.plotSamples(graph, target2D.yRootId, domainMin, domainMax, 240, target2D.variable, extraEnv)
        } else emptyList()
    }
    val range2D = remember(samples2D) { Evaluate.sampleRange(samples2D) }

    val surface3D = remember(graph, target3D, domainMin, domainMax, extraEnv) {
        if (target3D != null) {
            Evaluate.plotSurface3D(
                graph = graph,
                exprRootId = target3D.zRootId,
                min = max(-5f, domainMin),
                max = min(5f, domainMax),
                gridSize = 18,
                xVar = target3D.xVar,
                yVar = target3D.yVar,
                extraEnv = extraEnv
            )
        } else null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                EyebrowText("Plot view")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        plotMode3D && target3D != null -> "3D surface mesh"
                        target2D != null -> "Numeric trace"
                        else -> "No plottable object"
                    },
                    fontFamily = AuraDisplayFontFamily,
                    fontSize = 17.sp,
                    color = AuraText
                )
            }
            if (target3D != null) {
                AuraBtn(
                    onClick = { plotMode3D = !plotMode3D },
                    variant = if (plotMode3D) AuraBtnVariant.Active else AuraBtnVariant.Default,
                    height = 28.dp,
                    horizontalPadding = 8.dp
                ) {
                    AuraIcon(AuraIconName.Cube3D, modifier = Modifier.size(13.dp), tint = if (plotMode3D) AuraAmber else AuraDim)
                    Spacer(Modifier.width(4.dp))
                    Text(if (plotMode3D) "3D surface" else "2D slice", fontSize = 10.5.sp, color = AuraText)
                }
            }
        }
        AuraHairline()

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            if (plottable.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    plottable.forEach { entry ->
                        val isCurrent = active?.rootId == entry.rootId
                        AuraPill(
                            text = MathFormatter.simplifySymbols(entry.latex),
                            textColor = if (isCurrent) AuraAmber else AuraDim,
                            borderColor = if (isCurrent) AuraAmber.copy(alpha = 0.5f) else AuraLine,
                            backgroundColor = if (isCurrent) AuraAmber.copy(alpha = 0.12f) else Color(0x0D94A3B8),
                            onClick = { pickedRoot = entry.rootId }
                        )
                    }
                }
            }

            if (active != null && (target2D != null || target3D != null)) {
                AuraPanel(cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x33000000))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            MathText(
                                tex = if (plotMode3D && target3D != null) target3D.zLatex else (target2D?.yLatex ?: active.latex),
                                fontSize = 14.sp,
                                color = AuraText
                            )
                            Text(
                                text = if (plotMode3D && target3D != null) {
                                    "f(${MathFormatter.simplifySymbols(target3D.xVar)}, ${MathFormatter.simplifySymbols(target3D.yVar)})"
                                } else {
                                    "f(${MathFormatter.simplifySymbols(target2D?.variable ?: "x")}) · ${samples2D.size} pts"
                                },
                                fontFamily = AuraMonoFontFamily,
                                fontSize = 9.5.sp,
                                color = AuraMuted
                            )
                        }
                        AuraHairline()

                        if (plotMode3D && surface3D != null && target3D != null) {
                            // 3D Isometric Rotatable Surface Canvas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            yaw += dragAmount.x * 0.01f
                                            pitch = (pitch + dragAmount.y * 0.01f).coerceIn(0.15f, 1.35f)
                                        }
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val scale = min(size.width, size.height) * 0.36f
                                    val spanXY = max(1f, min(5f, domainMax) - max(-5f, domainMin))
                                    val spanZ = max(1f, surface3D.maxZ - surface3D.minZ)
                                    val midZ = (surface3D.maxZ + surface3D.minZ) / 2f

                                    fun project(pt: SurfacePoint3D): Offset {
                                        val nx = pt.x / (spanXY * 0.55f)
                                        val ny = pt.y / (spanXY * 0.55f)
                                        val nz = (pt.z - midZ) / (spanZ * 0.65f)
                                        val rx = nx * cos(yaw) - ny * sin(yaw)
                                        val ry = nx * sin(yaw) + ny * cos(yaw)
                                        val sx = cx + rx * scale
                                        val sy = cy + (ry * sin(pitch) - nz * cos(pitch)) * scale
                                        return Offset(sx, sy)
                                    }

                                    val grid = surface3D.points
                                    val n = surface3D.gridSize
                                    for (i in 0..n) {
                                        for (j in 0..n) {
                                            val p = grid[i][j] ?: continue
                                            val pScr = project(p)
                                            val t = ((p.z - surface3D.minZ) / spanZ).coerceIn(0f, 1f)
                                            val col = androidx.compose.ui.graphics.lerp(AuraTeal, AuraAmber, t)
                                            if (i < n) {
                                                grid[i + 1][j]?.let { pNext ->
                                                    drawLine(col.copy(alpha = 0.78f), pScr, project(pNext), strokeWidth = 1.6f)
                                                }
                                            }
                                            if (j < n) {
                                                grid[i][j + 1]?.let { pNext ->
                                                    drawLine(col.copy(alpha = 0.78f), pScr, project(pNext), strokeWidth = 1.6f)
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = "drag to rotate 3D surface · z ∈ [${String.format(Locale.US, "%.2f", surface3D.minZ)}, ${String.format(Locale.US, "%.2f", surface3D.maxZ)}]",
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = AuraMuted,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp)
                                )
                            }
                        } else {
                            // 2D Function Trace Canvas with Scrubbing
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .pointerInput(samples2D, domainMin, domainMax) {
                                        detectTapGestures { offset ->
                                            if (samples2D.isEmpty()) return@detectTapGestures
                                            val padL = 36f
                                            val padR = 14f
                                            val plotW = size.width - padL - padR
                                            val xVal = domainMin + ((offset.x - padL) / plotW) * (domainMax - domainMin)
                                            hoverPoint = samples2D.minByOrNull { abs(it.x - xVal) }
                                        }
                                    }
                                    .pointerInput(samples2D, domainMin, domainMax) {
                                        detectDragGestures(
                                            onDragEnd = { hoverPoint = null },
                                            onDragCancel = { hoverPoint = null }
                                        ) { change, _ ->
                                            change.consume()
                                            if (samples2D.isEmpty()) return@detectDragGestures
                                            val padL = 36f
                                            val padR = 14f
                                            val plotW = size.width - padL - padR
                                            val xVal = domainMin + ((change.position.x - padL) / plotW) * (domainMax - domainMin)
                                            hoverPoint = samples2D.minByOrNull { abs(it.x - xVal) }
                                        }
                                    }
                            ) {
                                val padL = 36f
                                val padR = 14f
                                val padT = 14f
                                val padB = 24f
                                val plotW = size.width - padL - padR
                                val plotH = size.height - padT - padB

                                drawRoundRect(
                                    color = Color(0x80020617),
                                    topLeft = Offset(padL, padT),
                                    size = Size(plotW, plotH),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )

                                val yMin = range2D.first
                                val yMax = range2D.second
                                fun px(x: Float) = padL + ((x - domainMin) / (domainMax - domainMin)) * plotW
                                fun py(y: Float) = padT + plotH - ((y - yMin) / (yMax - yMin)) * plotH

                                // Grid lines
                                for (k in 1..4) {
                                    val gx = padL + (plotW * k) / 5f
                                    drawLine(Color(0x1A94A3B8), Offset(gx, padT), Offset(gx, padT + plotH), 1f)
                                    val gy = padT + (plotH * k) / 5f
                                    drawLine(Color(0x1A94A3B8), Offset(padL, gy), Offset(padL + plotW, gy), 1f)
                                }

                                // Axes at x=0 and y=0
                                if (yMin <= 0f && yMax >= 0f) {
                                    drawLine(Color(0x5994A3B8), Offset(padL, py(0f)), Offset(padL + plotW, py(0f)), 1.3f)
                                }
                                if (domainMin <= 0f && domainMax >= 0f) {
                                    drawLine(Color(0x5994A3B8), Offset(px(0f), padT), Offset(px(0f), padT + plotH), 1.3f)
                                }

                                // Trace curve
                                if (samples2D.isNotEmpty()) {
                                    val path = Path()
                                    var prevInside = false
                                    val spanY = yMax - yMin
                                    for (pt in samples2D) {
                                        val inside = pt.y in (yMin - spanY * 0.6f)..(yMax + spanY * 0.6f)
                                        if (!inside) {
                                            prevInside = false
                                            continue
                                        }
                                        val sx = px(pt.x)
                                        val sy = py(pt.y)
                                        if (!prevInside) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                                        prevInside = true
                                    }
                                    drawPath(
                                        path = path,
                                        color = AuraAmber,
                                        style = DrawStroke(width = 2.4f, cap = StrokeCap.Round)
                                    )
                                }

                                // Hover crosshair
                                hoverPoint?.let { hp ->
                                    val hx = px(hp.x)
                                    val hy = py(hp.y)
                                    drawLine(
                                        color = AuraTeal.copy(alpha = 0.65f),
                                        start = Offset(hx, padT),
                                        end = Offset(hx, padT + plotH),
                                        strokeWidth = 1.3f
                                    )
                                    drawCircle(color = AuraTeal, radius = 4.5f, center = Offset(hx, hy))
                                }
                            }
                        }

                        AuraHairline()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = hoverPoint?.let {
                                    "f(${String.format(Locale.US, "%.2f", it.x)}) = ${String.format(Locale.US, "%.3f", it.y)}"
                                } ?: "domain [${domainMin.roundToInt()}, ${domainMax.roundToInt()}]",
                                fontFamily = AuraMonoFontFamily,
                                fontSize = 10.sp,
                                color = AuraTeal
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AuraBtn(
                                    onClick = {
                                        val c = (domainMin + domainMax) / 2f
                                        val h = ((domainMax - domainMin) / 2f) * 0.65f
                                        domainMin = c - h
                                        domainMax = c + h
                                    },
                                    height = 24.dp,
                                    horizontalPadding = 7.dp
                                ) {
                                    Text("in", fontSize = 10.sp, color = AuraDim)
                                }
                                AuraBtn(
                                    onClick = {
                                        val c = (domainMin + domainMax) / 2f
                                        val h = ((domainMax - domainMin) / 2f) * 1.5f
                                        domainMin = c - h
                                        domainMax = c + h
                                    },
                                    height = 24.dp,
                                    horizontalPadding = 7.dp
                                ) {
                                    Text("out", fontSize = 10.sp, color = AuraDim)
                                }
                                AuraBtn(
                                    onClick = {
                                        domainMin = -6f
                                        domainMax = 6f
                                    },
                                    height = 24.dp,
                                    horizontalPadding = 7.dp
                                ) {
                                    Text("reset", fontSize = 10.sp, color = AuraDim)
                                }
                                AuraBtn(
                                    onClick = { onSelect(active.nodeIds) },
                                    height = 24.dp,
                                    horizontalPadding = 7.dp
                                ) {
                                    Text("locate", fontSize = 10.sp, color = AuraDim)
                                }
                            }
                        }
                    }
                }

                // Interactive Parameter Sliders for additional free variables
                if (activeParams.isNotEmpty()) {
                    AuraPanel(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            EyebrowText("Parameter sliders")
                            Spacer(Modifier.height(6.dp))
                            activeParams.forEach { param ->
                                val v = paramValues[param] ?: 1.0f
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        MathText(tex = param, fontSize = 13.sp, color = AuraAmber)
                                        Text(
                                            text = String.format(Locale.US, "%.2f", v),
                                            fontFamily = AuraMonoFontFamily,
                                            fontSize = 10.5.sp,
                                            color = AuraTeal
                                        )
                                    }
                                    Slider(
                                        value = v,
                                        onValueChange = { nextV ->
                                            paramValues = paramValues.toMutableMap().apply { put(param, nextV) }
                                        },
                                        valueRange = -5f..5f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = AuraAmber,
                                            activeTrackColor = AuraAmber,
                                            inactiveTrackColor = AuraLine
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "A cluster becomes plottable when it is a function of one or more variables — bind a variable into a function vertex, or build y = f(x) or z = f(x, y). Values are sampled by walking the graph's ports.",
                    fontSize = 12.sp,
                    color = AuraMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x06FFFFFF))
                        .border(1.dp, AuraLine, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                )
            }

            // Evaluated Constant Clusters
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EyebrowText("Evaluated clusters")
                    Text(constants.size.toString(), fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                }
                if (constants.isEmpty()) {
                    Text(
                        text = "No fully numeric cluster yet. Fold constants from the inspector to evaluate one.",
                        fontSize = 11.5.sp,
                        color = AuraMuted
                    )
                } else {
                    constants.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x06FFFFFF))
                                .border(1.dp, AuraLine, RoundedCornerShape(12.dp))
                                .clickable { onSelect(entry.nodeIds) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            MathText(tex = entry.latex, fontSize = 13.5.sp, color = AuraText)
                            Text(
                                text = "= ${Evaluate.formatNumber(entry.value ?: 0.0)}",
                                fontFamily = AuraMonoFontFamily,
                                fontSize = 10.5.sp,
                                color = AuraTeal
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* DiscoveryPanel: Auto-Connect + Formal Proof Assistant              */
/* ------------------------------------------------------------------ */

private val BAND_COPY = mapOf(
    "literal" to "Nearby clusters only · equality and addition · strictest typing",
    "cautious" to "Adjacent clusters · arithmetic and comparisons · refutations suppressed",
    "balanced" to "Whole canvas · ratios, powers, sets and arrows · counterexamples surfaced",
    "wild" to "Everything · speculative arrows and containment · novelty outranks proximity"
)

@Composable
fun DiscoveryPanel(
    busy: Boolean,
    discovering: Boolean,
    scale: Float,
    families: List<DiscoveryFamily>,
    stats: DiscoveryResult?,
    proposals: List<Proposal>,
    dismissed: List<String>,
    appliedCount: Int,
    log: List<DiscoveryEntity>,
    focusOnly: Boolean,
    selectionSize: Int,
    onToggleFocus: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onToggleFamily: (DiscoveryFamily) -> Unit,
    onRun: () -> Unit,
    onApply: (Proposal) -> Unit,
    onApplyAll: (List<Proposal>) -> Unit,
    onDismiss: (String) -> Unit,
    onLocate: (Proposal) -> Unit
) {
    val visible = remember(proposals, dismissed) { proposals.filter { it.id !in dismissed } }
    val holding = remember(visible) { visible.filter { it.verdict == Verdict.Holds } }
    val band = Discover.scaleBand(scale)
    var expandedProofIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                EyebrowText("Discovery engine")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Auto-connect",
                    fontFamily = AuraDisplayFontFamily,
                    fontSize = 17.sp,
                    color = AuraText
                )
            }
            AuraPill(
                text = "${holding.size} hold",
                leadingIcon = AuraIconName.Bind,
                textColor = if (holding.isNotEmpty()) AuraTeal else AuraDim,
                borderColor = if (holding.isNotEmpty()) AuraTeal.copy(alpha = 0.4f) else AuraLine
            )
        }
        AuraHairline()

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // Connection Scale Slider Card
            AuraPanel(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Connection scale")
                        Text(
                            text = band.uppercase(),
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 10.sp,
                            color = AuraAmber
                        )
                    }
                    Slider(
                        value = scale,
                        onValueChange = onScaleChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AuraAmber,
                            activeTrackColor = AuraAmber,
                            inactiveTrackColor = AuraLine
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("literal", "cautious", "balanced", "wild").forEach { b ->
                            Text(b, fontFamily = AuraMonoFontFamily, fontSize = 9.sp, color = AuraMuted)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(BAND_COPY[band] ?: "", fontSize = 11.sp, color = AuraDim)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x40000000))
                            .border(1.dp, AuraLine, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "radius ×${String.format(Locale.US, "%.2f", 0.18f + 0.82f * scale)}",
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 9.5.sp,
                            color = AuraMuted
                        )
                        Text(
                            text = "depth ${(8 + scale * 20).roundToInt()} candidates",
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 9.5.sp,
                            color = AuraMuted
                        )
                    }
                }
            }

            // What to try (Family filters)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EyebrowText("What to try")
                    Text("${families.size}/6", fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraMuted)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    DiscoveryFamily.entries.forEach { fam ->
                        val active = fam in families
                        AuraPill(
                            text = fam.label,
                            textColor = if (active) AuraAmber else AuraDim,
                            borderColor = if (active) AuraAmber.copy(alpha = 0.55f) else AuraLine,
                            backgroundColor = if (active) AuraAmber.copy(alpha = 0.12f) else Color(0x0D94A3B8),
                            onClick = { onToggleFamily(fam) }
                        )
                    }
                }
            }

            AuraBtn(
                onClick = onToggleFocus,
                enabled = selectionSize > 0,
                variant = if (focusOnly && selectionSize > 0) AuraBtnVariant.Active else AuraBtnVariant.Default,
                modifier = Modifier.fillMaxWidth()
            ) {
                AuraIcon(AuraIconName.Marquee, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (selectionSize == 0) "selection only (nothing selected)" else "selection only ($selectionSize selected)",
                    fontSize = 11.sp,
                    color = AuraText
                )
            }

            AuraBtn(
                onClick = onRun,
                variant = AuraBtnVariant.Primary,
                enabled = !busy && !discovering && families.isNotEmpty(),
                testTag = "run_discovery_button",
                modifier = Modifier.fillMaxWidth()
            ) {
                AuraIcon(AuraIconName.Bind, tint = AuraAmberDarkText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (discovering) "probing connections…" else "Discover connections",
                    fontWeight = FontWeight.SemiBold,
                    color = AuraAmberDarkText
                )
            }

            if (stats != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statItems = listOf(
                        Triple("clusters", stats.clusters, AuraText),
                        Triple("probes", stats.considered, AuraText),
                        Triple("holds", stats.holds, AuraTeal),
                        Triple("refuted", stats.refuted, AuraRose)
                    )
                    statItems.forEach { (label, count, col) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x06FFFFFF))
                                .border(1.dp, AuraLine, RoundedCornerShape(10.dp))
                                .padding(vertical = 6.dp)
                        ) {
                            Text(count.toString(), fontFamily = AuraMonoFontFamily, fontSize = 12.sp, color = col)
                            Text(label.uppercase(), fontSize = 8.sp, color = AuraMuted)
                        }
                    }
                }
            }

            // Proposals List
            if (visible.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EyebrowText("Proposals")
                    Text(visible.size.toString(), fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                }
                visible.forEach { proposal ->
                    val vCol = when (proposal.verdict) {
                        Verdict.Holds -> AuraTeal
                        Verdict.Refuted -> AuraRose
                        Verdict.Unknown -> AuraDim
                    }
                    val vLabel = when (proposal.verdict) {
                        Verdict.Holds -> "holds true"
                        Verdict.Refuted -> "refuted"
                        Verdict.Unknown -> "unverifiable"
                    }
                    val vIcon = when (proposal.verdict) {
                        Verdict.Holds -> AuraIconName.Check
                        Verdict.Refuted -> AuraIconName.Error
                        Verdict.Unknown -> AuraIconName.Spark
                    }
                    val proofExpanded = proposal.id in expandedProofIds

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x06FFFFFF))
                            .border(1.dp, vCol.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuraPill(
                                text = vLabel.uppercase(),
                                leadingIcon = vIcon,
                                textColor = vCol,
                                borderColor = vCol.copy(alpha = 0.4f),
                                backgroundColor = vCol.copy(alpha = 0.14f)
                            )
                            AuraPill(text = proposal.kind)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${(proposal.score * 100).roundToInt()}",
                                fontFamily = AuraMonoFontFamily,
                                fontSize = 10.sp,
                                color = AuraMuted
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        MathBlock(tex = proposal.previewLatex, fontSize = 16.sp)
                        if (proposal.hint != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "recognised: ${proposal.hint}",
                                fontSize = 10.5.sp,
                                color = AuraAmber,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AuraAmber.copy(alpha = 0.08f))
                                    .border(1.dp, AuraAmber.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(proposal.rationale, fontSize = 11.sp, color = AuraDim)
                        Spacer(Modifier.height(4.dp))
                        Text(proposal.evidence, fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraMuted)

                        if (proposal.counterexample != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "counterexample · ${proposal.counterexample}",
                                fontFamily = AuraMonoFontFamily,
                                fontSize = 9.5.sp,
                                color = AuraRose,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AuraRose.copy(alpha = 0.08f))
                                    .border(1.dp, AuraRose.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Formal Proof Assistant Trace Toggle
                        if (proposal.proofTrace != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x26000000))
                                    .clickable {
                                        expandedProofIds = if (proofExpanded) expandedProofIds - proposal.id else expandedProofIds + proposal.id
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Proof trace · ${proposal.proofTrace.method.label}",
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 9.5.sp,
                                    color = AuraViolet
                                )
                                Text(
                                    text = if (proofExpanded) "hide" else "inspect",
                                    fontFamily = AuraMonoFontFamily,
                                    fontSize = 9.5.sp,
                                    color = AuraAmber
                                )
                            }
                            if (proofExpanded) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x40000000))
                                        .border(1.dp, AuraViolet.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    proposal.proofTrace.steps.forEach { stepLine ->
                                        Text(
                                            text = MathFormatter.simplifySymbols(stepLine),
                                            fontFamily = AuraMonoFontFamily,
                                            fontSize = 9.5.sp,
                                            color = AuraText
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AuraBtn(
                                onClick = { onApply(proposal) },
                                variant = AuraBtnVariant.Primary,
                                enabled = !busy,
                                height = 28.dp,
                                modifier = Modifier.weight(1f)
                            ) {
                                AuraIcon(AuraIconName.Check, modifier = Modifier.size(12.dp), tint = AuraAmberDarkText)
                                Spacer(Modifier.width(4.dp))
                                Text("plug in", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = AuraAmberDarkText)
                            }
                            AuraBtn(
                                onClick = { onLocate(proposal) },
                                height = 28.dp,
                                horizontalPadding = 8.dp
                            ) {
                                AuraIcon(AuraIconName.Fit, modifier = Modifier.size(12.dp))
                            }
                            AuraBtn(
                                onClick = { onDismiss(proposal.id) },
                                height = 28.dp,
                                horizontalPadding = 8.dp
                            ) {
                                AuraIcon(AuraIconName.Close, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                if (holding.isNotEmpty()) {
                    AuraBtn(
                        onClick = { onApplyAll(holding) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AuraIcon(AuraIconName.Spark, tint = AuraTeal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("plug in all ${holding.size} that hold true", fontSize = 12.sp, color = AuraTeal)
                    }
                }
            }

            if (appliedCount > 0) {
                Text(
                    text = "$appliedCount discovered connection${if (appliedCount == 1) "" else "s"} plugged in this session.",
                    fontSize = 11.sp,
                    color = AuraTeal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AuraTeal.copy(alpha = 0.08f))
                        .border(1.dp, AuraTeal.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                )
            }

            if (log.isNotEmpty()) {
                EyebrowText("Discovery log")
                log.take(6).forEach { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("scale ${String.format(Locale.US, "%.2f", entry.scale)}", fontFamily = AuraMonoFontFamily, fontSize = 9.sp, color = AuraMuted)
                            Text("${entry.holds} hold", fontFamily = AuraMonoFontFamily, fontSize = 9.sp, color = AuraTeal)
                            Text("${entry.refuted} refuted", fontFamily = AuraMonoFontFamily, fontSize = 9.sp, color = AuraRose)
                            Spacer(Modifier.weight(1f))
                            Text("${entry.clusters} clusters", fontFamily = AuraMonoFontFamily, fontSize = 9.sp, color = AuraMuted)
                        }
                        Text(entry.summary, fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* VerificationPanel: Executable Specifications Suite                 */
/* ------------------------------------------------------------------ */

@Composable
fun VerificationPanel() {
    var report by remember { mutableStateOf<SuiteReport?>(null) }

    LaunchedEffect(Unit) {
        report = KernelTests.runKernelSuite()
    }

    val current = report
    val ok = current != null && current.failed == 0

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                EyebrowText("Executable specification")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Kernel verification",
                    fontFamily = AuraDisplayFontFamily,
                    fontSize = 17.sp,
                    color = AuraText
                )
            }
            AuraPill(
                text = if (current != null) "${current.passed}/${current.total}" else "…",
                dotColor = if (ok) AuraTeal else AuraRose,
                textColor = if (ok) AuraTeal else AuraRose,
                borderColor = if (ok) AuraTeal.copy(alpha = 0.4f) else AuraRose.copy(alpha = 0.4f)
            )
        }
        AuraHairline()

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text(
                text = "Every claim made about the typed hypergraph — type admissibility, precedence-preserving serialisation, numeric & 3D evaluation, symbolic solving, discovery verification, and collapse reversibility — is asserted head-lessly.",
                fontSize = 11.5.sp,
                color = AuraDim
            )

            AuraBtn(
                onClick = { report = KernelTests.runKernelSuite() },
                modifier = Modifier.fillMaxWidth()
            ) {
                AuraIcon(AuraIconName.Spark, tint = AuraAmber, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("re-run suite", fontSize = 12.sp, color = AuraText)
            }

            if (current != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val items = listOf(
                        Triple("specs", current.total, AuraText),
                        Triple("passed", current.passed, AuraTeal),
                        Triple("failed", current.failed, if (current.failed > 0) AuraRose else AuraMuted)
                    )
                    items.forEach { (label, v, col) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x06FFFFFF))
                                .border(1.dp, AuraLine, RoundedCornerShape(10.dp))
                                .padding(vertical = 6.dp)
                        ) {
                            Text(v.toString(), fontFamily = AuraMonoFontFamily, fontSize = 12.sp, color = col)
                            Text(label.uppercase(), fontSize = 8.5.sp, color = AuraMuted)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${current.durationMs} ms", fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraMuted)
                    Text("${current.librarySymbols} symbols", fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraMuted)
                    Text("${current.suites.size} suites", fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraMuted)
                }

                current.suites.forEach { suite ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EyebrowText(suite.suite)
                            Text("${suite.passed} ✓", fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraTeal)
                            if (suite.failed > 0) {
                                Text("${suite.failed} ✗", fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraRose)
                            }
                        }
                        current.outcomes.filter { it.suite == suite.suite }.forEach { outcome ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x06FFFFFF))
                                    .border(1.dp, AuraLine, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                AuraIcon(
                                    name = if (outcome.ok) AuraIconName.Check else AuraIconName.Error,
                                    tint = if (outcome.ok) AuraTeal else AuraRose,
                                    modifier = Modifier.size(13.dp)
                                )
                                Column {
                                    Text(outcome.name, fontSize = 11.sp, color = AuraText)
                                    if (outcome.error != null) {
                                        Text(outcome.error, fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraRose)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
