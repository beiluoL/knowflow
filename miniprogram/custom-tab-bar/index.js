Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/index/index', text: '首页', icon: '🏠' },
      { pagePath: '/pages/knowledge/index', text: '知识库', icon: '📚' },
      { pagePath: '/pages/chat/index', text: 'AI问答', icon: '🤖' },
      { pagePath: '/pages/flashcard/index', text: '闪卡', icon: '🔖' },
      { pagePath: '/pages/profile/index', text: '我的', icon: '👤' }
    ]
  },

  methods: {
    switchTab(e) {
      const { index } = e.currentTarget.dataset
      const { list } = this.data
      const url = list[index].pagePath
      wx.switchTab({ url })
    }
  }
})
