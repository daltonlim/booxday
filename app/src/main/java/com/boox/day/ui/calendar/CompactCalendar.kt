package com.boox.day.ui.calendar

import androidx.compose.foundation.background
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.Divider
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.Task
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.LayoutProfiles
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Compact calendar pieces for narrow screens (plan Phase 1.5, "writing first"; layout B, Oct 2026):
 * a two-week strip and one-line Events/Due boxes stay above the page; the month grid opens as a
 * popup over the page.
 * Used only when LayoutProfiles.isCompact; the Regular layout does not use this file.
 */

/*
 * Pocket screens (Palma, Nov 2026): the header's calendar button cycles the one-week strip, the month
 * in its place ([PocketMonthPanel]) and nothing (full screen; its date line brings the week back).
 * The month moves the toolbar and page down instead of shrinking them: the page keeps its height in
 * all three, so Tasks ink stays on its rows.
 */

/** Fixed heights of the compact rows (the Tasks canvas height must never change at runtime). */
object CompactCalendarDimens {
    private val weekdayRowHeight: Dp = 14.dp
    private val weekRowHeight: Dp = 44.dp  // finger target

    /** Weekday letters and [weeks] rows of days (layout B: two weeks, 102 dp). */
    fun weekStripHeight(weeks: Int): Dp = weekdayRowHeight + weekRowHeight * weeks
    val chipRowHeight: Dp = 56.dp        // Events / Due boxes: 46dp boxes, comfortable for a finger
    val popupTopOffset: Dp = 58.dp       // below the compact header row (53dp buttons + padding)
    val popupHeaderButton: Dp = 52.dp    // Events/Due popup: ‹ › + touch size (Oct 2026, finger-friendly)
    val popupHeaderIcon: Dp = 32.dp      // ... and their icons
}

/** First day of the two-week window whose first row holds [anchorDate]. */
fun compactWindowStart(anchorDate: LocalDate, firstDayOfWeekSetting: AppSettings.FirstDayOfWeek): LocalDate {
    return firstDayOfWeekSetting.weekStart(anchorDate)
}

/**
 * [weeks] weeks (layout B, Oct 2026: two; pocket screens: one, or six for the month): the week
 * containing [anchorDate] and the next ones, under one row of single-letter weekdays, with event dots.
 * Days outside [shownMonth] (1-12), when given, are gray. Tapping a day selects it; the header ‹ ›
 * move the window [weeks] weeks at a time.
 */
@Composable
fun CompactTwoWeekStrip(
    anchorDate: LocalDate,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    weeks: Int = 2,
    shownMonth: Int? = null
) {
    val days = remember(anchorDate, firstDayOfWeekSetting, weeks) {
        val start = compactWindowStart(anchorDate, firstDayOfWeekSetting)
        (0L until weeks * 7L).map { start.plusDays(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CompactCalendarDimens.weekStripHeight(weeks))
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            days.take(7).forEach { day ->
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    fontSize = 11.sp,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                week.forEach { day ->
                    CompactDayCell(
                        day = day,
                        selectedDate = selectedDate,
                        today = today,
                        badges = badges,
                        onDateSelected = onDateSelected,
                        isOtherMonth = shownMonth != null && day.monthValue != shownMonth,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDayCell(
    day: LocalDate,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    onDateSelected: (String) -> Unit,
    isOtherMonth: Boolean,
    modifier: Modifier
) {
    val dateString = day.toString()  // ISO yyyy-MM-dd (locale independent)
    val isSelected = dateString == selectedDate
    val isToday = dateString == today
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .background(
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                } else Modifier
            )
            .noRippleClickable { onDateSelected(dateString) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val textColor = when {
            isSelected -> EInkTheme.Colors.white
            isOtherMonth -> EInkTheme.CalendarGrid.dayColorOutsideMonth
            else -> EInkTheme.Colors.primary
        }
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 17.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        // Event dot (space kept when empty so dates don't shift)
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(4.dp)
                .background(
                    if (dateString in badges.datesWithEvents) textColor else Color0,
                    CircleShape
                )
        )
    }
}

/** Transparent fill for the empty event-dot slot. */
private val Color0 = androidx.compose.ui.graphics.Color.Transparent

/**
 * "Events n · first event" and "Due n · first task" (layout B, Oct 2026): two one-line boxes side by
 * side; each opens that list over the page. The whole row height is the touch target.
 */
@Composable
fun CompactPreviewBoxes(
    eventsCount: Int,
    eventsPreview: String?,
    dueCount: Int,
    duePreview: String?,
    onEventsClick: () -> Unit,
    onDueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CompactCalendarDimens.chipRowHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactPreviewBox(label = "Events $eventsCount", preview = eventsPreview, onClick = onEventsClick, modifier = Modifier.weight(1f))
        CompactPreviewBox(label = "Due $dueCount", preview = duePreview, onClick = onDueClick, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactPreviewBox(label: String, preview: String?, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .noRippleClickable { onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 1
            )
            if (!preview.isNullOrBlank()) {
                Text(text = " · ", fontSize = 15.sp, color = EInkTheme.Colors.gray, maxLines = 1)
                Text(
                    text = preview,
                    fontSize = 15.sp,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Pocket screens: the month in place of the week strip, with [agenda] (the selected day's list, as
 * under the week strip) under it; the page follows below. The header's ‹ › change the month;
 * tapping a day selects it and the month stays.
 */
@Composable
fun PocketMonthPanel(
    year: Int,
    month: Int,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek,
    onDateSelected: (String) -> Unit,
    agenda: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.background)
    ) {
        CompactTwoWeekStrip(
            anchorDate = LocalDate.of(year, month + 1, 1),
            selectedDate = selectedDate,
            today = today,
            badges = badges,
            firstDayOfWeekSetting = firstDayOfWeekSetting,
            onDateSelected = onDateSelected,
            weeks = POCKET_MONTH_WEEKS,
            shownMonth = month + 1
        )
        agenda()
    }
}

/**
 * Pocket screens, under the week strip and the month: the day's events (time, title), then its due
 * tasks (checkbox, text). Its height is fixed ([POCKET_AGENDA_ROWS] rows, the last one "+N more" when
 * they don't fit) so the page under the week strip keeps its height, and with it the Tasks rows.
 */
@Composable
fun PocketDayAgenda(
    events: List<CalendarEvent>,
    dueToday: List<Task>,
    overdue: List<Task>,
    onOpenList: (tab: String) -> Unit
) {
    val sorted = remember(events) { events.sortedWith(compareBy({ !it.isAllDay }, { it.startTime })) }
    val dueTasks = dueToday.map { it to false } + overdue.map { it to true }
    val slots = if (sorted.size + dueTasks.size > POCKET_AGENDA_ROWS) POCKET_AGENDA_ROWS - 1 else POCKET_AGENDA_ROWS
    val shownEvents = sorted.take(slots)
    val shownDue = dueTasks.take(slots - shownEvents.size)
    val more = sorted.size + dueTasks.size - shownEvents.size - shownDue.size
    Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.background)
            .height(POCKET_AGENDA_ROW_HEIGHT * POCKET_AGENDA_ROWS + POCKET_AGENDA_PADDING * 2)
            .padding(horizontal = 16.dp, vertical = POCKET_AGENDA_PADDING)
    ) {
        if (shownEvents.isEmpty() && shownDue.isEmpty()) {
            PocketAgendaRow(onClick = { onOpenList("events") }) {
                Text(text = "Nothing on this day", fontSize = 15.sp, color = EInkTheme.Colors.gray)
            }
        }
        shownEvents.forEach { event ->
            PocketAgendaRow(onClick = { onOpenList("events") }) {
                Text(
                    text = pocketEventTime(event),
                    fontSize = 14.sp,
                    color = EInkTheme.Colors.gray,
                    maxLines = 1,
                    modifier = Modifier.width(POCKET_AGENDA_LEAD_WIDTH)
                )
                Text(
                    text = event.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        shownDue.forEach { (task, isOverdue) ->
            PocketAgendaRow(onClick = { onOpenList("due") }) {
                Box(modifier = Modifier.width(POCKET_AGENDA_LEAD_WIDTH)) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(1.5.dp, EInkTheme.Colors.primary, RoundedCornerShape(2.dp))
                    )
                }
                Text(
                    text = compactTaskPreview(task),
                    fontSize = 15.sp,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (isOverdue) "  · overdue" else "  · due today",
                    fontSize = 13.sp,
                    color = EInkTheme.Colors.gray,
                    maxLines = 1
                )
            }
        }
        if (more > 0) {
            PocketAgendaRow(onClick = { onOpenList(if (sorted.size > shownEvents.size) "events" else "due") }) {
                Text(
                    text = "+$more more",
                    fontSize = 14.sp,
                    color = EInkTheme.Colors.gray,
                    modifier = Modifier.padding(start = POCKET_AGENDA_LEAD_WIDTH)
                )
            }
        }
    }
    Divider(color = EInkTheme.Colors.primary, thickness = 2.dp)
}

@Composable
private fun PocketAgendaRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(POCKET_AGENDA_ROW_HEIGHT)
            .noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/**
 * The pocket Events/Due popup's event row, as in [PocketDayAgenda] but finger-size: time, title (and
 * its place), length; gray once it has ended. [onNoteClick] (linked Boox Notes) adds a note button.
 */
@Composable
fun PocketEventRow(event: CalendarEvent, isPast: Boolean, onClick: () -> Unit, onNoteClick: (() -> Unit)?) {
    val color = if (isPast) EInkTheme.Colors.gray else EInkTheme.Colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(POCKET_LIST_ROW_HEIGHT)
            .noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pocketEventTime(event),
            fontSize = 14.sp,
            color = EInkTheme.Colors.gray,
            maxLines = 1,
            modifier = Modifier.width(POCKET_AGENDA_LEAD_WIDTH + 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            event.location?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, fontSize = 13.sp, color = EInkTheme.Colors.gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onNoteClick != null) {
            IconButton(onClick = onNoteClick, modifier = Modifier.size(POCKET_LIST_ROW_HEIGHT)) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = "Open linked note",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        pocketEventLength(event)?.let {
            Text(text = it, fontSize = 13.sp, color = EInkTheme.Colors.gray, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
    Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
}

/** The pocket Events/Due popup's due row: checkbox, task, "due today" or "overdue". */
@Composable
fun PocketDueRow(task: Task, isOverdue: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(POCKET_LIST_ROW_HEIGHT)
            .noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(POCKET_AGENDA_LEAD_WIDTH + 8.dp)) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, EInkTheme.Colors.primary, RoundedCornerShape(2.dp))
            )
        }
        Text(
            text = compactTaskPreview(task),
            fontSize = 16.sp,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isOverdue) "overdue" else "due today",
            fontSize = 13.sp,
            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
            color = if (isOverdue) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
}

private fun pocketEventLength(event: CalendarEvent): String? {
    val end = event.endTime ?: return null
    if (event.isAllDay || end <= event.startTime) return null
    val minutes = (end - event.startTime) / 60_000
    return when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

private val POCKET_LIST_ROW_HEIGHT = 52.dp

private fun pocketEventTime(event: CalendarEvent): String =
    if (event.isAllDay) "All day"
    else Instant.ofEpochMilli(event.startTime).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))

/** Six weeks always, so the month keeps one height. */
private const val POCKET_MONTH_WEEKS = 6
private const val POCKET_AGENDA_ROWS = 3
private val POCKET_AGENDA_PADDING = 6.dp
private val POCKET_AGENDA_ROW_HEIGHT = 34.dp
private val POCKET_AGENDA_LEAD_WIDTH = 64.dp

/**
 * Month grid over the page. ‹ › change the shown month; tapping a day selects it and closes.
 */
@Composable
fun CompactMonthPopup(
    year: Int,
    month: Int,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek,
    showWeekNumbers: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val offsetY = with(LocalDensity.current) { CompactCalendarDimens.popupTopOffset.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .background(EInkTheme.PopupOverlay.background)
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor)
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = EInkTheme.Colors.primary
                    )
                }
                Text(
                    text = "${monthNames[month]} $year",
                    fontSize = EInkTheme.CalendarToolbar.monthFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = EInkTheme.Colors.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = EInkTheme.Colors.primary
                    )
                }
            }
            DayOfWeekHeader(
                selectedDate = selectedDate,
                firstDayOfWeekSetting = firstDayOfWeekSetting,
                showWeekNumbers = showWeekNumbers
            )
            CalendarGrid(
                year = year,
                month = month,
                selectedDate = selectedDate,
                today = today,
                badges = badges,
                onDateSelected = onDateSelected,
                firstDayOfWeekSetting = firstDayOfWeekSetting,
                showWeekNumbers = showWeekNumbers,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(288.dp)  // 6 week rows × 48 dp
            )
        }
    }
}

/**
 * Text for the Events box (layout B): on today the next event that hasn't ended, on other days the
 * first one; timed events start with their time. Null when the day has no events.
 */
fun compactEventPreview(events: List<CalendarEvent>, dayId: String, now: Long = System.currentTimeMillis()): String? {
    if (events.isEmpty()) return null
    val timed = events.filter { !it.isAllDay }.sortedBy { it.startTime }
    val upcoming = if (dayId == LocalDate.now().toString()) {
        timed.firstOrNull { (it.endTime ?: it.startTime) >= now }
    } else {
        timed.firstOrNull()
    }
    val pick = upcoming ?: events.firstOrNull { it.isAllDay } ?: timed.first()
    if (pick.isAllDay) return pick.title
    val time = Instant.ofEpochMilli(pick.startTime).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    return "$time ${pick.title}"
}

/** Text for the Due box (layout B): the task's typed, converted or recognized text. */
fun compactTaskPreview(task: Task): String =
    task.typedText?.takeIf { it.isNotBlank() }
        ?: task.convertedText?.takeIf { it.isNotBlank() }
        ?: task.recognizedText?.takeIf { it.isNotBlank() }
        ?: "Handwritten task"

/** Two-week strip + Events/Due boxes, or [agenda] in place of the boxes (the fixed-height rows above the page; layout B). */
@Composable
fun CompactCalendarRows(
    anchorDate: LocalDate,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek,
    eventsCount: Int,
    eventsPreview: String?,
    dueCount: Int,
    duePreview: String?,
    onDateSelected: (String) -> Unit,
    onOpenList: (tab: String) -> Unit,
    weeks: Int = 2,
    agenda: (@Composable () -> Unit)? = null
) {
    CompactTwoWeekStrip(
        anchorDate = anchorDate,
        selectedDate = selectedDate,
        today = today,
        badges = badges,
        firstDayOfWeekSetting = firstDayOfWeekSetting,
        onDateSelected = onDateSelected,
        weeks = weeks
    )
    if (agenda != null) {
        agenda()
        return
    }
    CompactPreviewBoxes(
        eventsCount = eventsCount,
        eventsPreview = eventsPreview,
        dueCount = dueCount,
        duePreview = duePreview,
        onEventsClick = { onOpenList("events") },
        onDueClick = { onOpenList("due") }
    )
}

/**
 * Events/Due day list over the page: ‹ day › and + (new event on that day) on top, [content] (the
 * list) in the middle and a Close button at the bottom. It covers only part of the screen (Oct 2026)
 * so a tap outside also closes it.
 */
@Composable
fun CompactDayListPopup(
    listDate: String,
    onListDateChange: (String) -> Unit,
    onAddEvent: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val offsetY = with(LocalDensity.current) { CompactCalendarDimens.popupTopOffset.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // Pocket screens (Nov 2026): a full-width sheet under the header, edged like the day's agenda
        val pocket = LayoutProfiles.isPocketScreen
        Column(
            modifier = Modifier
                .fillMaxWidth(if (pocket) 1f else 0.9f)
                .fillMaxHeight(0.72f)  // Oct 2026: 20% taller (was 0.6) to show more events and due items
                .background(EInkTheme.EventsPanel.rowBackground)
                .then(if (pocket) Modifier else Modifier.border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor))
        ) {
            if (pocket) Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
            // Header ‹ day › +: finger-size buttons (52dp, 32dp icons) with a gap before +
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { parseIsoDateOrNull(listDate)?.let { onListDateChange(it.minusDays(1).toString()) } },
                    modifier = Modifier.size(CompactCalendarDimens.popupHeaderButton)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous day",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(CompactCalendarDimens.popupHeaderIcon)
                    )
                }
                Text(
                    text = remember(listDate) {
                        parseIsoDateOrNull(listDate)
                            ?.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                            ?: listDate
                    },
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { parseIsoDateOrNull(listDate)?.let { onListDateChange(it.plusDays(1).toString()) } },
                    modifier = Modifier.size(CompactCalendarDimens.popupHeaderButton)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next day",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(CompactCalendarDimens.popupHeaderIcon)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onAddEvent, modifier = Modifier.size(CompactCalendarDimens.popupHeaderButton)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add event",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(CompactCalendarDimens.popupHeaderIcon)
                    )
                }
            }
            Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
            Column(modifier = Modifier.weight(1f)) {
                content()
            }
            Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
            EinkSecondaryButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            if (pocket) Divider(color = EInkTheme.Colors.primary, thickness = 2.dp)
        }
    }
}

/** Pocket screens: one black-outlined bar of [labels], the [selected] one filled (like the editor's Memos/Tasks switch). */
@Composable
fun PocketSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(POCKET_SEGMENTS_HEIGHT)
            .clip(RoundedCornerShape(POCKET_SEGMENTS_CORNER))
            .border(1.5.dp, EInkTheme.Colors.primary, RoundedCornerShape(POCKET_SEGMENTS_CORNER))
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background)
                    .noRippleClickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary,
                    maxLines = 1
                )
            }
        }
    }
}

private val POCKET_SEGMENTS_HEIGHT = 40.dp
private val POCKET_SEGMENTS_CORNER = 8.dp

/** Parses a yyyy-MM-dd key, or null. */
internal fun parseIsoDateOrNull(date: String): LocalDate? =
    runCatching { LocalDate.parse(date) }.getOrNull()
