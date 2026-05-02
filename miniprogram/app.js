const { get, setNetworkConnected } = require('./utils/request')

// ─── 订阅消息模板ID ────────────────────────────────────────────
// 在微信公众平台「功能 → 订阅消息」申请模板后，将模板ID填入此处
// 申请地址：https://mp.weixin.qq.com → 功能 → 订阅消息
const WX_TMPL_CHECKIN = 'YsrXmHlp_saK8CNnl_HttHQ-kLHkDpb9o2oPgcrMJiw'   // 打卡成功通知
const WX_TMPL_GIFT    = 'cQ5oalByIPAaIq0Hwl10Df1w2pCJxgJ7tZrei_c37oM'   // 礼品兑换成功通知

App({
  onLaunch() {
    // 将模板ID写入 storage，供各页面的 requestSubscribeMessage 调用
    wx.setStorageSync('wx_tmpl_checkin', WX_TMPL_CHECKIN)
    wx.setStorageSync('wx_tmpl_gift',    WX_TMPL_GIFT)

    // ── 网络状态监听 ──────────────────────────────────────────
    // 启动时获取当前网络状态，初始化 request.js 缓存
    wx.getNetworkType({
      success: (res) => {
        setNetworkConnected(res.networkType !== 'none')
      }
    })
    // 实时监听网络变化，同步给 request.js；弱网时显示顶部提示条
    wx.onNetworkStatusChange((res) => {
      setNetworkConnected(res.isConnected)
      if (!res.isConnected) {
        wx.showToast({ title: '当前无网络连接', icon: 'none', duration: 2000 })
      }
    })

    const token = wx.getStorageSync('token')
    if (token) {
      // token 存在，刷新用户信息到全局
      get('/api/v1/user/me', {}, { noRetry: true }).then(res => {
        this.globalData.userInfo = res.data
        wx.setStorageSync('isLogin', true)
      }).catch(() => {
        // token 失效，清除所有登录态
        wx.removeStorageSync('token')
        wx.removeStorageSync('isLogin')
        wx.removeStorageSync('isVerified')
      })
    }
  },
  globalData: {
    userInfo: null
  }
})
