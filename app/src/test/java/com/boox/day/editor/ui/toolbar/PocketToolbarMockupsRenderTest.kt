package com.boox.day.editor.ui.toolbar

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

/** Ways the pocket (Palma) editor toolbar could look, drawn with the app's own icons. Writes build/palma-mockups/toolbar-*.png. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w439dp-h1600dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PocketToolbarMockupsRenderTest {

    private val pen = appIcon("stylus_pen_40px")
    private val eraser = appIcon("ink_eraser_40px")
    private val undo = appIcon("undo_40px")
    private val redo = appIcon("redo_40px")
    private val menu = appIcon("menu_40px")

    @Test
    fun renderToolbarIdeas() = render("toolbar-ideas.png") {
        Column(Modifier.width(439.dp).background(Color(0xFFF2F2F2))) {
            Idea("0  Today", "Heavy outline on the switch, a black box on the pen, thick line above and thin below") { Current() }
            Idea("1  Quiet", "Hairlines above and below, a light switch, the pen marked by a bar under it") { Quiet() }
            Idea("2  Underline tabs", "Memos and Tasks as words with a bar under the open one; no boxes at all") { UnderlineTabs() }
            Idea("3  Icon switch", "The switch as two icons, which leaves room for redo") { IconSwitch() }
            Idea("4  Groups", "Drawing tools in one capsule and the pages in another") { Groups() }
            Idea("5  Pages only when needed", "No ‹ 1/1 › on a one-page day; with more pages a small 2/3") { NoPagesWhenOne() }
            Idea("6  Floating bar", "The bar as an outlined pill, inset from the edges") { Floating() }
        }
    }

    @Test
    fun renderToolbarFixes() = render("toolbar-fixes.png") {
        Column(Modifier.width(439.dp).background(Color(0xFFF2F2F2))) {
            Idea("Now", "Uneven gaps, the pen and eraser sit low (triangles, bar), a heavy switch, ‹ 1/1 › on a one-page day") { Now() }
            Idea("Fixed: one page", "Even gaps, one centre line, no triangles, a 32 dp switch; redo where the pages were") {
                Fixed(pages = null)
            }
            Idea("Fixed: three pages", "The pages come back as a small 2/3 › only when there is more than one") {
                Fixed(pages = "2/3")
            }
            Idea("Fixed, outlined switch", "The open tab in bold on light gray instead of solid black: the lightest bar") {
                Fixed(pages = null, outlinedSwitch = true)
            }
        }
    }

    @Composable
    private fun Now() {
        Divider(color = Color(0xFF555555), thickness = 1.dp)
        BarRow(Modifier.padding(horizontal = 3.dp)) {
            Switch(outline = 1.dp)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(44.dp)) {
                Icon(pen, null, modifier = Modifier.size(26.dp).align(Alignment.Center))
                Triangle(Modifier.align(Alignment.BottomEnd))
                Box(Modifier.align(Alignment.BottomCenter).width(24.dp).height(3.dp).background(Color.Black))
            }
            Spacer(Modifier.width(24.dp))
            Box(Modifier.size(44.dp)) {
                Icon(eraser, null, modifier = Modifier.size(26.dp).align(Alignment.Center))
                Triangle(Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.width(20.dp))
            Tool(undo)
            Spring()
            Pages()
            Spring()
            Tool(menu)
        }
        Divider(color = Color(0xFF555555), thickness = 1.dp)
    }

    @Composable
    private fun Fixed(pages: String?, outlinedSwitch: Boolean = false) {
        Divider(color = Color(0xFF555555), thickness = 1.dp)
        BarRow(Modifier.padding(horizontal = 10.dp)) {
            SlimSwitch(outlined = outlinedSwitch)
            Spring()
            Tool(pen, active = ActiveMark.Bar, iconSize = 24.dp)
            Spring()
            Tool(eraser, iconSize = 24.dp)
            Spring()
            Tool(undo, iconSize = 24.dp)
            Spring()
            if (pages == null) {
                Tool(redo, iconSize = 24.dp)
            } else {
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pages, fontSize = 14.sp, color = Color.Black)
                    Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(24.dp))
                }
            }
            Spring()
            Tool(menu, iconSize = 24.dp)
        }
        Divider(color = Color(0xFF555555), thickness = 1.dp)
    }

    @Composable
    private fun SlimSwitch(outlined: Boolean) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .width(116.dp)
                .height(32.dp)
                .clip(shape)
                .border(1.dp, if (outlined) Color(0xFF777777) else Color.Black, shape)
        ) {
            listOf("Memos" to true, "Tasks" to false).forEach { (label, open) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            when {
                                !open -> Color.White
                                outlined -> Color(0xFFDDDDDD)
                                else -> Color.Black
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
                        color = if (open && !outlined) Color.White else Color.Black
                    )
                }
            }
        }
    }

    @Composable
    private fun Triangle(modifier: Modifier) {
        Box(modifier.padding(end = 6.dp, bottom = 8.dp).size(5.dp).background(Color(0xFF444444)))
    }

    @Composable
    private fun Idea(title: String, note: String, bar: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            Text(note, fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                Spacer(Modifier.height(10.dp))
                bar()
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    @Composable
    private fun Current() {
        Divider(color = Color.Black, thickness = 2.dp)
        BarRow(Modifier.padding(horizontal = 4.dp)) {
            Switch(outline = 1.5.dp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                Icon(pen, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spring()
            Tool(eraser)
            Spring()
            Tool(undo)
            Spring()
            Pages()
            Spring()
            Tool(menu)
        }
        Divider(color = Color.Black, thickness = 1.dp)
    }

    @Composable
    private fun Quiet() {
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
        BarRow(Modifier.padding(horizontal = 8.dp)) {
            Switch(outline = 1.dp)
            Spring()
            Tool(pen, active = ActiveMark.Bar)
            Spring()
            Tool(eraser)
            Spring()
            Tool(undo)
            Spring()
            Pages(light = true)
            Spring()
            Tool(menu)
        }
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
    }

    @Composable
    private fun UnderlineTabs() {
        BarRow(Modifier.padding(horizontal = 8.dp)) {
            TextTab("Memos", open = true)
            Spacer(Modifier.width(14.dp))
            TextTab("Tasks", open = false)
            Spring()
            Tool(pen, active = ActiveMark.Bar)
            Tool(eraser)
            Tool(undo)
            Spacer(Modifier.width(4.dp))
            Pages(light = true)
            Tool(menu)
        }
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
    }

    @Composable
    private fun IconSwitch() {
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
        BarRow(Modifier.padding(horizontal = 8.dp)) {
            Row(
                Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).border(1.dp, Color.Black, RoundedCornerShape(19.dp))
            ) {
                Segment(Icons.Outlined.Description, open = true)
                Segment(Icons.Outlined.Checklist, open = false)
            }
            Spring()
            Tool(pen, active = ActiveMark.Circle)
            Spring()
            Tool(eraser)
            Spring()
            Tool(undo)
            Spring()
            Tool(redo)
            Spring()
            Pages(light = true)
            Spring()
            Tool(menu)
        }
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
    }

    @Composable
    private fun Groups() {
        BarRow(Modifier.padding(horizontal = 8.dp)) {
            Switch(outline = 1.dp, width = 112.dp)
            Spring()
            Row(
                Modifier.height(40.dp).border(1.dp, Color(0xFF999999), RoundedCornerShape(20.dp)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tool(pen, active = ActiveMark.Circle, size = 36.dp)
                Tool(eraser, size = 36.dp)
                Tool(undo, size = 36.dp)
            }
            Spring()
            Row(
                Modifier.height(40.dp).border(1.dp, Color(0xFF999999), RoundedCornerShape(20.dp)).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pages(light = true)
            }
            Spring()
            Tool(menu)
        }
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
    }

    @Composable
    private fun NoPagesWhenOne() {
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
        BarRow(Modifier.padding(horizontal = 8.dp)) {
            Switch(outline = 1.dp)
            Spring()
            Tool(pen, active = ActiveMark.Bar)
            Spring()
            Tool(eraser)
            Spring()
            Tool(undo)
            Spring()
            Tool(redo)
            Spring()
            Tool(menu)
        }
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
        Text("with three pages:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
        BarRow(Modifier.padding(horizontal = 8.dp)) {
            Switch(outline = 1.dp, width = 140.dp, memosLabel = "Memos 2/3")
            Spring()
            Tool(pen, active = ActiveMark.Bar)
            Spring()
            Tool(eraser)
            Spring()
            Tool(undo)
            Spring()
            Tool(Icons.Filled.ChevronRight)
            Spring()
            Tool(menu)
        }
        Divider(color = Color(0xFFBBBBBB), thickness = 1.dp)
    }

    @Composable
    private fun Floating() {
        BarRow(
            Modifier
                .padding(horizontal = 8.dp)
                .border(1.5.dp, Color.Black, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp)
        ) {
            Switch(outline = 0.dp, pill = true)
            Spring()
            Tool(pen, active = ActiveMark.Circle)
            Spring()
            Tool(eraser)
            Spring()
            Tool(undo)
            Spring()
            Pages(light = true)
            Spring()
            Tool(menu)
        }
    }

    private enum class ActiveMark { None, Bar, Circle }

    @Composable
    private fun BarRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).then(modifier),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    @Composable
    private fun RowScope.Spring() = Spacer(Modifier.weight(1f))

    @Composable
    private fun Tool(icon: ImageVector, active: ActiveMark = ActiveMark.None, size: Dp = 44.dp, iconSize: Dp = 26.dp) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (active == ActiveMark.Circle) Box(Modifier.size(size - 4.dp).clip(CircleShape).background(Color.Black))
            Icon(icon, null, tint = if (active == ActiveMark.Circle) Color.White else Color.Black, modifier = Modifier.size(iconSize))
            if (active == ActiveMark.Bar) {
                Box(Modifier.align(Alignment.BottomCenter).width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.Black))
            }
        }
    }

    @Composable
    private fun Switch(outline: Dp, width: Dp = 122.dp, pill: Boolean = false, memosLabel: String = "Memos") {
        val shape = RoundedCornerShape(if (pill) 19.dp else 8.dp)
        Row(
            Modifier
                .width(width)
                .height(38.dp)
                .clip(shape)
                .then(if (outline > 0.dp) Modifier.border(outline, Color.Black, shape) else Modifier.border(1.dp, Color(0xFFBBBBBB), shape))
        ) {
            Box(Modifier.weight(if (memosLabel == "Memos") 1f else 1.4f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(memosLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("Tasks", fontSize = 14.sp, maxLines = 1)
            }
        }
    }

    @Composable
    private fun RowScope.Segment(icon: ImageVector, open: Boolean) {
        Box(Modifier.width(46.dp).fillMaxHeight().background(if (open) Color.Black else Color.White), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (open) Color.White else Color.Black, modifier = Modifier.size(22.dp))
        }
    }

    @Composable
    private fun TextTab(label: String, open: Boolean) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
                color = if (open) Color.Black else Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(44.dp).height(3.dp).background(if (open) Color.Black else Color.Transparent))
        }
    }

    @Composable
    private fun Pages(light: Boolean = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val tint = if (light) Color.Gray else Color.Black
            Icon(Icons.Filled.ChevronLeft, null, tint = tint, modifier = Modifier.size(26.dp))
            Text("1/1", fontSize = 14.sp, color = Color.Gray)
            Icon(Icons.Filled.ChevronRight, null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }

    private fun appIcon(name: String): ImageVector {
        val xml = File("src/main/res/drawable/$name.xml").readText()
        fun attr(key: String) = Regex("android:$key=\"([^\"]+)\"").find(xml)!!.groupValues[1]
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = attr("viewportWidth").toFloat(),
            viewportHeight = attr("viewportHeight").toFloat()
        )
        Regex("android:pathData=\"([^\"]+)\"").findAll(xml).forEach {
            builder.addPath(
                pathData = PathParser().parsePathString(it.groupValues[1]).toNodes(),
                pathFillType = PathFillType.NonZero,
                fill = SolidColor(Color.Black)
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
