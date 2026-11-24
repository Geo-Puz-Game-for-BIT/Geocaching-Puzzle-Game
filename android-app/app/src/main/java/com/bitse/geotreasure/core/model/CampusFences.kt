package com.bitse.geotreasure.core.model

/**
 * 校区围界多边形（GCJ-02，与 App 落地图层同口径）。
 *
 * 来源：campus-unified-v5 包 sources/campus-scale-controls.json 的 OSM 围界
 * （geometry_wgs84，区域名 northwest=北 / northeast=东 / southwest=南），
 * 经 WGS84→GCJ-02 单步转换（2026-11-24 修正偏移 bug 后重生成，与 v12 底图 bounds 对齐验证）。
 * 用途：迷雾 v6——界内带模糊衰减填充呈朦胧半透明，界外雾恒不透明。
 */
object CampusFences {

    /** 围界多边形：北 → 东 → 南 三区，GCJ-02 顶点按 OSM 原始顺序排列 */
    val zones: Map<CampusZone, List<GeoPoint>> = mapOf(
        CampusZone.NORTH to listOf(
            GeoPoint(latitude = 39.738963784, longitude = 116.166993653),
            GeoPoint(latitude = 39.738837369, longitude = 116.166993747),
            GeoPoint(latitude = 39.734260815, longitude = 116.166996028),
            GeoPoint(latitude = 39.731631759, longitude = 116.167014655),
            GeoPoint(latitude = 39.731390827, longitude = 116.167016339),
            GeoPoint(latitude = 39.731394908, longitude = 116.168170023),
            GeoPoint(latitude = 39.731254587, longitude = 116.168171712),
            GeoPoint(latitude = 39.731259438, longitude = 116.169714293),
            GeoPoint(latitude = 39.731364750, longitude = 116.169714896),
            GeoPoint(latitude = 39.731407055, longitude = 116.169715097),
            GeoPoint(latitude = 39.731406453, longitude = 116.169824091),
            GeoPoint(latitude = 39.731405986, longitude = 116.169913939),
            GeoPoint(latitude = 39.731405366, longitude = 116.170032506),
            GeoPoint(latitude = 39.731404589, longitude = 116.170181787),
            GeoPoint(latitude = 39.731256672, longitude = 116.170180883),
            GeoPoint(latitude = 39.731258644, longitude = 116.172183843),
            GeoPoint(latitude = 39.731334683, longitude = 116.172275388),
            GeoPoint(latitude = 39.731774610, longitude = 116.172289668),
            GeoPoint(latitude = 39.731956647, longitude = 116.172335347),
            GeoPoint(latitude = 39.733988117, longitude = 116.172324172),
            GeoPoint(latitude = 39.737821958, longitude = 116.172335218),
            GeoPoint(latitude = 39.738036162, longitude = 116.172292947),
            GeoPoint(latitude = 39.738205080, longitude = 116.172240702),
            GeoPoint(latitude = 39.738594528, longitude = 116.172239923),
            GeoPoint(latitude = 39.738716092, longitude = 116.172052357),
            GeoPoint(latitude = 39.738883745, longitude = 116.171444679),
            GeoPoint(latitude = 39.739002365, longitude = 116.170581614),
            GeoPoint(latitude = 39.738963784, longitude = 116.166993653),
        ),
        CampusZone.EAST to listOf(
            GeoPoint(latitude = 39.736679538, longitude = 116.172861678),
            GeoPoint(latitude = 39.735751985, longitude = 116.177627943),
            GeoPoint(latitude = 39.735553444, longitude = 116.177796755),
            GeoPoint(latitude = 39.731473040, longitude = 116.177746412),
            GeoPoint(latitude = 39.731452537, longitude = 116.175244997),
            GeoPoint(latitude = 39.731453644, longitude = 116.175188158),
            GeoPoint(latitude = 39.731454739, longitude = 116.174870856),
            GeoPoint(latitude = 39.731455314, longitude = 116.174564423),
            GeoPoint(latitude = 39.731464892, longitude = 116.172760923),
            GeoPoint(latitude = 39.731572008, longitude = 116.172759432),
            GeoPoint(latitude = 39.731931358, longitude = 116.172702908),
            GeoPoint(latitude = 39.733900513, longitude = 116.172695220),
            GeoPoint(latitude = 39.733985123, longitude = 116.172695324),
            GeoPoint(latitude = 39.734081835, longitude = 116.172695428),
            GeoPoint(latitude = 39.736574532, longitude = 116.172697337),
            GeoPoint(latitude = 39.736679538, longitude = 116.172861678),
        ),
        CampusZone.SOUTH to listOf(
            GeoPoint(latitude = 39.730805908, longitude = 116.168130307),
            GeoPoint(latitude = 39.730674084, longitude = 116.168134489),
            GeoPoint(latitude = 39.730690503, longitude = 116.167145647),
            GeoPoint(latitude = 39.730601647, longitude = 116.167006331),
            GeoPoint(latitude = 39.725776255, longitude = 116.166998136),
            GeoPoint(latitude = 39.725767875, longitude = 116.169969927),
            GeoPoint(latitude = 39.725790487, longitude = 116.172114592),
            GeoPoint(latitude = 39.725964256, longitude = 116.172357514),
            GeoPoint(latitude = 39.730751146, longitude = 116.172256315),
            GeoPoint(latitude = 39.730791765, longitude = 116.172195189),
            GeoPoint(latitude = 39.730794449, longitude = 116.170216262),
            GeoPoint(latitude = 39.730688634, longitude = 116.170217653),
            GeoPoint(latitude = 39.730687582, longitude = 116.170029781),
            GeoPoint(latitude = 39.730686895, longitude = 116.169915203),
            GeoPoint(latitude = 39.730685846, longitude = 116.169725735),
            GeoPoint(latitude = 39.730685468, longitude = 116.169659820),
            GeoPoint(latitude = 39.730789483, longitude = 116.169658528),
            GeoPoint(latitude = 39.730805908, longitude = 116.168130307),
        ),
    )

    val allPolygons: List<List<GeoPoint>> get() = zones.values.toList()
}
