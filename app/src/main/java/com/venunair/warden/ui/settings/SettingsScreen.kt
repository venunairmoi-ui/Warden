package com.venunair.warden.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venunair.warden.BuildConfig
import com.venunair.warden.R
import com.venunair.warden.autodetect.hasMediaImageAccess
import com.venunair.warden.autodetect.mediaImagesPermission
import com.venunair.warden.data.AVAILABLE_REMINDER_OFFSETS
import com.venunair.warden.data.DigestFrequency
import com.venunair.warden.data.Region
import com.venunair.warden.data.SettingsRepository
import com.venunair.warden.data.ThemeMode
import java.text.DateFormat
import java.util.Date

private class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    onOpenArchivedItems: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(repository))
    val preferences by viewModel.preferences.collectAsState()
    val context = LocalContext.current

    var showAutoDetectExplanation by rememberSaveable { mutableStateOf(false) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Don't trust the raw callback boolean alone: on API 34+, choosing
        // "Select photos" in the system dialog denies READ_MEDIA_IMAGES
        // itself while silently granting READ_MEDIA_VISUAL_USER_SELECTED
        // instead — a real "yes, for these photos" that the plain boolean
        // would report as a flat denial. Re-check both permissions via
        // hasMediaImageAccess so a partial grant still turns the toggle on;
        // an actual "Don't allow" still leaves it off.
        viewModel.setAutoDetectEnabled(hasMediaImageAccess(context))
    }

    Scaffold(
        topBar = {
            // UI redesign pass, 2026-08-25: neutral chrome, same convention
            // as AddEditItemScreen/ItemDetailScreen — Settings is a
            // transactional screen, not the brand-colored Dashboard tab.
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_reminder_defaults)) {
                    Text(
                        "Pre-selected when adding a new item — change per item any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AVAILABLE_REMINDER_OFFSETS.forEach { offset ->
                            val checked = offset in preferences.defaultReminderOffsets
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    val updated = if (checked) {
                                        preferences.defaultReminderOffsets - offset
                                    } else {
                                        preferences.defaultReminderOffsets + offset
                                    }
                                    viewModel.setDefaultReminderOffsets(updated.sortedDescending())
                                },
                                // Pill shape + uppercase label-small — same
                                // reminder-chip treatment as AddEditItemScreen's
                                // identical chips (this is literally the same
                                // feature, just previewed here as a default).
                                shape = CircleShape,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = checked,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                label = {
                                    Text(
                                        (if (offset == 1) "1 day" else "$offset days").uppercase(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_digest_frequency)) {
                    DigestFrequency.entries.forEach { frequency ->
                        RadioRow(
                            label = frequency.displayName,
                            selected = preferences.digestFrequency == frequency,
                            onClick = { viewModel.setDigestFrequency(frequency) }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_auto_detect_section)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_auto_detect_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.settings_auto_detect_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = preferences.autoDetectEnabled,
                            onCheckedChange = { turningOn ->
                                if (!turningOn) {
                                    viewModel.setAutoDetectEnabled(false)
                                } else {
                                    val alreadyGranted = hasMediaImageAccess(context)
                                    if (alreadyGranted) {
                                        viewModel.setAutoDetectEnabled(true)
                                    } else {
                                        showAutoDetectExplanation = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_theme)) {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            label = mode.displayName,
                            selected = preferences.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) }
                        )
                    }
                }
            }

            item {
                // International-formatting pass, 2026-09-01: first setting
                // for non-India testers. INDIA stays first in the list and
                // is the default (see Region.kt) -- this section only ever
                // changes currency/date FORMATTING, never app language, and
                // is called out here so it doesn't read as a translation
                // toggle it isn't.
                SettingsSection(title = stringResource(R.string.settings_region)) {
                    Region.entries.forEach { region ->
                        RadioRow(
                            label = region.displayName,
                            selected = preferences.region == region,
                            onClick = { viewModel.setRegion(region) }
                        )
                    }
                    Text(
                        stringResource(R.string.settings_region_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                // Feedback, 2026-08-26: a user who archives an item by
                // mistake and misses the 5-second "Undo" snackbar had no
                // way back at all -- this is that way back. See
                // ArchivedItemsScreen's own doc comment for the full story.
                SettingsSection(title = stringResource(R.string.settings_data)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenArchivedItems),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    stringResource(R.string.settings_archived_items),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    stringResource(R.string.settings_archived_items_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Phase 2 (privacy): where data lives, permission
                    // explanations, and "Delete all my data" -- see
                    // PrivacyScreen's own doc comment.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPrivacy),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PrivacyTip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Privacy", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "What's stored, what permissions are for, and how to delete it all",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_backup)) {
                    val lastBackupText = preferences.lastBackupAtMillis?.let {
                        DateFormat.getDateInstance().format(Date(it))
                    } ?: stringResource(R.string.settings_backup_never)
                    Text(
                        stringResource(R.string.settings_backup_last, lastBackupText),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.settings_backup_coming_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_about)) {
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.settings_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAutoDetectExplanation) {
        AlertDialog(
            onDismissRequest = { showAutoDetectExplanation = false },
            title = { Text(stringResource(R.string.settings_auto_detect_permission_title)) },
            text = {
                Text(stringResource(R.string.settings_auto_detect_permission_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    showAutoDetectExplanation = false
                    mediaPermissionLauncher.launch(mediaImagesPermission())
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoDetectExplanation = false }) { Text("Not now") }
            }
        )
    }
}

/**
 * Same bordered "fieldset" card anatomy as AddEditItemScreen's
 * `FormSectionCard` / ItemDetailScreen's `DetailSectionCard` (2026-08-25
 * redesign pass) — bordered `surfaceContainerLow`, `outlineVariant`
 * border, `shapes.large`, uppercase label-small title + divider. This is
 * the 3rd screen to reuse this exact anatomy; still a locally-copied
 * composable (Kotlin `private` can't cross files) rather than a shared
 * one, which would mean pulling it into a common UI module — worth doing
 * if a 4th screen needs it, not yet for 3.
 */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
