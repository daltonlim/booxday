package com.boox.day.autoimage

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionSampleRenderTest {

    @Test
    fun renderSamples() {
        render("caption-short.png", "Stay focused — one thing at a time")
        render(
            "caption-long.png",
            "Ship the Palma build, call the dentist before 5, buy milk and eggs on the way home, " +
                "and remember that done is better than perfect when the week gets busy"
        )
    }

    private fun render(name: String, caption: String) {
        val page = Bitmap.createBitmap(824, 1648, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }
        val widths = listOf(520f, 640f, 380f, 600f, 460f, 700f, 300f, 560f, 420f, 650f, 500f, 350f)
        widths.forEachIndexed { line, width ->
            val baseline = 120f + line * 115f
            val path = Path().apply {
                var x = 60f
                moveTo(x, baseline)
                var up = true
                while (x < 60f + width) {
                    quadTo(x + 11f, baseline + if (up) -38f else 14f, x + 22f, baseline - 6f)
                    x += 22f
                    up = !up
                    if ((x.toInt() / 22) % 7 == 0) {
                        x += 26f
                        moveTo(x, baseline)
                    }
                }
            }
            canvas.drawPath(path, ink)
        }
        val result = AutoImageRenderer.withCaption(page, caption)
        File("build/caption-samples").apply { mkdirs() }.resolve(name).outputStream().use {
            result.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
