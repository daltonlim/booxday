package com.boox.day.data.db

import android.app.Application
import android.graphics.Color.BLACK
import androidx.test.core.app.ApplicationProvider
import com.boox.day.editor.utils.Pen.BALLPEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DayPageMoveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DayPageRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = AppDatabase.getDatabase(context)
        db.clearAllTables()
        repo = DayPageRepository(context)
        repo.invalidatePageCache()
    }

    @Test
    fun movePageToDate_replacesBlankTargetPageAndLeavesFreshSourcePage() {
        val sourceDate = "2026-11-16"
        val targetDate = "2026-11-17"

        val sourcePageId = repo.getOrCreatePageForDate(sourceDate)
        val blankTargetPageId = repo.getOrCreatePageForDate(targetDate)

        db.strokeDao().create(
            Stroke(
                id = UUID.randomUUID().toString(),
                size = 5f,
                pen = BALLPEN,
                color = BLACK,
                top = 0f,
                bottom = 10f,
                left = 0f,
                right = 10f,
                points = listOf(StrokePoint(x = 1f, y = 2f)),
                pageId = sourcePageId
            )
        )

        val remainingSourceId = repo.movePageToDate(sourceDate, targetDate, sourcePageId)
        assertNotNull(remainingSourceId)
        assertTrue(remainingSourceId != sourcePageId)

        val targetDayPage = repo.getByDate(targetDate)
        assertNotNull(targetDayPage)
        assertEquals(listOf(sourcePageId), targetDayPage!!.pageIds)
        assertEquals(sourcePageId, targetDayPage.openPageId)
        assertTrue( blankTargetPageId !in targetDayPage.pageIds)

        val sourceDayPage = repo.getByDate(sourceDate)
        assertNotNull(sourceDayPage)
        assertEquals(listOf(remainingSourceId), sourceDayPage!!.pageIds)
        assertEquals(remainingSourceId, sourceDayPage.openPageId)
    }

    @Test
    fun movePageToDate_appendsWhenTargetAlreadyHasContent() {
        val sourceDate = "2026-11-16"
        val targetDate = "2026-11-17"

        val sourcePage1 = repo.getOrCreatePageForDate(sourceDate)
        val sourcePage2 = repo.createNewPageForDate(sourceDate)
        val targetPage1 = repo.getOrCreatePageForDate(targetDate)

        db.strokeDao().create(
            Stroke(
                id = UUID.randomUUID().toString(),
                size = 5f,
                pen = BALLPEN,
                color = BLACK,
                top = 0f,
                bottom = 10f,
                left = 0f,
                right = 10f,
                points = listOf(StrokePoint(x = 1f, y = 2f)),
                pageId = targetPage1
            )
        )

        val remainingSourceId = repo.movePageToDate(sourceDate, targetDate, sourcePage2)
        assertEquals(sourcePage1, remainingSourceId)

        val targetDayPage = repo.getByDate(targetDate)
        assertNotNull(targetDayPage)
        assertEquals(listOf(targetPage1, sourcePage2), targetDayPage!!.pageIds)
        assertEquals(sourcePage2, targetDayPage.openPageId)

        val sourceDayPage = repo.getByDate(sourceDate)
        assertNotNull(sourceDayPage)
        assertEquals(listOf(sourcePage1), sourceDayPage!!.pageIds)
    }

    @Test
    fun copyPageToDate_duplicatesStrokesAndKeepsSourcePage() {
        val sourceDate = "2026-11-16"
        val targetDate = "2026-11-18"
        val sourcePageId = repo.getOrCreatePageForDate(sourceDate)

        db.strokeDao().create(
            Stroke(
                id = UUID.randomUUID().toString(),
                size = 5f,
                pen = BALLPEN,
                color = BLACK,
                top = 0f,
                bottom = 10f,
                left = 0f,
                right = 10f,
                points = listOf(StrokePoint(x = 1f, y = 2f)),
                pageId = sourcePageId
            )
        )

        assertNull(repo.movePageToDate(sourceDate, sourceDate, sourcePageId))
        assertNull(repo.copyPageToDate(sourceDate, sourceDate, sourcePageId))

        val copiedPageId = repo.copyPageToDate(sourceDate, targetDate, sourcePageId)
        assertNotNull(copiedPageId)
        assertTrue(copiedPageId != sourcePageId)

        val targetDayPage = repo.getByDate(targetDate)
        assertNotNull(targetDayPage)
        assertEquals(listOf(copiedPageId), targetDayPage!!.pageIds)
        assertTrue(repo.pageHasContent(copiedPageId!!))
        assertTrue(repo.pageHasContent(sourcePageId))
    }
}
