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

  // tab 切换不会重新触发 onLoad，必须在 onShow 里再拉一次，
  // 否则管理后台新加的点位在地图页永远看不到
  onShow() {
    this.loadLocations({ silent: true })
  },

  onPullDownRefresh() {
    this.loadLocations({ manual: true })
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

  /**
   * 拉取教育点列表
   * @param {object} opts
   * @param {boolean} opts.manual - 用户主动下拉刷新，给 toast 反馈
   * @param {boolean} opts.silent - 后台刷新（onShow），不弹 loading
   */
  loadLocations(opts = {}) {
    const { manual = false, silent = false } = opts
    if (!silent && !manual) wx.showLoading({ title: '加载中' })
    if (manual) wx.showNavigationBarLoading()

    get('/api/v1/locations', {}, { noRetry: true }).then(res => {
      const raw = res.data || []
      console.log('[map] /api/v1/locations 返回', raw.length, '条:', raw)

      const locations = raw.map(loc => ({
        ...loc,
        isOpen: loc.status === 1,
        longitude: parseFloat(loc.longitude),
        latitude: parseFloat(loc.latitude)
      }))
      const markers = locations
        .filter(l => !isNaN(l.longitude) && !isNaN(l.latitude))
        .map(loc => ({
          id: Number(loc.id),
          latitude: loc.latitude,
          longitude: loc.longitude,
          title: loc.name,
          width: 32,
          height: 32
        }))
      this.setData({ locations, markers, loading: false })

      if (manual) {
        wx.showToast({
          title: locations.length > 0 ? `已刷新 ${locations.length} 个点位` : '暂无点位',
          icon: 'none',
          duration: 1500
        })
      }
    }).catch(err => {
      console.error('[map] 加载点位失败', err)
      this.setData({ loading: false })
      wx.showToast({
        title: '加载失败：' + (err && err.message || '网络异常'),
        icon: 'none',
        duration: 2500
      })
    }).finally(() => {
      if (!silent && !manual) wx.hideLoading()
      if (manual) {
        wx.hideNavigationBarLoading()
        wx.stopPullDownRefresh()
      }
    })
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
