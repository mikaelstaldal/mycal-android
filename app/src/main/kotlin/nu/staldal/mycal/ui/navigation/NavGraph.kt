package nu.staldal.mycal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nu.staldal.mycal.ui.calendar.CalendarScreen
import nu.staldal.mycal.ui.event.EventDetailScreen
import nu.staldal.mycal.ui.event.EventFormScreen
import nu.staldal.mycal.ui.settings.SettingsScreen

@Composable
fun NavGraph(
    forceScheduleView: Boolean = false,
    openNewEvent: Boolean = false,
    viewEventId: Long? = null,
) {
    val navController = rememberNavController()

    LaunchedEffect(openNewEvent) {
        if (openNewEvent) {
            navController.navigate("event/new")
        }
    }

    LaunchedEffect(viewEventId) {
        if (viewEventId != null) {
            navController.navigate("event/$viewEventId")
        }
    }

    NavHost(navController = navController, startDestination = "calendar") {
        composable("calendar") {
            CalendarScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToEvent = { id -> navController.navigate("event/$id") },
                onNavigateToNewEvent = { navController.navigate("event/new") },
                forceScheduleView = forceScheduleView,
            )
        }

        composable(
            "event/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("id") ?: return@composable
            EventDetailScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate("event/edit/$id") },
            )
        }

        composable("event/new") {
            EventFormScreen(
                eventId = null,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            "event/edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("id") ?: return@composable
            EventFormScreen(
                eventId = eventId,
                onNavigateBack = {
                    navController.popBackStack("calendar", inclusive = false)
                },
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
