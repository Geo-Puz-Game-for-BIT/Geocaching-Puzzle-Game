package com.bitse.geotreasure.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bitse.geotreasure.ui.pages.camera.CameraRoute
import com.bitse.geotreasure.ui.pages.explore.ExploreRoute
import com.bitse.geotreasure.ui.pages.landmark.LandmarkDetailRoute
import com.bitse.geotreasure.ui.pages.map3d.Map3DRoute
import com.bitse.geotreasure.ui.pages.puzzle.ModulePuzzleRoute
import com.bitse.geotreasure.ui.pages.puzzle.PuzzleRoute
import com.bitse.geotreasure.ui.pages.records.RecordsRoute
import com.bitse.geotreasure.ui.pages.result.ResultRoute

/**
 * 全站导航图。
 *
 * 三个约定：
 *  1. 地标相关页面一律把 landmarkId 放路由参数里 —— 页面不猜「当前是哪个地标」；
 *  2. 底部三个 Tab 是「根目的地」，互切时 popUpTo(explore) + saveState，不堆栈；
 *  3. 打卡结果页接管返回：成功后按返回回地图（不再回到相机），失败则保留地标详情。
 */
@Composable
fun GeoTreasureNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.EXPLORE,
        modifier = modifier
    ) {
        composable(Routes.EXPLORE) {
            ExploreRoute(
                onOpenLandmark = { landmarkId, done -> navController.navigate(Routes.landmark(landmarkId, done)) },
                onOpen3D = { navController.navigate(Routes.MAP_3D) },
                onOpenPuzzle = { navController.navigateRoot(Routes.PUZZLE) },
                onOpenRecords = { navController.navigateRoot(Routes.RECORDS) }
            )
        }

        composable(
            route = Routes.LANDMARK,
            arguments = listOf(
                navArgument(Routes.ARG_LANDMARK_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_DONE) { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            LandmarkDetailRoute(
                onBack = { navController.popBackStack() },
                onStartCheckIn = { landmarkId -> navController.navigate(Routes.camera(landmarkId)) },
                onOpenPuzzle = { navController.navigate(Routes.PUZZLE) }
            )
        }

        composable(
            route = Routes.CAMERA,
            arguments = listOf(navArgument(Routes.ARG_LANDMARK_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val landmarkId = backStackEntry.arguments?.getString(Routes.ARG_LANDMARK_ID).orEmpty()
            CameraRoute(
                onBack = { navController.popBackStack() },
                onFinished = { outcomeKey ->
                    val keepDetail = outcomeKey.startsWith("fail-")
                    navController.navigate(Routes.result(landmarkId, outcomeKey)) {
                        // 成功/待确认：结果页接管返回，按返回直接回地图
                        popUpTo(Routes.LANDMARK) { inclusive = !keepDetail }
                    }
                }
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(
                navArgument(Routes.ARG_LANDMARK_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_OUTCOME) { type = NavType.StringType }
            )
        ) {
            ResultRoute(
                onBack = { navController.popBackStack() },
                // 原型 go:p05 = P05 拼图总览（三个片区卡），片区详情是总览里再点进去的一级
                onOpenPuzzle = { _ -> navController.navigate(Routes.PUZZLE) },
                onOpen3D = { navController.navigate(Routes.MAP_3D) },
                onExplore = { navController.navigateRoot(Routes.EXPLORE) },
                onOpenRecords = { navController.navigateRoot(Routes.RECORDS) },
                onRetryCheckIn = { landmarkId -> navController.navigate(Routes.camera(landmarkId)) },
                onOpenDetail = { landmarkId ->
                    navController.navigate(Routes.landmark(landmarkId)) {
                        // 已经有详情页在栈里就直接回去，避免叠加
                        popUpTo(Routes.LANDMARK) { inclusive = false }
                    }
                }
            )
        }

        composable(Routes.PUZZLE) {
            PuzzleRoute(
                onBack = { navController.navigateRoot(Routes.EXPLORE) },
                onOpenModule = { moduleId -> navController.navigate(Routes.module(moduleId)) },
                onOpenLandmark = { landmarkId -> navController.navigate(Routes.landmark(landmarkId, done = true)) },
                onExplore = { navController.navigateRoot(Routes.EXPLORE) },
                onOpenRecords = { navController.navigateRoot(Routes.RECORDS) }
            )
        }

        composable(
            route = Routes.MODULE,
            arguments = listOf(navArgument(Routes.ARG_MODULE_ID) { type = NavType.StringType })
        ) {
            ModulePuzzleRoute(
                onBack = { navController.popBackStack() },
                onOpenLandmark = { landmarkId -> navController.navigate(Routes.landmark(landmarkId, done = true)) },
                onExplore = { navController.navigateRoot(Routes.EXPLORE) },
                onOpenRecords = { navController.navigateRoot(Routes.RECORDS) }
            )
        }

        composable(Routes.RECORDS) {
            RecordsRoute(
                onBack = { navController.navigateRoot(Routes.EXPLORE) },
                onExplore = { navController.navigateRoot(Routes.EXPLORE) },
                onOpenPuzzle = { navController.navigateRoot(Routes.PUZZLE) },
                onOpenPending = { navController.navigateRoot(Routes.EXPLORE) }
            )
        }

        composable(Routes.MAP_3D) {
            Map3DRoute(
                onBack = { navController.popBackStack() },
                onExplore = { navController.navigateRoot(Routes.EXPLORE) },
                onOpenPuzzle = { navController.navigateRoot(Routes.PUZZLE) },
                // 按钮文案是「去这个地标打卡」，直达相机；要看任务与规则从 P01 水滴进
                onStartCheckIn = { landmarkId -> navController.navigate(Routes.camera(landmarkId)) }
            )
        }
    }
}

/** Tab 级切换：只保留一个根目的地，返回键不会在 Tab 之间来回跳 */
private fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(Routes.EXPLORE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
