package com.example.aura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraDisplayFontFamily
import com.example.ui.theme.AuraText

/**
 * Native Jetpack Compose mathematical renderer for Aura.
 * Renders stacked fractions, binomials, radicals, integrals/summations with limits,
 * superscripts, subscripts, accents, and full Unicode mathematical symbols at 60–120fps.
 */

object MathFormatter {
    private val SYMBOL_MAP = linkedMapOf(
        "\\mathbb{N}" to "ℕ",
        "\\mathbb{Z}" to "ℤ",
        "\\mathbb{Q}" to "ℚ",
        "\\mathbb{R}" to "ℝ",
        "\\mathbb{C}" to "ℂ",
        "\\mathbb{H}" to "ℍ",
        "\\mathbb{F}" to "𝔽",
        "\\mathbb{P}" to "ℙ",
        "\\mathbb{E}" to "𝔼",
        "\\mathcal{P}" to "𝒫",
        "\\mathcal{N}" to "𝒩",
        "\\mathcal{T}" to "𝒯",
        "\\mathcal{L}" to "ℒ",
        "\\mathcal{F}" to "ℱ",
        "\\mathfrak{g}" to "𝔤",
        "\\aleph_0" to "ℵ₀",
        "\\varepsilon_0" to "ε₀",
        "\\mu_0" to "μ₀",
        "\\vec{e}_1" to "e⃗₁",
        "\\vec{v}" to "v⃗",
        "\\vec{w}" to "w⃗",
        "\\vec{u}" to "u⃗",
        "\\vec{F}" to "F⃗",
        "\\mathbf{A}" to "𝐀",
        "\\mathbf{B}" to "𝐁",
        "\\mathbf{I}" to "𝐈",
        "\\mathbf{0}" to "𝟎",
        "\\hat{x}" to "x̂",
        "\\hat{\\theta}" to "θ̂",
        "\\bar{x}" to "x̄",
        "\\dot{x}" to "ẋ",
        "\\ddot{x}" to "ẍ",
        "\\tilde{x}" to "x̃",
        "\\overline{z}" to "z̅",
        "\\overline{x}" to "x̅",
        "\\underline{x}" to "x̲",
        "\\check{x}" to "x̌",
        "\\widehat{AB}" to "ÂB",
        "\\widetilde{AB}" to "ÃB",
        "\\tfrac{1}{2}" to "½",
        "\\tfrac{1}{3}" to "⅓",
        "\\sqrt{2}" to "√2",
        "\\sqrt{\\phantom{x}}" to "√□",
        "\\sqrt[\\phantom{n}]{\\phantom{x}}" to "ⁿ√□",
        "\\frac{\\phantom{a}}{\\phantom{a}}" to "a/b",
        "\\binom{\\phantom{n}}{\\phantom{k}}" to "(ⁿₖ)",
        "\\left|\\cdot\\right|" to "|·|",
        "\\left\\|\\cdot\\right\\|" to "‖·‖",
        "\\lfloor\\cdot\\rfloor" to "⌊·⌋",
        "\\lceil\\cdot\\rceil" to "⌈·⌉",
        "\\langle\\cdot\\rangle" to "⟨·⟩",
        "\\begin{pmatrix}\\cdot\\end{pmatrix}" to "(⋯)",
        "\\begin{bmatrix}\\cdot\\end{bmatrix}" to "[⋯]",
        "\\begin{vmatrix}\\cdot\\end{vmatrix}" to "|⋯|",
        "\\begin{cases}\\cdot\\end{cases}" to "{⋯",
        "\\doubleprime" to "″",
        "\\prime" to "′",
        "^\\circ" to "°",
        "^{\\circ}" to "°",
        "^{\\top}" to "ᵀ",
        "{}^{-1}" to "⁻¹",
        "\\phantom{a}" to "□",
        "\\phantom{b}" to "□",
        "\\phantom{x}" to "□",
        "\\phantom{n}" to "n",
        "\\phantom{k}" to "k",
        "\\operatorname{sinc}" to "sinc",
        "\\operatorname{versin}" to "versin",
        "\\operatorname{coversin}" to "coversin",
        "\\operatorname{arcsec}" to "arcsec",
        "\\operatorname{arccsc}" to "arccsc",
        "\\operatorname{arccot}" to "arccot",
        "\\operatorname{sech}" to "sech",
        "\\operatorname{csch}" to "csch",
        "\\operatorname{arsinh}" to "arsinh",
        "\\operatorname{arcosh}" to "arcosh",
        "\\operatorname{artanh}" to "artanh",
        "\\operatorname{lcm}" to "lcm",
        "\\operatorname{mod}" to "mod",
        "\\operatorname{ord}" to "ord",
        "\\operatorname{tr}" to "tr",
        "\\operatorname{rank}" to "rank",
        "\\operatorname{nullity}" to "nullity",
        "\\operatorname{im}" to "im",
        "\\operatorname{span}" to "span",
        "\\operatorname{diag}" to "diag",
        "\\operatorname{proj}" to "proj",
        "\\operatorname{Var}" to "Var",
        "\\operatorname{Cov}" to "Cov",
        "\\operatorname{Corr}" to "Corr",
        "\\operatorname{std}" to "std",
        "\\operatorname{median}" to "median",
        "\\operatorname{mode}" to "mode",
        "\\operatorname{int}" to "int",
        "\\operatorname{cl}" to "cl",
        "\\operatorname{Log}" to "Log",
        "\\operatorname{Res}" to "Res",
        "\\operatorname{Aut}" to "Aut",
        "\\operatorname{Hom}" to "Hom",
        "\\operatorname{Gal}" to "Gal",
        "\\operatorname{erf}" to "erf",
        "\\operatorname{erfc}" to "erfc",
        "\\operatorname{Li}" to "Li",
        "\\operatorname{P}" to "P",
        "\\operatorname{C}" to "C",
        "\\operatorname{B}" to "B",
        "\\arg\\min" to "arg min",
        "\\arg\\max" to "arg max",
        "\\limsup" to "lim sup",
        "\\liminf" to "lim inf",
        "\\arcsin" to "arcsin",
        "\\arccos" to "arccos",
        "\\arctan" to "arctan",
        "\\sinh" to "sinh",
        "\\cosh" to "cosh",
        "\\tanh" to "tanh",
        "\\coth" to "coth",
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tan",
        "\\sec" to "sec",
        "\\csc" to "csc",
        "\\cot" to "cot",
        "\\ln" to "ln",
        "\\log" to "log",
        "\\exp" to "exp",
        "\\det" to "det",
        "\\dim" to "dim",
        "\\ker" to "ker",
        "\\gcd" to "gcd",
        "\\sup" to "sup",
        "\\inf" to "inf",
        "\\max" to "max",
        "\\min" to "min",
        "\\lim" to "lim",
        "\\Re" to "ℜ",
        "\\Im" to "ℑ",
        "\\arg" to "arg",
        "\\iiint" to "∭",
        "\\iint" to "∬",
        "\\oint" to "∮",
        "\\int" to "∫",
        "\\bigcup" to "⋃",
        "\\bigcap" to "⋂",
        "\\bigoplus" to "⨁",
        "\\bigotimes" to "⨂",
        "\\coprod" to "∐",
        "\\prod" to "∏",
        "\\sum" to "∑",
        "\\hookrightarrow" to "↪",
        "\\twoheadrightarrow" to "↠",
        "\\leftrightarrow" to "↔",
        "\\rightleftharpoons" to "⇌",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\implies" to "⇒",
        "\\iff" to "⇔",
        "\\mapsto" to "↦",
        "\\to" to "→",
        "\\uparrow" to "↑",
        "\\downarrow" to "↓",
        "\\nearrow" to "↗",
        "\\searrow" to "↘",
        "\\trianglelefteq" to "⊴",
        "\\subseteq" to "⊆",
        "\\supseteq" to "⊇",
        "\\subset" to "⊂",
        "\\supset" to "⊃",
        "\\notin" to "∉",
        "\\in" to "∈",
        "\\ni" to "∋",
        "\\nexists" to "∄",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\therefore" to "∴",
        "\\because" to "∵",
        "\\vdash" to "⊢",
        "\\models" to "⊨",
        "\\measuredangle" to "∡",
        "\\angle" to "∠",
        "\\parallel" to "∥",
        "\\perp" to "⊥",
        "\\cong" to "≅",
        "\\simeq" to "≃",
        "\\equiv" to "≡",
        "\\approx" to "≈",
        "\\neq" to "≠",
        "\\propto" to "∝",
        "\\sim" to "∼",
        "\\le" to "≤",
        "\\leq" to "≤",
        "\\ge" to "≥",
        "\\geq" to "≥",
        "\\ll" to "≪",
        "\\gg" to "≫",
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\oplus" to "⊕",
        "\\ominus" to "⊖",
        "\\otimes" to "⊗",
        "\\rtimes" to "⋊",
        "\\setminus" to "∖",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\land" to "∧",
        "\\lor" to "∨",
        "\\neg" to "¬",
        "\\bmod" to " mod ",
        "\\nmid" to "∤",
        "\\mid" to "∣",
        "\\nabla" to "∇",
        "\\partial" to "∂",
        "\\hbar" to "ℏ",
        "\\infty" to "∞",
        "\\varnothing" to "∅",
        "\\emptyset" to "∅",
        "\\complement" to "∁",
        "\\varepsilon" to "ε",
        "\\epsilon" to "ε",
        "\\varphi" to "φ",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\iota" to "ι",
        "\\kappa" to "κ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\omicron" to "ο",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\upsilon" to "υ",
        "\\phi" to "φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Xi" to "Ξ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Upsilon" to "Υ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
        "\\ldots" to "…",
        "\\cdots" to "⋯",
        "\\vdots" to "⋮",
        "\\ddots" to "⋱",
        "\\dots" to "…",
        "\\square" to "□",
        "\\Box" to "□",
        "\\Diamond" to "◇",
        "\\triangle" to "△",
        "\\odot" to "⊙",
        "\\checkmark" to "✓",
        "\\dagger" to "†",
        "\\ast" to "∗",
        "\\star" to "⋆",
        "\\bullet" to "•",
        "\\circ" to "∘",
        "\\top" to "⊤",
        "\\bot" to "⊥",
        "\\lfloor" to "⌊",
        "\\rfloor" to "⌋",
        "\\lceil" to "⌈",
        "\\rceil" to "⌉",
        "\\langle" to "⟨",
        "\\rangle" to "⟩",
        "\\left\\|" to "‖",
        "\\right\\|" to "‖",
        "\\left|" to "|",
        "\\right|" to "|",
        "\\left(" to "(",
        "\\right)" to ")",
        "\\{" to "{",
        "\\}" to "}",
        "\\|" to "‖",
        "\\#" to "#",
        "\\&" to "&",
        "\\%" to "%",
        "\\," to " "
    )

    fun extractBracedGroup(s: String, openBraceIdx: Int): Pair<String, Int>? {
        if (openBraceIdx >= s.length || s[openBraceIdx] != '{') return null
        var depth = 0
        for (i in openBraceIdx until s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return s.substring(openBraceIdx + 1, i) to (i + 1)
                    }
                }
            }
        }
        return s.substring(openBraceIdx + 1) to s.length
    }

    /**
     * Converts raw LaTeX into a clean, readable mathematical string while preserving
     * superscript/subscript markers for AnnotatedString rendering.
     */
    fun simplifySymbols(raw: String): String {
        var s = raw.trim()
        // Handle accents like \hat{u}, \bar{u}, \vec{u}, \sqrt[n]{x}, \sqrt{x}
        s = replaceCommandWithOneArg(s, "\\sqrt") { "√($it)" }
        s = replaceCommandWithOneArg(s, "\\hat") { "${it}̂" }
        s = replaceCommandWithOneArg(s, "\\bar") { "${it}̄" }
        s = replaceCommandWithOneArg(s, "\\vec") { "${it}⃗" }
        s = replaceCommandWithOneArg(s, "\\overline") { "${it}̅" }
        for ((k, v) in SYMBOL_MAP) {
            s = s.replace(k, v)
        }
        return s
    }

    private fun replaceCommandWithOneArg(input: String, cmd: String, transform: (String) -> String): String {
        var s = input
        while (true) {
            val idx = s.indexOf("$cmd{")
            if (idx == -1) break
            val group = extractBracedGroup(s, idx + cmd.length) ?: break
            val inner = simplifySymbols(group.first)
            s = s.substring(0, idx) + transform(inner) + s.substring(group.second)
        }
        return s
    }

    fun toMathML(tex: String): String {
        val readable = simplifySymbols(tex)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return "<math xmlns=\"http://www.w3.org/1998/Math/MathML\" display=\"block\">\n  <mrow><mtext>$readable</mtext></mrow>\n</math>"
    }
}

/**
 * Splits a top-level LaTeX string into visual segments (plain math text vs top-level \frac{num}{den} or \binom{n}{k})
 * so fractions and binomials render as true 2D vertical stacks!
 */
sealed class MathSegment {
    data class InlineText(val tex: String) : MathSegment()
    data class StackedFraction(val numerator: String, val denominator: String, val isBinom: Boolean = false) : MathSegment()
}

private fun splitTopLevelSegments(tex: String): List<MathSegment> {
    val out = ArrayList<MathSegment>()
    val sb = StringBuilder()
    var i = 0
    while (i < tex.length) {
        val isFrac = tex.startsWith("\\frac{", i)
        val isBinom = tex.startsWith("\\binom{", i)
        if (isFrac || isBinom) {
            val cmdLen = if (isFrac) 5 else 6
            val firstGroup = MathFormatter.extractBracedGroup(tex, i + cmdLen)
            if (firstGroup != null) {
                val secondGroup = MathFormatter.extractBracedGroup(tex, firstGroup.second)
                if (secondGroup != null) {
                    if (sb.isNotEmpty()) {
                        out.add(MathSegment.InlineText(sb.toString()))
                        sb.clear()
                    }
                    out.add(MathSegment.StackedFraction(firstGroup.first, secondGroup.first, isBinom))
                    i = secondGroup.second
                    continue
                }
            }
        }
        sb.append(tex[i])
        i++
    }
    if (sb.isNotEmpty()) {
        out.add(MathSegment.InlineText(sb.toString()))
    }
    return out
}

@Composable
fun MathText(
    tex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = AuraText,
    maxLines: Int = 1
) {
    val segments = remember(tex) { splitTopLevelSegments(tex) }

    if (segments.size == 1 && segments[0] is MathSegment.InlineText) {
        InlineMathSpan(
            rawTex = (segments[0] as MathSegment.InlineText).tex,
            fontSize = fontSize,
            color = color,
            maxLines = maxLines,
            modifier = modifier
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = modifier
        ) {
            for (seg in segments) {
                when (seg) {
                    is MathSegment.InlineText -> {
                        InlineMathSpan(
                            rawTex = seg.tex,
                            fontSize = fontSize,
                            color = color,
                            maxLines = 1
                        )
                    }
                    is MathSegment.StackedFraction -> {
                        val subSize = (fontSize.value * 0.78f).sp
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (seg.isBinom) {
                                Text("(", fontSize = (fontSize.value * 1.15f).sp, color = color, fontFamily = AuraDisplayFontFamily)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                InlineMathSpan(rawTex = seg.numerator, fontSize = subSize, color = color, maxLines = 1)
                                if (!seg.isBinom) {
                                    Box(
                                        modifier = Modifier
                                            .widthIn(min = 16.dp, max = 96.dp)
                                            .fillMaxWidth()
                                            .height(1.2.dp)
                                            .background(color.copy(alpha = 0.75f))
                                    )
                                }
                                InlineMathSpan(rawTex = seg.denominator, fontSize = subSize, color = color, maxLines = 1)
                            }
                            if (seg.isBinom) {
                                Text(")", fontSize = (fontSize.value * 1.15f).sp, color = color, fontFamily = AuraDisplayFontFamily)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineMathSpan(
    rawTex: String,
    fontSize: TextUnit,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    val annotated = remember(rawTex, fontSize, color) {
        val cleaned = MathFormatter.simplifySymbols(rawTex)
        buildAnnotatedString {
            var i = 0
            while (i < cleaned.length) {
                val ch = cleaned[i]
                if ((ch == '^' || ch == '_') && i + 1 < cleaned.length) {
                    val isSuper = ch == '^'
                    val shift = if (isSuper) BaselineShift(0.34f) else BaselineShift(-0.24f)
                    val scaleSize = (fontSize.value * 0.72f).sp
                    if (cleaned[i + 1] == '{') {
                        val grp = MathFormatter.extractBracedGroup(cleaned, i + 1)
                        if (grp != null) {
                            val subText = grp.first.replace("{", "").replace("}", "")
                            withStyle(SpanStyle(baselineShift = shift, fontSize = scaleSize, color = color)) {
                                append(subText)
                            }
                            i = grp.second
                            continue
                        }
                    } else {
                        val nextChar = cleaned[i + 1].toString()
                        withStyle(SpanStyle(baselineShift = shift, fontSize = scaleSize, color = color)) {
                            append(nextChar)
                        }
                        i += 2
                        continue
                    }
                }
                if (ch == '{' || ch == '}') {
                    i++
                    continue
                }
                if (ch.isLetter() && ch.code < 128) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, color = color)) {
                        append(ch)
                    }
                } else {
                    withStyle(SpanStyle(color = color)) {
                        append(ch)
                    }
                }
                i++
            }
        }
    }

    Text(
        text = annotated,
        fontFamily = AuraDisplayFontFamily,
        fontSize = fontSize,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun MathBlock(
    tex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = AuraText
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        MathText(tex = tex, fontSize = fontSize, color = color, maxLines = 1)
    }
}
