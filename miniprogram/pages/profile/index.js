Page({
  data: {
    badges: [
      { name: '连续7天', icon: '🔥', bg: 'linear-gradient(135deg, #F59E0B 0%, #FBBF24 100%)', color: '#1A1D23' },
      { name: '百篇阅读', icon: '📖', bg: 'linear-gradient(135deg, #3B6FE0 0%, #5B8FE8 100%)', color: '#1A1D23' },
      { name: '代码达人', icon: '💻', bg: 'linear-gradient(135deg, #10B981 0%, #34D399 100%)', color: '#1A1D23' },
      { name: '千篇阅读', icon: '📚', bg: '#E8ECF1', color: '#6B7280' },
      { name: '社区之星', icon: '⭐', bg: '#E8ECF1', color: '#6B7280' },
      { name: '全能学者', icon: '🎓', bg: '#E8ECF1', color: '#6B7280' },
    ]
  },

  onLoad() {
    console.log('个人中心页面加载')
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 4 })
    }
  },

  goToStatistics() {
    wx.navigateTo({
      url: '/pages/statistics/index'
    })
  },

  goToAgreement() {
    wx.navigateTo({
      url: '/pages/agreement/index'
    })
  },

  goToPrivacy() {
    wx.navigateTo({
      url: '/pages/privacy/index'
    })
  }
})