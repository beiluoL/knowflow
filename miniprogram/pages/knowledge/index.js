Page({
  data: {
    categories: [
      {
        id: 1, name: 'Java', icon: '☕', bgColor: 'rgba(59, 111, 224, 0.1)', count: 156, expanded: false,
        children: [
          { id: 11, name: 'Java 基础语法' }, { id: 12, name: '面向对象编程' }, { id: 13, name: 'Java 集合框架' },
          { id: 14, name: 'Java 并发编程' }, { id: 15, name: 'JVM 原理' }, { id: 16, name: 'Spring 框架' },
          { id: 17, name: 'Spring Boot' }, { id: 18, name: 'MyBatis' }, { id: 19, name: 'Maven/Gradle' },
        ]
      },
      {
        id: 2, name: 'Python', icon: '🐍', bgColor: 'rgba(16, 185, 129, 0.1)', count: 98, expanded: false,
        children: [
          { id: 21, name: 'Python 基础语法' }, { id: 22, name: '面向对象' }, { id: 23, name: '数据处理' },
          { id: 24, name: 'Web 开发' }, { id: 25, name: '爬虫技术' }, { id: 26, name: '数据分析' },
          { id: 27, name: '机器学习' }, { id: 28, name: '自动化运维' },
        ]
      },
      {
        id: 3, name: '前端', icon: '🎨', bgColor: 'rgba(245, 158, 11, 0.1)', count: 87, expanded: false,
        children: [
          { id: 31, name: 'HTML/CSS 基础' }, { id: 32, name: 'JavaScript 核心' }, { id: 33, name: 'TypeScript' },
          { id: 34, name: 'Vue.js' }, { id: 35, name: 'React' }, { id: 36, name: '小程序开发' },
          { id: 37, name: 'Node.js' }, { id: 38, name: '前端工程化' }, { id: 39, name: '性能优化' },
        ]
      },
      {
        id: 4, name: 'AI', icon: '🧠', bgColor: 'rgba(139, 92, 246, 0.1)', count: 45, expanded: false,
        children: [
          { id: 41, name: '机器学习基础' }, { id: 42, name: '深度学习' }, { id: 43, name: '自然语言处理' },
          { id: 44, name: '计算机视觉' }, { id: 45, name: '大模型应用' }, { id: 46, name: 'Prompt 工程' },
        ]
      },
      {
        id: 5, name: '算法', icon: '📐', bgColor: 'rgba(239, 68, 68, 0.1)', count: 72, expanded: false,
        children: [
          { id: 51, name: '数组与链表' }, { id: 52, name: '栈与队列' }, { id: 53, name: '树与图' },
          { id: 54, name: '排序算法' }, { id: 55, name: '动态规划' }, { id: 56, name: '贪心算法' },
          { id: 57, name: '回溯算法' }, { id: 58, name: '并查集' },
        ]
      },
      {
        id: 6, name: '数据库', icon: '🗄️', bgColor: 'rgba(6, 182, 212, 0.1)', count: 64, expanded: false,
        children: [
          { id: 61, name: 'MySQL 基础' }, { id: 62, name: 'SQL 优化' }, { id: 63, name: 'Redis 缓存' },
          { id: 64, name: 'MongoDB' }, { id: 65, name: '分库分表' }, { id: 66, name: '事务与锁' },
        ]
      },
    ],
    recentReadings: [
      { id: 1, icon: '📖', title: 'HashMap 扩容机制详解', category: 'Java', time: '2小时前', progress: 85 },
      { id: 2, icon: '📖', title: 'Python 装饰器从入门到精通', category: 'Python', time: '昨天', progress: 45 },
      { id: 3, icon: '📖', title: 'React Hooks 最佳实践', category: '前端', time: '3天前', progress: 20 },
      { id: 4, icon: '📖', title: 'MySQL 索引优化实战', category: '数据库', time: '4天前', progress: 60 },
      { id: 5, icon: '📖', title: 'Spring Boot 自动装配原理', category: 'Java', time: '5天前', progress: 30 },
      { id: 6, icon: '📖', title: 'Redis 持久化机制 RDB vs AOF', category: '数据库', time: '1周前', progress: 75 },
    ],
    recommendations: [
      { id: 1, tag: 'Java', tagBg: 'rgba(59, 111, 224, 0.1)', tagColor: '#3B6FE0', title: 'JVM 内存模型深度解析', desc: '深入理解 JVM 内存结构，掌握垃圾回收机制，面试高频考点', author: '技术小王', views: '2.1k' },
      { id: 2, tag: 'Python', tagBg: 'rgba(16, 185, 129, 0.1)', tagColor: '#10B981', title: 'Python 数据分析实战指南', desc: '从零开始学习 Pandas、NumPy 和 Matplotlib，掌握数据可视化', author: '数据达人', views: '1.8k' },
      { id: 3, tag: '前端', tagBg: 'rgba(245, 158, 11, 0.1)', tagColor: '#F59E0B', title: 'Vue3 组合式 API 详解', desc: '掌握 Composition API、响应式原理和性能优化技巧', author: '前端小白', views: '3.5k' },
      { id: 4, tag: '算法', tagBg: 'rgba(239, 68, 68, 0.1)', tagColor: '#EF4444', title: 'LeetCode 高频 200 题精讲', desc: '系统梳理面试高频算法题，配套图解和代码实现', author: '算法大牛', views: '5.2k' },
      { id: 5, tag: '数据库', tagBg: 'rgba(6, 182, 212, 0.1)', tagColor: '#06B6D4', title: 'Redis 设计与实现', desc: '从源码角度理解 Redis 数据结构、持久化和集群架构', author: 'DBA老张', views: '2.8k' },
      { id: 6, tag: 'AI', tagBg: 'rgba(139, 92, 246, 0.1)', tagColor: '#8B5CF6', title: '大模型 Prompt 工程实战', desc: '掌握提示词设计技巧，提升 AI 应用效果和效率', author: 'AI研究员', views: '4.1k' },
    ]
  },

  onLoad() {
    console.log('知识库页面加载')
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 })
    }
  },

  goToSearch() {
    wx.navigateTo({ url: '/pages/search/index' })
  },

  toggleCategory(e) {
    const { id } = e.currentTarget.dataset
    const categories = this.data.categories.map(item => {
      if (item.id === id) return { ...item, expanded: !item.expanded }
      return item
    })
    this.setData({ categories })
  },

  goToArticle(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/article/index?id=${id}` })
  },

  goToArticleDetail(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/article/index?id=${id}` })
  },

  goToAllArticles() {
    wx.showToast({ title: '功能开发中', icon: 'none' })
  }
})