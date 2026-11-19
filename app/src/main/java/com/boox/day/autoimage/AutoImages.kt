package com.boox.day.autoimage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.boox.day.AppDensity
import com.boox.day.ai.MemoPageContent
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task
import com.boox.day.editor.ui.TASKS_CONTROL_COLUMN_WIDTH
import com.boox.day.share.HandwrittenRows
import com.boox.day.sleepscreen.SleepAgenda
import com.boox.day.sleepscreen.SleepAgendaRenderer
import com.boox.day.sleepscreen.SleepAgendaStyle
import com.boox.day.sleepscreen.SleepScreenContent
import com.boox.day.sleepscreen.SleepScreenPublisher
import com.boox.day.sleepscreen.SleepSizeClass
import com.boox.day.tasks.TaskPagination
import com.boox.day.today.DayDataWatcher
import com.boox.day.today.DayDigestLoader
import com.boox.day.today.LastWrite
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Auto-saved images (Nov 2026): with [AutoImageSettings.notes] or [AutoImageSettings.tasks] on, the memo page
 * opened last (else the one edited last) and today's to-do list are saved as full-screen PNG files in [AutoImageFolder] every time they change.
 *
 * When they are saved, always in an [AutoImageWorker] so it also happens with the app closed:
 * - a moment after another memo page is opened ([memoOpened]),
 * - a few seconds after the day's data changes, or [AutoImageSettings.inkDelaySeconds] after the pen rests ([DayDataWatcher]),
 * - just after midnight (the new day's pages replace yesterday's),
 * - right away when a setting changes.
 *
 * File names are fixed (memo.png, todo-page-1.png, todo-list.png): a file is rewritten only when
 * its picture changed, and the files of pages that are gone are deleted.
 */
object AutoImages {
    private const val TAG = "AutoImages"
    private const val WORK_NOW = "auto_images_now"
    private const val WORK_DAILY = "auto_images_daily"
    private const val PREFS = "auto_images"
    private const val MEMO_FILE = "memo.png"

    /** One file per memo page, before the last edited memo page; still ours so the old files get deleted. */
    private const val OLD_NOTES_PREFIX = "notes-page-"

    /** Recently edited pages looked through for the last memo page: Tasks pages are among them too. */
    private const val RECENT_PAGES = 50
    private const val TASKS_PREFIX = "todo-page-"
    private const val TASKS_LIST = "todo-list.png"
    private const val QUIET_MS = 5_000L

    /** Wait after a memo or tasks page is opened, so scrolling through pages saves only the one it stops on. */
    private const val OPENED_DELAY_MS = 2_000L
    private const val KEY_OPENED_KIND = "opened_kind"
    private const val KIND_MEMO = "memo"
    private const val KIND_TASKS = "tasks"
    private const val KEY_OPENED_MEMO = "opened_memo"
    private const val KEY_SHOWN_MEMO = "shown_memo"
    private const val KEY_OPENED_TASKS_DATE = "opened_tasks_date"
    private const val KEY_OPENED_TASKS_INDEX = "opened_tasks_index"
    private const val KEY_SHOWN_TASKS_DATE = "shown_tasks_date"
    private const val KEY_SHOWN_TASKS_INDEX = "shown_tasks_index"

    /** Ink too far from a row to count as written in it; the app's own rule decides inside. */
    private const val MIN_TASKS_GEOMETRY_PX = 10f

    private val mutex = Mutex()
    private var watcher: DayDataWatcher? = null
    private var watcherInkDelay = 0
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null

    /** When images were last saved, or why the last try failed (the Preferences card shows it). */
    val lastWrite = LastWrite("auto_images_status")

    fun settings(context: Context): AutoImageSettings = SleepAgenda.settings(context).autoImages

    /** BooxDayApp.onCreate. */
    fun onAppStart(context: Context, appScope: CoroutineScope) {
        val app = context.applicationContext
        appContext = app
        scope = appScope
        appScope.launch(Dispatchers.IO) {
            val saved = settings(app)
            if (saved.anyOn) withContext(Dispatchers.Main) { startFollowing(app, saved) }
        }
    }

    /** Preferences: [changed] was just saved. Follows changes while anything is on, and saves now. */
    fun onSettingsChanged(context: Context, changed: AutoImageSettings) {
        val app = context.applicationContext
        if (changed.anyOn) {
            startFollowing(app, changed)
            requestSave(app, force = true)
        } else {
            stopFollowing(app)
            WorkManager.getInstance(app).cancelUniqueWork(WORK_NOW)
        }
    }

    /** The Memos canvas shows [pageId]: memo.png becomes that page. */
    fun memoOpened(context: Context, pageId: String) {
        val prefs = prefs(context)
        if (prefs.getString(KEY_OPENED_MEMO, null) == pageId && prefs.getString(KEY_OPENED_KIND, KIND_MEMO) == KIND_MEMO) return
        prefs.edit()
            .putString(KEY_OPENED_MEMO, pageId)
            .putString(KEY_OPENED_KIND, KIND_MEMO)
            .apply()
        if (settings(context).anyOn) requestSave(context, delayMs = OPENED_DELAY_MS)
    }

    /** The Tasks canvas shows [date] page [pageIndex]: memo.png and todo-page-*.png follow that Tasks page. */
    fun tasksOpened(context: Context, date: String, pageIndex: Int) {
        val prefs = prefs(context)
        if (prefs.getString(KEY_OPENED_TASKS_DATE, null) == date &&
            prefs.getInt(KEY_OPENED_TASKS_INDEX, 0) == pageIndex &&
            prefs.getString(KEY_OPENED_KIND, KIND_MEMO) == KIND_TASKS
        ) return
        prefs.edit()
            .putString(KEY_OPENED_TASKS_DATE, date)
            .putInt(KEY_OPENED_TASKS_INDEX, pageIndex)
            .putString(KEY_OPENED_KIND, KIND_TASKS)
            .apply()
        if (settings(context).anyOn) requestSave(context, delayMs = OPENED_DELAY_MS)
    }

    fun requestSave(context: Context, force: Boolean = false, delayMs: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<AutoImageWorker>()
            .setInputData(workDataOf(AutoImageWorker.KEY_FORCE to force))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Draws today's images and saves those that changed ([force]: all of them). Returns how many files were
     * written. Throws when the folder can't be written to.
     */
    suspend fun saveAll(context: Context, force: Boolean = false): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val saved = SleepAgenda.settings(app)
            val choice = saved.autoImages
            val today = LocalDate.now()
            val (width, height, ppi) = SleepScreenPublisher.panel(app)
            val keep = mutableSetOf<String>()
            var written = 0
            try {
                val openedKind = prefs(app).getString(KEY_OPENED_KIND, KIND_MEMO)
                val preferTasksForMemoFile = (choice.tasks && !choice.notes) || (openedKind == KIND_TASKS)
                if (choice.notes || choice.tasks) {
                    val primaryImage = if (preferTasksForMemoFile) {
                        openedTasksImage(app, saved, today, width, height, ppi)
                            ?: if (choice.notes) (openedMemoImage(app, width, height) ?: lastEditedMemoImage(app, width, height)) else null
                    } else {
                        (openedMemoImage(app, width, height) ?: lastEditedMemoImage(app, width, height))
                            ?: if (choice.tasks) openedTasksImage(app, saved, today, width, height, ppi) else null
                    }
                    primaryImage?.let { image ->
                        keep += MEMO_FILE
                        if (save(app, MEMO_FILE, image, choice, force)) written++
                    }
                }
                if (choice.tasks) {
                    val pages = if (choice.tasksStyle == TasksImageStyle.HANDWRITING) {
                        taskPageImages(app, saved, today, width, height)
                    } else {
                        null
                    }
                    if (pages != null) {
                        pages.forEachIndexed { index, image ->
                            val name = "$TASKS_PREFIX${index + 1}.png"
                            keep += name
                            if (save(app, name, image, choice, force)) written++
                        }
                    } else {
                        val image = taskListImage(app, saved, today, width, height, ppi)
                        keep += TASKS_LIST
                        if (save(app, TASKS_LIST, image, choice, force)) written++
                    }
                }
                AutoImageFolder.deleteOthers(app, keep, ::isOurImage)
                lastWrite.succeeded(app)
            } catch (e: Exception) {
                lastWrite.failed(app, if (e is SecurityException) "the folder can't be written to" else "the images couldn't be saved")
                throw e
            }
            Log.i(TAG, "Saved $written of ${keep.size} images")
            written
        }
    }

    internal fun isOurImage(name: String): Boolean =
        name == MEMO_FILE || name == TASKS_LIST ||
            (name.endsWith(".png") && (name.startsWith(OLD_NOTES_PREFIX) || name.startsWith(TASKS_PREFIX)))

    /**
     * The memo page opened last, or when that page is empty/gone, the last opened memo page that had
     * something on it; null when neither has content.
     */
    private suspend fun openedMemoImage(context: Context, width: Int, height: Int): Bitmap? {
        val prefs = prefs(context)
        val opened = prefs.getString(KEY_OPENED_MEMO, null)
        opened?.let { memoPageImage(context, it, width, height) }?.let { image ->
            prefs.edit().putString(KEY_SHOWN_MEMO, opened).apply()
            return image
        }
        val shown = prefs.getString(KEY_SHOWN_MEMO, null)?.takeIf { it != opened } ?: return null
        return memoPageImage(context, shown, width, height)
    }

    private suspend fun memoPageImage(context: Context, pageId: String, width: Int, height: Int): Bitmap? {
        if (AppDatabase.getDatabase(context).dayPageDao().getDateByPageId(pageId) == null) return null
        val image = AutoImageRenderer.memoPage(context, MemoPageContent.load(context, pageId), width, height) ?: return null
        return toScreen(image, width, height)
    }

    /**
     * The memo page edited last, on any day, the size of the screen; null when no memo page has anything on it.
     * Page.updatedAt follows every edit (PageView); memo pages are the ones a day lists, Tasks pages are not.
     */
    private suspend fun lastEditedMemoImage(context: Context, width: Int, height: Int): Bitmap? {
        val database = AppDatabase.getDatabase(context)
        val dayPages = database.dayPageDao()
        val recent = database.openHelper.readableDatabase
            .query("SELECT id FROM page ORDER BY updatedAt DESC LIMIT $RECENT_PAGES")
            .use { c -> List(c.count) { c.moveToNext(); c.getString(0) } }
        for (pageId in recent) {
            if (dayPages.getDateByPageId(pageId) == null) continue
            val image = AutoImageRenderer.memoPage(context, MemoPageContent.load(context, pageId), width, height) ?: continue
            return toScreen(image, width, height)
        }
        return null
    }

    /** [image] as a full-screen picture: as it is when it already fits the screen exactly, else fitted onto it. */
    private fun toScreen(image: Bitmap, width: Int, height: Int): Bitmap {
        if (image.width == width && image.height == height) return image
        return AutoImageRenderer.fitToScreen(image, width, height).also { image.recycle() }
    }

    /**
     * The Tasks page opened last (or the last non-blank Tasks page shown, else today's first non-blank
     * Tasks page), rendered in the user's chosen style ([TasksImageStyle.HANDWRITING] or [TasksImageStyle.LIST]).
     */
    private suspend fun openedTasksImage(
        context: Context,
        saved: AppSettings,
        today: LocalDate,
        width: Int,
        height: Int,
        ppi: Int
    ): Bitmap? {
        val prefs = prefs(context)
        val openedDate = prefs.getString(KEY_OPENED_TASKS_DATE, null) ?: today.toString()
        val openedIndex = prefs.getInt(KEY_OPENED_TASKS_INDEX, 0)
        if (saved.autoImages.tasksStyle == TasksImageStyle.HANDWRITING) {
            singleTaskPageImage(context, saved, openedDate, openedIndex, width, height)?.let { image ->
                prefs.edit()
                    .putString(KEY_SHOWN_TASKS_DATE, openedDate)
                    .putInt(KEY_SHOWN_TASKS_INDEX, openedIndex)
                    .apply()
                return image
            }
            val shownDate = prefs.getString(KEY_SHOWN_TASKS_DATE, null)
            val shownIndex = prefs.getInt(KEY_SHOWN_TASKS_INDEX, 0)
            if (shownDate != null && (shownDate != openedDate || shownIndex != openedIndex)) {
                singleTaskPageImage(context, saved, shownDate, shownIndex, width, height)?.let { return it }
            }
            taskPageImages(context, saved, today, width, height)?.firstOrNull()?.let { return it }
        }
        val targetDay = runCatching { LocalDate.parse(openedDate) }.getOrDefault(today)
        return taskListImage(context, saved, targetDay, width, height, ppi)
    }

    private suspend fun singleTaskPageImage(
        context: Context,
        saved: AppSettings,
        date: String,
        pageIndex: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        val canvasHeight = saved.tasksInkCanvasHeight
        val topOffset = saved.tasksInkTopOffset
        if (canvasHeight <= topOffset + MIN_TASKS_GEOMETRY_PX) return null
        val database = AppDatabase.getDatabase(context)
        val dao = database.taskDao()
        val handwriting = HandwrittenRows(context, database.openHelper.readableDatabase)
        val rowCount = TaskPagination.rowsPerPage(date)
        val rowsHeight = TaskPagination.rowsHeight(canvasHeight, rowCount, topOffset)
        val columnPx = TASKS_CONTROL_COLUMN_WIDTH.value * AppDensity.dpi / 160f
        val tasks = dao.getTasksForPage(date, pageIndex)
        val pageId = tasks.firstOrNull()?.pageId ?: return null
        val rows = tasks.map { task -> taskRow(task, handwriting.inRow(pageId, date, task.rowIndex)) }
        if (rows.none { it.hasContent }) return null
        val page = AutoImageRenderer.tasksPage(
            context, MemoPageContent.load(context, pageId), rows, rowCount, rowsHeight, topOffset, width, columnPx
        )
        return toScreen(page, width, height)
    }

    /**
     * Today's Tasks pages with something on them, on the row geometry the stored ink belongs to, each the size
     * of the screen. Null before Tasks has been shown once (no geometry yet): the typed list is used instead.
     */
    private suspend fun taskPageImages(context: Context, saved: AppSettings, day: LocalDate, width: Int, height: Int): List<Bitmap>? {
        val canvasHeight = saved.tasksInkCanvasHeight
        val topOffset = saved.tasksInkTopOffset
        if (canvasHeight <= topOffset + MIN_TASKS_GEOMETRY_PX) return null
        val date = day.toString()
        val database = AppDatabase.getDatabase(context)
        val dao = database.taskDao()
        val handwriting = HandwrittenRows(context, database.openHelper.readableDatabase)
        val rowCount = TaskPagination.rowsPerPage(date)
        val rowsHeight = TaskPagination.rowsHeight(canvasHeight, rowCount, topOffset)
        val columnPx = TASKS_CONTROL_COLUMN_WIDTH.value * AppDensity.dpi / 160f
        val lastIndex = dao.getMaxTaskPageIndex(date) ?: return emptyList()
        return (0..lastIndex).mapNotNull { pageIndex ->
            val tasks = dao.getTasksForPage(date, pageIndex)
            val pageId = tasks.firstOrNull()?.pageId ?: return@mapNotNull null
            val rows = tasks.map { task -> taskRow(task, handwriting.inRow(pageId, date, task.rowIndex)) }
            if (rows.none { it.hasContent }) return@mapNotNull null
            val page = AutoImageRenderer.tasksPage(
                context, MemoPageContent.load(context, pageId), rows, rowCount, rowsHeight, topOffset, width, columnPx
            )
            toScreen(page, width, height)
        }
    }

    private fun taskRow(task: Task, hasInk: Boolean): AutoImageRenderer.TaskRow {
        val typed = task.typedText?.takeIf { it.isNotBlank() } ?: task.convertedText?.takeIf { it.isNotBlank() }
        val hasContent = hasInk || task.isCompleted || task.priority.isNotEmpty() || typed != null ||
            !task.recognizedText.isNullOrBlank() || task.parentTaskId != null || task.dueDay != null
        return AutoImageRenderer.TaskRow(
            rowIndex = task.rowIndex,
            hasContent = hasContent,
            completed = task.isCompleted,
            priority = task.priority,
            text = typed.takeIf { !hasInk },
            isSubtask = task.parentTaskId != null,
        )
    }

    /** Today's tasks as a typed list the size of the screen, drawn like the sleep screen. */
    private suspend fun taskListImage(context: Context, saved: AppSettings, day: LocalDate, width: Int, height: Int, ppi: Int): Bitmap {
        val sizeClass = SleepSizeClass.of(minOf(width, height), ppi)
        val digest = DayDigestLoader(context).load(day)
        return SleepAgendaRenderer(SleepAgendaStyle.forClass(sizeClass, ppi)).render(
            SleepAgendaRenderer.Input(
                digest = digest,
                widthPx = width,
                heightPx = height,
                nowMs = System.currentTimeMillis(),
                use24Hour = saved.timeFormat == AppSettings.TimeFormat.TwentyFourHour,
                firstDayOfWeek = saved.firstDayOfWeek.dayOfWeek,
                choice = SleepScreenContent(events = saved.autoImages.listShowsEvents, tasks = true, memo = false, month = false),
            )
        )
    }

    /**
     * Writes [image], with [AutoImageSettings.captionText] along its bottom, as [name] unless the file already
     * holds the same picture, and recycles it. True when written.
     */
    private fun save(context: Context, name: String, image: Bitmap, choice: AutoImageSettings, force: Boolean): Boolean {
        val captioned = AutoImageRenderer.withCaption(image, choice.captionText)
        val png = try {
            encode(captioned)
        } finally {
            captioned.recycle()
        }
        val hash = png.contentHashCode()
        val prefs = prefs(context)
        if (!force && prefs.getInt(hashKey(name), 0) == hash) return false
        AutoImageFolder.write(context, name, png)
        prefs.edit().putInt(hashKey(name), hash).apply()
        return true
    }

    private fun encode(image: Bitmap): ByteArray =
        ByteArrayOutputStream().use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

    private fun startFollowing(context: Context, choice: AutoImageSettings) {
        val app = context.applicationContext
        appContext = app
        if (watcher == null || watcherInkDelay != choice.inkDelaySeconds) {
            watcher?.stop()
            watcherInkDelay = choice.inkDelaySeconds
            watcher = DayDataWatcher(quietMs = QUIET_MS, inkQuietMs = choice.inkDelaySeconds * 1_000L) {
                appContext?.let { requestSave(it) }
            }
        }
        scope?.let { watcher?.start(app, it) }
        scheduleDaily(app)
    }

    private fun stopFollowing(context: Context) {
        watcher?.stop()
        watcher = null
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DAILY)
    }

    /** A job just after midnight every day (WorkManager: BOOX drops AlarmManager alarms). */
    private fun scheduleDaily(context: Context) {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, 2))
        val request = PeriodicWorkRequestBuilder<AutoImageWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun hashKey(name: String) = "hash_$name"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Saves today's images, when the setting is on. */
class AutoImageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AutoImages.settings(applicationContext).anyOn) return Result.success()
        return try {
            AutoImages.saveAll(applicationContext, force = inputData.getBoolean(KEY_FORCE, false))
            Result.success()
        } catch (e: Exception) {
            Log.w("AutoImageWorker", "Images not saved: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}
