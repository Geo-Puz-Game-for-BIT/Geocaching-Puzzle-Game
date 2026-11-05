package com.bitse.geotreasure.ui.navigation

/**
 * 一个地标一条路由（landmark/LM-003），而不是“打开详情页再看全局状态”。
 * 这是本轮修复「点开理教却显示疏桐」的结构性做法：地标 id 由参数带入，
 * 页面里没有任何“猜当前地标”的逻辑，从根上不可能错位。
 */
object Routes {
    const val EXPLORE = "explore"
    const val PUZZLE = "puzzle"
    const val MODULE = "module/{moduleId}"
    const val RECORDS = "records"
    const val MAP_3D = "map3d"

    const val LANDMARK = "landmark/{landmarkId}?done={done}"
    const val CAMERA = "camera/{landmarkId}"
    const val RESULT = "result/{landmarkId}/{outcome}"

    const val ARG_LANDMARK_ID = "landmarkId"
    const val ARG_MODULE_ID = "moduleId"
    const val ARG_OUTCOME = "outcome"
    const val ARG_DONE = "done"

    fun landmark(landmarkId: String, done: Boolean = false) =
        "landmark/$landmarkId?done=$done"

    fun camera(landmarkId: String) = "camera/$landmarkId"

    fun result(landmarkId: String, outcome: String) = "result/$landmarkId/$outcome"

    fun module(moduleId: String) = "module/$moduleId"
}
