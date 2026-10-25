package com.example.aura.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aura.data.*
import com.example.aura.kernel.*
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class RightTab {
    Inspector, Solve, Plot, Connect, Verify
}

data class AuraToast(val message: String, val isError: Boolean = false)

data class AuraUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val discovering: Boolean = false,
    val documents: List<DocumentSummary> = emptyList(),
    val activePayload: DocumentPayload? = null,
    val selectedIds: List<String> = emptyList(),
    val viewportCenter: PointF = PointF(560f, 300f),
    val rightTab: RightTab = RightTab.Inspector,
    val sidebarOpen: Boolean = false,
    val rightDrawerOpen: Boolean = false,
    val inkDialogOpen: Boolean = false,
    val libraryDialogOpen: Boolean = false,
    val exportDialogOpen: Boolean = false,
    val scale: Float = 0.65f,
    val families: List<DiscoveryFamily> = DiscoveryFamily.entries,
    val focusOnly: Boolean = false,
    val discoveryStats: DiscoveryResult? = null,
    val proposals: List<Proposal> = emptyList(),
    val dismissedProposals: List<String> = emptyList(),
    val discoveryLog: List<DiscoveryEntity> = emptyList(),
    val appliedConnectionsCount: Int = 0,
    val toast: AuraToast? = null
)

class AuraViewModel(private val repository: AuraRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuraUiState())
    val state: StateFlow<AuraUiState> = _state.asStateFlow()

    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            val docs = repository.ensureSeed()
            val firstId = docs.firstOrNull()?.id
            val payload = if (firstId != null) repository.getDocument(firstId) else null
            val log = if (firstId != null) repository.listDiscoveries(firstId) else emptyList()
            _state.update {
                it.copy(
                    loading = false,
                    documents = docs,
                    activePayload = payload,
                    discoveryLog = log
                )
            }
        }
    }

    fun notify(message: String, isError: Boolean = false) {
        toastJob?.cancel()
        _state.update { it.copy(toast = AuraToast(message, isError)) }
        toastJob = viewModelScope.launch {
            delay(3200L)
            _state.update { it.copy(toast = null) }
        }
    }

    fun setSelected(ids: List<String>) {
        _state.update { it.copy(selectedIds = ids) }
    }

    fun setViewportCenter(center: PointF) {
        _state.update { it.copy(viewportCenter = center) }
    }

    fun setRightTab(tab: RightTab) {
        _state.update { it.copy(rightTab = tab) }
    }

    fun setSidebarOpen(open: Boolean) {
        _state.update { it.copy(sidebarOpen = open) }
    }

    fun setRightDrawerOpen(open: Boolean) {
        _state.update { it.copy(rightDrawerOpen = open) }
    }

    fun setInkDialogOpen(open: Boolean) {
        _state.update { it.copy(inkDialogOpen = open) }
    }

    fun setLibraryDialogOpen(open: Boolean) {
        _state.update { it.copy(libraryDialogOpen = open) }
    }

    fun setExportDialogOpen(open: Boolean) {
        _state.update { it.copy(exportDialogOpen = open) }
    }

    fun setScale(scale: Float) {
        _state.update { it.copy(scale = scale.coerceIn(0f, 1f)) }
    }

    fun toggleFamily(family: DiscoveryFamily) {
        _state.update { st ->
            val next = if (family in st.families) st.families - family else st.families + family
            st.copy(families = next)
        }
    }

    fun toggleFocusOnly() {
        _state.update { it.copy(focusOnly = !it.focusOnly) }
    }

    fun dismissProposal(id: String) {
        _state.update { it.copy(dismissedProposals = it.dismissedProposals + id) }
    }

    fun switchDocument(id: String) {
        val currentId = _state.value.activePayload?.id
        if (id == currentId) {
            _state.update { it.copy(sidebarOpen = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val payload = repository.getDocument(id)
            val log = repository.listDiscoveries(id)
            val docs = repository.listDocuments()
            _state.update {
                it.copy(
                    busy = false,
                    documents = docs,
                    activePayload = payload,
                    selectedIds = emptyList(),
                    sidebarOpen = false,
                    proposals = emptyList(),
                    discoveryStats = null,
                    discoveryLog = log
                )
            }
        }
    }

    fun createDocument() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val count = _state.value.documents.size + 1
            val payload = repository.createDocument("Canvas $count")
            val docs = repository.listDocuments()
            _state.update {
                it.copy(
                    busy = false,
                    documents = docs,
                    activePayload = payload,
                    selectedIds = emptyList(),
                    sidebarOpen = false,
                    proposals = emptyList(),
                    discoveryStats = null
                )
            }
            notify("New canvas created")
        }
    }

    private fun executeMutation(
        action: String,
        quiet: Boolean = false,
        selectFocus: Boolean = false,
        clearSelection: Boolean = false,
        block: (AuraGraph) -> Triple<AuraGraph, String, String?>
    ) {
        val docId = _state.value.activePayload?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val (payload, outcome) = repository.applyMutation(docId, action, block)
                val docs = repository.listDocuments()
                val (summary, focusId) = outcome
                _state.update { st ->
                    val nextSel = when {
                        clearSelection -> emptyList()
                        selectFocus && focusId != null -> listOf(focusId)
                        else -> st.selectedIds
                    }
                    st.copy(
                        busy = false,
                        activePayload = payload,
                        documents = docs,
                        selectedIds = nextSel
                    )
                }
                if (!quiet) notify(summary)
            } catch (e: Throwable) {
                // The kernel is pure Kotlin: a rejected mutation must never take the app down.
                _state.update { it.copy(busy = false) }
                notify(e.message ?: "Mutation rejected by hypergraph kernel", isError = true)
            }
        }
    }

    fun addElementById(itemId: String, label: String) {
        val pos = _state.value.viewportCenter
        val entry = SymbolLibrary.LIBRARY_BY_ID[itemId] ?: return
        executeMutation("addNode", selectFocus = true) { graph ->
            val node = makeNode(
                kind = entry.kind,
                token = entry.token,
                label = entry.label,
                type = entry.type,
                variant = entry.variant,
                x = pos.x,
                y = pos.y
            )
            Triple(
                AuraGraph(graph.nodes + node, graph.edges),
                "Placed ${node.label} as a typed vertex",
                node.id
            )
        }
        _state.update { it.copy(sidebarOpen = false) }
    }

    fun addRecognizedNode(node: AuraNode, position: PointF? = null) {
        val pos = position ?: _state.value.viewportCenter
        executeMutation("addNode", selectFocus = true) { graph ->
            val placed = node.copy(id = newId(), x = pos.x, y = pos.y)
            Triple(
                AuraGraph(graph.nodes + placed, graph.edges),
                "Placed ${placed.label}",
                placed.id
            )
        }
    }

    fun moveVertices(positions: List<Pair<String, PointF>>) {
        val map = positions.toMap()
        executeMutation("move", quiet = true) { graph ->
            val updated = graph.nodes.map { n ->
                val pt = map[n.id]
                if (pt != null) n.copy(x = pt.x, y = pt.y) else n
            }
            Triple(
                AuraGraph(updated, graph.edges),
                "Repositioned ${map.size} ${if (map.size == 1) "vertex" else "vertices"}",
                positions.firstOrNull()?.first
            )
        }
    }

    fun commitGhostCandidate(candidate: GhostPath) {
        commitPatch(candidate.toPatch(), quiet = false)
    }

    fun commitPatch(patch: ConnectionPatch, quiet: Boolean = false) {
        executeMutation("connect", quiet = quiet, selectFocus = true) { graph ->
            val merged = Engine.commitCandidate(graph, patch)
            val idx = Engine.indexGraph(merged)
            val root = Engine.componentRoot(idx, patch.focusId) ?: idx.byId[patch.focusId]
            Triple(
                merged,
                "Committed binding — ${patch.addedEdges.size} port${if (patch.addedEdges.size == 1) "" else "s"} bound",
                root?.id ?: patch.focusId
            )
        }
    }

    fun deleteSelected(ids: List<String> = _state.value.selectedIds) {
        val remove = ids.toSet()
        if (remove.isEmpty()) return
        executeMutation("delete", clearSelection = true) { graph ->
            val nodes = graph.nodes.filter { it.id !in remove }
            val edges = graph.edges.filter { it.source !in remove && it.target !in remove }
            Triple(
                AuraGraph(nodes, edges),
                "Deleted ${graph.nodes.size - nodes.size} ${if (graph.nodes.size - nodes.size == 1) "vertex" else "vertices"}",
                null
            )
        }
    }

    fun collapseSelected(ids: List<String> = _state.value.selectedIds) {
        if (ids.isEmpty()) return
        executeMutation("collapse", selectFocus = true) { graph ->
            val res = Engine.collapseSelection(graph, ids)
                ?: throw IllegalArgumentException("Select vertices to collapse")
            Triple(
                res.graph,
                "Collapsed ${ids.size} vertices into one expression vertex",
                res.groupId
            )
        }
    }

    fun expandGroup(groupId: String) {
        executeMutation("expand", selectFocus = true) { graph ->
            val expanded = Engine.expandGroup(graph, groupId)
                ?: throw IllegalArgumentException("That vertex is not a collapsed expression")
            Triple(expanded, "Expanded collapsed expression", groupId)
        }
    }

    fun reflowGraph() {
        executeMutation("reflow", quiet = false) { graph ->
            Triple(Engine.reflowGraph(graph), "Auto-laid out connected components", null)
        }
    }

    fun simplifyConstants() {
        executeMutation("simplify") { graph ->
            val res = Evaluate.foldConstants(graph)
            if (res.folded == 0) {
                // Also check if a symbolic algebraic rule applies!
                val symSteps = SymbolicEngine.simplifyStepByStep(graph, _state.value.selectedIds.firstOrNull())
                if (symSteps.isNotEmpty()) {
                    val last = symSteps.last()
                    return@executeMutation Triple(
                        last.graph,
                        "${last.ruleName}: ${MathFormatter.simplifySymbols(last.beforeLatex)} → ${MathFormatter.simplifySymbols(last.afterLatex)}",
                        last.focusRootId
                    )
                }
                throw IllegalArgumentException("No constant or symbolic subexpressions to fold")
            }
            val preview = res.summaries.take(2).joinToString(", ")
            Triple(
                res.graph,
                "Folded ${res.folded} constant subexpression${if (res.folded == 1) "" else "s"} ($preview)",
                null
            )
        }
    }

    fun applySymbolicStep(step: RewriteStep) {
        executeMutation("symbolicRewrite", selectFocus = true) {
            Triple(
                step.graph,
                "${step.ruleName}: ${MathFormatter.simplifySymbols(step.afterLatex)}",
                step.focusRootId
            )
        }
    }

    fun insertLatexExpression(latex: String) {
        val pos = _state.value.viewportCenter
        executeMutation("insertExpression", selectFocus = true) { graph ->
            val parsed = LatexParser.parseLatex(latex)
            val idx = Engine.indexGraph(parsed.graph)
            val comps = Engine.componentsOf(parsed.graph)
            val positions = LinkedHashMap<String, PointF>()
            comps.forEachIndexed { offset, comp ->
                val anchor = PointF(pos.x, pos.y + offset * 130f)
                positions.putAll(Engine.layoutComponent(parsed.graph, comp.rootId, anchor, idx))
            }
            val addedNodes = parsed.graph.nodes.map { n ->
                val pt = positions[n.id]
                if (pt != null) n.copy(x = pt.x, y = pt.y) else n
            }
            Triple(
                AuraGraph(graph.nodes + addedNodes, graph.edges + parsed.graph.edges),
                "Materialised ${parsed.nodeCount} vertices from structural recovery",
                parsed.rootId
            )
        }
    }

    fun runDiscovery() {
        val st = _state.value
        val payload = st.activePayload ?: return
        viewModelScope.launch {
            _state.update { it.copy(discovering = true) }
            try {
                val result = Discover.discover(
                    graph = payload.graph,
                    options = DiscoveryOptions(
                        scale = st.scale,
                        families = st.families,
                        focusIds = if (st.focusOnly && st.selectedIds.isNotEmpty()) st.selectedIds else null
                    )
                )
                val holdNames = result.proposals
                    .filter { it.verdict == Verdict.Holds }
                    .take(2)
                    .map { MathFormatter.simplifySymbols(it.previewLatex) }
                val summary = if (holdNames.isNotEmpty()) {
                    "holds: ${holdNames.joinToString(" ; ")}"
                } else {
                    "${result.proposals.size} candidates explored"
                }
                val log = repository.recordDiscovery(
                    documentId = payload.id,
                    scale = result.scale,
                    clusters = result.clusters,
                    considered = result.considered,
                    proposed = result.proposals.size,
                    holds = result.holds,
                    refuted = result.refuted,
                    summary = summary
                )
                _state.update {
                    it.copy(
                        discovering = false,
                        discoveryStats = result,
                        proposals = result.proposals,
                        dismissedProposals = emptyList(),
                        discoveryLog = log
                    )
                }
                notify(
                    if (result.holds > 0) {
                        "${result.holds} connection${if (result.holds == 1) "" else "s"} hold true at scale ${String.format(Locale.US, "%.2f", st.scale)}"
                    } else {
                        "${result.proposals.size} candidates explored — none verified at this scale"
                    }
                )
            } catch (e: Throwable) {
                _state.update { it.copy(discovering = false) }
                notify(e.message ?: "Discovery failed", isError = true)
            }
        }
    }

    fun applySingleProposal(proposal: Proposal) {
        commitPatch(proposal.patch, quiet = true)
        _state.update { st ->
            st.copy(
                proposals = st.proposals.filter { it.id != proposal.id },
                appliedConnectionsCount = st.appliedConnectionsCount + 1
            )
        }
        notify("Plugged in: ${MathFormatter.simplifySymbols(proposal.previewLatex)}")
    }

    fun applyProposalBatch(list: List<Proposal>) {
        if (list.isEmpty()) return
        val bestByPair = LinkedHashMap<String, Proposal>()
        for (p in list) {
            val key = p.clusterIds.sorted().joinToString("|")
            val existing = bestByPair[key]
            if (existing == null || p.score > existing.score) {
                bestByPair[key] = p
            }
        }
        val batch = bestByPair.values.toList()
        executeMutation("autoConnect") { graph ->
            val res = Discover.applyProposals(graph, batch.map { it.patch })
            if (res.applied == 0) throw IllegalArgumentException("Proposals conflicted with committed graph")
            Triple(
                res.graph,
                "Plugged in ${res.applied} discovered connection${if (res.applied == 1) "" else "s"}",
                batch.firstOrNull()?.patch?.focusId
            )
        }
        val appliedIds = batch.map { it.id }.toSet()
        _state.update { st ->
            st.copy(
                proposals = st.proposals.filter { it.id !in appliedIds },
                appliedConnectionsCount = st.appliedConnectionsCount + batch.size
            )
        }
    }

    fun undo() {
        val docId = _state.value.activePayload?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val payload = repository.undo(docId)
            val docs = repository.listDocuments()
            _state.update {
                it.copy(busy = false, activePayload = payload, documents = docs, selectedIds = emptyList())
            }
            notify("Undid the last graph transaction")
        }
    }

    fun redo() {
        val docId = _state.value.activePayload?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val payload = repository.redo(docId)
            val docs = repository.listDocuments()
            _state.update {
                it.copy(busy = false, activePayload = payload, documents = docs, selectedIds = emptyList())
            }
            notify("Redid the last graph transaction")
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AuraDatabase.getInstance(context)
                    return AuraViewModel(AuraRepository(db.auraDao())) as T
                }
            }
    }
}

@Composable
fun AuraWorkspaceScreen(viewModel: AuraViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val payload = state.activePayload
    val graph = payload?.graph ?: AuraGraph()
    val diagnostics = remember(graph) { Engine.diagnose(graph) }
    val expressions = remember(graph) { Engine.documentLatex(graph) }
    val hasErrors = remember(diagnostics) { diagnostics.any { it.severity == DiagnosticSeverity.Error } }

    fun copyToClipboard(text: String, message: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Aura", text))
        viewModel.notify(message)
    }

    BackHandler(enabled = state.sidebarOpen || state.rightDrawerOpen) {
        if (state.rightDrawerOpen) viewModel.setRightDrawerOpen(false)
        else if (state.sidebarOpen) viewModel.setSidebarOpen(false)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val screenWidth = maxWidth
        val isWideTablet = screenWidth >= 960.dp
        val isMediumTablet = screenWidth >= 700.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Telemetry Header Bar (56dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Color(0xEB070B12))
                    .padding(horizontal = 10.dp)
            ) {
                if (!isMediumTablet) {
                    AuraBtn(
                        onClick = { viewModel.setSidebarOpen(true) },
                        height = 34.dp,
                        horizontalPadding = 8.dp,
                        testTag = "open_sidebar_button"
                    ) {
                        AuraIcon(AuraIconName.Menu)
                    }
                }

                // Aura Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(AuraAmberBright, AuraAmberDeep)))
                ) {
                    Text(
                        text = "A",
                        fontFamily = AuraDisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = AuraAmberDarkText
                    )
                }

                // Canvas Title & Telemetry
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = payload?.title ?: "Loading…",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = AuraText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        AuraPill(
                            text = if (hasErrors) "ill-typed" else "coherent",
                            dotColor = if (hasErrors) AuraRose else AuraTeal,
                            textColor = if (hasErrors) AuraRose else AuraTeal,
                            borderColor = if (hasErrors) AuraRose.copy(alpha = 0.4f) else AuraTeal.copy(alpha = 0.35f)
                        )
                    }
                    Text(
                        text = "${graph.nodes.size} V · ${graph.edges.size} E · ${expressions.size} clusters · ${payload?.history?.size ?: 0} tx" +
                            if (state.selectedIds.isNotEmpty()) " · ${state.selectedIds.size} selected" else "",
                        fontFamily = AuraMonoFontFamily,
                        fontSize = 9.sp,
                        color = AuraMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Buttons
                AuraBtn(
                    onClick = { viewModel.undo() },
                    enabled = payload?.canUndo == true && !state.busy,
                    height = 34.dp,
                    horizontalPadding = 8.dp,
                    testTag = "undo_button"
                ) {
                    AuraIcon(AuraIconName.Undo)
                }
                AuraBtn(
                    onClick = { viewModel.redo() },
                    enabled = payload?.canRedo == true && !state.busy,
                    height = 34.dp,
                    horizontalPadding = 8.dp,
                    testTag = "redo_button"
                ) {
                    AuraIcon(AuraIconName.Redo)
                }
                AuraBtn(
                    onClick = {
                        viewModel.setRightTab(RightTab.Connect)
                        if (!isWideTablet) viewModel.setRightDrawerOpen(true)
                        viewModel.runDiscovery()
                    },
                    variant = AuraBtnVariant.Primary,
                    enabled = !state.busy && !state.discovering,
                    height = 34.dp,
                    horizontalPadding = 10.dp,
                    testTag = "auto_connect_button"
                ) {
                    AuraIcon(AuraIconName.Bind, tint = AuraAmberDarkText, modifier = Modifier.size(14.dp))
                    if (screenWidth >= 420.dp) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (state.discovering) "probing…" else "Auto-connect",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AuraAmberDarkText
                        )
                    }
                }
                AuraBtn(
                    onClick = { viewModel.setInkDialogOpen(true) },
                    enabled = !state.busy,
                    height = 34.dp,
                    horizontalPadding = 9.dp,
                    testTag = "write_ink_button"
                ) {
                    AuraIcon(AuraIconName.Pen, modifier = Modifier.size(14.dp))
                    if (screenWidth >= 520.dp) {
                        Spacer(Modifier.width(4.dp))
                        Text("Write", fontSize = 11.5.sp, color = AuraText)
                    }
                }
                AuraBtn(
                    onClick = { viewModel.setLibraryDialogOpen(true) },
                    height = 34.dp,
                    horizontalPadding = 9.dp,
                    testTag = "open_symbols_button"
                ) {
                    AuraIcon(AuraIconName.Docs, modifier = Modifier.size(14.dp))
                    if (screenWidth >= 600.dp) {
                        Spacer(Modifier.width(4.dp))
                        Text("Symbols", fontSize = 11.5.sp, color = AuraText)
                    }
                }
                AuraBtn(
                    onClick = { viewModel.setExportDialogOpen(true) },
                    height = 34.dp,
                    horizontalPadding = 8.dp,
                    testTag = "export_button"
                ) {
                    AuraIcon(AuraIconName.Export, modifier = Modifier.size(14.dp))
                }
                if (!isWideTablet) {
                    AuraBtn(
                        onClick = { viewModel.setRightDrawerOpen(true) },
                        height = 34.dp,
                        horizontalPadding = 8.dp,
                        testTag = "open_inspector_button"
                    ) {
                        AuraIcon(AuraIconName.Graph, tint = AuraAmber, modifier = Modifier.size(14.dp))
                    }
                }
            }
            AuraHairline()

            // Main Body: Docked Left Sidebar (on tablets) + Central CanvasStage + Docked Right Drawer (on wide tablets)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isMediumTablet) {
                    AuraSidebar(
                        graph = graph,
                        documents = state.documents,
                        activeId = payload?.id ?: "",
                        busy = state.busy,
                        diagnostics = diagnostics,
                        onSwitchDocument = { viewModel.switchDocument(it) },
                        onCreateDocument = { viewModel.createDocument() },
                        onAddElement = { id, label -> viewModel.addElementById(id, label) },
                        onOpenLibrary = { viewModel.setLibraryDialogOpen(true) },
                        modifier = Modifier.width(264.dp)
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AuraLine))
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (payload != null) {
                        CanvasStage(
                            graph = graph,
                            documentId = payload.id,
                            documentTitle = payload.title,
                            selected = state.selectedIds,
                            busy = state.busy,
                            onSelect = { viewModel.setSelected(it) },
                            onMove = { viewModel.moveVertices(it) },
                            onCommitCandidate = { viewModel.commitGhostCandidate(it) },
                            onExpand = { viewModel.expandGroup(it) },
                            onViewportCenter = { viewModel.setViewportCenter(it) },
                            onPlaceRecognized = { node, pt -> viewModel.addRecognizedNode(node, pt) },
                            onSelectionAction = { action, ids ->
                                when (action) {
                                    SelectionAction.Collapse -> viewModel.collapseSelected(ids)
                                    SelectionAction.Expand -> ids.firstOrNull()?.let { viewModel.expandGroup(it) }
                                    SelectionAction.Delete -> viewModel.deleteSelected(ids)
                                    SelectionAction.Simplify -> viewModel.simplifyConstants()
                                    SelectionAction.Solve -> {
                                        viewModel.setRightTab(RightTab.Solve)
                                        if (!isWideTablet) viewModel.setRightDrawerOpen(true)
                                    }
                                }
                            },
                            onOpenLibrary = { viewModel.setLibraryDialogOpen(true) },
                            onOpenInk = { viewModel.setInkDialogOpen(true) }
                        )
                    }
                }

                if (isWideTablet) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AuraLine))
                    RightPanelContainer(
                        state = state,
                        graph = graph,
                        diagnostics = diagnostics,
                        viewModel = viewModel,
                        onCopy = ::copyToClipboard,
                        onCloseDrawer = null,
                        modifier = Modifier.width(334.dp)
                    )
                }
            }
        }

        // Modal Left Sidebar Drawer (for phones / compact screens)
        if (!isMediumTablet && state.sidebarOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xA6000000))
                    .clickable { viewModel.setSidebarOpen(false) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(288.dp)
                        .clickable(enabled = false) {}
                ) {
                    AuraSidebar(
                        graph = graph,
                        documents = state.documents,
                        activeId = payload?.id ?: "",
                        busy = state.busy,
                        diagnostics = diagnostics,
                        onSwitchDocument = { viewModel.switchDocument(it) },
                        onCreateDocument = { viewModel.createDocument() },
                        onAddElement = { id, label -> viewModel.addElementById(id, label) },
                        onOpenLibrary = {
                            viewModel.setSidebarOpen(false)
                            viewModel.setLibraryDialogOpen(true)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AuraLine))
                }
            }
        }

        // Modal Right Inspector/Plot/Solve/Connect/Verify Drawer (for phones & medium screens)
        if (!isWideTablet && state.rightDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xA6000000))
                    .clickable { viewModel.setRightDrawerOpen(false) }
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(max = 350.dp)
                        .fillMaxWidth(0.92f)
                        .clickable(enabled = false) {}
                ) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AuraLine))
                    RightPanelContainer(
                        state = state,
                        graph = graph,
                        diagnostics = diagnostics,
                        viewModel = viewModel,
                        onCopy = ::copyToClipboard,
                        onCloseDrawer = { viewModel.setRightDrawerOpen(false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Dialogs
        InkDialog(
            open = state.inkDialogOpen,
            onClose = { viewModel.setInkDialogOpen(false) },
            busy = state.busy,
            onInsertNode = { viewModel.addRecognizedNode(it) },
            onInsertLatex = { viewModel.insertLatexExpression(it) },
            onNotify = { msg, err -> viewModel.notify(msg, err) }
        )

        SymbolLibraryDialog(
            open = state.libraryDialogOpen,
            busy = state.busy,
            onClose = { viewModel.setLibraryDialogOpen(false) },
            onPick = { id, label -> viewModel.addElementById(id, label) }
        )

        if (payload != null) {
            ExportDialog(
                open = state.exportDialogOpen,
                payload = payload,
                expressions = expressions,
                onClose = { viewModel.setExportDialogOpen(false) },
                onCopy = ::copyToClipboard
            )
        }

        // Toast Banner
        state.toast?.let { toast ->
            AuraPanel(
                cornerRadius = 16.dp,
                borderColor = if (toast.isError) AuraRose.copy(alpha = 0.5f) else AuraTeal.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    AuraIcon(
                        name = if (toast.isError) AuraIconName.Error else AuraIconName.Check,
                        tint = if (toast.isError) AuraRose else AuraTeal,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = toast.message,
                        fontSize = 12.sp,
                        color = if (toast.isError) AuraRose else AuraTeal
                    )
                }
            }
        }
    }
}

@Composable
private fun RightPanelContainer(
    state: AuraUiState,
    graph: AuraGraph,
    diagnostics: List<Diagnostic>,
    viewModel: AuraViewModel,
    onCopy: (String, String) -> Unit,
    onCloseDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AuraSidebarBg)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            val tabs = listOf(
                Triple(RightTab.Inspector, "inspect", AuraIconName.Graph),
                Triple(RightTab.Solve, "solve", AuraIconName.Solve),
                Triple(RightTab.Plot, "plot", AuraIconName.Spark),
                Triple(RightTab.Connect, "connect", AuraIconName.Bind),
                Triple(RightTab.Verify, "verify", AuraIconName.Check)
            )
            tabs.forEach { (tab, label, icon) ->
                val active = state.rightTab == tab
                AuraBtn(
                    onClick = { viewModel.setRightTab(tab) },
                    variant = if (active) AuraBtnVariant.Active else AuraBtnVariant.Default,
                    height = 28.dp,
                    horizontalPadding = 6.dp,
                    testTag = "tab_${label}",
                    modifier = if (tab == RightTab.Verify) Modifier else Modifier.weight(1f)
                ) {
                    AuraIcon(icon, modifier = Modifier.size(11.dp), tint = if (active) AuraAmber else AuraDim)
                    if (tab != RightTab.Verify) {
                        Spacer(Modifier.width(3.dp))
                        Text(label, fontSize = 10.sp, color = if (active) AuraAmber else AuraText, maxLines = 1)
                    }
                }
            }
            if (onCloseDrawer != null) {
                AuraBtn(
                    onClick = onCloseDrawer,
                    height = 28.dp,
                    horizontalPadding = 6.dp
                ) {
                    AuraIcon(AuraIconName.Close, modifier = Modifier.size(12.dp))
                }
            }
        }
        AuraHairline()

        Box(modifier = Modifier.weight(1f)) {
            when (state.rightTab) {
                RightTab.Inspector -> InspectorPanel(
                    graph = graph,
                    selected = state.selectedIds,
                    diagnostics = diagnostics,
                    history = state.activePayload?.history ?: emptyList(),
                    busy = state.busy,
                    onDelete = { viewModel.deleteSelected() },
                    onCollapse = { viewModel.collapseSelected() },
                    onExpand = { viewModel.expandGroup(it) },
                    onReflow = { viewModel.reflowGraph() },
                    onSimplify = { viewModel.simplifyConstants() },
                    onOpenSymbolicSolve = { viewModel.setRightTab(RightTab.Solve) },
                    onCopy = onCopy,
                    onSelect = { viewModel.setSelected(it) }
                )
                RightTab.Solve -> SymbolicSolvePanel(
                    graph = graph,
                    selected = state.selectedIds,
                    busy = state.busy,
                    onApplyStep = { viewModel.applySymbolicStep(it) },
                    onFoldConstants = { viewModel.simplifyConstants() },
                    onSelect = { viewModel.setSelected(it) }
                )
                RightTab.Plot -> PlotPanel(
                    graph = graph,
                    onSelect = { viewModel.setSelected(it) }
                )
                RightTab.Connect -> DiscoveryPanel(
                    busy = state.busy,
                    discovering = state.discovering,
                    scale = state.scale,
                    families = state.families,
                    stats = state.discoveryStats,
                    proposals = state.proposals,
                    dismissed = state.dismissedProposals,
                    appliedCount = state.appliedConnectionsCount,
                    log = state.discoveryLog,
                    focusOnly = state.focusOnly,
                    selectionSize = state.selectedIds.size,
                    onToggleFocus = { viewModel.toggleFocusOnly() },
                    onScaleChange = { viewModel.setScale(it) },
                    onToggleFamily = { viewModel.toggleFamily(it) },
                    onRun = { viewModel.runDiscovery() },
                    onApply = { viewModel.applySingleProposal(it) },
                    onApplyAll = { viewModel.applyProposalBatch(it) },
                    onDismiss = { viewModel.dismissProposal(it) },
                    onLocate = { prop ->
                        viewModel.setSelected(prop.clusterIds)
                        onCloseDrawer?.invoke()
                    }
                )
                RightTab.Verify -> VerificationPanel()
            }
        }
    }
}
