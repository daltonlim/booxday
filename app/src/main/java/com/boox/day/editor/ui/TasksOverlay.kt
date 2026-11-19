package com.boox.day.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import com.boox.day.R
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Task
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.LayoutProfiles
import com.boox.day.data.db.TaskDueDates

/**
 * Width of the control column containing checkbox, priority, due date, and move buttons.
 * This is used to ensure the canvas doesn't overlap with controls.
 *
 * Due dates are always enabled (no longer a user preference).
 */
val TASKS_CONTROL_COLUMN_WIDTH: Dp
    get() = if (LayoutProfiles.isNarrowScreen) {
        // Narrow screens (Go 6): no due-date button; fixed per device, because Tasks ink is stored
        // in canvas pixels and a wider column would cover the left of existing handwriting
        EInkTheme.TasksTab.controlColumnWidthCompactProfile
    } else {
        EInkTheme.TasksTab.controlColumnWidthWithDueDates
    }

/**
 * Height offset for top alignment.
 * OPTION C: SharedToolbar is now EXTERNAL to the canvas area, so minimal offset needed.
 * Just 1dp for visual separation/border alignment.
 */
val TASKS_TOOLBAR_OFFSET = 1.dp

/**
 * Control column for Tasks mode - positioned to the LEFT of the canvas.
 * Contains checkbox, priority dropdown, and chevron button for each task row.
 *
 * IMPORTANT: This is a SEPARATE composable from the canvas area.
 * The canvas (EditorSurface) should be placed to the RIGHT of this column
 * so that stylus input doesn't overlap with the controls.
 *
 * INTERACTIONS:
 * - Checkbox tap: Toggle task completion
 * - Priority tap: Open priority dropdown
 * - Chevron tap: Contextual action (type if empty, move if has content)
 * - Row long-press: Clear the task slot (if has content)
 *
 * @param tasks List of tasks for current page
 * @param taskHasContent Function to check if a task slot has content (strokes, text, metadata)
 * @param onCheckboxToggle Called when checkbox is toggled
 * @param onPriorityChange Called when priority is changed
 * @param onChevronTap Called when chevron is tapped (caller decides action based on content)
 * @param onChevronLongPress Called when chevron is long-pressed (direct edit)
 * @param onLongPressRow Called when row is long-pressed (for clearing)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksControlColumn(
    tasks: List<Task>,
    taskHasContent: (Task) -> Boolean,
    onCheckboxToggle: (Task) -> Unit,
    onCheckboxLongPress: () -> Unit,
    onPriorityChange: (Task, String) -> Unit,
    onDueDateTap: (Task) -> Unit,
    onChevronTap: (Task) -> Unit,
    onChevronLongPress: (Task) -> Unit,
    onLongPressRow: (Task) -> Unit,
    onReorderRow: ((Task, Int) -> Unit)? = null,
    onSetSubtask: ((Task, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var columnHeightPx by remember { mutableStateOf(0) }
    var dragTargetRow by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = modifier
            .width(TASKS_CONTROL_COLUMN_WIDTH)
            .fillMaxHeight()
            .background(EInkTheme.TasksTab.controlColumnBackground)
            .onSizeChanged { columnHeightPx = it.height }
    ) {
        // Top spacer to match toolbar overlay offset - keeps rows aligned with canvas lines
        Spacer(modifier = Modifier.height(TASKS_TOOLBAR_OFFSET))

        tasks.forEachIndexed { index, task ->
            // key() provides stable identity to reduce unnecessary recomposition
            key(task.id) {
                val hasContent = taskHasContent(task)
                val isSubtask = task.parentTaskId != null
                val rowHeightPx = if (tasks.isNotEmpty() && columnHeightPx > 0) {
                    (columnHeightPx.toFloat() / tasks.size).coerceAtLeast(1f)
                } else 0f
                TaskControlRow(
                    isCompleted = task.isCompleted,
                    isSubtask = isSubtask,
                    dueDay = task.dueDay,
                    hasContent = hasContent,
                    rowIndex = index,
                    totalRows = tasks.size,
                    isDragTarget = dragTargetRow == index,
                    onCheckboxToggle = { onCheckboxToggle(task) },
                    onOpenMenu = { onChevronTap(task) },
                    onHandleGesture = { totalDx, totalDy, finished ->
                        if (abs(totalDy) >= abs(totalDx) && rowHeightPx > 0f && onReorderRow != null && hasContent) {
                            val target = (index + (totalDy / rowHeightPx).roundToInt()).coerceIn(0, tasks.lastIndex)
                            if (finished) {
                                dragTargetRow = null
                                if (target != index) onReorderRow(task, target)
                            } else {
                                dragTargetRow = target
                            }
                        } else if (finished) {
                            dragTargetRow = null
                            if (abs(totalDx) > abs(totalDy) && onSetSubtask != null && index > 0) {
                                // Swipe right (or left) on a main task makes it a subtask;
                                // swipe left (or right) on a subtask turns it back into a main task
                                onSetSubtask(task, !isSubtask)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Extracted row composable with Google Keep style 6-dot drag handle on the left of the checkbox:
 * - Pull Up / Down: reorder task row
 * - Swipe Right / Left: indent as subtask or unindent back to main task
 * - Tap checkbox: toggle completion
 * - Long-press row: open task menu (or type dialog if empty)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskControlRow(
    isCompleted: Boolean,
    isSubtask: Boolean = false,
    dueDay: String?,
    hasContent: Boolean,
    rowIndex: Int,
    totalRows: Int,
    isDragTarget: Boolean = false,
    onCheckboxToggle: () -> Unit,
    onOpenMenu: () -> Unit,
    onHandleGesture: (totalDx: Float, totalDy: Float, finished: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnCheckboxToggle by rememberUpdatedState(onCheckboxToggle)
    val currentOnOpenMenu by rememberUpdatedState(onOpenMenu)
    val currentOnHandleGesture by rememberUpdatedState(onHandleGesture)

    Column(
        modifier = modifier.then(
            if (isDragTarget) Modifier.border(2.dp, EInkTheme.Colors.primary) else Modifier
        )
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    val dragSlop = viewConfiguration.touchSlop * 0.6f
                    val longPressMs = 450L
                    val handleWidthPx = 28.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startedOnCheckbox = down.position.x >= handleWidthPx
                        val downTime = System.currentTimeMillis()
                        var totalDx = 0f
                        var totalDy = 0f
                        var dragging = false
                        var longPressed = false
                        while (true) {
                            val event = withTimeoutOrNull(50L) { awaitPointerEvent() }
                            if (event == null) {
                                if (!dragging && !longPressed && System.currentTimeMillis() - downTime >= longPressMs) {
                                    longPressed = true
                                    currentOnOpenMenu()
                                }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            totalDx += change.position.x - change.previousPosition.x
                            totalDy += change.position.y - change.previousPosition.y
                            if (!dragging && (abs(totalDx) > dragSlop || abs(totalDy) > dragSlop)) {
                                dragging = true
                            }
                            if (dragging) {
                                change.consume()
                                currentOnHandleGesture(totalDx, totalDy, false)
                            } else if (!longPressed && System.currentTimeMillis() - downTime >= longPressMs) {
                                longPressed = true
                                currentOnOpenMenu()
                            }
                        }
                        if (dragging) {
                            currentOnHandleGesture(totalDx, totalDy, true)
                        } else if (!longPressed) {
                            if (startedOnCheckbox && hasContent) {
                                currentOnCheckboxToggle()
                            } else {
                                currentOnOpenMenu()
                            }
                        }
                    }
                }
                .padding(
                    start = if (isSubtask) 24.dp else 2.dp,
                    end = 2.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Google Keep style 6-dot drag handle on the LEFT of the checkbox
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val dotColor = if (hasContent) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray
                        val dotRadius = 1.8.dp.toPx()
                        val colGap = 6.dp.toPx()
                        val rowGap = 6.dp.toPx()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        for (r in -1..1) {
                            for (c in listOf(-0.5f, 0.5f)) {
                                drawCircle(
                                    color = dotColor,
                                    radius = dotRadius,
                                    center = Offset(cx + c * colGap, cy + r * rowGap)
                                )
                            }
                        }
                    }
            )

            // E-Ink Checkbox visual (interactions handled by the row's unified gesture detector above)
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(EInkTheme.TasksTab.checkboxIconCompact)
                        .background(
                            color = if (isCompleted) EInkTheme.Checkbox.checkedBackground else EInkTheme.Colors.white,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = EInkTheme.Checkbox.borderWidth,
                            color = EInkTheme.Checkbox.borderColor,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EInkTheme.Checkbox.checkmarkColor,
                            modifier = Modifier.size((EInkTheme.TasksTab.checkboxIconCompact.value * 0.7f).dp)
                        )
                    }
                }
            }
        }

        // Horizontal divider between rows (except last)
        if (rowIndex < totalRows - 1) {
            Divider(
                color = EInkTheme.TasksTab.rowDividerColor,
                thickness = EInkTheme.TasksTab.rowDividerThickness,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
            )
        }
    }
}

/**
 * Horizontal lines overlay for the canvas area.
 *
 * PERF: Uses drawWithCache to cache line position calculations.
 * The cache is invalidated only when size changes, avoiding redundant
 * calculations on every frame/composition.
 */
@Composable
fun TasksLinesOverlay(
    rowCount: Int = EInkTheme.TasksTab.rowCount,
    modifier: Modifier = Modifier
) {
    val lineColor = EInkTheme.TasksTab.rowDividerColor
    val strokeWidthDp = EInkTheme.TasksTab.rowDividerThickness
    val topOffsetDp = TASKS_TOOLBAR_OFFSET

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                // Cache these calculations - only recomputed when size changes
                val topOffsetPx = topOffsetDp.toPx()
                val strokeWidth = strokeWidthDp.toPx()
                val visibleHeight = size.height - topOffsetPx
                val rowHeight = visibleHeight / rowCount

                onDrawBehind {
                    // Draw a horizontal line at the BOTTOM of each row (row separators)
                    for (i in 0 until rowCount) {
                        val y = topOffsetPx + (i + 1) * rowHeight
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Butt
                        )
                    }
                }
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EInkCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    onLongPress: () -> Unit = {},
    touchSize: Dp = EInkTheme.Global.touchTarget,
    iconSize: Dp = EInkTheme.Checkbox.size,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(touchSize)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,  // No ripple for E-Ink
                onClick = { onCheckedChange() },
                onLongClick = { onLongPress() }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Checkbox visual (can be smaller than touch target)
        Box(
            modifier = Modifier
                .size(iconSize)
                .background(
                    color = if (checked) EInkTheme.Checkbox.checkedBackground else EInkTheme.Colors.white,
                    shape = RoundedCornerShape(4.dp)
                )
                .border(
                    width = EInkTheme.Checkbox.borderWidth,
                    color = EInkTheme.Checkbox.borderColor,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = EInkTheme.Checkbox.checkmarkColor,
                    modifier = Modifier.size((iconSize.value * 0.7f).dp)
                )
            }
        }
    }
}

@Composable
private fun EInkReorderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(22.dp)
            .height(34.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(21.dp)
                .background(
                    color = EInkTheme.Colors.white,
                    shape = RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EInkPriorityButton(
    priority: String,
    isCompleted: Boolean,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onPriorityChange: (String) -> Unit,
    touchSize: Dp = EInkTheme.TasksTab.priorityButtonSize,
    iconSize: Dp = EInkTheme.TasksTab.priorityButtonSize,
    // Compact screens only (plan Phase 1.5): due-date mark + calendar entry in the menu
    dueDay: String? = null,
    onDueDateTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(touchSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null  // No ripple for E-Ink
            ) { onShowMenuChange(true) },
        contentAlignment = Alignment.Center
    ) {
        // Priority button visual
        Box(
            modifier = Modifier
                .size(iconSize)
                .background(
                    color = when {
                        isCompleted -> EInkTheme.Colors.offWhite
                        priority == "A" -> EInkTheme.Colors.primary  // Inverted for high priority
                        else -> EInkTheme.Colors.white
                    },
                    shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .border(
                    width = when {
                        isCompleted -> 1.5.dp
                        priority == "A" -> 2.dp
                        priority == "B" -> 2.dp
                        priority == "C" -> 1.5.dp
                        else -> 1.5.dp
                    },
                    color = when {
                        isCompleted -> EInkTheme.Colors.gray
                        priority.isEmpty() -> EInkTheme.Colors.darkGray
                        else -> EInkTheme.Colors.primary
                    },
                    shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = priority.ifEmpty { "-" },
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = if (priority == "A" || priority == "B") FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isCompleted -> EInkTheme.Disabled.foreground
                    priority == "A" -> EInkTheme.Colors.white  // White text on black for "A"
                    else -> EInkTheme.Colors.primary
                }
            )
        }

        // Compact: small mark when a due date is set (red when overdue)
        if (onDueDateTap != null && dueDay != null) {
            val isOverdue = TaskDueDates.isOverdue(dueDay, isCompleted)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(7.dp)
                    .background(
                        if (isOverdue) EInkTheme.Colors.red else EInkTheme.Colors.primary,
                        CircleShape
                    )
            )
        }

        // E-Ink optimized priority popup (no scrim, no shadow, no animation)
        if (showMenu) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, touchSize.value.toInt() + 4),
                onDismissRequest = { onShowMenuChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .background(EInkTheme.PopupMenu.background)
                        .border(
                            width = EInkTheme.PopupMenu.borderWidth,
                            color = EInkTheme.PopupMenu.borderColor
                        )
                ) {
                    listOf(
                        "" to "None",
                        "A" to "A (High)",
                        "B" to "B (Medium)",
                        "C" to "C (Low)"
                    ).forEach { (value, label) ->
                        val isSelected = priority == value
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(EInkTheme.MenuItem.height)
                                .background(
                                    if (isSelected) EInkTheme.Selection.background
                                    else EInkTheme.PopupMenu.background
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onPriorityChange(value)
                                    onShowMenuChange(false)
                                }
                                .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                fontSize = EInkTheme.MenuItem.fontSize,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) EInkTheme.Selection.foreground
                                        else EInkTheme.MenuItem.textColor
                            )
                        }
                    }
                    // Compact: due date lives here until the task card (Phase T3)
                    if (onDueDateTap != null) {
                        Divider(color = EInkTheme.PopupMenu.borderColor, thickness = 0.5.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(EInkTheme.MenuItem.height)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onShowMenuChange(false)
                                    onDueDateTap()
                                }
                                .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (dueDay != null) R.drawable.ic_calendar_due_set
                                         else R.drawable.ic_calendar_due
                                ),
                                contentDescription = if (dueDay != null) "Change due date" else "Set due date",
                                tint = EInkTheme.Colors.primary,
                                modifier = Modifier.size(EInkTheme.TasksTab.calendarIconCompact)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Due date button for Tasks control column.
 * Shows calendar icon with visual state indicating:
 * - Empty (gray outline): No due date set
 * - Filled (black): Due date set
 * - Filled with badge: Overdue
 */
@Composable
private fun EInkDueDateButton(
    dueDay: String?,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One overdue rule (plan T2): overdue only from the day after the due day
    val isOverdue = TaskDueDates.isOverdue(dueDay, isCompleted)
    val hasDueDate = dueDay != null
    val touchSize = EInkTheme.TasksTab.calendarTouchCompact
    val iconSize = EInkTheme.TasksTab.calendarIconCompact

    Box(
        modifier = modifier
            .size(touchSize)
            // A ticked task's due date opens too (owner, 31 Oct 2026)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                id = if (hasDueDate) R.drawable.ic_calendar_due_set
                     else R.drawable.ic_calendar_due
            ),
            contentDescription = when {
                isOverdue -> "Overdue"
                hasDueDate -> "Due date set"
                else -> "Set due date"
            },
            tint = when {
                isOverdue -> EInkTheme.Colors.primary
                hasDueDate -> EInkTheme.Colors.primary
                else -> EInkTheme.TasksTab.dueDateIconColorEmpty
            },
            modifier = Modifier.size(iconSize)
        )

        // Overdue badge (small dot)
        if (isOverdue) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .background(EInkTheme.Colors.red, CircleShape)
            )
        }
    }
}
