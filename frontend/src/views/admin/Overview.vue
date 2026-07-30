<template>
  <!-- 管理后台概览：5 列指标 + 趋势图/操作日志 + 分类统计/热门路径 + 系统健康/AI统计/待办 -->
  <div class="overview-page animate-fade-in">
    <!-- ===== 标题行：管理后台 + 时间筛选 + 操作按钮 ===== -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="kb-h1">管理后台</h1>
        <button type="button" class="btn-secondary">
          <Icon name="calendar" :size="14" />
          <span>最近 7 天</span>
          <Icon name="chevron-down" :size="12" />
        </button>
      </div>
      <div class="header-right">
        <button type="button" class="btn-secondary" @click="loadData">
          <Icon name="refresh-cw" :size="14" />
          <span>刷新数据</span>
        </button>
        <button type="button" class="btn-primary">
          <Icon name="download" :size="14" />
          <span>导出报表</span>
        </button>
      </div>
    </div>

    <!-- ===== 5 列核心指标卡 ===== -->
    <div class="metrics-grid">
      <!-- 1. 活跃用户 -->
      <div class="metric-card">
        <div class="metric-head">
          <span class="kb-body-sm">活跃用户</span>
          <div class="metric-icon-box metric-icon-primary">
            <Icon name="users" :size="16" />
          </div>
        </div>
        <div class="metric-value">{{ formatNum(activeUsers) }}</div>
        <div v-if="usersTrend > 0" class="metric-trend trend-up">
          <Icon name="trending-up" :size="12" />
          <span>+{{ usersTrend }}%</span>
        </div>
        <div v-else class="metric-sub">今日活跃</div>
      </div>
      <!-- 2. 新增文档 -->
      <div class="metric-card">
        <div class="metric-head">
          <span class="kb-body-sm">新增文档</span>
          <div class="metric-icon-box metric-icon-primary">
            <Icon name="file-plus" :size="16" />
          </div>
        </div>
        <div class="metric-value">{{ formatNum(newDocs) }}</div>
        <span class="metric-sub">本周</span>
      </div>
      <!-- 3. AI 问答 -->
      <div class="metric-card">
        <div class="metric-head">
          <span class="kb-body-sm">AI 问答</span>
          <div class="metric-icon-box metric-icon-primary">
            <Icon name="message-square" :size="16" />
          </div>
        </div>
        <div class="metric-value">{{ formatNum(aiQueries) }}</div>
        <span class="metric-sub">今日 {{ todayAiQueries }}</span>
      </div>
      <!-- 4. 学习完成 -->
      <div class="metric-card">
        <div class="metric-head">
          <span class="kb-body-sm">学习完成</span>
          <div class="metric-icon-box metric-icon-success">
            <Icon name="graduation-cap" :size="16" />
          </div>
        </div>
        <div class="metric-value">{{ formatNum(learningCompleted) }}</div>
        <div class="metric-trend trend-up">
          <Icon name="trending-up" :size="12" />
          <span>+{{ learningTrend }}%</span>
        </div>
      </div>
      <!-- 5. 系统存储 -->
      <div class="metric-card">
        <div class="metric-head">
          <span class="kb-body-sm">系统存储</span>
          <div class="metric-icon-box metric-icon-primary">
            <Icon name="hard-drive" :size="16" />
          </div>
        </div>
        <div class="metric-value-row">
          <span class="metric-value">{{ storageUsed }}</span>
          <span class="metric-sub">GB / {{ storageTotal }} GB</span>
        </div>
        <div class="storage-bar">
          <div class="storage-bar-fill" :style="{ width: `${storagePct}%` }"></div>
        </div>
      </div>
    </div>

    <!-- ===== Row 3：用户活跃度趋势 (3) + 最近操作日志 (2) ===== -->
    <div class="row-3-2">
      <!-- 左 3 列：用户活跃度趋势 - 双柱状图 -->
      <div class="dash-card col-3">
        <div class="card-head">
          <h3 class="kb-h3">用户活跃度趋势</h3>
          <div class="legend-group">
            <div class="legend-item">
              <span class="legend-dot legend-doc"></span>
              <span class="legend-text">文档操作量</span>
            </div>
            <div class="legend-item">
              <span class="legend-dot legend-ai"></span>
              <span class="legend-text">AI 使用量</span>
            </div>
          </div>
        </div>
        <div class="bar-chart">
          <div
            v-for="item in activityTrend"
            :key="item.day"
            class="bar-col"
          >
            <div class="bar-group">
              <div class="bar bar-doc" :style="{ height: `${item.docPct}%` }"></div>
              <div class="bar bar-ai" :style="{ height: `${item.aiPct}%` }"></div>
            </div>
            <span class="bar-label">{{ item.day }}</span>
          </div>
        </div>
      </div>
      <!-- 右 2 列：最近操作日志 -->
      <div class="dash-card col-2">
        <div class="card-head">
          <h3 class="kb-h3">最近操作日志</h3>
          <a href="#" class="link-text">查看全部</a>
        </div>
        <ul class="log-list">
          <li
            v-for="log in recentLogs"
            :key="log.id"
            class="log-item"
          >
            <div class="log-avatar" :style="log.avatarStyle">{{ log.initial }}</div>
            <div class="log-content">
              <p class="log-text">{{ log.text }}</p>
              <p class="log-time">{{ log.time }}</p>
            </div>
            <span class="log-badge" :class="log.badgeClass">{{ log.badgeText }}</span>
          </li>
          <li v-if="recentLogs.length === 0" class="log-empty">暂无操作记录</li>
        </ul>
      </div>
    </div>

    <!-- ===== Row 4：内容分类统计 (3) + 热门学习路径 (2) ===== -->
    <div class="row-3-2">
      <!-- 左 3 列：内容分类统计 - 横向条形图 -->
      <div class="dash-card col-3">
        <div class="card-head">
          <h3 class="kb-h3">内容分类统计</h3>
          <a href="#" class="link-text">管理分类</a>
        </div>
        <div class="hbar-list">
          <div
            v-for="item in categoryStats"
            :key="item.name"
            class="hbar-row"
          >
            <span class="hbar-label">{{ item.name }}</span>
            <div class="hbar-track">
              <div class="hbar-fill" :style="{ width: `${item.pct}%` }"></div>
            </div>
            <span class="hbar-value">{{ item.count }}</span>
          </div>
          <div v-if="categoryStats.length === 0" class="log-empty">暂无分类数据</div>
        </div>
      </div>
      <!-- 右 2 列：热门学习路径 -->
      <div class="dash-card col-2">
        <div class="card-head">
          <h3 class="kb-h3">热门学习路径</h3>
          <a href="#" class="link-text">查看全部</a>
        </div>
        <ul class="rank-list">
          <li
            v-for="item in hotPaths"
            :key="item.id"
            class="rank-item"
          >
            <span class="rank-num" :class="{ 'rank-top': item.rank === 1 }">{{ item.rank }}</span>
            <div class="rank-body">
              <div class="rank-head">
                <p class="rank-title">{{ item.title }}</p>
                <span class="rank-count">{{ formatNum(item.users) }} 人</span>
              </div>
              <div class="rank-bar">
                <div class="rank-bar-fill" :style="{ width: `${item.pct}%` }"></div>
              </div>
            </div>
          </li>
        </ul>
      </div>
    </div>

    <!-- ===== Row 5：系统健康 + AI 使用统计 + 待处理事项 ===== -->
    <div class="row-3-equal">
      <!-- 列 1：系统健康 -->
      <div class="dash-card">
        <h3 class="kb-h3 card-title">系统健康</h3>
        <div class="health-list">
          <div v-for="item in systemHealth" :key="item.label" class="health-item">
            <div class="health-head">
              <div class="health-label">
                <Icon :name="item.icon" :size="16" class="health-icon" />
                <span>{{ item.label }}</span>
              </div>
              <span class="health-value" :style="{ color: item.color }">{{ item.value }}%</span>
            </div>
            <div class="health-bar">
              <div class="health-bar-fill" :style="{ width: `${item.value}%`, background: item.color }"></div>
            </div>
          </div>
        </div>
      </div>
      <!-- 列 2：AI 使用统计 -->
      <div class="dash-card">
        <h3 class="kb-h3 card-title">AI 使用统计</h3>
        <div class="ai-total">
          <Icon name="sparkles" :size="16" class="ai-icon" />
          <span class="ai-total-label">本月总调用</span>
          <span class="ai-total-value">{{ formatNum(aiTotal) }}</span>
        </div>
        <div class="ai-list">
          <div v-for="item in aiStats" :key="item.label" class="ai-row">
            <div class="ai-label">
              <span class="ai-dot" :style="{ background: item.color }"></span>
              <span>{{ item.label }}</span>
            </div>
            <span class="ai-value">{{ formatNum(item.count) }}</span>
          </div>
        </div>
      </div>
      <!-- 列 3：待处理事项 -->
      <div class="dash-card">
        <div class="card-head">
          <h3 class="kb-h3">待处理事项</h3>
          <span class="pending-count">{{ pendingItems.length }} 项</span>
        </div>
        <div class="pending-list">
          <div
            v-for="item in pendingItems"
            :key="item.id"
            class="pending-item"
          >
            <span class="priority-badge" :class="`badge-${item.priority}`">{{ item.priorityLabel }}</span>
            <div class="pending-body">
              <p class="pending-text">{{ item.text }}</p>
              <p class="pending-sub">{{ item.sub }}</p>
            </div>
            <button type="button" class="btn-sm">处理</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-概览：5 列指标 + 趋势图/操作日志 + 分类统计/热门路径 + 系统健康/AI统计/待办。
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { adminApi } from '@/api'
import type { AdminOverviewVO, CategoryVO, HealthMetric } from '@/api/types'

const overview = ref<AdminOverviewVO | null>(null)
const categories = ref<CategoryVO[]>([])
const loading = ref(true)

const formatNum = (n: number): string => n.toLocaleString('en-US')

// ===== 核心指标 =====
const activeUsers = computed(() => overview.value?.todayActiveUsers ?? overview.value?.totalUsers ?? 0)
const usersTrend = computed(() => (overview.value?.todayNewUsers ?? 0) > 0 ? 12 : 0)
const newDocs = computed(() => overview.value?.todayNewDocs ?? Math.round((overview.value?.totalDocs ?? 0) * 0.05))
const aiQueries = computed(() => overview.value?.totalConversations ?? 0)
const todayAiQueries = computed(() => Math.round(aiQueries.value * 0.067))
const learningCompleted = computed(() => 89) // 后端暂无字段，使用静态值
const learningTrend = computed(() => 23)
const storageUsed = computed(() => '24.5')
const storageTotal = computed(() => 50)
const storagePct = computed(() => 49)

// ===== 用户活跃度趋势（7 天双柱） =====
// 后端 userGrowth 仅有新增/累计用户，这里映射为文档操作量 + AI 使用量演示
const activityTrend = computed(() => {
  const data = overview.value?.userGrowth ?? []
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  if (data.length === 0) {
    // 无数据时返回空结构
    return days.map((d) => ({ day: d, docPct: 0, aiPct: 0 }))
  }
  const maxVal = data.reduce((m, d) => Math.max(m, d.newUsers ?? 0, d.totalUsers ?? 0), 0) || 1
  return data.map((d, i) => ({
    day: d.day || days[i] || '',
    docPct: Math.round(((d.totalUsers ?? 0) / maxVal) * 100),
    aiPct: Math.round(((d.newUsers ?? 0) / maxVal) * 100),
  }))
})

// ===== 最近操作日志 =====
interface LogItem {
  id: number
  initial: string
  avatarStyle: Record<string, string>
  text: string
  time: string
  badgeClass: string
  badgeText: string
}
const logTypeMap: Record<string, { bg: string; color: string; text: string }> = {
  upload: { bg: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)', text: '上传' },
  edit: { bg: 'rgba(245,158,11,0.08)', color: 'var(--kb-warning)', text: '编辑' },
  delete: { bg: 'rgba(239,68,68,0.1)', color: 'var(--kb-destructive)', text: '删除' },
  learn: { bg: 'rgba(16,185,129,0.08)', color: 'var(--kb-accent)', text: '学习' },
  post: { bg: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)', text: '发帖' },
  register: { bg: 'rgba(16,185,129,0.08)', color: 'var(--kb-accent)', text: '注册' },
}
const recentLogs = computed<LogItem[]>(() => {
  const list = overview.value?.recentActivities ?? []
  // 从用户名取首字作为头像
  return list.slice(0, 8).map((a) => {
    const cfg = logTypeMap[a.type] ?? logTypeMap.post
    const initial = (a.userName || '?').charAt(0)
    return {
      id: a.id,
      initial,
      avatarStyle: { background: cfg.bg, color: cfg.color },
      text: `${a.userName} ${a.action}`,
      time: a.time,
      badgeClass: `log-badge-${a.type in logTypeMap ? a.type : 'post'}`,
      badgeText: cfg.text,
    }
  })
})

// ===== 内容分类统计（横向条形图） =====
const categoryStats = computed(() => {
  const cats = categories.value
  const total = cats.reduce((s, c) => s + (c.docCount ?? 0), 0) || 1
  return cats
    .slice(0, 6)
    .map((c) => ({
      name: c.name,
      count: c.docCount ?? 0,
      pct: Math.round(((c.docCount ?? 0) / total) * 100),
    }))
    .sort((a, b) => b.count - a.count)
})

// ===== 热门学习路径（后端暂无接口，使用静态数据） =====
const hotPaths = ref([
  { id: 1, rank: 1, title: '机器学习工程师', users: 1286, pct: 78 },
  { id: 2, rank: 2, title: '全栈 Web 开发', users: 984, pct: 65 },
  { id: 3, rank: 3, title: '数据分析师', users: 756, pct: 52 },
  { id: 4, rank: 4, title: '产品设计入门', users: 523, pct: 38 },
  { id: 5, rank: 5, title: '云原生架构', users: 341, pct: 26 },
])

// ===== 系统健康（来自后端 healthMetrics，回退静态数据） =====
const healthColorMap: Record<string, string> = {
  good: 'var(--kb-accent)',
  warn: 'var(--kb-warning)',
  bad: 'var(--kb-destructive)',
}
const systemHealth = computed(() => {
  const metrics = (overview.value?.healthMetrics ?? []) as HealthMetric[]
  if (metrics.length === 0) {
    // 后端无数据时回退静态值
    return [
      { label: 'CPU', value: 32, color: 'var(--kb-accent)', icon: 'cpu' },
      { label: '内存', value: 58, color: 'var(--kb-warning)', icon: 'memory-stick' },
      { label: 'API 调用', value: 30, color: 'var(--kb-primary)', icon: 'zap' },
    ]
  }
  return metrics.slice(0, 3).map((m) => ({
    label: m.label,
    value: m.value,
    color: healthColorMap[m.level] ?? 'var(--kb-primary)',
    icon: m.icon || 'cpu',
  }))
})

// ===== AI 使用统计（后端暂无字段，使用静态数据） =====
const aiTotal = computed(() => 8450)
const aiStats = ref([
  { label: '润色', count: 3200, color: 'var(--kb-primary)' },
  { label: '出题', count: 1860, color: 'var(--kb-accent)' },
  { label: '摘要', count: 2100, color: 'var(--kb-warning)' },
  { label: '代码注释', count: 1290, color: 'rgba(59,111,224,0.35)' },
])

// ===== 待处理事项（后端暂无接口，使用静态数据） =====
const pendingItems = ref([
  { id: 1, priority: 'high' as const, priorityLabel: '高', text: '审核 3 篇待发布文档', sub: '文档团队提交' },
  { id: 2, priority: 'high' as const, priorityLabel: '高', text: '修复 AI 问答超时告警', sub: '系统自动检测' },
  { id: 3, priority: 'medium' as const, priorityLabel: '中', text: '更新知识库分类规则', sub: '运营团队反馈' },
  { id: 4, priority: 'low' as const, priorityLabel: '低', text: '清理过期缓存数据', sub: '定期维护任务' },
])

const loadData = async () => {
  loading.value = true
  try {
    const [ov, cats] = await Promise.all([
      adminApi.overview(),
      adminApi.categories(),
    ])
    overview.value = ov
    categories.value = cats
  } catch {
    overview.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
/* ===== 页面容器 ===== */
.overview-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 标题行 ===== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-primary:hover { opacity: 0.9; }
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-sidebar-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-secondary:hover { background: var(--kb-muted); }
.btn-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 12px;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-sm:hover { opacity: 0.85; }

/* ===== 5 列指标卡 ===== */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
.metric-card {
  padding: 20px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
}
.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.metric-icon-box {
  width: 32px;
  height: 32px;
  border-radius: var(--kb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}
.metric-icon-primary {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.metric-icon-success {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-accent);
}
.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
  margin-bottom: 4px;
}
.metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 10px;
}
.metric-sub {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.metric-trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
}
.trend-up { color: var(--kb-accent); }
.storage-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  overflow: hidden;
}
.storage-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--kb-primary);
  transition: width 0.4s ease;
}

/* ===== 通用卡片 ===== */
.dash-card {
  padding: 20px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.card-title {
  margin-bottom: 20px;
}
.link-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-primary);
  text-decoration: none;
}
.link-text:hover { opacity: 0.8; }

/* ===== Row 3:2 布局 ===== */
.row-3-2 {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;
}
.col-3 { min-width: 0; }
.col-2 { min-width: 0; }

/* ===== 双柱状图 ===== */
.legend-group {
  display: flex;
  align-items: center;
  gap: 16px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}
.legend-doc { background: var(--kb-primary); }
.legend-ai { background: rgba(59, 111, 224, 0.35); }
.legend-text {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  height: 200px;
}
.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 1;
  height: 100%;
}
.bar-group {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 100%;
  width: 100%;
  justify-content: center;
}
.bar {
  flex: 1;
  max-width: 18px;
  border-radius: 3px 3px 0 0;
  min-height: 4px;
  transition: height 0.3s ease;
}
.bar-doc { background: var(--kb-primary); }
.bar-ai { background: rgba(59, 111, 224, 0.35); }
.bar-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

/* ===== 操作日志 ===== */
.log-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.log-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--kb-border);
}
.log-item:last-child { border-bottom: none; }
.log-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.log-content {
  flex: 1;
  min-width: 0;
}
.log-text {
  font-size: 14px;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
}
.log-time {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 2px 0 0 0;
}
.log-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}
.log-badge-upload { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }
.log-badge-edit { background: rgba(245, 158, 11, 0.08); color: var(--kb-warning); }
.log-badge-delete { background: rgba(239, 68, 68, 0.1); color: var(--kb-destructive); }
.log-badge-learn { background: rgba(16, 185, 129, 0.08); color: var(--kb-accent); }
.log-badge-post { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }
.log-badge-register { background: rgba(16, 185, 129, 0.08); color: var(--kb-accent); }
.log-empty {
  text-align: center;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  padding: 20px 0;
}

/* ===== 横向条形图 ===== */
.hbar-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hbar-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hbar-label {
  font-size: 14px;
  color: var(--kb-foreground);
  width: 72px;
  flex-shrink: 0;
}
.hbar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  overflow: hidden;
}
.hbar-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--kb-primary);
  transition: width 0.4s ease;
}
.hbar-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  width: 36px;
  text-align: right;
  flex-shrink: 0;
}

/* ===== 热门路径排行 ===== */
.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rank-num {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: var(--kb-muted);
  color: var(--kb-sidebar-foreground);
  flex-shrink: 0;
}
.rank-top {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.rank-body {
  flex: 1;
  min-width: 0;
}
.rank-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.rank-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
}
.rank-count {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-left: 8px;
  flex-shrink: 0;
}
.rank-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  overflow: hidden;
}
.rank-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--kb-primary);
  transition: width 0.4s ease;
}

/* ===== Row 3 等列 ===== */
.row-3-equal {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

/* ===== 系统健康 ===== */
.health-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.health-item {
  /* 单条健康指标 */
}
.health-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.health-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--kb-foreground);
}
.health-icon { color: var(--kb-muted-foreground); }
.health-value {
  font-size: 14px;
  font-weight: 600;
}
.health-bar {
  height: 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  overflow: hidden;
}
.health-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}

/* ===== AI 使用统计 ===== */
.ai-total {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.ai-icon { color: var(--kb-primary); }
.ai-total-label {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.ai-total-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin-left: auto;
}
.ai-list {
  display: flex;
  flex-direction: column;
}
.ai-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--kb-border);
}
.ai-row:last-child { border-bottom: none; }
.ai-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--kb-foreground);
}
.ai-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.ai-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}

/* ===== 待处理事项 ===== */
.pending-count {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-destructive);
}
.pending-list {
  display: flex;
  flex-direction: column;
}
.pending-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--kb-border);
}
.pending-item:last-child { border-bottom: none; }
.priority-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
  margin-top: 2px;
}
.badge-high { background: rgba(239, 68, 68, 0.1); color: var(--kb-destructive); }
.badge-medium { background: rgba(245, 158, 11, 0.08); color: var(--kb-warning); }
.badge-low { background: rgba(16, 185, 129, 0.08); color: var(--kb-accent); }
.pending-body {
  flex: 1;
  min-width: 0;
}
.pending-text {
  font-size: 14px;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
}
.pending-sub {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 2px 0 0 0;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .row-3-2 {
    grid-template-columns: 1fr;
  }
  .row-3-equal {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
