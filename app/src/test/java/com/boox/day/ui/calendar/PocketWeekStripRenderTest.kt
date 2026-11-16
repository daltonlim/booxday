package com.boox.day.ui.calendar

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.Task
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Palma's week strip and month (Nov 2026), made-up data. Saves to app/build/palma-mockups/. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w900dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PocketWeekStripRenderTest {

    private val badges = CalendarBadges(datesWithEvents = setOf("2026-11-16", "2026-11-18", "2026-11-20", "2026-11-24"))

    @Test
    fun renderTheWeek() = render("pocket-week-rows.png") {
        CompactCalendarRows(
            anchorDate = LocalDate.of(2026, 11, 16),
            selectedDate = "2026-11-16",
            today = "2026-11-16",
            badges = badges,
            firstDayOfWeekSetting = AppSettings.FirstDayOfWeek.Sunday,
            eventsCount = 2,
            eventsPreview = "9:30 Stand-up",
            dueCount = 1,
            duePreview = "Pay rent",
            onDateSelected = {},
            onOpenList = {},
            weeks = 1,
            agenda = { agenda() }
        )
    }

    @Test
    fun renderTheMonth() = render("pocket-month.png") {
        PocketMonthPanel(
            year = 2026,
            month = 10,
            selectedDate = "2026-11-16",
            today = "2026-11-16",
            badges = badges,
            firstDayOfWeekSetting = AppSettings.FirstDayOfWeek.Sunday,
            onDateSelected = {},
            agenda = { agenda() }
        )
    }

    @Composable
    private fun agenda() = PocketDayAgenda(
        events = listOf(event("Dentist", 14, 0), event("Stand-up", 9, 30)),
        dueToday = listOf(task("Pay rent")),
        overdue = emptyList(),
        onOpenList = {}
    )

    private fun event(title: String, hour: Int, minute: Int) = CalendarEvent(
        id = title,
        calendarId = "cal",
        calendarName = "Calendar",
        title = title,
        dayId = "2026-11-16",
        startTime = LocalDate.of(2026, 11, 16).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    private fun task(text: String) = Task(dayDate = "2026-11-16", taskPageIndex = 0, rowIndex = 0, pageId = text, typedText = text)

    private fun render(name: String, content: @Composable () -> Unit) {
        var bounds = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(300 / 160f, 1f)) {
                Column(Modifier.width(439.5.dp).background(Color.White).onGloballyPositioned { bounds = it.boundsInRoot() }) {
                    content()
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(full))
        val image = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        File(File("build/palma-mockups").apply { mkdirs() }, name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
