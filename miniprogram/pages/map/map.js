const { get } = require('../../utils/request')

Page({
  data: {
    longitude: 117.02,
    latitude: 36.67,
    scrollToId: '',
    markers: [],
    locations: [],
    loading: true
  },

  onLoad() {
    this.initLocation()
    this.loadLocations()
  },

  initLocation() {
    const { platform } = wx.getDeviceInfo()
    if (platform === 'devtools' || platform === 'mac' || platform === 'windows') return
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        this.setData({ longitude: res.longitude, latitude: res.latitude })
      }
    })
  },

  loadLocations() {
    wx.showLoading({ title: '加载中' })
    get('/api/v1/locations', {}, { noRetry: true }).then(res => {
      wx.hideLoading()
      const locations = (res.data || []).map(loc => ({
        ...loc,
        isOpen: loc.status === 1,
        longitude: parseFloat(loc.longitude),
        latitude: parseFloat(loc.latitude)
      }))
      const markers = locations.map(loc => ({
        id: Number(loc.id),
        latitude: loc.latitude,
        longitude: loc.longitude,
        title: loc.name,
        width: 32,
        height: 32
      }))
      this.setData({ locations, markers, loading: false })
    }).catch(() => { wx.hideLoading(); this.setData({ loading: false }) })
  },

  onMarkerTap(e) {
    const markerId = e.detail.markerId || e.markerId
    this.setData({ scrollToId: 'card-' + markerId })
  },

  goToDetail(e) {
    wx.navigateTo({ url: `/pages/location/detail?id=${e.currentTarget.dataset.id}` })
  },

  goToSearch() {
    wx.navigateTo({ url: '/pages/search/search' })
  },

  startNav(e) {
    const loc = e.currentTarget.dataset.loc
    const { platform } = wx.getDeviceInfo()
    if (platform === 'devtools' || platform === 'mac' || platform === 'windows') {
      wx.showToast({ title: '模拟器无法导航', icon: 'none' })
      return
    }
    wx.openLocation({
      latitude: Number(loc.latitude),
      longitude: Number(loc.longitude),
      name: loc.name,
      address: loc.address,
      scale: 18
    })
  }
})
