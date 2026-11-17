package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PulseMixDarkColorScheme = darkColorScheme(
    primary = DeckACyan,
    onPrimary = VoidBlack,
    primaryContainer = DeckACyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = DeckBAmber,
    onSecondary = VoidBlack,
    secondaryContainer = DeckBAmberDim,
    onSecondaryContainer = TextPrimary,
    tertiary = VuGreen,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = ConsoleCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = DeckSurface,
    onSurfaceVariant = TextSecondary,
    outline = PanelBorder,
    error = VuRed,
    onError = TextPrimary
)

private val PulseMixShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp)
)

@Composable
fun PulseMixTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PulseMixDarkColorScheme,
        typography = PulseMixTypography,
        shapes = PulseMixShapes,
        content = content
    )
}
