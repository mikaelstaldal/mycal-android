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
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun NavGraph(
    forceScheduleView: Boolean = false,
    openNewEvent: Boolean = false,
    viewEventId: String? = null,
) {
    val navController = rememberNavController()

    LaunchedEffect(openNewEvent) {
        if (openNewEvent) {
            navController.navigate("event/new")
        }
    }

    LaunchedEffect(viewEventId) {
        if (viewEventId != null) {
            val encoded = URLEncoder.encode(viewEventId, "UTF-8")
            navController.navigate("event/$encoded")
        }
    }

    NavHost(navController = navController, startDestination = "calendar") {
        composable("calendar") {
            CalendarScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToEvent = { id ->
                    val encoded = URLEncoder.encode(id, "UTF-8")
                    navController.navigate("event/$encoded")
                },
                onNavigateToNewEvent = { navController.navigate("event/new") },
                forceScheduleView = forceScheduleView,
            )
        }

        composable(
            "event/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("id") ?: return@composable
            val eventId = URLDecoder.decode(rawId, "UTF-8")
            EventDetailScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    val encoded = URLEncoder.encode(id, "UTF-8")
                    navController.navigate("event/edit/$encoded")
                },
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
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("id") ?: return@composable
            val eventId = URLDecoder.decode(rawId, "UTF-8")
            EventFormScreen(
                eventId = eventId,
                onNavigateBack = {
                    navController.popBackStack()
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
