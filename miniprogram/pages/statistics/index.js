Page({
  data: {
    stats: [
      { icon: '⏱', label: '学习时长', value: '128.5', unit: '小时', color: '#3B6FE0' },
      { icon: '📖', label: '完成课程', value: '36', unit: '个', color: '#3B6FE0' },
      { icon: '📚', label: '闪卡复习', value: '248', unit: '张', color: '#3B6FE0' },
      { icon: '🎯', label: '正确率', value: '82', unit: '%', color: '#3B6FE0' },
    ],
    heatmapData: [
      0, 1, 2, 1, 3, 2, 1,
      1, 2, 3, 2, 1, 3, 2,
      2, 2, 1, 3, 2, 3, 1,
      1, 3, 2, 3, 1, 2, 3,
      2, 1, 3, 2, 1, 0, 2,
    ],
    subjects: [
      { name: 'Java', time: '42.5h', percent: 100 },
      { name: 'Python', time: '31.2h', percent: 73 },
      { name: '前端', time: '28.6h', percent: 67 },
      { name: '算法', time: '15.8h', percent: 37 },
      { name: '数据库', time: '10.4h', percent: 24 },
    ],
    recentLogs: [
      { id: 1, icon: '✅', content: '完成了 Java HashMap 扩容机制章节', time: '2小时前' },
      { id: 2, icon: '🔄', content: '复习了 Python 基础语法闪卡 12 张', time: '5小时前' },
      { id: 3, icon: '💻', content: '学习了 CSS Flexbox 布局详解', time: '昨天' },
      { id: 4, icon: '🧠', content: '完成了排序算法练习 15 题', time: '昨天' },
      { id: 5, icon: '📚', content: '复习了 Java 集合框架闪卡 8 张', time: '2天前' },
      { id: 6, icon: '✅', content: '完成了 Redis 持久化机制章节', time: '2天前' },
      { id: 7, icon: '🎯', content: 'AI 问答练习：回答了 6 道技术问题', time: '3天前' },
      { id: 8, icon: '📖', content: '阅读了 Vue3 响应式原理文章', time: '3天前' },
    ]
  },

  onLoad() {
    console.log('学习统计页面加载')
  }
})