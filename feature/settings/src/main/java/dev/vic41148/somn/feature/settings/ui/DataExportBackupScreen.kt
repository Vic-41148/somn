package dev.vic41148.somn.feature.settings.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.feature.settings.SettingsViewModel

/**
 * Sub-screen behind Settings -> "Data Export & Backup". The export/backup/import group hit 10+
 * rows, so it moved out of the Settings list (which is now sections under light label headers)
 * and onto its own pushed screen with a back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportBackupScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(exportStatus) {
        exportStatus?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(importStatus) {
        importStatus?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Export & Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            SettingSection(title = "Backup") {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        // A document provider may refuse to persist the grant (some cloud providers
                        // only offer transient access). Failing here must not crash the screen or
                        // silently swallow the choice - surface an error so the user knows the
                        // picked directory will not survive a restart.
                        try {
                            context.contentResolver.takePersistableUriPermission(uri, flags)
                        } catch (e: SecurityException) {
                            viewModel.updateBackupUriError(
                                "The chosen directory grants only temporary access. Pick a directory from a provider that supports persistent access"
                            )
                            return@rememberLauncherForActivityResult
                        }
                        viewModel.updateBackupUri(uri.toString())
                    }
                }

                Text(
                    text = "Backup Directory",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { launcher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text(if (settings.backupUri != null) "  Change Directory" else "  Select Backup Directory")
                }
                settings.backupDirectoryError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (settings.backupUri != null) {
                    Text(
                        text = "Auto-backup runs on alarm dismiss",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = { viewModel.performManualBackup() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Backup Now")
                    }
                }
            }

            SettingSection(title = "Recovery Key") {
                Text(
                    text = if (settings.backupPassphraseSet) {
                        "Your recovery key encrypts the backups"
                    } else {
                        "You set no recovery key. Backups stay on this device only. Off-site sync " +
                            "stays off. An upload is not a backup when it dies with the phone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.backupPassphraseSet) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                var confirmReplaceKey by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        // Replacing a key orphans every backup written under the old one, so make the
                        // destructive case an explicit second step.
                        if (settings.backupPassphraseSet) confirmReplaceKey = true
                        else viewModel.generateRecoveryKey()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(if (settings.backupPassphraseSet) "Replace Recovery Key" else "Generate Recovery Key")
                }

                if (confirmReplaceKey) {
                    AlertDialog(
                        onDismissRequest = { confirmReplaceKey = false },
                        title = { Text("Replace recovery key?") },
                        text = {
                            Text(
                                "You can open the existing backups only with the current key. " +
                                    "Keep the key somewhere safe. Without the key you lose access to the backups."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmReplaceKey = false
                                viewModel.generateRecoveryKey()
                            }) { Text("Replace") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmReplaceKey = false }) { Text("Cancel") }
                        }
                    )
                }

                val newRecoveryKey by viewModel.newRecoveryKey.collectAsState()
                newRecoveryKey?.let { key ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissRecoveryKey() },
                        title = { Text("Save your recovery key") },
                        text = {
                            Column {
                                Text(
                                    "You see this key once. Without the key you cannot open an encrypted backup. " +
                                        "We cannot open it either."
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                SelectionContainer {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.dismissRecoveryKey() }) {
                                Text("I saved it")
                            }
                        }
                    )
                }
            }

            SettingSection(title = "Restore") {
                var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
                val restoreLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) pendingRestoreUri = uri }

                Text(
                    text = "Replace everything on this device with the contents of a backup file. " +
                        "The app re-arms the restored alarms automatically on the next app start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Button(
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Restore From Backup File")
                }

                pendingRestoreUri?.let { uri ->
                    var passphrase by remember(uri) { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { pendingRestoreUri = null },
                        title = { Text("Restore database?") },
                        text = {
                            Column {
                                Text(
                                    "This replaces all sleep data on this device. Enter your recovery " +
                                        "key if the backup is encrypted. Leave the field blank if the backup is not encrypted."
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it },
                                    label = { Text("Recovery key") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.restoreDatabase(uri, passphrase.ifBlank { null })
                                pendingRestoreUri = null
                            }) { Text("Restore") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
                        }
                    )
                }

                val restartRequired by viewModel.restartRequired.collectAsState()
                if (restartRequired) {
                    Text(
                        text = "Restore is complete. Close Somn fully. Reopen Somn to load the restored data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            SettingSection(title = "Export & Import") {
                Button(
                    onClick = { viewModel.exportData(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("  Export All Data (CSV)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.exportAllDataZip(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("  Export All Data (JSON + CSV, .zip)")
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Import from Sleep as Android",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Best effort import. Sleep as Android documents no official export format, " +
                        "so cycle and biological context, sleep stages, and audio events do not transfer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        viewModel.importSleepAsAndroidFile(context, uri)
                    }
                }
                Button(
                    onClick = { importLauncher.launch("text/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Select sleep-export.csv")
                }
            }
        }
    }
}