const { get } = require('../../utils/request')

Page({
  data: {
    location: null,
    loading: true
  },

  onLoad(options) {
    const id = options.id
    if (!id) return
    wx.showLoading({ title: '加载中' })
    get(`/api/v1/locations/${id}`).then(res => {
      wx.hideLoading()
      const loc = res.data
      wx.setNavigationBarTitle({ title: loc.name || '点位详情' })
      this.setData({
        location: {
          ...loc,
          isOpen: loc.status === 1,
          longitude: parseFloat(loc.longitude),
          latitude: parseFloat(loc.latitude)
        },
        loading: false
      })
    }).catch(() => {
      wx.hideLoading()
      this.setData({ loading: false })
    })
  },

  startNav() {
    const loc = this.data.location
    if (!loc) return
    const { platform } = wx.getDeviceInfo()
    if (platform === 'devtools' || platform === 'mac' || platform === 'windows') {
      wx.showToast({ title: '模拟器无法调起导航', icon: 'none' })
      return
    }
    wx.openLocation({
      latitude: loc.latitude,
      longitude: loc.longitude,
      name: loc.name,
      address: loc.address,
      scale: 18
    })
  },

  goCheckin() {
    if (!this.data.location) return
    if (wx.getStorageSync('isLogin') !== true) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    if (wx.getStorageSync('isVerified') !== true) {
      wx.showModal({
        title: '需要实名认证',
        content: '打卡前请先完成实名认证',
        confirmText: '去认证',
        success: res => { if (res.confirm) wx.navigateTo({ url: '/pages/auth/auth' }) }
      })
      return
    }
    wx.navigateTo({ url: '/pages/checkin/form?scene=' + this.data.location.id })
  }
})
