const { get, put } = require('../../utils/request')

Page({
  data: {
    verifyStatus: 0,      // 0未提交 1审核中 2已通过 3已拒绝
    rejectReason: '',
    realName: '',
    idCard: '',
    idCardMasked: '',
    isVerifying: false,
    submitting: false
  },

  onLoad() {
    this.loadStatus()
  },

  onShow() {
    this.loadStatus()
  },

  loadStatus() {
    // 先从本地缓存快速渲染
    const cached = wx.getStorageSync('verifyStatus') || 0
    this.setData({
      verifyStatus:  cached,
      realName:      wx.getStorageSync('realName')      || '',
      idCardMasked:  wx.getStorageSync('idCardMasked')  || '',
      rejectReason:  wx.getStorageSync('rejectReason')  || ''
    })

    // 再从接口拉最新状态（防止审核结果未同步）
    if (wx.getStorageSync('isLogin') === true) {
      get('/api/v1/user/me').then(res => {
        const { verifyStatus, verifyRejectReason, realName, idCard } = res.data
        wx.setStorageSync('verifyStatus', verifyStatus || 0)
        wx.setStorageSync('rejectReason', verifyRejectReason || '')
        if (realName) wx.setStorageSync('realName', realName)
        if (verifyStatus === 2) wx.setStorageSync('isVerified', true)

        // 优先用接口返回的脱敏串，兜底读本地缓存
        const idCardMasked = res.data.idCardMasked
          || (idCard ? idCard.replace(/^(.{4}).+(.{4})$/, '$1**********$2') : null)
          || wx.getStorageSync('idCardMasked')
          || ''
        if (idCardMasked) wx.setStorageSync('idCardMasked', idCardMasked)
        this.setData({
          verifyStatus:  verifyStatus || 0,
          rejectReason:  verifyRejectReason || '',
          realName:      realName || '',
          idCardMasked
        })
      }).catch(() => {})
    }
  },

  startVerify() { this.setData({ isVerifying: true }) },

  // 审核被拒绝后重新提交
  resubmit() {
    this.setData({ isVerifying: true, verifyStatus: 0, idCard: '' })
  },

  onNameInput(e) { this.setData({ realName: e.detail.value }) },
  onIdCardInput(e) { this.setData({ idCard: e.detail.value }) },

  submitAuth() {
    const realName = this.data.realName.trim()
    const idCard   = this.data.idCard.trim()

    if (!realName) return wx.showToast({ title: '请输入真实姓名', icon: 'none' })
    if (!/^\d{17}[\dXx]$/.test(idCard)) return wx.showToast({ title: '请输入有效身份证号', icon: 'none' })

    this.setData({ submitting: true })
    wx.showLoading({ title: '提交中...' })

    put('/api/v1/user/profile', { realName, idCard })
      .then(() => {
        wx.hideLoading()
        const idCardMasked = idCard.replace(/^(.{4}).+(.{4})$/, '$1**********$2')
        wx.setStorageSync('verifyStatus',  1)
        wx.setStorageSync('realName',      realName)
        wx.setStorageSync('idCardMasked',  idCardMasked)
        wx.setStorageSync('isVerified',    false)
        this.setData({
          submitting: false,
          verifyStatus: 1,
          isVerifying: false,
          idCardMasked
        })
        wx.showToast({ title: '提交成功，等待审核', icon: 'success' })
      })
      .catch(err => {
        wx.hideLoading()
        this.setData({ submitting: false })
        wx.showToast({ title: err.message || '提交失败，请重试', icon: 'none' })
      })
  }
})
