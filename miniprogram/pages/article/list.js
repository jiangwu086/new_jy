const { get } = require('../../utils/request')

Page({
  data: {
    list: [],
    loading: false
  },

  onLoad(options) {
    // type 参数：1-政策说明 2-理赔指南 3-警示案例
    // 也兼容旧版字符串参数 'policy' 等
    const typeMap = {
      '1': { title: '政策说明', type: 1 },
      '2': { title: '理赔指南', type: 2 },
      '3': { title: '警示案例', type: 3 },
      'policy': { title: '政策与指南', type: null },
      'notice': { title: '系统公告', type: 1 }
    }
    const cfg = typeMap[options.type] || { title: '文章列表', type: null }
    wx.setNavigationBarTitle({ title: cfg.title })
    // 记下当前类型，下拉刷新时复用
    this._currentType = cfg.type
    this.loadList(cfg.type)
  },

  onPullDownRefresh() {
    this.loadList(this._currentType)
    setTimeout(() => wx.stopPullDownRefresh(), 1200)
  },

  loadList(type) {
    this.setData({ loading: true })
    const params = {}
    if (type != null) params.type = type
    get('/api/v1/articles', params).then(res => {
      this.setData({
        list: (res.data || []).map(a => ({
          ...a,
          date: a.createTime ? a.createTime.slice(0, 10) : ''
        })),
        loading: false
      })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  goToDetail(e) {
    wx.navigateTo({ url: `/pages/article/detail?id=${e.currentTarget.dataset.id}` })
  }
})
