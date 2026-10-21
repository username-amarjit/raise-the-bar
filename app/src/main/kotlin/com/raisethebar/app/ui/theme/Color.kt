package com.raisethebar.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Design tokens from section 11 (plan.html). */
@Immutable
data class RtbColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val line: Color,
    val push: Color,
    val pull: Color,
    val legs: Color,
    val gold: Color,
    val plateGold: Color,
    val rest: Color,
    val pushSoft: Color,
    val pullSoft: Color,
    val legsSoft: Color,
    val goldSoft: Color,
    val restSoft: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

val LightRtbColors =
    RtbColors(
        bg = Color(0xFFE6E8EB),
        surface = Color(0xFFFBFBFA),
        surface2 = Color(0xFFEEF0F2),
        ink = Color(0xFF1C1F24),
        ink2 = Color(0xFF3C424B),
        muted = Color(0xFF5C636E),
        line = Color(0xFFCDD2D8),
        push = Color(0xFFC42F27),
        pull = Color(0xFF1E4FB5),
        legs = Color(0xFF1C7745),
        gold = Color(0xFF946600),
        plateGold = Color(0xFFE3A500),
        rest = Color(0xFF7D848E),
        pushSoft = Color(0xFFF5DDDA),
        pullSoft = Color(0xFFDCE4F5),
        legsSoft = Color(0xFFD5EADD),
        goldSoft = Color(0xFFF6EBCB),
        restSoft = Color(0xFFE3E6EA),
        onAccent = Color(0xFFFFFFFF),
        isDark = false,
    )

val DarkRtbColors =
    RtbColors(
        bg = Color(0xFF1A1D21),
        surface = Color(0xFF23272D),
        surface2 = Color(0xFF2C3138),
        ink = Color(0xFFEEF0F2),
        ink2 = Color(0xFFC8CDD4),
        muted = Color(0xFF9AA2AD),
        line = Color(0xFF363C44),
        push = Color(0xFFFF6D5E),
        pull = Color(0xFF7EA0FF),
        legs = Color(0xFF45C487),
        gold = Color(0xFFF0C04A),
        plateGold = Color(0xFFF0C04A),
        rest = Color(0xFF8A929C),
        pushSoft = Color(0xFF3B2220),
        pullSoft = Color(0xFF1E2944),
        legsSoft = Color(0xFF173123),
        goldSoft = Color(0xFF3A3117),
        restSoft = Color(0xFF2C3138),
        onAccent = Color(0xFF14171A),
        isDark = true,
    )

val LocalRtbColors = staticCompositionLocalOf { LightRtbColors }

/** Accent families used by cards, tags and chips: push red, pull blue, legs green, gold for modes, grey for rest. */
enum class Accent { PUSH, PULL, LEGS, GOLD, REST }

fun RtbColors.accent(a: Accent): Color =
    when (a) {
        Accent.PUSH -> push
        Accent.PULL -> pull
        Accent.LEGS -> legs
        Accent.GOLD -> plateGold
        Accent.REST -> rest
    }

fun RtbColors.accentSoft(a: Accent): Color =
    when (a) {
        Accent.PUSH -> pushSoft
        Accent.PULL -> pullSoft
        Accent.LEGS -> legsSoft
        Accent.GOLD -> goldSoft
        Accent.REST -> restSoft
    }
