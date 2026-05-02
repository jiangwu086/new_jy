const { get } = require('../../utils/request')

Page({
  data: {
    isLogin: false,
    isVerified: false,
    nearbyLocations: [],
    loading: true
  },

  onLoad() {
    this.loadLocations()
  },

  onShow() {
    this.refreshUserStatus()
    // 每次回到首页都刷新打卡点列表，确保后台改完立刻生效
    this.loadLocations()
  },

  onPullDownRefresh() {
    this.refreshUserStatus()
    this.loadLocations()
    setTimeout(() => wx.stopPullDownRefresh(), 1200)
  },

  loadLocations() {
    get('/api/v1/locations', {}, { noRetry: true }).then(res => {
      const locs = (res.data || []).slice(0, 3).map(l => ({
        ...l,
        isOpen: l.status === 1
      }))
      this.setData({ nearbyLocations: locs, loading: false })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  /**
   * 刷新登录 & 认证状态
   * 先用本地缓存快速渲染，再异步拉接口同步最新认证结果（解决管理员审核后状态不更新问题）
   */
  refreshUserStatus() {
    const isLogin = wx.getStorageSync('isLogin') === true
    // 先用缓存快速渲染，避免闪屏
    this.setData({
      isLogin,
      isVerified: wx.getStorageSync('isVerified') === true
    })
    // 已登录时异步拉接口，同步最新认证结果（审核通过后不用重新打开 App）
    if (isLogin) {
      get('/api/v1/user/me', {}, { noAuth: false, noRetry: true }).then(res => {
        const { verifyStatus } = res.data || {}
        const isVerified = verifyStatus === 2
        // 更新本地缓存
        wx.setStorageSync('isVerified', isVerified)
        wx.setStorageSync('verifyStatus', verifyStatus || 0)
        this.setData({ isVerified })
      }).catch(() => {})
    }
  },

  checkLogin() {
    if (wx.getStorageSync('isLogin') !== true) {
      wx.navigateTo({ url: '/pages/login/login' })
      return false
    }
    return true
  },

  checkVerified() {
    if (wx.getStorageSync('isVerified') !== true) {
      wx.showModal({
        title: '请先完成实名认证',
        content: '现场打卡需要先完成本人实名认证，建议使用人脸识别核验。',
        confirmText: '去认证',
        cancelText: '稍后',
        success: (res) => {
          if (res.confirm) wx.navigateTo({ url: '/pages/auth/auth' })
        }
      })
      return false
    }
    return true
  },

  goToCheckin() {
    if (!this.checkLogin()) return
    if (!this.checkVerified()) return
    const { platform } = wx.getDeviceInfo()
    if (platform === 'devtools' || platform === 'mac' || platform === 'windows') {
      wx.showToast({ title: '模拟器跳过扫码', icon: 'none' })
      setTimeout(() => {
        wx.navigateTo({ url: '/pages/checkin/form?scene=1' })
      }, 500)
      return
    }
    wx.scanCode({
      success: (res) => {
        wx.navigateTo({ url: '/pages/checkin/form?scene=' + encodeURIComponent(res.result) })
      },
      fail: (err) => {
        if (!err.errMsg.includes('cancel')) {
          wx.showToast({ title: '扫码失败，请重试', icon: 'none' })
        }
      }
    })
  },

  goToMap() { wx.switchTab({ url: '/pages/map/map' }) },

  goToRecord() {
    if (!this.checkLogin()) return
    wx.switchTab({ url: '/pages/record/record' })
  },

  goToGift() {
    if (!this.checkLogin()) return
    if (!this.checkVerified()) return
    wx.navigateTo({ url: '/pages/gift/index' })
  },

  goToAuth() {
    if (!this.checkLogin()) return
    wx.navigateTo({ url: '/pages/auth/auth' })
  },

  goToNotice() { wx.navigateTo({ url: '/pages/article/list?type=1' }) },

  goToSearch() { wx.navigateTo({ url: '/pages/search/search' }) },

  goToLocationDetail(e) {
    wx.navigateTo({ url: `/pages/location/detail?id=${e.currentTarget.dataset.id}` })
  }
})
