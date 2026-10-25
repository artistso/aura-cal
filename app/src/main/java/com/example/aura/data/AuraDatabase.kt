package com.example.aura.data

import android.content.Context
import androidx.room.*
import com.example.aura.kernel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room SQLite persistence layer for Aura.
 * Stores documents, typed hypergraph nodes, port-bound edges, discovery logs,
 * and full before/after JSON graph snapshots for exact undo/redo transactions.
 */

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "nodes",
    indices = [Index("documentId")]
)
data class NodeEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val kind: String,
    val type: String,
    val token: String,
    val label: String,
    val variant: String?,
    val x: Float,
    val y: Float,
    val collapsed: Boolean,
    val collapsedLatex: String?,
    val collapsedPayloadJson: String?
)

@Entity(
    tableName = "edges",
    indices = [Index("documentId")]
)
data class EdgeEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val sourceId: String,
    val targetId: String,
    val port: String,
    val role: String
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["documentId", "seq"])]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val seq: Int,
    val action: String,
    val summary: String,
    val beforeJson: String,
    val afterJson: String,
    val undone: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "discoveries",
    indices = [Index("documentId")]
)
data class DiscoveryEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val scale: Float,
    val clusters: Int,
    val considered: Int,
    val proposed: Int,
    val holds: Int,
    val refuted: Int,
    val summary: String,
    val createdAt: Long
)

data class DocumentSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val updatedAt: Long,
    val nodeCount: Int,
    val transactionCount: Int
)

data class HistoryEntry(
    val id: String,
    val seq: Int,
    val action: String,
    val summary: String,
    val undone: Boolean,
    val createdAt: Long
)

data class DocumentPayload(
    val id: String,
    val title: String,
    val subtitle: String,
    val updatedAt: Long,
    val graph: AuraGraph,
    val history: List<HistoryEntry>,
    val canUndo: Boolean,
    val canRedo: Boolean
)

object GraphJsonCodec {
    fun encodeGraph(graph: AuraGraph): String {
        val root = JSONObject()
        val nodesArr = JSONArray()
        for (n in graph.nodes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("kind", n.kind.wireName)
            obj.put("type", n.type.name)
            obj.put("token", n.token)
            obj.put("label", n.label)
            if (n.variant != null) obj.put("variant", n.variant)
            obj.put("x", n.x.toDouble())
            obj.put("y", n.y.toDouble())
            obj.put("collapsed", n.collapsed)
            if (n.collapsedLatex != null) obj.put("collapsedLatex", n.collapsedLatex)
            if (n.collapsedPayload != null) {
                obj.put("collapsedPayload", encodeGraph(AuraGraph(n.collapsedPayload.nodes, n.collapsedPayload.edges)))
            }
            nodesArr.put(obj)
        }
        val edgesArr = JSONArray()
        for (e in graph.edges) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("source", e.source)
            obj.put("target", e.target)
            obj.put("port", e.port.wireName)
            obj.put("role", e.role)
            edgesArr.put(obj)
        }
        root.put("nodes", nodesArr)
        root.put("edges", edgesArr)
        return root.toString()
    }

    fun decodeGraph(json: String): AuraGraph {
        if (json.isBlank()) return AuraGraph()
        val root = JSONObject(json)
        val nodesArr = root.optJSONArray("nodes") ?: JSONArray()
        val nodes = ArrayList<AuraNode>(nodesArr.length())
        for (i in 0 until nodesArr.length()) {
            val obj = nodesArr.getJSONObject(i)
            val payloadStr = if (obj.has("collapsedPayload")) obj.optString("collapsedPayload") else null
            val payload = if (!payloadStr.isNullOrBlank()) {
                val sub = decodeGraph(payloadStr)
                CollapsedPayload(sub.nodes, sub.edges)
            } else null
            nodes.add(
                AuraNode(
                    id = obj.getString("id"),
                    kind = NodeKind.fromString(obj.optString("kind")),
                    type = MathType.fromString(obj.optString("type")),
                    token = obj.optString("token"),
                    label = obj.optString("label"),
                    variant = if (obj.has("variant") && !obj.isNull("variant")) obj.getString("variant") else null,
                    x = obj.optDouble("x", 0.0).toFloat(),
                    y = obj.optDouble("y", 0.0).toFloat(),
                    collapsed = obj.optBoolean("collapsed", false),
                    collapsedLatex = if (obj.has("collapsedLatex") && !obj.isNull("collapsedLatex")) obj.getString("collapsedLatex") else null,
                    collapsedPayload = payload
                )
            )
        }
        val edgesArr = root.optJSONArray("edges") ?: JSONArray()
        val edges = ArrayList<AuraEdge>(edgesArr.length())
        for (i in 0 until edgesArr.length()) {
            val obj = edgesArr.getJSONObject(i)
            val port = PortId.fromString(obj.optString("port")) ?: PortId.Left
            edges.add(
                AuraEdge(
                    id = obj.getString("id"),
                    source = obj.getString("source"),
                    target = obj.getString("target"),
                    port = port,
                    role = obj.optString("role", port.wireName)
                )
            )
        }
        return AuraGraph(nodes, edges)
    }
}

@Dao
interface AuraDao {
    @Query("SELECT * FROM documents ORDER BY createdAt ASC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY createdAt ASC")
    suspend fun getDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity)

    @Query("UPDATE documents SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchDocument(id: String, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("SELECT * FROM nodes WHERE documentId = :documentId")
    suspend fun getNodes(documentId: String): List<NodeEntity>

    @Query("SELECT COUNT(*) FROM nodes WHERE documentId = :documentId")
    suspend fun getNodeCount(documentId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    @Query("DELETE FROM nodes WHERE documentId = :documentId")
    suspend fun deleteNodesForDocument(documentId: String)

    @Query("SELECT * FROM edges WHERE documentId = :documentId")
    suspend fun getEdges(documentId: String): List<EdgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdges(edges: List<EdgeEntity>)

    @Query("DELETE FROM edges WHERE documentId = :documentId")
    suspend fun deleteEdgesForDocument(documentId: String)

    @Query("SELECT * FROM transactions WHERE documentId = :documentId ORDER BY seq DESC LIMIT 60")
    suspend fun getTransactions(documentId: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE documentId = :documentId")
    suspend fun getTransactionCount(documentId: String): Int

    @Query("SELECT COALESCE(MAX(seq), 0) FROM transactions WHERE documentId = :documentId")
    suspend fun getMaxSeq(documentId: String): Int

    @Query("SELECT * FROM transactions WHERE documentId = :documentId AND undone = 0 ORDER BY seq DESC LIMIT 1")
    suspend fun getLatestUndoable(documentId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE documentId = :documentId AND undone = 1 ORDER BY seq ASC LIMIT 1")
    suspend fun getEarliestRedoable(documentId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: TransactionEntity)

    @Query("UPDATE transactions SET undone = :undone WHERE id = :id")
    suspend fun setTransactionUndone(id: String, undone: Boolean)

    @Query("DELETE FROM transactions WHERE documentId = :documentId")
    suspend fun deleteTransactionsForDocument(documentId: String)

    @Query("SELECT * FROM discoveries WHERE documentId = :documentId ORDER BY createdAt DESC LIMIT 15")
    suspend fun getDiscoveries(documentId: String): List<DiscoveryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscovery(discovery: DiscoveryEntity)
}

@Database(
    entities = [
        DocumentEntity::class,
        NodeEntity::class,
        EdgeEntity::class,
        TransactionEntity::class,
        DiscoveryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun auraDao(): AuraDao

    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_hypergraph.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class AuraRepository(private val dao: AuraDao) {

    suspend fun ensureSeed(): List<DocumentSummary> = withContext(Dispatchers.IO) {
        val existing = dao.getDocuments()
        if (existing.isEmpty()) {
            var now = System.currentTimeMillis()
            for (seed in Seeds.SEED_DOCUMENTS) {
                val docId = newId()
                dao.insertDocument(
                    DocumentEntity(
                        id = docId,
                        title = seed.title,
                        subtitle = seed.subtitle,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                saveGraphInternal(docId, seed.graph)
                now += 10L
            }
        }
        listDocuments()
    }

    suspend fun listDocuments(): List<DocumentSummary> = withContext(Dispatchers.IO) {
        dao.getDocuments().map { doc ->
            DocumentSummary(
                id = doc.id,
                title = doc.title,
                subtitle = doc.subtitle,
                updatedAt = doc.updatedAt,
                nodeCount = dao.getNodeCount(doc.id),
                transactionCount = dao.getTransactionCount(doc.id)
            )
        }
    }

    suspend fun getDocument(documentId: String): DocumentPayload? = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(documentId) ?: return@withContext null
        val graph = loadGraphInternal(documentId)
        val txs = dao.getTransactions(documentId).map {
            HistoryEntry(
                id = it.id,
                seq = it.seq,
                action = it.action,
                summary = it.summary,
                undone = it.undone,
                createdAt = it.createdAt
            )
        }
        DocumentPayload(
            id = doc.id,
            title = doc.title,
            subtitle = doc.subtitle,
            updatedAt = doc.updatedAt,
            graph = graph,
            history = txs,
            canUndo = txs.any { !it.undone },
            canRedo = txs.any { it.undone }
        )
    }

    suspend fun createDocument(title: String): DocumentPayload = withContext(Dispatchers.IO) {
        val seed = Seeds.emptyDocument(title.ifBlank { "Untitled canvas" })
        val id = newId()
        val now = System.currentTimeMillis()
        dao.insertDocument(
            DocumentEntity(
                id = id,
                title = seed.title,
                subtitle = seed.subtitle,
                createdAt = now,
                updatedAt = now
            )
        )
        saveGraphInternal(id, seed.graph)
        getDocument(id)!!
    }

    suspend fun applyMutation(
        documentId: String,
        action: String,
        block: (AuraGraph) -> Triple<AuraGraph, String, String?>
    ): Pair<DocumentPayload, Pair<String, String?>> = withContext(Dispatchers.IO) {
        val before = loadGraphInternal(documentId)
        val (nextGraph, summary, focusId) = block(before)
        val problem = Engine.validateGraph(nextGraph)
        if (problem != null) throw IllegalArgumentException(problem)
        val seq = dao.getMaxSeq(documentId) + 1
        saveGraphInternal(documentId, nextGraph)
        val now = System.currentTimeMillis()
        dao.insertTransaction(
            TransactionEntity(
                id = newId(),
                documentId = documentId,
                seq = seq,
                action = action,
                summary = summary,
                beforeJson = GraphJsonCodec.encodeGraph(before),
                afterJson = GraphJsonCodec.encodeGraph(nextGraph),
                undone = false,
                createdAt = now
            )
        )
        dao.touchDocument(documentId, now)
        val payload = getDocument(documentId) ?: throw IllegalStateException("Document not found")
        payload to (summary to focusId)
    }

    suspend fun undo(documentId: String): DocumentPayload? = withContext(Dispatchers.IO) {
        val entry = dao.getLatestUndoable(documentId) ?: return@withContext getDocument(documentId)
        val beforeGraph = GraphJsonCodec.decodeGraph(entry.beforeJson)
        saveGraphInternal(documentId, beforeGraph)
        dao.setTransactionUndone(entry.id, true)
        dao.touchDocument(documentId, System.currentTimeMillis())
        getDocument(documentId)
    }

    suspend fun redo(documentId: String): DocumentPayload? = withContext(Dispatchers.IO) {
        val entry = dao.getEarliestRedoable(documentId) ?: return@withContext getDocument(documentId)
        val afterGraph = GraphJsonCodec.decodeGraph(entry.afterJson)
        saveGraphInternal(documentId, afterGraph)
        dao.setTransactionUndone(entry.id, false)
        dao.touchDocument(documentId, System.currentTimeMillis())
        getDocument(documentId)
    }

    suspend fun recordDiscovery(
        documentId: String,
        scale: Float,
        clusters: Int,
        considered: Int,
        proposed: Int,
        holds: Int,
        refuted: Int,
        summary: String
    ): List<DiscoveryEntity> = withContext(Dispatchers.IO) {
        dao.insertDiscovery(
            DiscoveryEntity(
                id = newId(),
                documentId = documentId,
                scale = scale,
                clusters = clusters,
                considered = considered,
                proposed = proposed,
                holds = holds,
                refuted = refuted,
                summary = summary.take(240),
                createdAt = System.currentTimeMillis()
            )
        )
        dao.getDiscoveries(documentId)
    }

    suspend fun listDiscoveries(documentId: String): List<DiscoveryEntity> = withContext(Dispatchers.IO) {
        dao.getDiscoveries(documentId)
    }

    private suspend fun loadGraphInternal(documentId: String): AuraGraph {
        val nodeRows = dao.getNodes(documentId)
        val edgeRows = dao.getEdges(documentId)
        val nodes = nodeRows.map { r ->
            val payload = if (!r.collapsedPayloadJson.isNullOrBlank()) {
                val sub = GraphJsonCodec.decodeGraph(r.collapsedPayloadJson)
                CollapsedPayload(sub.nodes, sub.edges)
            } else null
            AuraNode(
                id = r.id,
                kind = NodeKind.fromString(r.kind),
                type = MathType.fromString(r.type),
                token = r.token,
                label = r.label,
                variant = r.variant,
                x = r.x,
                y = r.y,
                collapsed = r.collapsed,
                collapsedLatex = r.collapsedLatex,
                collapsedPayload = payload
            )
        }
        val edges = edgeRows.map { r ->
            val port = PortId.fromString(r.port) ?: PortId.Left
            AuraEdge(
                id = r.id,
                source = r.sourceId,
                target = r.targetId,
                port = port,
                role = r.role
            )
        }
        return AuraGraph(nodes, edges)
    }

    private suspend fun saveGraphInternal(documentId: String, graph: AuraGraph) {
        dao.deleteEdgesForDocument(documentId)
        dao.deleteNodesForDocument(documentId)
        if (graph.nodes.isNotEmpty()) {
            dao.insertNodes(
                graph.nodes.map { n ->
                    NodeEntity(
                        id = n.id,
                        documentId = documentId,
                        kind = n.kind.wireName,
                        type = n.type.name,
                        token = n.token,
                        label = n.label,
                        variant = n.variant,
                        x = n.x,
                        y = n.y,
                        collapsed = n.collapsed,
                        collapsedLatex = n.collapsedLatex,
                        collapsedPayloadJson = n.collapsedPayload?.let {
                            GraphJsonCodec.encodeGraph(AuraGraph(it.nodes, it.edges))
                        }
                    )
                }
            )
        }
        if (graph.edges.isNotEmpty()) {
            dao.insertEdges(
                graph.edges.map { e ->
                    EdgeEntity(
                        id = e.id,
                        documentId = documentId,
                        sourceId = e.source,
                        targetId = e.target,
                        port = e.port.wireName,
                        role = e.role
                    )
                }
            )
        }
    }
}
