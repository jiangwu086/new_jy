Component({
  properties: {
    show: { type: Boolean, value: false },
    /**
     * 骨架布局类型：
     *   home       — 3 张点位卡片（首页附近教育点）
     *   stats-list — 统计行 + 5 条记录（我的记录）
     *   gift       — 2×2 礼品卡片（积分兑换）
     *   list       — 8 条流水行（积分明细）
     */
    type: { type: String, value: 'list' }
  }
})
