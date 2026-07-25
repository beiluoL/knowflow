Page({
  data: {
    categories: [
      { id: 1, name: 'Java', icon: '☕', bgColor: 'rgba(59, 111, 224, 0.1)' },
      { id: 2, name: 'Python', icon: '🐍', bgColor: 'rgba(16, 185, 129, 0.1)' },
      { id: 3, name: '前端', icon: '🎨', bgColor: 'rgba(245, 158, 11, 0.1)' },
      { id: 4, name: 'AI', icon: '🧠', bgColor: 'rgba(139, 92, 246, 0.1)' },
      { id: 5, name: '算法', icon: '📐', bgColor: 'rgba(239, 68, 68, 0.1)' },
      { id: 6, name: '数据库', icon: '🗄️', bgColor: 'rgba(6, 182, 212, 0.1)' },
      { id: 7, name: '运维', icon: '⚙️', bgColor: 'rgba(100, 116, 139, 0.1)' },
      { id: 8, name: 'Go', icon: '🐹', bgColor: 'rgba(14, 165, 233, 0.1)' },
    ],
    interviewQuestions: [
      { id: 1, tag: 'Java', tagIcon: '☕', tagColor: '#3B6FE0', tagBg: 'rgba(59, 111, 224, 0.1)', title: 'HashMap 扩容机制详解', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '2.3k' },
      { id: 2, tag: 'Java', tagIcon: '☕', tagColor: '#3B6FE0', tagBg: 'rgba(59, 111, 224, 0.1)', title: 'Synchronized 锁升级原理', difficulty: '困难', diffBg: 'rgba(239, 68, 68, 0.1)', diffColor: '#EF4444', count: '1.8k' },
      { id: 3, tag: 'Java', tagIcon: '☕', tagColor: '#3B6FE0', tagBg: 'rgba(59, 111, 224, 0.1)', title: 'volatile 关键字原理', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '3.1k' },
      { id: 4, tag: 'Java', tagIcon: '☕', tagColor: '#3B6FE0', tagBg: 'rgba(59, 111, 224, 0.1)', title: 'JVM 垃圾回收算法对比', difficulty: '困难', diffBg: 'rgba(239, 68, 68, 0.1)', diffColor: '#EF4444', count: '1.5k' },
      { id: 5, tag: 'Java', tagIcon: '☕', tagColor: '#3B6FE0', tagBg: 'rgba(59, 111, 224, 0.1)', title: 'Spring IOC 容器原理', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '4.2k' },
      { id: 6, tag: 'Java', tagIcon: '☕', tagColor: '#3B6FE0', tagBg: 'rgba(59, 111, 224, 0.1)', title: '线程池核心参数解析', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '2.8k' },
      { id: 7, tag: 'Python', tagIcon: '🐍', tagColor: '#10B981', tagBg: 'rgba(16, 185, 129, 0.1)', title: 'Python GIL 锁机制', difficulty: '困难', diffBg: 'rgba(239, 68, 68, 0.1)', diffColor: '#EF4444', count: '1.8k' },
      { id: 8, tag: 'Python', tagIcon: '🐍', tagColor: '#10B981', tagBg: 'rgba(16, 185, 129, 0.1)', title: '装饰器与闭包原理', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '2.1k' },
      { id: 9, tag: 'Python', tagIcon: '🐍', tagColor: '#10B981', tagBg: 'rgba(16, 185, 129, 0.1)', title: '异步编程 asyncio', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '1.6k' },
      { id: 10, tag: '前端', tagIcon: '🎨', tagColor: '#F59E0B', tagBg: 'rgba(245, 158, 11, 0.1)', title: 'React Hooks 深度解析', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '3.1k' },
      { id: 11, tag: '前端', tagIcon: '🎨', tagColor: '#F59E0B', tagBg: 'rgba(245, 158, 11, 0.1)', title: 'Vue3 响应式原理', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '2.7k' },
      { id: 12, tag: '前端', tagIcon: '🎨', tagColor: '#F59E0B', tagBg: 'rgba(245, 158, 11, 0.1)', title: '浏览器事件循环机制', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '3.5k' },
      { id: 13, tag: '前端', tagIcon: '🎨', tagColor: '#F59E0B', tagBg: 'rgba(245, 158, 11, 0.1)', title: 'Webpack 构建优化', difficulty: '简单', diffBg: 'rgba(16, 185, 129, 0.1)', diffColor: '#10B981', count: '1.9k' },
      { id: 14, tag: '数据库', tagIcon: '🗄️', tagColor: '#06B6D4', tagBg: 'rgba(6, 182, 212, 0.1)', title: 'MySQL 索引优化策略', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '2.4k' },
      { id: 15, tag: '数据库', tagIcon: '🗄️', tagColor: '#06B6D4', tagBg: 'rgba(6, 182, 212, 0.1)', title: 'Redis 缓存穿透解决方案', difficulty: '中等', diffBg: '#E8ECF1', diffColor: '#F59E0B', count: '3.0k' },
    ],
    learningPaths: [
      { id: 1, title: 'Java 后端开发', icon: '☕', iconBg: 'rgba(59, 111, 224, 0.1)', chapters: 12, hours: 48, progress: 35, progressColor: '#3B6FE0' },
      { id: 2, title: 'Python 数据分析', icon: '🐍', iconBg: 'rgba(16, 185, 129, 0.1)', chapters: 8, hours: 32, progress: 0, progressColor: '#10B981' },
      { id: 3, title: '前端全栈进阶', icon: '🎨', iconBg: 'rgba(245, 158, 11, 0.1)', chapters: 15, hours: 60, progress: 12, progressColor: '#F59E0B' },
      { id: 4, title: '算法与数据结构', icon: '📐', iconBg: 'rgba(239, 68, 68, 0.1)', chapters: 10, hours: 40, progress: 0, progressColor: '#EF4444' },
      { id: 5, title: '云原生与容器化', icon: '⚙️', iconBg: 'rgba(100, 116, 139, 0.1)', chapters: 6, hours: 24, progress: 0, progressColor: '#64748B' },
    ]
  },

  onLoad() {
    console.log('首页加载')
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 })
    }
  },

  goToSearch() {
    wx.navigateTo({ url: '/pages/search/index' })
  },

  onCategoryTap(e) {
    const { id, name } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/knowledge/index?category=${id}&name=${name}` })
  },

  onInterviewTap(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/article/index?id=${id}` })
  },

  onPathTap(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/statistics/index?pathId=${id}` })
  },

  goToMoreInterview() {
    wx.switchTab({ url: '/pages/knowledge/index' })
  },

  goToLearningPaths() {
    wx.switchTab({ url: '/pages/knowledge/index' })
  },

  startChallenge() {
    wx.showToast({ title: '功能开发中', icon: 'none' })
  },

  onBannerTap() {
    wx.switchTab({ url: '/pages/knowledge/index' })
  }
})