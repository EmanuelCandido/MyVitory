package com.myvitory.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.myvitory.app.MyVitoryApp
import com.myvitory.app.R
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.notify.Notifications
import com.myvitory.app.ui.settings.SettingsScreen
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.exercise.ExerciseScreen
import com.myvitory.app.ui.library.LibraryScreen
import com.myvitory.app.ui.recovery.RecoveryScreen
import com.myvitory.app.ui.replan.ReplanScreen
import com.myvitory.app.ui.template.TemplateEditorScreen
import com.myvitory.app.ui.theme.Palette
import com.myvitory.app.ui.today.TodayScreen
import com.myvitory.app.ui.week.WeekScreen
import com.myvitory.app.ui.workout.WorkoutScreen
import kotlinx.coroutines.launch

private object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val RECOVERY = "recovery"
    const val REPLAN = "replan"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val WORKOUT = "workout/{sessionId}"
    const val TEMPLATE = "template/{templateId}"
    const val EXERCISE = "exercise/{exerciseId}?sessionId={sessionId}"
    fun workout(id: Long) = "workout/$id"
    fun template(id: Long) = "template/$id"
    fun exercise(id: String, sessionId: Long? = null) = "exercise/$id" + (sessionId?.let { "?sessionId=$it" } ?: "")
}

private data class Tab(val route: String, val label: String, @param:DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(Routes.TODAY, "Hoje", R.drawable.ic_home),
    Tab(Routes.WEEK, "Semana", R.drawable.ic_calendar),
    Tab(Routes.RECOVERY, "Recuperação", R.drawable.ic_pulse),
    Tab(Routes.LIBRARY, "Exercícios", R.drawable.ic_dumbbell),
)

/** Destinos que uma notificação pode pedir. */
sealed interface AppLink {
    data object Today : AppLink
    data class Workout(val sessionId: Long) : AppLink
    /** "Não treinei" na pergunta do dia seguinte: marca e abre a nova semana. */
    data class Missed(val sessionId: Long) : AppLink
}

@Composable
fun MyVitoryRoot(link: AppLink? = null, onLinkHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undo = remember {
        UndoMessenger { message, onUndo ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = message,
                    actionLabel = if (onUndo != null) "Desfazer" else null,
                    duration = if (onUndo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) onUndo?.invoke()
            }
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == null || tabs.any { it.route == route }

    LaunchedEffect(link) {
        when (link) {
            null -> return@LaunchedEffect
            AppLink.Today -> nav.navigate(Routes.TODAY) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
            is AppLink.Workout -> nav.navigate(Routes.workout(link.sessionId)) { launchSingleTop = true }
            is AppLink.Missed -> {
                val training = (context.applicationContext as MyVitoryApp).container.training
                val undoMark = training.markSession(link.sessionId, SessionStatus.MISSED)
                Notifications.cancel(context, Notifications.ID_YESTERDAY)
                if (undoMark != null) {
                    undo.show("Marcado como não treinado") { scope.launch { training.undoMark(undoMark) } }
                    nav.navigate(Routes.REPLAN) { launchSingleTop = true }
                }
            }
        }
        onLinkHandled()
    }

    CompositionLocalProvider(LocalUndo provides undo) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Palette.Ground,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(data, containerColor = Palette.Text, contentColor = Palette.Ground, actionColor = Palette.Ground)
                }
            },
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = Palette.Surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { AppIcon(tab.icon, null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Palette.Ground,
                                    selectedTextColor = Palette.Text,
                                    indicatorColor = Palette.Lime,
                                    unselectedIconColor = Palette.TextMuted,
                                    unselectedTextColor = Palette.TextMuted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Routes.TODAY) {
                composable(Routes.TODAY) {
                    TodayScreen(
                        contentPadding = padding,
                        onOpenWorkout = { nav.navigate(Routes.workout(it)) },
                        onOpenReplan = { nav.navigate(Routes.REPLAN) { launchSingleTop = true } },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onOpenWeek = {
                            nav.navigate(Routes.WEEK) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Routes.WEEK) {
                    WeekScreen(
                        contentPadding = padding,
                        onOpenWorkout = { nav.navigate(Routes.workout(it)) },
                        onOpenTemplate = { nav.navigate(Routes.template(it)) },
                        onOpenReplan = { nav.navigate(Routes.REPLAN) { launchSingleTop = true } },
                    )
                }
                composable(Routes.RECOVERY) {
                    RecoveryScreen(contentPadding = padding)
                }
                composable(Routes.REPLAN) {
                    ReplanScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(contentPadding = padding, onOpenExercise = { nav.navigate(Routes.exercise(it)) })
                }
                composable(
                    Routes.EXERCISE,
                    arguments = listOf(
                        navArgument("exerciseId") { type = NavType.StringType },
                        navArgument("sessionId") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { entry ->
                    val args = entry.arguments!!
                    ExerciseScreen(
                        exerciseId = args.getString("exerciseId")!!,
                        sessionId = args.getLong("sessionId").takeIf { it > 0 },
                        onBack = { nav.popBackStack() },
                        // Parecidos abrem só para consulta (sem troca), por cima desta tela.
                        onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                    )
                }
                composable(Routes.WORKOUT, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                    val sessionId = entry.arguments!!.getLong("sessionId")
                    WorkoutScreen(
                        sessionId,
                        onClose = { nav.popBackStack() },
                        onOpenExercise = { exerciseId -> nav.navigate(Routes.exercise(exerciseId, sessionId)) },
                    )
                }
                composable(Routes.TEMPLATE, arguments = listOf(navArgument("templateId") { type = NavType.LongType })) { entry ->
                    TemplateEditorScreen(entry.arguments!!.getLong("templateId"), onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
