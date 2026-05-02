const { get, post } = require('../../utils/request')

Page({
  data: {
    usablePoints: 0,
    giftList: [],
    loading: true
  },

  onShow() {
    this.loadPoints()
    this.loadGifts()
  },

  onPullDownRefresh() {
    this.loadPoints()
    this.loadGifts()
    setTimeout(() => wx.stopPullDownRefresh(), 1200)
  },

  loadPoints() {
    if (wx.getStorageSync('isLogin') !== true) return
    get('/api/v1/user/me', {}, { noRetry: true }).then(res => {
      this.setData({ usablePoints: res.data.totalPoints || 0 })
    }).catch(() => {})
  },

  loadGifts() {
    this.setData({ loading: true })
    // orgId 可从 storage 读取（如果用户所在机构有专属礼品）
    const orgId = wx.getStorageSync('orgId') || ''
    const url = orgId ? `/api/v1/gifts?orgId=${orgId}` : '/api/v1/gifts'
    get(url, {}, { noRetry: true }).then(res => {
      const list = (res.data || []).map(item => ({
        ...item,
        canExchange: this.data.usablePoints >= item.pointsCost,
        stockText: item.stock === -1 ? '充足' : (item.stock > 0 ? `剩余 ${item.stock}` : '已抢完'),
        stockOut: item.stock === 0
      }))
      this.setData({ giftList: list, loading: false })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  exchangeGift(e) {
    const { id, pointscost, name, stockout } = e.currentTarget.dataset
    const points = parseInt(pointscost)

    if (wx.getStorageSync('isLogin') !== true) {
      return wx.showToast({ title: '请先登录', icon: 'none' })
    }
    if (stockout) {
      return wx.showToast({ title: '该礼品已抢完', icon: 'none' })
    }
    if (this.data.usablePoints < points) {
      return wx.showToast({ title: `积分不足（需 ${points} 分）`, icon: 'none' })
    }

    // 先请求订阅消息授权（用户"允许"后后端才能推送兑换通知）
    // 无论是否授权，都继续弹确认框，不阻断兑换流程
    const TMPL_IDS = [
      wx.getStorageSync('wx_tmpl_gift') || ''
    ].filter(t => t.length > 0)

    const doConfirm = () => {
      wx.showModal({
        title: '确认兑换',
        content: `确认使用 ${points} 积分兑换【${name}】？\n兑换后请携带小程序截图前往教育点领取礼品。`,
        confirmText: '确认兑换',
        cancelText: '再想想',
        success: (res) => {
          if (!res.confirm) return
          wx.showLoading({ title: '兑换中...' })
          post(`/api/v1/gifts/${id}/exchange`, {}).then(() => {
            wx.hideLoading()
            wx.showToast({ title: '兑换成功！', icon: 'success' })
            // 刷新积分和礼品库存
            this.loadPoints()
            this.loadGifts()
          }).catch(err => {
            wx.hideLoading()
            wx.showToast({ title: err.message || '兑换失败', icon: 'none' })
          })
        }
      })
    }

    if (TMPL_IDS.length > 0) {
      let done = false
      const safeConfirm = () => { if (!done) { done = true; doConfirm() } }
      try {
        wx.requestSubscribeMessage({
          tmplIds: TMPL_IDS,
          success: safeConfirm,
          fail:    safeConfirm
        })
      } catch (e) {
        safeConfirm()
      }
      setTimeout(safeConfirm, 3000)
    } else {
      doConfirm()
    }
  }
})
