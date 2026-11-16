package com.boox.day.ui.calendar

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ways the pocket (Palma) calendar header could look, over the week strip. Writes build/palma-mockups/header-ideas.png. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w439dp-h2000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PocketHeaderMockupsRenderTest {

    private val calendarThin = strokeIcon("calendar_today_thin")
    private val gray = Color(0xFF777777)
    private val hairline = Color(0xFFBBBBBB)

    @Test
    fun renderHeaderIdeas() = render("header-ideas.png") {
        Column(Modifier.width(439.dp).background(Color(0xFFF2F2F2))) {
            Idea("0  Now", "Big arrows far from the title, a heavy 16 box, the ⋮ badge cut by the edge, no line before the days") { Now() }
            Idea("1  Quiet", "Year in gray, arrows sized like the text, a light today button, the badge as a dot, a hairline under") { Quiet() }
            Idea("2  Title left, arrows right", "The month reads first; ‹ › paired at the right like the page arrows on the bottom bar") { TitleLeft() }
            Idea("3  Week | Month switch", "The view is a switch like Memos | Tasks instead of a mystery tap on 16; hold the title for today") { ViewSwitch() }
            Idea("4  The day first", "The selected day is the title, the week number under it; ‹ › step a week, Today shows when away") { DayFirst() }
            Idea("5  Centred", "‹ Nov 2026 › in the middle, today on the left, ⋮ on the right: symmetric and calm") { Centred() }
        }
    }

    @Composable
    private fun Now() {
        Header(height = 60.dp, line = false) {
            Icon(Icons.Filled.KeyboardArrowLeft, null, modifier = Modifier.padding(6.dp).size(32.dp))
            Text("Nov 2026", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            Icon(Icons.Filled.KeyboardArrowRight, null, modifier = Modifier.padding(6.dp).size(32.dp))
            Spring()
            TodayIcon(size = 34.dp, fontSize = 13.sp)
            Spacer(Modifier.width(18.dp))
            Box(Modifier.size(53.dp)) {
                Icon(Icons.Filled.MoreVert, null, modifier = Modifier.size(34.dp).align(Alignment.Center))
                NumberBadge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp))
            }
        }
    }

    @Composable
    private fun Quiet() {
        Header {
            Icon(Icons.Filled.ChevronLeft, null, modifier = Modifier.padding(horizontal = 6.dp).size(26.dp))
            Title()
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.padding(horizontal = 6.dp).size(26.dp))
            Spring()
            TodayIcon(size = 28.dp, fontSize = 11.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.width(14.dp))
            More()
        }
    }

    @Composable
    private fun TitleLeft() {
        Header {
            Spacer(Modifier.width(10.dp))
            Title(fontSize = 22.sp)
            Spring()
            PairedArrows()
            Spacer(Modifier.width(10.dp))
            TodayIcon(size = 28.dp, fontSize = 11.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            More()
        }
    }

    @Composable
    private fun ViewSwitch() {
        Header {
            Spacer(Modifier.width(10.dp))
            Title()
            Icon(Icons.Filled.ArrowDropDown, null, tint = gray, modifier = Modifier.size(22.dp))
            Spring()
            Row(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Color.Black, RoundedCornerShape(16.dp))
            ) {
                Segment(Icons.Outlined.CalendarViewWeek, "Week", open = true)
                Segment(Icons.Outlined.CalendarViewMonth, "Month", open = false)
            }
            Spacer(Modifier.width(4.dp))
            More()
        }
    }

    @Composable
    private fun DayFirst() {
        Header(height = 56.dp) {
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Mon 16 Nov", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Week 47 · 2026", fontSize = 12.sp, color = gray)
            }
            Spring()
            Box(
                Modifier.height(30.dp).border(1.dp, Color.Black, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("Today", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(8.dp))
            PairedArrows()
            More()
        }
    }

    @Composable
    private fun Centred() {
        Header {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                TodayIcon(size = 28.dp, fontSize = 11.sp, weight = FontWeight.SemiBold)
            }
            Spring()
            Icon(Icons.Filled.ChevronLeft, null, modifier = Modifier.padding(horizontal = 8.dp).size(26.dp))
            Title()
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.padding(horizontal = 8.dp).size(26.dp))
            Spring()
            More()
        }
    }

    @Composable
    private fun Title(fontSize: TextUnit = 20.sp) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("November") }
                withStyle(SpanStyle(color = gray, fontWeight = FontWeight.Normal)) { append(" 2026") }
            },
            fontSize = fontSize,
            maxLines = 1
        )
    }

    @Composable
    private fun PairedArrows() {
        Row(
            Modifier.height(32.dp).border(1.dp, hairline, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ChevronLeft, null, modifier = Modifier.padding(horizontal = 8.dp).size(22.dp))
            Box(Modifier.width(1.dp).height(18.dp).background(hairline))
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.padding(horizontal = 8.dp).size(22.dp))
        }
    }

    @Composable
    private fun RowScope.Segment(icon: ImageVector, label: String, open: Boolean) {
        Row(
            Modifier.fillMaxHeight().background(if (open) Color.Black else Color.White).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tint = if (open) Color.White else Color.Black
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = tint, fontSize = 13.sp, fontWeight = if (open) FontWeight.Bold else FontWeight.Normal)
        }
    }

    @Composable
    private fun TodayIcon(size: Dp, fontSize: TextUnit, weight: FontWeight = FontWeight.Bold) {
        Box(Modifier.size(size + 10.dp), contentAlignment = Alignment.Center) {
            Icon(calendarThin, null, modifier = Modifier.size(size))
            Text("16", fontSize = fontSize, fontWeight = weight, modifier = Modifier.padding(top = size * 0.2f))
        }
    }

    @Composable
    private fun More() {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, null, modifier = Modifier.size(26.dp))
            Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 11.dp).size(7.dp).background(Color.Black, CircleShape))
        }
    }

    @Composable
    private fun NumberBadge(modifier: Modifier) {
        Box(modifier.size(18.dp).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) {
            Text("1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun Header(height: Dp = 50.dp, line: Boolean = true, content: @Composable RowScope.() -> Unit) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(height).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
            if (line) Divider(color = hairline, thickness = 0.5.dp)
            WeekStrip(top = if (line) 6.dp else 0.dp)
        }
    }

    @Composable
    private fun WeekStrip(top: Dp) {
        Column(Modifier.fillMaxWidth().padding(top = top, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, fontSize = 12.sp, color = gray, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                (15..21).forEach { day ->
                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                        val selected = day == 16
                        Box(
                            Modifier.size(32.dp).background(if (selected) Color.Black else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$day", fontSize = 16.sp, color = if (selected) Color.White else Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Idea(title: String, note: String, bar: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            Text(note, fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                Box(Modifier.fillMaxWidth().height(10.dp).background(Color.Black))
                bar()
            }
        }
    }

    @Composable
    private fun RowScope.Spring() = Spacer(Modifier.weight(1f))

    private fun strokeIcon(name: String): ImageVector {
        val xml = File("src/main/res/drawable/$name.xml").readText()
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        Regex("android:pathData=\"([^\"]+)\"").findAll(xml).forEach {
            builder.addPath(
                pathData = PathParser().parsePathString(it.groupValues[1]).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f
            )
        }
        return builder.build()
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var bounds = Rect.Zero
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(300 / 160f)) {
                Box(Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) { content() }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root: View = activity.window.decorView.rootView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(full))
        val crop = Bitmap.createBitmap(
            full,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt().coerceAtMost(full.width - bounds.left.toInt()),
            bounds.height.toInt().coerceAtMost(full.height - bounds.top.toInt())
        )
        File(File("build/palma-mockups").apply { mkdirs() }, name).outputStream().use {
            crop.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
