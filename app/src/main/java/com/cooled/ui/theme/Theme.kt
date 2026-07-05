package com.cooled.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A dark, LED-matrix-inspired palette: near-black surfaces so the app chrome
 * recedes, a warm amber accent standing in for lit LED pixels, and a cool
 * violet secondary for less-prominent affordances. Deliberately dark-only
 * (no light scheme) - this is a device-control app people mostly use in the
 * same low-light rooms they'd have an LED sign switched on in.
 */
private val CooledDarkColorScheme = darkColorScheme(
    background = Color(0xFF0B0B0E),
    onBackground = Color(0xFFE7E6EA),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE7E6EA),
    surfaceVariant = Color(0xFF1D1D24),
    onSurfaceVariant = Color(0xFFAFAEB8),
    surfaceContainer = Color(0xFF17171D),
    surfaceContainerHigh = Color(0xFF1E1E26),
    surfaceContainerHighest = Color(0xFF26262F),
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF3A2400),
    primaryContainer = Color(0xFF54390A),
    onPrimaryContainer = Color(0xFFFFDCB0),
    secondary = Color(0xFFB39DDB),
    onSecondary = Color(0xFF2E1A5C),
    secondaryContainer = Color(0xFF41307A),
    onSecondaryContainer = Color(0xFFE5DBFF),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF00382F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3E3E47),
    outlineVariant = Color(0xFF2A2A32)
)

private val CooledTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.3.sp)
    )
}

private val CooledShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun CooledTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CooledDarkColorScheme, typography = CooledTypography, shapes = CooledShapes, content = content)
}

/** A muted, always-legible label style for secondary metadata lines (addresses, byte dumps, timestamps). */
val MaterialTheme.monoCaption: TextStyle
    @Composable get() = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = 12.sp,
        color = colorScheme.onSurfaceVariant
    )
