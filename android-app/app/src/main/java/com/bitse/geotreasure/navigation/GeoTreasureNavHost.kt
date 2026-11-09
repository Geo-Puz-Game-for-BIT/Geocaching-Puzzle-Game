package com.bitse.geotreasure.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bitse.geotreasure.BuildConfig
import com.bitse.geotreasure.app.GeoTreasureApplication
import com.bitse.geotreasure.feature.camera.CameraScreen
import com.bitse.geotreasure.feature.camera.CameraViewModel
import com.bitse.geotreasure.feature.checkin.CheckInResultScreen
import com.bitse.geotreasure.feature.checkin.CheckInResultViewModel
import com.bitse.geotreasure.feature.landmark.LandmarkDetailScreen
import com.bitse.geotreasure.feature.landmark.LandmarkDetailViewModel
import com.bitse.geotreasure.feature.map.MapScreen
import com.bitse.geotreasure.feature.map.MapViewModel
import com.bitse.geotreasure.feature.puzzle.PuzzleScreen
import com.bitse.geotreasure.feature.puzzle.PuzzleViewModel

@Composable
fun GeoTreasureNavHost() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as GeoTreasureApplication
    val container = application.container

    NavHost(navController = navController, startDestination = AppRoutes.MAP) {
        composable(AppRoutes.MAP) {
            val mapViewModel: MapViewModel = viewModel(
                factory = MapViewModel.factory(
                    landmarkRepository = container.landmarkRepository,
                    locationProvider = container.locationProvider,
                    privacyManager = container.privacyManager,
                    explorationProgressStore = container.explorationProgressStore,
                    hasAmapKey = BuildConfig.HAS_AMAP_KEY,
                    hasCloudApi = BuildConfig.HAS_CLOUD_API,
                    debugEnabled = BuildConfig.DEBUG,
                ),
            )
            MapScreen(
                viewModel = mapViewModel,
                onOpenLandmark = { navController.navigate(AppRoutes.landmarkDetail(it)) },
            )
        }
        composable(
            route = AppRoutes.LANDMARK_DETAIL,
            arguments = listOf(navArgument("landmarkId") { type = NavType.StringType }),
        ) { entry ->
            val landmarkId = requireNotNull(entry.arguments?.getString("landmarkId"))
            val detailViewModel: LandmarkDetailViewModel = viewModel(
                factory = LandmarkDetailViewModel.factory(
                    landmarkId = landmarkId,
                    repository = container.landmarkRepository,
                    locationProvider = container.locationProvider,
                ),
            )
            LandmarkDetailScreen(
                viewModel = detailViewModel,
                onBack = navController::popBackStack,
                onOpenCamera = { id, attemptId -> navController.navigate(AppRoutes.camera(id, attemptId)) },
            )
        }
        composable(
            route = AppRoutes.CAMERA,
            arguments = listOf(
                navArgument("landmarkId") { type = NavType.StringType },
                navArgument("attemptId") { type = NavType.StringType },
            ),
        ) { entry ->
            val landmarkId = requireNotNull(entry.arguments?.getString("landmarkId"))
            val attemptId = requireNotNull(entry.arguments?.getString("attemptId"))
            val cameraViewModel: CameraViewModel = viewModel(
                factory = CameraViewModel.factory(
                    landmarkId = landmarkId,
                    attemptId = attemptId,
                    draftStore = container.checkInDraftStore,
                    cameraManager = container.createCameraManager(),
                    checkInRepository = container.checkInRepository,
                    submitCoordinator = container.submitCoordinator,
                    progressStore = container.explorationProgressStore,
                    locationProvider = container.locationProvider,
                    faceQualityChecker = container.createFaceQualityChecker(),
                    landmarkRepository = container.landmarkRepository,
                ),
            )
            CameraScreen(
                viewModel = cameraViewModel,
                onBack = navController::popBackStack,
                onResult = { checkInId, completedLandmarkId ->
                    navController.navigate(AppRoutes.checkInResult(checkInId, completedLandmarkId))
                },
                onOpenDraft = { draftLandmarkId, draftAttemptId ->
                    // C-2：续用旧草稿，替换当前 camera 入口，避免旧弹窗残留
                    navController.navigate(AppRoutes.camera(draftLandmarkId, draftAttemptId)) {
                        popUpTo(AppRoutes.CAMERA) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = AppRoutes.CHECK_IN_RESULT,
            arguments = listOf(
                navArgument("checkInId") { type = NavType.StringType },
                navArgument("landmarkId") { type = NavType.StringType },
            ),
        ) { entry ->
            val checkInId = requireNotNull(entry.arguments?.getString("checkInId"))
            val landmarkId = requireNotNull(entry.arguments?.getString("landmarkId"))
            val resultViewModel: CheckInResultViewModel = viewModel(
                factory = CheckInResultViewModel.factory(
                    checkInId = checkInId,
                    landmarkId = landmarkId,
                ),
            )
            CheckInResultScreen(
                viewModel = resultViewModel,
                onOpenPuzzle = { navController.navigate(AppRoutes.puzzle(it)) },
            )
        }
        composable(
            route = AppRoutes.PUZZLE,
            arguments = listOf(navArgument("puzzleId") { type = NavType.StringType }),
        ) { entry ->
            val puzzleId = requireNotNull(entry.arguments?.getString("puzzleId"))
            val puzzleViewModel: PuzzleViewModel = viewModel(
                factory = PuzzleViewModel.factory(puzzleId, container.explorationProgressStore),
            )
            PuzzleScreen(viewModel = puzzleViewModel, onBack = navController::popBackStack)
        }
    }
}
