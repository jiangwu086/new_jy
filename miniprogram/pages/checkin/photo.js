const { post, uploadFile } = require('../../utils/request')

Page({
  data: {
    locationId: '',
    locationName: '',
    longitude: 0,
    latitude: 0,
    currentTime: '',
    submitting: false
  },

  timer: null,

  onLoad(options) {
    this.setData({
      locationId: options.locId || '',
      locationName: decodeURIComponent(options.locName || '教育点')
    })
    this.startClock()
    this.getLocation()
  },

  onUnload() {
    if (this.timer) clearInterval(this.timer)
  },

  startClock() {
    const fmt = () => {
      const now = new Date()
      return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ` +
             `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`
    }
    this.setData({ currentTime: fmt() })
    this.timer = setInterval(() => this.setData({ currentTime: fmt() }), 1000)
  },

  getLocation() {
    const { platform } = wx.getDeviceInfo()
    if (platform === 'devtools' || platform === 'mac' || platform === 'windows') {
      // 模拟器使用济南历下区坐标
      this.setData({ longitude: 117.021, latitude: 36.671 })
      return
    }
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      success: (res) => {
        this.setData({ longitude: res.longitude, latitude: res.latitude })
      },
      fail: () => wx.showToast({ title: '无法获取定位，请授权位置权限', icon: 'none' })
    })
  },

  takePhoto() {
    if (this.data.submitting) return
    const { platform } = wx.getDeviceInfo()
    const isDevtools = platform === 'devtools' || platform === 'mac' || platform === 'windows'

    // 真机：直接对页面内 <camera> 组件拍照（不调起系统相机、不弹对话框）
    if (!isDevtools) {
      const ctx = wx.createCameraContext()
      ctx.takePhoto({
        quality: 'normal',
        success: (res) => this.uploadAndSubmit(res.tempImagePath),
        fail: (err) => {
          console.warn('camera takePhoto fail, fallback to chooseMedia', err)
          // 极少数机型 / 权限异常时，兜底让用户调起相机应用
          this.fallbackChooseCamera()
        }
      })
      return
    }

    // 开发者工具：直接调起相机（不弹"拍摄/相册"二选一对话框）
    this.fallbackChooseCamera()
  },

  /** 兜底：调起系统相机直接拍照（不进相册） */
  fallbackChooseCamera() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],   // 只允许相机，避免弹出"拍摄/相册"二级菜单
      camera: 'front',
      success: (res) => {
        const tempPath = res.tempFiles && res.tempFiles[0] && res.tempFiles[0].tempFilePath
        if (tempPath) this.uploadAndSubmit(tempPath)
      },
      fail: () => wx.showToast({ title: '已取消', icon: 'none' })
    })
  },

  /**
   * 拍/选完照后：先上传到后端拿到可访问 URL，再调 submit 接口
   */
  uploadAndSubmit(tempPath) {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    wx.showLoading({ title: '上传照片...', mask: true })
    uploadFile('/api/v1/checkin/upload-photo', tempPath).then(data => {
      wx.hideLoading()
      if (!data || !data.url) {
        this.setData({ submitting: false })
        wx.showToast({ title: '照片上传失败', icon: 'none' })
        return
      }
      this.doSubmit(data.url)
    }).catch(err => {
      wx.hideLoading()
      this.setData({ submitting: false })
      wx.showModal({
        title: '照片上传失败',
        content: err.message || '请检查网络后重试',
        showCancel: false
      })
    })
  },

  doSubmit(photoUrl) {
    this.setData({ submitting: true })
    wx.showLoading({ title: '打卡提交中...' })
    post('/api/v1/checkin/submit', {
      locationId: Number(this.data.locationId),
      longitude: this.data.longitude,
      latitude: this.data.latitude,
      photoUrl
    }).then(res => {
      wx.hideLoading()
      const d = res.data
      wx.redirectTo({
        url: `/pages/checkin/success?checkinId=${d.checkinId}&points=${d.pointsEarned}` +
             `&current=${d.currentCount}&max=${d.maxCount}` +
             `&locName=${encodeURIComponent(this.data.locationName)}`
      })
    }).catch(err => {
      wx.hideLoading()
      this.setData({ submitting: false })
      wx.showModal({
        title: '打卡失败',
        content: err.message || '提交失败，请重试',
        showCancel: false
      })
    })
  },

  error(e) {
    console.log('camera error', e.detail)
    wx.showModal({
      title: '摄像头授权失败',
      content: '请在小程序设置中允许使用摄像头',
      showCancel: false,
      success: () => wx.navigateBack()
    })
  }
})
