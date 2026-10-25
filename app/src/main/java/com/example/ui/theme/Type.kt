package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val AuraDisplayFontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.SemiBold)
)

val AuraMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val AuraSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = AuraDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AuraDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = AuraDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = AuraSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AuraSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AuraSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AuraMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AuraSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.6.sp
    )
)
