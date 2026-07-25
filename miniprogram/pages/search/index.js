Page({
  data: {
    searchText: '',
    searched: false,
    hotTags: ['Java集合', 'HashMap', 'Python基础', 'Redis', 'MySQL', '微服务', '算法', '前端'],
    history: ['Java集合', 'HashMap扩容', 'Python基础'],
    results: []
  },

  onLoad() {
    console.log('搜索页面加载')
    // 从本地存储读取历史记录
    const history = wx.getStorageSync('searchHistory') || []
    if (history.length > 0) {
      this.setData({ history })
    }
  },

  // 返回上一页
  goBack() {
    wx.navigateBack()
  },

  // 清空搜索框
  clearSearch() {
    this.setData({ 
      searchText: '',
      results: [],
      searched: false
    })
  },

  // 清空历史记录
  clearHistory() {
    this.setData({ history: [] })
    wx.removeStorageSync('searchHistory')
    wx.showToast({
      title: '已清空历史',
      icon: 'none'
    })
  },

  // 刷新热门标签
  refreshHotTags() {
    const allTags = ['Java集合', 'HashMap', 'Python基础', 'Redis', 'MySQL', '微服务', '算法', '前端', 'Spring', 'Docker', 'K8s', '设计模式']
    const randomTags = allTags.sort(() => Math.random() - 0.5).slice(0, 8)
    this.setData({ hotTags: randomTags })
    wx.showToast({
      title: '已刷新',
      icon: 'none',
      duration: 1000
    })
  },

  // 搜索输入
  onSearchInput(e) {
    const value = e.detail.value.trim()
    this.setData({ 
      searchText: value,
      searched: false
    })
    
    if (value) {
      // 模拟搜索结果
      this.searchContent(value)
    } else {
      this.setData({ results: [] })
    }
  },

  // 执行搜索
  doSearch() {
    const { searchText } = this.data
    if (!searchText) return
    
    this.searchContent(searchText)
    this.saveToHistory(searchText)
  },

  // 搜索内容
  searchContent(keyword) {
    // 模拟搜索结果
    const mockResults = [
      { id: 1, title: `${keyword} 原理详解`, summary: `深入分析${keyword}的核心原理和实现方式，帮助理解底层机制...`, category: '技术', time: '2天前' },
      { id: 2, title: `${keyword} 最佳实践`, summary: `总结${keyword}的最佳使用方式和常见问题解决方案...`, category: '实践', time: '1周前' },
      { id: 3, title: `${keyword} 源码解析`, summary: `从源码角度深入分析${keyword}的设计思路和实现细节...`, category: '源码', time: '1个月前' },
    ]
    
    this.setData({ 
      results: mockResults,
      searched: true
    })
  },

  // 通过标签搜索
  searchByTag(e) {
    const { keyword } = e.currentTarget.dataset
    this.setData({ searchText: keyword })
    this.searchContent(keyword)
    this.saveToHistory(keyword)
  },

  // 保存到历史记录
  saveToHistory(keyword) {
    let history = this.data.history.filter(item => item !== keyword)
    history.unshift(keyword)
    history = history.slice(0, 10) // 最多保留10条
    
    this.setData({ history })
    wx.setStorageSync('searchHistory', history)
  },

  // 跳转到文章详情
  goToArticle(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({
      url: `/pages/article/index?id=${id}`
    })
  }
})