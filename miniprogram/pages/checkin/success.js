Page({
  data: {
    locationName: '',
    checkinTime: '',
    pointsEarned: 0,
    currentCount: 0,
    maxCount: 5
  },

  onLoad(options) {
    const now = new Date()
    const t = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ` +
              `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`
    this.setData({
      locationName: decodeURIComponent(options.locName || ''),
      checkinTime: t,
      pointsEarned: parseInt(options.points) || 0,
      currentCount: parseInt(options.current) || 0,
      maxCount: parseInt(options.max) || 5
    })
  },

  goToRecord() {
    wx.switchTab({ url: '/pages/record/record' })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
