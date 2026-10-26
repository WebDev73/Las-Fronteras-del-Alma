package app.fronteras.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.fronteras.android.ui.conversation.ConversationScreen
import app.fronteras.android.ui.diagnostic.DiagnosticScreen
import app.fronteras.android.ui.home.HomeAction
import app.fronteras.android.ui.home.HomeScreen
import app.fronteras.android.ui.onboarding.OnboardingScreen
import app.fronteras.android.ui.progress.PathScreen
import app.fronteras.android.ui.progress.ProgressScreen
import app.fronteras.android.ui.review.ReviewScreen
import app.fronteras.android.ui.review.SessionReviewScreen
import app.fronteras.android.ui.scenarios.ScenariosScreen
import app.fronteras.android.ui.settings.DeckScreen
import app.fronteras.android.ui.settings.HistoryScreen
import app.fronteras.android.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DIAGNOSTIC = "diagnostic"
    const val HOME = "home"
    const val SCENARIOS = "scenarios"
    const val CONVERSATION = "conversation?scenario={scenario}&competency={competency}&minutes={minutes}"
    const val SESSION_REVIEW = "session/{id}"
    const val REVIEW = "review"
    const val PATH = "path"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val DECK = "deck"

    fun conversation(scenario: String?, competency: String?, minutes: Int) =
        "conversation?scenario=${scenario.orEmpty()}&competency=${competency.orEmpty()}&minutes=$minutes"
    fun session(id: String) = "session/$id"
}

@Composable
fun FronterasNavHost(startOnboarding: Boolean, nav: NavHostController = rememberNavController()) {
    fun goHome() = nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }

    NavHost(nav, startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.HOME) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { nav.navigate(Routes.DIAGNOSTIC) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
        }
        composable(Routes.DIAGNOSTIC) { DiagnosticScreen(onDone = ::goHome) }
        composable(Routes.HOME) {
            HomeScreen { a ->
                when (a) {
                    HomeAction.Diagnostic -> nav.navigate(Routes.DIAGNOSTIC)
                    HomeAction.Review -> nav.navigate(Routes.REVIEW)
                    is HomeAction.Conversation -> nav.navigate(Routes.conversation(a.scenarioId, a.competencyId, a.minutes))
                    HomeAction.Scenarios -> nav.navigate(Routes.SCENARIOS)
                    HomeAction.Path -> nav.navigate(Routes.PATH)
                    HomeAction.Progress -> nav.navigate(Routes.PROGRESS)
                    HomeAction.Settings -> nav.navigate(Routes.SETTINGS)
                }
            }
        }
        composable(Routes.SCENARIOS) {
            ScenariosScreen(onBack = { nav.popBackStack() }, onStart = { id, m -> nav.navigate(Routes.conversation(id, null, m)) })
        }
        composable(
            Routes.CONVERSATION,
            arguments = listOf(
                navArgument("scenario") { type = NavType.StringType; defaultValue = "" },
                navArgument("competency") { type = NavType.StringType; defaultValue = "" },
                navArgument("minutes") { type = NavType.IntType; defaultValue = 10 },
            ),
        ) { entry ->
            val args = entry.arguments
            ConversationScreen(
                scenarioId = args?.getString("scenario")?.ifEmpty { null },
                competencyId = args?.getString("competency")?.ifEmpty { null },
                minutes = args?.getInt("minutes") ?: 10,
                onBack = { nav.popBackStack() },
                onEnded = { id -> nav.navigate(Routes.session(id)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.SESSION_REVIEW, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            SessionReviewScreen(entry.arguments?.getString("id").orEmpty(), onReview = { nav.navigate(Routes.REVIEW) { popUpTo(Routes.HOME) } }, onHome = ::goHome)
        }
        composable(Routes.REVIEW) { ReviewScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PATH) { PathScreen(onBack = { nav.popBackStack() }, onStart = { c, m -> nav.navigate(Routes.conversation(null, c, m)) }) }
        composable(Routes.PROGRESS) { ProgressScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onDeck = { nav.navigate(Routes.DECK) },
                onErased = { nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } } },
            )
        }
        composable(Routes.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.session(it)) }) }
        composable(Routes.DECK) { DeckScreen(onBack = { nav.popBackStack() }) }
    }
}
