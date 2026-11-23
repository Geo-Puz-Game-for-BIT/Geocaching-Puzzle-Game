package com.bitse.geotreasure.core.model

/**
 * 校区围界多边形（GCJ-02，与 App 落地图层同口径）。
 *
 * 来源：campus-unified-v5 包 sources/campus-scale-controls.json 的 OSM 围界
 * （geometry_wgs84），经 WGS84→GCJ-02 迭代转换（v12 验收同源数据）。
 * 用途：迷雾 v4（E「连续云层·步行开雾」设计）校界判定——校界外雾 alpha 恒 1，
 * 校界内侧羽化。三区多边形互不相交。
 */
object CampusFences {

    /** 围界多边形：北 → 东 → 南 三区，GCJ-02 顶点按 OSM 原始顺序排列 */
    val zones: Map<CampusZone, List<GeoPoint>> = mapOf(
        CampusZone.NORTH to listOf(
            GeoPoint(latitude = 39.737647900, longitude = 116.160933600),
            GeoPoint(latitude = 39.737521500, longitude = 116.160933700),
            GeoPoint(latitude = 39.732945500, longitude = 116.160936200),
            GeoPoint(latitude = 39.730316800, longitude = 116.160955000),
            GeoPoint(latitude = 39.730075900, longitude = 116.160956700),
            GeoPoint(latitude = 39.730082100, longitude = 116.162113600),
            GeoPoint(latitude = 39.729941800, longitude = 116.162115300),
            GeoPoint(latitude = 39.729949500, longitude = 116.163662200),
            GeoPoint(latitude = 39.730054800, longitude = 116.163662800),
            GeoPoint(latitude = 39.730097100, longitude = 116.163663000),
            GeoPoint(latitude = 39.730096700, longitude = 116.163772300),
            GeoPoint(latitude = 39.730096400, longitude = 116.163862400),
            GeoPoint(latitude = 39.730096000, longitude = 116.163981300),
            GeoPoint(latitude = 39.730095500, longitude = 116.164131000),
            GeoPoint(latitude = 39.729947600, longitude = 116.164130100),
            GeoPoint(latitude = 39.729953300, longitude = 116.166138700),
            GeoPoint(latitude = 39.730029500, longitude = 116.166230500),
            GeoPoint(latitude = 39.730469400, longitude = 116.166244800),
            GeoPoint(latitude = 39.730651500, longitude = 116.166290600),
            GeoPoint(latitude = 39.732682700, longitude = 116.166279300),
            GeoPoint(latitude = 39.736516100, longitude = 116.166290200),
            GeoPoint(latitude = 39.736730200, longitude = 116.166247800),
            GeoPoint(latitude = 39.736899000, longitude = 116.166195400),
            GeoPoint(latitude = 39.737288400, longitude = 116.166194600),
            GeoPoint(latitude = 39.737409600, longitude = 116.166006500),
            GeoPoint(latitude = 39.737576100, longitude = 116.165397100),
            GeoPoint(latitude = 39.737693100, longitude = 116.164531600),
            GeoPoint(latitude = 39.737647900, longitude = 116.160933600),
        ),
        CampusZone.EAST to listOf(
            GeoPoint(latitude = 39.735374800, longitude = 116.166818200),
            GeoPoint(latitude = 39.734456300, longitude = 116.171598000),
            GeoPoint(latitude = 39.734258100, longitude = 116.171767300),
            GeoPoint(latitude = 39.730178100, longitude = 116.171717000),
            GeoPoint(latitude = 39.730152900, longitude = 116.169208500),
            GeoPoint(latitude = 39.730153900, longitude = 116.169151500),
            GeoPoint(latitude = 39.730154400, longitude = 116.168833300),
            GeoPoint(latitude = 39.730154400, longitude = 116.168526000),
            GeoPoint(latitude = 39.730160600, longitude = 116.166717400),
            GeoPoint(latitude = 39.730267700, longitude = 116.166715900),
            GeoPoint(latitude = 39.730626900, longitude = 116.166659200),
            GeoPoint(latitude = 39.732595800, longitude = 116.166651400),
            GeoPoint(latitude = 39.732680400, longitude = 116.166651500),
            GeoPoint(latitude = 39.732777100, longitude = 116.166651600),
            GeoPoint(latitude = 39.735269500, longitude = 116.166653400),
            GeoPoint(latitude = 39.735374800, longitude = 116.166818200),
        ),
        CampusZone.SOUTH to listOf(
            GeoPoint(latitude = 39.729493100, longitude = 116.162073800),
            GeoPoint(latitude = 39.729361300, longitude = 116.162078000),
            GeoPoint(latitude = 39.729375900, longitude = 116.161086400),
            GeoPoint(latitude = 39.729286800, longitude = 116.160946700),
            GeoPoint(latitude = 39.724462000, longitude = 116.160938700),
            GeoPoint(latitude = 39.724459100, longitude = 116.163918800),
            GeoPoint(latitude = 39.724485700, longitude = 116.166069500),
            GeoPoint(latitude = 39.724659900, longitude = 116.166313100),
            GeoPoint(latitude = 39.729446000, longitude = 116.166211400),
            GeoPoint(latitude = 39.729486500, longitude = 116.166150100),
            GeoPoint(latitude = 39.729485500, longitude = 116.164165600),
            GeoPoint(latitude = 39.729379700, longitude = 116.164167000),
            GeoPoint(latitude = 39.729378300, longitude = 116.163978600),
            GeoPoint(latitude = 39.729377400, longitude = 116.163863700),
            GeoPoint(latitude = 39.729376000, longitude = 116.163673700),
            GeoPoint(latitude = 39.729375500, longitude = 116.163607600),
            GeoPoint(latitude = 39.729479500, longitude = 116.163606300),
            GeoPoint(latitude = 39.729493100, longitude = 116.162073800),
        ),
    )

    val allPolygons: List<List<GeoPoint>> get() = zones.values.toList()
}
