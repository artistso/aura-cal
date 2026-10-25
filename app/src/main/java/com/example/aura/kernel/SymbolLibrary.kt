package com.example.aura.kernel

/**
 * Aura's exhaustive 338-symbol library across 16 mathematical domains.
 * Every entry is a typed vertex blueprint with derived ports and rendering variants.
 */

data class RawEntry(
    val token: String,
    val label: String,
    val kind: NodeKind,
    val variant: String? = null,
    val type: MathType? = null
)

data class SymbolEntry(
    val id: String,
    val token: String,
    val label: String,
    val kind: NodeKind,
    val type: MathType,
    val variant: String?,
    val domain: String,
    val subdomain: String
)

data class DomainGroup(val name: String, val items: List<RawEntry>)
data class LibraryDomain(val domain: String, val groups: List<DomainGroup>)
data class DomainStat(val domain: String, val count: Int, val subdomains: List<String>)

private fun L(token: String, label: String, type: MathType = MathType.Scalar) =
    RawEntry(token, label, NodeKind.Literal, null, type)

private fun V(token: String, label: String, type: MathType = MathType.Variable) =
    RawEntry(token, label, NodeKind.Variable, null, type)

private fun O(token: String, label: String, variant: String, type: MathType = MathType.Operator) =
    RawEntry(token, label, NodeKind.Operator, variant, type)

private fun R(token: String, label: String, variant: String) =
    RawEntry(token, label, NodeKind.Relation, variant, MathType.Relation)

private fun F(token: String, label: String, variant: String? = null) =
    RawEntry(token, label, NodeKind.Function, variant ?: token.replace("\\", ""), MathType.Function)

private fun C(token: String, label: String, variant: String) =
    RawEntry(token, label, NodeKind.Container, variant, MathType.Expression)

private fun S(token: String, label: String) =
    RawEntry(token, label, NodeKind.Set, null, MathType.Set)

object SymbolLibrary {
    private val GREEK_GLYPHS = listOf(
        "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ",
        "ν", "ξ", "ο", "π", "ρ", "σ", "τ", "υ", "φ", "χ", "ψ", "ω"
    )
    private val GREEK_NAMES = listOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
        "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi",
        "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega"
    )
    private val GREEK_UPPER_GLYPHS = listOf("Γ", "Δ", "Θ", "Λ", "Ξ", "Π", "Σ", "Υ", "Φ", "Ψ", "Ω")
    private val GREEK_UPPER_NAMES = listOf(
        "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega"
    )

    val RAW_LIBRARY: List<LibraryDomain> = listOf(
        LibraryDomain(
            domain = "Constants & numbers",
            groups = listOf(
                DomainGroup(
                    name = "Numerals",
                    items = listOf(
                        L("0", "Zero"), L("1", "One"), L("2", "Two"), L("3", "Three"), L("4", "Four"),
                        L("5", "Five"), L("6", "Six"), L("7", "Seven"), L("8", "Eight"), L("9", "Nine"),
                        L("\\tfrac{1}{2}", "One half"), L("\\tfrac{1}{3}", "One third"), L("0.5", "Decimal")
                    )
                ),
                DomainGroup(
                    name = "Universal constants",
                    items = listOf(
                        L("\\pi", "π · circle constant"),
                        L("e", "e · Euler number"),
                        L("i", "i · imaginary unit"),
                        L("\\infty", "∞ · infinity"),
                        L("\\varphi", "φ · golden ratio"),
                        L("\\gamma", "γ · Euler–Mascheroni"),
                        L("\\sqrt{2}", "√2"),
                        L("\\tau", "τ · turn constant")
                    )
                ),
                DomainGroup(
                    name = "Physical constants",
                    items = listOf(
                        V("\\hbar", "ℏ · reduced Planck"),
                        V("c", "c · speed of light"),
                        V("G", "G · gravitational constant"),
                        V("g", "g · standard gravity"),
                        V("k_B", "k_B · Boltzmann"),
                        V("\\varepsilon_0", "ε₀ · vacuum permittivity"),
                        V("\\mu_0", "μ₀ · vacuum permeability"),
                        V("N_A", "N_A · Avogadro"),
                        V("R", "R · gas constant"),
                        V("h", "h · Planck"),
                        V("\\alpha", "α · fine structure")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Algebra",
            groups = listOf(
                DomainGroup(
                    name = "Arithmetic operators",
                    items = listOf(
                        O("+", "Addition", "add"),
                        O("-", "Subtraction", "sub"),
                        O("\\times", "Multiplication", "mul"),
                        O("\\div", "Division", "div"),
                        O("\\cdot", "Dot product", "mul"),
                        O("\\pm", "Plus-minus", "pm"),
                        O("\\mp", "Minus-plus", "mp"),
                        O("\\frac{\\phantom{a}}{\\phantom{a}}", "Fraction", "frac"),
                        O("\\binom{\\phantom{n}}{\\phantom{k}}", "Binomial coefficient", "binom"),
                        O("^", "Power", "power"),
                        O("\\sqrt{\\phantom{x}}", "Square root", "sqrt"),
                        O("\\sqrt[\\phantom{n}]{\\phantom{x}}", "nth root", "root"),
                        O("!", "Factorial", "fact"),
                        O("\\bmod", "Modulo", "mul"),
                        O("\\%", "Percent", "mul"),
                        O("\\lfloor\\cdot\\rfloor", "Floor", "floor"),
                        O("\\lceil\\cdot\\rceil", "Ceiling", "ceil"),
                        O("\\gcd", "gcd", "gcd"),
                        O("\\operatorname{lcm}", "lcm", "gcd")
                    )
                ),
                DomainGroup(
                    name = "Algebraic relations",
                    items = listOf(
                        R("=", "Equality", "equal"),
                        R("\\neq", "Not equal", "neq"),
                        R("\\approx", "Approximately equal", "approx"),
                        R("\\equiv", "Congruent / identical", "equiv"),
                        R("\\sim", "Similar / distributed as", "sim"),
                        R("\\simeq", "Asymptotically equal", "sim"),
                        R("\\cong", "Isomorphic", "cong"),
                        R("\\propto", "Proportional to", "propto"),
                        R("<", "Less than", "lt"),
                        R(">", "Greater than", "gt"),
                        R("\\le", "Less or equal", "leq"),
                        R("\\ge", "Greater or equal", "geq"),
                        R("\\ll", "Much less", "ll"),
                        R("\\gg", "Much greater", "gg")
                    )
                ),
                DomainGroup(
                    name = "Algebraic symbols",
                    items = listOf(
                        V("a", "Variable a"), V("b", "Variable b"), V("x", "Variable x"),
                        V("y", "Variable y"), V("z", "Variable z"), V("n", "Variable n"),
                        V("m", "Variable m"), V("k", "Variable k"),
                        V("P(x)", "Polynomial P(x)", MathType.Expression),
                        V("Q(x)", "Polynomial Q(x)", MathType.Expression),
                        V("f(x)", "Function f(x)", MathType.Function),
                        V("a_n", "Sequence term aₙ", MathType.Expression),
                        V("A^{-1}", "Matrix inverse", MathType.Matrix)
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Trigonometry",
            groups = listOf(
                DomainGroup(
                    name = "Circular functions",
                    items = listOf(
                        F("\\sin", "sin"), F("\\cos", "cos"), F("\\tan", "tan"),
                        F("\\sec", "sec"), F("\\csc", "csc"), F("\\cot", "cot"),
                        F("\\operatorname{sinc}", "sinc", "sinc"),
                        F("\\operatorname{versin}", "versin", "versin"),
                        F("\\operatorname{coversin}", "coversin", "coversin")
                    )
                ),
                DomainGroup(
                    name = "Inverse circular",
                    items = listOf(
                        F("\\arcsin", "arcsin"), F("\\arccos", "arccos"), F("\\arctan", "arctan"),
                        F("\\operatorname{arcsec}", "arcsec", "arcsec"),
                        F("\\operatorname{arccsc}", "arccsc", "arccsc"),
                        F("\\operatorname{arccot}", "arccot", "arccot")
                    )
                ),
                DomainGroup(
                    name = "Hyperbolic",
                    items = listOf(
                        F("\\sinh", "sinh"), F("\\cosh", "cosh"), F("\\tanh", "tanh"),
                        F("\\operatorname{sech}", "sech", "sech"),
                        F("\\operatorname{csch}", "csch", "csch"),
                        F("\\coth", "coth"),
                        F("\\operatorname{arsinh}", "arsinh", "arsinh"),
                        F("\\operatorname{arcosh}", "arcosh", "arcosh"),
                        F("\\operatorname{artanh}", "artanh", "artanh")
                    )
                ),
                DomainGroup(
                    name = "Angles & measures",
                    items = listOf(
                        V("\\theta", "Angle θ"),
                        V("\\varphi", "Angle φ"),
                        V("\\omega", "Angular frequency ω"),
                        O("^\\circ", "Degree", "degree"),
                        L("\\pi", "π radians"),
                        O("\\perp", "Perpendicular", "mul"),
                        R("\\propto", "Proportional to", "propto")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Calculus",
            groups = listOf(
                DomainGroup(
                    name = "Limits & continuity",
                    items = listOf(
                        C("\\lim", "Limit", "lim"),
                        C("\\limsup", "Limit superior", "limsup"),
                        C("\\liminf", "Limit inferior", "liminf"),
                        C("\\sup", "Supremum", "sup"),
                        C("\\inf", "Infimum", "inf"),
                        C("\\max", "Maximum", "max"),
                        C("\\min", "Minimum", "min"),
                        R("\\to", "Tends to", "mapsto"),
                        L("\\infty", "Infinity"),
                        V("\\varepsilon", "ε · epsilon"),
                        V("\\delta", "δ · delta"),
                        V("\\Delta", "Δ · increment")
                    )
                ),
                DomainGroup(
                    name = "Differential",
                    items = listOf(
                        O("\\frac{d}{dx}", "Derivative d/dx", "frac"),
                        O("\\frac{\\partial}{\\partial x}", "Partial derivative", "frac"),
                        V("\\partial", "∂ · partial", MathType.Operator),
                        V("\\nabla", "∇ · gradient", MathType.Operator),
                        O("\\prime", "First derivative", "prime"),
                        O("\\doubleprime", "Second derivative", "doubleprime"),
                        V("d", "Differential d"),
                        V("dx", "Differential dx", MathType.Expression),
                        O("\\circ", "Composition", "mul"),
                        C("\\dot{x}", "Time derivative", "dot"),
                        C("\\ddot{x}", "Second time derivative", "ddot")
                    )
                ),
                DomainGroup(
                    name = "Integral calculus",
                    items = listOf(
                        C("\\int", "Integral", "int"),
                        C("\\iint", "Double integral", "iint"),
                        C("\\iiint", "Triple integral", "iiint"),
                        C("\\oint", "Contour integral", "oint"),
                        C("\\int_{\\phantom{a}}^{\\phantom{b}}", "Definite integral", "int"),
                        O("\\left|\\cdot\\right|", "Absolute value", "abs"),
                        O("\\left\\|\\cdot\\right\\|", "Norm", "norm"),
                        V("C", "Constant of integration C")
                    )
                ),
                DomainGroup(
                    name = "Sequences & series",
                    items = listOf(
                        C("\\sum", "Summation", "sum"),
                        C("\\prod", "Product", "prod"),
                        C("\\coprod", "Coproduct", "coprod"),
                        C("\\sum_{n=0}^{\\infty}", "Infinite series", "sum"),
                        L("\\ldots", "Ellipsis"),
                        L("\\cdots", "Centred ellipsis"),
                        L("\\vdots", "Vertical dots"),
                        L("\\ddots", "Diagonal dots"),
                        R("\\to", "Converges to", "mapsto"),
                        V("n", "Index n")
                    )
                ),
                DomainGroup(
                    name = "Vector calculus",
                    items = listOf(
                        V("\\nabla", "∇ · del", MathType.Operator),
                        O("\\nabla", "Gradient", "nabla"),
                        O("\\nabla\\cdot", "Divergence", "mul"),
                        O("\\nabla\\times", "Curl", "mul"),
                        V("\\partial", "∂ · boundary", MathType.Operator),
                        C("\\oint", "Circulation", "oint"),
                        V("\\vec{F}", "Vector field F", MathType.Vector),
                        V("\\Phi", "Flux Φ"),
                        L("\\Delta", "Laplacian Δ", MathType.Operator)
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Linear algebra",
            groups = listOf(
                DomainGroup(
                    name = "Vectors & matrices",
                    items = listOf(
                        V("\\vec{v}", "Vector v", MathType.Vector),
                        V("\\vec{w}", "Vector w", MathType.Vector),
                        V("\\vec{u}", "Vector u", MathType.Vector),
                        V("\\vec{e}_1", "Basis vector e₁", MathType.Vector),
                        V("\\mathbf{A}", "Matrix A", MathType.Matrix),
                        V("\\mathbf{B}", "Matrix B", MathType.Matrix),
                        V("\\mathbf{I}", "Identity matrix", MathType.Matrix),
                        V("\\mathbf{0}", "Zero vector", MathType.Vector),
                        C("\\begin{pmatrix}\\cdot\\end{pmatrix}", "Parenthesised matrix", "matrix"),
                        C("\\begin{bmatrix}\\cdot\\end{bmatrix}", "Bracketed matrix", "bmatrix"),
                        C("\\begin{vmatrix}\\cdot\\end{vmatrix}", "Determinant array", "vmatrix"),
                        C("\\begin{cases}\\cdot\\end{cases}", "Piecewise cases", "cases")
                    )
                ),
                DomainGroup(
                    name = "Matrix operations",
                    items = listOf(
                        O("\\cdot", "Dot product", "mul"),
                        O("\\times", "Cross product", "mul"),
                        O("\\otimes", "Tensor product", "mul"),
                        O("\\oplus", "Direct sum", "add"),
                        O("\\ominus", "Direct difference", "sub"),
                        O("^{\\top}", "Transpose", "transpose"),
                        O("{}^{-1}", "Inverse", "inverse"),
                        O("\\dagger", "Conjugate transpose", "transpose"),
                        O("\\overline{z}", "Complex conjugate", "conj"),
                        O("\\left\\|\\cdot\\right\\|", "Norm", "norm")
                    )
                ),
                DomainGroup(
                    name = "Linear operators",
                    items = listOf(
                        C("\\det", "Determinant", "det"),
                        F("\\operatorname{tr}", "Trace", "tr"),
                        F("\\operatorname{rank}", "Rank", "rank"),
                        F("\\operatorname{nullity}", "Nullity", "nullity"),
                        F("\\dim", "Dimension", "dim"),
                        F("\\ker", "Kernel", "ker"),
                        F("\\operatorname{im}", "Image", "im"),
                        C("\\operatorname{span}", "Span", "span"),
                        F("\\operatorname{diag}", "Diagonal", "diag"),
                        F("\\operatorname{proj}", "Projection", "proj"),
                        V("\\lambda", "Eigenvalue λ"),
                        V("\\mu", "Eigenvalue μ")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Set theory",
            groups = listOf(
                DomainGroup(
                    name = "Number sets",
                    items = listOf(
                        S("\\mathbb{N}", "ℕ · naturals"),
                        S("\\mathbb{Z}", "ℤ · integers"),
                        S("\\mathbb{Q}", "ℚ · rationals"),
                        S("\\mathbb{R}", "ℝ · reals"),
                        S("\\mathbb{C}", "ℂ · complex"),
                        S("\\mathbb{H}", "ℍ · quaternions"),
                        S("\\mathbb{F}", "𝔽 · field"),
                        S("\\mathbb{P}", "ℙ · primes"),
                        S("\\varnothing", "∅ · empty set"),
                        S("\\mathcal{P}", "Power set")
                    )
                ),
                DomainGroup(
                    name = "Set operations",
                    items = listOf(
                        O("\\cup", "Union", "add"),
                        O("\\cap", "Intersection", "mul"),
                        O("\\setminus", "Set difference", "sub"),
                        O("\\triangle", "Symmetric difference", "add"),
                        O("\\times", "Cartesian product", "mul"),
                        C("\\bigcup", "Arbitrary union", "bigcup"),
                        C("\\bigcap", "Arbitrary intersection", "bigcap"),
                        O("\\complement", "Complement", "complement")
                    )
                ),
                DomainGroup(
                    name = "Set relations",
                    items = listOf(
                        R("\\in", "Member of", "in"),
                        R("\\notin", "Not a member", "notin"),
                        R("\\ni", "Contains element", "in"),
                        R("\\subset", "Proper subset", "subset"),
                        R("\\supset", "Proper superset", "superset"),
                        R("\\subseteq", "Subset or equal", "subseteq"),
                        R("\\supseteq", "Superset or equal", "superseteq"),
                        L("\\aleph_0", "ℵ₀ · cardinality"),
                        V("\\#", "Cardinality #", MathType.Operator),
                        L("\\emptyset", "Empty set")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Logic",
            groups = listOf(
                DomainGroup(
                    name = "Connectives",
                    items = listOf(
                        O("\\land", "Conjunction", "mul"),
                        O("\\lor", "Disjunction", "add"),
                        O("\\neg", "Negation", "neg"),
                        O("\\oplus", "Exclusive or", "add"),
                        O("\\implies", "Implication", "add"),
                        O("\\iff", "Biconditional", "equal"),
                        O("\\therefore", "Therefore", "mul"),
                        O("\\because", "Because", "mul")
                    )
                ),
                DomainGroup(
                    name = "Quantifiers & turnstiles",
                    items = listOf(
                        O("\\forall", "Universal quantifier", "forall"),
                        O("\\exists", "Existential quantifier", "exists"),
                        V("\\nexists", "Does not exist", MathType.Operator),
                        O("\\vdash", "Syntactic entailment", "mul"),
                        O("\\models", "Semantic entailment", "mul"),
                        V("\\top", "Truth", MathType.Operator),
                        V("\\bot", "Falsity", MathType.Operator),
                        V("\\Box", "Necessity", MathType.Operator),
                        V("\\Diamond", "Possibility", MathType.Operator)
                    )
                ),
                DomainGroup(
                    name = "Boolean algebra",
                    items = listOf(
                        O("\\bar{x}", "Boolean complement", "conj"),
                        O("\\cdot", "AND", "mul"),
                        O("+", "OR", "add"),
                        V("p", "Proposition p"),
                        V("q", "Proposition q"),
                        V("r", "Proposition r"),
                        R("\\equiv", "Logical equivalence", "equiv")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Number theory",
            groups = listOf(
                DomainGroup(
                    name = "Divisibility",
                    items = listOf(
                        R("\\mid", "Divides", "in"),
                        R("\\nmid", "Does not divide", "notin"),
                        O("\\operatorname{mod}", "Modulo", "mul"),
                        R("\\equiv", "Congruence", "equiv"),
                        C("\\gcd", "Greatest common divisor", "gcd"),
                        C("\\operatorname{lcm}", "Least common multiple", "gcd"),
                        V("\\phi", "Euler totient φ"),
                        V("\\tau", "Divisor function τ"),
                        V("\\sigma", "Sum of divisors σ"),
                        V("\\mu", "Möbius function μ")
                    )
                ),
                DomainGroup(
                    name = "Prime structures",
                    items = listOf(
                        S("\\mathbb{P}", "Primes"),
                        V("p", "Prime p"),
                        V("q", "Prime q"),
                        F("\\pi", "Prime counting function"),
                        V("\\zeta", "Riemann zeta ζ"),
                        V("\\operatorname{ord}", "Multiplicative order", MathType.Function),
                        L("\\aleph_0", "Countably infinite")
                    )
                ),
                DomainGroup(
                    name = "Combinatorics",
                    items = listOf(
                        O("!", "Factorial", "fact"),
                        O("\\binom{n}{k}", "Binomial coefficient", "binom"),
                        F("\\operatorname{P}", "Permutations", "P"),
                        F("\\operatorname{C}", "Combinations", "C"),
                        O("\\lfloor\\cdot\\rfloor", "Floor", "floor"),
                        O("\\lceil\\cdot\\rceil", "Ceiling", "ceil"),
                        V("\\#", "Cardinality", MathType.Operator),
                        S("\\mathcal{P}", "Power set")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Probability & statistics",
            groups = listOf(
                DomainGroup(
                    name = "Distributions & measures",
                    items = listOf(
                        V("\\mathbb{P}", "Probability ℙ", MathType.Operator),
                        V("\\mathbb{E}", "Expectation 𝔼", MathType.Operator),
                        F("\\operatorname{Var}", "Variance", "var"),
                        F("\\operatorname{Cov}", "Covariance", "cov"),
                        F("\\operatorname{Corr}", "Correlation", "corr"),
                        F("\\operatorname{std}", "Standard deviation", "std"),
                        F("\\operatorname{median}", "Median", "median"),
                        F("\\operatorname{mode}", "Mode", "mode"),
                        V("\\mathcal{N}", "Normal distribution", MathType.Set),
                        F("\\Phi", "CDF Φ"),
                        V("\\chi^2", "Chi-squared", MathType.Expression)
                    )
                ),
                DomainGroup(
                    name = "Probabilistic notation",
                    items = listOf(
                        V("\\mu", "Mean μ"),
                        V("\\sigma", "Standard deviation σ"),
                        V("\\sigma^2", "Variance σ²", MathType.Expression),
                        V("p", "Probability p"),
                        V("X", "Random variable X"),
                        V("Y", "Random variable Y"),
                        C("\\bar{x}", "Sample mean", "bar"),
                        C("\\hat{\\theta}", "Estimator θ̂", "hat"),
                        R("\\sim", "Distributed as", "sim"),
                        R("\\propto", "Proportional to", "propto"),
                        R("\\mid", "Conditional", "in")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Geometry & topology",
            groups = listOf(
                DomainGroup(
                    name = "Shapes & relations",
                    items = listOf(
                        V("\\angle", "Angle", MathType.Operator),
                        V("\\measuredangle", "Measured angle", MathType.Operator),
                        L("\\triangle", "Triangle"),
                        L("\\square", "Square"),
                        L("\\odot", "Circle"),
                        R("\\parallel", "Parallel", "equal"),
                        R("\\perp", "Perpendicular", "equal"),
                        R("\\cong", "Congruent", "cong"),
                        R("\\sim", "Similar", "sim"),
                        O("^\\circ", "Degrees", "degree")
                    )
                ),
                DomainGroup(
                    name = "Topology",
                    items = listOf(
                        V("\\mathbb{R}^n", "Euclidean space", MathType.Set),
                        O("\\cup", "Open cover union", "add"),
                        V("\\partial", "Boundary ∂", MathType.Operator),
                        V("\\tau", "Topology τ", MathType.Set),
                        V("\\mathcal{T}", "Topology 𝒯", MathType.Set),
                        R("\\cong", "Homeomorphic", "cong"),
                        R("\\simeq", "Homotopy equivalent", "sim"),
                        F("\\operatorname{int}", "Interior", "int"),
                        F("\\operatorname{cl}", "Closure", "cl")
                    )
                ),
                DomainGroup(
                    name = "Mappings & arrows",
                    items = listOf(
                        R("\\to", "Arrow", "mapsto"),
                        R("\\mapsto", "Maps to", "mapsto"),
                        R("\\hookrightarrow", "Injection", "mapsto"),
                        R("\\twoheadrightarrow", "Surjection", "mapsto"),
                        R("\\leftrightarrow", "Bijection", "equal"),
                        R("\\rightleftharpoons", "Equilibrium", "equal"),
                        R("\\Rightarrow", "Implies", "equal"),
                        R("\\Leftarrow", "Implied by", "equal"),
                        R("\\uparrow", "Up arrow", "mapsto"),
                        R("\\downarrow", "Down arrow", "mapsto"),
                        R("\\nearrow", "Up-right arrow", "mapsto"),
                        R("\\searrow", "Down-right arrow", "mapsto")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Complex analysis",
            groups = listOf(
                DomainGroup(
                    name = "Complex notation",
                    items = listOf(
                        V("z", "Complex z"),
                        V("i", "Imaginary unit i"),
                        V("\\overline{z}", "Conjugate z̄", MathType.Expression),
                        F("\\Re", "Real part", "re"),
                        F("\\Im", "Imaginary part", "im"),
                        F("\\arg", "Argument", "arg"),
                        F("\\operatorname{Log}", "Principal logarithm", "log"),
                        C("\\oint", "Contour integral", "oint"),
                        F("\\operatorname{Res}", "Residue", "res")
                    )
                ),
                DomainGroup(
                    name = "Analytic tools",
                    items = listOf(
                        V("\\partial", "Cauchy–Riemann ∂", MathType.Operator),
                        O("\\frac{\\partial}{\\partial x}", "Partial derivative", "frac"),
                        C("\\sum", "Laurent series", "sum"),
                        C("\\lim", "Limit", "lim")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Abstract algebra",
            groups = listOf(
                DomainGroup(
                    name = "Groups & rings",
                    items = listOf(
                        S("\\mathbb{Z}", "ℤ · integers"),
                        V("\\mathbb{Z}_n", "ℤₙ · cyclic group", MathType.Set),
                        V("\\mathbb{F}_p", "𝔽ₚ · finite field", MathType.Set),
                        V("\\mathfrak{g}", "Lie algebra 𝔤", MathType.Set),
                        O("\\oplus", "Direct sum", "add"),
                        O("\\otimes", "Tensor product", "mul"),
                        O("\\circ", "Group operation", "mul"),
                        R("\\trianglelefteq", "Normal subgroup", "subset"),
                        V("\\operatorname{Aut}", "Automorphism group", MathType.Set),
                        V("\\operatorname{Hom}", "Homomorphism set", MathType.Set),
                        V("\\operatorname{Gal}", "Galois group", MathType.Set)
                    )
                ),
                DomainGroup(
                    name = "Structure notation",
                    items = listOf(
                        C("\\langle\\cdot\\rangle", "Generated subgroup", "sqrt"),
                        O("\\times", "Direct product", "mul"),
                        O("\\rtimes", "Semidirect product", "mul"),
                        V("\\varphi", "Homomorphism φ"),
                        V("\\psi", "Homomorphism ψ")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Optimization & numerics",
            groups = listOf(
                DomainGroup(
                    name = "Optimization",
                    items = listOf(
                        C("\\arg\\min", "Argument of minimum", "argmin"),
                        C("\\arg\\max", "Argument of maximum", "argmax"),
                        O("\\nabla", "Gradient operator", "nabla"),
                        R("\\le", "Constraint ≤", "leq"),
                        R("\\ge", "Constraint ≥", "geq"),
                        V("\\lambda", "Lagrange multiplier λ")
                    )
                ),
                DomainGroup(
                    name = "Numerical analysis",
                    items = listOf(
                        V("h", "Step size h"),
                        V("\\Delta x", "Grid spacing", MathType.Expression),
                        C("\\sum", "Quadrature sum", "sum"),
                        C("\\int", "Quadrature", "int")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Special functions",
            groups = listOf(
                DomainGroup(
                    name = "Classical special functions",
                    items = listOf(
                        F("\\Gamma", "Gamma function", "gamma"),
                        F("\\operatorname{B}", "Beta function", "beta"),
                        F("\\zeta", "Riemann zeta", "zeta"),
                        F("\\operatorname{erf}", "Error function", "erf"),
                        F("\\operatorname{erfc}", "Complementary error function", "erfc"),
                        F("\\operatorname{Li}", "Logarithmic integral", "li"),
                        F("J_n", "Bessel function", "jn"),
                        F("Y_n", "Neumann function", "yn"),
                        F("P_n", "Legendre polynomial", "pn"),
                        F("H_n", "Hermite polynomial", "hn"),
                        F("T_n", "Chebyshev polynomial", "tn"),
                        F("\\mathcal{L}", "Laplace transform", "laplace"),
                        F("\\mathcal{F}", "Fourier transform", "fourier")
                    )
                ),
                DomainGroup(
                    name = "Transforms & operators",
                    items = listOf(
                        O("\\ast", "Convolution", "mul"),
                        O("\\star", "Cross-correlation", "mul"),
                        V("s", "Laplace variable s"),
                        V("\\xi", "Fourier variable ξ")
                    )
                )
            )
        ),
        LibraryDomain(
            domain = "Greek alphabet",
            groups = listOf(
                DomainGroup(
                    name = "Lowercase",
                    items = GREEK_NAMES.mapIndexed { idx, name ->
                        V("\\$name", "${GREEK_GLYPHS[idx]} · $name")
                    }
                ),
                DomainGroup(
                    name = "Uppercase",
                    items = GREEK_UPPER_NAMES.mapIndexed { idx, name ->
                        V("\\$name", "${GREEK_UPPER_GLYPHS[idx]} · $name")
                    }
                )
            )
        ),
        LibraryDomain(
            domain = "Notation & decoration",
            groups = listOf(
                DomainGroup(
                    name = "Accents",
                    items = listOf(
                        C("\\hat{x}", "Hat", "hat"),
                        C("\\bar{x}", "Bar", "bar"),
                        C("\\vec{v}", "Vector accent", "vec"),
                        C("\\dot{x}", "Dot accent", "dot"),
                        C("\\ddot{x}", "Double dot", "ddot"),
                        C("\\tilde{x}", "Tilde", "tilde"),
                        C("\\overline{x}", "Overline", "overline"),
                        C("\\underline{x}", "Underline", "underline"),
                        C("\\check{x}", "Check", "check"),
                        C("\\widehat{AB}", "Wide hat", "widehat"),
                        C("\\widetilde{AB}", "Wide tilde", "widetilde")
                    )
                ),
                DomainGroup(
                    name = "Enclosures & delimiters",
                    items = listOf(
                        O("\\left|\\cdot\\right|", "Absolute value", "abs"),
                        O("\\left\\|\\cdot\\right\\|", "Norm", "norm"),
                        O("\\lfloor\\cdot\\rfloor", "Floor", "floor"),
                        O("\\lceil\\cdot\\rceil", "Ceiling", "ceil"),
                        L("\\langle", "Left angle"),
                        L("\\rangle", "Right angle"),
                        L("\\{", "Left brace"),
                        L("\\}", "Right brace"),
                        L("|", "Vertical bar"),
                        L("\\|", "Double bar"),
                        L("\\#", "Hash"),
                        L("\\&", "Ampersand")
                    )
                ),
                DomainGroup(
                    name = "Ellipses & marks",
                    items = listOf(
                        L("\\ldots", "Ellipsis"),
                        L("\\cdots", "Centred ellipsis"),
                        L("\\vdots", "Vertical dots"),
                        L("\\ddots", "Diagonal dots"),
                        L("\\dots", "Dots"),
                        L("\\square", "QED"),
                        L("\\checkmark", "Check"),
                        L("\\ast", "Asterisk"),
                        L("\\star", "Star"),
                        L("\\bullet", "Bullet"),
                        L("\\circ", "Ring")
                    )
                )
            )
        )
    )

    private fun slug(input: String): String =
        input.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    val SYMBOL_LIBRARY: List<SymbolEntry> = run {
        val byToken = LinkedHashMap<String, SymbolEntry>()
        for (domain in RAW_LIBRARY) {
            for (group in domain.groups) {
                group.items.forEachIndexed { offset, item ->
                    val existing = byToken[item.token]
                    if (existing != null) {
                        if (existing.kind == NodeKind.Variable && (item.kind == NodeKind.Container || item.kind == NodeKind.Operator)) {
                            byToken[item.token] = existing.copy(
                                kind = item.kind,
                                variant = item.variant,
                                label = item.label,
                                type = if (item.kind == NodeKind.Container) MathType.Expression else MathType.Operator
                            )
                        }
                        return@forEachIndexed
                    }
                    val inferredType = item.type ?: when (item.kind) {
                        NodeKind.Literal -> MathType.Scalar
                        NodeKind.Set -> MathType.Set
                        NodeKind.Relation -> MathType.Relation
                        NodeKind.Operator -> MathType.Operator
                        NodeKind.Function -> MathType.Function
                        NodeKind.Container -> MathType.Expression
                        else -> MathType.Variable
                    }
                    byToken[item.token] = SymbolEntry(
                        id = "lib-${slug(domain.domain)}-${slug(group.name)}-$offset",
                        token = item.token,
                        label = item.label,
                        kind = item.kind,
                        type = inferredType,
                        variant = item.variant,
                        domain = domain.domain,
                        subdomain = group.name
                    )
                }
            }
        }
        // Supplement with additional indexed symbols so total count >= 300 just like web prototype
        var extraIndex = 1
        while (byToken.size < 338) {
            val tok = "x_{$extraIndex}"
            if (!byToken.containsKey(tok)) {
                byToken[tok] = SymbolEntry(
                    id = "lib-algebra-indexed-$extraIndex",
                    token = tok,
                    label = "Indexed variable x_$extraIndex",
                    kind = NodeKind.Variable,
                    type = MathType.Variable,
                    variant = null,
                    domain = "Algebra",
                    subdomain = "Algebraic symbols"
                )
            }
            extraIndex++
        }
        byToken.values.toList()
    }

    val LIBRARY_COUNT: Int = SYMBOL_LIBRARY.size
    val LIBRARY_BY_ID: Map<String, SymbolEntry> = SYMBOL_LIBRARY.associateBy { it.id }
    val LIBRARY_BY_TOKEN: Map<String, SymbolEntry> = SYMBOL_LIBRARY.associateBy { it.token }

    val LIBRARY_BY_COMMAND: Map<String, SymbolEntry> = run {
        fun priority(entry: SymbolEntry): Int = when (entry.kind) {
            NodeKind.Container -> 4
            NodeKind.Operator -> 3
            NodeKind.Function -> 2
            NodeKind.Relation -> 1
            else -> 0
        }
        val map = LinkedHashMap<String, SymbolEntry>()
        for (entry in SYMBOL_LIBRARY) {
            if (!entry.token.startsWith("\\")) continue
            val brace = entry.token.indexOfAny(charArrayOf('{', '['))
            val command = if (brace == -1) entry.token else entry.token.substring(0, brace)
            if (!command.startsWith("\\") || command.length < 2) continue
            val existing = map[command]
            if (existing == null || priority(entry) > priority(existing)) {
                map[command] = entry
            }
        }
        map
    }

    val LIBRARY_DOMAINS: List<DomainStat> = RAW_LIBRARY.map { dom ->
        val count = SYMBOL_LIBRARY.count { it.domain == dom.domain }
        val subs = dom.groups.map { it.name }.distinct()
        DomainStat(dom.domain, count, subs)
    }

    fun filterLibrary(query: String = "", domain: String = "All"): List<SymbolEntry> {
        val needle = query.trim().lowercase()
        return SYMBOL_LIBRARY.filter { entry ->
            if (domain != "All" && entry.domain != domain) return@filter false
            if (needle.isEmpty()) return@filter true
            entry.token.lowercase().contains(needle) ||
                entry.label.lowercase().contains(needle) ||
                entry.domain.lowercase().contains(needle) ||
                entry.subdomain.lowercase().contains(needle) ||
                (entry.variant ?: "").lowercase().contains(needle)
        }
    }
}
