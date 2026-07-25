Page({
  data: {
    decks: [
      { name: 'Java 核心', cards: [] },
      { name: 'Python', cards: [] },
      { name: '前端', cards: [] },
      { name: '算法', cards: [] }
    ],
    currentDeck: 0,
    currentIndex: 0,
    cards: [
      { question: 'HashMap 的扩容机制是什么？', answer: '当元素数量达到阈值（容量 × 加载因子0.75）时，HashMap 会创建一个容量翻倍的新数组，并将所有元素重新哈希分配到新数组中。' },
      { question: 'Java 中的 equals() 和 == 有什么区别？', answer: '== 比较的是对象引用（内存地址），equals() 比较的是对象内容。默认情况下 equals() 也是比较引用，但可以被重写来比较内容。' },
      { question: 'ArrayList 和 LinkedList 的区别？', answer: 'ArrayList 基于动态数组实现，查询快 O(1)，增删慢 O(n)；LinkedList 基于双向链表实现，增删快 O(1)，查询慢 O(n)。' },
      { question: 'Java 中的多态是什么？', answer: '多态是指同一个方法调用，由于对象不同可能会有不同的行为。实现条件：继承、重写、父类引用指向子类对象。' },
      { question: '接口和抽象类的区别？', answer: '接口只能定义抽象方法（Java8后可以有默认方法），抽象类可以有具体方法。一个类可以实现多个接口，但只能继承一个抽象类。' },
      { question: 'Synchronized 和 Lock 的区别？', answer: 'Synchronized 是 JVM 层面的关键字，自动释放锁；Lock 是 API 层面的接口，需要手动释放锁，但功能更强大（可中断、超时、公平锁）。' },
      { question: '线程池的核心参数有哪些？', answer: '核心线程数(corePoolSize)、最大线程数(maximumPoolSize)、存活时间(keepAliveTime)、任务队列(workQueue)、拒绝策略(handler)。' },
      { question: 'JVM 内存分为哪些区域？', answer: '堆（Heap）、虚拟机栈（VM Stack）、本地方法栈（Native Method Stack）、方法区（Method Area）、程序计数器（PC Register）。' },
      { question: '什么是双亲委派模型？', answer: '类加载器加载类时，先委托父类加载器加载，父类加载器无法加载时才自己加载。保证核心类库的安全。' },
      { question: 'volatile 关键字的作用？', answer: '保证变量的可见性（一个线程修改后其他线程立即可见）和禁止指令重排序，但不保证原子性。' },
      { question: '深拷贝和浅拷贝的区别？', answer: '浅拷贝只复制对象本身，不复制引用对象；深拷贝会递归复制对象及其引用的所有对象。' },
      { question: 'String、StringBuilder、StringBuffer 的区别？', answer: 'String 不可变；StringBuilder 可变但线程不安全，单线程性能最好；StringBuffer 可变且线程安全（加了 synchronized）。' },
    ],
    currentCard: { question: '', answer: '' },
    isFlipped: false,
    stats: { remembered: 0, forgotten: 0, remaining: 12 }
  },

  onLoad() {
    console.log('闪卡页面加载')
    this.initCurrentCard()
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 3 })
    }
  },

  initCurrentCard() {
    const { cards, currentIndex } = this.data
    if (cards.length > 0) {
      this.setData({ currentCard: cards[currentIndex], isFlipped: false })
    }
  },

  selectDeck(e) {
    const { index } = e.currentTarget.dataset
    this.setData({ currentDeck: index, currentIndex: 0, isFlipped: false })
    this.initCurrentCard()
  },

  flipCard() {
    this.setData({ isFlipped: !this.data.isFlipped })
  },

  handleAnswer(e) {
    const { type } = e.currentTarget.dataset
    const { currentIndex, cards, stats } = this.data
    const newStats = { ...stats }
    if (type === 'success') {
      newStats.remembered++
      wx.showToast({ title: '太棒了！', icon: 'success', duration: 1000 })
    } else {
      newStats.forgotten++
      wx.showToast({ title: '继续加油！', icon: 'none', duration: 1000 })
    }
    newStats.remaining--

    if (currentIndex < cards.length - 1) {
      this.setData({ currentIndex: currentIndex + 1, isFlipped: false, stats: newStats })
      this.initCurrentCard()
    } else {
      this.setData({ stats: newStats })
      wx.showModal({
        title: '学习完成',
        content: `已记住：${newStats.remembered}\n需复习：${newStats.forgotten}`,
        showCancel: false,
        confirmText: '再来一轮',
        success: () => { this.resetLearning() }
      })
    }
  },

  resetLearning() {
    this.setData({
      currentIndex: 0, isFlipped: false,
      stats: { remembered: 0, forgotten: 0, remaining: this.data.cards.length }
    })
    this.initCurrentCard()
  }
})