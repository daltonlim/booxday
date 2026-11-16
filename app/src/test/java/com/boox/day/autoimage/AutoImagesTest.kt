package com.boox.day.autoimage

import com.boox.day.data.datastore.AppSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoImagesTest {

    @Test
    fun onlyTheAutoSavedFilesAreOurs() {
        assertTrue(AutoImages.isOurImage("memo.png"))
        assertTrue(AutoImages.isOurImage("notes-page-1.png"))
        assertTrue(AutoImages.isOurImage("todo-page-12.png"))
        assertTrue(AutoImages.isOurImage("todo-list.png"))
        assertFalse(AutoImages.isOurImage("notes-page-1.png.tmp"))
        assertFalse(AutoImages.isOurImage("holiday.png"))
        assertFalse(AutoImages.isOurImage("booxday-today-1.png"))
    }

    @Test
    fun settingsSavedBeforeTheFeatureLoadWithItOff() {
        val json = Json { ignoreUnknownKeys = true }
        val settings = json.decodeFromString(AppSettings.serializer(), """{"version":1}""")
        assertEquals(AutoImageSettings(), settings.autoImages)
        assertFalse(settings.autoImages.anyOn)
    }

    @Test
    fun settingsKeepTheirChoices() {
        val json = Json { ignoreUnknownKeys = true }
        val chosen = AutoImageSettings(
            notes = true,
            tasks = true,
            tasksStyle = TasksImageStyle.LIST,
            listShowsEvents = true,
            inkDelaySeconds = 10,
            captionText = "Stay focused",
        )
        val text = json.encodeToString(AppSettings.serializer(), AppSettings(version = 1, autoImages = chosen))
        assertEquals(chosen, json.decodeFromString(AppSettings.serializer(), text).autoImages)
    }
}
