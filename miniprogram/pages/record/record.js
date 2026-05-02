const { get } = require('../../utils/request')

Page({
  data: {
    isLogin: wx.getStorageSync('isLogin') === true,  // 同步读取，首帧即生效，避免闪屏
    totalPoints: 0,
    monthCheckins: 0,
    redeemCount: 0,
    currentTab: 'checkin',
    records: [],
    pointsLogs: [],
    loading: false
  },

  onShow() {
    const isLogin = wx.getStorageSync('isLogin') === true
    this.setData({ isLogin })
    if (!isLogin) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.loadData()
  },

  onPullDownRefresh() {
    if (wx.getStorageSync('isLogin') !== true) {
      wx.stopPullDownRefresh(); return
    }
    this.loadData()
    setTimeout(() => wx.stopPullDownRefresh(), 1200)
  },

  loadData() {
    this.setData({ loading: true })
    Promise.all([
      get('/api/v1/user/me', {}, { noRetry: true }),
      get('/api/v1/checkin/history', {}, { noRetry: true }),
      get('/api/v1/user/points-log', {}, { noRetry: true })
    ]).then(([userRes, checkinRes, pointsRes]) => {
      const user = userRes.data
      const checkins = checkinRes.data || []
      const logs = (pointsRes.data && pointsRes.data.list) || []

      // 统计本周期打卡次数（当前月份）
      const now = new Date()
      const thisPeriod = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`
      const monthCheckins = checkins.filter(c => String(c.period || '').startsWith(thisPeriod)).length

      // 统计核销次数（积分流水 type=2）
      const redeemCount = logs.filter(l => l.type === 2).length

      // 构建打卡记录展示数据（需要关联点位名称）
      // 异步加载点位列表做 id→name 映射
      this.loadLocationsAndMerge(checkins, logs, {
        totalPoints: user.totalPoints || 0,
        monthCheckins,
        redeemCount
      })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  loadLocationsAndMerge(checkins, logs, stats) {
    // 后端返回的 Map 是 snake_case，但兼容 camelCase 以防 entity 模式
    const pick = (c, ...keys) => {
      for (const k of keys) if (c[k] != null) return c[k]
      return undefined
    }

    const buildRecord = (c, locMap) => {
      const checkinTime = pick(c, 'checkinTime', 'checkin_time') || ''
      const locationId  = pick(c, 'locationId',  'location_id')
      const locationName = pick(c, 'locationName', 'location_name')   // history 接口现在直接带了点位名
      const points      = pick(c, 'pointsEarned', 'points_earned') || 0
      const status      = pick(c, 'status')        // 1 有效 0 无效
      const reviewStatus = pick(c, 'reviewStatus', 'review_status')   // 0 待审核 1 已通过 2 已驳回
      const reviewRemark = pick(c, 'reviewRemark', 'review_remark')

      // 状态：rejected 驳回 / pending 待审核 / valid 有效（已通过或还没审核就计入）
      let statusType = 'valid'
      let statusLabel = ''
      if (reviewStatus === 2 || status === 0) {
        statusType  = 'rejected'
        statusLabel = '已驳回'
      } else if (reviewStatus === 0) {
        statusType  = 'pending'
        statusLabel = '审核中'
      }

      return {
        id: c.id,
        dateBadge: checkinTime ? String(checkinTime).slice(5, 10) : '--',
        name: locationName || (locMap && locMap[locationId]) || `点位 #${locationId}`,
        time: checkinTime ? String(checkinTime).slice(11, 16) : '--',
        period: c.period || '',
        score: points,
        // 驳回时积分扣回，展示成 0
        scoreDisplay: statusType === 'rejected' ? 0 : points,
        statusType,
        statusLabel,
        rejectRemark: statusType === 'rejected' ? (reviewRemark || '') : ''
      }
    }

    get('/api/v1/locations', {}, { noRetry: true }).then(locRes => {
      const locMap = {}
      ;(locRes.data || []).forEach(l => { locMap[l.id] = l.name })

      const records = checkins.map(c => buildRecord(c, locMap))

      const pointsLogs = logs.map(l => ({
        ...l,
        typeLabel: { 1: '打卡获得', 2: '积分核销', 3: '系统调整', 4: '在线兑换' }[l.type] || '变动',
        amountText: (l.amount > 0 ? '+' : '') + l.amount,
        date: pick(l, 'createTime', 'create_time')
              ? String(pick(l, 'createTime', 'create_time')).slice(0, 10)
              : ''
      }))

      this.setData({
        ...stats,
        records,
        pointsLogs,
        loading: false
      })
    }).catch(() => {
      // 降级：不显示点位名称
      const records = checkins.map(c => buildRecord(c, null))
      this.setData({ ...stats, records, loading: false })
    })
  },

  switchTab(e) {
    this.setData({ currentTab: e.currentTarget.dataset.tab })
  }
})
