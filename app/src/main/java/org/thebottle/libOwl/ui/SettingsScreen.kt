package org.thebottle.libOwl.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.thebottle.libOwl.data.AccentColor
import org.thebottle.libOwl.data.AppSettings
import org.thebottle.libOwl.data.CurrencySymbol
import org.thebottle.libOwl.data.LoanPrefs
import org.thebottle.libOwl.data.LoanReminders
import org.thebottle.libOwl.data.folderDisplayPath
import java.text.DateFormat
import java.util.Date

/** Current app settings, provided at the top of the UI so any screen can read them. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    backup: BackupViewModel,
    onBack: () -> Unit,
    onCurrencyChange: (CurrencySymbol) -> Unit,
    onAccentChange: (AccentColor) -> Unit,
    onLoanPrefsChange: (LoanPrefs) -> Unit
) {
    val settings = LocalAppSettings.current
    var showCurrency by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val loans = settings.loans
    var showLoanLength by remember { mutableStateOf(false) }
    var showRemindWhen by remember { mutableStateOf(false) }
    var notifyDeniedMessage by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onLoanPrefsChange(loans.copy(remindersEnabled = true))
        if (!granted) notifyDeniedMessage = true
    }

    LaunchedEffect(notifyDeniedMessage) {
        if (notifyDeniedMessage) {
            snackbar.showSnackbar("Notifications are blocked for Library Owl. Allow them in the phone's app settings to get reminders.")
            notifyDeniedMessage = false
        }
    }

    LaunchedEffect(backup.message) {
        backup.message?.let {
            snackbar.showSnackbar(it)
            backup.messageShown()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) backup.chooseFolder(uri)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SettingsSection("Display")
            ListItem(
                headlineContent = { Text("Currency") },
                supportingContent = { Text(settings.currency.summary) },
                modifier = Modifier.clickable { showCurrency = true }
            )
            ListItem(
                headlineContent = { Text("Accent color") },
                supportingContent = { Text(settings.accent.label) },
                trailingContent = {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(settings.accent.swatch)))
                },
                modifier = Modifier.clickable { showAccent = true }
            )

            SettingsSection("Loans")
            ListItem(
                headlineContent = { Text("Default loan length") },
                supportingContent = { Text(loanLengthLabel(loans.defaultLoanDays)) },
                modifier = Modifier.clickable { showLoanLength = true }
            )
            ListItem(
                headlineContent = { Text("Due date reminders") },
                supportingContent = { Text("A daily notification around 9 am for loans that are due soon or overdue") },
                trailingContent = {
                    Switch(
                        checked = loans.remindersEnabled,
                        onCheckedChange = { on ->
                            if (!on) {
                                onLoanPrefsChange(loans.copy(remindersEnabled = false))
                            } else if (Build.VERSION.SDK_INT >= 33 && !LoanReminders.canNotify(context)) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onLoanPrefsChange(loans.copy(remindersEnabled = true))
                                if (!LoanReminders.canNotify(context)) notifyDeniedMessage = true
                            }
                        }
                    )
                }
            )
            if (loans.remindersEnabled) {
                ListItem(
                    headlineContent = { Text("Remind me") },
                    supportingContent = { Text(remindWhenLabel(loans.remindDaysBefore) + ". Overdue loans are always included.") },
                    modifier = Modifier.clickable { showRemindWhen = true }
                )
                ListItem(
                    headlineContent = { Text("Check for due loans now") },
                    supportingContent = { Text("Shows a notification if anything is due soon or overdue") },
                    modifier = Modifier.clickable {
                        if (LoanReminders.canNotify(context)) LoanReminders.checkNow(context)
                        else notifyDeniedMessage = true
                    }
                )
            }

            SettingsSection("Backup")
            ListItem(
                headlineContent = { Text("Backup location") },
                supportingContent = {
                    Text(
                        settings.backupFolder?.let { uri ->
                            folderDisplayPath(uri) ?: settings.backupFolderName ?: "Folder selected"
                        } ?: "Not set"
                    )
                },
                modifier = Modifier.clickable { folderPicker.launch(null) }
            )
            ListItem(
                headlineContent = { Text("Back up now") },
                supportingContent = {
                    Text(
                        settings.lastBackup?.let {
                            "Last backup: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                        } ?: if (settings.backupFolder == null) "Choose a backup location first" else "Never backed up"
                    )
                },
                modifier = Modifier.clickable { backup.backupNow() }
            )
            ListItem(
                headlineContent = { Text("Restore from backup") },
                supportingContent = { Text("Replace this library with a backup file") },
                modifier = Modifier.clickable { restorePicker.launch(arrayOf("*/*")) }
            )
        }
    }

    if (showAccent) {
        AlertDialog(
            onDismissRequest = { showAccent = false },
            title = { Text("Accent color") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AccentColor.entries.forEach { option ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(64.dp).clickable { onAccentChange(option); showAccent = false }
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(Color(option.swatch)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (option == settings.accent) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
                                }
                            }
                            Text(option.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccent = false }) { Text("Cancel") } }
        )
    }

    if (showCurrency) {
        AlertDialog(
            onDismissRequest = { showCurrency = false },
            title = { Text("Currency") },
            text = {
                LazyColumn {
                    items(CurrencySymbol.entries.toList(), key = { it.name }) { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onCurrencyChange(option); showCurrency = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == settings.currency, onClick = null)
                            Text(option.summary, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCurrency = false }) { Text("Cancel") } }
        )
    }

    if (showLoanLength) {
        ChoiceDialog(
            title = "Default loan length",
            options = listOf(0, 7, 14, 21, 30).map { it to loanLengthLabel(it) },
            selected = loans.defaultLoanDays,
            onPick = { onLoanPrefsChange(loans.copy(defaultLoanDays = it)); showLoanLength = false },
            onDismiss = { showLoanLength = false }
        )
    }
    if (showRemindWhen) {
        ChoiceDialog(
            title = "Remind me",
            options = listOf(0, 1, 2, 3, 7).map { it to remindWhenLabel(it) },
            selected = loans.remindDaysBefore,
            onPick = { onLoanPrefsChange(loans.copy(remindDaysBefore = it)); showRemindWhen = false },
            onDismiss = { showRemindWhen = false }
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Your current books, covers and settings on this phone will be replaced by the backup. " +
                        "This can't be undone, and the app will restart."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingRestore = null; backup.restore(uri) }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }

    backup.busy?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(text, modifier = Modifier.padding(start = 16.dp))
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

private fun loanLengthLabel(days: Int) = when (days) {
    0 -> "No due date"
    7 -> "1 week"
    14 -> "2 weeks"
    21 -> "3 weeks"
    30 -> "1 month"
    else -> "$days days"
}

private fun remindWhenLabel(days: Int) = when (days) {
    0 -> "On the due date"
    1 -> "1 day before"
    7 -> "1 week before"
    else -> "$days days before"
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
