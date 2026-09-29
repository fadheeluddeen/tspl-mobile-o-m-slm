package com.technavious.om15.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.technavious.om15.data.db.AppDatabase
import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.ui.screens.*

object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PROJECT_CREATE = "project/create"
    const val PROJECT_DETAIL = "project/{projectId}"
    const val TEST_SELECT = "project/{projectId}/select-test"
    const val TEST_FORM = "test/{assignmentId}"
    const val CAMERA = "camera/{assignmentId}/{fieldKey}"
}

@Composable
fun OM15NavHost(
    navController: NavHostController,
    repository: TestRepository
) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                repository = repository,
                onCreateProject = { navController.navigate(Routes.PROJECT_CREATE) },
                onProjectClick = { projectId ->
                    navController.navigate("project/$projectId")
                },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PROJECT_CREATE) {
            ProjectCreateScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onCreated = { projectId ->
                    navController.navigate("project/$projectId") {
                        popUpTo(Routes.DASHBOARD)
                    }
                }
            )
        }

        composable(
            Routes.PROJECT_DETAIL,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ProjectDetailScreen(
                projectId = projectId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onAddTest = { navController.navigate("project/$projectId/select-test") },
                onTestClick = { assignmentId ->
                    navController.navigate("test/$assignmentId")
                }
            )
        }

        composable(
            Routes.TEST_SELECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            TestSelectScreen(
                projectId = projectId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onTestSelected = { assignmentId ->
                    navController.navigate("test/$assignmentId") {
                        popUpTo("project/$projectId")
                    }
                }
            )
        }

        composable(
            Routes.TEST_FORM,
            arguments = listOf(navArgument("assignmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assignmentId = backStackEntry.arguments?.getString("assignmentId") ?: return@composable
            TestFormScreen(
                assignmentId = assignmentId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onNavigateToCamera = { fieldKey ->
                    navController.navigate("camera/$assignmentId/${android.net.Uri.encode(fieldKey)}")
                }
            )
        }

        composable(
            Routes.CAMERA,
            arguments = listOf(
                navArgument("assignmentId") { type = NavType.StringType },
                navArgument("fieldKey") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val assignmentId = backStackEntry.arguments?.getString("assignmentId") ?: return@composable
            val fieldKey = backStackEntry.arguments?.getString("fieldKey") ?: return@composable
            CameraScreen(
                assignmentId = assignmentId,
                fieldKey = fieldKey,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
