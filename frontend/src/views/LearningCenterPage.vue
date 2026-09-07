<template>
  <div class="learning-center animate-fade-in">
    <!-- ========== Phase UI-4: 今日行动（学习首页第一屏，回答「我现在该做什么」） ========== -->
    <section class="today-action">
      <div class="today-head">
        <div>
          <!-- 3.2 §28：与 Sidebar 固定入口同名，避免「菜单说学习首页、页面说学习中心」 -->
          <h1 class="ta-title">学习首页</h1>
          <p class="ta-subtitle">今天继续推进你的学习</p>
        </div>
        <Button :icon-name="mainCta.icon" @click="goTo(mainCta.path)">{{ mainCta.text }}</Button>
      </div>

      <!-- 今日完成（真实任务数据；系统无每日目标设置，因此不展示伪造百分比目标 §29） -->
      <div v-if="tasks.length > 0" class="ta-progress">
        <div class="ta-progress-head">
          <span class="ta-label">今日完成</span>
          <span class="ta-count tabular-nums">{{ doneCount }} / {{ tasks.length }}</span>
        </div>
        <div
          class="ta-progress-bar"
          role="progressbar"
          :aria-valuenow="donePercent"
          aria-valuemin="0"
          aria-valuemax="100"
          aria-label="今日任务完成进度"
        >
          <div class="ta-progress-fill" :style="{ width: donePercent + '%' }"></div>
        </div>
      </div>

      <!-- 待复习 + 待巩固：复用 Home 的展示卡片（§48/§49 复用优先），数据真实、错误隔离 -->
      <div class="ta-buckets">
        <ReviewDueCard
          :items="reviewRequired"
          :loading="reviewLoading"
          :error="reviewError"
          @retry="loadReviewRequired"
          @start="goTo('/learning/review')"
          @continue="goTo('/learning/paths')"
        />
        <WeakPointCard
          :items="weakPoints"
          :loading="weakLoading"
          :error="weakError"
          @retry="loadWeakPoints"
          @view-all="goTo('/learning/mastery')"
        />
      </div>

      <!-- 当前学习路径（真实 paths[0]，只读进度不 N+1） -->
      <div v-if="currentPath" class="ta-path">
        <div class="min-w-0">
          <span class="ta-label">当前学习路径</span>
          <h3 class="ta-path-title">{{ currentPath.title }}</h3>
        </div>
        <Button variant="secondary" size="sm" icon-name="play" @click="goTo(`/learning/path/${currentPath.id}`)">继续</Button>
      </div>
      <div v-else class="ta-path ta-path-empty">
        <p>还没有进行中的学习路径</p>
        <Button variant="secondary" size="sm" icon-name="route" @click="goTo('/learning/paths')">浏览学习路径</Button>
      </div>

      <!-- Architecture-5 §23/§225：学习中心展示「具体」待复习知识点（Home 只显示数量） -->
      <div v-if="reviewRequired.length > 0" class="ta-review-chips">
        <span class="ta-chips-label">待复习：</span>
        <button
          v-for="k in reviewRequired.slice(0, 5)"
          :key="k.knowledgeId"
          type="button"
          class="ta-chip"
          :title="`查看「${k.name || '未命名知识点'}」掌握度`"
          @click="goTo(`/learning/knowledge-mastery/${k.knowledgeId}`)"
        >{{ k.name || '未命名知识点' }}</button>
        <span v-if="reviewRequired.length > 5" class="ta-chip-more">+{{ reviewRequired.length - 5 }}</span>
      </div>

      <!-- Architecture-4.0-5：场景练习待补强（真实 SCENARIO_COMPLETED 事件驱动：最近一次未覆盖全部要点 → 深链回场景重答） -->
      <div v-if="scenarioDebt.length > 0 && !scenarioDebtLoading" class="ta-review-chips ta-scenario-debt-chips">
        <span class="ta-chips-label">待补强场景：</span>
        <button
          v-for="s in scenarioDebt.slice(0, 5)"
          :key="s.scenarioId"
          type="button"
          class="ta-chip"
          :title="`最近覆盖 ${s.matched}/${s.total} 个要点，回到场景补强再答`"
          @click="goTo(`/learning/scenario?id=${s.scenarioId}`)"
        >{{ s.scenarioTitle }}</button>
        <span v-if="scenarioDebt.length > 5" class="ta-chip-more">+{{ scenarioDebt.length - 5 }}</span>
      </div>

      <!-- Architecture-4.1 §28：轻量学习状态（当前知识 / 掌握 / 优势 / 需加强 / 下一步）——
           只在有真实 Evidence 时出现，不铺 20 张数据卡片；无数据整块隐藏（§33 不伪造） -->
      <div v-if="strengthSummary.strongest" class="ta-evidence">
        <div class="ta-evidence-head">
          <Icon name="trending-up" :size="13" />
          <span>当前情况</span>
          <span v-if="latestEvidenceLabel" class="ta-evidence-latest">最近：{{ latestEvidenceLabel }}</span>
        </div>
        <div class="ta-evidence-rows">
          <div class="ta-evidence-row">
            <span class="ta-evidence-key">优势</span>
            <span class="ta-evidence-val ta-evidence-good">
              {{ DIMENSION_LABEL[strengthSummary.strongest.dimension] }} {{ strengthSummary.strongest.score }} 分
            </span>
          </div>
          <div v-if="strengthSummary.weakest" class="ta-evidence-row">
            <span class="ta-evidence-key">需要加强</span>
            <span class="ta-evidence-val ta-evidence-bad">
              {{ DIMENSION_LABEL[strengthSummary.weakest.dimension] }} {{ strengthSummary.weakest.score }} 分
            </span>
          </div>
          <div v-if="nextStrategyLabel" class="ta-evidence-row">
            <span class="ta-evidence-key">下一步</span>
            <span class="ta-evidence-val">{{ nextStrategyLabel }}</span>
          </div>
        </div>
      </div>

      <!-- Architecture-4/5：下一步（§225 流程收尾：今日计划 → 待复习 → 薄弱 → 路径 → 下一步） -->
      <div class="ta-next">
        <NextActionCard :action="nextAction" dismissible />
      </div>

      <!-- Architecture-6：需要关注（理解层，§88-91/§157）——为什么建议关注、怎么处理 -->
      <div class="ta-insights">
        <LearningStateCard :diagnoses="centerDiagnoses" mode="center" />
      </div>
    </section>

    <!-- ========== Section 1: 每日名言卡片 ========== -->
    <section class="quote-card">
      <div class="quote-card-accent"></div>
      <div class="quote-card-body">
        <div class="quote-card-header">
          <span class="quote-card-badge">
            <Icon name="quote" :size="14" />
            每日一言
          </span>
          <button
            type="button"
            class="quote-refresh-btn"
            :disabled="quoteSwitching"
            title="换一条"
            @click="refreshQuote"
          >
            <Icon name="refresh-cw" :size="15" :class="{ 'spin': quoteSwitching }" />
            <span>换一条</span>
          </button>
        </div>
        <Transition name="quote-fade" mode="out-in">
          <blockquote :key="currentQuote.text" class="quote-card-text">
            {{ currentQuote.text }}
          </blockquote>
        </Transition>
        <div class="quote-card-footer">
          <span class="quote-card-line"></span>
          <cite class="quote-card-author">
            <span class="author-name">{{ currentQuote.author }}</span>
            <span v-if="currentQuote.title" class="author-title">{{ currentQuote.title }}</span>
          </cite>
        </div>
      </div>
    </section>

    <!-- ========== Section 2: 学习数据概览 ========== -->
    <section class="stats-bar">
      <div class="stats-greeting">
        <h1 class="kb-h1">{{ greeting }}，{{ userName }}</h1>
        <p class="kb-body">
          今天是你连续学习的第
          <span class="font-semibold tabular-nums" style="color: var(--kb-primary);">{{ streakDays }}</span>
          天，继续保持！本周已学习
          <span class="font-semibold tabular-nums" style="color: var(--kb-accent);">{{ weekHours }}</span>
          小时。
        </p>
      </div>
      <div class="stats-pills">
        <div class="stats-pill stats-pill-flame">
          <Icon name="flame" :size="16" />
          <span class="tabular-nums">连续 {{ streakDays }} 天</span>
        </div>
        <!-- Phase UI-5：无真实「今日分钟」接口时隐藏（不显示伪造的 0 分钟） -->
        <div v-if="todayMinutes > 0" class="stats-pill stats-pill-clock">
          <Icon name="clock" :size="16" />
          <span class="tabular-nums">今日 {{ todayMinutes }} 分钟</span>
        </div>
        <div class="stats-pill stats-pill-energy">
          <Icon name="zap" :size="16" />
          <span class="tabular-nums">能量 {{ energy }}</span>
        </div>
        <router-link to="/learning/mode" class="stats-pill stats-pill-focus" title="进入沉浸学习模式">
          <Icon name="moon" :size="16" />
          <span>沉浸模式</span>
        </router-link>
        <router-link to="/learning/focus" class="stats-pill stats-pill-focus" title="进入沉浸工作台">
          <Icon name="zap" :size="16" />
          <span>沉浸工作台</span>
        </router-link>
      </div>
    </section>

    <!-- Error -->
    <div v-if="error" class="rounded-lg border p-6 flex flex-col items-center justify-center gap-3 mb-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="alert-circle" :size="32" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button type="button" class="px-3 py-1.5 rounded-lg text-sm font-medium focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]" style="background: var(--kb-primary); color: var(--kb-primary-foreground);" @click="loadData">重新加载</button>
    </div>

    <!-- Loading -->
    <template v-else-if="loading">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-4">
        <div class="lg:col-span-2 space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div v-for="i in 4" :key="i" class="rounded-xl border p-4 animate-pulse h-56" style="background: var(--kb-card); border-color: var(--kb-border);"></div>
          </div>
        </div>
        <div class="space-y-4">
          <div class="rounded-xl border p-4 animate-pulse h-72" style="background: var(--kb-card); border-color: var(--kb-border);"></div>
        </div>
      </div>
    </template>

    <!-- Content -->
    <template v-else>
      <!-- ========== Section 3: 快速入口（3.2 §120-125：页面内部 Card 导航，默认前 8 + 更多） ========== -->
      <section class="quick-nav">
        <router-link
          v-for="(item, idx) in quickNavVisible"
          :key="idx"
          :to="item.to"
          class="quick-nav-card"
        >
          <div class="quick-nav-icon" :style="{ background: `${item.color}14`, color: item.color }">
            <Icon :name="item.icon" :size="22" />
          </div>
          <span class="quick-nav-label">{{ item.label }}</span>
          <Icon name="arrow-right" :size="14" class="quick-nav-arrow" />
        </router-link>
      </section>
      <div v-if="quickNavRestCount > 0 || showAllQuickNav" class="quick-nav-more">
        <button
          type="button"
          class="quick-nav-more-btn"
          :aria-expanded="showAllQuickNav"
          @click="showAllQuickNav = !showAllQuickNav"
        >
          <Icon :name="showAllQuickNav ? 'chevron-up' : 'chevron-down'" :size="14" />
          <span>{{ showAllQuickNav ? '收起工具' : `更多工具（${quickNavRestCount}）` }}</span>
        </button>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <!-- 左侧：进行中课程 + 今日任务 -->
        <div class="lg:col-span-2 space-y-4">
          <!-- 进行中课程 -->
          <section>
            <div class="flex items-center justify-between mb-4">
              <h2 class="kb-h2">进行中的课程</h2>
              <router-link to="/learning/paths" class="text-sm font-medium hover:opacity-80 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]" style="color: var(--kb-primary);">查看全部</router-link>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div
                v-for="(path, idx) in ongoingPaths"
                :key="path.id"
                class="path-card"
                @click="goToPath(path.id)"
              >
                <div class="path-card-cover" :style="{ background: `linear-gradient(135deg, ${pathColors[idx % pathColors.length]}1A, ${pathColors[idx % pathColors.length]}0D)` }">
                  <Icon :name="pathIcons[idx % pathIcons.length]" :size="56" :style="{ color: pathColors[idx % pathColors.length] }" />
                </div>
                <div class="p-4">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="text-[13px] px-2 py-0.5 rounded-full font-medium" :style="{ background: `${pathColors[idx % pathColors.length]}14`, color: pathColors[idx % pathColors.length] }">{{ path.categoryName || pathBadges[idx % pathBadges.length] }}</span>
                    <span class="kb-body-sm tabular-nums">{{ path.completedChapters || 0 }}/{{ path.chapterCount || 0 }} 章节</span>
                  </div>
                  <h3 class="kb-h3 mb-1 truncate">{{ path.title }}</h3>
                  <p class="kb-body-sm mb-3 line-clamp-1">{{ path.description || '系统化学习路径，循序渐进掌握技能' }}</p>
                  <div class="flex items-center justify-between mb-2">
                    <span class="text-[13px] font-medium tabular-nums" :style="{ color: pathColors[idx % pathColors.length] }">{{ pathProgress(path) }}%</span>
                    <span class="kb-body-sm tabular-nums">预计 {{ path.estimatedHours || 3 }} 小时完成</span>
                  </div>
                  <div class="w-full h-1.5 rounded-full" style="background: var(--kb-muted);">
                    <div class="h-full rounded-full transition-[width] duration-500" :style="{ width: `${pathProgress(path)}%`, background: pathColors[idx % pathColors.length] }"></div>
                  </div>
                  <button
                    type="button"
                    class="mt-3 w-full py-2 rounded-lg text-sm font-medium flex items-center justify-center gap-1.5 transition-opacity hover:opacity-90 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
                    style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
                    @click.stop="goToPath(path.id)"
                  >
                    <Icon name="play" :size="16" />继续学习
                  </button>
                </div>
              </div>
              <p v-if="ongoingPaths.length === 0" class="col-span-2 py-12 text-center text-sm" style="color: var(--kb-muted-foreground);">
                暂无进行中的课程，<router-link to="/learning/paths" class="hover:underline" style="color: var(--kb-primary);">浏览全部路径</router-link>
              </p>
            </div>
          </section>

          <!-- 今日任务 -->
          <section>
            <div class="flex items-center justify-between mb-4">
              <h2 class="kb-h2">今日任务</h2>
              <div class="flex items-center gap-3">
                <span class="kb-body-sm tabular-nums">已完成 {{ doneTasks }}/{{ tasks.length }}</span>
                <button type="button" class="add-task-btn" @click="showAddTask = !showAddTask">
                  <Icon name="plus" :size="14" />
                  <span>添加计划</span>
                </button>
              </div>
            </div>

            <!-- 添加计划表单 -->
            <div v-if="showAddTask" class="add-task-form">
              <input
                v-model="newTask.title"
                type="text"
                placeholder="计划标题，如：阅读《JavaScript 高级程序设计》第3章"
                class="add-task-input"
                @keydown.enter="handleCreateTask"
              />
              <textarea
                v-model="newTask.description"
                placeholder="描述（可选）"
                rows="2"
                class="add-task-textarea"
              ></textarea>
              <div class="flex items-center gap-2 mt-2">
                <select v-model="newTask.type" class="add-task-select">
                  <option value="study">学习</option>
                  <option value="reading">阅读</option>
                  <option value="practice">练习</option>
                  <option value="review">复习</option>
                  <option value="other">其他</option>
                </select>
                <input
                  v-model="newTask.deadline"
                  type="datetime-local"
                  class="add-task-date"
                />
                <div class="flex-1"></div>
                <button type="button" class="add-task-cancel" @click="resetAddTask">取消</button>
                <button type="button" class="add-task-submit" :disabled="creatingTask || !newTask.title.trim()" @click="handleCreateTask">
                  {{ creatingTask ? '添加中...' : '确认添加' }}
                </button>
              </div>
            </div>

            <div class="rounded-xl border divide-y" style="background: var(--kb-card); border-color: var(--kb-border);">
              <div
                v-for="task in tasks"
                :key="task.id"
                class="flex items-center gap-3 px-4 py-3.5 task-row"
              >
                <button
                  type="button"
                  class="task-check-btn"
                  :style="task.status === 1 ? { borderColor: 'var(--kb-accent)', background: 'var(--kb-accent)' } : task.status === 0 ? { borderColor: 'var(--kb-primary)' } : { borderColor: 'var(--kb-border)' }"
                  :title="task.status === 1 ? '标记为未完成' : '标记为已完成'"
                  @click="toggleTaskStatus(task)"
                >
                  <Icon v-if="task.status === 1" name="check" :size="12" style="color: var(--kb-accent-foreground);" />
                </button>
                <div class="flex-1 min-w-0">
                  <p
                    class="text-sm font-medium"
                    :style="task.status === 1 ? { color: 'var(--kb-muted-foreground)', textDecoration: 'line-through' } : { color: 'var(--kb-foreground)' }"
                  >{{ task.title }}</p>
                  <p v-if="task.description" class="kb-body-sm">{{ task.description }}</p>
                </div>
                <span
                  v-if="task.status === 1"
                  class="text-[13px] px-2 py-0.5 rounded-full whitespace-nowrap"
                  style="background: rgba(16,185,129,0.08); color: var(--kb-accent);"
                >已完成</span>
                <span
                  v-else-if="task.status === 0"
                  class="text-[13px] px-2 py-0.5 rounded-full whitespace-nowrap"
                  style="background: rgba(59,111,224,0.08); color: var(--kb-primary);"
                >进行中</span>
                <span
                  v-else
                  class="text-[13px] px-2 py-0.5 rounded-full whitespace-nowrap"
                  style="background: var(--kb-muted); color: var(--kb-muted-foreground);"
                >待开始</span>
                <button type="button" class="task-delete-btn" title="删除" @click="handleDeleteTask(task)">
                  <Icon name="trash-2" :size="14" />
                </button>
              </div>
              <p v-if="tasks.length === 0 && !showAddTask" class="px-4 py-10 text-center text-sm" style="color: var(--kb-muted-foreground);">
                暂无今日任务，点击上方「添加计划」按钮创建你的第一个学习计划吧
              </p>
            </div>
          </section>
        </div>

        <!-- 右侧：学习热力图 + 推荐下一步 + 本周概览 -->
        <div class="space-y-4">
          <!-- 学习热力图 -->
          <section class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
            <div class="flex items-center justify-between mb-3">
              <h3 class="kb-h3">学习热力图</h3>
              <span class="kb-body-sm">最近 {{ heatWeeks }} 周</span>
            </div>
            <div class="flex gap-0.5 flex-wrap justify-center mb-3 overflow-x-auto no-scrollbar">
              <div v-for="(week, wi) in heatWeeksData" :key="wi" class="flex flex-col gap-0.5">
                <div
                  v-for="(cell, di) in week"
                  :key="di"
                  class="heatmap-cell"
                  :class="cell ? `heatmap-${cell.level}` : 'opacity-0'"
                  :title="cell ? `${cell.date} · ${cell.count} 次` : ''"
                ></div>
              </div>
            </div>
            <div class="flex items-center justify-center gap-1.5 mt-2">
              <span class="kb-caption">少</span>
              <div class="heatmap-cell heatmap-0"></div>
              <div class="heatmap-cell heatmap-1"></div>
              <div class="heatmap-cell heatmap-2"></div>
              <div class="heatmap-cell heatmap-3"></div>
              <div class="heatmap-cell heatmap-4"></div>
              <span class="kb-caption">多</span>
            </div>
            <div class="mt-3 pt-3 flex items-center justify-between" style="border-top: 1px solid var(--kb-border);">
              <span class="kb-body-sm">本月累计</span>
              <span class="text-sm font-semibold tabular-nums" style="color: var(--kb-primary);">{{ monthHours }} 小时</span>
            </div>
            <p v-if="heatTotal === 0" class="text-[13px] mt-2" style="color: var(--kb-muted-foreground);">
              暂无学习记录，开始阅读或复习后会显示学习热力
            </p>
          </section>

          <!-- 本周概览 -->
          <section class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
            <h3 class="kb-h3 mb-3">本周概览</h3>
            <div class="grid grid-cols-2 gap-3">
              <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-primary);">{{ weekHours }}</p>
                <p class="kb-body-sm mt-1">学习时长(h)</p>
              </div>
              <!-- Phase UI-5：以下三项无真实数据接口，置 0 时隐藏，避免展示伪造统计（§59） -->
              <div v-if="weekChapters > 0" class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-accent);">{{ weekChapters }}</p>
                <p class="kb-body-sm mt-1">完成章节</p>
              </div>
              <div v-if="weekPractices > 0" class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-warning);">{{ weekPractices }}</p>
                <p class="kb-body-sm mt-1">练习题数</p>
              </div>
              <div v-if="accuracy > 0" class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
                <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-destructive);">{{ accuracy }}%</p>
                <p class="kb-body-sm mt-1">正确率</p>
              </div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
// 学习中心（Phase UI-4 重构）：今日行动中心 —— 待复习 / 待巩固 / 当前路径 / 今日任务 + 动态主 CTA。
// 原有内容（数据概览/热力图/推荐下一步/本周概览）保留在下部，第一屏以「今日行动」为核心。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import ReviewDueCard from '@/components/home/ReviewDueCard.vue'
import WeakPointCard from '@/components/home/WeakPointCard.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { userApi, learningApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/apps'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import LearningStateCard from '@/components/workflow/LearningStateCard.vue'
import { resolveNextAction } from '@/learning/action-priority'
import { actionToCta } from '@/learning/route'
import { scenarioPracticeDebt } from '@/learning/scenario-debt'
import { diagnoseLearningState } from '@/learning/intelligence/diagnosis'
import { resolveEvidenceBatch } from '@/learning/evidence/resolver'
import { aggregateEvidence } from '@/learning/evidence/aggregate'
import { diagnoseFromEvidence, strongestAndWeakest, DIMENSION_LABEL } from '@/learning/evidence/diagnosis'
import { buildTopCandidate } from '@/learning/evidence/action-candidate'
import { STRATEGY_LABEL } from '@/learning/evidence/strategy'
import type { NextLearningAction } from '@/learning/types';
import type { LearningDiagnosis } from '@/learning/intelligence/types';
import type {
  LearningTaskVO,
  LearningPathVO,
  LearningGoalVO,
  DailyActivityVO,
  LearningTaskInput,
  KnowledgeMasteryVO,
  LearningEventVO,
} from '@/api/types'
import type { ScenarioPracticeDebtItem } from '@/learning/scenario-debt'

const auth = useAuthStore()
const appStore = useAppStore()
const router = useRouter()
const loading = ref(false)
const error = ref('')

/* ================= Phase UI-4：今日行动数据（真实接口，独立错误隔离） ================= */
const reviewRequired = ref<KnowledgeMasteryVO[]>([])
const reviewLoading = ref(false)
const reviewError = ref('')
const weakPoints = ref<KnowledgeMasteryVO[]>([])
const weakLoading = ref(false)
const weakError = ref('')

/* ================= Architecture-4.0-5：场景练习待补强（真实 SCENARIO 事件驱动，Practice Debt） ================= */
const scenarioDebt = ref<ScenarioPracticeDebtItem[]>([])
const scenarioDebtLoading = ref(false)

async function loadReviewRequired() {
  reviewLoading.value = true
  reviewError.value = ''
  try {
    reviewRequired.value = await learningApi.knowledgeMasteryReviewRequired()
  } catch (e) {
    reviewError.value = getApiError(e, '待复习数据加载失败')
  } finally {
    reviewLoading.value = false
  }
}

async function loadWeakPoints() {
  weakLoading.value = true
  weakError.value = ''
  try {
    weakPoints.value = await learningApi.knowledgeMasteryWeak()
  } catch (e) {
    weakError.value = getApiError(e, '待巩固数据加载失败')
  } finally {
    weakLoading.value = false
  }
}

/**
 * 场景练习待补强（4.0-5）：拉取最近 50 条 SCENARIO_COMPLETED 事件 → 纯函数筛选「最近一次未覆盖全部要点」的场景。
 * 失败静默（页面只在有数据时显示条带，不阻塞学习中心）。
 */
async function loadScenarioDebt() {
  scenarioDebtLoading.value = true
  try {
    const page = await learningApi.learningEvents({ eventType: 'SCENARIO_COMPLETED', current: 1, size: 50 })
    scenarioDebt.value = scenarioPracticeDebt(page.records ?? [])
  } catch {
    scenarioDebt.value = []
  } finally {
    scenarioDebtLoading.value = false
  }
}

/** 今日完成数：真实 tasks 中 status === 1 的数量（§28/§29，不伪造百分比目标） */
const doneCount = computed(() => tasks.value.filter((t) => t.status === 1).length)
const donePercent = computed(() => {
  if (tasks.value.length === 0) return 0
  return Math.round((doneCount.value / tasks.value.length) * 100)
})

/** 当前学习路径：取第一个路径，仅读其章节进度，不做 N+1（§70/§71） */
const currentPath = computed(() => ongoingPaths.value[0] || null)

/* ============ Architecture-4.1：Evidence & Strategy Engine（§4-§30） ============
 * 数据流：最近学习事件 → resolveEvidenceBatch（Event→Evidence）
 *   → aggregateEvidence（多次证据 + 时间衰减 + 样本上限）
 *   → diagnoseFromEvidence（能力诊断 + 确定性根因）
 *   → buildTopCandidate（Strategy + App 可用性 + Goal 上下文）
 *   → 作为 ctx.evidence 注入既有 resolveNextAction 统一排序（§25：不重写 NextAction）。
 * 无事件 / 无证据时整条链自动为空，既有行为完全不变（§36）。
 */
const recentEvents = ref<LearningEventVO[]>([])

async function loadEvidence() {
  try {
    // 一次请求取最近全类型事件（后端支持不带 eventType 分页），避免 6 次类型轮询
    const page = await learningApi.learningEvents({ current: 1, size: 100 })
    recentEvents.value = page.records ?? []
  } catch {
    recentEvents.value = [] // 静默降级：Evidence 缺失不影响学习中心其余部分
  }
}

/** 事件 → 证据（不可解析的事件自动丢弃） */
const evidences = computed(() => resolveEvidenceBatch(recentEvents.value))
/** 证据 → 六维聚合（30 天窗口、时间衰减、样本上限） */
const evidenceAggregate = computed(() => aggregateEvidence(evidences.value))
/** 聚合 → 能力诊断（无证据返回 []，绝不编造诊断） */
const behaviorDiagnoses = computed(() => diagnoseFromEvidence(evidenceAggregate.value))
/** 诊断 → 策略 → 候选动作（过 App 可用性 + 绑定 Goal 上下文） */
const evidenceCandidate = computed(() =>
  buildTopCandidate(evidenceAggregate.value, behaviorDiagnoses.value, appStore.enabledApps, {
    goal: activeGoal.value,
  }),
)

/** §28 轻量状态：优势 / 需加强（只在有真实 Evidence 的维度里比较） */
const strengthSummary = computed(() => strongestAndWeakest(evidenceAggregate.value))
/** 最近一次证据的标签（用户自己填的主题，真实可见，用于「最近在练」上下文） */
const latestEvidenceLabel = computed(() => {
  const last = evidences.value[evidences.value.length - 1]
  return last ? last.label : null
})
/** 下一步策略名（供状态卡展示，与 NextAction 同源） */
const nextStrategyLabel = computed(() => {
  const c = evidenceCandidate.value
  return c ? STRATEGY_LABEL[c.strategy] : null
})

/** Architecture-4/4.0 + 4.1：唯一 Next Action 决策入口（§69/§179）——
 *  Review > Task > Evidence(4.1) > Weak > GoalPath > Path > Explore */
const nextAction = computed<NextLearningAction>(() =>
  resolveNextAction({
    reviewRequired: reviewRequired.value,
    weakPoints: weakPoints.value,
    tasks: tasks.value,
    currentPath: currentPath.value,
    goal: activeGoal.value,
    goalPath: goalLinkedPath.value,
    installedApps: appStore.enabledApps,
    evidence: evidenceCandidate.value,
  }),
)

/** 动态主 CTA（§13/§42）：与「下一步」卡片共用同一优先级来源（§69），不再各自定义 */
const mainCta = computed(() => actionToCta(nextAction.value))

/** Architecture-6：需要关注（§6.6/§88-91/§157）——理解+计划层。
 *  复用学习中心已加载的真实数据，本地纯函数推导，不新增 API（§123-129）。最多展示 3 条（§157）。 */
const centerDiagnoses = computed<LearningDiagnosis[]>(() =>
  diagnoseLearningState({
    reviewRequired: reviewRequired.value,
    weakPoints: weakPoints.value,
    tasks: tasks.value,
    currentPath: currentPath.value,
  }),
)

function goTo(path: string) {
  router.push(path)
}

const userName = computed(() => auth.user?.nickname || '同学')
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const streakDays = ref(0)
const weekHours = ref(0)
const todayMinutes = ref(0)
const monthHours = ref(0)
const weekChapters = ref(0)
const weekPractices = ref(0)
const accuracy = ref(0)
const energy = ref(0)

const ongoingPaths = ref<LearningPathVO[]>([])
const tasks = ref<LearningTaskVO[]>([])
/** Architecture-4.0：当前学习目标（activeGoal）与其关联路线（全量 paths 中匹配，不截断前 4 条） */
const activeGoal = ref<LearningGoalVO | null>(null)
const allPaths = ref<LearningPathVO[]>([])
const goalLinkedPath = computed<LearningPathVO | null>(() => {
  const g = activeGoal.value
  if (!g?.pathId) return null
  return allPaths.value.find((p) => p.id === g.pathId) ?? null
})

const pathColors = ['var(--kb-primary)', 'var(--kb-success)', 'var(--kb-warning)', 'var(--kb-danger)']
const pathIcons = ['code-2', 'database', 'brain', 'shield']
const pathBadges = ['前端', '后端', '算法', '安全']

const pathProgress = (p: LearningPathVO) => {
  if (!p.chapterCount || p.chapterCount === 0) return 0
  return Math.min(100, Math.round(((p.completedChapters || 0) / p.chapterCount) * 100))
}

const doneTasks = computed(() => tasks.value.filter((t) => t.status === 1).length)

// 名人名言数据池
interface Quote {
  text: string
  author: string
  title?: string
}

const quotes: Quote[] = [
  { text: '学而时习之，不亦说乎？', author: '孔子', title: '《论语》' },
  { text: '知之者不如好之者，好之者不如乐之者。', author: '孔子', title: '《论语》' },
  { text: '路漫漫其修远兮，吾将上下而求索。', author: '屈原', title: '《离骚》' },
  { text: '读书破万卷，下笔如有神。', author: '杜甫', title: '《奉赠韦左丞丈二十二韵》' },
  { text: '业精于勤，荒于嬉；行成于思，毁于随。', author: '韩愈', title: '《进学解》' },
  { text: '纸上得来终觉浅，绝知此事要躬行。', author: '陆游', title: '《冬夜读书示子聿》' },
  { text: '问渠那得清如许？为有源头活水来。', author: '朱熹', title: '《观书有感》' },
  { text: '千里之行，始于足下。', author: '老子', title: '《道德经》' },
  { text: '书山有路勤为径，学海无涯苦作舟。', author: '韩愈' },
  { text: '博学之，审问之，慎思之，明辨之，笃行之。', author: '子思', title: '《中庸》' },
  { text: '不积跬步，无以至千里；不积小流，无以成江海。', author: '荀子', title: '《劝学》' },
  { text: '学而不思则罔，思而不学则殆。', author: '孔子', title: '《论语》' },
  { text: '天行健，君子以自强不息。', author: '佚名', title: '《周易》' },
  { text: '三更灯火五更鸡，正是男儿读书时。', author: '颜真卿', title: '《劝学》' },
  { text: '黑发不知勤学早，白首方悔读书迟。', author: '颜真卿', title: '《劝学》' },
  { text: '少壮不努力，老大徒伤悲。', author: '佚名', title: '《汉乐府》' },
  { text: '玉不琢，不成器；人不学，不知道。', author: '佚名', title: '《礼记》' },
  { text: '敏而好学，不耻下问。', author: '孔子', title: '《论语》' },
  { text: '读万卷书，行万里路。', author: '刘彝', title: '《画旨》' },
  { text: '锲而舍之，朽木不折；锲而不舍，金石可镂。', author: '荀子', title: '《劝学》' },
]

// 随机选取一条名言（避免与当前重复）
const currentQuote = ref<Quote>(quotes[Math.floor(Math.random() * quotes.length)])
const quoteSwitching = ref(false)

function refreshQuote() {
  if (quoteSwitching.value || quotes.length <= 1) return
  quoteSwitching.value = true
  let next: Quote
  do {
    next = quotes[Math.floor(Math.random() * quotes.length)]
  } while (next.text === currentQuote.value.text)
  currentQuote.value = next
  setTimeout(() => { quoteSwitching.value = false }, 400)
}

/**
 * 学习首页快捷入口（3.2 §36-40/§120-125：页面内部 Card 导航，不是 Sidebar 菜单）。
 *
 * UI/UX 3.2 起，学习 Sidebar 只保留「学习首页」一个固定入口（§8/§28），
 * 原先的固定二级菜单（今日计划 / 学习路径 / 复习计划 / 掌握度 / 学习报告 / 挑战 /
 * 学习工作台 / 番茄钟 / 专注模式 / 编程 Agent）全部下沉到本页卡片，
 * 功能与路由一律保留（§51-59），只换入口位置（§105-107 避免死功能）。
 *
 * 前 8 个默认展示，其余折叠进「更多工具」（§118：学习首页不做第二个 Sidebar、§183：前 N 个 + 更多）。
 */
const QUICK_NAV_LIMIT = 8

const quickNavItems = [
  // ── 学习核心能力（页面内部，始终可用）
  { label: '今日计划', icon: 'calendar-check', color: 'var(--kb-primary)', to: '/learning/today-plan' },
  { label: '学习路径', icon: 'route', color: 'var(--kb-primary)', to: '/learning/paths' },
  { label: '复习计划', icon: 'calendar-check', color: 'var(--kb-warning)', to: '/learning/review' },
  { label: '掌握分布', icon: 'pie-chart', color: '#06B6D4', to: '/learning/mastery' },
  { label: '学习报告', icon: 'bar-chart-2', color: 'var(--kb-accent)', to: '/learning/report' },
  { label: '番茄钟专注', icon: 'timer', color: 'var(--kb-danger)', to: '/learning/pomodoro' },
  { label: '编程 Agent', icon: 'cpu', color: '#8B5CF6', to: '/coding/agent' },
  { label: '学习工作台', icon: 'brain', color: '#8B5CF6', to: '/workbench' },
  // ── 更多工具（含 App 类，按启用态过滤；与 Sidebar 动态菜单同源，不冲突 §127）
  { label: '代码练习', icon: 'code', color: 'var(--kb-warning)', to: '/learning/code-practice' },
  { label: '编程挑战', icon: 'rocket', color: '#8B5CF6', to: '/challenge' },
  // Architecture-4.0-4：场景练习（Scenario Lab，Practice 节点，非 App 无需启用态过滤）
  { label: '场景练习', icon: 'pen-tool', color: '#06B6D4', to: '/learning/scenario' },
  // Architecture-4.0-7：费曼表达（Expression 节点：四维确定性自检评分，非 App 无需启用态过滤）
  { label: '费曼表达', icon: 'book-open', color: '#8B5CF6', to: '/learning/feynman' },
  // Architecture-4.1：第一性原理推导（Understanding 节点：六环节结构完整度，非 App 无需启用态过滤）
  { label: '第一性原理', icon: 'compass', color: '#8B5CF6', to: '/learning/first-principles' },
  { label: '闪卡复习', icon: 'layers', color: 'var(--kb-success)', to: '/learning/flashcards' },
  { label: '知识图谱', icon: 'share-2', color: '#8B5CF6', to: '/learning/knowledge-graph' },
  { label: '行为事件', icon: 'activity', color: 'var(--kb-primary)', to: '/learning/events' },
  { label: 'AI 问答', icon: 'brain', color: '#06B6D4', to: '/chat' },
]

// UI/UX 3.0 §34：学习中心快捷入口中「App 类链接」按 UserApp 启用态过滤（不硬编码展示已停用 App）。
// 番茄钟（focus）为沉浸式核心工具（§13），始终保持；其余 App 类链接仅启用时显示。
const QUICK_NAV_APP_ROUTE: Record<string, string> = {
  '/learning/flashcards': 'flashcard',
  '/learning/code-practice': 'coding',
  '/learning/knowledge-graph': 'knowledge-graph',
}
const quickNavItemsResolved = computed(() =>
  quickNavItems.filter((it) => {
    const key = QUICK_NAV_APP_ROUTE[it.to]
    if (!key) return true
    const st = appStore.stateOf(key)
    return st.installed && st.enabled
  }),
)

/** 是否展开「更多工具」 */
const showAllQuickNav = ref(false)

/** 默认只渲染前 N 个（§183），展开后渲染全部 */
const quickNavVisible = computed(() =>
  showAllQuickNav.value
    ? quickNavItemsResolved.value
    : quickNavItemsResolved.value.slice(0, QUICK_NAV_LIMIT),
)

/** 折叠区剩余数量（为 0 时不渲染切换按钮） */
const quickNavRestCount = computed(() =>
  Math.max(0, quickNavItemsResolved.value.length - QUICK_NAV_LIMIT),
)

// ===== 添加计划功能 =====
const showAddTask = ref(false)
const creatingTask = ref(false)
const newTask = ref<LearningTaskInput>({
  title: '',
  description: '',
  type: 'study',
  deadline: '',
})

const resetAddTask = () => {
  showAddTask.value = false
  newTask.value = { title: '', description: '', type: 'study', deadline: '' }
}

const handleCreateTask = async () => {
  if (!newTask.value.title.trim()) {
    notify('请输入计划标题', 'warning')
    return
  }
  creatingTask.value = true
  try {
    const payload: LearningTaskInput = {
      title: newTask.value.title.trim(),
      description: newTask.value.description?.trim() || undefined,
      type: newTask.value.type || 'study',
    }
    if (newTask.value.deadline) {
      payload.deadline = new Date(newTask.value.deadline).toISOString()
    }
    await learningApi.createTask(payload)
    notify('计划添加成功', 'success')
    resetAddTask()
    // 重新加载任务列表
    const taskList = await learningApi.tasks().catch(() => [] as LearningTaskVO[])
    tasks.value = taskList.slice(0, 5)
  } catch (e: unknown) {
    notify('添加失败：' + getApiError(e), 'error')
  } finally {
    creatingTask.value = false
  }
}

const toggleTaskStatus = async (task: LearningTaskVO) => {
  const newStatus = task.status === 1 ? 0 : 1
  try {
    await learningApi.updateTaskStatus(task.id, newStatus)
    task.status = newStatus
    if (newStatus === 1) {
      notify('任务已完成', 'success')
    }
  } catch (e: unknown) {
    notify('操作失败：' + getApiError(e), 'error')
  }
}

const handleDeleteTask = async (task: LearningTaskVO) => {
  const ok = await confirmDialog(`确定删除计划「${task.title}」吗？`)
  if (!ok) return
  try {
    await learningApi.deleteTask(task.id)
    tasks.value = tasks.value.filter((t) => t.id !== task.id)
    notify('已删除', 'success')
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const goToPath = (id: number) => {
  window.location.href = `/learning/path/${id}`
}

// 热力图数据
interface HeatCell {
  date: string
  count: number
  level: number
}

const dailyActivity = ref<DailyActivityVO[]>([])
const heatWeeksData = computed<(HeatCell | null)[][]>(() => {
  const list = dailyActivity.value
  if (!list.length) return []
  const max = Math.max(1, ...list.map((d) => d.count))
  const firstDow = (new Date(`${list[0].date}T00:00:00`).getDay() + 6) % 7
  const cells: (HeatCell | null)[] = []
  for (let i = 0; i < firstDow; i++) cells.push(null)
  for (const d of list) {
    const level = d.count === 0 ? 0 : Math.min(4, Math.ceil((d.count / max) * 4))
    cells.push({ date: d.date, count: d.count, level })
  }
  const weeks: (HeatCell | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return weeks
})
const heatWeeks = computed(() => heatWeeksData.value.length || 16)
const heatTotal = computed(() => dailyActivity.value.reduce((sum, d) => sum + d.count, 0))

async function loadData(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [stats, paths, taskList, activity, goal] = await Promise.all([
      userApi.stats(),
      learningApi.paths().catch(() => [] as LearningPathVO[]),
      learningApi.tasks().catch(() => [] as LearningTaskVO[]),
      learningApi.dailyActivity(120).catch(() => [] as DailyActivityVO[]),
      learningApi.activeGoal().catch(() => null as LearningGoalVO | null),
    ])

    streakDays.value = stats.streakDays ?? 0
    // Phase UI-5（承接 UI-4 遗留 §59）：删除无依据的伪造统计。
    // 原实现用 totalStudyHours×6 / 70+闪卡数/10 / ×3 等推导"今日分钟/正确率/本周章节"，
    // 均无真实数据来源，违反"禁止伪造百分比"原则。
    // 现改为只呈现真实字段；无对应接口的一律置 0，交由模板 v-if 隐藏，而非凭空编造。
    weekHours.value = stats.totalStudyHours ?? 0
    monthHours.value = stats.totalStudyHours ?? 0
    todayMinutes.value = 0 // 无「今日分钟」接口，置 0 由模板隐藏
    weekChapters.value = 0 // 无「本周章节」接口，置 0 由模板隐藏
    weekPractices.value = 0 // 无「本周练习」接口，置 0 由模板隐藏
    accuracy.value = 0 // 无「正确率」接口，置 0 由模板隐藏
    energy.value = stats.energy ?? 0

    allPaths.value = paths
    ongoingPaths.value = paths.slice(0, 4)
    activeGoal.value = goal
    tasks.value = taskList.slice(0, 5)
    dailyActivity.value = activity
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载失败'
    error.value = `学习数据加载失败：${message}`
    notify('学习数据加载失败', 'error')
  } finally {
    loading.value = false
  }

  // Phase UI-4：待复习 / 待巩固 独立并联加载，任一失败只影响对应卡片，不阻塞学习中心（§44/§45）
  // Architecture-4.1：Evidence 事件流同样独立加载（失败静默，Evidence 缺失不阻塞既有功能）
  await Promise.allSettled([loadReviewRequired(), loadWeakPoints(), loadScenarioDebt(), loadEvidence()])
}

onMounted(() => {
  void loadData()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

/* ===== Phase UI-4: 今日行动区 ===== */
.today-action {
  margin-bottom: 24px;
}
.today-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.ta-title {
  margin: 0;
  font-size: var(--kb-fs-h2);
  font-weight: 700;
  line-height: var(--kb-lh-h2);
  color: var(--kb-foreground);
}
.ta-subtitle {
  margin-top: 4px;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-md);
}
.ta-progress {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.ta-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.ta-label {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
}
.ta-count {
  color: var(--kb-foreground);
  font-weight: 600;
  font-size: var(--kb-fs-body-sm);
}
.ta-progress-bar {
  height: 8px;
  border-radius: 9999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.ta-progress-fill {
  height: 100%;
  border-radius: 9999px;
  background: var(--kb-primary);
  transition: width 200ms ease;
}
.ta-buckets {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  margin-top: 16px;
}
@media (min-width: 1024px) {
  .ta-buckets {
    grid-template-columns: 1fr 1fr;
  }
}
.ta-path {
  margin-top: 16px;
  padding: 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.ta-path-title {
  margin-top: 4px;
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
}
.ta-path-empty {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-md);
}

/* Architecture-5 §23：待复习具体知识点 chips（学习中心展示明细，Home 只展示数量） */
.ta-review-chips {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ta-chips-label {
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  color: var(--kb-muted-foreground);
}
.ta-chip {
  padding: 4px 12px;
  border: 1px solid var(--kb-border);
  border-radius: 999px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.ta-chip:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.ta-chip-more {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}

/* Architecture-4.1 §28：轻量学习状态卡（当前情况 → 下一步，不铺数据看板） */
.ta-evidence {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-bg);
}
.ta-evidence-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  color: var(--kb-foreground);
}
.ta-evidence-latest {
  margin-left: auto;
  font-weight: 400;
  color: var(--kb-muted-foreground);
}
.ta-evidence-rows {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
}
.ta-evidence-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-caption);
}
.ta-evidence-key {
  color: var(--kb-muted-foreground);
}
.ta-evidence-val {
  font-weight: 600;
  color: var(--kb-foreground);
}
.ta-evidence-good {
  color: var(--kb-success);
}
.ta-evidence-bad {
  color: var(--kb-danger);
}

/* Architecture-5 §225：下一步收尾主流程 */
.ta-next {
  margin-top: 16px;
}

/* Architecture-6：需要关注（理解层） */
.ta-insights {
  margin-top: 16px;
}

/* 添加计划按钮 */
.add-task-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
  color: var(--kb-primary);
  cursor: pointer;
  transition: background 0.15s ease;
}
.add-task-btn:hover {
  background: rgba(59, 111, 224, 0.12);
}

/* 添加计划表单 */
.add-task-form {
  margin-bottom: 12px;
  padding: 16px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.add-task-input {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  font-size: 14px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease;
}
.add-task-input:focus {
  border-color: var(--kb-primary);
}
.add-task-textarea {
  width: 100%;
  margin-top: 8px;
  padding: 8px 12px;
  font-size: 13px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  resize: none;
  transition: border-color 0.15s ease;
}
.add-task-textarea:focus {
  border-color: var(--kb-primary);
}
.add-task-select {
  height: 32px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
}
.add-task-date {
  height: 32px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
}
.add-task-cancel {
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.15s ease;
}
.add-task-cancel:hover {
  background: var(--kb-muted);
}
.add-task-submit {
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.add-task-submit:hover {
  opacity: 0.9;
}
.add-task-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 任务行交互 */
.task-row {
  transition: background 0.15s ease;
}
.task-row:hover {
  background: var(--kb-muted);
}
.task-check-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--kb-border);
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}
.task-check-btn:hover {
  transform: scale(1.1);
}
.task-delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
}
.task-row:hover .task-delete-btn {
  opacity: 1;
}
.task-delete-btn:hover {
  color: var(--kb-destructive);
  background: rgba(239, 68, 68, 0.08);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.kb-h1 { font-size: 28px; font-weight: 700; line-height: 1.3; letter-spacing: -0.02em; color: var(--kb-foreground); font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif); }
.kb-h2 { font-size: 22px; font-weight: 600; line-height: 1.35; letter-spacing: -0.01em; color: var(--kb-foreground); font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif); }
.kb-h3 { font-size: 18px; font-weight: 600; line-height: 1.4; color: var(--kb-foreground); }
.kb-body { font-size: 14px; line-height: 1.6; color: var(--kb-card-foreground); }
.kb-body-sm { font-size: 13px; line-height: 1.5; color: var(--kb-muted-foreground); }
.kb-caption { font-size: 12px; font-weight: 500; line-height: 1.4; color: var(--kb-muted-foreground); letter-spacing: 0.04em; text-transform: uppercase; }

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }

/* ========== 每日名言卡片 ========== */
.quote-card {
  position: relative;
  display: flex;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 20px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  transition: box-shadow 0.2s ease;
}
.quote-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.quote-card-accent {
  width: 5px;
  flex-shrink: 0;
  background: linear-gradient(180deg, var(--kb-primary) 0%, #8B5CF6 100%);
}

.quote-card-body {
  flex: 1;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.quote-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quote-card-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.03em;
  padding: 4px 10px;
  border-radius: 20px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

.quote-refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  padding: 6px 14px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}
.quote-refresh-btn:hover:not(:disabled) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.05);
}
.quote-refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.quote-refresh-btn .spin {
  animation: spin 0.6s linear;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.quote-card-text {
  font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif);
  font-size: 20px;
  font-weight: 500;
  line-height: 1.7;
  color: var(--kb-foreground);
  letter-spacing: 0.01em;
  margin: 0;
  quotes: none;
  text-align: left;
}
.quote-card-text::before {
  content: none;
}
.quote-card-text::after {
  content: none;
}

.quote-card-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}
.quote-card-line {
  width: 32px;
  height: 2px;
  border-radius: 2px;
  background: var(--kb-primary);
  flex-shrink: 0;
  opacity: 0.5;
}
.quote-card-author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-style: normal;
}
.author-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.author-title {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* 名言切换过渡动画 */
.quote-fade-enter-active,
.quote-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.quote-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.quote-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ========== 学习数据概览条 ========== */
.stats-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  padding: 20px 24px;
  margin-bottom: 20px;
}

.stats-greeting {
  min-width: 0;
}

.stats-pills {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.stats-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}

.stats-pill-flame {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

.stats-pill-clock {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-accent);
}

.stats-pill-energy {
  background: rgba(245, 158, 11, 0.08);
  color: var(--kb-warning);
}

.stats-pill-focus {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  text-decoration: none;
  transition: background 0.15s, transform 0.15s;
}
.stats-pill-focus:hover {
  background: rgba(99, 102, 241, 0.18);
  transform: translateY(-1px);
}

/* ========== 快速入口 ========== */
.quick-nav {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .quick-nav {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 480px) {
  .quick-nav {
    grid-template-columns: repeat(2, 1fr);
  }
}

.quick-nav-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 8px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  transition: all 0.2s ease;
  cursor: pointer;
  text-decoration: none;
  position: relative;
}

.quick-nav-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 12px rgba(59, 111, 224, 0.08);
  transform: translateY(-2px);
}

.quick-nav-card:hover .quick-nav-arrow {
  opacity: 1;
  transform: translateX(0);
}

.quick-nav-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-nav-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.quick-nav-arrow {
  position: absolute;
  top: 12px;
  right: 10px;
  color: var(--kb-primary);
  opacity: 0;
  transform: translateX(-4px);
  transition: all 0.2s ease;
}

/* 3.2 §183：「更多工具」折叠开关（默认只展示前 8 个，避免学习首页变第二个 Sidebar） */
.quick-nav-more {
  display: flex;
  justify-content: center;
  margin: -8px 0 20px;
}

.quick-nav-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--kb-border);
  border-radius: 999px;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.quick-nav-more-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
  border-color: var(--kb-primary);
}

.quick-nav-more-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}

/* ========== 课程卡片 ========== */
.path-card {
  border-radius: 14px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.path-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.path-card-cover {
  height: 112px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Heatmap levels */
.heatmap-cell { width: 16px; height: 16px; border-radius: 3px; }
.heatmap-0 { background: var(--kb-muted); }
.heatmap-1 { background: rgba(59, 111, 224, 0.2); }
.heatmap-2 { background: rgba(59, 111, 224, 0.4); }
.heatmap-3 { background: rgba(59, 111, 224, 0.65); }
.heatmap-4 { background: var(--kb-primary); }

/* 移动端适配 */
@media (max-width: 768px) {
  .quote-card-body {
    padding: 18px 20px;
    gap: 12px;
  }
  .quote-card-text {
    font-size: 17px;
  }
  .quote-refresh-btn span {
    display: none;
  }
  .quote-refresh-btn {
    padding: 6px 8px;
  }
  .stats-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
