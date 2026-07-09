const { get, post, put, uploadFile, resolveUrl } = require('../../utils/request')

Page({
  data: {
    isLogin: wx.getStorageSync('isLogin') === true,  // 同步读取，首帧即生效，避免闪屏
    nickName: '',
    avatarUrl: '',
    avatarLetter: '客',          // 头像占位文字：姓氏 → 昵称首字 → 默认「客」
    realName: '',
    phone: '',
    totalPoints: 0,
    usablePoints: 0,
    isVerified: false,
    verifyStatus: 0,   // 0未提交 1审核中 2已通过 3已拒绝
    // 换绑手机号弹窗
    showPhoneModal: false,
    newPhone: '',
    newPhoneCode: '',
    phoneCdSecs: 0
  },

  _phoneCdTimer: null,

  onShow() {
    const isLogin = wx.getStorageSync('isLogin') === true
    this.setData({ isLogin })
    if (!isLogin) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.loadUserInfo()
  },

  onPullDownRefresh() {
    if (wx.getStorageSync('isLogin') !== true) {
      wx.stopPullDownRefresh(); return
    }
    this.loadUserInfo()
    setTimeout(() => wx.stopPullDownRefresh(), 1200)
  },

  loadUserInfo() {
    // 先用本地缓存快速渲染（phone 不读旧缓存，防止账号切换后残留错误号码）
    this.setData({
      realName:     wx.getStorageSync('realName')     || '',
      phone:        '',   // 等接口返回后再填，避免旧账号残留
      verifyStatus: wx.getStorageSync('verifyStatus') || 0,
      isVerified:   wx.getStorageSync('isVerified') === true
    })

    // 从接口拉取最新数据（含 realName/isVerified，防止换设备/重装后状态丢失）
    get('/api/v1/user/me').then(res => {
      const user = res.data
      console.log('[profile] /api/v1/user/me 返回:', user)
      const isVerified = user.verifyStatus === 2

      // 全量覆盖本地缓存，防止账号切换后数据残留
      wx.setStorageSync('isVerified',    isVerified)
      wx.setStorageSync('verifyStatus',  user.verifyStatus ?? 0)
      wx.setStorageSync('phone',         user.phone || '')
      if (user.realName)     wx.setStorageSync('realName',     user.realName)
      if (user.idCardMasked) wx.setStorageSync('idCardMasked', user.idCardMasked)
      if (user.rejectReason) wx.setStorageSync('rejectReason', user.rejectReason)

      // 手机号脱敏展示：186****6024
      const rawPhone = user.phone || ''
      const phone = rawPhone.length === 11
        ? rawPhone.slice(0, 3) + '****' + rawPhone.slice(7)
        : rawPhone

      this.setData({
        nickName:     user.nickName    || '骑手用户',
        avatarUrl:    resolveUrl(user.avatarUrl || ''),     // 相对路径 → 完整 URL，否则 image 加载 500
        realName:     user.realName    || '',
        avatarLetter: this.computeAvatarLetter(user.realName, user.nickName),
        phone,
        totalPoints:  user.totalPoints  || 0,
        usablePoints: user.usablePoints || user.totalPoints || 0,
        verifyStatus: user.verifyStatus ?? 0,
        isVerified
      })
    }).catch(() => {
      // 网络失败：用登录时存下的手机号兜底，避免显示空白
      const raw = wx.getStorageSync('phone') || ''
      const phone = raw.length === 11
        ? raw.slice(0, 3) + '****' + raw.slice(7)
        : raw
      this.setData({
        phone,
        realName:     wx.getStorageSync('realName')     || '',
        verifyStatus: wx.getStorageSync('verifyStatus') || 0,
        isVerified:   wx.getStorageSync('isVerified') === true
      })
    })
  },

  goToRecord() {
    wx.switchTab({ url: '/pages/record/record' })
  },

  goToGift() {
    wx.navigateTo({ url: '/pages/gift/index' })
  },

  goToPoints() {
    wx.navigateTo({ url: '/pages/points/index' })
  },

  goToList(e) {
    const type = e.currentTarget.dataset.type
    wx.navigateTo({ url: `/pages/article/list?type=${type}` })
  },

  goToAuth() {
    wx.navigateTo({ url: '/pages/auth/auth' })
  },

  // ── 头像 ──────────────────────────────────────────────────

  /** 计算头像占位文字：姓氏 → 昵称首字 → 默认「客」 */
  computeAvatarLetter(realName, nickName) {
    if (realName && realName.length > 0) return realName[0]
    if (nickName && nickName.length > 0) return nickName[0]
    return '客'
  },

  /** 点击头像 → 选图 → 上传 → 更新 avatarUrl */
  changeAvatar() {
    if (wx.getStorageSync('isLogin') !== true) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const tempPath = res.tempFiles && res.tempFiles[0] && res.tempFiles[0].tempFilePath
        if (!tempPath) return
        wx.showLoading({ title: '上传中...', mask: true })
        uploadFile('/api/v1/checkin/upload-photo', tempPath).then(data => {
          if (!data || !data.url) throw new Error('上传失败')
          // 后端存相对路径，给 image 标签用前要补全为完整 URL
          return put('/api/v1/user/profile', { avatarUrl: data.url }).then(() => data.url)
        }).then(url => {
          wx.hideLoading()
          this.setData({ avatarUrl: resolveUrl(url) })
          wx.showToast({ title: '头像已更新', icon: 'success' })
        }).catch(err => {
          wx.hideLoading()
          wx.showToast({ title: err.message || '上传失败', icon: 'none' })
        })
      }
    })
  },

  // ── 换绑/绑定手机号 ───────────────────────────────────────
  showChangePhone() {
    this.setData({ showPhoneModal: true, newPhone: '', newPhoneCode: '' })
  },
  hideChangePhone() {
    this.setData({ showPhoneModal: false })
  },
  // 拦截弹窗内部的点击事件冒泡，防止触发 mask 的 hideChangePhone。
  // catchtap="" 在新版微信基础库下不再作为占位生效，必须给个真实方法名。
  onModalBoxTap() {
    /* 仅用于阻止冒泡，无业务逻辑 */
  },
  onNewPhoneInput(e)  { this.setData({ newPhone: e.detail.value }) },
  onNewCodeInput(e)   { this.setData({ newPhoneCode: e.detail.value }) },

  sendNewPhoneCode() {
    const phone = this.data.newPhone.trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' }); return
    }
    // 换绑场景使用 reset 模板（已登录用户重置手机号）
    post('/api/v1/auth/send-sms', { phone, type: 'reset' })
      .then(() => {
        wx.showToast({ title: '验证码已发送', icon: 'success' })
        let secs = 60
        this.setData({ phoneCdSecs: secs })
        this._phoneCdTimer = setInterval(() => {
          secs--
          if (secs <= 0) { clearInterval(this._phoneCdTimer); this.setData({ phoneCdSecs: 0 }) }
          else this.setData({ phoneCdSecs: secs })
        }, 1000)
      })
      .catch(err => wx.showToast({ title: err.message || '发送失败', icon: 'none' }))
  },

  confirmChangePhone() {
    const phone = this.data.newPhone.trim()
    const code  = this.data.newPhoneCode.trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' }); return
    }
    if (code.length !== 6) {
      wx.showToast({ title: '请输入6位验证码', icon: 'none' }); return
    }
    post('/api/v1/user/change-phone', { phone, code })
      .then(() => {
        wx.setStorageSync('phone', phone)
        const masked = phone.slice(0, 3) + '****' + phone.slice(7)
        this.setData({ phone: masked, showPhoneModal: false })
        wx.showToast({ title: '手机号已更新', icon: 'success' })
      })
      .catch(err => wx.showToast({ title: err.message || '验证码错误或已过期', icon: 'none' }))
  },

  logout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('isLogin')
          wx.removeStorageSync('isVerified')
          wx.removeStorageSync('realName')
          wx.removeStorageSync('idCardMasked')
          wx.removeStorageSync('phone')
          wx.reLaunch({ url: '/pages/index/index' })
        }
      }
    })
  }
})
