const { get } = require('../../utils/request')

Page({
  data: {
    locationId: '',
    locationName: '加载中...',
    idCardMasked: '',
    phone: '',
    currentCount: 0,
    maxCount: 5,
    canCheckin: false,
    loading: true
  },

  onLoad(options) {
    if (!this.checkAuthStatus()) return

    // 优先从 options.scene 取点位ID
    // 扫描小程序码时 scene = "lid=123"，直接跳转时 scene = "123" 或由 query 传入
    let locationId = '1'
    if (options.scene) {
      const decoded = decodeURIComponent(options.scene)
      const match = decoded.match(/(?:lid=)?(\d+)/)
      if (match) locationId = match[1]
    } else if (options.locationId) {
      locationId = options.locationId
    }
    this.setData({
      locationId,
      idCardMasked: wx.getStorageSync('idCardMasked') || '',
      phone: wx.getStorageSync('phone') || ''
    })
    this.initCheckin(locationId)
  },

  checkAuthStatus() {
    if (wx.getStorageSync('isLogin') !== true) {
      wx.navigateTo({ url: '/pages/login/login' })
      return false
    }
    if (wx.getStorageSync('isVerified') !== true) {
      wx.showModal({
        title: '请先完成实名认证',
        content: '现场打卡前需要完成实名认证，审核通过后即可打卡。',
        confirmText: '去认证',
        showCancel: false,
        success: () => wx.redirectTo({ url: '/pages/auth/auth' })
      })
      return false
    }
    return true
  },

  initCheckin(locationId) {
    wx.showLoading({ title: '检查打卡资格...' })
    get('/api/v1/checkin/init', { locationId }).then(res => {
      wx.hideLoading()
      const d = res.data
      this.setData({
        locationName: d.locationName,
        currentCount: d.currentCount,
        maxCount: d.maxCount,
        canCheckin: d.canCheckin,
        loading: false
      })
      if (!d.canCheckin) {
        wx.showModal({
          title: '本周期已达上限',
          content: `您本周期已打卡 ${d.currentCount} 次（上限 ${d.maxCount} 次），下个周期再来！`,
          showCancel: false,
          success: () => wx.navigateBack()
        })
      }
    }).catch(err => {
      wx.hideLoading()
      wx.showModal({
        title: '提示',
        content: err.message || '点位信息获取失败，请确认点位是否有效',
        showCancel: false,
        success: () => wx.navigateBack()
      })
    })
  },

  goToNext() {
    if (!this.data.canCheckin) return

    // 在跳转拍照前请求订阅消息授权（用户点击"允许"后后端才能推送通知）
    // TMPL_IDS 填写在微信公众平台申请的模板ID，最多一次申请3个
    const TMPL_IDS = [
      wx.getStorageSync('wx_tmpl_checkin') || '',  // 打卡成功通知模板ID
      wx.getStorageSync('wx_tmpl_gift')    || '',  // 礼品兑换成功通知模板ID
    ].filter(id => id.length > 0)

    const doNavigate = () => {
      wx.navigateTo({
        url: '/pages/checkin/photo?locId=' + this.data.locationId
              + '&locName=' + encodeURIComponent(this.data.locationName)
      })
    }

    if (TMPL_IDS.length === 0) {
      doNavigate()
      return
    }

    // 模拟器对 requestSubscribeMessage 支持不稳定，加 try-catch + 超时兜底
    let done = false
    const safeNavigate = () => { if (!done) { done = true; doNavigate() } }

    const triggerSubscribe = () => {
      try {
        wx.requestSubscribeMessage({
          tmplIds: TMPL_IDS,
          success: safeNavigate,
          fail:    safeNavigate
        })
      } catch (e) {
        safeNavigate()
      }
      // 3 秒后无论如何都跳转（防止模拟器下弹窗卡死）
      setTimeout(safeNavigate, 3000)
    }

    // 微信原生订阅弹窗只显示模板标题，没有上下文说明。
    // 这里先弹一个友好的解释，告诉用户为什么要订阅、订阅之后会收到什么。
    wx.showModal({
      title: '开启服务通知',
      content:
        '为了让您及时了解打卡进度与积分动态，我们需要向您发送以下两类微信服务通知：\n\n' +
        '· 打卡成功提醒 — 现场拍照核验通过后立即告知\n' +
        '· 礼品兑换提醒 — 积分兑换或核销成功时告知\n\n' +
        '点击「同意」后，微信会再询问一次具体的订阅范围；通知不会用于营销，您可随时在微信「服务通知」中关闭。',
      confirmText: '同意并继续',
      cancelText:  '暂不订阅',
      confirmColor: '#3b82f6',
      success: (res) => {
        if (res.confirm) {
          triggerSubscribe()
        } else {
          // 用户拒绝订阅说明，仍然让他们继续打卡流程，只是不申请订阅
          doNavigate()
        }
      },
      fail: doNavigate
    })
  }
})
