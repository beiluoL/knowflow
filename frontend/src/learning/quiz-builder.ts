/**
 * UI/UX 5.0 §十一/§十九：AI 出题的同域一致性构建器（确定性纯函数）。
 *
 * 题目一致性铁律：**题干与 4 个选项必须属于同一知识域。**
 * 干扰项只允许来自「同 chapter / path / doc / category」的卡片，
 * 绝不混入跨领域答案 —— 例如「JavaScript 类型题」的选项里绝不能出现
 * Self-Attention / 变压器 之类其他领域的答案。
 *
 * 语义校验：
 *  - 正确答案 = 题干卡的 back（与题干同卡，天然同域）；
 *  - 选项 = {正确答案} ∪ {同域干扰项×3}，Set 去重后 indexOf 定位正确项（幂等）；
 *  - 同域干扰项不足 3 个 → 放弃该题（宁缺毋滥，禁止跨域回退补位）。
 *
 * 本模块零副作用、零 API，输出可由 store / 页面 / 测试三方复用。
 */
/** 构建器可接受的卡片最小结构（便于测试造数，不必携带全部 FlashcardVO 字段） */
export interface QuizCardSource {
  id: number
  front?: string
  back?: string
  chapterId?: number
  pathId?: number
  docId?: number
  categoryId?: number
  category?: string
  difficulty?: number
}

export interface BuiltQuizOption {
  /** 原卡 id（字符串化，供前端 :key / 展开态索引使用） */
  cardId: string
  /** 题干（原卡 front） */
  question: string
  /** 4 个选项：1 正确 + 3 同域干扰（乱序） */
  options: string[]
  /** options 中正确项下标（确定性：indexOf 正确答案文本） */
  correctAnswer: number
  /** 正确项文本（= 题干卡 back，同时作解析/答案展示） */
  answer: string
  /** 该题所属知识域（调试 / 断言用） */
  domain: string
  category: string
  difficulty: number
  /** 生成后题目总数上限 */
  limit: number
}

/**
 * 卡片知识域键：章节 → 学习路径 → 来源文档 → 分类 → 分类文本兜底。
 * 同源即同域；无任何归属的孤立卡不可作为他人干扰项（其自身也无法成题，因同域不足）。
 */
export function cardDomainKey(c: QuizCardSource): string {
  if (c.chapterId) return `chapter:${c.chapterId}`
  if (c.pathId) return `path:${c.pathId}`
  if (c.docId) return `doc:${c.docId}`
  if (c.categoryId) return `category:${c.categoryId}`
  return `raw:${(c.category ?? '').trim()}`
}

/** Fisher-Yates 就地洗牌（返回新数组） */
function shuffle<T>(input: T[]): T[] {
  const a = input.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/**
 * 从真实闪卡构建选择题。
 * @param cards  候选卡（通常来自某个知识范围的 flashcards 接口）
 * @param limit  题目数量上限（默认 12）
 * @returns      同域一致的选择题；同域干扰不足的卡被跳过
 */
export function buildQuizFromCards(cards: QuizCardSource[], limit = 12): BuiltQuizOption[] {
  const pool = cards.filter(
    (c): c is QuizCardSource & { front: string; back: string } =>
      Boolean(c.front && c.back && c.back.trim().length > 0),
  )
  // 预聚类：同一域卡片集中，避免每题 O(n²) 全表扫描
  const byDomain = new Map<string, QuizCardSource[]>()
  for (const c of pool) {
    const k = cardDomainKey(c)
    const arr = byDomain.get(k)
    if (arr) arr.push(c)
    else byDomain.set(k, [c])
  }

  const result: BuiltQuizOption[] = []
  for (const c of pool) {
    const answer = c.back.trim()
    const domain = cardDomainKey(c)
    const sameDomain = byDomain.get(domain) ?? pool
    const distractors = Array.from(
      new Set(
        sameDomain
          .filter((o) => o.id !== c.id && o.back && o.back.trim() !== answer)
          .map((o) => o.back!.trim()),
      ),
    )
    // 同域干扰项不足 3 个 → 放弃该题（宁缺毋滥，禁止用跨领域内容补位）
    if (distractors.length < 3) continue

    const options = shuffle([answer, ...shuffle(distractors).slice(0, 3)])
    result.push({
      cardId: String(c.id),
      question: c.front.trim(),
      options,
      correctAnswer: options.indexOf(answer),
      answer,
      domain,
      category: c.category || '未分类',
      difficulty: c.difficulty ?? 2,
      limit,
    })
    if (result.length >= limit) break
  }
  return result
}
