const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    activeTab: 'wx',
    agreed: false,
    wxLoading: false,
    phone: '',
    smsCode: '',
    codeSending: false,
    countdown: 0,
    phoneLoading: false,
    statusBarHeight: 0,
  },

  _countdownTimer: null,

  onLoad() {
    const { statusBarHeight } = wx.getWindowInfo()
    this.setData({ statusBarHeight })
  },

  onUnload() {
    if (this._countdownTimer) clearInterval(this._countdownTimer)
  },

  switchTab(e)      { this.setData({ activeTab: e.currentTarget.dataset.tab }) },
  toggleAgreement() { this.setData({ agreed: !this.data.agreed }) },
  openService()     { wx.navigateTo({ url: '/pages/agreement/service' }) },
  openPrivacy()     { wx.navigateTo({ url: '/pages/agreement/privacy' }) },
  goBack()          { wx.switchTab({ url: '/pages/index/index' }) },

  // ── 微信授权登录（真机 + 开发者工具统一入口）────────────────
  // 真机：open-type="getPhoneNumber" 触发，拿到 phoneCode + wx.login code
  // 开发者工具：getPhoneNumber 不可用，errMsg 不为 ok，降级为仅 wx.login（无手机号）
  onGetPhoneNumber(e) {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先勾选协议', icon: 'none' })
      return
    }

    const phoneCode = e.detail.errMsg === 'getPhoneNumber:ok' ? e.detail.code : null

    this.setData({ wxLoading: true })
    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          this.setData({ wxLoading: false })
          wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
          return
        }
        this._doWxLogin(loginRes.code, phoneCode)
      },
      fail: () => {
        this.setData({ wxLoading: false })
        wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
      }
    })
  },

  _doWxLogin(loginCode, phoneCode) {
    request.post('/api/v1/auth/wx-login', { loginCode, phoneCode })
      .then(res => {
        this.setData({ wxLoading: false })
        this._finishLogin(res.data)
      })
      .catch(err => {
        this.setData({ wxLoading: false })
        wx.showToast({ title: err.message || '登录失败', icon: 'none' })
      })
  },

  // ── 手机号验证码登录 ────────────────────────────────────────
  onPhoneInput(e) { this.setData({ phone: e.detail.value }) },
  onCodeInput(e)  { this.setData({ smsCode: e.detail.value }) },

  sendSmsCode() {
    const phone = this.data.phone.trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (this.data.countdown > 0) return
    this.setData({ codeSending: true })
    request.post('/api/v1/auth/send-sms', { phone })
      .then(() => {
        this.setData({ codeSending: false })
        wx.showToast({ title: '验证码已发送', icon: 'success' })
        this._startCountdown()
      })
      .catch(err => {
        this.setData({ codeSending: false })
        wx.showToast({ title: err.message || '发送失败，请重试', icon: 'none' })
      })
  },

  _startCountdown() {
    this.setData({ countdown: 60 })
    this._countdownTimer = setInterval(() => {
      const n = this.data.countdown - 1
      if (n <= 0) { clearInterval(this._countdownTimer); this.setData({ countdown: 0 }) }
      else        { this.setData({ countdown: n }) }
    }, 1000)
  },

  doPhoneLogin() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先勾选协议', icon: 'none' })
      return
    }
    const phone = this.data.phone.trim()
    const code  = this.data.smsCode.trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (code.length !== 6) {
      wx.showToast({ title: '请输入6位验证码', icon: 'none' })
      return
    }
    this.setData({ phoneLoading: true })
    request.post('/api/v1/auth/phone-login', { phone, code })
      .then(res => {
        this.setData({ phoneLoading: false })
        this._finishLogin(res.data)
      })
      .catch(err => {
        this.setData({ phoneLoading: false })
        wx.showToast({ title: err.message || '验证码错误或已过期', icon: 'none' })
      })
  },

  _finishLogin(data) {
    // 先清除上一个账号的所有缓存，防止换账号后数据混用
    const keepKeys = ['isLogin', 'token', 'userId', 'phone', 'nickName', 'avatarUrl']
    ;['realName', 'idCardMasked', 'isVerified', 'verifyStatus', 'rejectReason'].forEach(k => {
      wx.removeStorageSync(k)
    })

    // 写入新账号数据
    wx.setStorageSync('isLogin',  true)
    wx.setStorageSync('token',    data.token)
    wx.setStorageSync('userId',   data.userId)
    wx.setStorageSync('phone',    data.phone    || '')
    wx.setStorageSync('nickName', data.nickName || '')
    wx.setStorageSync('avatarUrl',data.avatarUrl|| '')
    app.globalData.userInfo = data
    wx.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => { wx.switchTab({ url: '/pages/index/index' }) }, 800)
  }
})
