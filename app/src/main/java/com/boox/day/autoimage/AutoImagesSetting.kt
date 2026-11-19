package com.boox.day.autoimage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.today.LastWrite
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RadioOption
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.launch

/**
 * Preferences card "Auto-save images" (Nov 2026, [AutoImages]): which images, how the to-do list looks,
 * how long after writing and the folder. Keeps its own state, so the Preferences dialog and
 * MainScreen need no new parameters.
 */
@Composable
fun AutoImagesSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(GlobalAppSettings.current.autoImages) }
    var folder by remember { mutableStateOf(AutoImageFolder.folderLabel(context)) }
    var hasChosenFolder by remember { mutableStateOf(AutoImageFolder.chosenFolder(context) != null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun change(changed: AutoImageSettings) {
        choice = changed
        KvProxy(context).setAppSettings(GlobalAppSettings.current.copy(autoImages = changed))
        AutoImages.onSettingsChanged(context, changed)
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching { AutoImageFolder.chooseFolder(context, tree) }
                .onSuccess {
                    folder = AutoImageFolder.folderLabel(context)
                    hasChosenFolder = true
                    status = null
                    if (choice.anyOn) AutoImages.requestSave(context, force = true)
                }
                .onFailure { status = "That folder can't be used. Please choose another one." }
        }
    }

    SettingCard(
        title = "Auto-save images",
        description = "Saves the memo or tasks page you opened last to memo.png (on a blank page, the page shown before) and today's to-do list as pictures every time they change, " +
            "to use as a wallpaper or screensaver, or in a synced folder."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            SwitchRow("Memo page", choice.notes) { change(choice.copy(notes = it)) }
            SwitchRow("To-do list", choice.tasks) { change(choice.copy(tasks = it)) }

            if (choice.tasks) {
                Heading("To-do list looks like")
                Row {
                    RadioOption("Handwriting", choice.tasksStyle == TasksImageStyle.HANDWRITING, {
                        change(choice.copy(tasksStyle = TasksImageStyle.HANDWRITING))
                    })
                    RadioOption("Typed list", choice.tasksStyle == TasksImageStyle.LIST, {
                        change(choice.copy(tasksStyle = TasksImageStyle.LIST))
                    })
                }
                Note(
                    if (choice.tasksStyle == TasksImageStyle.HANDWRITING) {
                        "Each Tasks page as you wrote it, with its checkboxes."
                    } else {
                        "Handwritten tasks show as text once they have been read."
                    }
                )
                if (choice.tasksStyle == TasksImageStyle.LIST) {
                    SwitchRow("Today's events above the tasks", choice.listShowsEvents) { change(choice.copy(listShowsEvents = it)) }
                }
            }

            if (choice.anyOn) {
                Heading("Save after the pen rests for")
                Row {
                    AutoImageSettings.INK_DELAY_CHOICES.forEach { seconds ->
                        RadioOption(delayLabel(seconds), choice.inkDelaySeconds == seconds, {
                            change(choice.copy(inkDelaySeconds = seconds))
                        })
                    }
                }

                Heading("Caption along the bottom")
                CaptionField(choice.captionText) { change(choice.copy(captionText = it)) }
                Note("White text on a black band at the bottom of every image. Leave empty for none.")

                Text(
                    text = LastWrite.statusText(
                        AutoImages.lastWrite.read(context), done = "saved",
                        use24Hour = GlobalAppSettings.current.timeFormat == AppSettings.TimeFormat.TwentyFourHour
                    ),
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.primary
                )
            }

            Text(text = "Folder: $folder", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                EinkSecondaryButton(text = "Choose Folder", onClick = { pickFolder.launch(null) }, width = 150.dp)
                if (hasChosenFolder) {
                    EinkSecondaryButton(
                        text = "Use Default",
                        onClick = {
                            AutoImageFolder.useDefaultFolder(context)
                            folder = AutoImageFolder.folderLabel(context)
                            hasChosenFolder = false
                            if (choice.anyOn) AutoImages.requestSave(context, force = true)
                        },
                        width = 130.dp
                    )
                }
            }
            if (choice.anyOn) {
                EinkSecondaryButton(
                    text = "Save Now",
                    onClick = {
                        status = "Saving…"
                        scope.launch {
                            status = runCatching { AutoImages.saveAll(context, force = true) }
                                .fold({ "Saved $it ${if (it == 1) "image" else "images"}" }, { "Not saved: ${it.message ?: "the folder can't be written to"}" })
                        }
                    },
                    width = 130.dp
                )
            }
            status?.let { Note(it) }
        }
    }
}

private fun delayLabel(seconds: Int): String = if (seconds % 60 == 0) "${seconds / 60} min" else "$seconds s"

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = EInkTheme.Global.touchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary, modifier = Modifier.weight(1f))
        EInkToggle(checked = checked, onCheckedChange = onChange)
    }
}

/** Saved when typing is done (Done or leaving the field), so the images aren't redrawn on every letter. */
@Composable
private fun CaptionField(saved: String, onSave: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var draft by remember { mutableStateOf(saved) }
    fun commit() {
        if (draft != saved) onSave(draft)
    }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = TextStyle(fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .border(1.dp, EInkTheme.Colors.primary)
            .padding(10.dp)
            .onFocusChanged { if (!it.isFocused) commit() }
    )
}

@Composable
private fun Heading(text: String) {
    Text(text = text, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
}

@Composable
private fun Note(text: String) {
    Text(text = text, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
}
