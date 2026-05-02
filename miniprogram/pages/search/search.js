const { get } = require('../../utils/request')

Page({
  data: {
    keyword: '',
    hasSearched: false,
    results: [],
    allLocations: []
  },

  onLoad() {
    this.loadAll()
  },

  onPullDownRefresh() {
    this.loadAll().finally(() => {
      // 如果之前已搜索过，刷新后基于最新点位重新筛选
      if (this.data.hasSearched && this.data.keyword) this.doSearch()
      wx.stopPullDownRefresh()
    })
  },

  /** 预加载全部点位，用于本地搜索 */
  loadAll() {
    return get('/api/v1/locations').then(res => {
      this.setData({ allLocations: res.data || [] })
    }).catch(() => {})
  },

  onInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  doSearch() {
    const kw = this.data.keyword.trim().toLowerCase()
    if (!kw) return
    const results = this.data.allLocations.filter(loc =>
      (loc.name && loc.name.toLowerCase().includes(kw)) ||
      (loc.address && loc.address.toLowerCase().includes(kw)) ||
      (loc.districtCode && loc.districtCode.includes(kw))
    )
    this.setData({ hasSearched: true, results })
  },

  goToDetail(e) {
    wx.navigateTo({ url: `/pages/location/detail?id=${e.currentTarget.dataset.id}` })
  },

  goBack() {
    wx.navigateBack()
  }
})
