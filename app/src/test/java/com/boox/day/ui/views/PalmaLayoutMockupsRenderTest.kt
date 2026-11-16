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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Undo
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
@Config(sdk = [35], application = Application::class, qualifiers = "w900dp-h1800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PalmaLayoutMockupsRenderTest {

    private val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
        "0-now" to { Now() },
        "a-week-strip" to { WeekStrip() },
        "b-writing-first" to { WritingFirst(calendarOpen = false) },
        "b-writing-first-calendar-open" to { WritingFirst(calendarOpen = true) },
        "c-stacked" to { Stacked() },
    )

    @Test
    fun renderMockups() {
        val out = File("build/palma-mockups").apply { mkdirs() }
        val images = screens.map { (name, screen) ->
            var bounds = Rect.Zero
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(PALMA_DENSITY, 1f)) {
                    Box(
                        Modifier.size(SCREEN_W, SCREEN_H).background(Color.White).onGloballyPositioned { bounds = it.boundsInRoot() }
                    ) { screen() }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.findViewById<View>(android.R.id.content)
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(full))
            val image = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            File(out, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name to image
        }
        writeOverview(out, images)
    }

    private fun writeOverview(out: File, images: List<Pair<String, Bitmap>>) {
        val scale = 0.5f
        val gap = 40
        val label = 70
        val w = (images.first().second.width * scale).toInt()
        val h = (images.first().second.height * scale).toInt()
        val sheet = Bitmap.createBitmap(images.size * (w + gap) + gap, h + label + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(android.graphics.Color.rgb(230, 230, 230))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = Typeface.DEFAULT_BOLD }
        val titles = listOf("Now", "A · Week strip", "B · Writing first", "B · Month opened", "C · Stacked")
        images.forEachIndexed { i, (_, image) ->
            val left = gap + i * (w + gap)
            canvas.drawText(titles[i], left.toFloat(), 48f, text)
            canvas.drawBitmap(image, null, android.graphics.Rect(left, label, left + w, label + h), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        File(out, "overview.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val PALMA_DENSITY = 300 / 160f
        val SCREEN_W = 439.5.dp
        val SCREEN_H = 879.dp
    }
}

private val Gray = Color(0xFF8A8A8A)
private val Line = Color(0xFFCFCFCF)
private const val TODAY = 16
private val EVENT_DAYS = setOf(3, 10, 16, 18, 20, 24)
private val EVENTS = listOf("9:30" to "Stand-up", "14:00" to "Dentist")
private val INK = listOf(listOf(5, 2, 4, 3), listOf(3, 6, 2), listOf(4, 3, 5, 2, 3), listOf(6, 2, 4))

@Composable
private fun Now() = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Icons.Filled.ChevronLeft)
        Text("Nov 2026", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Glyph(Icons.Filled.ChevronRight)
        Spacer(Modifier.weight(1f))
        Glyph(Icons.Outlined.Today)
        Spacer(Modifier.width(14.dp))
        Glyph(Icons.Filled.Add)
        Spacer(Modifier.width(14.dp))
        Glyph(Icons.Filled.MoreVert)
    }
    Row(Modifier.fillMaxWidth().height(290.dp)) {
        Column(Modifier.weight(0.56f)) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("16", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(" Monday", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(Modifier.border(1.5.dp, Color.Black, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("Week", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            MonthGrid(cell = 40.dp, numberSize = 15.sp, dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
        Column(Modifier.weight(0.44f).padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Tab("Events", selected = true, Modifier.weight(1f))
                Tab("Due", selected = false, Modifier.weight(1f))
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
private fun WeekStrip() = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Monday 16", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("November 2026", fontSize = 13.sp, color = Gray)
        }
        Spacer(Modifier.weight(1f))
        Glyph(Icons.Outlined.Today)
        Spacer(Modifier.width(16.dp))
        Glyph(Icons.Filled.Add)
        Spacer(Modifier.width(16.dp))
        Glyph(Icons.Filled.MoreVert)
    }
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
    Divider(color = Color.Black, thickness = 1.dp)
    Toolbar(showPages = true)
    Ink(Modifier.fillMaxWidth().weight(1f))
}

@Composable
private fun WritingFirst(calendarOpen: Boolean) = Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mon 16 Nov", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("  ·  2 events", fontSize = 14.sp, color = Gray)
        Spacer(Modifier.weight(1f))
        Glyph(if (calendarOpen) Icons.Filled.KeyboardArrowUp else Icons.Outlined.CalendarMonth)
        Spacer(Modifier.width(14.dp))
        Glyph(Icons.Filled.MoreVert)
    }
    Divider(color = Color.Black, thickness = 1.dp)
    if (calendarOpen) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(Icons.Filled.ChevronLeft)
                Spacer(Modifier.weight(1f))
                Text("November 2026", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Glyph(Icons.Filled.ChevronRight)
            }
            MonthGrid(cell = 46.dp, numberSize = 17.sp, dayNames = listOf("S", "M", "T", "W", "T", "F", "S"))
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
        Glyph(Icons.Outlined.Today)
        Spacer(Modifier.width(16.dp))
        Glyph(Icons.Filled.Add)
        Spacer(Modifier.width(16.dp))
        Glyph(Icons.Filled.MoreVert)
    }
    Box(Modifier.padding(horizontal = 8.dp)) {
        MonthGrid(cell = 34.dp, numberSize = 15.sp, dayNames = listOf("S", "M", "T", "W", "T", "F", "S"))
    }
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
private fun Toolbar(showPages: Boolean, height: Dp = 50.dp, undo: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(150.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).border(1.5.dp, Color.Black, RoundedCornerShape(8.dp))
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("Memos", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Text("Tasks", fontSize = 15.sp) }
        }
        Spacer(Modifier.width(14.dp))
        Glyph(Icons.Outlined.Draw)
        Spacer(Modifier.width(14.dp))
        Glyph(Icons.Outlined.AutoFixNormal)
        if (undo) {
            Spacer(Modifier.width(14.dp))
            Glyph(Icons.Outlined.Undo)
        }
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
private fun MonthGrid(cell: Dp, numberSize: TextUnit, dayNames: List<String>) = Column {
    Row(Modifier.fillMaxWidth()) {
        dayNames.forEachIndexed { i, name ->
            Text(
                name, Modifier.weight(1f).padding(vertical = 4.dp), fontSize = 12.sp,
                color = if (i == 1) Color.Black else Gray, fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
private fun DayNumber(day: Int, inMonth: Boolean, size: Dp, fontSize: TextUnit) {
    val today = inMonth && day == TODAY
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(if (today) Color.Black else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$day", fontSize = fontSize,
            color = when {
                today -> Color.White
                inMonth -> Color.Black
                else -> Color(0xFFBBBBBB)
            },
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal
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
private fun RowScope.Tab(text: String, selected: Boolean, modifier: Modifier) {
    Box(
        modifier.height(30.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) Color.Black else Color.White)
            .border(1.dp, Color.Black, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, color = if (selected) Color.White else Color.Black, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Glyph(icon: ImageVector, size: Dp = 28.dp) = Icon(icon, null, Modifier.size(size), tint = Color.Black)

@Composable
private fun ColumnScope.Ink(modifier: Modifier, lines: List<List<Int>> = INK) {
    ComposeCanvas(modifier) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val letter = 11.dp.toPx()
        lines.forEachIndexed { i, words ->
            var x = 24.dp.toPx()
            val base = 56.dp.toPx() + i * 70.dp.toPx()
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
