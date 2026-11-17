package com.boox.day.autoimage

import kotlinx.serialization.Serializable

/**
 * Auto-saved images (Nov 2026, [AutoImages]): today's memo pages and to-do list kept as PNG files that
 * follow every change, for a photo frame app, a synced folder or setting as a wallpaper. Off until turned on.
 */
@Serializable
data class AutoImageSettings(
    /** One PNG of the memo page opened last (on a blank page, the memo shown before). */
    val notes: Boolean = false,
    /** Today's to-do list, drawn as [tasksStyle]. */
    val tasks: Boolean = false,
    val tasksStyle: TasksImageStyle = TasksImageStyle.HANDWRITING,
    /** Typed list only: today's events above the tasks. */
    val listShowsEvents: Boolean = false,
    /** How long the pen rests before ink is saved; other changes are saved after a few seconds. */
    val inkDelaySeconds: Int = 30,
    /** Drawn in white on a black band along the bottom of every image; blank: no band. */
    val captionText: String = "",
) {
    val anyOn: Boolean get() = notes || tasks

    companion object {
        val INK_DELAY_CHOICES = listOf(1, 10, 30, 60)
    }
}

@Serializable
enum class TasksImageStyle {
    /** The Tasks pages as written: ink, typed text, checkboxes and priorities, one PNG per page. */
    HANDWRITING,
    /** A typed list of the tasks (handwriting shows once it has been read), one PNG the size of the screen. */
    LIST,
}