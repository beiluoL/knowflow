// 本地学习会话工具：番茄钟专注记录与学习伙伴（宠物）状态持久化。
// 仅使用浏览器 localStorage，不依赖任何后端接口。
// 用于在「番茄钟」页面累计专注时长，并在「学习中心」渲染真实的学习热力图。

export interface PomodoroSession {
  date: string // YYYY-MM-DD
  minutes: number
}

export interface LearningPetState {
  name: string
  level: number
  mood: string
  energy: number
  exp: number
  maxExp: number
  avatar: string
}

const SESSIONS_KEY = 'knowflow:pomodoro:sessions'
const PET_KEY = 'knowflow:pet'

export function dateStr(d: Date): string {
  const y = d.getFullYear()
  const m = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function loadSessions(): PomodoroSession[] {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (s): s is PomodoroSession =>
        typeof s?.date === 'string' && typeof s?.minutes === 'number',
    )
  } catch {
    return []
  }
}

export function addSession(date: string, minutes: number): void {
  const sessions = loadSessions()
  sessions.push({ date, minutes })
  localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessions))
}

export function todayMinutes(sessions: PomodoroSession[], today: string = dateStr(new Date())): number {
  return sessions.filter((s) => s.date === today).reduce((sum, s) => sum + s.minutes, 0)
}

export function todayPomodoros(sessions: PomodoroSession[], today: string = dateStr(new Date())): number {
  return sessions.filter((s) => s.date === today).length
}

// 计算连续学习天数（截至今天，若今天无记录则从昨天往前算，至少 0）
export function streakDays(sessions: PomodoroSession[]): number {
  if (sessions.length === 0) return 0
  const days = new Set(sessions.map((s) => s.date))
  let streak = 0
  const cursor = new Date()
  // 若今天还没记录，从昨天开始计，避免“当天未学就断签”的严苛判定
  if (!days.has(dateStr(cursor))) cursor.setDate(cursor.getDate() - 1)
  while (days.has(dateStr(cursor))) {
    streak++
    cursor.setDate(cursor.getDate() - 1)
  }
  return streak
}

// 生成最近 weeks 周（每周 7 天）的学习强度热力图，level 0~3
export function heatmap(sessions: PomodoroSession[], weeks = 5): number[] {
  const byDay = new Map<string, number>()
  sessions.forEach((s) => {
    byDay.set(s.date, (byDay.get(s.date) ?? 0) + s.minutes)
  })
  const levels: number[] = []
  const cursor = new Date()
  // 对齐到本周一，便于从 5 周前开始铺满 35 格
  const dayOfWeek = (cursor.getDay() + 6) % 7 // 周一=0
  cursor.setDate(cursor.getDate() - dayOfWeek - (weeks - 1) * 7)
  for (let i = 0; i < weeks * 7; i++) {
    const minutes = byDay.get(dateStr(cursor)) ?? 0
    let level = 0
    if (minutes >= 120) level = 3
    else if (minutes >= 60) level = 2
    else if (minutes > 0) level = 1
    levels.push(level)
    cursor.setDate(cursor.getDate() + 1)
  }
  return levels
}

export function loadPet(defaultPet: LearningPetState): LearningPetState {
  try {
    const raw = localStorage.getItem(PET_KEY)
    if (!raw) return { ...defaultPet }
    const parsed = JSON.parse(raw)
    return { ...defaultPet, ...parsed }
  } catch {
    return { ...defaultPet }
  }
}

export function savePet(pet: LearningPetState): void {
  localStorage.setItem(PET_KEY, JSON.stringify(pet))
}

// ===== 闪卡「本地已复习」记录（按日期隔离，用于复习计划页统计） =====
const REVIEWED_PREFIX = 'knowflow:reviewed:'

export function loadReviewedIds(date: string): Set<string> {
  try {
    const raw = localStorage.getItem(REVIEWED_PREFIX + date)
    if (!raw) return new Set()
    const arr = JSON.parse(raw)
    return new Set(Array.isArray(arr) ? (arr as string[]) : [])
  } catch {
    return new Set()
  }
}

export function markReviewed(date: string, id: string): void {
  const set = loadReviewedIds(date)
  set.add(id)
  localStorage.setItem(REVIEWED_PREFIX + date, JSON.stringify([...set]))
}
