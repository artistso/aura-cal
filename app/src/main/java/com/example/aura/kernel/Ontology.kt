package com.example.aura.kernel

import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Aura's mathematical ontology: the type lattice, port vocabulary and the
 * element catalogue used by the palette, the connection engine and the
 * recognition pipeline.
 */

enum class MathType(val label: String) {
    Scalar("Scalar"),
    Variable("Variable"),
    Vector("Vector"),
    Matrix("Matrix"),
    Set("Set"),
    Expression("Expression"),
    Relation("Relation"),
    Function("Function"),
    Operator("Operator"),
    Unknown("Unknown");

    companion object {
        fun fromString(value: String?): MathType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: Expression
    }
}

enum class NodeKind(val wireName: String) {
    Literal("literal"),
    Variable("variable"),
    Operator("operator"),
    Relation("relation"),
    Function("function"),
    Container("container"),
    Set("set"),
    Group("group");

    companion object {
        fun fromString(value: String?): NodeKind =
            entries.find { it.wireName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: Variable
    }
}

enum class PortId(val wireName: String) {
    Left("left"),
    Right("right"),
    Base("base"),
    Exponent("exponent"),
    Subscript("subscript"),
    Numerator("numerator"),
    Denominator("denominator"),
    Lower("lower"),
    Upper("upper"),
    Body("body"),
    Arg("arg");

    companion object {
        fun fromString(value: String?): PortId? =
            entries.find { it.wireName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
    }
}

enum class PortSide {
    Left, Right, Top, Bottom, TopRight, BottomRight, RightTop, RightBottom
}

data class CollapsedPayload(
    val nodes: List<AuraNode>,
    val edges: List<AuraEdge>
)

data class AuraNode(
    val id: String = newId(),
    val kind: NodeKind,
    val type: MathType = MathType.Expression,
    val token: String,
    val label: String,
    val variant: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val collapsed: Boolean = false,
    val collapsedLatex: String? = null,
    val collapsedPayload: CollapsedPayload? = null
)

data class AuraEdge(
    val id: String = newId(),
    val source: String,
    val target: String,
    val port: PortId,
    val role: String = port.wireName
)

data class AuraGraph(
    val nodes: List<AuraNode> = emptyList(),
    val edges: List<AuraEdge> = emptyList()
)

data class PortDef(
    val id: PortId,
    val label: String,
    val accepts: List<MathType>,
    val required: Boolean,
    val hint: String,
    val side: PortSide
)

enum class DiagnosticSeverity {
    Error, Warning, Info
}

data class Diagnostic(
    val nodeId: String,
    val edgeId: String? = null,
    val severity: DiagnosticSeverity,
    val message: String
)

data class PointF(val x: Float, val y: Float)

data class ConnectionPatch(
    val kind: String,
    val addedNodes: List<AuraNode>,
    val addedEdges: List<AuraEdge>,
    val removedEdgeIds: List<String>,
    val draggedId: String,
    val focusId: String
)

data class GhostPath(
    val colorHex: Long,
    val title: String,
    val rationale: String,
    val score: Float,
    val kind: String,
    val draggedId: String,
    val targetId: String,
    val targetPort: PortId,
    val anchor: PointF,
    val previewLatex: String,
    val addedNodes: List<AuraNode>,
    val addedEdges: List<AuraEdge>,
    val removedEdgeIds: List<String>,
    val focusId: String
) {
    fun toPatch(): ConnectionPatch = ConnectionPatch(
        kind = kind,
        addedNodes = addedNodes,
        addedEdges = addedEdges,
        removedEdgeIds = removedEdgeIds,
        draggedId = draggedId,
        focusId = focusId
    )
}

object Ontology {
    val OPERAND_TYPES = listOf(
        MathType.Scalar,
        MathType.Variable,
        MathType.Vector,
        MathType.Matrix,
        MathType.Expression
    )

    val EXPRESSIONISH = listOf(
        MathType.Scalar,
        MathType.Variable,
        MathType.Vector,
        MathType.Matrix,
        MathType.Expression,
        MathType.Function,
        MathType.Set
    )

    val GHOST_COLORS = listOf(
        0xFFF0B45AL,
        0xFF5EEAD4L,
        0xFFF472B6L,
        0xFFA78BFAL
    )

    val POSTFIX_VARIANTS = setOf(
        "fact", "transpose", "inverse", "conj", "prime", "doubleprime", "degree", "complement"
    )

    val PREFIX_VARIANTS = setOf("neg", "forall", "exists", "nabla", "partial")

    val ACCENT_VARIANTS = setOf(
        "hat", "bar", "vec", "dot", "ddot", "tilde", "overline", "underline", "check", "widehat", "widetilde"
    )

    val BIG_VARIANTS = setOf(
        "sum", "prod", "coprod", "int", "iint", "iiint", "oint",
        "lim", "limsup", "liminf", "sup", "inf", "max", "min",
        "bigcup", "bigcap", "bigoplus", "bigotimes", "det", "gcd",
        "argmin", "argmax", "span"
    )

    val MATRIX_VARIANTS = setOf("matrix", "bmatrix", "vmatrix", "cases")
    val ENCLOSURE_VARIANTS = setOf("abs", "norm", "floor", "ceil")
    val SHAREABLE_KINDS = setOf(NodeKind.Literal, NodeKind.Variable, NodeKind.Set)

    const val TILE_HEIGHT = 52f
    const val TILE_MIN = 46f
}

fun newId(): String = UUID.randomUUID().toString()

fun makeNode(
    kind: NodeKind,
    token: String,
    label: String,
    type: MathType = MathType.Expression,
    variant: String? = null,
    x: Float = 0f,
    y: Float = 0f,
    collapsed: Boolean = false,
    id: String = newId(),
    collapsedLatex: String? = null,
    collapsedPayload: CollapsedPayload? = null
): AuraNode = AuraNode(
    id = id,
    kind = kind,
    type = type,
    token = token,
    label = label,
    variant = variant,
    x = x,
    y = y,
    collapsed = collapsed,
    collapsedLatex = collapsedLatex,
    collapsedPayload = collapsedPayload
)

fun portsFor(node: AuraNode): List<PortDef> {
    val exponent = PortDef(
        id = PortId.Exponent,
        label = "Exponent",
        accepts = listOf(MathType.Scalar, MathType.Variable, MathType.Expression),
        required = false,
        hint = "Raise this element to a power",
        side = PortSide.TopRight
    )
    val subscript = PortDef(
        id = PortId.Subscript,
        label = "Subscript",
        accepts = listOf(MathType.Scalar, MathType.Variable),
        required = false,
        hint = "Attach an index",
        side = PortSide.BottomRight
    )
    val v = node.variant
    return when (node.kind) {
        NodeKind.Operator -> {
            when {
                v != null && v in listOf("abs", "norm", "floor", "ceil") -> listOf(
                    PortDef(PortId.Arg, "Operand", Ontology.OPERAND_TYPES, true, "Expression inside $v", PortSide.Right)
                )
                v != null && v in Ontology.POSTFIX_VARIANTS -> listOf(
                    PortDef(PortId.Arg, "Operand", Ontology.OPERAND_TYPES, true, "Apply $v to this expression", PortSide.Left)
                )
                v != null && v in Ontology.PREFIX_VARIANTS -> listOf(
                    PortDef(PortId.Arg, "Operand", Ontology.OPERAND_TYPES, true, "Scope of $v", PortSide.Right)
                )
                v == "binom" -> listOf(
                    PortDef(PortId.Numerator, "Upper index", Ontology.OPERAND_TYPES, true, "Choose from", PortSide.TopRight),
                    PortDef(PortId.Denominator, "Lower index", Ontology.OPERAND_TYPES, true, "Choose this many", PortSide.BottomRight)
                )
                v == "power" -> listOf(
                    PortDef(PortId.Base, "Base", Ontology.OPERAND_TYPES, true, "Value being exponentiated", PortSide.Left),
                    PortDef(PortId.Exponent, "Exponent", Ontology.OPERAND_TYPES, true, "Power to raise to", PortSide.Right)
                )
                v == "frac" -> listOf(
                    PortDef(PortId.Numerator, "Numerator", Ontology.OPERAND_TYPES, true, "Top of the fraction", PortSide.TopRight),
                    PortDef(PortId.Denominator, "Denominator", Ontology.OPERAND_TYPES, true, "Bottom of the fraction", PortSide.BottomRight)
                )
                else -> listOf(
                    PortDef(PortId.Left, "Left operand", Ontology.OPERAND_TYPES, true, "First operand", PortSide.Left),
                    PortDef(PortId.Right, "Right operand", Ontology.OPERAND_TYPES, true, "Second operand", PortSide.Right)
                )
            }
        }
        NodeKind.Relation -> {
            if (v != null && v in listOf("in", "notin", "subset", "supset", "subseteq", "supseteq")) {
                listOf(
                    PortDef(PortId.Left, "Element", Ontology.OPERAND_TYPES, true, "Member expression", PortSide.Left),
                    PortDef(PortId.Right, "Set", listOf(MathType.Set), true, "Set it belongs to", PortSide.Right)
                )
            } else {
                listOf(
                    PortDef(PortId.Left, "Left side", Ontology.OPERAND_TYPES, true, "Left expression", PortSide.Left),
                    PortDef(PortId.Right, "Right side", Ontology.OPERAND_TYPES, true, "Right expression", PortSide.Right)
                )
            }
        }
        NodeKind.Function -> listOf(
            PortDef(PortId.Arg, "Argument", Ontology.OPERAND_TYPES, true, "Value the function acts on", PortSide.Right)
        )
        NodeKind.Container -> {
            when {
                v != null && v in Ontology.ACCENT_VARIANTS -> listOf(
                    PortDef(PortId.Body, "Accented body", Ontology.OPERAND_TYPES, true, "Expression under the accent", PortSide.Right)
                )
                v != null && v in Ontology.MATRIX_VARIANTS -> listOf(
                    PortDef(PortId.Body, "Entries", Ontology.OPERAND_TYPES, true, "Rows and columns of the array", PortSide.Right)
                )
                v != null && v in Ontology.ENCLOSURE_VARIANTS -> listOf(
                    PortDef(PortId.Body, "Body", Ontology.OPERAND_TYPES, true, "Expression inside the enclosure", PortSide.Right)
                )
                v == "sqrt" -> listOf(
                    PortDef(PortId.Body, "Radicand", Ontology.OPERAND_TYPES, true, "Expression under the radical", PortSide.Right)
                )
                v == "root" -> listOf(
                    PortDef(PortId.Upper, "Index", listOf(MathType.Scalar, MathType.Variable), false, "Root index", PortSide.TopRight),
                    PortDef(PortId.Body, "Radicand", Ontology.OPERAND_TYPES, true, "Expression under the radical", PortSide.Right)
                )
                v != null && v in Ontology.BIG_VARIANTS -> listOf(
                    PortDef(PortId.Lower, "Lower limit", listOf(MathType.Scalar, MathType.Variable), false, "Lower bound", PortSide.BottomRight),
                    PortDef(PortId.Upper, "Upper limit", listOf(MathType.Scalar, MathType.Variable), false, "Upper bound", PortSide.TopRight),
                    PortDef(PortId.Body, "Body", Ontology.OPERAND_TYPES, true, "Integrand / summand", PortSide.Right)
                )
                else -> listOf(
                    PortDef(PortId.Body, "Body", Ontology.OPERAND_TYPES, true, "Contained expression", PortSide.Right)
                )
            }
        }
        NodeKind.Set -> emptyList()
        NodeKind.Group -> listOf(exponent)
        NodeKind.Literal, NodeKind.Variable -> listOf(exponent, subscript)
    }
}

fun findPort(node: AuraNode, portId: PortId): PortDef? =
    portsFor(node).find { it.id == portId }

data class AdmissionResult(val ok: Boolean, val exact: Boolean)

fun admits(accepts: List<MathType>, type: MathType): AdmissionResult {
    if (type in accepts) return AdmissionResult(ok = true, exact = true)
    if (type == MathType.Unknown) return AdmissionResult(ok = false, exact = false)
    if (MathType.Expression in accepts && type in Ontology.OPERAND_TYPES) {
        return AdmissionResult(ok = true, exact = false)
    }
    if (MathType.Variable in accepts && type == MathType.Scalar) {
        return AdmissionResult(ok = true, exact = false)
    }
    if (MathType.Scalar in accepts && (type == MathType.Variable || type == MathType.Expression)) {
        return AdmissionResult(ok = true, exact = false)
    }
    return AdmissionResult(ok = false, exact = false)
}

fun portAdmits(port: PortDef, type: MathType): AdmissionResult = admits(port.accepts, type)

fun isShareable(node: AuraNode): Boolean = node.kind in Ontology.SHAREABLE_KINDS

fun isOperandLike(node: AuraNode, type: MathType): Boolean {
    if (node.kind == NodeKind.Relation || node.kind == NodeKind.Operator) return false
    return type in Ontology.EXPRESSIONISH
}

fun estimateWidth(node: AuraNode): Float {
    val token = node.token
    val v = node.variant
    return when (node.kind) {
        NodeKind.Operator -> when {
            v == "power" -> 24f
            v == "frac" -> 44f
            v == "binom" -> 48f
            v != null && (v in Ontology.POSTFIX_VARIANTS || v in Ontology.PREFIX_VARIANTS) -> 40f
            else -> 46f
        }
        NodeKind.Relation -> if (token.length > 3) 58f else 52f
        NodeKind.Function -> max(64f, 24f + token.replace("\\", "").length * 13f)
        NodeKind.Container -> when {
            v != null && v in Ontology.MATRIX_VARIANTS -> 66f
            v != null && (v in Ontology.ACCENT_VARIANTS || v == "sqrt") -> 56f
            v == "frac" -> 44f
            else -> 54f
        }
        NodeKind.Set -> 54f
        NodeKind.Group -> {
            val inner = node.collapsedLatex ?: token
            min(190f, max(64f, 30f + inner.length * 9f))
        }
        else -> {
            val clean = token.replace("\\", "")
            max(Ontology.TILE_MIN, 22f + min(clean.length, 6) * 11f)
        }
    }
}

fun tileHeight(): Float = Ontology.TILE_HEIGHT

fun nodeDisplayKind(node: AuraNode): String = when (node.kind) {
    NodeKind.Literal -> "Literal"
    NodeKind.Variable -> "Variable"
    NodeKind.Operator -> "Operator"
    NodeKind.Relation -> "Relation"
    NodeKind.Function -> "Function"
    NodeKind.Container -> "Container"
    NodeKind.Set -> "Set"
    NodeKind.Group -> "Collapsed expression"
}

fun arityOf(node: AuraNode): Int = portsFor(node).count { it.required }
