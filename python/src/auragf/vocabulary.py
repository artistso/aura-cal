"""AGF vocabulary: kinds, ports and the admissibility rules (SPEC §3, §4.2).

Every constant here is normative. ``docs/conformance/cases.json`` checks the derived port sets
against the reference implementation.
"""

from __future__ import annotations

from dataclasses import dataclass

# --------------------------------------------------------------------------- kinds

KINDS = frozenset(
    {"literal", "variable", "operator", "relation", "function", "container", "set", "group"}
)

#: A shareable vertex may appear under several parents; the rest are tree nodes (SPEC §3.1).
SHAREABLE_KINDS = frozenset({"literal", "variable", "set"})

PORT_NAMES = (
    "left",
    "right",
    "base",
    "exponent",
    "subscript",
    "numerator",
    "denominator",
    "lower",
    "upper",
    "body",
    "arg",
)

# --------------------------------------------------------------------------- types

#: SPEC §4. The lattice is flat in 0.1; widening lives in :func:`admits`, not in a subtype table.
TYPES = (
    "Scalar",
    "Variable",
    "Vector",
    "Matrix",
    "Set",
    "Expression",
    "Relation",
    "Function",
    "Operator",
    "Unknown",
)

OPERAND_TYPES = frozenset({"Scalar", "Variable", "Vector", "Matrix", "Expression"})
SCALARISH = frozenset({"Scalar", "Variable"})

#: Index and limit ports accept a bare bound or an index relation such as ``n = 1`` (SPEC §3.3).
BOUND_TYPES = frozenset({"Scalar", "Variable", "Relation"})

# --------------------------------------------------------------------------- variants

POSTFIX_VARIANTS = frozenset(
    {"fact", "transpose", "inverse", "conj", "prime", "doubleprime", "degree", "complement"}
)
PREFIX_VARIANTS = frozenset({"neg", "forall", "exists", "nabla", "partial", "pm"})
ACCENT_VARIANTS = frozenset(
    {"hat", "bar", "vec", "dot", "ddot", "tilde", "overline", "underline", "check",
     "widehat", "widetilde"}
)
MATRIX_VARIANTS = frozenset({"matrix", "bmatrix", "vmatrix", "cases"})
ENCLOSURE_VARIANTS = frozenset({"abs", "norm", "floor", "ceil"})
BIG_VARIANTS = frozenset(
    {"sum", "prod", "coprod", "int", "iint", "iiint", "oint", "lim", "limsup", "liminf",
     "sup", "inf", "max", "min", "bigcup", "bigcap", "bigoplus", "bigotimes", "det", "gcd",
     "argmin", "argmax", "span"}
)
MEMBERSHIP_VARIANTS = frozenset({"in", "notin", "subset", "supset", "subseteq", "supseteq"})

# --------------------------------------------------------------------------- ports


@dataclass(frozen=True)
class PortDef:
    """A declared slot on a vertex (SPEC §3.3)."""

    id: str
    label: str
    accepts: frozenset[str]
    required: bool


_EXPONENT = PortDef("exponent", "Exponent", frozenset({"Scalar", "Variable", "Expression"}), False)
_SUBSCRIPT = PortDef("subscript", "Subscript", frozenset({"Scalar", "Variable"}), False)

_BINARY = (
    PortDef("left", "Left operand", OPERAND_TYPES, True),
    PortDef("right", "Right operand", OPERAND_TYPES, True),
)


def ports_for(kind: str, variant: str | None) -> tuple[PortDef, ...]:
    """Declared ports for ``(kind, variant)``. An empty tuple means the vertex takes none."""
    if kind == "operator":
        if variant in ENCLOSURE_VARIANTS:
            return (PortDef("arg", "Operand", OPERAND_TYPES, True),)
        if variant in POSTFIX_VARIANTS or variant in PREFIX_VARIANTS:
            return (PortDef("arg", "Operand", OPERAND_TYPES, True),)
        if variant == "binom":
            return (
                PortDef("numerator", "Upper index", OPERAND_TYPES, True),
                PortDef("denominator", "Lower index", OPERAND_TYPES, True),
            )
        if variant == "power":
            return (
                PortDef("base", "Base", OPERAND_TYPES, True),
                PortDef("exponent", "Exponent", OPERAND_TYPES, True),
            )
        if variant == "frac":
            return (
                PortDef("numerator", "Numerator", OPERAND_TYPES, True),
                PortDef("denominator", "Denominator", OPERAND_TYPES, True),
            )
        return _BINARY

    if kind == "relation":
        if variant in MEMBERSHIP_VARIANTS:
            return (
                PortDef("left", "Element", OPERAND_TYPES, True),
                PortDef("right", "Set", frozenset({"Set"}), True),
            )
        return (
            PortDef("left", "Left side", OPERAND_TYPES, True),
            PortDef("right", "Right side", OPERAND_TYPES, True),
        )

    if kind == "function":
        return (PortDef("arg", "Argument", OPERAND_TYPES, True),)

    if kind == "container":
        if variant in ACCENT_VARIANTS or variant in MATRIX_VARIANTS or variant in ENCLOSURE_VARIANTS:
            return (PortDef("body", "Body", OPERAND_TYPES, True),)
        if variant == "sqrt":
            return (PortDef("body", "Radicand", OPERAND_TYPES, True),)
        if variant == "root":
            return (
                PortDef("upper", "Index", SCALARISH, False),
                PortDef("body", "Radicand", OPERAND_TYPES, True),
            )
        if variant in BIG_VARIANTS:
            return (
                PortDef("lower", "Lower limit", BOUND_TYPES, False),
                PortDef("upper", "Upper limit", BOUND_TYPES, False),
                PortDef("body", "Body", OPERAND_TYPES, True),
            )
        return (PortDef("body", "Body", OPERAND_TYPES, True),)

    if kind == "set":
        return ()

    if kind == "group":
        return (_EXPONENT,)

    if kind in ("literal", "variable"):
        return (_EXPONENT, _SUBSCRIPT)

    return ()


#: ``(kind, variant) -> port names`` for quick membership checks (SPEC §5 W5).
PORTS: dict[str, dict[str | None, tuple[str, ...]]] = {}


def _build_port_index() -> None:
    variants: set[str | None] = {None}
    for group in (
        POSTFIX_VARIANTS,
        PREFIX_VARIANTS,
        ACCENT_VARIANTS,
        MATRIX_VARIANTS,
        ENCLOSURE_VARIANTS,
        BIG_VARIANTS,
        MEMBERSHIP_VARIANTS,
    ):
        variants |= set(group)
    variants |= {
        "binom", "power", "frac", "sqrt", "root", "matrix", "cases", "add", "sub", "mul",
        "juxta", "div", "equal", "approx", "leq", "geq", "neq", "mapsto", "neg", "exp",
        "ln", "sin", "cos",
    }
    for kind in sorted(KINDS):
        PORTS[kind] = {v: tuple(p.id for p in ports_for(kind, v)) for v in variants}


_build_port_index()


def declares_port(kind: str, variant: str | None, port: str) -> bool:
    """True when ``port`` is declared for the vertex. Unknown variants fall back to ``None``."""
    table = PORTS.get(kind, {})
    names = table.get(variant)
    if names is None:
        names = table.get(None, ())
    return port in names


# --------------------------------------------------------------------------- admissibility


@dataclass(frozen=True)
class Admission:
    ok: bool
    exact: bool


def admits(accepts: frozenset[str], math_type: str) -> Admission:
    """Admissibility with widening (SPEC §4.2)."""
    if math_type in accepts:
        return Admission(True, True)
    if math_type == "Unknown":
        return Admission(False, False)
    if "Expression" in accepts and math_type in OPERAND_TYPES:
        return Admission(True, False)
    if "Variable" in accepts and math_type == "Scalar":
        return Admission(True, False)
    if "Scalar" in accepts and math_type in ("Variable", "Expression"):
        return Admission(True, False)
    return Admission(False, False)
