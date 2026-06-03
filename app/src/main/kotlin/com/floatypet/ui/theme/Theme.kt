package com.floatypet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 暖色基调，固定亮色方案（MVP 不做深色，见 design-spec）。
private val WarmColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = ChipBg,
    onPrimaryContainer = PrimaryDeep,
    secondary = Mint,
    background = WarmBg,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    onSurfaceVariant = Ink2,
    outline = Line,
)

@Composable
fun FloatyPetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WarmColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
