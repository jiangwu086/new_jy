const { get } = require('../../utils/request')

Page({
  data: {
    article: null,
    loading: true
  },

  onLoad(options) {
    const id = options.id
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1000)
      return
    }
    wx.showLoading({ title: '加载中' })
    get(`/api/v1/articles/${id}`).then(res => {
      wx.hideLoading()
      const article = res.data
      wx.setNavigationBarTitle({ title: article.title || '文章详情' })
      this.setData({
        article: {
          ...article,
          date: article.createTime ? article.createTime.slice(0, 10) : ''
        },
        loading: false
      })
    }).catch(() => {
      wx.hideLoading()
      this.setData({ loading: false })
      wx.showToast({ title: '加载失败', icon: 'none' })
    })
  }
})
