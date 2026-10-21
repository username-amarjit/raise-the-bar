package com.raisethebar.app.ui.settings

import android.content.ActivityNotFoundException
import android.database.SQLException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.raisethebar.app.backup.BackupFiles
import com.raisethebar.app.data.Backup
import com.raisethebar.app.data.BackupFile
import com.raisethebar.app.data.BackupRejected
import com.raisethebar.app.data.BackupSummary
import com.raisethebar.app.data.Dates
import com.raisethebar.app.data.exportBackup
import com.raisethebar.app.data.importBackup
import com.raisethebar.app.data.summary
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.engine.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Settings → Export: every table as one JSON file with its schema version, shared through the share sheet. */
@Composable
fun ExportRow() {
    val context = LocalContext.current
    val repo = LocalRepo.current
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    ListRow(
        name = "Export your data",
        meta = if (busy) "Preparing the file…" else "Everything, as one file, through the share sheet",
        onClick = {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        val text = repo.exportBackup()
                        val file = withContext(Dispatchers.IO) { BackupFiles.write(context, text, repo.time.today()) }
                        context.startActivity(BackupFiles.shareIntent(context, file))
                    } catch (e: IOException) {
                        actions.message("The backup file couldn't be written. Is the phone out of space?")
                    } catch (e: ActivityNotFoundException) {
                        actions.message("No app on this phone can receive the file.")
                    } finally {
                        busy = false
                    }
                }
            }
        },
    )
}

/**
 * Import: pick a file, check it (version, structure and value ranges), show what it holds, and replace all data only
 * after the user confirms. [trigger] draws the button and receives the function that opens the file picker.
 */
@Composable
fun BackupImport(onImported: () -> Unit = {}, trigger: @Composable (open: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val repo = LocalRepo.current
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<BackupFile?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    try {
                        pending = withContext(Dispatchers.IO) { Backup.decode(BackupFiles.read(context, uri)) }
                    } catch (e: BackupRejected) {
                        actions.message(e.message ?: Backup.DAMAGED)
                    } catch (e: IOException) {
                        actions.message("The file couldn't be read. Nothing was changed.")
                    } catch (e: SecurityException) {
                        actions.message("The file couldn't be opened. Nothing was changed.")
                    }
                }
            }
        }
    trigger { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    pending?.let { file ->
        ImportConfirm(
            file.summary(),
            onConfirm = {
                pending = null
                scope.launch {
                    try {
                        repo.importBackup(file)
                        actions.message("Backup imported.")
                        onImported()
                    } catch (e: SQLException) {
                        actions.message("The backup couldn't be imported.")
                    }
                }
            },
            onCancel = { pending = null },
        )
    }
}

@Composable
fun ImportConfirm(summary: BackupSummary, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Replace everything with this backup?") },
        text = { Text(summary.describe()) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Replace my data") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

internal fun BackupSummary.describe(): String {
    val day = exportedAt.toLocalDate()
    return "Exported ${Dates.day(day)} ${day.year} at ${Time.hhmm(exportedAt.toLocalTime())}. It holds " +
        "${count(workouts, "workout")} (${count(sets, "set")}), ${count(foodEntries, "food entry", "food entries")}, " +
        "${count(weighIns, "morning weight")}, ${count(injuries, "injury", "injuries")} and " +
        "${count(daysOffAndTrips, "day off or trip", "days off or trips")}.\n\n" +
        "Importing replaces everything on this phone. This can't be undone."
}

private fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"
