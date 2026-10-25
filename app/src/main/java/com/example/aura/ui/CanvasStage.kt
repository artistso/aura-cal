package com.example.aura.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.kernel.*
import com.example.ui.theme.*
import kotlin.math.*

enum class CanvasTool {
    Pointer, Marquee, Draw
}

enum class SelectionAction {
    Collapse, Expand, Delete, Simplify, Solve
}

private data class ClusterBox(
    val id: String,
    val index: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val nodeIds: List<String>,
    val typeLabel: String,
    val selected: Boolean
)

fun kindAccentColor(kind: NodeKind): Color = when (kind) {
    NodeKind.Relation -> AuraTeal
    NodeKind.Operator -> AuraViolet
    NodeKind.Container -> AuraAmber
    NodeKind.Function -> AuraPink
    NodeKind.Set -> AuraBlue
    NodeKind.Group -> AuraTeal
    else -> AuraDim
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CanvasStage(
    graph: AuraGraph,
    documentId: String,
    documentTitle: String,
    selected: List<String>,
    busy: Boolean,
    onSelect: (List<String>) -> Unit,
    onMove: (List<Pair<String, PointF>>) -> Unit,
    onCommitCandidate: (GhostPath) -> Unit,
    onExpand: (String) -> Unit,
    onViewportCenter: (PointF) -> Unit,
    onPlaceRecognized: (AuraNode, PointF) -> Unit,
    onSelectionAction: (SelectionAction, List<String>) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenInk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var viewportW by remember { mutableFloatStateOf(1080f) }
    var viewportH by remember { mutableFloatStateOf(720f) }

    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(0.9f) }

    var tool by remember { mutableStateOf(CanvasTool.Pointer) }
    var previewPositions by remember(graph) { mutableStateOf<Map<String, PointF>?>(null) }
    var candidates by remember(documentId) { mutableStateOf<List<GhostPath>>(emptyList()) }
    var guides by remember { mutableStateOf<Pair<Float?, Float?>>(null to null) }
    var helpOpen by remember { mutableStateOf(false) }

    // Marquee box in screen pixels
    var marqueeStart by remember { mutableStateOf<Offset?>(null) }
    var marqueeCurrent by remember { mutableStateOf<Offset?>(null) }

    // Direct-on-canvas stylus/pen ink strokes (in world coordinates)
    var strokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var activeStrokePoints by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var inkPredictions by remember { mutableStateOf<List<InkPrediction>>(emptyList()) }

    val displayGraph = remember(graph, previewPositions) {
        val prev = previewPositions ?: return@remember graph
        graph.copy(
            nodes = graph.nodes.map { n ->
                val pt = prev[n.id]
                if (pt != null) n.copy(x = pt.x, y = pt.y) else n
            }
        )
    }

    val index = remember(displayGraph) { Engine.indexGraph(displayGraph) }
    val scopes = remember(displayGraph) { Evaluate.scopeByNode(displayGraph) }
    val diagnostics = remember(displayGraph) { Engine.diagnose(displayGraph) }
    val diagnosticsByNode = remember(diagnostics) { diagnostics.groupBy { it.nodeId } }
    val errorCount = remember(diagnostics) { diagnostics.count { it.severity == DiagnosticSeverity.Error } }
    val warningCount = remember(diagnostics) { diagnostics.count { it.severity == DiagnosticSeverity.Warning } }

    val clusters = remember(displayGraph, index, selected) {
        Engine.componentsOf(displayGraph).mapIndexedNotNull { idx, comp ->
            val nodes = comp.nodeIds.mapNotNull { index.byId[it] }
            if (nodes.isEmpty()) return@mapIndexedNotNull null
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (n in nodes) {
                val hw = estimateWidth(n) / 2f
                val hh = tileHeight() / 2f
                minX = min(minX, n.x - hw)
                maxX = max(maxX, n.x + hw)
                minY = min(minY, n.y - hh)
                maxY = max(maxY, n.y + hh)
            }
            val pad = 22f
            val root = index.byId[comp.rootId]
            ClusterBox(
                id = comp.rootId,
                index = idx + 1,
                x = minX - pad,
                y = minY - pad,
                width = max(70f, maxX - minX + pad * 2f),
                height = max(56f, maxY - minY + pad * 2f),
                nodeIds = comp.nodeIds,
                typeLabel = if (root?.kind == NodeKind.Group) "collapsed" else "expression",
                selected = comp.nodeIds.any { it in selected }
            )
        }
    }

    val selectionBounds = remember(displayGraph, selected) {
        val selNodes = displayGraph.nodes.filter { it.id in selected }
        if (selNodes.isEmpty()) null
        else {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (n in selNodes) {
                val hw = estimateWidth(n) / 2f
                val hh = tileHeight() / 2f
                minX = min(minX, n.x - hw)
                maxX = max(maxX, n.x + hw)
                minY = min(minY, n.y - hh)
                maxY = max(maxY, n.y + hh)
            }
            GraphBounds(minX, minY, maxX, maxY)
        }
    }

    fun fitToBounds(bounds: GraphBounds) {
        if (viewportW <= 10f || viewportH <= 10f) return
        val pad = min(140f, viewportW * 0.14f)
        val spanW = max(240f, bounds.maxX - bounds.minX)
        val spanH = max(200f, bounds.maxY - bounds.minY)
        val targetZoom = min(
            (viewportW - pad * 2f) / spanW,
            (viewportH - pad * 2f) / spanH
        ).coerceIn(0.32f, 1.25f)
        val cx = (bounds.minX + bounds.maxX) / 2f
        val cy = (bounds.minY + bounds.maxY) / 2f
        zoom = targetZoom
        panX = viewportW / 2f - cx * targetZoom
        panY = viewportH / 2f - cy * targetZoom
    }

    fun fitCanvas() {
        fitToBounds(Engine.graphBounds(graph))
    }

    LaunchedEffect(documentId, viewportW, viewportH) {
        if (viewportW > 50f && viewportH > 50f) {
            fitCanvas()
        }
    }

    LaunchedEffect(panX, panY, zoom, viewportW, viewportH) {
        if (viewportW > 0f && viewportH > 0f) {
            onViewportCenter(
                PointF(
                    x = (viewportW / 2f - panX) / zoom,
                    y = (viewportH / 2f - panY) / zoom
                )
            )
        }
    }

    fun toWorld(screenX: Float, screenY: Float): PointF = PointF(
        x = (screenX - panX) / zoom,
        y = (screenY - panY) / zoom
    )

    fun toScreen(worldX: Float, worldY: Float): Offset = Offset(
        x = worldX * zoom + panX,
        y = worldY * zoom + panY
    )

    // Marching ants animation for ghost paths
    val infiniteTransition = rememberInfiniteTransition(label = "ghostDash")
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuraCanvasBg)
            .onSizeChanged { sz ->
                viewportW = sz.width.toFloat()
                viewportH = sz.height.toFloat()
            }
            .testTag("aura_canvas_stage")
            // Background Pan / Zoom / Marquee / Stylus Ink
            .pointerInteropFilter { motionEvent ->
                if (tool != CanvasTool.Draw) return@pointerInteropFilter false
                val action = motionEvent.actionMasked
                val wx = (motionEvent.x - panX) / zoom
                val wy = (motionEvent.y - panY) / zoom
                val pressure = motionEvent.pressure.coerceIn(0.15f, 1.4f)
                val tilt = motionEvent.getAxisValue(android.view.MotionEvent.AXIS_TILT)
                when (action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        inkPredictions = emptyList()
                        activeStrokePoints = listOf(InkPoint(wx, wy, pressure, tilt))
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val last = activeStrokePoints.lastOrNull()
                        if (last == null || hypot(wx - last.x, wy - last.y) >= 1.2f) {
                            activeStrokePoints = activeStrokePoints + InkPoint(wx, wy, pressure, tilt)
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (activeStrokePoints.size > 1) {
                            val nextStrokes = strokes + Stroke(activeStrokePoints)
                            strokes = nextStrokes
                            inkPredictions = InkRecognizer.classifyInk(nextStrokes).take(3)
                        }
                        activeStrokePoints = emptyList()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(tool) {
                if (tool == CanvasTool.Pointer) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val nextZoom = (zoom * gestureZoom).coerceIn(0.28f, 2.4f)
                        val worldX = (centroid.x - panX) / zoom
                        val worldY = (centroid.y - panY) / zoom
                        zoom = nextZoom
                        panX = centroid.x - worldX * nextZoom + pan.x
                        panY = centroid.y - worldY * nextZoom + pan.y
                    }
                } else if (tool == CanvasTool.Marquee) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            marqueeStart = offset
                            marqueeCurrent = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            marqueeCurrent = change.position
                        },
                        onDragEnd = {
                            val s = marqueeStart
                            val c = marqueeCurrent
                            if (s != null && c != null) {
                                val x0 = min(s.x, c.x)
                                val x1 = max(s.x, c.x)
                                val y0 = min(s.y, c.y)
                                val y1 = max(s.y, c.y)
                                if (x1 - x0 > 8f && y1 - y0 > 8f) {
                                    val hits = displayGraph.nodes.filter { node ->
                                        val scr = toScreen(node.x, node.y)
                                        val hw = (estimateWidth(node) * zoom) / 2f
                                        val hh = (tileHeight() * zoom) / 2f
                                        scr.x + hw >= x0 && scr.x - hw <= x1 && scr.y + hh >= y0 && scr.y - hh <= y1
                                    }.map { it.id }
                                    onSelect(hits)
                                }
                            }
                            marqueeStart = null
                            marqueeCurrent = null
                        },
                        onDragCancel = {
                            marqueeStart = null
                            marqueeCurrent = null
                        }
                    )
                }
            }
            .pointerInput(tool) {
                if (tool == CanvasTool.Pointer) {
                    detectTapGestures(
                        onTap = {
                            candidates = emptyList()
                            helpOpen = false
                            onSelect(emptyList())
                        }
                    )
                }
            }
    ) {
        // 1. Canvas Layer: Radial Glows, Dot Grid, Cluster Envelopes, Alignment Guides, Bezier Port Edges, Ghost Paths & Stylus Ink
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle dual radial glows matching .aura-canvas CSS
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x14F0B45A), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                    radius = max(size.width, size.height) * 0.55f
                ),
                radius = max(size.width, size.height) * 0.55f,
                center = Offset(size.width * 0.18f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x105EEAD4), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.84f),
                    radius = max(size.width, size.height) * 0.5f
                ),
                radius = max(size.width, size.height) * 0.5f,
                center = Offset(size.width * 0.85f, size.height * 0.84f)
            )

            // Multi-scale Dot Grid
            val step = (32f * zoom).coerceAtLeast(14f)
            val startX = ((panX % step) + step) % step
            val startY = ((panY % step) + step) % step
            var gx = startX
            while (gx < size.width) {
                var gy = startY
                while (gy < size.height) {
                    drawCircle(
                        color = Color(0x3894A3B8),
                        radius = 1.15f,
                        center = Offset(gx, gy)
                    )
                    gy += step
                }
                gx += step
            }

            // Cluster Envelopes
            for (cluster in clusters) {
                val topLeft = toScreen(cluster.x, cluster.y)
                val w = cluster.width * zoom
                val h = cluster.height * zoom
                val isDimmed = selected.isNotEmpty() && !cluster.selected
                val borderAlpha = if (isDimmed) 0.08f else if (cluster.selected) 0.45f else 0.18f
                val borderCol = if (cluster.selected) AuraAmber.copy(alpha = borderAlpha) else Color(0xFF94A3B8).copy(alpha = borderAlpha)
                val fillCol = if (cluster.selected) AuraAmber.copy(alpha = 0.05f) else Color(0xFF94A3B8).copy(alpha = if (isDimmed) 0.01f else 0.028f)

                drawRoundRect(
                    color = fillCol,
                    topLeft = topLeft,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(22f * zoom, 22f * zoom)
                )
                drawRoundRect(
                    color = borderCol,
                    topLeft = topLeft,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(22f * zoom, 22f * zoom),
                    style = DrawStroke(
                        width = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )
            }

            // Snapping Alignment Guides
            guides.first?.let { guideX ->
                val sx = guideX * zoom + panX
                drawLine(
                    color = AuraTeal.copy(alpha = 0.78f),
                    start = Offset(sx, 0f),
                    end = Offset(sx, size.height),
                    strokeWidth = 1.4f
                )
            }
            guides.second?.let { guideY ->
                val sy = guideY * zoom + panY
                drawLine(
                    color = AuraTeal.copy(alpha = 0.78f),
                    start = Offset(0f, sy),
                    end = Offset(size.width, sy),
                    strokeWidth = 1.4f
                )
            }

            // Directed Port Binding Hyperedges
            for (edge in displayGraph.edges) {
                val parent = index.byId[edge.source] ?: continue
                val child = index.byId[edge.target] ?: continue
                val portDef = findPort(parent, edge.port) ?: continue
                val fromW = Engine.portAnchor(parent, portDef)
                val toW = PointF(child.x, child.y)
                val from = toScreen(fromW.x, fromW.y)
                val to = toScreen(toW.x, toW.y)
                val midX = (from.x + to.x) / 2f
                val midY = (from.y + to.y) / 2f
                val dy = (to.y - from.y) * 0.18f

                val isSelected = parent.id in selected || child.id in selected
                val invalid = diagnostics.any { it.edgeId == edge.id && it.severity == DiagnosticSeverity.Error }

                val path = Path().apply {
                    moveTo(from.x, from.y)
                    quadraticTo(midX, midY + dy, to.x, to.y)
                }
                val strokeColor = when {
                    invalid -> AuraRose
                    isSelected -> AuraAmber
                    else -> Color(0xFF48607F)
                }
                drawPath(
                    path = path,
                    color = strokeColor.copy(alpha = if (invalid || isSelected) 0.95f else 0.62f),
                    style = DrawStroke(
                        width = (if (invalid || isSelected) 2.4f else 1.6f) * zoom.coerceIn(0.75f, 1.35f),
                        cap = StrokeCap.Round,
                        pathEffect = if (invalid) PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f) else null
                    )
                )
                drawCircle(
                    color = if (invalid) AuraRose else Color(0xFF8AA0BD),
                    radius = 3.2f * zoom.coerceIn(0.7f, 1.3f),
                    center = to
                )
                drawCircle(
                    color = if (invalid) AuraRose else AuraAmber,
                    radius = (if (isSelected) 3.6f else 2.5f) * zoom.coerceIn(0.7f, 1.3f),
                    center = from
                )
            }

            // Homeomorphic Ghost Candidate Paths
            val ghostSourceId = candidates.firstOrNull()?.draggedId
            val ghostSourceNode = if (ghostSourceId != null) index.byId[ghostSourceId] else null
            if (ghostSourceNode != null) {
                val startW = Engine.outAnchor(ghostSourceNode)
                val start = toScreen(startW.x, startW.y)
                candidates.forEachIndexed { idx, candidate ->
                    val end = toScreen(candidate.anchor.x, candidate.anchor.y)
                    val bend = (46f + idx * 22f) * zoom
                    val ctrlX = (start.x + end.x) / 2f
                    val ctrlY = min(start.y, end.y) - bend
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        quadraticTo(ctrlX, ctrlY, end.x, end.y)
                    }
                    val col = Color(candidate.colorHex)
                    drawPath(
                        path = path,
                        color = col.copy(alpha = 0.92f),
                        style = DrawStroke(
                            width = 2.8f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f), dashPhase)
                        )
                    )
                    drawCircle(color = col.copy(alpha = 0.25f), radius = 9f, center = end)
                    drawCircle(color = col, radius = 4.5f, center = end)
                }
            }

            // Stylus / Pen Ink Strokes (with pressure modulation)
            val allStrokes = if (activeStrokePoints.size > 1) strokes + Stroke(activeStrokePoints) else strokes
            for (stroke in allStrokes) {
                val pts = stroke.points
                for (i in 1 until pts.size) {
                    val p0 = toScreen(pts[i - 1].x, pts[i - 1].y)
                    val p1 = toScreen(pts[i].x, pts[i].y)
                    val pressureWidth = (2.4f + pts[i].pressure * 3.8f) * zoom.coerceIn(0.7f, 1.4f)
                    drawLine(
                        color = AuraInkStroke,
                        start = p0,
                        end = p1,
                        strokeWidth = pressureWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 2. Interactive Cluster Header Pills
        for (cluster in clusters) {
            val scr = toScreen(cluster.x + 14f, cluster.y - 9f)
            if (scr.x < -200f || scr.x > viewportW + 100f || scr.y < -60f || scr.y > viewportH + 60f) continue
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .offset { IntOffset(scr.x.roundToInt(), scr.y.roundToInt()) }
                    .clip(CircleShape)
                    .background(Color(0xFF080D15))
                    .border(
                        1.dp,
                        if (cluster.selected) AuraAmber.copy(alpha = 0.55f) else AuraLine,
                        CircleShape
                    )
                    .clickable { onSelect(cluster.nodeIds) }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (cluster.selected) AuraAmber else AuraMuted)
                )
                Text(
                    text = "CLUSTER ${cluster.index} · ${cluster.nodeIds.size} V · ${cluster.typeLabel.uppercase()}",
                    fontFamily = AuraMonoFontFamily,
                    fontSize = 8.5.sp,
                    color = if (cluster.selected) AuraAmber else AuraMuted
                )
            }
        }

        // 3. Floating Ghost Expression Preview Cards on the Canvas
        candidates.forEachIndexed { idx, candidate ->
            val scr = toScreen(candidate.anchor.x, candidate.anchor.y - 26f - idx * 14f)
            val col = Color(candidate.colorHex)
            Column(
                modifier = Modifier
                    .offset { IntOffset((scr.x - 95.dp.toPx()).roundToInt(), (scr.y - 68.dp.toPx()).roundToInt()) }
                    .widthIn(min = 150.dp, max = 260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xEE080D15))
                    .border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable {
                        onCommitCandidate(candidate)
                        candidates = emptyList()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(col)
                    )
                    Text(
                        text = candidate.kind.replace("-", " ").uppercase(),
                        fontFamily = AuraMonoFontFamily,
                        fontSize = 9.sp,
                        color = col
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(candidate.score * 100).roundToInt()}%",
                        fontFamily = AuraMonoFontFamily,
                        fontSize = 9.sp,
                        color = AuraMuted
                    )
                }
                Spacer(Modifier.height(3.dp))
                MathText(tex = candidate.previewLatex, fontSize = 15.sp, color = AuraText)
            }
        }

        // 4. Draggable Vertex Tiles & Port Dots
        for (node in displayGraph.nodes) {
            val wWorld = estimateWidth(node)
            val hWorld = tileHeight()
            val scrCenter = toScreen(node.x, node.y)
            val wPx = with(density) { (wWorld * zoom).coerceAtLeast(32f) }
            val hPx = with(density) { (hWorld * zoom).coerceAtLeast(32f) }
            val leftPx = scrCenter.x - wPx / 2f
            val topPx = scrCenter.y - hPx / 2f

            // Viewport culling (always keep selected nodes mounted)
            val isSelected = node.id in selected
            if (!isSelected && (leftPx > viewportW + 180f || leftPx + wPx < -180f || topPx > viewportH + 180f || topPx + hPx < -180f)) {
                continue
            }

            val issues = diagnosticsByNode[node.id] ?: emptyList()
            val hasError = issues.any { it.severity == DiagnosticSeverity.Error }
            val hasWarning = issues.any { it.severity == DiagnosticSeverity.Warning }
            val scopeHalo = scopes[node.id]

            // Open / occupied port dots around the vertex
            if ((isSelected || zoom > 0.72f) && zoom > 0.45f) {
                for (portDef in portsFor(node)) {
                    val occupied = index.children[node.id]?.containsKey(portDef.id) == true
                    if (!isSelected && (occupied || !portDef.required)) continue
                    val pAnchor = Engine.portAnchor(node, portDef)
                    val pScr = toScreen(pAnchor.x, pAnchor.y)
                    val dotSize = if (occupied) 6.dp else 9.dp
                    val dotPx = with(density) { dotSize.toPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (pScr.x - dotPx / 2f).roundToInt(),
                                    (pScr.y - dotPx / 2f).roundToInt()
                                )
                            }
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(if (occupied) AuraTeal.copy(alpha = 0.35f) else AuraAmber.copy(alpha = 0.25f))
                            .border(
                                1.5.dp,
                                if (occupied) AuraTeal.copy(alpha = 0.7f) else AuraAmber,
                                CircleShape
                            )
                    )
                }
            }

            val tileShape = if (node.kind == NodeKind.Relation) CircleShape else RoundedCornerShape((14f * zoom.coerceIn(0.7f, 1.2f)).dp)
            val borderColor = when {
                isSelected -> AuraAmber
                hasError -> AuraRose.copy(alpha = 0.85f)
                hasWarning -> AuraAmber.copy(alpha = 0.48f)
                node.kind == NodeKind.Relation -> AuraTeal.copy(alpha = 0.42f)
                node.kind == NodeKind.Set -> AuraBlue.copy(alpha = 0.45f)
                node.kind == NodeKind.Function -> AuraPink.copy(alpha = 0.4f)
                node.kind == NodeKind.Group -> AuraTeal.copy(alpha = 0.55f)
                else -> Color(0x4294A3B8)
            }
            val bgBrush = when (node.kind) {
                NodeKind.Relation -> Brush.verticalGradient(listOf(Color(0xF51E383C), Color(0xF80B1A1E)))
                NodeKind.Set -> Brush.verticalGradient(listOf(Color(0xF51C2842), Color(0xF80B1220)))
                NodeKind.Group -> Brush.verticalGradient(listOf(Color(0x385EEAD4), Color(0xF80D1921)))
                else -> Brush.verticalGradient(listOf(AuraTileTop, AuraTileBottom))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .size(
                        width = with(density) { wPx.toDp() },
                        height = with(density) { hPx.toDp() }
                    )
                    .then(
                        if (scopeHalo != null) {
                            Modifier.border(
                                1.5.dp,
                                Color(scopeHalo.first).copy(alpha = if (isSelected) 0.9f else 0.38f),
                                tileShape
                            )
                        } else Modifier
                    )
                    .clip(tileShape)
                    .background(bgBrush)
                    .border(if (isSelected) 2.dp else 1.dp, borderColor, tileShape)
                    .pointerInput(node.id, graph, selected, zoom) {
                        detectTapGestures(
                            onTap = {
                                onSelect(listOf(node.id))
                            },
                            onDoubleTap = {
                                if (node.collapsed) {
                                    onExpand(node.id)
                                } else {
                                    onSelect(Engine.componentIds(graph, node.id).toList())
                                }
                            },
                            onLongPress = {
                                onSelect(Engine.componentIds(graph, node.id).toList())
                            }
                        )
                    }
                    .pointerInput(node.id, graph, selected, zoom) {
                        var movableIds = emptySet<String>()
                        var origins = emptyMap<String, PointF>()
                        var totalDx = 0f
                        var totalDy = 0f
                        var lastCandidateTime = 0L

                        detectDragGestures(
                            onDragStart = {
                                val nextSel = if (node.id in selected) selected else listOf(node.id)
                                onSelect(nextSel)
                                val currentIdx = Engine.indexGraph(graph)
                                val mov = LinkedHashSet<String>()
                                for (rootId in nextSel) {
                                    mov.addAll(Engine.subtreeIds(currentIdx, rootId))
                                }
                                movableIds = mov
                                origins = graph.nodes.filter { it.id in mov }.associate { it.id to PointF(it.x, it.y) }
                                totalDx = 0f
                                totalDy = 0f
                                candidates = emptyList()
                                guides = null to null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDx += dragAmount.x / zoom
                                totalDy += dragAmount.y / zoom

                                val anchorOrigin = origins[node.id] ?: PointF(node.x, node.y)
                                var snapX: Float? = null
                                var snapY: Float? = null
                                val snapThreshold = 8f / zoom
                                var bestX = snapThreshold
                                var bestY = snapThreshold

                                for (other in graph.nodes) {
                                    if (other.id in movableIds) continue
                                    val dx = abs((anchorOrigin.x + totalDx) - other.x)
                                    if (dx < bestX) {
                                        bestX = dx
                                        snapX = other.x
                                    }
                                    val dy = abs((anchorOrigin.y + totalDy) - other.y)
                                    if (dy < bestY) {
                                        bestY = dy
                                        snapY = other.y
                                    }
                                }
                                val appliedDx = if (snapX != null) snapX - anchorOrigin.x else totalDx
                                val appliedDy = if (snapY != null) snapY - anchorOrigin.y else totalDy
                                guides = snapX to snapY

                                val nextPrev = LinkedHashMap<String, PointF>()
                                for ((id, orig) in origins) {
                                    nextPrev[id] = PointF(
                                        (orig.x + appliedDx).roundToInt().toFloat(),
                                        (orig.y + appliedDy).roundToInt().toFloat()
                                    )
                                }
                                previewPositions = nextPrev

                                val now = System.currentTimeMillis()
                                val draggedNow = nextPrev[node.id]
                                if (draggedNow != null && now - lastCandidateTime >= 45L) {
                                    lastCandidateTime = now
                                    val tempGraph = graph.copy(
                                        nodes = graph.nodes.map { n ->
                                            val pt = nextPrev[n.id]
                                            if (pt != null) n.copy(x = pt.x, y = pt.y) else n
                                        }
                                    )
                                    candidates = Engine.findCandidates(tempGraph, node.id, draggedNow, radius = 380f)
                                }
                            },
                            onDragEnd = {
                                guides = null to null
                                val prev = previewPositions
                                if (prev != null && prev.isNotEmpty() && (abs(totalDx) + abs(totalDy) > 2f)) {
                                    onMove(prev.entries.map { it.key to it.value })
                                }
                            },
                            onDragCancel = {
                                guides = null to null
                                previewPositions = null
                            }
                        )
                    }
            ) {
                // Left kind accent bar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp)
                        .width(3.dp)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(kindAccentColor(node.kind))
                )

                val fontSize = (18f * zoom.coerceIn(0.65f, 1.25f)).sp
                when {
                    node.kind == NodeKind.Group -> {
                        MathText(
                            tex = node.collapsedLatex ?: node.token,
                            fontSize = (13f * zoom.coerceIn(0.7f, 1.2f)).sp,
                            color = AuraText,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    node.kind == NodeKind.Operator && node.variant == "frac" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(AuraText.copy(alpha = 0.85f))
                            )
                            Text(
                                text = "FRAC",
                                fontFamily = AuraMonoFontFamily,
                                fontSize = (7.5f * zoom.coerceIn(0.75f, 1.2f)).sp,
                                color = AuraMuted
                            )
                        }
                    }
                    node.kind == NodeKind.Operator && node.variant == "juxta" -> {
                        Text("·", fontSize = fontSize, color = AuraDim, fontWeight = FontWeight.Bold)
                    }
                    node.kind == NodeKind.Operator && node.variant == "power" -> {
                        Text("^", fontFamily = AuraMonoFontFamily, fontSize = fontSize, color = AuraAmber)
                    }
                    else -> {
                        MathText(
                            tex = node.token.ifEmpty { node.label },
                            fontSize = fontSize,
                            color = AuraText
                        )
                    }
                }

                // Diagnostic count badge
                if (issues.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (hasError) AuraRose else AuraAmber)
                    ) {
                        Text(
                            text = issues.size.toString(),
                            fontFamily = AuraMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = AuraBg
                        )
                    }
                }
            }
        }

        // 5. Marquee Selection Overlay
        val mStart = marqueeStart
        val mCurr = marqueeCurrent
        if (mStart != null && mCurr != null) {
            val left = min(mStart.x, mCurr.x)
            val top = min(mStart.y, mCurr.y)
            val w = abs(mCurr.x - mStart.x)
            val h = abs(mCurr.y - mStart.y)
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(
                        width = with(density) { w.toDp() },
                        height = with(density) { h.toDp() }
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(AuraAmber.copy(alpha = 0.12f))
                    .border(1.dp, AuraAmber.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            )
        }

        // 6. HUD Top-Left: Graph Summary
        AuraPanel(
            cornerRadius = 16.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .widthIn(max = 260.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AuraAmber.copy(alpha = 0.12f))
                ) {
                    AuraIcon(AuraIconName.Graph, tint = AuraAmber, modifier = Modifier.size(16.dp))
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = documentTitle,
                        fontFamily = AuraSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = AuraText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("${graph.nodes.size} V", fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                        Text("/", fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                        Text("${graph.edges.size} E", fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                        Text("/", fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = AuraMuted)
                        val statusColor = when {
                            errorCount > 0 -> AuraRose
                            warningCount > 0 -> AuraAmber
                            else -> AuraTeal
                        }
                        val statusText = when {
                            errorCount > 0 -> "$errorCount conflict${if (errorCount == 1) "" else "s"}"
                            warningCount > 0 -> "$warningCount open"
                            else -> "coherent"
                        }
                        Text(statusText, fontFamily = AuraMonoFontFamily, fontSize = 10.sp, color = statusColor)
                    }
                }
            }
        }

        // 7. HUD Top-Right: Tool Palette (Pointer, Marquee, Pen/Stylus, Fit, Legend)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xE80A1019))
                .border(1.dp, AuraLine, RoundedCornerShape(15.dp))
                .padding(4.dp)
        ) {
            AuraBtn(
                onClick = { tool = CanvasTool.Pointer },
                variant = if (tool == CanvasTool.Pointer) AuraBtnVariant.Active else AuraBtnVariant.Default,
                height = 32.dp,
                horizontalPadding = 8.dp,
                testTag = "tool_pointer"
            ) {
                AuraIcon(AuraIconName.Pointer, tint = if (tool == CanvasTool.Pointer) AuraAmber else AuraDim)
            }
            AuraBtn(
                onClick = { tool = CanvasTool.Marquee },
                variant = if (tool == CanvasTool.Marquee) AuraBtnVariant.Active else AuraBtnVariant.Default,
                height = 32.dp,
                horizontalPadding = 8.dp,
                testTag = "tool_marquee"
            ) {
                AuraIcon(AuraIconName.Marquee, tint = if (tool == CanvasTool.Marquee) AuraAmber else AuraDim)
            }
            AuraBtn(
                onClick = {
                    tool = CanvasTool.Draw
                    candidates = emptyList()
                },
                variant = if (tool == CanvasTool.Draw) AuraBtnVariant.Active else AuraBtnVariant.Default,
                height = 32.dp,
                horizontalPadding = 8.dp,
                testTag = "tool_draw"
            ) {
                AuraIcon(AuraIconName.Pen, tint = if (tool == CanvasTool.Draw) AuraAmber else AuraDim)
            }
            Box(Modifier.width(1.dp).height(18.dp).background(AuraLine))
            AuraBtn(
                onClick = {
                    if (selectionBounds != null) fitToBounds(selectionBounds) else fitCanvas()
                },
                height = 32.dp,
                horizontalPadding = 8.dp,
                testTag = "tool_fit"
            ) {
                AuraIcon(AuraIconName.Fit, tint = AuraDim)
            }
            AuraBtn(
                onClick = { helpOpen = !helpOpen },
                variant = if (helpOpen) AuraBtnVariant.Active else AuraBtnVariant.Default,
                height = 32.dp,
                horizontalPadding = 8.dp,
                testTag = "tool_help"
            ) {
                AuraIcon(AuraIconName.Docs, tint = if (helpOpen) AuraAmber else AuraDim)
            }
        }

        // 8. Floating Selection Action Bar (Collapse, Fold, Simplify, Solve, Zoom, Delete)
        if (selectionBounds != null && selected.isNotEmpty()) {
            val scrTopLeft = toScreen(selectionBounds.minX, selectionBounds.minY)
            val barX = scrTopLeft.x.coerceIn(12f, max(12f, viewportW - 360f))
            val barY = (scrTopLeft.y - 54f).coerceIn(58f, max(58f, viewportH - 120f))
            val singleNode = if (selected.size == 1) index.byId[selected[0]] else null

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .offset { IntOffset(barX.roundToInt(), barY.roundToInt()) }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF5090E16))
                    .border(1.dp, AuraAmber.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${selected.size} V",
                    fontFamily = AuraMonoFontFamily,
                    fontSize = 10.sp,
                    color = AuraAmber,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Box(Modifier.width(1.dp).height(16.dp).background(AuraLine))
                if (singleNode?.collapsed == true) {
                    AuraBtn(
                        onClick = { onSelectionAction(SelectionAction.Expand, selected) },
                        enabled = !busy,
                        height = 28.dp,
                        horizontalPadding = 7.dp
                    ) {
                        AuraIcon(AuraIconName.Expand, modifier = Modifier.size(12.dp), tint = AuraTeal)
                        Spacer(Modifier.width(3.dp))
                        Text("expand", fontSize = 10.5.sp, color = AuraText)
                    }
                } else {
                    AuraBtn(
                        onClick = { onSelectionAction(SelectionAction.Collapse, selected) },
                        enabled = !busy,
                        height = 28.dp,
                        horizontalPadding = 7.dp
                    ) {
                        AuraIcon(AuraIconName.Collapse, modifier = Modifier.size(12.dp), tint = AuraDim)
                        Spacer(Modifier.width(3.dp))
                        Text("collapse", fontSize = 10.5.sp, color = AuraText)
                    }
                }
                AuraBtn(
                    onClick = { onSelectionAction(SelectionAction.Simplify, selected) },
                    enabled = !busy,
                    height = 28.dp,
                    horizontalPadding = 7.dp
                ) {
                    AuraIcon(AuraIconName.Spark, modifier = Modifier.size(12.dp), tint = AuraAmber)
                    Spacer(Modifier.width(3.dp))
                    Text("fold", fontSize = 10.5.sp, color = AuraText)
                }
                AuraBtn(
                    onClick = { onSelectionAction(SelectionAction.Solve, selected) },
                    enabled = !busy,
                    height = 28.dp,
                    horizontalPadding = 7.dp
                ) {
                    AuraIcon(AuraIconName.Solve, modifier = Modifier.size(12.dp), tint = AuraViolet)
                    Spacer(Modifier.width(3.dp))
                    Text("solve", fontSize = 10.5.sp, color = AuraText)
                }
                AuraBtn(
                    onClick = { fitToBounds(selectionBounds) },
                    height = 28.dp,
                    horizontalPadding = 6.dp
                ) {
                    AuraIcon(AuraIconName.Fit, modifier = Modifier.size(12.dp), tint = AuraDim)
                }
                AuraBtn(
                    onClick = { onSelectionAction(SelectionAction.Delete, selected) },
                    variant = AuraBtnVariant.Danger,
                    enabled = !busy,
                    height = 28.dp,
                    horizontalPadding = 6.dp
                ) {
                    AuraIcon(AuraIconName.Trash, modifier = Modifier.size(12.dp), tint = AuraRose)
                }
                AuraBtn(
                    onClick = { onSelect(emptyList()) },
                    height = 28.dp,
                    horizontalPadding = 6.dp
                ) {
                    AuraIcon(AuraIconName.Close, modifier = Modifier.size(12.dp), tint = AuraDim)
                }
            }
        }

        // 9. On-Canvas Stylus Ink Recognition Banner
        if (tool == CanvasTool.Draw && strokes.isNotEmpty()) {
            AuraPanel(
                cornerRadius = 18.dp,
                borderColor = AuraAmber.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 62.dp, start = 14.dp, end = 14.dp)
                    .widthIn(max = 460.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuraIcon(AuraIconName.Pen, tint = AuraAmber, modifier = Modifier.size(14.dp))
                            EyebrowText("Tier 1 · Stylus & On-Device Ink", color = AuraAmber)
                        }
                        Text(
                            text = "${strokes.size} stroke${if (strokes.size == 1) "" else "s"}",
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 10.sp,
                            color = AuraMuted
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        inkPredictions.forEach { pred ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF090E16))
                                    .border(1.dp, AuraLineStrong, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val allPts = strokes.flatMap { it.points }
                                        val cx = allPts.map { it.x }.average().toFloat()
                                        val cy = allPts.map { it.y }.average().toFloat()
                                        onPlaceRecognized(
                                            makeNode(
                                                kind = pred.kind,
                                                token = pred.latex,
                                                label = pred.label,
                                                type = pred.type,
                                                variant = pred.variant
                                            ),
                                            PointF(cx, cy)
                                        )
                                        strokes = emptyList()
                                        inkPredictions = emptyList()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AuraAmber.copy(alpha = 0.15f))
                                ) {
                                    MathText(tex = pred.latex, fontSize = 14.sp, color = AuraAmber)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pred.kind.wireName.uppercase()} · ${(pred.confidence * 100).roundToInt()}%",
                                        fontFamily = AuraMonoFontFamily,
                                        fontSize = 8.5.sp,
                                        color = AuraMuted,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = pred.type.label,
                                        fontSize = 10.5.sp,
                                        color = AuraText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuraBtn(
                            onClick = {
                                strokes = emptyList()
                                inkPredictions = emptyList()
                            },
                            height = 26.dp
                        ) {
                            Text("clear ink", fontSize = 10.5.sp, color = AuraDim)
                        }
                        AuraBtn(
                            onClick = { tool = CanvasTool.Pointer },
                            height = 26.dp
                        ) {
                            Text("done", fontSize = 10.5.sp, color = AuraDim)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "tap a prediction to place vertex",
                            fontFamily = AuraMonoFontFamily,
                            fontSize = 9.5.sp,
                            color = AuraMuted
                        )
                    }
                }
            }
        }

        // 10. Empty Canvas Guidance Card
        if (displayGraph.nodes.isEmpty()) {
            AuraPanel(
                cornerRadius = 22.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 380.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    EyebrowText("Empty canvas")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Start a hypergraph",
                        fontFamily = AuraDisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 21.sp,
                        color = AuraText
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Every symbol you place becomes a typed vertex with open ports. Bind vertices by dragging, or let the discovery engine propose the bindings for you.",
                        fontSize = 12.sp,
                        color = AuraDim
                    )
                    Spacer(Modifier.height(12.dp))
                    AuraBtn(
                        onClick = onOpenLibrary,
                        variant = AuraBtnVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AuraIcon(AuraIconName.Docs, tint = AuraAmberDarkText)
                        Spacer(Modifier.width(6.dp))
                        Text("Browse ${SymbolLibrary.LIBRARY_COUNT} symbols", fontWeight = FontWeight.SemiBold, color = AuraAmberDarkText)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuraBtn(onClick = onOpenInk, modifier = Modifier.weight(1f)) {
                            AuraIcon(AuraIconName.Pen, tint = AuraDim)
                            Spacer(Modifier.width(5.dp))
                            Text("Write ink", color = AuraText, fontSize = 12.sp)
                        }
                        AuraBtn(onClick = onOpenInk, modifier = Modifier.weight(1f)) {
                            AuraIcon(AuraIconName.Graph, tint = AuraDim)
                            Spacer(Modifier.width(5.dp))
                            Text("Parse LaTeX", color = AuraText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 11. Legend & Shortcuts Popover
        if (helpOpen) {
            AuraPanel(
                cornerRadius = 18.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp)
                    .width(280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EyebrowText("Canvas legend")
                        AuraBtn(
                            onClick = { helpOpen = false },
                            height = 24.dp,
                            horizontalPadding = 6.dp
                        ) {
                            AuraIcon(AuraIconName.Close, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val legendItems = listOf(
                        "Literal / Variable" to AuraDim,
                        "Operator" to AuraViolet,
                        "Relation" to AuraTeal,
                        "Container" to AuraAmber,
                        "Function" to AuraPink,
                        "Set" to AuraBlue
                    )
                    legendItems.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            row.forEach { (label, col) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(col))
                                    Text(label, fontSize = 10.5.sp, color = AuraDim)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AuraHairline()
                    Spacer(Modifier.height(8.dp))
                    val gestures = listOf(
                        "Drag vertex" to "Reveal ghost bindings",
                        "Tap ghost path" to "Commit binding",
                        "Double-tap" to "Select cluster / expand",
                        "Pinch / drag" to "Zoom & pan canvas",
                        "Pen tool" to "Stylus ink recognition"
                    )
                    gestures.forEach { (k, v) ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(v, fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraMuted)
                            Text(k, fontFamily = AuraMonoFontFamily, fontSize = 9.5.sp, color = AuraAmber)
                        }
                    }
                }
            }
        }

        // 12. Bottom-Left Minimap
        if (displayGraph.nodes.isNotEmpty() && candidates.isEmpty()) {
            val bounds = remember(displayGraph) { Engine.graphBounds(displayGraph) }
            val miniW = 132f
            val miniH = 84f
            val spanX = max(320f, bounds.maxX - bounds.minX)
            val spanY = max(220f, bounds.maxY - bounds.minY)
            val scale = min((miniW - 14f) / spanX, (miniH - 14f) / spanY)
            val offX = (miniW - spanX * scale) / 2f - bounds.minX * scale
            val offY = (miniH - spanY * scale) / 2f - bounds.minY * scale

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .size(width = miniW.dp, height = miniH.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6080D15))
                    .border(1.dp, AuraLine, RoundedCornerShape(14.dp))
                    .pointerInput(bounds, scale, offX, offY) {
                        detectTapGestures { tapOffset ->
                            val dpScale = density.density
                            val mx = tapOffset.x / dpScale
                            val my = tapOffset.y / dpScale
                            val wx = (mx - offX) / scale
                            val wy = (my - offY) / scale
                            panX = viewportW / 2f - wx * zoom
                            panY = viewportH / 2f - wy * zoom
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dpPx = density.density
                    for (node in displayGraph.nodes) {
                        val nx = (node.x * scale + offX) * dpPx
                        val ny = (node.y * scale + offY) * dpPx
                        val nw = max(3f, estimateWidth(node) * scale * dpPx)
                        val nh = max(3f, tileHeight() * scale * dpPx)
                        val isSel = node.id in selected
                        drawRoundRect(
                            color = if (isSel) AuraAmber else kindAccentColor(node.kind).copy(alpha = 0.6f),
                            topLeft = Offset(nx - nw / 2f, ny - nh / 2f),
                            size = Size(nw, nh),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                    // Viewport rect
                    val vLeft = ((-panX / zoom) * scale + offX) * dpPx
                    val vTop = ((-panY / zoom) * scale + offY) * dpPx
                    val vW = max(8f, (viewportW / zoom) * scale * dpPx)
                    val vH = max(8f, (viewportH / zoom) * scale * dpPx)
                    drawRoundRect(
                        color = AuraTeal.copy(alpha = 0.1f),
                        topLeft = Offset(vLeft, vTop),
                        size = Size(vW, vH),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                    drawRoundRect(
                        color = AuraTeal.copy(alpha = 0.75f),
                        topLeft = Offset(vLeft, vTop),
                        size = Size(vW, vH),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = DrawStroke(width = 1.2f)
                    )
                }
            }
        }

        // 13. Bottom-Right Zoom HUD
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE80A1019))
                .border(1.dp, AuraLine, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            AuraBtn(
                onClick = { zoom = (zoom - 0.15f).coerceIn(0.28f, 2.4f) },
                height = 28.dp,
                horizontalPadding = 8.dp
            ) {
                Text("−", fontSize = 14.sp, color = AuraText)
            }
            Text(
                text = "${(zoom * 100).roundToInt()}%",
                fontFamily = AuraMonoFontFamily,
                fontSize = 10.5.sp,
                color = AuraDim,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            AuraBtn(
                onClick = { zoom = (zoom + 0.15f).coerceIn(0.28f, 2.4f) },
                height = 28.dp,
                horizontalPadding = 8.dp
            ) {
                Text("+", fontSize = 14.sp, color = AuraText)
            }
        }

        // 14. Bottom Ghost Candidate Carousel
        if (candidates.isNotEmpty()) {
            AuraPanel(
                cornerRadius = 18.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 54.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuraIcon(AuraIconName.Bind, tint = AuraAmber, modifier = Modifier.size(14.dp))
                            EyebrowText("Homeomorphic candidates", color = AuraAmber)
                        }
                        AuraBtn(
                            onClick = { candidates = emptyList() },
                            height = 24.dp,
                            horizontalPadding = 8.dp
                        ) {
                            Text("cancel", fontSize = 10.sp, color = AuraDim)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        candidates.forEachIndexed { idx, candidate ->
                            val col = Color(candidate.colorHex)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .widthIn(min = 190.dp, max = 250.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xF0090E16))
                                    .border(1.dp, col.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .clickable(enabled = !busy) {
                                        onCommitCandidate(candidate)
                                        candidates = emptyList()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(col.copy(alpha = 0.16f))
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontFamily = AuraMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = col
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${candidate.kind.replace("-", " ").uppercase()} · ${(candidate.score * 100).roundToInt()}%",
                                        fontFamily = AuraMonoFontFamily,
                                        fontSize = 9.sp,
                                        color = col
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    MathText(tex = candidate.previewLatex, fontSize = 14.sp, color = AuraText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
