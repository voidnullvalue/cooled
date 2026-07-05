package com.cooled.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cooled.core.assets.OriginalLedAsset
import com.cooled.core.assets.OriginalLedAssetSummary
import com.cooled.core.protocol.CoolleduxProgramBytecode
import com.cooled.core.protocol.TransferState
import com.cooled.ui.components.NumberField
import com.cooled.ui.components.SectionCard

@Composable
fun DisplayScreen(
    uploadText: String,
    onUploadTextChange: (String) -> Unit,
    uploadSpeed: String,
    onUploadSpeedChange: (String) -> Unit,
    uploadEffect: String,
    onUploadEffectChange: (String) -> Unit,
    uploadFontSize: Int?,
    onUploadFontSizeChange: (Int?) -> Unit,
    onSendTextProgram: () -> Unit,
    assetSummary: OriginalLedAssetSummary,
    assetUploadPath: String,
    onAssetSelected: (OriginalLedAsset) -> Unit,
    onClearAsset: () -> Unit,
    onSendAssetProgram: () -> Unit,
    transferState: TransferState,
    onCancelTransfer: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TransferProgressCard(transferState, onCancelTransfer)
        }

        item {
            SectionCard(
                "Upload text",
                icon = Icons.Filled.TextFields,
                subtitle = "Effect 1 and 4-13 hold the text still, word-wrapped and centered on the panel. Every other effect number scrolls it across instead. Font size only applies to CoolLEDUX panels - Auto picks the largest size that fits your panel's row count."
            ) {
                OutlinedTextField(
                    value = uploadText,
                    onValueChange = onUploadTextChange,
                    label = { Text("Text to display") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(uploadSpeed, onUploadSpeedChange, "Speed (0-255)")
                    NumberField(uploadEffect, onUploadEffectChange, "Effect (0-255)")
                }
                Text("Font size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = uploadFontSize == null,
                        onClick = { onUploadFontSizeChange(null) },
                        label = { Text("Auto") }
                    )
                    CoolleduxProgramBytecode.supportedFontSizes.forEach { size ->
                        FilterChip(
                            selected = uploadFontSize == size,
                            onClick = { onUploadFontSizeChange(size) },
                            label = { Text("${size}px") }
                        )
                    }
                }
                Button(onClick = onSendTextProgram, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text("  Send to device")
                }
            }
        }

        item {
            SectionCard(
                "Original app assets",
                icon = Icons.Filled.Image,
                subtitle = "Icons, emoji and animations extracted from the original CoolLED app (${assetSummary.total} available)"
            ) {
                if (assetUploadPath.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Selected: ${assetUploadPath.substringAfterLast('/')}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onClearAsset) { Text("Clear") }
                    }
                }
                assetSummary.examples.take(8).forEach { asset ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = { onAssetSelected(asset) }) { Text(asset.kind) }
                        Text(asset.fileName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                Button(onClick = onSendAssetProgram, enabled = assetUploadPath.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text("  Send asset to device")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun TransferProgressCard(state: TransferState, onCancel: () -> Unit) {
    val (label, progress) = when (state) {
        is TransferState.Idle -> return
        is TransferState.AwaitingStartAck -> "Waiting for device to accept upload..." to null
        is TransferState.SendingChunk -> "Sending ${state.index + 1} of ${state.total}" to (state.index + 1).toFloat() / state.total.toFloat()
        is TransferState.Completed -> (if (state.skipped) "Nothing to upload" else "Upload complete") to 1f
        is TransferState.Failed -> "Upload failed: ${state.reason}" to null
        is TransferState.Cancelled -> "Upload cancelled" to null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (state is TransferState.SendingChunk || state is TransferState.AwaitingStartAck) {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.height(16.dp))
                        Text("  Cancel")
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else if (state is TransferState.AwaitingStartAck) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
