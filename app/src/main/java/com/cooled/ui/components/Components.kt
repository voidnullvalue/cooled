package com.cooled.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A titled, elevated group of related controls - the app's one repeated structural unit, replacing the old flat wall of Rows. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

/** A single row pairing a label with a value and an optional trailing control - the workhorse for most feature toggles/actions. */
@Composable
fun FeatureRow(
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** A toggle row - FeatureRow specialized for the common "label + switch" shape. */
@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, caption: String? = null, enabled: Boolean = true) {
    FeatureRow(label, modifier, caption, enabled) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A slider with its current value rendered as a trailing number, avoiding the old pattern of a separate WhiteText line above every slider. */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueText: (Float) -> String = { it.roundToInt().toString() }
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(valueText(value), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, enabled = enabled)
    }
}

/** A small rounded status pill, used for connection state / matrix size / capability badges. */
@Composable
fun StatChip(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val bg = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .background(bg, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/**
 * A live-ish visual stand-in for the physical LED matrix: a grid of rounded
 * dots sized from the connected device's reported columns/rows, lit in the
 * accent color when [on] and dimmed to near-invisible otherwise. This is
 * intentionally not a pixel-perfect renderer of actual program content (the
 * BLE protocol doesn't expose a live framebuffer readback) - it exists so
 * "the app feels like it's controlling a light" rather than reading as a
 * bare settings form.
 */
@Composable
fun MatrixPreview(columns: Int?, rows: Int?, on: Boolean, brightness: Int, modifier: Modifier = Modifier) {
    val effectiveColumns = (columns ?: 32).coerceIn(4, 64)
    val effectiveRows = (rows ?: 16).coerceIn(4, 32)
    val litColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF08080A))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(effectiveColumns.toFloat() / effectiveRows.toFloat())
                .padding(14.dp)
        ) {
            val cellW = size.width / effectiveColumns
            val cellH = size.height / effectiveRows
            val cell = min(cellW, cellH)
            val dotSize = cell * 0.72f
            val gridW = cell * effectiveColumns
            val gridH = cell * effectiveRows
            val offsetX = (size.width - gridW) / 2f
            val offsetY = (size.height - gridH) / 2f
            val alpha = if (on) (0.35f + 0.65f * (brightness.coerceIn(1, 100) / 100f)) else 0f
            for (row in 0 until effectiveRows) {
                for (col in 0 until effectiveColumns) {
                    val cx = offsetX + col * cell + cell / 2f
                    val cy = offsetY + row * cell + cell / 2f
                    val color = if (on) litColor.copy(alpha = max(0.12f, alpha)) else dimColor
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(cx - dotSize / 2f, cy - dotSize / 2f),
                        size = Size(dotSize, dotSize),
                        cornerRadius = CornerRadius(dotSize * 0.3f, dotSize * 0.3f)
                    )
                }
            }
        }
    }
}

/** Compact numeric input for values like hour/minute/mask fields, used instead of full-width TextFields for short numbers. */
@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) onChange(new) },
        label = { Text(label, fontFamily = FontFamily.Default) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start),
        modifier = modifier.size(width = 92.dp, height = 64.dp)
    )
}
