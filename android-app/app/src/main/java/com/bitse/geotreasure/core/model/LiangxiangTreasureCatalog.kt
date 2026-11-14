package com.bitse.geotreasure.core.model

import kotlin.math.cos
import kotlin.math.sin

object LiangxiangTreasureCatalog {
    const val PUZZLE_ID = "puzzle_liangxiang_campus"

    // 难度/地形严格按交付原型 .fig（P05 三校区片区帧实测）：
    // 难度 1=★☆☆ 2=★★☆；地形 1=平地 2=林荫 3=台阶 4=湖畔
    // 坐标/占地：2026-11-14 实测校准值（P01 真实地图地标定位），矩形占地为轴对齐宽×高（米），
    // 学服为圆形半径 56m；占地同时用于地图区域叠加层与本地到点演示判定。
    val landmarks: List<Landmark> = listOf(
        landmark("north_lake", "北湖", CampusZone.NORTH, 0, 39.734934, 116.170196, rectangle(390.0, 202.0, 0.0), "沿湖岸寻找具有校园辨识度的取景点。", 1, 4),
        landmark("north_zongjiao", "综教", CampusZone.NORTH, 1, 39.733317, 116.171304, rectangle(177.0, 89.0, 0.0), "观察综合教学楼入口附近的建筑标识。", 1, 1),
        landmark("north_student_service", "学服", CampusZone.NORTH, 2, 39.732696, 116.167787, circle(56.0), "在学生服务中心外选择不影响通行的位置完成任务。", 1, 1),
        landmark("north_library", "图书馆", CampusZone.NORTH, 3, 39.731851, 116.171335, rectangle(149.0, 97.0, 0.0), "寻找徐特立图书馆具有代表性的外立面。", 2, 3),
        landmark("east_gantang", "甘棠", CampusZone.EAST, 0, 39.736011, 116.173911, rectangle(184.0, 81.0, 0.0), "在甘棠园公共区域寻找任务标志。", 1, 1),
        landmark("east_qinyuan", "勤园", CampusZone.EAST, 1, 39.734705, 116.176207, rectangle(204.0, 133.0, 0.0), "从公共道路观察勤园的区域特征。", 1, 1),
        landmark("east_wencui", "文萃", CampusZone.EAST, 2, 39.732692, 116.173830, rectangle(178.0, 259.0, 0.0), "寻找文萃楼附近容易辨认的建筑细节。", 2, 2),
        landmark("east_wenbo", "文博中心", CampusZone.EAST, 3, 39.733296, 116.176620, rectangle(190.0, 124.0, 0.0), "在文博中心外完成不包含他人正脸的合影。", 2, 1),
        landmark("south_shutong", "疏桐", CampusZone.SOUTH, 0, 39.729654, 116.167735, rectangle(110.0, 215.0, 0.0), "沿疏桐园公共道路寻找指定区域。", 2, 2),
        landmark("south_boya", "博雅", CampusZone.SOUTH, 1, 39.726161, 116.169149, rectangle(110.0, 72.0, 0.0), "在博雅园公共区域完成现场任务。", 1, 1),
        landmark("south_lijiao", "理教", CampusZone.SOUTH, 2, 39.730289, 116.171439, rectangle(136.0, 89.0, 0.0), "寻找理科教学楼入口附近的校园标识。", 1, 3),
        landmark("south_industrial_ecology", "工业生态楼", CampusZone.SOUTH, 3, 39.726058, 116.171010, rectangle(132.0, 47.0, 0.0), "在工业生态楼外选择安全位置完成打卡。", 2, 1),
    )

    val puzzle: PuzzleDefinition = PuzzleDefinition(
        id = PUZZLE_ID,
        title = "良乡校区探索图",
        defaultRevealMode = PuzzleRevealMode.FOG_OF_WAR,
        sections = CampusZone.entries.map { zone ->
            val zoneLandmarks = landmarks.filter { it.campusZone == zone }.sortedBy { it.pieceIndex }
            PuzzleSectionDefinition(
                id = "section_${zone.name.lowercase()}",
                campusZone = zone,
                rows = 2,
                columns = 2,
                fragments = zoneLandmarks.map { landmark ->
                    PuzzleFragmentDefinition(
                        id = landmark.fragmentId,
                        landmarkId = landmark.id,
                        title = landmark.name,
                        pieceIndex = landmark.pieceIndex,
                    )
                },
            )
        },
    )

    private fun landmark(
        idSuffix: String,
        name: String,
        campusZone: CampusZone,
        pieceIndex: Int,
        latitude: Double,
        longitude: Double,
        checkInArea: CheckInArea,
        hint: String,
        difficulty: Int,
        terrain: Int,
    ) = Landmark(
        id = "lmk_$idSuffix",
        name = name,
        point = GeoPoint(latitude = latitude, longitude = longitude),
        campusZone = campusZone,
        fragmentId = "fragment_$idSuffix",
        pieceIndex = pieceIndex,
        coordinateVerified = false,
        checkInArea = checkInArea,
        difficulty = difficulty,
        terrain = terrain,
        hint = "$hint 当前坐标仅用于开发演示，正式打卡前需要实地核验。",
    )

    private fun circle(radiusMeters: Double) = CircularCheckInArea(radiusMeters)

    private fun rectangle(
        widthMeters: Double,
        heightMeters: Double,
        rotationDegrees: Double,
    ): PolygonCheckInArea {
        val halfWidth = widthMeters / 2.0
        val halfHeight = heightMeters / 2.0
        val radians = Math.toRadians(rotationDegrees)
        val cosine = cos(radians)
        val sine = sin(radians)
        return PolygonCheckInArea(
            listOf(
                LocalOffsetMeters(-halfWidth, -halfHeight),
                LocalOffsetMeters(halfWidth, -halfHeight),
                LocalOffsetMeters(halfWidth, halfHeight),
                LocalOffsetMeters(-halfWidth, halfHeight),
            ).map { offset ->
                LocalOffsetMeters(
                    eastMeters = offset.eastMeters * cosine - offset.northMeters * sine,
                    northMeters = offset.eastMeters * sine + offset.northMeters * cosine,
                )
            },
        )
    }
}
