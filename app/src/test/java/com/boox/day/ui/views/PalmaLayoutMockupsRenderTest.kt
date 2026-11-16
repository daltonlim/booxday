package com.boox.day.ui.views

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas as ComposeCanvas
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Layout drafts for the Boox Palma (824 × 1648 px, 300 dpi), made-up data (Nov 2026). Saves to app/build/palma-mockups/. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1800dp-h1800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PalmaLayoutMockupsRenderTest {

    private class Screen(val file: String, val title: String, val size: DpSize, val content: @Composable () -> Unit)

    private val screens = listOf(
        Screen("00-now", "Now", PORTRAIT) { Now() },
        Screen("a1-week-strip", "A · Week strip", PORTRAIT) { WeekStrip(expanded = false) },
        Screen("a2-week-strip-agenda-open", "A · Agenda opened", PORTRAIT) { WeekStrip(expanded = true) },
        Screen("a3-week-strip-tasks", "A · Tasks tab", PORTRAIT) { WeekStripTasks() },
        Screen("b1-writing-first", "B · Writing first", PORTRAIT) { WritingFirst(calendarOpen = false) },
        Screen("b2-writing-first-month-open", "B · Month opened", PORTRAIT) { WritingFirst(calendarOpen = true) },
        Screen("c-stacked", "C · Stacked month", PORTRAIT) { Stacked() },
        Screen("d1-bottom-tabs-memos", "D · Bottom tabs: Memos", PORTRAIT) { BottomTabs(calendarTab = false) },
        Screen("d2-bottom-tabs-calendar", "D · Bottom tabs: Calendar", PORTRAIT) { BottomTabs(calendarTab = true) },
        Screen("e-today-dashboard", "E · Today dashboard", PORTRAIT) { Dashboard() },
        Screen("f-day-pager", "F · Day pager", PORTRAIT) { DayPager() },
        Screen("g-side-drawer", "G · Side drawer", PORTRAIT) { SideDrawer() },
        Screen("h-tasks-and-memo", "H · Tasks + memo", PORTRAIT) { TasksAndMemo() },
        Screen("i-floating-toolbar", "I · Floating toolbar", PORTRAIT) { FloatingToolbar() },
        Screen("j-landscape", "J · Landscape split", LANDSCAPE) { Landscape() },
    )

    @Test
    fun renderMockups() {
        val out = File("build/palma-mockups").apply { mkdirs() }
        out.listFiles()?.forEach { it.delete() }
        val images = screens.map { screen ->
            var bounds = Rect.Zero
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(PALMA_DENSITY, 1f)) {
                    Box(
                        Modifier.size(screen.size).background(Color.White).onGloballyPositioned { bounds = it.boundsInRoot() }
                    ) { screen.content() }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.findViewById<View>(android.R.id.content)
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(full))
            val image = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            File(out, "${screen.file}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screen.title to image
        }
        writeOverview(out, images)
    }

    /** All screens at a third of their size, five portrait screens a row; landscape takes two. */
    private fun writeOverview(out: File, images: List<Pair<String, Bitmap>>) {
        val scale = 1 / 3f
        val gap = 30
        val label = 56
        val cellW = (images.first().second.width * scale).toInt()
        val cellH = (images.first().second.height * scale).toInt()
        val perRow = 5
        val sheetW = gap + perRow * (cellW + gap)
        val placed = mutableListOf<Triple<String, Bitmap, android.graphics.Rect>>()
        var x = gap
        var y = gap
        images.forEach { (title, image) ->
            val w = (image.width * scale).toInt()
            val h = (image.height * scale).toInt()
            if (x + w > sheetW - gap + 1) {
                x = gap
                y += label + cellH + gap
            }
            placed += Triple(title, image, android.graphics.Rect(x, y + label, x + w, y + label + h))
            x += w + gap
        }
        val sheet = Bitmap.createBitmap(sheetW, y + label + cellH + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(android.graphics.Color.rgb(228, 228, 228))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; typeface = Typeface.DEFAULT_BOLD }
        val frame = Paint().apply { color = android.graphics.Color.rgb(150, 150, 150); style = Paint.Style.STROKE; strokeWidth = 2f }
        placed.forEach { (title, image, rect) ->
            canvas.drawText(title, rect.left.toFloat(), rect.top - 16f, text)
            canvas.drawBitmap(image, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(rect, frame)
        }
        File(out, "overview.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val PALMA_DENSITY = 300 / 160f
        val PORTRAIT = DpSize(439.5.dp, 879.dp)
        val LANDSCAPE = DpSize(879.dp, 439.5.dp)
    }
}

private val Gray = Color(0xFF8A8A8A)
private val Line = Color(0xFFCFCFCF)
private const val TODAY = 16
private val EVENT_DAYS = setOf(3, 10, 16, 18, 20, 24)
private val EVENTS = listOf("9:30" to "Stand-up", "14:00" to "Dentist")
private val TASKS = listOf("Pay rent" to true, "Call the plumber" to false, "Buy milk and eggs" to false, "Book flights" to false)
private val INK = listOf(listOf(5, 2, 4, 3), listOf(3, 6, 2), listOf(4, 3, 5, 2, 3), listOf(6, 2, 4))

@Composable
private fun Now() = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Icons.Filled.ChevronLeft)
        Text("Nov 2026", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Glyph(Icons.Filled.ChevronRight)
        Spacer(Modifier.weight(1f))
        IconRow(Icons.Outlined.Today, Icons.Filled.Add, Icons.Filled.MoreVert)
    }
    Row(Modifier.fillMaxWidth().height(290.dp)) {
        Column(Modifier.weight(0.56f)) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("16", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(" Monday", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Chip("Week")
            }
            MonthGrid(cell = 40.dp, numberSize = 15.sp, dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
        Column(Modifier.weight(0.44f).padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Tab("Events", selected = true)
                Tab("Due", selected = false)
            }
            Spacer(Modifier.height(10.dp))
            EVENTS.forEach { (time, title) ->
                Text(time, fontSize = 12.sp, color = Gray)
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
    Divider(color = Color.Black, thickness = 1.dp)
    Toolbar(showPages = true)
    Ink(Modifier.fillMaxWidth().weight(1f))
}

@Composable
private fun WeekStripHeader() {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Monday 16", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("November 2026", fontSize = 13.sp, color = Gray)
        }
        Spacer(Modifier.weight(1f))
        IconRow(Icons.Outlined.Today, Icons.Filled.Add, Icons.Filled.MoreVert)
    }
    WeekRow()
}

@Composable
private fun WeekStrip(expanded: Boolean) = Column(Modifier.fillMaxSize()) {
    WeekStripHeader()
    if (expanded) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            EVENTS.forEach { (time, title) -> AgendaRow(time, title) }
            DueRow("Pay rent")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Glyph(Icons.Filled.KeyboardArrowUp, 22.dp) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("9:30 ", fontSize = 14.sp, color = Gray)
            Text("Stand-up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("  ·  +1 more  ·  1 due", fontSize = 14.sp, color = Gray)
            Spacer(Modifier.weight(1f))
            Glyph(Icons.Filled.KeyboardArrowDown, 22.dp)
        }
    }
    Divider(color = Color.Black, thickness = 1.dp)
    Toolbar(showPages = true)
    Ink(Modifier.fillMaxWidth().weight(1f), lines = if (expanded) INK.take(3) else INK)
}

@Composable
private fun WeekStripTasks() = Column(Modifier.fillMaxSize()) {
    WeekStripHeader()
    Spacer(Modifier.height(6.dp))
    Divider(color = Color.Black, thickness = 1.dp)
    Toolbar(showPages = true, tasksSelected = true)
    TaskRows(Modifier.fillMaxWidth().weight(1f), rows = 10, handwritten = true)
}

@Composable
private fun WritingFirst(calendarOpen: Boolean) = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mon 16 Nov", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("  ·  2 events", fontSize = 14.sp, color = Gray)
        Spacer(Modifier.weight(1f))
        IconRow(if (calendarOpen) Icons.Filled.KeyboardArrowUp else Icons.Outlined.CalendarMonth, Icons.Filled.MoreVert)
    }
    Divider(color = Color.Black, thickness = 1.dp)
    if (calendarOpen) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            MonthTitle()
            MonthGrid(cell = 46.dp, numberSize = 17.sp)
        }
        Divider(color = Line, thickness = 1.dp)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            EVENTS.forEach { (time, title) -> AgendaRow(time, title) }
            DueRow("Pay rent")
        }
        Divider(color = Color.Black, thickness = 2.dp)
        Ink(Modifier.fillMaxWidth().weight(1f), lines = INK.take(1))
    } else {
        Ink(Modifier.fillMaxWidth().weight(1f))
    }
    Divider(color = Color.Black, thickness = 1.dp)
    Toolbar(showPages = false, height = 60.dp, undo = true)
}

@Composable
private fun Stacked() = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("November", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(" 2026", fontSize = 20.sp, color = Gray)
        Spacer(Modifier.weight(1f))
        IconRow(Icons.Outlined.Today, Icons.Filled.Add, Icons.Filled.MoreVert)
    }
    Box(Modifier.padding(horizontal = 8.dp)) { MonthGrid(cell = 34.dp, numberSize = 15.sp) }
    Divider(color = Line, thickness = 1.dp)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("Monday 16", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        EVENTS.forEach { (time, title) -> AgendaRow(time, title) }
    }
    Divider(color = Color.Black, thickness = 1.dp)
    Toolbar(showPages = true)
    Ink(Modifier.fillMaxWidth().weight(1f), lines = INK.take(3))
}

@Composable
private fun BottomTabs(calendarTab: Boolean) = Column(Modifier.fillMaxSize()) {
    if (calendarTab) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("November 2026", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconRow(Icons.Outlined.Today, Icons.Filled.Add)
        }
        Box(Modifier.padding(horizontal = 8.dp)) { MonthGrid(cell = 52.dp, numberSize = 18.sp) }
        Divider(color = Line, thickness = 1.dp)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).weight(1f)) {
            Text("Monday 16", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            EVENTS.forEach { (time, title) -> AgendaRow(time, title) }
            DueRow("Pay rent")
        }
    } else {
        Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mon 16 Nov", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconRow(Icons.Outlined.Draw, Icons.Outlined.AutoFixNormal, Icons.Outlined.Undo, Icons.Filled.Menu)
        }
        Divider(color = Color.Black, thickness = 1.dp)
        Ink(Modifier.fillMaxWidth().weight(1f))
    }
    BottomNav(selected = if (calendarTab) 1 else 2)
}

@Composable
private fun Dashboard() = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Monday 16", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("November 2026", fontSize = 13.sp, color = Gray)
        }
        Spacer(Modifier.weight(1f))
        IconRow(Icons.Outlined.CalendarMonth, Icons.Filled.MoreVert)
    }
    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card("Events", "2") { EVENTS.forEach { (time, title) -> AgendaRow(time, title) } }
        Card("Tasks", "1 of 4 done") { TASKS.forEach { (title, done) -> TypedTask(title, done) } }
        Card("Memo", "Page 1 of 2", Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) { Ink(Modifier.fillMaxSize(), lines = INK.take(3), top = 30.dp) }
        }
    }
    BottomNav(selected = 0)
}

@Composable
private fun DayPager() = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Icons.Filled.ChevronLeft, 32.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Today", fontSize = 12.sp, color = Gray)
            Text("Monday 16 November", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Glyph(Icons.Filled.ChevronRight, 32.dp)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("9:30 Stand-up")
        Chip("14:00 Dentist")
        Chip("1 due")
    }
    Spacer(Modifier.height(6.dp))
    Divider(color = Color.Black, thickness = 1.dp)
    Toolbar(showPages = true)
    Ink(Modifier.fillMaxWidth().weight(1f))
    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("Swipe for other days", fontSize = 12.sp, color = Gray)
    }
}

@Composable
private fun SideDrawer() = Box(Modifier.fillMaxSize()) {
    WritingFirst(calendarOpen = false)
    Box(Modifier.fillMaxSize().background(Color(0x55000000)))
    Column(Modifier.width(350.dp).fillMaxHeight().background(Color.White).border(1.dp, Color.Black)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BooxDay", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Glyph(Icons.Outlined.Today)
        }
        Column(Modifier.padding(horizontal = 6.dp)) {
            MonthTitle()
            MonthGrid(cell = 40.dp, numberSize = 15.sp)
        }
        Divider(color = Line, thickness = 1.dp)
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            EVENTS.forEach { (time, title) -> AgendaRow(time, title) }
            DueRow("Pay rent")
        }
        Divider(color = Line, thickness = 1.dp)
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            MenuRow(Icons.Outlined.ViewWeek, "Week view")
            MenuRow(Icons.Outlined.Search, "Search")
            MenuRow(Icons.Outlined.Settings, "Settings")
        }
    }
}

@Composable
private fun TasksAndMemo() = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mon 16 Nov", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("  ·  2 events", fontSize = 14.sp, color = Gray)
        Spacer(Modifier.weight(1f))
        IconRow(Icons.Outlined.CalendarMonth, Icons.Filled.MoreVert)
    }
    Divider(color = Color.Black, thickness = 1.dp)
    SectionLabel("Tasks", "1 of 4 done")
    TaskRows(Modifier.fillMaxWidth().height(260.dp), rows = 5, handwritten = true)
    Divider(color = Color.Black, thickness = 2.dp)
    SectionLabel("Memo", "Page 1 of 2")
    Ink(Modifier.fillMaxWidth().weight(1f), lines = INK.take(3), top = 30.dp)
    Divider(color = Color.Black, thickness = 1.dp)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconRow(Icons.Outlined.Draw, Icons.Outlined.AutoFixNormal, Icons.Outlined.Undo)
        Spacer(Modifier.weight(1f))
        Glyph(Icons.Filled.Menu)
    }
}

@Composable
private fun FloatingToolbar() = Box(Modifier.fillMaxSize()) {
    Ink(Modifier.fillMaxSize(), top = 90.dp)
    Text("Mon 16 Nov", Modifier.padding(16.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gray)
    Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)
            .border(1.5.dp, Color.Black, RoundedCornerShape(28.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeSwitch(width = 130.dp)
        Spacer(Modifier.width(14.dp))
        IconRow(Icons.Outlined.Draw, Icons.Outlined.AutoFixNormal, Icons.Outlined.Undo, Icons.Outlined.CalendarMonth)
    }
}

@Composable
private fun Landscape() = Row(Modifier.fillMaxSize()) {
    Column(Modifier.width(330.dp).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Monday 16 Nov", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconRow(Icons.Outlined.Today, Icons.Filled.MoreVert)
        }
        Box(Modifier.padding(horizontal = 6.dp)) { MonthGrid(cell = 36.dp, numberSize = 14.sp) }
        Divider(color = Line, thickness = 1.dp)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            EVENTS.forEach { (time, title) -> AgendaRow(time, title) }
        }
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
    Column(Modifier.weight(1f).fillMaxHeight()) {
        Toolbar(showPages = true)
        Ink(Modifier.fillMaxWidth().weight(1f), lines = INK.take(4))
    }
}

@Composable
private fun Toolbar(showPages: Boolean, height: Dp = 50.dp, undo: Boolean = false, tasksSelected: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ModeSwitch(tasksSelected = tasksSelected)
        Spacer(Modifier.width(14.dp))
        if (undo) IconRow(Icons.Outlined.Draw, Icons.Outlined.AutoFixNormal, Icons.Outlined.Undo) else IconRow(Icons.Outlined.Draw, Icons.Outlined.AutoFixNormal)
        Spacer(Modifier.weight(1f))
        if (showPages) {
            Glyph(Icons.Filled.ChevronLeft, 22.dp)
            Text("1/2", fontSize = 13.sp, color = Gray)
            Glyph(Icons.Filled.ChevronRight, 22.dp)
            Spacer(Modifier.width(6.dp))
        }
        Glyph(Icons.Filled.Menu)
    }
    Divider(color = Line, thickness = 1.dp)
}

@Composable
private fun ModeSwitch(width: Dp = 150.dp, tasksSelected: Boolean = false) {
    Row(Modifier.width(width).height(34.dp).clip(RoundedCornerShape(8.dp)).border(1.5.dp, Color.Black, RoundedCornerShape(8.dp))) {
        listOf("Memos" to !tasksSelected, "Tasks" to tasksSelected).forEach { (label, on) ->
            Box(Modifier.weight(1f).fillMaxHeight().background(if (on) Color.Black else Color.White), contentAlignment = Alignment.Center) {
                Text(label, color = if (on) Color.White else Color.Black, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Int) {
    Divider(color = Color.Black, thickness = 1.dp)
    Row(Modifier.fillMaxWidth().height(64.dp)) {
        listOf(Icons.Outlined.Today to "Today", Icons.Outlined.CalendarMonth to "Calendar", Icons.Outlined.EditNote to "Memos", Icons.Outlined.TaskAlt to "Tasks")
            .forEachIndexed { i, (icon, label) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (i == selected) Color.Black else Color.White)
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                    ) { Icon(icon, null, Modifier.size(24.dp), tint = if (i == selected) Color.White else Color.Black) }
                    Text(label, fontSize = 12.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
    }
}

@Composable
private fun MonthTitle() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Icons.Filled.ChevronLeft)
        Spacer(Modifier.weight(1f))
        Text("November 2026", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Glyph(Icons.Filled.ChevronRight)
    }
}

@Composable
private fun MonthGrid(cell: Dp, numberSize: TextUnit, dayNames: List<String> = listOf("S", "M", "T", "W", "T", "F", "S")) = Column {
    Row(Modifier.fillMaxWidth()) {
        dayNames.forEachIndexed { i, name ->
            Text(
                name, Modifier.weight(1f).padding(vertical = 4.dp), fontSize = 12.sp, textAlign = TextAlign.Center,
                color = if (i == 1) Color.Black else Gray, fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
    val cells = (1..30).map { it to true } + (1..12).map { it to false }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().height(cell)) {
            week.forEach { (day, inMonth) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    DayNumber(day, inMonth, size = cell - 10.dp, fontSize = numberSize)
                    EventDot(inMonth && day in EVENT_DAYS && day != TODAY)
                }
            }
        }
    }
}

@Composable
private fun WeekRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, letter ->
            val day = 15 + i
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(letter, fontSize = 12.sp, color = Gray)
                Spacer(Modifier.height(4.dp))
                DayNumber(day, inMonth = true, size = 40.dp, fontSize = 17.sp)
                EventDot(day in EVENT_DAYS && day != TODAY)
            }
        }
    }
}

@Composable
private fun DayNumber(day: Int, inMonth: Boolean, size: Dp, fontSize: TextUnit) {
    val today = inMonth && day == TODAY
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(if (today) Color.Black else Color.Transparent), contentAlignment = Alignment.Center) {
        Text(
            "$day", fontSize = fontSize, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            color = when {
                today -> Color.White
                inMonth -> Color.Black
                else -> Color(0xFFBBBBBB)
            }
        )
    }
}

@Composable
private fun EventDot(shown: Boolean) {
    Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (shown) Color.Black else Color.Transparent))
}

@Composable
private fun AgendaRow(time: String, title: String) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, Modifier.width(56.dp), fontSize = 14.sp, color = Gray)
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DueRow(title: String) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp)) { Glyph(Icons.Outlined.CheckBoxOutlineBlank, 20.dp) }
        Text(title, fontSize = 15.sp)
        Text("  · due today", fontSize = 13.sp, color = Gray)
    }
}

@Composable
private fun TypedTask(title: String, done: Boolean) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(if (done) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            title, fontSize = 15.sp, color = if (done) Gray else Color.Black,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

@Composable
private fun TaskRows(modifier: Modifier, rows: Int, handwritten: Boolean) {
    Column(modifier) {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Glyph(if (i == 0) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, 22.dp)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
                if (handwritten && i < TASKS.size) {
                    Ink(Modifier.weight(1f).fillMaxHeight(), lines = listOf(INK[i % INK.size].take(3)), top = 34.dp, left = 12.dp)
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            Divider(color = Line, thickness = 1.dp)
        }
    }
}

@Composable
private fun Card(title: String, detail: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().border(1.dp, Color.Black, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("  $detail", fontSize = 13.sp, color = Gray)
            Spacer(Modifier.weight(1f))
            Glyph(Icons.Filled.ChevronRight, 22.dp)
        }
        content()
    }
}

@Composable
private fun SectionLabel(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("  $detail", fontSize = 13.sp, color = Gray)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, 24.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 16.sp)
    }
}

@Composable
private fun Chip(text: String) {
    Box(Modifier.border(1.dp, Color.Black, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowScope.Tab(text: String, selected: Boolean) {
    Box(
        Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) Color.Black else Color.White)
            .border(1.dp, Color.Black, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, color = if (selected) Color.White else Color.Black, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun IconRow(vararg icons: ImageVector) {
    icons.forEachIndexed { i, icon ->
        if (i > 0) Spacer(Modifier.width(16.dp))
        Glyph(icon)
    }
}

@Composable
private fun Glyph(icon: ImageVector, size: Dp = 28.dp) = Icon(icon, null, Modifier.size(size), tint = Color.Black)

@Composable
private fun Ink(modifier: Modifier, lines: List<List<Int>> = INK, top: Dp = 56.dp, left: Dp = 24.dp) {
    ComposeCanvas(modifier) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val letter = 11.dp.toPx()
        lines.forEachIndexed { i, words ->
            var x = left.toPx()
            val base = top.toPx() + i * 70.dp.toPx()
            words.forEach { letters ->
                val path = Path().apply { moveTo(x, base) }
                repeat(letters) { k ->
                    path.quadraticBezierTo(x + letter / 2f, base + if (k % 2 == 0) -20.dp.toPx() else 7.dp.toPx(), x + letter, base - 2.dp.toPx())
                    x += letter
                }
                drawPath(path, Color.Black, style = stroke)
                x += 16.dp.toPx()
            }
        }
    }
}
