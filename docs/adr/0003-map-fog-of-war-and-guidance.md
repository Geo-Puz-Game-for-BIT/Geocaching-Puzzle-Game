# 地图页战争迷雾与引导设计

日期：2026-10-22

## 1. 目标

战争迷雾属于地图页的探索反馈，不放在地标详情页。用户在地图上移动时，当前位置周围的侦查范围逐步打开；已打开的区域在当前探索会话中保持可见，未探索区域显示为灰色半透明遮罩。

侦查半径为 15 米，只有定位精度不高于 50 米的位置样本才能产生新的探索记录。首次可靠定位点立即开雾；之后相隔至少 10 米才新增探索中心，使行走中的可见区域连续。数值集中在 `FogOfWarRules`，后续可以改成关卡配置。

当前位置的 15 米侦查视野在首次定位回调后立即显示，包括精度暂时不足的情况；这种视野只是临时可见，不写入 `exploredCenters`。只有精度达标的位置才成为永久探索记录。定位信号超时或报错时，临时视野收起，既有探索区域保留。

## 2. 当前实现

位置数据流为：

```text
LocationProvider
    -> MapViewModel
    -> MapUiState.exploredCenters
    -> MapRenderState
    -> MapAdapter / DemoMapContent
```

- `MapViewModel` 收到首次可靠定位后立即记录探索中心，后续按 10 米的最小间隔记录。
- 高德地图使用原生 `TileOverlay` 绘制灰色透明迷雾；瓦片按地理坐标清除历史探索中心、实时侦查视野、已发现地标范围和已完成分块。瓦片随地图自身平移和缩放，状态变化时刷新缓存，避免屏幕上层蒙版与地图手势不同步。
- 普通玩家界面不绘制侦查圈，只显示侦查范围内已经被驱散的正常地图；开发者调试模式可以单独打开侦查圈。
- 离线演示地图使用 Compose 的灰色遮罩和透明圆孔，保持与真实地图相同的交互概念。
- 地标覆盖物仍由地图适配器管理。普通模式默认不预显地标；可靠定位进入某地标的 `CheckInArea` 后，才将该地标标为已发现，显示 Marker、选中信息面板并打开其打卡范围附近的迷雾。Debug“显示全图”可查看全部地标。
- 完成地标任务后，地图使用 `CampusRevealBlock` 多边形孔洞驱散该地标所属分块的全部迷雾；每区四块与 2×2 碎片 ID 对应。当前多边形基于演示坐标临时计算，正式边界需实测校准。

发现与完成分别建模：进入范围只负责提示和局部揭示，不产生碎片奖励；C/D 的真实照片上传与服务端打卡成功回执负责完成状态，B 再点亮整个分块。当前演示完成按钮只改进程内状态。

当前探索记录和演示版完成地标记录保存在应用内存中，应用进程结束后会清空。正式版本需要把 `exploredCenters` 与完成地标状态持久化到本地数据库或按关卡保存到服务端。

## 3.1 开发者调试模式

Debug 包的地图页提供开发者调试面板，可以：

- 临时显示全图，关闭迷雾；
- 显示或隐藏侦查范围圈；
- 调整当前选中地标的打卡范围；
- 在圆形和矩形范围之间切换；
- 直接标记当前地标完成，验证区域驱散效果。
- 查看已记录的探索中心数量；在真实 GPS 精度不高于 20 米时，复制当前选中地标的候选 GCJ-02 坐标、精度与采样时间。此操作不自动修改目录或上传位置。

调试覆盖只存在于当前进程状态，不改变正式地图数据。Release 包不显示该入口。

## 3. 引导功能的边界

引导箭头是可选的辅助道具，不能自动替代寻宝过程。建议按以下顺序开发：

1. 首版只提供手动“显示线索”按钮，不加入自动计时，先验证箭头是否容易理解。
2. 每局设置有限次数，例如 3 次；次数写入 `GuideState.remainingUses`。
3. 引导目标从未完成地标中选择距离当前位置最近的一个，使用 `distanceMeters` 计算。
4. 箭头方向需要当前位置到目标点的方位角，以及手机当前朝向。位置模型建议增加 `bearingDegrees`，朝向由 `SensorManager` 或高德定位结果提供。
5. 以后再接入谜语：服务端返回 `HintChallenge`，玩家回答正确后生成 `GuideGrant`；客户端只负责展示获准的箭头，不在客户端保存正确答案。

建议的接口命名：

```kotlin
data class GuideState(
    val visible: Boolean = false,
    val targetLandmarkId: String? = null,
    val bearingDegrees: Float? = null,
    val distanceMeters: Double? = null,
    val remainingUses: Int = 3,
)

sealed interface GuideAction {
    data object RequestHint : GuideAction
    data class SubmitRiddle(val answer: String) : GuideAction
    data object Dismiss : GuideAction
}

interface GuidePolicy {
    fun chooseNearestTarget(
        location: GeoPoint,
        landmarks: List<Landmark>,
        completedLandmarkIds: Set<String>,
    ): Landmark?
}
```

## 4. 分工

- B：`MapViewModel` 中的探索状态、地图迷雾覆盖物、侦查圈、箭头绘制和手机朝向适配。
- D：引导次数、谜语挑战、答案校验和服务端防重复使用接口。
- C：拍照完成后更新已完成地标，使引导目标能够排除已完成任务。
- A：灰色透明度、侦查圈颜色、箭头图标、按钮和动画规范。
- E：双机地图、定位、迷雾与完成驱散回归，归档截图、录屏和缺陷。

## 5. 暂缓项

- 真实校园地图的遮罩边界和道路裁切需要最终地图底图后再调整。
- 学校官方 PDF 只有平面布局，没有地理配准；当前分块多边形不是官方精确边界。
- 探索记录跨进程恢复需要本地存储或服务端进度接口。
- 自动卡关计时、谜语校验和引导次数不能在 B 当前的 Fake Repository 阶段定稿。
- 正式开放前，地标坐标和打卡范围必须完成实地核验，避免迷雾已经显示但目标点位置不准确。
