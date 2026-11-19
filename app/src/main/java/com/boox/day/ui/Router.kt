package com.boox.day.ui
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.boox.day.R
import com.boox.day.api.OpenRouterClient
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.editor.DrawCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.boox.day.ui.views.BugReportScreen
import com.boox.day.ui.views.MainScreen
import com.boox.day.ui.views.SettingsView
import com.boox.day.ui.views.WelcomeView
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull


private val logRouter = TaggedLog("Router")
private const val STARTUP_CANVAS_HANDOFF_TIMEOUT_MS = 1800L
/** The longest the splash waits for the canvas's first picture (Nov 2026). */
private const val STARTUP_CANVAS_DRAWN_TIMEOUT_MS = 1000L

/**
 * Main Router for BooxDay
 *
 * Navigation structure:
 * - "main" - Calendar + Drawing canvas (default start)
 * - "welcome" - First-time setup/permissions (if needed)
 * - "settings" - App settings
 * - "bugReport" - Bug reporting
 */
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@Composable
fun Router() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Determine start destination based on first-run status only
    // NOTE: hasFilePermission check removed - MANAGE_EXTERNAL_STORAGE is now optional (Boox Notes only)
    // Database is in internal storage (context.filesDir) and doesn't need this permission
    val startDestination = remember {
        if (GlobalAppSettings.current.showWelcome) {
            "welcome"
        } else {
            "main"
        }
    }
    val shouldWaitForCanvasHandoff = remember(startDestination) {
        startDestination == "main"
    }
    val shouldBypassWelcomeGateForStartup = remember(startDestination) {
        startDestination == "main" && GlobalAppSettings.current.onboardingDismissedAt == 0L
    }

    // Splash screen state
    var showSplash by remember { mutableStateOf(true) }
    var shouldComposeNavHost by remember {
        DrawCanvas.prepareStartupSplash(
            ignoreWelcomePopupUntilCanvasReady = shouldBypassWelcomeGateForStartup
        )
        mutableStateOf(GlobalAppSettings.loaded)
    }

    LaunchedEffect(Unit) {
        if (!shouldComposeNavHost) {
            // Let the splash paint once before composing the heavy navigation tree underneath it.
            withFrameNanos { }
            // Oct 2026: not before the saved settings are in (MainActivity). On defaults the main screen
            // showed a wrong setup badge and its writers could save defaults over the user's settings
            snapshotFlow { GlobalAppSettings.loaded }.first { it }
            shouldComposeNavHost = true
        }
    }

    // Pre-load data during splash screen with adaptive timing
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        logRouter.i("PERF: Splash pre-loading START")

        coroutineScope {
            // Pre-create today's pages if not already preloaded by BooxDayApp
            val pagesJob = launch(Dispatchers.IO) {
                try {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    if (GlobalAppSettings.preloadedDate == today &&
                        GlobalAppSettings.preloadedMemosPageId != null &&
                        GlobalAppSettings.preloadedTasksPageId != null
                    ) {
                        logRouter.i("PERF: Today's pages already preloaded in BooxDayApp")
                        return@launch
                    }
                    val dayPageRepo = DayPageRepository(context)
                    val taskRepo = TaskRepository(context)

                    val memosPageId = dayPageRepo.getOrCreatePageForDate(today)
                    logRouter.i("PERF: Memos page pre-created: $memosPageId")

                    val (tasks, _) = taskRepo.getOrCreateTasksForPage(today, 0)
                    val tasksPageId = tasks.firstOrNull()?.pageId
                    logRouter.i("PERF: Tasks page pre-created: $tasksPageId")

                    if (memosPageId != null && tasksPageId != null) {
                        GlobalAppSettings.setPreloadedPageIds(today, memosPageId, tasksPageId)
                        logRouter.i("PERF: Page IDs stored in GlobalAppSettings")
                    }
                } catch (e: Exception) {
                    logRouter.e("PERF: Page pre-creation failed: ${e.message}")
                }
            }

            // Wait for critical preloads with timeout
            withTimeoutOrNull(2000) {
                pagesJob.join()
            }
        }
        // The main screen appears only after the settings load (see shouldComposeNavHost): keep
        // the splash up until then, never a blank screen
        snapshotFlow { GlobalAppSettings.loaded }.first { it }

        logRouter.i("PERF: Splash pre-loading COMPLETE (${System.currentTimeMillis() - startTime}ms)")
        if (shouldWaitForCanvasHandoff) {
            // A drawing surface shows black until its first picture: the splash stays until the
            // canvas has drawn once (the black Memos flash at launch, Nov 2026).
            withFrameNanos { }
            val drawn = DrawCanvas.awaitStartupCanvasDrawn(STARTUP_CANVAS_DRAWN_TIMEOUT_MS)
            logRouter.i("STARTUP_FRAME: canvas drawn before the splash left: $drawn")
            // Native drawing can freeze normal UI posting on some BOOX firmware.
            // Remove the cover and let the app submit its UI before permitting raw drawing;
            // otherwise a white cover can remain visible even while the pen works (#1).
            showSplash = false
            withFrameNanos { }
            withFrameNanos { }
            logRouter.i("STARTUP_FRAME: splash removed before native drawing activation")

            // Retain the existing raw-drawing settle delay and bounded readiness wait.
            DrawCanvas.notifySplashDismissed()
            val canvasReady = DrawCanvas.awaitStartupCanvasReady(STARTUP_CANVAS_HANDOFF_TIMEOUT_MS)
            if (!canvasReady) {
                logRouter.w("PERF: Startup canvas handoff timed out after ${STARTUP_CANVAS_HANDOFF_TIMEOUT_MS}ms")
            }
        } else {
            DrawCanvas.notifySplashDismissed()
        }
        DrawCanvas.finishStartupSplashHandoff()
        showSplash = false
    }

    Box(Modifier.fillMaxSize()) {
        if (shouldComposeNavHost) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                // Main screen - Calendar + Drawing Canvas
                composable(route = "main") {
                    MainScreen(navController = navController)
                }

                // Welcome/Setup screen (shown on first run or if permissions needed)
                composable(route = "welcome") {
                    WelcomeView(navController = navController)
                }

                // Settings screen
                composable(route = "settings") {
                    SettingsView(navController = navController)
                }

                // Bug report screen
                composable(route = "bugReport") {
                    BugReportScreen(navController = navController)
                }
            }
        }

        // Seamless splash icon cover matching Android's system splash icon until the canvas draws
        AnimatedVisibility(
            visible = showSplash,
            exit = ExitTransition.None
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_icon),
                    contentDescription = "BooxDay Logo",
                    modifier = Modifier.size(192.dp)
                )
            }
        }
    }
}
