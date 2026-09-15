package com.venunair.warden.ui.privacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.venunair.warden.capture.AttachmentStorage
import com.venunair.warden.data.ItemRepository
import kotlinx.coroutines.launch

/**
 * Phase 2 (commercial-readiness plan, "Build commercial trust"): explains
 * where data lives, what each requested permission is for in plain
 * language, and offers the "Delete all my data" control the doc calls
 * for. Reachable from Settings, one level below it (same depth as
 * Archived items) -- deliberately not folded into Settings itself, since
 * this is read-heavy explanatory content plus one destructive action,
 * not a preference toggle.
 *
 * Updated 2026-09-15: Google Drive backup/restore shipped this session
 * (see backup/DriveBackupManager.kt), the app's first-ever network
 * feature -- "Where your data lives" below now reflects that it's
 * opt-in, not a claim that nothing ever leaves the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    repository: ItemRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PrivacySection(title = "Where your data lives") {
                    Text(
                        "Everything you add — item details, photos, and documents — is stored only on this device, in Wisma's private app storage, unless you turn on Google Drive backup yourself. There's no account required to use Wisma, and no analytics or ad tracking runs in this app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "If you back up to Google Drive: your data goes into a hidden folder in your own Google account, invisible in your normal Drive, and only Wisma can read it — Wisma's developer never sees it. You choose when a backup happens; nothing uploads on its own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "If you use Share or Export from another app to bring in a receipt, that file is copied into Wisma's own storage — the original stays wherever you shared it from, unaffected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                PrivacySection(title = "Permissions, explained") {
                    PermissionExplanation(
                        name = "Camera",
                        body = "Used only when you choose to photograph a receipt or warranty card. Wisma never opens the camera on its own."
                    )
                    PermissionExplanation(
                        name = "Notifications",
                        body = "Used to remind you before something expires or renews. You can turn individual reminders off per item, or disable notifications entirely in your phone's system settings."
                    )
                    PermissionExplanation(
                        name = "Photo access",
                        body = "Only requested if you turn on Auto-detect in Settings, and only to suggest photos that look like a receipt or warranty card — nothing is added to your items without your confirmation, and photo access is off by default."
                    )
                    PermissionExplanation(
                        name = "Internet",
                        body = "Only used when you choose to back up or restore from Google Drive in Settings. Wisma makes no other network connections."
                    )
                }
            }

            item {
                PrivacySection(title = "Your data") {
                    Text(
                        "You can permanently delete everything Wisma has stored — every item, photo, and document — right now, on this device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { showDeleteAllConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete all my data") }
                }
            }
        }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "Every item, photo, and document tracked in Wisma will be permanently removed from this device. This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        scope.launch {
                            val allAttachments = repository.getAllAttachmentsForDeletion()
                            repository.deleteAllItems()
                            allAttachments.forEach { attachment ->
                                AttachmentStorage.deleteBackingFile(context, attachment.localFileUri)
                                attachment.thumbnailUri?.let {
                                    AttachmentStorage.deleteBackingFile(context, it)
                                }
                            }
                            snackbarHostState.showSnackbar("All data deleted")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PermissionExplanation(name: String, body: String) {
    Column {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Same bordered "fieldset" card anatomy as Settings/AddEditItem/ItemDetail's
 *  section cards (2026-08-25 redesign pass) -- see SettingsScreen's
 *  SettingsSection doc comment for why this stays a locally-copied
 *  composable rather than a shared one. */
@Composable
private fun PrivacySection(title: String, content: @Composable () -> Unit) {
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
