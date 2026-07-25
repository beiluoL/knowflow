Page({
  data: {
    messages: [],
    inputText: '',
    isTyping: false,
    scrollToView: '',
    quickQuestions: [
      'HashMap 扩容机制是什么？',
      'Python 装饰器怎么用？',
      'React Hooks 有哪些？',
      '如何学习数据结构？'
    ]
  },

  onLoad() {
    console.log('AI问答页面加载')
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 2 })
    }
  },

  // 输入框内容变化
  onInput(e) {
    this.setData({ inputText: e.detail.value })
  },

  // 快捷提问
  askQuick(e) {
    const { question } = e.currentTarget.dataset
    this.setData({ inputText: question })
    this.sendMessage()
  },

  // 发送消息
  sendMessage() {
    const { inputText, messages, isTyping } = this.data
    if (!inputText.trim() || isTyping) return

    // 添加用户消息
    const userMessage = {
      id: Date.now(),
      role: 'user',
      content: inputText.trim(),
      time: this.formatTime(new Date())
    }
    
    this.setData({ 
      messages: [...messages, userMessage],
      inputText: '',
      isTyping: true
    })

    // 滚动到底部
    this.scrollToBottom()

    // 模拟AI回复
    setTimeout(() => {
      this.replyToUser(inputText.trim())
    }, 1000 + Math.random() * 1000)
  },

  // AI回复
  replyToUser(question) {
    // 模拟AI回复内容
    const responses = {
      'HashMap': 'HashMap 是 Java 中常用的数据结构，它基于哈希表实现。扩容机制：当元素数量达到阈值（容量 × 加载因子）时，HashMap 会创建一个容量翻倍的新数组，并将所有元素重新哈希分配。默认加载因子是 0.75，这是时间和空间的平衡点。',
      'Python': 'Python 装饰器是一种设计模式，可以在不修改函数代码的情况下增强函数功能。基本语法：@decorator_name 定义在函数上方。常见用途：日志记录、性能测试、权限校验等。',
      'React': 'React Hooks 是 React 16.8 引入的新特性，允许在函数组件中使用状态和其他 React 特性。常用 Hooks：useState（状态管理）、useEffect（副作用处理）、useContext（上下文消费）、useReducer（复杂状态逻辑）等。',
      '数据结构': '学习数据结构建议：1. 从基础开始，掌握数组、链表、栈、队列；2. 理解树和图的概念；3. 学习常用算法（排序、查找）；4. 刷题实践（LeetCode）；5. 分析时间复杂度和空间复杂度。'
    }

    let response = '这是一个很好的问题！让我来详细解答：\n\n'
    
    for (const key in responses) {
      if (question.includes(key)) {
        response = responses[key]
        break
      }
    }
    
    if (response === '这是一个很好的问题！让我来详细解答：\n\n') {
      response += '您提到的技术点很有深度。建议从以下几个方面深入理解：\n1. 基础概念和原理\n2. 实际应用场景\n3. 最佳实践和常见问题\n4. 性能优化技巧\n\n有具体想深入了解的部分吗？'
    }

    const aiMessage = {
      id: Date.now(),
      role: 'assistant',
      content: response,
      time: this.formatTime(new Date())
    }

    this.setData({
      messages: [...this.data.messages, aiMessage],
      isTyping: false
    })

    this.scrollToBottom()
  },

  // 滚动到底部
  scrollToBottom() {
    const messages = this.data.messages
    if (messages.length > 0) {
      this.setData({
        scrollToView: `msg-${messages[messages.length - 1].id}`
      })
    }
  },

  // 格式化时间
  formatTime(date) {
    const hours = date.getHours().toString().padStart(2, '0')
    const minutes = date.getMinutes().toString().padStart(2, '0')
    return `${hours}:${minutes}`
  }
})