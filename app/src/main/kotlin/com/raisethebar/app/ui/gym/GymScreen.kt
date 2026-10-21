package com.raisethebar.app.ui.gym

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.Dates
import com.raisethebar.app.data.fixedPrompts
import com.raisethebar.app.data.markFixedAsked
import com.raisethebar.app.data.saveEquipment
import com.raisethebar.app.data.setEquipmentStatus
import com.raisethebar.app.ui.Actions
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SecondaryButton
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.components.Segmented
import com.raisethebar.app.ui.components.SubHeading
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.setup.WeightsEditor
import com.raisethebar.app.ui.setup.hasWeights
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.EquipmentStatus
import com.raisethebar.engine.Gym

fun statusLabel(status: String): String =
    when (status) {
        EquipmentStatus.BUSY -> "Busy today"
        EquipmentStatus.BROKEN -> "Broken"
        EquipmentStatus.GONE -> "Gone"
        else -> "Working"
    }

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Report", text))
}

/** My Gym (milestone 5): every item with today's status; tap one to change it, set its weights or report it. */
@Composable
fun GymScreen(onItem: (String) -> Unit) {
    val data = rememberLoad { Triple(gym().values.toList(), fixedPrompts(), time.today()) }
    ScreenFrame(title = "My Gym", onBack = null, caption = "Tap an item to change its status, weights or report it") {
        val (items, prompts, today) = data ?: return@ScreenFrame
        prompts.forEach { FixedPrompt(it) }
        val (gone, here) = items.partition { it.status == EquipmentStatus.GONE }
        here.groupBy { it.section }.forEach { (section, group) ->
            SubHeading(section)
            group.forEach { e ->
                val status = e.statusOn(today)
                ListRow(
                    name = e.name,
                    meta =
                        statusLabel(status) + (e.brokenSince?.takeIf { status == EquipmentStatus.BROKEN }?.let { " since ${Dates.short(it)}" } ?: ""),
                    metaAccent = status != EquipmentStatus.WORKING,
                    onClick = { onItem(e.id) },
                )
            }
        }
        if (gone.isNotEmpty()) {
            SubHeading("Not in this gym")
            gone.forEach { e -> ListRow(name = e.name, meta = "Gone", onClick = { onItem(e.id) }) }
        }
    }
}

/** "Is the leg press fixed yet?", asked every 3 days for broken items. */
@Composable
fun FixedPrompt(item: Equipment) {
    val actions = rememberActions()
    val context = LocalContext.current
    RtbCard {
        SectionLabel("Broken since ${item.brokenSince?.let(Dates::short) ?: "a while"}")
        Body("Is the ${item.name.lowercase()} fixed yet?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Yes, it's fixed", onClick = {
                actions.run { setEquipmentStatus(item.id, EquipmentStatus.WORKING) }
            }, modifier = Modifier.weight(1f))
            SecondaryButton("Not yet", onClick = { actions.run { markFixedAsked(item.id) } }, modifier = Modifier.weight(1f))
        }
        ReportButton(item, context, actions)
    }
}

@Composable
private fun ReportButton(item: Equipment, context: Context, actions: Actions) {
    val since = item.brokenSince ?: item.statusDate ?: return
    com.raisethebar.app.ui.components.LinkButton("Report it: copy a message for the facilities team", onClick = {
        copyToClipboard(context, Gym.reportMessage(item.name, since))
        actions.message("Message copied. Paste it into an email or chat.")
    })
}

/** One item: status (working, busy today, broken, gone), weights, report it, and the exercises that use it. */
@Composable
fun EquipmentScreen(id: String, onBack: () -> Unit, onExercise: (String) -> Unit) {
    val actions = rememberActions()
    val context = LocalContext.current
    val data = rememberLoad(id) { Triple(gym()[id], exercises().values.filter { id in it.equipmentIds }.sortedBy { it.name }, time.today()) }
    val item = data?.first
    var draft by remember(item) { mutableStateOf(item) }
    ScreenFrame(
        title = item?.name.orEmpty(),
        onBack = onBack,
        caption = "My Gym",
        bottom =
            if (item != null && hasWeights(item)) {
                { PrimaryButton("Save weights", onClick = { draft?.let { d -> actions.run(then = onBack) { saveEquipment(d) } } }) }
            } else {
                null
            },
    ) {
        val (e, uses, today) = data ?: return@ScreenFrame
        if (e == null) return@ScreenFrame
        val statuses = EquipmentStatus.ALL
        SectionLabel("Status")
        Segmented(statuses.map(::statusLabel), statuses.indexOf(e.statusOn(today)), { i ->
            actions.run { setEquipmentStatus(e.id, statuses[i]) }
        }, description = "Status")
        Body(
            when (e.statusOn(today)) {
                EquipmentStatus.BUSY -> "Busy is for today only: it resets at midnight. Backups are used meanwhile."
                EquipmentStatus.BROKEN -> "Backups are used until it's fixed. Every 3 days the app asks whether it's fixed."
                EquipmentStatus.GONE -> "Gone for good: the best backup takes over every slot that used it."
                else -> "Working: the plan uses it as normal."
            },
            muted = true,
        )
        if (e.status == EquipmentStatus.BROKEN) {
            val message = Gym.reportMessage(e.name, e.brokenSince ?: today)
            AlertBox(message, kind = AlertKind.WARN, title = "Report it")
            SecondaryButton("Copy the message", onClick = {
                copyToClipboard(context, message)
                actions.message("Message copied. Paste it into an email or chat.")
            })
        }
        val d = draft
        if (d != null && hasWeights(d) && e.status != EquipmentStatus.GONE) {
            SectionLabel("Weights")
            WeightsEditor(d, onChange = { draft = it })
        }
        e.note?.let { Body(it, muted = true) }
        if (uses.isNotEmpty()) {
            SectionLabel("Exercises that use it")
            uses.forEach { ex -> ListRow(name = ex.name, onClick = { onExercise(ex.id) }) }
        }
    }
}
