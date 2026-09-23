package com.kesham.pocketbrain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kesham.pocketbrain.ui.theme.ClayAlert
import com.kesham.pocketbrain.ui.theme.ClayPrimary
import com.kesham.pocketbrain.ui.theme.ClaySurface
import com.kesham.pocketbrain.ui.theme.ClayTextPrimary
import com.kesham.pocketbrain.ui.theme.ClayTextSecondary

@Composable
internal fun ModelManagerRoute(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val viewModel: ModelManagerViewModel =
        viewModel(factory = ModelManagerViewModel.getFactory(context))

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val freeBytes by viewModel.freeBytes.collectAsStateWithLifecycle()

    ModelManagerScreen(
        rows = rows,
        wifiOnly = wifiOnly,
        freeBytes = freeBytes,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onDownload = viewModel::download,
        onCancel = viewModel::cancel,
        onDelete = viewModel::delete,
        onBack = onBack,
    )
}

@Composable
private fun ModelManagerScreen(
    rows: List<ModelRowState>,
    wifiOnly: Boolean,
    freeBytes: Long,
    onWifiOnlyChange: (Boolean) -> Unit,
    onDownload: (CatalogEntry) -> Unit,
    onCancel: (Model) -> Unit,
    onDelete: (Model) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaySurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = ClayTextSecondary)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ClayTextPrimary
            )
        }

        Text(
            text = "${formatBytes(freeBytes)} free · Gemma 3 1B is licence-gated and must be " +
                "side-loaded with push_model.sh",
            style = MaterialTheme.typography.labelSmall,
            color = ClayTextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        ClayBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = ClayPillShape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wi-Fi only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClayTextPrimary
                )
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ClayTextPrimary,
                        checkedTrackColor = ClayPrimary,
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(rows) { row ->
                ModelRow(
                    row = row,
                    onDownload = { onDownload(row.entry) },
                    onCancel = { onCancel(row.entry.model) },
                    onDelete = { onDelete(row.entry.model) },
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    row: ModelRowState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    ClayBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = ClayTextPrimary
                    )
                    Text(
                        text = "${row.entry.capability.label} · ${formatBytes(row.entry.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ClayTextSecondary
                    )
                }

                val status = when {
                    row.isVerifying -> "Verifying…"
                    row.progress != null -> "${(row.progress * 100).toInt()}%"
                    row.isDownloaded -> "Downloaded"
                    row.isPushed -> "Side-loaded"
                    else -> null
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = ClayTextSecondary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                val actionLabel = when {
                    row.isBusy -> "Cancel"
                    row.isDownloaded -> "Delete"
                    row.isPushed -> null
                    else -> "Download"
                }
                if (actionLabel != null) {
                    ClayBox(
                        modifier = Modifier.clickable {
                            when {
                                row.isBusy -> onCancel()
                                row.isDownloaded -> onDelete()
                                else -> onDownload()
                            }
                        },
                        shape = ClayPillShape,
                        color = if (row.isDownloaded && !row.isBusy) ClaySurface else ClayPrimary,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = ClayTextPrimary
                        )
                    }
                }
            }

            if (row.progress != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { row.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = ClayPrimary,
                    trackColor = ClayTextSecondary.copy(alpha = 0.2f),
                )
            }

            if (row.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = row.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = ClayAlert
                )
            }
        }
    }
}
