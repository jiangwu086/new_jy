const { get } = require('../../utils/request')

const PAGE_SIZE = 20

Page({
  data: {
    activeTab: 'points',

    // 积分流水
    pointsList: [],
    pointsPage: 1,
    pointsTotal: 0,
    pointsHasMore: false,
    loadingPoints: false,
    loadingMorePoints: false,

    // 兑换记录
    redeemsList: [],
    redeemsPage: 1,
    redeemsTotal: 0,
    redeemsHasMore: false,
    loadingRedeems: false,
    loadingMoreRedeems: false,

    typeLabel: {
      1: '打卡获得',
      2: '线下核销',
      3: '系统调整',
      4: '在线兑换'
    }
  },

  onLoad() {
    this.loadPoints(1)
    this.loadRedeems(1)
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
  },

  // ── 积分流水 ──────────────────────────────────────
  loadPoints(page) {
    if (page === 1) {
      this.setData({ loadingPoints: true, pointsList: [], pointsPage: 1 })
    } else {
      this.setData({ loadingMorePoints: true })
    }
    get(`/api/v1/user/points-log?page=${page}&size=${PAGE_SIZE}`, {}, { noRetry: true }).then(res => {
      const { list = [], total = 0 } = res.data || {}
      const formatted = list.map(item => ({
        ...item,
        createTime: this.formatTime(item.createTime)
      }))
      this.setData({
        pointsList: page === 1 ? formatted : [...this.data.pointsList, ...formatted],
        pointsTotal: total,
        pointsPage: page,
        pointsHasMore: page * PAGE_SIZE < total,
        loadingPoints: false,
        loadingMorePoints: false
      })
    }).catch(() => {
      this.setData({ loadingPoints: false, loadingMorePoints: false })
    })
  },

  loadMorePoints() {
    if (this.data.loadingMorePoints || !this.data.pointsHasMore) return
    this.loadPoints(this.data.pointsPage + 1)
  },

  // ── 兑换记录 ──────────────────────────────────────
  loadRedeems(page) {
    if (page === 1) {
      this.setData({ loadingRedeems: true, redeemsList: [], redeemsPage: 1 })
    } else {
      this.setData({ loadingMoreRedeems: true })
    }
    get(`/api/v1/user/redeems?page=${page}&size=${PAGE_SIZE}`, {}, { noRetry: true }).then(res => {
      const { list = [], total = 0 } = res.data || {}
      const formatted = list.map(item => ({
        ...item,
        createTime: this.formatTime(item.createTime)
      }))
      this.setData({
        redeemsList: page === 1 ? formatted : [...this.data.redeemsList, ...formatted],
        redeemsTotal: total,
        redeemsPage: page,
        redeemsHasMore: page * PAGE_SIZE < total,
        loadingRedeems: false,
        loadingMoreRedeems: false
      })
    }).catch(() => {
      this.setData({ loadingRedeems: false, loadingMoreRedeems: false })
    })
  },

  loadMoreRedeems() {
    if (this.data.loadingMoreRedeems || !this.data.redeemsHasMore) return
    this.loadRedeems(this.data.redeemsPage + 1)
  },

  // ── 工具 ─────────────────────────────────────────
  formatTime(t) {
    if (!t) return ''
    return t.replace('T', ' ').substring(0, 16)
  }
})
