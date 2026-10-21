package com.raisethebar.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.raisethebar.app.R

/** Barlow Condensed (600, 700, 800) for headings and big numbers. */
val BarlowCondensed =
    FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
        Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    )

/** Barlow (400 to 700) for text. */
val Barlow =
    FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )

private fun display(size: Int, line: Int = size, weight: FontWeight = FontWeight.ExtraBold) =
    TextStyle(fontFamily = BarlowCondensed, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Barlow, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val RtbTypography =
    Typography(
        displayLarge = display(56, 52),
        displayMedium = display(44, 42),
        displaySmall = display(36, 36),
        headlineLarge = display(32, 32),
        headlineMedium = display(28, 28),
        headlineSmall = display(24, 26),
        titleLarge = display(22, 24, FontWeight.Bold),
        titleMedium = body(17, 22, FontWeight.SemiBold),
        titleSmall = body(15, 20, FontWeight.SemiBold),
        bodyLarge = body(17, 25),
        bodyMedium = body(15, 21),
        bodySmall = body(13, 18),
        labelLarge = display(18, 22),
        labelMedium = body(13, 16, FontWeight.Bold),
        labelSmall = body(12, 15, FontWeight.Bold),
    )
