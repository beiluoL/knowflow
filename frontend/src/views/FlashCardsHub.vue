<template>
  <MiniAppShell
    title="闪卡学习"
    subtitle="进入 → 回忆 → 自评 → 系统记录 → 下一张"
    :knowledge-name="knowledgeContext?.name"
    :clearable-context="!!knowledgeContext"
    back-label="返回"
    @back="goBack"
    @clear-context="clearContext"
  >
    <div class="flashcards-hub phase-c-flashcards animate-fade-in">
    <FlashcardExchangeDialog v-if="exchangeMode" :mode="exchangeMode" @close="exchangeMode = null" @saved="reloadMine(); loadLearnCards()" />
    <FlashcardSettingsDialog v-if="settingsOpen" @close="settingsOpen = false" @loaded="settings = $event" @saved="reviewSettingsSaved" />
    <ChapterResourceLinks resource-kind="flashcard" />
    <!-- ===== Tab 切换（§十七：会话中收起，不与管理控件抢视觉焦点） ===== -->
    <div v-if="!inSession" class="tabs-bar" role="tablist" aria-label="闪卡模式切换">
      <button
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === 'learn' }"
        role="tab"
        :aria-selected="activeTab === 'learn'"
        @click="activeTab = 'learn'"
      >
        <Icon name="graduation-cap" :size="16" />
        <span>学习闪卡</span>
        <span class="tab-count tabular-nums">{{ learnTotal }}</span>
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === 'review' }"
        role="tab"
        :aria-selected="activeTab === 'review'"
        @click="activeTab = 'review'"
      >
        <Icon name="sparkles" :size="16" />
        <span>推荐复习</span>
        <span class="tab-count tabular-nums">{{ reviewTotal }}</span>
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === 'mine' }"
        role="tab"
        :aria-selected="activeTab === 'mine'"
        @click="activeTab = 'mine'"
      >
        <Icon name="bookmark" :size="16" />
        <span>我的闪卡</span>
        <span class="tab-count tabular-nums">{{ mineTotal }}</span>
      </button>
    </div>

    <!-- ===== 统一搜索框（§九：降为二级入口，默认收起；§十七：会话中不显示） ===== -->
    <div v-if="searchOpen && !inSession" class="global-search">
      <Icon name="search" :size="14" class="search-icon" />
      <input
        v-model="globalKeyword"
        type="text"
        :placeholder="searchPlaceholder"
        class="search-input kf-input kf-form-control"
        @keyup.enter="onGlobalSearch"
      />
      <button v-if="globalKeyword" type="button" class="clear-btn" @click="clearSearch" aria-label="清除">
        <Icon name="x" :size="14" />
      </button>
    </div>

    <!-- ===== Tab 1: 学习闪卡 ===== -->
    <section v-show="activeTab === 'learn'" class="tab-panel">
      <!-- §四/§十七：顶部只保留「进度 / 今日学习 / 待复习」；会话中额外提供退出与快捷键提示 -->
      <div v-if="!learnLoading && !learnError && learnFiltered.length > 0 && sessionStarted" class="learn-topbar">
        <span class="learn-counter tabular-nums" aria-live="polite">
          {{ learnIndex + 1 }} / {{ learnFiltered.length }}
        </span>
        <div class="learn-progress-track" role="progressbar" :aria-valuenow="learnProgress"
             aria-valuemin="0" aria-valuemax="100" :aria-label="`本轮已保存评价 ${learnProgress}%`">
          <div class="learn-progress-fill" :style="{ width: `${learnProgress}%` }"></div>
        </div>
        <span class="learn-stat">今日学习 <b class="tabular-nums">{{ todayLearned }}</b></span>
        <span class="learn-stat" :title="`SRS 已到期需复习的闪卡（${learnDueCount} 张）`">
          待复习 <b class="tabular-nums">{{ learnDueCount }}</b>
        </span>
        <div class="learn-topbar-actions">
          <button v-if="inSession" type="button" class="learn-icon-btn" aria-label="退出学习会话"
                  title="退出学习会话（Esc）" @click="exitSessionMode">
            <Icon name="x" :size="16" />
          </button>
          <button type="button" class="learn-icon-btn" :class="{ active: searchOpen }"
                  :aria-expanded="searchOpen" aria-label="搜索闪卡" title="搜索" @click="searchOpen = !searchOpen">
            <Icon name="search" :size="16" />
          </button>
          <button type="button" class="learn-icon-btn" aria-label="随机排序" title="随机排序" @click="shuffleCards">
            <Icon name="shuffle" :size="16" />
          </button>
          <Select class="select-inline" v-model="selectedCategory" aria-label="按分类筛选闪卡" :options="categories.map(cat => ({ value: cat, label: cat }))" />
        </div>
      </div>

      <!-- 快捷键轻量提示（§十八：会话中才显示，不长期占用空间） -->
      <p v-if="inSession" class="kf-hint">
        <Icon name="info" :size="12" /><span>{{ keyboardHint }}</span>
      </p>

      <!-- 加载态（§二十二：骨架卡，不是空白 + Loading…） -->
      <div v-if="learnLoading" class="state-area" aria-busy="true" aria-label="闪卡加载中">
        <div class="skeleton-card">
          <div class="sk-line sk-tag"></div>
          <div class="sk-line sk-title"></div>
          <div class="sk-line sk-body"></div>
          <div class="sk-line sk-body short"></div>
        </div>
      </div>

      <!-- 错误态（§六：① 发生了什么 ② 是否影响当前页面 ③ 怎么解决；不渲染原始异常） -->
      <div v-else-if="learnError" class="state-area" role="alert">
        <Icon name="alert-circle" :size="40" class="state-icon state-icon-error" />
        <p class="state-text">{{ learnError.title }}</p>
        <p class="state-hint">{{ learnError.scope }}</p>
        <p class="state-hint">{{ learnError.hint }}</p>
        <div class="empty-actions">
          <button type="button" class="btn-primary" @click="loadLearnCards">
            <Icon name="refresh-cw" :size="14" /><span>重新加载</span>
          </button>
        </div>
      </div>

      <!-- 空态（§二十一：状态 / 原因 / 下一步三要素，按场景分别给出） -->
      <div v-else-if="learnFiltered.length === 0" class="state-area">
        <Icon name="layers" :size="48" class="state-icon" />
        <template v-if="globalKeyword">
          <p class="state-text">没有匹配的闪卡</p>
          <p class="state-hint">当前关键词「{{ globalKeyword }}」在{{ selectedCategory === '全部' ? '全部卡片' : `「${selectedCategory}」分类` }}中没有命中。</p>
          <div class="empty-actions">
            <button type="button" class="btn-outline" @click="clearSearch">
              <Icon name="x" :size="14" /><span>清除搜索条件</span>
            </button>
          </div>
        </template>
        <template v-else-if="knowledgeContext || routeChapterId">
          <p class="state-text">这个学习节点还没有闪卡</p>
          <p class="state-hint">
            当前上下文「{{ knowledgeContext?.name || `章节 ${routeChapterId}` }}」下没有可用闪卡。
            可以清除上下文后练习全部闪卡，或去「我的闪卡」为它创建一批。
          </p>
          <div class="empty-actions">
            <button type="button" class="btn-primary" @click="clearContext">
              <Icon name="layers" :size="14" /><span>查看全部闪卡</span>
            </button>
            <button type="button" class="btn-outline" @click="activeTab = 'mine'">
              <Icon name="plus" :size="14" /><span>去创建闪卡</span>
            </button>
          </div>
        </template>
        <template v-else>
          <p class="state-text">暂无可学习的闪卡</p>
          <p class="state-hint">系统闪卡来自已发布的学习路径；你还没有报名任何路径，或路径下暂无闪卡。</p>
          <div class="empty-actions">
            <button type="button" class="btn-primary" @click="activeTab = 'mine'">
              <Icon name="plus" :size="14" /><span>去创建闪卡</span>
            </button>
            <button type="button" class="btn-outline" @click="goBack">
              <Icon name="route" :size="14" /><span>浏览学习路径</span>
            </button>
          </div>
        </template>
      </div>

      <!-- §九 准备态：先明确「要学什么 / 有多少 / 从哪来」，再进学习态。
           用户明确点「开始学习」（或 Space/Enter）后才计入一次学习会话。 -->
      <div v-else-if="showPreparation" class="prep-panel">
        <div class="prep-card">
          <span class="prep-badge"><Icon name="play" :size="13" /><span>准备开始</span></span>
          <h2 class="prep-title">你将复习 <b class="tabular-nums">{{ learnFiltered.length }}</b> 张卡片</h2>
          <p class="prep-source">
            来源：<b>{{ prepSourceLabel }}</b>
          </p>
          <ul class="prep-meta">
            <li v-if="learnDueCount > 0">
              <Icon name="clock" :size="14" />其中 <b class="tabular-nums">{{ learnDueCount }}</b> 张已到复习时间
            </li>
            <li v-if="prepWeakCount > 0">
              <Icon name="alert-circle" :size="14" /><b class="tabular-nums">{{ prepWeakCount }}</b> 张来自薄弱知识
            </li>
            <li>
              <Icon name="info" :size="14" />每张的自评都会写入学习记录，用于调整后续复习节奏
            </li>
          </ul>
          <!-- P2-UI-Flashcards：Action Group 两个按钮等高（36px）、同圆角、同图标尺寸（16px）、
               同图标↔文字间距（gap 8px）；只用填充/描边区分 Primary 与 Secondary。 -->
          <div class="prep-controls">
            <div class="prep-schedule"><span>排程</span><Select class="select-inline" v-model="algorithmMode" aria-label="复习排程" :options="[{ value: 'fsrs', label: 'FSRS 智能复习（四档）' }, { value: 'legacy', label: '保留旧 SM-2（三档）' }]" /></div>
            <Checkbox v-model="dueOnly" label="仅新卡与到期卡" />
            <button type="button" class="btn-outline" @click="openReviewSettings">复习设置</button>
          </div>
          <p v-if="isFsrs" class="form-hint">首次评分开始收集 FSRS 记忆状态，旧卡身份、原到期时间和历史评分保留；预览不会改动数据。</p>
          <div class="prep-actions">
            <button type="button" class="btn-primary" @click="startSession">
              <Icon name="play" :size="16" /><span>开始学习</span>
            </button>
            <button type="button" class="btn-outline" @click="searchOpen = true">
              <Icon name="search" :size="16" /><span>先筛选卡片</span>
            </button>
          </div>
          <p class="prep-hint">快捷键：Space / Enter 开始</p>
        </div>
      </div>

      <!-- 闪卡翻转展示区（§八：标签 → 问题 → 提示 → 翻卡；卡片本身就是主交互） -->
      <!-- UI/UX 6.1：会话态接入 FocusShell 全屏专注模式 -->
      <FocusShell
        manage-focus
        reading-layout
        flashcard-layout
        progress-description="本轮已保存评价的独立卡片数 / 开始时的队列张数；不是今日评分次数"
        :exit-disabled="reviewing"
        v-else-if="inSession"
        :aria-label="`闪卡学习 · ${learnIndex + 1} / ${learnFiltered.length}`"
        :progress="{ current: ratedInSession, total: learnFiltered.length }"
        :has-unsaved-progress="ratedInSession > 0"
        :exit-message="`已完成 ${ratedInSession} / ${learnFiltered.length} 张。已提交的评价会保留。`"
        @exit="handleFocusExit"
      >
        <template #brand><span class="review-brand"><FlashcardReviewIcon name="book" /><span>KnowFlow</span></span></template>
        <template #mode><span class="session-mode sr-only">{{ isFsrs ? 'FSRS 复习' : 'SM-2 复习' }}</span></template>
        <template #status>
          <div class="session-status">
            <span v-if="reviewing" role="status" aria-live="polite">正在保存评价…</span>
            <span v-else class="session-statistics" title="今日新卡为首次评分的卡片数，今日复习为其余评分次数；按设置时区与日切统计，与本轮独立卡片进度不同">
              <template v-if="todayStats"><Icon name="bar-chart-2" :size="18" /><span class="daily-statistics">今日新卡 <b>{{ todayStats.newCards }}</b> · 今日复习 <b>{{ todayStats.reviews }}</b> 次 · </span><span>本轮完成 <b>{{ ratedInSession }}</b></span></template>
              <template v-else>今日统计暂不可用</template>
            </span>
            <button type="button" class="btn-outline btn-sm review-settings" aria-label="设置" :disabled="reviewing || settingsOpen" @click="openReviewSettings"><Icon name="settings" :size="20" /><span>设置</span></button>
          </div>
        </template>
        <section ref="readingStage" class="flashcard-stage" aria-label="闪卡阅读区">
        <p class="session-position">第 {{ learnIndex + 1 }} / {{ learnFiltered.length }} 张 · 本轮已保存 {{ ratedInSession }} 张</p>
        <details class="session-mobile-statistics"><summary>今日统计</summary><p v-if="todayStats">今日新卡 {{ todayStats.newCards }} · 今日复习 {{ todayStats.reviews }} 次 · 本轮完成 {{ ratedInSession }}</p><p v-else>今日统计暂不可用</p></details>
        <p v-if="lastDueAt" class="session-receipt" role="status">已记录，上张卡下次复习：{{ dueLabel(lastDueAt) }}</p>
        <div
          :key="currentCard?.id"
          class="flip-card"
          :class="{ flipped: isFlipped, dragging: drag.active }"
          :style="dragStyle"
          @pointerdown="beginDrag"
          @pointermove="moveDrag"
          @pointerup="endDrag"
          @pointercancel="cancelDrag"
          @lostpointercapture="cancelDrag"
          role="button"
          tabindex="0"
          :aria-label="isFlipped ? '查看答案中，按 Space 或点击翻回问题' : '翻转卡片查看答案（Space）'"
          :aria-pressed="isFlipped"
          @click="onCardClick"
          @keydown.enter="onCardKeydown"
          @keydown.space="onCardKeydown"
        >
          <div class="flip-card-inner" @transitionend.self="flipping = false">
            <div class="flip-card-face flip-card-front" :inert="isFlipped" :aria-hidden="isFlipped">
              <div class="card-front-label"><Icon name="layers" :size="20" /><span>问题</span></div>
              <div class="face-question markdown-body" v-html="renderCard(currentCard?.question)"></div>
              <div class="face-tags">
                <span v-if="currentCard?.category" class="face-tag tag-category">{{ currentCard.category }}</span>
                <span v-if="currentCard?.difficulty != null" class="face-tag tag-sub">{{ getDifficultyLabel(currentCard.difficulty) }}</span>
                <!-- §十五：轻量「为什么是这张卡」徽章 -->
                <span
                  v-for="b in currentCard?.badges || []"
                  :key="b.kind"
                  class="face-tag"
                  :class="`tag-why tag-why-${b.kind}`"
                >{{ b.label }}</span>
              </div>
              <p class="face-hint">先在心中回答，再查看答案</p>
              <div class="flip-pill"><Icon name="rotate-ccw" :size="14" /><span>点击翻转（Space）</span></div>
            </div>
            <div class="flip-card-face flip-card-back" :inert="!isFlipped" :aria-hidden="!isFlipped">
              <div class="back-header">
                <Icon name="lightbulb" :size="16" class="back-icon" />
                <span class="back-label">参考答案</span>
              </div>
              <div class="back-body">
                <h3 class="back-title">{{ searchSummary(currentCard?.question ?? '') }}</h3>
                <div class="back-text markdown-body" v-html="renderCard(currentCard?.answer)"></div>
                <div class="back-tags">
                  <span class="tag-heading"><FlashcardReviewIcon name="tag" />知识标签</span>
                  <span v-if="currentCard?.category" class="back-tag tag-category">{{ currentCard.category }}</span>
                  <span v-if="currentCard?.difficulty != null" class="back-tag tag-sub">{{ getDifficultyLabel(currentCard.difficulty) }}</span>
                  <!-- §九：关联知识点 —— 只显示真实上下文（深链/映射），无则不显示，不伪造 -->
                  <span v-if="knowledgeContext" class="back-tag back-tag-kb">
                    关联知识：{{ knowledgeContext.name }}
                  </span>
                </div>
              </div>
              <span class="gesture-handle"><FlashcardReviewIcon name="hand" /><span>拖动评分：{{ isFsrs ? '左重来 · 右记得 · 上简单' : '左不会 · 右记得' }}</span><span v-if="dragLabel" class="drag-label" aria-live="polite">{{ dragLabel }}</span></span>
            </div>
          </div>
        </div>

      <div v-if="isFsrs && (scheduleLoading || scheduleError)" class="kf-hint" :role="scheduleError ? 'alert' : 'status'">
        {{ scheduleLoading ? '正在计算真实复习间隔…' : scheduleError }}
        <button v-if="scheduleError" type="button" class="btn-outline btn-sm" @click="loadSchedule">重新计算</button>
      </div>
      <details class="session-help">
        <summary>快捷键与手势</summary>
        <p>Space 翻转 · ← → 切换 · Esc 退出</p>
        <p v-if="isFsrs">1 重来 · 2 困难 · 3 记得 · 4 简单；困难请用按钮或 2。</p>
        <p v-else>1 不会 · 2 模糊 · 3 记得；模糊请用按钮或 2。</p>
        <p>查看答案后，拖动卡片底部手柄：{{ isFsrs ? '左重来、右记得、上简单' : '左不会、右记得' }}，越过阈值后松手评分。正文可选字，代码可横向滚动。</p>
        <p>先完成当前评价的保存或重试，再切换卡片。自评不等于客观正确率。</p>
        <p v-if="knowledgeContext">关联知识依据：KnowledgeId：{{ knowledgeContext.id }}</p>
      </details>
      </section>
      <!-- §二十三：提交失败必须显式告知，且不跳过该卡；提供重试（§六：不渲染原始异常） -->
      <div v-if="rateError" ref="rateErrorElement" class="rate-error" role="alert" tabindex="-1">
        <Icon name="alert-circle" :size="15" />
        <div class="rate-error-text">
          <b>{{ rateError.title }}</b>
          <span>{{ rateError.hint }} 这张卡仍停留在当前位置。</span>
        </div>
        <button type="button" class="btn-outline btn-sm" :disabled="reviewing" @click="retryRate">
          <Icon name="refresh-cw" :size="13" /><span>重试</span>
        </button>
      </div>

      <!-- §九/§二十六：底部只保留三个学习动作（不会 / 模糊 / 记得）；翻页降为次要图标 -->
      <!-- UI/UX 5.1 §九：准备态不出现评分动作（避免绕过「开始学习」直接产生学习记录） -->
      <template #actions>
        <div
          v-if="!learnLoading && !learnError && learnFiltered.length > 0 && sessionStarted"
          class="action-bar"
        >
          <button type="button" class="action-btn btn-ghost-icon" :disabled="learnIndex === 0 || reviewing || flipping || !!rateError"
                  aria-label="上一张（←）" title="上一张（←）" @click="prevCard">
            <Icon name="skip-back" :size="16" />
          </button>
          <button v-if="!isFlipped" type="button" class="action-btn btn-primary" :disabled="reviewing || flipping" @click="flipCard">显示答案</button>
          <template v-if="isFlipped && isFsrs">
            <button v-for="(label, index) in fsrsLabels" :key="label" type="button" class="action-btn fsrs-rating" :title="schedulePreview?.previews[index] ? `预计复习时间：${dueLabel(schedulePreview.previews[index]!.dueAt)}` : '待计算'" :class="`rating-${index + 1}`" :disabled="reviewing || flipping || !!rateError || scheduleLoading || !schedulePreview" :aria-busy="reviewing && submittingRating === index + 1" :aria-label="`${fsrsNames[index]}：${label}（${index + 1}）`" @click="rateCard(fsrsSummaryRating(index + 1), undefined, index + 1)">
              <Icon v-if="reviewing && submittingRating === index + 1" name="loader" :size="28" class="rating-icon is-saving" />
              <FlashcardReviewIcon v-else :name="ratingIconNames[index]!" class="rating-icon" />
              <span class="rating-copy"><span class="rating-title">{{ reviewing && submittingRating === index + 1 ? '保存中…' : `${fsrsNames[index]} · ${label}` }}</span><small>{{ intervalLabel(schedulePreview?.previews[index]?.intervalSeconds) }}</small></span>
            </button>
          </template>
          <button v-if="isFlipped && !isFsrs" type="button" class="action-btn btn-danger" :disabled="reviewing || flipping"
                  aria-label="不会（1）" title="不会（1）" @click="rateCard('unknown')">
            <Icon name="x" :size="14" /><span>{{ RATING_LABEL.unknown }}</span>
          </button>
          <button v-if="isFlipped && !isFsrs" type="button" class="action-btn btn-warning" :disabled="reviewing || flipping"
                  aria-label="模糊（2）" title="模糊（2）" @click="rateCard('familiar')">
            <Icon name="rotate-ccw" :size="14" /><span>{{ RATING_LABEL.familiar }}</span>
          </button>
          <button v-if="isFlipped && !isFsrs" type="button" class="action-btn btn-success" :disabled="reviewing || flipping"
                  aria-label="记得（3）" title="记得（3）" @click="rateCard('mastered')">
            <Icon name="check" :size="14" /><span>{{ RATING_LABEL.mastered }}</span>
          </button>
          <button type="button" class="action-btn btn-ghost-icon" :disabled="learnIndex >= learnFiltered.length - 1 || reviewing || flipping || !!rateError"
                  aria-label="下一张" title="下一张" @click="nextCard">
            <Icon name="skip-forward" :size="16" />
          </button>
        </div>
      </template>
      </FocusShell>

      <!-- Architecture-4/5：本次复习完成 → 结果 + 奖励 + 学习状态 + 下一步（§77/§85/§138） -->
      <!-- §二十：学习完成页 —— 真实自评分布 + 真实 Recall 表现 + 下一步（复用 NextAction Resolver） -->
      <LearningResultPanel
        v-if="learnSessionDone"
        :result="`本次学习完成 · ${sessionSummary.total} 张卡片`"
        :reward="sessionReward"
        :mastery-updated="false"
        :next-action="sessionNextAction"
        class="mt-4"
      >
        <template #result>
          <div class="session-summary">
            <div v-if="isFsrs" class="ss-stats"><span v-for="(label, index) in fsrsLabels" :key="label" class="ss-item">{{ label }} <b>{{ fsrsRatings.filter(rating => rating === index + 1).length }}</b></span></div>
            <div v-else class="ss-stats">
              <span class="ss-item ss-mastered">记得 <b class="tabular-nums">{{ sessionSummary.mastered }}</b></span>
              <span class="ss-item ss-familiar">模糊 <b class="tabular-nums">{{ sessionSummary.familiar }}</b></span>
              <span class="ss-item ss-unknown">不会 <b class="tabular-nums">{{ sessionSummary.unknown }}</b></span>
            </div>
            <p v-if="!isFsrs && sessionSummary.selfRatedScore != null" class="ss-line">
              本次自评表现：<b class="tabular-nums">{{ sessionSummary.selfRatedScore }}</b> / 100
              <span class="ss-note">（自评换算，不是掌握度）</span>
            </p>
            <p v-if="recallPerformance" class="ss-line">
              记忆保持（Recall）：<b class="tabular-nums">{{ recallPerformance.score }}</b> / 100
              <span class="ss-note">（基于 {{ recallPerformance.sampleCount }} 条真实证据）</span>
            </p>
            <div class="ss-actions">
              <button type="button" class="btn-primary" @click="restartSession">
                <Icon name="rotate-ccw" :size="14" /><span>继续学习</span>
              </button>
              <button type="button" class="btn-outline" @click="router.push('/learning/mastery')">
                <Icon name="target" :size="14" /><span>查看薄弱点</span>
              </button>
            </div>
          </div>
        </template>
      </LearningResultPanel>

      <transition name="fade">
        <p v-if="feedback" class="feedback-text" aria-live="polite">{{ feedback }}</p>
      </transition>
    </section>

    <!-- ===== Tab 2: 推荐复习（基于错题本 source=flashcard 未掌握项） ===== -->
    <section v-show="activeTab === 'review'" class="tab-panel">
      <!-- 推荐说明条 -->
      <div class="review-intro">
        <Icon name="sparkles" :size="16" class="intro-icon" />
        <div class="intro-text">
          <p class="intro-title">智能推荐复习</p>
          <p class="intro-desc">根据你的学习历史，从错题本中筛选出未完全掌握的闪卡，优先推荐复习。</p>
        </div>
        <button type="button" class="btn-ghost" @click="loadReviewCards" title="刷新推荐">
          <Icon name="refresh-cw" :size="14" :class="{ spinning: reviewLoading }" />
        </button>
      </div>

      <!-- 加载态 -->
      <div v-if="reviewLoading" class="state-area">
        <div class="loading-spinner"></div>
        <p class="state-text">生成推荐中…</p>
      </div>

      <!-- 空态 -->
      <div v-else-if="reviewFiltered.length === 0" class="state-area">
        <Icon name="check-circle" :size="48" class="state-icon" />
        <p class="state-text">{{ globalKeyword ? '没有匹配的推荐卡片' : '暂无待复习卡片' }}</p>
        <p class="state-hint">{{ globalKeyword ? '试试清除搜索条件' : '去「学习闪卡」练习，未掌握的卡片会自动进入推荐' }}</p>
        <div v-if="!globalKeyword" class="empty-actions">
          <button type="button" class="btn-primary" @click="activeTab = 'learn'">
            <Icon name="graduation-cap" :size="14" /><span>去学习</span>
          </button>
        </div>
      </div>

      <!-- 推荐卡片堆：翻转 + 标记掌握 -->
      <template v-else>
        <div class="progress-block">
          <div class="progress-left">
            <div class="progress-meta">
              <span class="progress-label">复习进度</span>
              <span class="progress-value tabular-nums">{{ reviewIndex + 1 }} / {{ reviewFiltered.length }}</span>
            </div>
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: `${reviewProgress}%` }"></div>
            </div>
          </div>
          <div class="progress-right">
            <div class="stat-cell"><div class="stat-num stat-known tabular-nums">{{ reviewMasteredCount }}</div><div class="kf-caption">已掌握</div></div>
            <div class="stat-cell"><div class="stat-num stat-review tabular-nums">{{ reviewFiltered.length - reviewIndex - 1 }}</div><div class="kf-caption">剩余</div></div>
          </div>
        </div>

        <div class="flip-area">
          <div
            class="flip-card"
            :class="{ flipped: isReviewFlipped }"
            role="button"
            tabindex="0"
            aria-label="翻转卡片查看答案"
            @click="flipReviewCard"
            @keydown.enter.prevent="($event.currentTarget as HTMLElement).click()"
            @keydown.space.prevent="($event.currentTarget as HTMLElement).click()"
          >
            <div class="flip-card-inner">
              <div class="flip-card-face flip-card-front">
                <div class="face-tags">
                  <span class="face-tag tag-category">{{ currentReviewCard?.category || '通用' }}</span>
                  <span class="face-tag tag-sub">{{ getDifficultyLabel(currentReviewCard?.difficulty) }}</span>
                  <span class="face-tag tag-source">来自错题本</span>
                </div>
                <h2 class="face-question">{{ currentReviewCard?.question }}</h2>
                <p class="face-hint">请点击卡片查看答案</p>
                <div class="flip-pill"><Icon name="rotate-ccw" :size="14" /><span>点击翻转</span></div>
              </div>
              <div class="flip-card-face flip-card-back">
                <div class="back-header">
                  <Icon name="lightbulb" :size="16" class="back-icon" />
                  <span class="back-label">答案</span>
                </div>
                <div class="back-body">
                  <h3 class="back-title">{{ currentReviewCard?.question }}</h3>
                  <p class="back-text">{{ currentReviewCard?.correctAnswer || currentReviewCard?.wrongAnswer || '暂无答案' }}</p>
                  <div v-if="currentReviewCard?.category" class="back-tags">
                    <span class="back-tag">{{ currentReviewCard.category }}</span>
                    <span class="back-tag">复习 {{ currentReviewCard.reviewCount || 0 }} 次</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="action-bar">
          <button type="button" class="action-btn btn-outline" :disabled="reviewIndex === 0" @click="prevReviewCard">
            <Icon name="skip-back" :size="14" /><span>上一张</span>
          </button>
          <button type="button" class="action-btn btn-warning" @click="keepReviewing">
            <Icon name="rotate-ccw" :size="14" /><span>继续复习</span>
          </button>
          <button type="button" class="action-btn btn-success" :disabled="markingMastered" @click="markReviewMastered">
            <Icon name="check" :size="14" /><span>已掌握</span>
          </button>
          <button type="button" class="action-btn btn-outline" :disabled="reviewIndex >= reviewFiltered.length - 1" @click="nextReviewCard">
            <span>下一张</span><Icon name="skip-forward" :size="14" />
          </button>
        </div>

        <transition name="fade">
          <p v-if="reviewFeedback" class="feedback-text">{{ reviewFeedback }}</p>
        </transition>
      </template>
    </section>

    <!-- ===== Tab 3: 我的闪卡 ===== -->
    <section v-show="activeTab === 'mine'" class="tab-panel">
      <!-- 工具栏 -->
      <div class="toolbar-card">
        <div class="filter-row">
          <Select class="select-inline" v-model="filterDifficultySelect" @update:modelValue="applyFilters" :options="[{ value: null, label: '全部难度' }, { value: 1, label: '简单' }, { value: 2, label: '中等' }, { value: 3, label: '困难' }]" aria-label="筛选闪卡难度" />
          <Select class="select-inline" v-model="filters.sourceType" @update:modelValue="applyFilters" :options="[{ value: '', label: '全部来源' }, { value: 'MANUAL', label: '手动创建' }, { value: 'AI_DOC', label: 'AI · 文档' }, { value: 'AI_KB', label: 'AI · 知识库' }, { value: 'IMPORT', label: '批量导入' }]" aria-label="筛选闪卡来源" />
          <Select class="select-inline" v-model="filterCategorySelect" @update:modelValue="applyFilters" :options="[{ value: null, label: '全部知识库' }, ...categories2.map(c => ({ value: c.id, label: c.name }))]" aria-label="筛选闪卡知识库" />
        </div>
        <div class="action-row">
          <div class="action-left">
            <button type="button" class="btn-primary" @click="openCreate()">
              <Icon name="plus" :size="14" /><span>新增闪卡</span>
            </button>
            <button type="button" class="btn-secondary" @click="openGenerate">
              <Icon name="sparkles" :size="14" /><span>AI 生成</span>
            </button>
            <button type="button" class="btn-secondary" @click="exchangeMode = 'import'">
              <Icon name="upload" :size="14" /><span>导入</span>
            </button>
            <button type="button" class="btn-secondary" :disabled="mineCards.length === 0" @click="exchangeMode = 'export'">
              <Icon name="download" :size="14" /><span>导出</span>
            </button>
            <button type="button" class="btn-ghost" @click="openImport">JSON 兼容导入</button>
            <button type="button" class="btn-ghost" :disabled="mineCards.length === 0" @click="doExport">JSON 导出</button>
            <button
              v-if="viewMode === 'list'"
              type="button"
              class="btn-secondary danger"
              :disabled="selectedIds.length === 0"
              @click="batchDelete"
            >
              <Icon name="trash-2" :size="14" /><span>删除选中</span>
              <span v-if="selectedIds.length" class="badge">{{ selectedIds.length }}</span>
            </button>
          </div>
          <div class="action-right">
            <div class="view-switch" role="tablist" aria-label="视图切换">
              <button type="button" class="view-btn" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'">
                <Icon name="list" :size="14" /><span>列表</span>
              </button>
              <button type="button" class="view-btn" :class="{ active: viewMode === 'card' }" @click="viewMode = 'card'">
                <Icon name="grid" :size="14" /><span>卡片</span>
              </button>
            </div>
            <button type="button" class="btn-ghost" @click="reloadMine()" title="刷新">
              <Icon name="refresh-cw" :size="14" :class="{ spinning: mineLoading }" />
            </button>
          </div>
        </div>
      </div>

      <!-- 加载态 -->
      <div v-if="mineLoading" class="state-area">
        <div class="loading-spinner"></div>
        <p class="state-text">加载中…</p>
      </div>

      <!-- 空态 -->
      <template v-else-if="mineCards.length === 0">
        <div class="state-area empty-card">
          <Icon name="layers" :size="48" class="state-icon" />
          <p class="state-text">{{ hasMineFilter ? '当前筛选条件下没有闪卡' : '还没有闪卡' }}</p>
          <p class="state-hint">{{ hasMineFilter ? '试试调整难度、来源、知识库筛选或清除搜索关键词。' : '手动创建一张，或选择知识库/文档让 AI 一键生成。' }}</p>
          <div class="empty-actions">
            <template v-if="hasMineFilter">
              <button type="button" class="btn-secondary" @click="resetMineFilters">
                <Icon name="refresh-cw" :size="14" /><span>重置筛选</span>
              </button>
            </template>
            <template v-else>
              <button type="button" class="btn-primary" @click="openCreate">
                <Icon name="plus" :size="14" /><span>新增闪卡</span>
              </button>
              <button type="button" class="btn-secondary" @click="openGenerate">
                <Icon name="sparkles" :size="14" /><span>AI 生成</span>
              </button>
            </template>
          </div>
        </div>
      </template>

      <template v-else>
        <div ref="mineContentRef" class="mine-content">
          <!-- 列表视图：表格自身横向滚动，页面不产生水平滚动 -->
          <div v-if="viewMode === 'list'" class="list-wrap">
            <table class="kf-table">
              <colgroup>
                <col style="width: 44px" />
                <col style="width: 56px" />
                <col style="width: 244px" />
                <col style="width: 260px" />
                <col style="width: 80px" />
                <col style="width: 116px" />
                <col style="width: 140px" />
                <col style="width: 204px" />
              </colgroup>
              <thead>
                <tr>
                  <th scope="col">
                    <Checkbox  :checked="allSelected" :indeterminate="someSelected" aria-label="选择当前页全部闪卡" @native-change="toggleAll" />
                  </th>
                  <th scope="col">#</th>
                  <th scope="col">正面（问题）</th>
                  <th scope="col">背面（答案）</th>
                  <th scope="col">难度</th>
                  <th scope="col">来源</th>
                  <th scope="col">更新时间</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(c, idx) in mineCards" :key="c.id">
                  <td><Checkbox  :checked="selectedIds.includes(c.id)" :aria-label="`选择闪卡 ${c.front}`" @native-change="toggleId(c.id)" /></td>
                  <td class="index-cell tabular-nums text-muted">{{ (pageNum - 1) * pageSize + idx + 1 }}</td>
                  <td>
                    <button type="button" class="cell-button" title="查看完整问题" aria-label="查看完整问题" @click="openDetail(c)">
                      <span v-if="c.tags" class="mini-tags">
                        <span v-for="t in splitTags(c.tags).slice(0, 2)" :key="t" class="mini-tag">#{{ t }}</span>
                      </span>
                      <span class="cell-text cell-front">{{ c.front }}</span>
                    </button>
                  </td>
                  <td>
                    <button type="button" class="cell-button" title="查看完整答案" aria-label="查看完整答案" @click="openDetail(c)">
                      <span class="cell-text cell-back">{{ c.back }}</span>
                    </button>
                  </td>
                  <td><span class="diff-badge" :class="diffClass(c.difficulty)">{{ diffLabel(c.difficulty) }}</span></td>
                  <td>
                    <span class="source-badge" :class="sourceClass(c.sourceType)">
                      <Icon :name="sourceIcon(c.sourceType)" :size="13" />{{ sourceLabel(c.sourceType) }}
                    </span>
                  </td>
                  <td class="time-cell text-muted tabular-nums">{{ formatTime(c.updateTime || c.createTime) }}</td>
                  <td>
                    <div class="row-actions">
                      <button type="button" class="row-btn" aria-label="查看答案" @click="openDetail(c)">
                        <Icon name="eye" :size="15" /><span>查看答案</span>
                      </button>
                      <button type="button" class="row-btn icon-only" aria-label="编辑" title="编辑" @click="openEdit(c)">
                        <Icon name="edit" :size="15" />
                      </button>
                      <button type="button" class="row-btn icon-only danger" aria-label="删除" title="删除" @click="removeOne(c)">
                        <Icon name="trash-2" :size="15" />
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 卡片视图 -->
          <div v-else class="card-grid">
            <article
              v-for="c in mineCards"
              :key="c.id"
              class="fc-card"
              :class="{ flipped: flippedId === c.id }"
              @click.self="toggleFlip(c.id)"
            >
              <div class="fc-card-inner">
                <section class="fc-face fc-front" :aria-hidden="flippedId === c.id" :inert="flippedId === c.id">
                  <header class="fc-face-top">
                    <div class="fc-badges">
                      <span class="diff-badge" :class="diffClass(c.difficulty)">{{ diffLabel(c.difficulty) }}</span>
                      <span class="source-badge" :class="sourceClass(c.sourceType)">
                        <Icon :name="sourceIcon(c.sourceType)" :size="13" />{{ sourceLabel(c.sourceType) }}
                      </span>
                    </div>
                    <div class="fc-top-actions">
                      <button type="button" class="icon-btn" :aria-label="`编辑 ${c.front}`" title="编辑" @click.stop="openEdit(c)">
                        <Icon name="edit" :size="15" />
                      </button>
                      <button type="button" class="icon-btn danger" :aria-label="`删除 ${c.front}`" title="删除" @click.stop="removeOne(c)">
                        <Icon name="trash-2" :size="15" />
                      </button>
                    </div>
                  </header>
                  <button type="button" class="fc-content fc-front-title" title="查看完整内容" @click.stop="openDetail(c)">
                    {{ c.front }}
                  </button>
                  <footer class="fc-face-bottom">
                    <div class="fc-tags">
                      <span v-for="t in splitTags(c.tags || '')" :key="t" class="fc-tag">#{{ t }}</span>
                    </div>
                    <button type="button" class="fc-flip-btn" @click.stop="toggleFlip(c.id)">
                      <Icon name="eye" :size="14" />查看答案
                    </button>
                  </footer>
                </section>
                <section class="fc-face fc-back" :aria-hidden="flippedId !== c.id" :inert="flippedId !== c.id">
                  <header class="fc-face-top">
                    <div class="fc-badges">
                      <span v-if="c.categoryName" class="kf-chip"><Icon name="bookmark" :size="12" />{{ c.categoryName }}</span>
                      <span v-else class="diff-badge" :class="diffClass(c.difficulty)">{{ diffLabel(c.difficulty) }}</span>
                    </div>
                    <div class="fc-top-actions">
                      <button type="button" class="icon-btn" :aria-label="`编辑 ${c.front}`" title="编辑" @click.stop="openEdit(c)">
                        <Icon name="edit" :size="15" />
                      </button>
                      <button type="button" class="icon-btn danger" :aria-label="`删除 ${c.front}`" title="删除" @click.stop="removeOne(c)">
                        <Icon name="trash-2" :size="15" />
                      </button>
                    </div>
                  </header>
                  <div class="fc-back-scroll">
                    <pre v-if="looksLikeCode(c.back)" class="fc-code">{{ c.back }}</pre>
                    <p v-else class="fc-back-content">{{ c.back }}</p>
                  </div>
                  <footer class="fc-face-bottom">
                    <div class="fc-meta">
                      <Icon name="calendar" :size="13" /><span>更新于 {{ formatTime(c.updateTime || c.createTime) }}</span>
                    </div>
                    <button type="button" class="fc-flip-btn" @click.stop="toggleFlip(c.id)">
                      <Icon name="rotate-ccw" :size="14" />返回正面
                    </button>
                  </footer>
                </section>
              </div>
            </article>
          </div>
        </div>

        <!-- 分页：列表与卡片共享同一数据模型 -->
        <div v-if="mineTotal > 0" class="mine-pagination" aria-label="闪卡分页">
          <div class="page-meta">
            <span>共 <b class="tabular-nums">{{ mineTotal }}</b> 张卡片</span>
            <span class="page-range tabular-nums">显示 {{ pageStart }}–{{ pageEnd }} / {{ mineTotal }} 条</span>
            <span v-if="selectedIds.length" class="selection-hint">已选 {{ selectedIds.length }} 张（当前页）</span>
          </div>
          <div class="page-controls">
            <label class="page-size-label">
              每页
              <Select class="select-inline page-size-select" :model-value="pageSize" :options="pageSizeOptions" aria-label="每页数量" @update:modelValue="changePageSize" />
            </label>
            <Pagination :page-num="pageNum" :page-size="pageSize" :total="mineTotal" :show-info="false" @change="changePage" />
          </div>
        </div>
      </template>

      <!-- 完整内容查看（列表摘要与卡片正文共用） -->
      <div v-if="detailCard" class="modal-mask" @click.self="detailCard = null">
        <div class="modal modal-lg detail-modal" role="dialog" aria-modal="true" aria-labelledby="flashcard-detail-title">
          <header class="modal-header">
            <h3 id="flashcard-detail-title">闪卡详情</h3>
            <button type="button" class="icon-btn" aria-label="关闭" @click="detailCard = null"><Icon name="x" :size="16" /></button>
          </header>
          <div class="modal-body detail-body">
            <section class="detail-block">
              <div class="detail-head">
                <span>正面 · 问题</span>
                <button type="button" class="detail-copy" @click="copyText(detailCard.front)"><Icon name="copy" :size="14" />复制</button>
              </div>
              <pre v-if="looksLikeCode(detailCard.front)" class="detail-text detail-code">{{ detailCard.front }}</pre>
              <div v-else class="detail-text markdown-body" v-html="renderCard(detailCard.front)"></div>
            </section>
            <section class="detail-block">
              <div class="detail-head">
                <span>背面 · 答案</span>
                <button type="button" class="detail-copy" @click="copyText(detailCard.back)"><Icon name="copy" :size="14" />复制</button>
              </div>
              <pre v-if="looksLikeCode(detailCard.back)" class="detail-text detail-code">{{ detailCard.back }}</pre>
              <div v-else class="detail-text markdown-body" v-html="renderCard(detailCard.back)"></div>
            </section>
          </div>
          <footer class="modal-footer detail-footer">
            <div class="detail-tags">
              <span v-if="detailCard.categoryName" class="kf-chip"><Icon name="bookmark" :size="12" />{{ detailCard.categoryName }}</span>
              <span v-for="t in splitTags(detailCard.tags || '')" :key="t" class="fc-tag">#{{ t }}</span>
            </div>
            <div class="detail-actions">
              <button type="button" class="btn-secondary" @click="editFromDetail"><Icon name="edit" :size="14" /><span>编辑</span></button>
              <button type="button" class="btn-primary" @click="detailCard = null">关闭</button>
            </div>
          </footer>
        </div>
      </div>

      <!-- ===== 新增 / 编辑弹窗 ===== -->
      <div v-if="formDialog.visible" class="modal-mask" @click.self="closeForm">
        <div class="modal modal-lg">
          <header class="modal-header">
            <h3>{{ formDialog.mode === 'edit' ? '编辑闪卡' : '新增闪卡' }}</h3>
            <button type="button" class="icon-btn" @click="closeForm" title="关闭"><Icon name="x" :size="16" /></button>
          </header>
          <div class="modal-body form-grid">
            <div class="form-item">
              <label class="form-label" for="flashcard-note-type">卡片类型</label>
              <Select id="flashcard-note-type" v-model="formData.type" :disabled="formDialog.mode === 'edit' && !noteId" :options="[{ value: 'BASIC', label: '基础问答' }, { value: 'CLOZE', label: 'Cloze 挖空' }, { value: 'CODE_CLOZE', label: '代码补全' }, { value: 'IMAGE', label: '图文' }, { value: 'SEQUENCE', label: '流程步骤' }, { value: 'SCENARIO', label: '场景追问' }]" aria-label="闪卡类型" />
            </div>
            <div v-if="formData.type === 'CODE_CLOZE'" class="form-item">
              <label class="form-label" for="flashcard-code-language">代码语言</label>
              <Select id="flashcard-code-language" v-model="formData.language" :options="['java','sql','python','javascript','typescript','text'].map(lang => ({ value: lang, label: lang }))" aria-label="代码语言" />
            </div>
            <p v-if="formData.type.endsWith('CLOZE')" class="form-hint col-span-2">使用 <code v-text="clozeExample"></code>，不同编号生成独立复习卡。同编号同步揭晓。代码补全直接填写源码。</p>
            <p v-if="formData.type === 'IMAGE'" class="form-hint col-span-2">使用 Markdown 图片语法引用已有授权附件，例如 ![说明](/api/…)，图片权限沿用原附件接口。</p>
            <div class="form-item col-span-2">
              <label class="form-label">正面 · 问题 / 概念 <span class="req">*</span></label>
              <textarea v-model="formData.front" rows="3" placeholder="例如：什么是闭包（Closure）？" class="kf-textarea kf-form-control" />
            </div>
            <div class="form-item col-span-2">
              <label class="form-label">背面 · 答案 / 解释 <span class="req">*</span></label>
              <textarea v-model="formData.back" rows="4" placeholder="用要点化的方式给出解释，便于记忆。" class="kf-textarea kf-form-control" />
            </div>
            <div class="form-item">
              <label class="form-label">难度</label>
              <Select v-model="formData.difficulty" :options="[{ value: 1, label: '简单' }, { value: 2, label: '中等' }, { value: 3, label: '困难' }]" aria-label="闪卡难度" />
            </div>
            <div class="form-item">
              <label class="form-label">自定义分类</label>
              <input v-model="formData.category" type="text" class="kf-input kf-form-control" placeholder="如：前端基础" />
            </div>
            <div class="form-item">
              <label class="form-label">关联知识库</label>
              <Select v-model="formCategorySelect" :disabled="formData.type !== 'BASIC' || !!noteId" :options="[{ value: null, label: '不关联' }, ...categories2.map(c => ({ value: c.id, label: c.name }))]" aria-label="关联知识库" />
            </div>
            <div class="form-item">
              <label class="form-label">关联文档</label>
              <Select v-model="formDocSelect" :disabled="!formData.categoryId || formData.type !== 'BASIC' || !!noteId" :options="[{ value: null, label: '不关联' }, ...dialogDocs.map(d => ({ value: d.id, label: d.title }))]" aria-label="关联文档" />
            </div>
            <div class="form-item col-span-2">
              <label class="form-label">标签（逗号分隔）</label>
              <input v-model="formData.tags" type="text" class="kf-input kf-form-control" placeholder="例如：闭包,作用域,面试题" />
            </div>
          </div>
          <div v-if="notePreview.length" class="preview-wrap" aria-label="闪卡编辑预览">
            <article v-for="item in notePreview" :key="item.ordinal" class="preview-card">
              <b>复习卡 {{ item.ordinal || 1 }}</b><div v-html="renderCard(item.front)"></div><hr><div v-html="renderCard(item.back)"></div>
            </article>
          </div>
          <footer class="modal-footer">
            <button type="button" class="btn-outline" :disabled="notePreviewBusy" @click="previewNote">{{ notePreviewBusy ? '预览中…' : '预览卡片' }}</button>
            <button type="button" class="btn-ghost" @click="closeForm">取消</button>
            <button type="button" class="btn-primary" :disabled="submitting" @click="submitForm">
              <Icon name="check" :size="14" /><span>{{ submitting ? '保存中…' : '保存' }}</span>
            </button>
          </footer>
        </div>
      </div>

      <!-- ===== AI 生成弹窗 ===== -->
      <div v-if="genDialog.visible" class="modal-mask" @click.self="closeGenerate">
        <div class="modal modal-lg">
          <header class="modal-header">
            <h3><Icon name="sparkles" :size="16" /> AI 批量生成闪卡</h3>
            <button type="button" class="icon-btn" @click="closeGenerate" title="关闭"><Icon name="x" :size="16" /></button>
          </header>
          <div class="modal-body form-grid">
            <div class="form-item">
              <label class="form-label">生成来源 <span class="req">*</span></label>
              <div class="seg-group">
                <button type="button" class="seg-btn" :class="{ active: genDialog.source === 'kb' }" @click="genDialog.source = 'kb'; genDialog.docId = undefined">知识库</button>
                <button type="button" class="seg-btn" :class="{ active: genDialog.source === 'doc' }" @click="genDialog.source = 'doc'">文档</button>
              </div>
            </div>
            <div class="form-item">
              <label class="form-label">偏好难度</label>
              <Select v-model="genDialog.difficultyPreference" :options="[{ value: 0, label: '均衡' }, { value: 1, label: '偏简单' }, { value: 2, label: '偏中等' }, { value: 3, label: '偏困难' }]" aria-label="生成难度偏好" />
            </div>
            <div v-if="genDialog.source === 'kb'" class="form-item col-span-2">
              <label class="form-label">选择知识库 <span class="req">*</span></label>
              <Select v-model="generationCategorySelect" :options="[{ value: null, label: '请选择知识库' }, ...categories2.map(c => ({ value: c.id, label: c.name }))]" aria-label="生成知识库" />
              <p class="form-hint">将聚合该知识库下已发布的文档（最多 15 篇），基于整体内容生成综合闪卡。</p>
            </div>
            <div v-if="genDialog.source === 'doc'" class="form-item">
              <label class="form-label">选择知识库</label>
              <Select v-model="generationCategorySelect" @update:modelValue="genDialog.docId = undefined" :options="[{ value: null, label: '全部' }, ...categories2.map(c => ({ value: c.id, label: c.name }))]" aria-label="生成知识库" />
            </div>
            <div v-if="genDialog.source === 'doc'" class="form-item">
              <label class="form-label">选择文档 <span class="req">*</span></label>
              <Select v-model="generationDocSelect" :options="[{ value: null, label: '请选择文档' }, ...dialogDocs.map(d => ({ value: d.id, label: truncate(d.title, 36) }))]" aria-label="生成文档" />
            </div>
            <div class="form-item">
              <label class="form-label">生成数量</label>
              <div class="count-stepper">
                <button type="button" class="stepper-btn" :disabled="genDialog.count <= 3" @click="genDialog.count -= 1">−</button>
                <input v-model.number="genDialog.count" type="number" min="1" max="30" class="stepper-input kf-input kf-form-control" />
                <button type="button" class="stepper-btn" :disabled="genDialog.count >= 30" @click="genDialog.count += 1">+</button>
                <span class="stepper-hint">（1-30 张）</span>
              </div>
            </div>
            <div v-if="previewList.length" class="form-item col-span-2">
              <label class="form-label">已生成预览（{{ previewList.length }} 张）</label>
              <div class="preview-wrap">
                <div v-for="(p, i) in previewList" :key="i" class="preview-card">
                  <div class="preview-index">Q{{ i + 1 }}</div>
                  <div class="preview-front"><b>Q：</b>{{ p.front }}</div>
                  <div class="preview-back"><b>A：</b>{{ p.back }}</div>
                </div>
              </div>
            </div>
          </div>
          <footer class="modal-footer">
            <button type="button" class="btn-ghost" @click="closeGenerate">取消</button>
            <button type="button" class="btn-primary" :disabled="generating || !genReady" @click="doGenerate">
              <Icon name="sparkles" :size="14" /><span>{{ generating ? 'AI 生成中，请稍候…' : '开始生成并保存' }}</span>
            </button>
          </footer>
        </div>
      </div>

      <!-- ===== 导入弹窗 ===== -->
      <div v-if="importDialog.visible" class="modal-mask" @click.self="closeImport">
        <div class="modal modal-lg">
          <header class="modal-header">
            <h3><Icon name="upload" :size="16" /> 导入闪卡</h3>
            <button type="button" class="icon-btn" @click="closeImport" title="关闭"><Icon name="x" :size="16" /></button>
          </header>
          <div class="modal-body">
            <div class="import-hint">
              <p><b>支持 JSON 数组格式</b>，每张卡必填 <code>front</code>（正面）和 <code>back</code>（背面）；可选字段：<code>difficulty</code>(1/2/3)、<code>category</code>、<code>tags</code>（逗号分隔）。</p>
              <p><b>示例：</b></p>
<pre class="sample-block">[
  { "front": "Q1", "back": "A1", "difficulty": 2, "tags": "标签1,标签2" },
  { "front": "Q2", "back": "A2", "category": "前端" }
]</pre>
            </div>
            <textarea v-model="importDialog.jsonText" rows="14" class="kf-textarea kf-form-control" placeholder="粘贴 JSON 数组…"></textarea>
            <p v-if="importDialog.parseError" class="parse-error">
              <Icon name="alert-circle" :size="14" /> {{ importDialog.parseError }}
            </p>
          </div>
          <footer class="modal-footer">
            <button type="button" class="btn-ghost" @click="closeImport">取消</button>
            <button type="button" class="btn-primary" :disabled="importing" @click="doImport">
              <Icon name="upload" :size="14" /><span>{{ importing ? '导入中…' : '确认导入' }}</span>
            </button>
          </footer>
        </div>
      </div>
    </section>
  </div>
</MiniAppShell>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { renderMarkdown } from '@/utils/markdown'
import { searchSummary } from '@/utils/searchText'
import { useSanitize } from '@/composables/useSanitize'
import FlashcardSettingsDialog from '@/components/flashcards/FlashcardSettingsDialog.vue'
import FlashcardReviewIcon from '@/components/flashcards/FlashcardReviewIcon.vue'
import FlashcardExchangeDialog from '@/components/flashcards/FlashcardExchangeDialog.vue'
import ChapterResourceLinks from '@/components/Learning/ChapterResourceLinks.vue'
/**
 * 闪卡大厅（FlashCardsHub）
 * 整合「学习闪卡 / 推荐复习 / 我的闪卡」三个功能模块：
 * - 学习闪卡 Tab：调用 learningApi.flashcards() 拉取系统闪卡，支持翻转/三档评分/进度统计
 * - 推荐复习 Tab：调用 mistakesApi.list({ mastered: 0 }) 取未掌握错题，前端过滤 source=flashcard，
 *   支持翻转 / 标记掌握（同步调 mistakesApi.markMastered）
 * - 我的闪卡 Tab：调用 learningApi.myFlashcards() 等 CRUD 接口，支持增删改查/导入导出/AI 生成
 * - 顶部统一搜索框：按当前 Tab 过滤（学习/推荐前端过滤、我的侧同步后端参数）
 * 设计遵循项目 --kf-* CSS 变量体系与组件规范，Toast/确认弹窗统一走 @/utils/toast。
 */
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
import Pagination from '@/components/ui/Pagination.vue'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import FocusShell from '@/components/layout/FocusShell.vue'
import LearningResultPanel from '@/components/workflow/LearningResultPanel.vue'
import { useLearningStore } from '@/stores/learning'
import { useWalletStore } from '@/stores/wallet'
import { learningApi } from '@/api/learning'
import { categoriesApi } from '@/api/categories'
import { docsApi } from '@/api'
import { mistakesApi } from '@/api/mistakes'
import type { FlashcardVO, FlashcardInput, FlashcardNoteInput, FlashcardSchedulePreview, FlashcardReviewSettings, CategoryVO, DocVO, MistakeVO } from '@/api/types'
import { markReviewed, dateStr } from '@/utils/studySession'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
// Interaction OS（UI/UX 5.1）§六：统一错误态文案（禁止把 err.message / status code 渲染给用户）
import { describePageError } from '@/utils/error-message'
// §十九：退出/离开确认使用项目统一 Dialog（禁浏览器原生 confirm，按钮文案可定制）
import { dialog } from '@/utils/dialog'
import { setLastCompleted } from '@/learning/action-priority'
import type { NextLearningAction } from '@/learning/types'
import {
  KEYBOARD_HINT,
  RATING_LABEL,
  createSessionQueue,
  dueCount,
  isSessionComplete,
  isSessionInProgress,
  keyToRating,
  ratingToQuality,
  resolveEventKnowledgeId,
  summarizeSession,
  type FlashcardRating,
  type QueueContext,
  type SessionCard,
  type SessionSummary,
} from '@/learning/flashcard-session'

const router = useRouter()
const route = useRoute()

// Architecture-5 §229 / Architecture-4.2 §12：Mini App 上下文
// knowledgeId/knowledgeName 来自 NextAction（Mini App 深链），chapterId 来自
// NextAction/Strategy 的章节兼容方式（appendChapterQuery → /learning/flashcards?chapterId=N）。
const knowledgeContext = ref<{ id: number; name: string } | null>(null)
/** 深链指定的学习节点章节（真实后端过滤依据：GET /learning/flashcards?chapterId=N） */
const routeChapterId = ref<number | null>(null)
/** 深链指定的学习路径（真实后端过滤依据：GET /learning/flashcards?pathId=N） */
const routePathId = ref<number | null>(null)
{
  const kid = route.query.knowledgeId
  const kname = route.query.knowledgeName
  const cid = route.query.chapterId
  const pid = route.query.pathId
  if (typeof kid === 'string' && kid) {
    knowledgeContext.value = {
      id: Number(kid),
      name: typeof kname === 'string' && kname ? kname : '该知识点',
    }
  }
  const cidNum = typeof cid === 'string' ? Number(cid) : NaN
  if (Number.isFinite(cidNum) && cidNum > 0) routeChapterId.value = cidNum
  const pidNum = typeof pid === 'string' ? Number(pid) : NaN
  if (Number.isFinite(pidNum) && pidNum > 0) routePathId.value = pidNum
}

// ===================== Tab 切换 =====================
/** UI/UX 5.0 §九：搜索降为二级入口，默认收起 */
const searchOpen = ref(false)

/**
 * UI/UX 5.1 §八：支持 `?tab=` 深链。
 * 其他页面（如 Quiz 空态的「去添加闪卡」）可以把用户直接送到「我的闪卡」创建入口，
 * 而不是丢到默认的学习 Tab 让用户自己再找一次。
 */
function resolveInitialTab(): 'learn' | 'review' | 'mine' {
  const t = route.query.tab
  return t === 'review' || t === 'mine' || t === 'learn' ? t : 'learn'
}
const activeTab = ref<'learn' | 'review' | 'mine'>(resolveInitialTab())

// ===================== 全局搜索 =====================
const globalKeyword = ref('')
const searchPlaceholder = computed(() => {
  if (activeTab.value === 'learn') return '在系统闪卡中搜索问题/答案…'
  if (activeTab.value === 'review') return '在推荐复习中搜索问题/答案…'
  return '在个人闪卡中搜索正面/背面/标签…'
})
const onGlobalSearch = () => {
  // 我的侧需同步后端参数；学习/推荐侧为 computed 前端过滤
  if (activeTab.value === 'mine') reloadMine(true)
}
const clearSearch = () => {
  globalKeyword.value = ''
  if (activeTab.value === 'mine') reloadMine(true)
}

// ===================== 学习闪卡 State =====================
// UI/UX 5.0+：不再在本页维护一套私有闪卡结构，统一使用 learning/flashcard-session 的
// SessionCard（保留 chapterId/pathId/nextReviewTime/reviewCount 等真实字段用于队列排序）。
type Rating = FlashcardRating

const learnLoading = ref(false)
/** 加载失败的**原始异常**（Interaction OS §六：不再把 err.message 直接渲染给用户） */
const learnLoadError = ref<unknown>(null)
const reviewing = ref(false)
const algorithmMode = ref<'fsrs' | 'legacy'>('fsrs')
const isFsrs = computed(() => algorithmMode.value === 'fsrs')
const dueOnly = ref(false)
const sessionQueueIds = ref<number[] | null>(null)
const schedulePreview = ref<FlashcardSchedulePreview | null>(null)
const scheduleLoading = ref(false)
const scheduleError = ref('')
const fsrsRatings = ref<number[]>([])
const fsrsLabels = ['重来', '困难', '记得', '简单'] as const
const fsrsNames = ['Again', 'Hard', 'Good', 'Easy'] as const
const ratingIconNames = ['again', 'hard', 'good', 'easy'] as const
const submittingRating = ref<number | null>(null)
const fsrsSummaryRating = (rating: number): Rating => rating === 1 ? 'unknown' : rating === 2 ? 'familiar' : 'mastered'
const sessionId = ref<string>(crypto.randomUUID())
const sessionResumed = ref(false)
const pendingFsrs = ref<{ cardId: number; rating: number; revision: number; settingsRevision: number; requestId: string } | null>(null)
const lastDueAt = ref('')
const settingsOpen = ref(false)
const settings = ref<FlashcardReviewSettings>({ scheduling: { desiredRetention: .9, maximumInterval: 36500, learningMinutes: [1,10], relearningMinutes: [10] }, newCardLimit: 20, reviewLimit: 200, timezone: 'Asia/Shanghai', dayStartHour: 4 })
const todayStats = ref<{newCards:number;reviews:number;completed:number} | null>(null)
const sessionScope = computed(() => `${routePathId.value ?? ''}:${routeChapterId.value ?? ''}:${knowledgeContext.value?.id ?? ''}`)
const intervalLabel = (seconds?: number) => seconds == null ? '待计算' : seconds < 3600 ? `${Math.max(1, Math.round(seconds / 60))} 分钟` : seconds < 86400 ? `${Math.round(seconds / 3600)} 小时` : `${Math.round(seconds / 86400)} 天`
const dueLabel = (due: string) => new Date(due).toLocaleString('zh-CN', { timeZone: settings.value.timezone })
let scheduleRequest = 0
async function loadSchedule() {
  const id = currentCard.value?.id
  const request = ++scheduleRequest
  schedulePreview.value = null
  scheduleError.value = ''
  if (!id || !inSession.value || !isFsrs.value) { scheduleLoading.value = false; return }
  scheduleLoading.value = true
  try { const result = await learningApi.flashcardSchedule(id); if (request === scheduleRequest) schedulePreview.value = result }
  catch (e) { if (request === scheduleRequest) scheduleError.value = getApiError(e, '无法计算复习间隔，请重试') }
  finally { if (request === scheduleRequest) scheduleLoading.value = false }
}
async function loadStatistics() {
  try { const result = await learningApi.flashcardStatistics(); todayStats.value = result.today; todayLearned.value = result.today.completed }
  catch { todayStats.value = null } // Unavailable statistics are not displayed as zero.
}
function openReviewSettings() { settingsOpen.value = true }
async function reviewSettingsSaved(saved: FlashcardReviewSettings) {
  settings.value = saved
  await loadSchedule()
  await loadStatistics()
}
function saveSessionDraft() {
  if (!isFsrs.value) return
  learning.saveFlashcardSession(sessionScope.value, { sessionId: sessionId.value, queueIds: learnFiltered.value.map(card => card.id), currentId: currentCard.value?.id ?? null, createdAt: Date.now() })
}
async function resumeSession() {
  const draft = learning.readFlashcardSession(sessionScope.value)
  if (!draft) return
  const recorded = await learningApi.flashcardSession(draft.sessionId)
  const byId = new Map(learnCards.value.map(card => [card.id, card]))
  const restored = draft.queueIds.map(id => byId.get(id)).filter((card): card is SessionCard => !!card)
  if (!restored.length) return
  learnCards.value = restored
  sessionQueueIds.value = restored.map(card => card.id)
  sessionId.value = draft.sessionId
  sessionResumed.value = true
  algorithmMode.value = 'fsrs'
  const restoredIds = new Set(restored.map(card => card.id))
  const successful = new Map(recorded.reviews.filter(review => restoredIds.has(review.cardId)).map(review => [review.cardId, review.rating]))
  ratedCardIds.value = [...successful.keys()]
  fsrsRatings.value = [...successful.values()]
  sessionRatings.value = fsrsRatings.value.map(fsrsSummaryRating)
  ratedInSession.value = ratedCardIds.value.length
  learnIndex.value = Math.max(0, learnFiltered.value.findIndex(card => !ratedCardIds.value.includes(card.id)))
  sessionStarted.value = true
  if (isSessionComplete(ratedInSession.value, learnFiltered.value.length)) void finalizeSession()
}

const feedback = ref('')
let feedbackTimer: ReturnType<typeof setTimeout> | undefined

const categories = ref<string[]>(['全部'])
const selectedCategory = ref('全部')
const learnIndex = ref(0)
const isFlipped = ref(false)
const learnCards = ref<SessionCard[]>([])

/** 本次会话已自评序列（真实行为，用于完成页分布统计，不参与任何评分计算） */
const sessionRatings = ref<FlashcardRating[]>([])
const ratedCardIds = ref<number[]>([])
/** 提交失败时保留待重试的评分（§二十三：不静默失败、不跳过该卡） */
const rateError = ref<{ rating: FlashcardRating; fsrsRating?: number; cardId: number; title: string; hint: string } | null>(
  null,
)
const rateErrorElement = ref<HTMLElement | null>(null)
watch(rateError, async error => {
  if (!error) return
  await nextTick()
  rateErrorElement.value?.focus()
})
/** 会话模式：用户点击「开始学习」后进入（UI/UX 5.1 §九 准备态 → 学习态） */
const sessionStarted = ref(false)

/**
 * 统一错误文案（Interaction OS §六）：① 发生了什么 ② 是否影响当前页面 ③ 怎么解决。
 * 原始异常只进 `rawForLog`，禁止渲染。
 */
const learnError = computed(() =>
  learnLoadError.value ? describePageError(learnLoadError.value, { subject: '无法加载闪卡' }) : null,
)

/**
 * §九 准备态：还有卡可学、但本次会话尚未开始。
 * 只有在真实存在可学卡片时才成立 —— 空态/错误态优先，不制造无内容的「准备页」。
 */
const showPreparation = computed(
  () => !learnSessionDone.value && !sessionStarted.value && learnFiltered.value.length > 0,
)

/** §九 准备态「来源」文案：只描述真实来源（深链知识点 / 章节 / 全部），不编造 */
const prepSourceLabel = computed(() => {
  if (knowledgeContext.value?.name) return knowledgeContext.value.name
  if (routeChapterId.value) return `章节 ${routeChapterId.value}`
  if (selectedCategory.value !== '全部') return `${selectedCategory.value} 分类`
  return '全部闪卡'
})

/** 准备态里「来自薄弱知识的卡片数」（真实徽章判定，无映射时为 0，不猜） */
const prepWeakCount = computed(
  () => learnFiltered.value.filter((c) => c.badges?.some((b) => b.kind === 'weak')).length,
)

/** 章节 → 知识点映射（真实 chapterKnowledge 端点结果，用于「薄弱知识」徽章判定；取不到则不产出） */
const knowledgeByChapter = ref<Record<number, number[]>>({})

const todayCount = ref(0)
const correctCount = ref(0)
const knownCount = ref(0)
const reviewCount = ref(0)
const newCount = ref(0)
let studyTimer: ReturnType<typeof setInterval> | undefined

const learnTotal = ref(0)
// 今日已学：跨 Tab 聚合（学习侧评分 + 推荐侧标记掌握）
const todayLearned = ref(0)
const studySeconds = ref(0)

// ===================== Architecture-4：本次复习会话（结果 + 奖励 + 下一步 §77/§138） =====================
const learning = useLearningStore()
const walletStore = useWalletStore()
const ratedInSession = ref(0)
const sessionBaseline = ref<{ coin: number; xp: number } | null>(null)
const sessionReward = ref<{ xp: number; coin: number } | null>(null)
const sessionEnded = ref(false)
const sessionNextAction = ref<NextLearningAction | null>(null)

/** 队列上下文：全部来自真实数据（route 深链 / store 掌握度 / 真实章节映射），缺项即不参与排序 */
const queueContext = computed<QueueContext>(() => ({
  knowledgeId: knowledgeContext.value?.id ?? null,
  chapterId: routeChapterId.value,
  pathId: routePathId.value ?? learning.currentPath?.id ?? null,
  weakKnowledgeIds: learning.weakPoints.map((w) => w.knowledgeId),
  knowledgeByChapter: knowledgeByChapter.value,
}))

/** 会话完成：全部过滤后的卡片都已评分（真实数据驱动，§77） */
const learnSessionDone = computed(() =>
  isSessionComplete(ratedInSession.value, learnFiltered.value.length),
)

/** 本次会话自评分布（完成页真实展示，§二十） */
const sessionSummary = computed<SessionSummary>(() => summarizeSession(sessionRatings.value))

/**
 * 真实 Recall 表现：来自 Evidence 聚合的 RECALL 维度（§二十：复用现有 Evidence，不另算一套）。
 * 无样本时返回 null → 完成页不显示该行（禁止把「没有数据」显示成 0）。
 */
const recallPerformance = computed(() => {
  const dim = learning.evidenceAggregate?.dimensions?.find((d) => d.dimension === 'RECALL')
  if (!dim || dim.score == null || dim.sampleCount <= 0) return null
  return { score: Math.round(dim.score), sampleCount: dim.sampleCount }
})

/** 待复习张数（真实到期口径：后端 nextReviewTime 已过） */
const learnDueCount = computed(() => dueCount(learnCards.value, new Date()))

/** 会话模式：已开始学习且未完成时，收起 Tab 与管理类控件（§十七） */
const inSession = computed(
  () => activeTab.value === 'learn' && sessionStarted.value && !learnSessionDone.value,
)

function resetSession(): void {
  sessionQueueIds.value = null
  sessionResumed.value = false
  ratedInSession.value = 0
  sessionBaseline.value = null
  sessionReward.value = null
  sessionEnded.value = false
  sessionNextAction.value = null
  sessionRatings.value = []
  fsrsRatings.value = []
  pendingFsrs.value = null
  ratedCardIds.value = []
  rateError.value = null
  sessionStarted.value = false
}

/** 会话开始时记录真实钱包基线（Reward 后端计算，§142/§198） */
async function ensureSessionBaseline(): Promise<void> {
  if (sessionBaseline.value || sessionResumed.value) return
  await walletStore.fetchWallet(true)
  sessionBaseline.value = { coin: walletStore.wallet?.coin ?? 0, xp: walletStore.wallet?.xp ?? 0 }
}

/** 会话完成：真实奖励 diff + 下一步（§77/§138/§183） */
async function finalizeSession(): Promise<void> {
  if (sessionEnded.value) return
  sessionEnded.value = true
  if (isFsrs.value) learning.saveFlashcardSession(sessionScope.value, null)
  setLastCompleted('FLASHCARD') // 循环保护（§261-262）：刚完成闪卡，不再立刻推荐闪卡
  await walletStore.fetchWallet(true)
  if (sessionBaseline.value && walletStore.wallet) {
    const dCoin = walletStore.wallet.coin - sessionBaseline.value.coin
    const dXp = walletStore.wallet.xp - sessionBaseline.value.xp
    if (dCoin > 0 || dXp > 0) sessionReward.value = { xp: dXp, coin: dCoin }
  }
  // 下一步：UI/UX 5.0 §三 —— 读唯一 store，不再自行拼上下文（旧实现缺 goal / goalPath / evidence）
  await learning.load(true)
  sessionNextAction.value = learning.nextAction
}

/**
 * 加载章节 → 知识点真实映射（Architecture-4.2 端点）。
 * **仅在深链给出 chapterId 时调用**：这是把「薄弱知识点」与「卡片所属章节」关联起来的
 * 唯一真实依据；取不到（无 ACCEPTED 映射）就保持为空，前端不产出薄弱徽章（不猜）。
 */
async function loadChapterKnowledge(): Promise<void> {
  const cid = routeChapterId.value
  if (cid == null) return
  try {
    const ck = await learningApi.chapterKnowledge(cid)
    const ids = (ck?.knowledge ?? []).map((k) => k.id).filter((id) => id > 0)
    if (ids.length > 0) knowledgeByChapter.value = { ...knowledgeByChapter.value, [cid]: ids }
  } catch {
    // 映射缺失是正常情况（该章节尚未做知识提取），不打断学习
    knowledgeByChapter.value = {}
  }
}

// 学习侧：分类 + 关键词前端过滤（在已排序队列之上过滤，顺序不变）
const learnFiltered = computed(() => {
  if (sessionStarted.value && sessionQueueIds.value) {
    const byId = new Map(learnCards.value.map(card => [card.id, card]))
    return sessionQueueIds.value.map(id => byId.get(id)).filter((card): card is SessionCard => !!card)
  }
  return learnCards.value.filter((card) => {
    if (dueOnly.value && (card.reviewCount > 0 || !!card.nextReviewTime) && (!card.nextReviewTime || new Date(card.nextReviewTime).getTime() > Date.now())) return false
    const catOk = selectedCategory.value === '全部' || card.category === selectedCategory.value
    if (!catOk) return false
    const kw = globalKeyword.value.trim().toLowerCase()
    if (!kw) return true
    return card.question.toLowerCase().includes(kw) || card.answer.toLowerCase().includes(kw)
  })
})

const currentCard = computed(() => learnFiltered.value[learnIndex.value] || null)
const readingStage = ref<HTMLElement | null>(null)
let faceScroll = { front: 0, back: 0 }
watch([() => currentCard.value?.id, isFlipped], async ([id, flipped], [previousId, previousFlipped]) => {
  const previousBody = readingStage.value?.closest('.focus-body')
  if (id !== previousId) faceScroll = { front: 0, back: 0 }
  else faceScroll[previousFlipped ? 'back' : 'front'] = previousBody?.scrollTop ?? 0
  await nextTick()
  if (currentCard.value?.id !== id || isFlipped.value !== flipped) return
  const body = readingStage.value?.closest('.focus-body')
  if (body) body.scrollTop = faceScroll[flipped ? 'back' : 'front']
})

const learnProgress = computed(() => {
  if (learnFiltered.value.length === 0) return 0
  return Math.round((ratedInSession.value / learnFiltered.value.length) * 100)
})

const newCountComputed = computed(() => {
  return Math.max(0, learnFiltered.value.length - knownCount.value - reviewCount.value)
})
watch(newCountComputed, (v) => { newCount.value = v })

watch(selectedCategory, () => {
  learnIndex.value = 0
  isFlipped.value = false
  resetSession()
})
watch(globalKeyword, () => {
  learnIndex.value = 0
  isFlipped.value = false
  resetSession()
})

const getDifficultyLabel = (difficulty?: number) => {
  if (difficulty === 2) return '困难'
  if (difficulty === 3) return '高级'
  return '基础'
}
/**
 * Interaction OS（UI/UX 5.1）§九 准备态 → 学习态的唯一入口。
 * 用户明确点击「开始学习」（或按 Space / Enter）后才进入学习态，
 * 避免「一进页面就被自动计入一次学习会话」——那会让中途退出的确认、进度统计都失去意义。
 */
const startSession = () => {
  if (learnFiltered.value.length === 0) return
  sessionQueueIds.value = learnFiltered.value.map(card => card.id)
  sessionStarted.value = true
  learnIndex.value = Math.max(0, learnFiltered.value.findIndex(card => !ratedCardIds.value.includes(card.id)))
  isFlipped.value = false
  if (isFsrs.value) { if (!ratedInSession.value) sessionId.value = crypto.randomUUID(); saveSessionDraft() }
}

const { sanitize } = useSanitize()
const renderCard = (text?: string) => sanitize(renderMarkdown(text ?? ''))
const flipping = ref(false)
let flipTimer: ReturnType<typeof setTimeout> | undefined
let suppressClick = false
const drag = reactive({ active: false, id: -1, startX: 0, startY: 0, x: 0, y: 0 })
const dragStyle = computed(() => ({ transform: `translate(${drag.x}px, ${drag.y}px) rotate(${drag.x / 30}deg)` }))
const dragLabel = computed(() => !drag.active ? '' : drag.y < -100 && Math.abs(drag.y) > Math.abs(drag.x) ? (isFsrs.value ? '松手：简单' : '') : drag.x < -100 && Math.abs(drag.x) > Math.abs(drag.y) ? (isFsrs.value ? '松手：重来' : '松手：不会') : drag.x > 100 && Math.abs(drag.x) > Math.abs(drag.y) ? '松手：记得' : '继续拖动，松手前不会评分')
function beginDrag(e: PointerEvent) {
  if (!isFlipped.value || reviewing.value || rateError.value || flipping.value || !e.isPrimary || e.button !== 0) return
  if (!(e.target as HTMLElement).closest('.gesture-handle')) return
  Object.assign(drag, { active: true, id: e.pointerId, startX: e.clientX, startY: e.clientY, x: 0, y: 0 })
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}
function moveDrag(e: PointerEvent) {
  if (!drag.active || e.pointerId !== drag.id) return
  drag.x = e.clientX - drag.startX
  drag.y = e.clientY - drag.startY
}
function cancelDrag() { Object.assign(drag, { active: false, x: 0, y: 0 }) }
function endDrag(e: PointerEvent) {
  if (!drag.active || e.pointerId !== drag.id) return
  const { x, y } = drag
  suppressClick = Math.hypot(x, y) > 12
  cancelDrag()
  if (suppressClick) {
    setTimeout(() => { suppressClick = false }, 0)
    if (x < -100 && Math.abs(x) > Math.abs(y)) void rateCard('unknown', undefined, isFsrs.value ? 1 : undefined)
    else if (x > 100 && Math.abs(x) > Math.abs(y)) void rateCard('mastered', undefined, isFsrs.value ? 3 : undefined)
    else if (isFsrs.value && y < -100 && Math.abs(y) > Math.abs(x)) void rateCard('mastered', undefined, 4)
  }
}
function onCardClick(e: MouseEvent) {
  if ((e.target as HTMLElement).closest('a,button,audio,img,pre') || window.getSelection()?.toString()) return
  flipCard()
}
function onCardKeydown(e: KeyboardEvent) {
  if (e.target !== e.currentTarget) return
  e.preventDefault()
  e.stopPropagation()
  if (!e.repeat) flipCard()
}
const flipCard = () => {
  if (reviewing.value || rateError.value || flipping.value || suppressClick) return
  isFlipped.value = !isFlipped.value
  flipping.value = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches !== true
  if (flipTimer) clearTimeout(flipTimer)
  flipTimer = setTimeout(() => { flipping.value = false }, 240)
}
const prevCard = () => {
  if (!reviewing.value && !rateError.value && !flipping.value && learnIndex.value > 0) { isFlipped.value = false; learnIndex.value-- }
}
const nextCard = () => {
  if (!reviewing.value && !rateError.value && !flipping.value && learnIndex.value < learnFiltered.value.length - 1) { isFlipped.value = false; learnIndex.value++ }
}
const shuffleCards = () => {
  const arr = learnCards.value.slice()
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
  learnCards.value = arr
  learnIndex.value = 0
  isFlipped.value = false
  resetSession()
}

const showFeedback = (msg: string) => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedback.value = msg
  feedbackTimer = setTimeout(() => { feedback.value = '' }, 1600)
}

/**
 * 提交自评：调用后端既有 SM-2 复习接口，并推进到下一张。
 *
 * ── 与旧实现的三点差别 ──
 *   1. 透传真实 knowledgeId（深链上下文）→ 后端写入 FLASHCARD_REVIEWED 事件 metadata
 *      → Evidence 解析器可把该证据归属到具体知识点；
 *   2. **提交失败不推进、不静默**（§二十三）：保留当前卡与待重试评分，
 *      由用户在错误条上点「重试」，避免「点一下没反应但卡片已经跳过」的数据丢失；
 *   3. 会话自评序列独立记录，供完成页展示真实分布。
 */
const rateCard = async (rating: Rating, cardOverride?: SessionCard, fsrsRating?: number) => {
  const card = cardOverride ?? currentCard.value
  if (reviewing.value || !isFlipped.value || flipping.value || !card || learnSessionDone.value) return
  if (ratedCardIds.value.includes(card.id)) {
    showFeedback('这张卡已评价，请继续下一张')
    return
  }
  if (isFsrs.value && (!fsrsRating || !schedulePreview.value || scheduleLoading.value)) return
  reviewing.value = true
  submittingRating.value = fsrsRating ?? null
  rateError.value = null
  try {
    // Architecture-4：会话基线（第一次评分前记录，用于会话结束时的真实奖励 diff）
    await ensureSessionBaseline()
    if (isFsrs.value && fsrsRating && schedulePreview.value) {
      if (!pendingFsrs.value || pendingFsrs.value.cardId !== card.id || pendingFsrs.value.rating !== fsrsRating) pendingFsrs.value = { cardId: card.id, rating: fsrsRating, revision: schedulePreview.value.revision, settingsRevision: schedulePreview.value.settingsRevision, requestId: crypto.randomUUID() }
      const pending = pendingFsrs.value
      const result = await learningApi.reviewFlashcardFsrs(card.id, { rating: fsrsRating, revision: pending.revision, settingsRevision: pending.settingsRevision, requestId: pending.requestId, sessionId: sessionId.value, knowledgeId: resolveEventKnowledgeId(knowledgeContext.value?.id ?? null, queueContext.value) })
      card.nextReviewTime = result.dueAt
      card.reviewCount = fsrsRating === 1 ? 0 : card.reviewCount + 1
      lastDueAt.value = result.dueAt
      fsrsRatings.value.push(fsrsRating)
      pendingFsrs.value = null
    } else {
      await learningApi.reviewFlashcard(card.id, ratingToQuality(rating), resolveEventKnowledgeId(knowledgeContext.value?.id ?? null, queueContext.value))
    }
    sessionStarted.value = true
    markReviewed(dateStr(new Date()), String(card.id))
    sessionRatings.value.push(rating)
    ratedCardIds.value.push(card.id)
    ratedInSession.value++
    todayCount.value++
    todayLearned.value++
    if (rating === 'mastered') { knownCount.value++; correctCount.value++ }
    else if (rating === 'familiar') { reviewCount.value++; correctCount.value++ }
    else { reviewCount.value++ }
    // 未完全掌握的卡片归集到错题本，便于后续针对性复习
    if (rating !== 'mastered' && card.question) {
      mistakesApi.add({
        question: card.question,
        wrongAnswer: '未完全掌握该知识点',
        correctAnswer: card.answer,
        category: card.category,
        difficulty: card.difficulty,
        source: 'flashcard',
      }).catch(() => { /* 归集失败不阻断复习 */ })
    }
    // Phase UI-7：评分接口不返回 nextReviewAt，原「明天再复习这张」是前端猜的时间（§31 禁止）。
    // 改为如实提示「已记录」，具体下次复习时间由复习计划页展示后端真实数据。
    if (rating === 'unknown') showFeedback('已记录 · 会在复习计划中安排再练')
    else if (rating === 'familiar') showFeedback('已记录 · 继续保持')
    else showFeedback('已记录 · 掌握得不错')
    if (learnIndex.value < learnFiltered.value.length - 1) {
      isFlipped.value = false
      learnIndex.value++
    }
    if (isFsrs.value) { saveSessionDraft(); void loadStatistics() }
    // Architecture-4：最后一张评分完成 → 会话结束（结果 + 奖励 + 下一步 §77/§138）
    if (isSessionComplete(ratedInSession.value, learnFiltered.value.length)) {
      void finalizeSession()
    }
  } catch (e: unknown) {
    // §二十三：提交失败必须告知并允许重试；**不推进**，保证「用户看到的仍是没提交成功的那张」。
    // §六：文案走统一契约，不把 err.message（如 "Request failed with status code 502"）抛给用户。
    if (isFsrs.value && (e as { response?: { status?: number } }).response?.status === 409) {
      pendingFsrs.value = null
      try {
        await resumeSession()
        await loadSchedule()
        if (ratedCardIds.value.includes(card.id)) {
          showFeedback('已从服务器恢复这张卡的复习记录')
          if (learnSessionDone.value) void finalizeSession()
          return
        }
      } catch { scheduleError.value = '无法核对服务器进度，请重新加载后继续' }
    }
    const desc = describePageError(e, { subject: '本次评价未成功提交', scope: 'section' })
    rateError.value = { rating, fsrsRating, cardId: card.id, title: desc.title, hint: desc.hint }
    notify(desc.title, 'error')
  } finally {
    reviewing.value = false
    submittingRating.value = null
  }
}

/** 重试上一次失败的评分（§二十三） */
const retryRate = async () => {
  const pending = rateError.value
  if (!pending) return
  const card = learnFiltered.value.find((c) => c.id === pending.cardId) ?? currentCard.value
  rateError.value = null
  await rateCard(pending.rating, card ?? undefined, pending.fsrsRating)
}

/**
 * 加载学习闪卡并构建确定性学习队列。
 *
 * §十四：带 chapterId/pathId 深链进入时，直接把后端过滤参数透传（
 *       GET /learning/flashcards?pathId=&chapterId=），使「NextAction 指到的那个节点」
 *       成为本次队列的真实来源，而不是全库随机闪卡。
 */
const loadLearnCards = async () => {
  learnLoading.value = true
  learnLoadError.value = null
  try {
    const selectedCard = Number(route.query.cardId)
    const list = Number.isSafeInteger(selectedCard) && selectedCard > 0
      ? [await learningApi.myFlashcardDetail(selectedCard)]
      : await learningApi.flashcards(routePathId.value ?? undefined, routeChapterId.value ?? undefined)
    learnCards.value = createSessionQueue(list, queueContext.value)
    learnTotal.value = list.length
    const uniqueCats = Array.from(new Set(learnCards.value.map((f) => f.category || '通用')))
    categories.value = ['全部', ...uniqueCats]
    newCount.value = learnFiltered.value.length
    resetSession()
    // 计时器防重：重复加载（重试 / 切换上下文）不得累积多个 interval
    if (studyTimer) clearInterval(studyTimer)
    studyTimer = setInterval(() => { studySeconds.value++ }, 1000)
  } catch (e: unknown) {
    // §二十三：加载失败要给出错误态 + 重试，而不是只弹一个 toast 后留白。
    // §六：存原始异常，由 `learnError` 统一翻译为三要素文案（渲染层不碰 err.message）。
    learnLoadError.value = e
    learnCards.value = []
    learnTotal.value = 0
  } finally {
    learnLoading.value = false
  }
}

// ===================== 推荐复习 State =====================
// 数据来源：错题本中 source=flashcard 且未掌握（mastered=0）的卡片
const reviewLoading = ref(false)
const reviewCards = ref<MistakeVO[]>([])
const reviewIndex = ref(0)
const isReviewFlipped = ref(false)
const reviewFeedback = ref('')
let reviewFeedbackTimer: ReturnType<typeof setTimeout> | undefined
const markingMastered = ref(false)
const reviewMasteredCount = ref(0)

const reviewTotal = computed(() => reviewCards.value.length)

// 推荐侧：关键词前端过滤
const reviewFiltered = computed(() => {
  const kw = globalKeyword.value.trim().toLowerCase()
  if (!kw) return reviewCards.value
  return reviewCards.value.filter((c) => {
    const q = (c.question || '').toLowerCase()
    const a = (c.correctAnswer || c.wrongAnswer || '').toLowerCase()
    return q.includes(kw) || a.includes(kw)
  })
})

const currentReviewCard = computed(() => reviewFiltered.value[reviewIndex.value] || null)

const reviewProgress = computed(() => {
  if (reviewFiltered.value.length === 0) return 0
  return Math.round(((reviewIndex.value + 1) / reviewFiltered.value.length) * 100)
})

watch(globalKeyword, () => {
  reviewIndex.value = 0
  isReviewFlipped.value = false
})

const flipReviewCard = () => { isReviewFlipped.value = !isReviewFlipped.value }
const prevReviewCard = () => {
  if (reviewIndex.value > 0) { isReviewFlipped.value = false; reviewIndex.value-- }
}
const nextReviewCard = () => {
  if (reviewIndex.value < reviewFiltered.value.length - 1) { isReviewFlipped.value = false; reviewIndex.value++ }
}

const showReviewFeedback = (msg: string) => {
  if (reviewFeedbackTimer) clearTimeout(reviewFeedbackTimer)
  reviewFeedback.value = msg
  reviewFeedbackTimer = setTimeout(() => { reviewFeedback.value = '' }, 1600)
}

// 继续复习：仅推进到下一张，不调后端
const keepReviewing = () => {
  if (reviewIndex.value < reviewFiltered.value.length - 1) {
    isReviewFlipped.value = false
    reviewIndex.value++
    showReviewFeedback('已切换到下一张，未提交学习记录')
  } else {
    showReviewFeedback('已经是最后一张了')
  }
}

// 标记掌握：调 mistakesApi.markMastered，从推荐列表移除并推进
const markReviewMastered = async () => {
  if (markingMastered.value || !currentReviewCard.value) return
  markingMastered.value = true
  try {
    await mistakesApi.markMastered(currentReviewCard.value.id)
    reviewMasteredCount.value++
    todayLearned.value++
    // 从推荐列表移除当前卡片
    const removedId = currentReviewCard.value.id
    reviewCards.value = reviewCards.value.filter((c) => c.id !== removedId)
    if (reviewIndex.value >= reviewCards.value.length) {
      reviewIndex.value = Math.max(0, reviewCards.value.length - 1)
    }
    isReviewFlipped.value = false
    showReviewFeedback('已标记掌握 · 不会再推荐')
  } catch (e: unknown) {
    notify(getApiError(e, '标记掌握失败'), 'error')
  } finally {
    markingMastered.value = false
  }
}

const loadReviewCards = async () => {
  reviewLoading.value = true
  try {
    // 取未掌握错题（mastered=0），前端过滤 source=flashcard
    const res = await mistakesApi.list({ mastered: 0, pageNum: 1, pageSize: 200 })
    const all = res?.records || []
    reviewCards.value = all.filter((m) => m.source === 'flashcard')
    reviewIndex.value = 0
    isReviewFlipped.value = false
    reviewMasteredCount.value = 0
  } catch (e: unknown) {
    notify(getApiError(e, '加载推荐复习失败'), 'error')
    reviewCards.value = []
  } finally {
    reviewLoading.value = false
  }
}

// ===================== 我的闪卡 State =====================
const mineLoading = ref(false)
const submitting = ref(false)
const generating = ref(false)
const exchangeMode = ref<'import' | 'export' | null>(null)
const importing = ref(false)
const mineCards = ref<FlashcardVO[]>([])
const categories2 = ref<CategoryVO[]>([])
const dialogDocs = ref<DocVO[]>([])
const previewList = ref<FlashcardVO[]>([])
const flippedId = ref<number | null>(null)
const viewMode = ref<'list' | 'card'>('card')
const selectedIds = ref<number[]>([])

const mineTotal = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
const pageSizeOptions = [
  { value: 12, label: '12' },
  { value: 24, label: '24' },
  { value: 48, label: '48' },
]
const mineContentRef = ref<HTMLElement | null>(null)
const detailCard = ref<FlashcardVO | null>(null)

const filters = reactive({
  difficulty: undefined as number | undefined,
  sourceType: '' as string,
  categoryId: undefined as number | undefined,
})

const allSelected = computed(() => mineCards.value.length > 0 && selectedIds.value.length === mineCards.value.length)
const someSelected = computed(() => selectedIds.value.length > 0 && !allSelected.value)
const pageStart = computed(() => (mineTotal.value === 0 ? 0 : (pageNum.value - 1) * pageSize.value + 1))
const pageEnd = computed(() => Math.min(pageNum.value * pageSize.value, mineTotal.value))

const formDialog = reactive<{ visible: boolean; mode: 'create' | 'edit'; id?: number }>({ visible: false, mode: 'create' })
const genDialog = reactive<{
  visible: boolean
  source: 'kb' | 'doc'
  categoryId?: number
  docId?: number
  count: number
  difficultyPreference: number
}>({ visible: false, source: 'kb', count: 10, difficultyPreference: 0 })
const importDialog = reactive<{ visible: boolean; jsonText: string; parseError: string }>({ visible: false, jsonText: '', parseError: '' })
const clozeExample = '{{c1::答案::提示}}'
const noteId = ref<number | null>(null)
const noteRevision = ref<number | undefined>()
const notePreview = ref<{ ordinal: number; front: string; back: string }[]>([])
const notePreviewBusy = ref(false)
const formData = reactive<FlashcardInput & Pick<FlashcardNoteInput, 'type' | 'language'>>({
  type: 'BASIC', language: 'java',
  front: '',
  back: '',
  difficulty: 1,
  category: '',
  tags: '',
  categoryId: undefined,
  docId: undefined,
  pathId: undefined,
  chapterId: undefined,
})

const filterDifficultySelect = computed({ get: () => filters.difficulty ?? null, set: (value: number | null) => { filters.difficulty = value ?? undefined } })
const filterCategorySelect = computed({ get: () => filters.categoryId ?? null, set: (value: number | null) => { filters.categoryId = value ?? undefined } })
const formCategorySelect = computed({ get: () => formData.categoryId ?? null, set: (value: number | null) => { formData.categoryId = value ?? undefined } })
const formDocSelect = computed({ get: () => formData.docId ?? null, set: (value: number | null) => { formData.docId = value ?? undefined } })
const generationCategorySelect = computed({ get: () => genDialog.categoryId ?? null, set: (value: number | null) => { genDialog.categoryId = value ?? undefined } })
const generationDocSelect = computed({ get: () => genDialog.docId ?? null, set: (value: number | null) => { genDialog.docId = value ?? undefined } })

const genReady = computed(() => {
  if (genDialog.source === 'kb') return genDialog.categoryId != null
  return genDialog.docId != null
})

watch(
  () => [formDialog.visible, formData.categoryId, genDialog.visible, genDialog.categoryId] as const,
  async () => {
    if (!formDialog.visible && !genDialog.visible) return
    const cid = formDialog.visible ? formData.categoryId : genDialog.categoryId
    if (!cid) {
      if (genDialog.visible && genDialog.source === 'doc') {
        const r = await docsApi.list({ pageSize: 200, pageNum: 1 }).catch(() => ({ list: [] as DocVO[] }))
        dialogDocs.value = (r as { list?: DocVO[] })?.list || []
      } else {
        dialogDocs.value = []
      }
      return
    }
    const r = await docsApi.list({ categoryId: Number(cid), pageSize: 200, pageNum: 1 }).catch(() => ({ list: [] as DocVO[] }))
    dialogDocs.value = (r as { list?: DocVO[] })?.list || []
  },
)

const loadCategories2 = async () => {
  try {
    const list = await categoriesApi.tree()
    const flat: CategoryVO[] = []
    const walk = (arr: CategoryVO[]) => {
      arr.forEach(n => {
        flat.push(n)
        if (n.children && n.children.length) walk(n.children as CategoryVO[])
      })
    }
    walk(list as CategoryVO[])
    categories2.value = flat
  } catch {
    try { categories2.value = await categoriesApi.list() } catch { /* 兜底失败忽略 */ }
  }
}

const reloadMine = async (resetPage = false) => {
  if (resetPage) pageNum.value = 1
  mineLoading.value = true
  try {
    const res = await learningApi.myFlashcards({
      keyword: globalKeyword.value || undefined,
      difficulty: filters.difficulty,
      categoryId: filters.categoryId,
      sourceType: filters.sourceType || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    mineCards.value = res.records
    mineTotal.value = res.total
    // 当前页最后一条被删除 / 页码越界：自动回退到有效页，不停留在空白页
    if (mineCards.value.length === 0 && res.total > 0 && pageNum.value > 1) {
      pageNum.value = Math.max(1, Math.ceil(res.total / pageSize.value))
      return await reloadMine()
    }
    // 选择状态按稳定 ID 收敛到当前页，绝不把未显示页面的记录当作已选
    selectedIds.value = selectedIds.value.filter(id => mineCards.value.some(c => c.id === id))
    flippedId.value = null
  } catch (e: unknown) {
    notify(getApiError(e, '加载我的闪卡失败'), 'error')
  } finally {
    mineLoading.value = false
  }
}

/** 筛选条件变化：回到第一页并清空跨页无效选择 */
const applyFilters = () => {
  selectedIds.value = []
  void reloadMine(true)
}
/** 删除后的分页校正由 reloadMine 内部完成（末页空白自动回退） */
const reloadAfterDelete = () => reloadMine()

const changePage = (page: number) => {
  if (page === pageNum.value) return
  pageNum.value = page
  selectedIds.value = []
  void reloadMine()
  void nextTick(() => mineContentRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

const changePageSize = (size: unknown) => {
  const next = Number(size) || 12
  if (next === pageSize.value) return
  pageSize.value = next
  pageNum.value = 1
  selectedIds.value = []
  void reloadMine()
}

/** 当前是否处于筛选/搜索状态：用于区分「确实没有卡片」与「筛选无结果」 */
const hasMineFilter = computed(
  () =>
    !!globalKeyword.value.trim() ||
    filters.difficulty !== undefined ||
    filters.sourceType !== '' ||
    filters.categoryId !== undefined,
)

const resetMineFilters = () => {
  globalKeyword.value = ''
  filters.difficulty = undefined
  filters.sourceType = ''
  filters.categoryId = undefined
  applyFilters()
}

// ===================== 选择 =====================
const toggleAll = (e: Event) => {
  const checked = (e.target as HTMLInputElement).checked
  selectedIds.value = checked ? mineCards.value.map(c => c.id) : []
}
const toggleId = (id: number) => {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}

// ===================== CRUD =====================
const resetForm = () => {
  noteId.value = null
  noteRevision.value = undefined
  notePreview.value = []
  Object.assign(formData, {
    front: '', back: '', difficulty: 1, category: '', tags: '', type: 'BASIC', language: 'java',
    categoryId: undefined, docId: undefined,
  })
}
const openCreate = () => {
  resetForm()
  formDialog.mode = 'create'
  formDialog.id = undefined
  formDialog.visible = true
}
const openEdit = async (c: FlashcardVO) => {
  resetForm()
  formDialog.mode = 'edit'
  formDialog.id = c.id
  Object.assign(formData, {
    front: c.front || '', back: c.back || '', difficulty: c.difficulty || 1,
    category: c.category || '', tags: c.tags || '',
    categoryId: c.categoryId, docId: c.docId,
  })
  try {
    const note = await learningApi.flashcardNoteByCard(c.id)
    if (note) {
      noteId.value = note.id
      noteRevision.value = note.revision
      Object.assign(formData, { type: note.type, language: note.language, front: note.front, back: note.back, category: note.category, tags: note.tags })
    }
    formDialog.visible = true
  } catch (e) { notify(getApiError(e, '无法加载完整闪卡笔记，请重试'), 'error') }
}
const closeForm = () => { formDialog.visible = false }

const previewNote = async () => {
  notePreviewBusy.value = true
  notePreview.value = []
  try { notePreview.value = (await learningApi.previewFlashcardNote({ type: formData.type, front: formData.front, back: formData.back, language: formData.language })).cards }
  catch (e) { notify(getApiError(e, '预览失败，请检查卡片内容'), 'error') }
  finally { notePreviewBusy.value = false }
}

const submitForm = async () => {
  if (!formData.front.trim() || (!formData.type.endsWith('CLOZE') && !formData.back.trim())) {
    notify('正面和背面内容必填', 'error')
    return
  }
  submitting.value = true
  try {
    const payload: FlashcardInput = {
      front: formData.front.trim(),
      back: formData.back.trim(),
      difficulty: formData.difficulty,
      category: formData.category?.trim() || undefined,
      tags: formData.tags?.trim() || undefined,
      categoryId: formData.categoryId,
      docId: formData.docId,
    }
    if (formData.type !== 'BASIC' || noteId.value) {
      const note: FlashcardNoteInput = { type: formData.type, front: formData.front, back: formData.back, language: formData.language, category: formData.category, tags: formData.tags, revision: noteRevision.value }
      if (noteId.value) {
        try { await learningApi.updateFlashcardNote(noteId.value, note) }
        catch (e) {
          const code = (e as { response?: { status?: number } }).response?.status
          if (code !== 409 || !await confirmDialog('本次修改可能删除或重编号复习卡。旧学习事件会保留，未变化编号保留排程。确认继续？', {"title":"保存并调整复习卡？","confirmText":"继续保存","cancelText":"取消","icon":"trash-2","variant":"danger"})) throw e
          await learningApi.updateFlashcardNote(noteId.value, { ...note, confirmHistoryImpact: true })
        }
      } else await learningApi.createFlashcardNote(note)
      notify('闪卡笔记及复习卡已保存', 'success')
    } else if (formDialog.mode === 'edit' && formDialog.id) {
      await learningApi.updateMyFlashcard(formDialog.id, payload)
      notify('已更新', 'success')
    } else {
      await learningApi.createMyFlashcard(payload)
      notify('已新增', 'success')
    }
    closeForm()
    await Promise.all([reloadMine(), loadLearnCards()])
  } catch (e: unknown) {
    notify(getApiError(e, '保存失败'), 'error')
  } finally {
    submitting.value = false
  }
}

const removeOne = async (c: FlashcardVO) => {
  await confirmDialog(`确认删除「${truncate(c.front || '', 40)}」？`, { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
          try {
              await learningApi.deleteMyFlashcard(c.id);
              notify('已删除', 'success');
              await reloadAfterDelete();
          }
          catch (e: unknown) {
              notify(getApiError(e, '删除失败'), 'error');
              throw e;
          }
      } });
}

const batchDelete = async () => {
  if (selectedIds.value.length === 0) return
  await confirmDialog(`确认删除选中的 ${selectedIds.value.length} 张闪卡？`, { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
          try {
              await learningApi.deleteMyFlashcards(selectedIds.value);
              notify(`已删除 ${selectedIds.value.length} 张`, 'success');
              selectedIds.value = [];
              await reloadAfterDelete();
          }
          catch (e: unknown) {
              notify(getApiError(e, '删除失败'), 'error');
              throw e;
          }
      } });
}

// ===================== Flip =====================
const toggleFlip = (id: number) => {
  flippedId.value = flippedId.value === id ? null : id
}

// ===================== AI Generate =====================
const openGenerate = () => {
  genDialog.visible = true
  genDialog.source = 'kb'
  genDialog.categoryId = categories2.value[0]?.id
  genDialog.docId = undefined
  genDialog.count = 10
  genDialog.difficultyPreference = 0
  previewList.value = []
}
const closeGenerate = () => { genDialog.visible = false; previewList.value = [] }

const doGenerate = async () => {
  if (!genReady.value) { notify('请选择生成来源', 'error'); return }
  generating.value = true
  previewList.value = []
  try {
    const list = await learningApi.generateMyFlashcards({
      categoryId: genDialog.source === 'kb' ? genDialog.categoryId : undefined,
      docId: genDialog.source === 'doc' ? genDialog.docId : undefined,
      count: genDialog.count,
      difficultyPreference: genDialog.difficultyPreference || undefined,
    })
    previewList.value = list
    notify(`成功生成 ${list.length} 张闪卡`, 'success')
    await reloadMine()
  } catch (e: unknown) {
    notify(getApiError(e, 'AI 生成失败'), 'error')
  } finally {
    generating.value = false
  }
}

// ===================== Import / Export =====================
const openImport = () => { importDialog.visible = true; importDialog.jsonText = ''; importDialog.parseError = '' }
const closeImport = () => { importDialog.visible = false }

const doImport = async () => {
  let arr: unknown[]
  try {
    if (!importDialog.jsonText.trim()) throw new Error('请粘贴 JSON 数组')
    arr = JSON.parse(importDialog.jsonText)
    if (!Array.isArray(arr)) throw new Error('必须是 JSON 数组')
  } catch (e: unknown) {
    importDialog.parseError = e instanceof Error ? e.message : 'JSON 解析失败'
    return
  }
  const valid = (arr as Record<string, unknown>[])
    .filter(x => x && typeof x.front === 'string' && typeof x.back === 'string')
    .map(x => ({
      front: String(x.front),
      back: String(x.back),
      difficulty: typeof x.difficulty === 'number' ? x.difficulty : undefined,
      category: typeof x.category === 'string' ? x.category : undefined,
      tags: typeof x.tags === 'string' ? x.tags : undefined,
      categoryId: typeof x.categoryId === 'number' ? x.categoryId : undefined,
      docId: typeof x.docId === 'number' ? x.docId : undefined,
    } as FlashcardInput))
  if (valid.length === 0) {
    importDialog.parseError = '没有有效卡片，请检查 front/back 字段'
    return
  }
  importing.value = true
  importDialog.parseError = ''
  try {
    const r = await learningApi.importMyFlashcards(valid)
    notify(`成功导入 ${r.inserted} 张`, 'success')
    closeImport()
    reloadMine()
  } catch (e: unknown) {
    notify(getApiError(e, '导入失败'), 'error')
  } finally {
    importing.value = false
  }
}

const doExport = async () => {
  try {
    const list = await learningApi.exportMyFlashcards()
    const safe = list.map(c => ({
      front: c.front, back: c.back, category: c.category, difficulty: c.difficulty,
      tags: c.tags, sourceType: c.sourceType, categoryId: c.categoryId, docId: c.docId, createTime: c.createTime,
    }))
    const json = JSON.stringify(safe, null, 2)
    const blob = new Blob([json], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const stamp = new Date().toISOString().replace(/-/g, '').replace(/:/g, '').replace(/T/g, '').slice(0, 14)
    a.href = url
    a.download = `my-flashcards-${stamp}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    notify(`已导出 ${list.length} 张`, 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '导出失败'), 'error')
  }
}

// ===================== 会话模式 / 退出 / 键盘（§十七 / §十八 / §十九） =====================

/** 键盘快捷键提示文案（统一来自纯函数模块，便于测试） */
const keyboardHint = computed(() => isFsrs.value ? 'Space 显示答案 · 1 重来 · 2 困难 · 3 记得 · 4 简单 · ← → 导航 · Esc 退出' : KEYBOARD_HINT)

/** UI/UX 6.1：FocusShell 退出处理（FocusShell 已内置确认弹窗，无需二次确认） */
function handleFocusExit(): void {
  if (reviewing.value) return
  sessionStarted.value = false
}

/** 退出学习会话（§十九）：只结束 Session 模式，**不销毁**已完成的进度 */
async function exitSessionMode(): Promise<void> {
  if (reviewing.value) return
  if (!inSession.value) {
    sessionStarted.value = false
    return
  }
  const ok = await dialog.confirm({"variant":"default",
    title: '结束本次学习？',
    message: `已完成 ${ratedInSession.value} / ${learnFiltered.value.length} 张。已提交的评价会保留。`,
    confirmText: '结束学习',
    cancelText: '继续学习',
    icon: 'layers',
  })
  if (ok) sessionStarted.value = false
}

/** 再学一轮（§二十 [继续学习]）：重置会话计数并重新拉取队列（真实数据，不伪造进度） */
async function restartSession(): Promise<void> {
  sessionEnded.value = false
  resetSession()
  learnIndex.value = 0
  isFlipped.value = false
  await loadLearnCards()
}

/**
 * 离开页面确认（§十九）：
 * 学习进行中（已评价 ≥1 张且未完成）时必须确认，覆盖顶部返回按钮与浏览器返回。
 * 使用项目统一 Dialog（禁止浏览器原生 confirm）。
 */
onBeforeRouteLeave(async () => {
  if (reviewing.value) return false
  if (!isSessionInProgress(ratedInSession.value, learnFiltered.value.length)) return true
  return dialog.confirm({"variant":"default",
    title: '结束本次学习？',
    message: `已完成 ${ratedInSession.value} / ${learnFiltered.value.length} 张。离开后可从当前进度继续。`,
    confirmText: '结束学习',
    cancelText: '继续学习',
    icon: 'layers',
  })
})

/** 键盘快捷键（§十八）：Space 翻卡 · 1/2/3 自评 · ← → 切换 · Esc 退出 */
function onKeydown(e: KeyboardEvent): void {
  if (e.isComposing || settingsOpen.value || e.defaultPrevented || e.repeat || e.ctrlKey || e.metaKey || e.altKey || activeTab.value !== 'learn') return
  // 输入控件内不劫持按键（搜索框 / 我的闪卡表单）
  const t = e.target instanceof HTMLElement ? e.target : null
  const control = t?.closest('button,a[href],summary,[role=button],[role=link],audio,video,pre[tabindex]')
  if (control && !control.matches('.fsrs-rating,.flip-card')) return
  if (t && (([' ', 'Spacebar', 'Enter'].includes(e.key) && !!t.closest('button,a[href],summary')) || t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable || !!t.closest('[role=dialog]:not(.focus-shell), [role=textbox]'))) return
  if (learnSessionDone.value || learnFiltered.value.length === 0) return

  // UI/UX 5.1 §九：准备态下 Space / Enter 承担「开始学习」，且不允许 1/2/3 跳过准备直接评分
  if (!sessionStarted.value) {
    if (e.key === ' ' || e.key === 'Spacebar' || e.key === 'Enter') {
      e.preventDefault()
      startSession()
    }
    return
  }

  if (e.key === ' ' || e.key === 'Spacebar') {
    e.preventDefault()
    flipCard()
    return
  }
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    prevCard()
    return
  }
  if (e.key === 'ArrowRight') {
    e.preventDefault()
    nextCard()
    return
  }
  if (e.key === 'Escape') {
    e.preventDefault()
    if (inSession.value) void exitSessionMode()
    return
  }
  if (isFsrs.value && /^[1-4]$/.test(e.key)) {
    e.preventDefault()
    if (rateError.value) void retryRate()
    else { const value = Number(e.key); void rateCard(fsrsSummaryRating(value), undefined, value) }
    return
  }
  const rating = keyToRating(e.key)
  if (rating) {
    e.preventDefault()
    // 上一次提交失败时，同一按键直接重试（与「重试」按钮等价）
    if (rateError.value) void retryRate()
    else void rateCard(rating)
  }
}

/** 清除深链上下文（含章节过滤）→ 回到完整闪卡池 */
async function clearContext(): Promise<void> {
  knowledgeContext.value = null
  routeChapterId.value = null
  knowledgeByChapter.value = {}
  sessionStarted.value = false
  await loadLearnCards()
}

// ===================== Helpers =====================
const goBack = () => {
  if (window.history.length > 1) router.back()
  else router.push('/learning/center')
}
const truncate = (s: string, n = 40) => (s && s.length > n ? s.slice(0, n) + '…' : s)
const splitTags = (s?: string) => (s ? s.split(/[,，]/).map(x => x.trim()).filter(Boolean) : [])
const diffLabel = (d?: number) => (d === 1 ? '简单' : d === 2 ? '中等' : d === 3 ? '困难' : '中等')
const diffClass = (d?: number) => (d === 1 ? 'diff-easy' : d === 3 ? 'diff-hard' : 'diff-medium')
const sourceLabel = (s?: string) =>
  s === 'MANUAL' ? '手动' : s === 'AI_DOC' ? 'AI·文档' : s === 'AI_KB' ? 'AI·知识库' : s === 'IMPORT' ? '导入' : s === 'TECH_ENGLISH' ? '专业英语' : '未知'
const sourceIcon = (s?: string) =>
  s === 'MANUAL' ? 'pencil' : (s === 'AI_DOC' || s === 'AI_KB') ? 'sparkles' : s === 'IMPORT' ? 'upload' : s === 'TECH_ENGLISH' ? 'book-open' : 'help-circle'
const sourceClass = (s?: string) =>
  s === 'MANUAL' ? 'src-manual' : (s === 'AI_DOC' || s === 'AI_KB') ? 'src-ai' : s === 'IMPORT' ? 'src-imp' : ''
const formatTime = (t?: string) => {
  if (!t) return '—'
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ===================== 完整内容查看 / 复制 =====================
const openDetail = (c: FlashcardVO) => { detailCard.value = c }
const editFromDetail = async () => {
  const card = detailCard.value
  if (!card) return
  detailCard.value = null
  await openEdit(card)
}
const copyText = async (text?: string) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    notify('已复制', 'success')
  } catch {
    notify('复制失败，请手动选择文本', 'error')
  }
}
/** 粗略判断是否为代码/结构化文本：命中时用等宽字体并允许容器内横向滚动 */
const looksLikeCode = (text?: string) => {
  const s = (text || '').trim()
  if (!s) return false
  if (s.includes('```')) return true
  if (/(^|\n)\s*(public|private|protected|class|interface|enum|import|package|function|const|let|var|def|SELECT|INSERT|UPDATE|DELETE|CREATE|@Override|#include)\b/.test(s)) return true
  const lines = s.split('\n')
  if (lines.length < 3) return false
  const codeish = lines.filter(l => /[;{}<>]$|^\s*[\w$.]+\(.*\)\s*;?\s*$/.test(l.trim())).length
  return codeish >= Math.ceil(lines.length / 2)
}

watch([() => currentCard.value?.id, inSession, algorithmMode], () => { void loadSchedule() })
watch(dueOnly, () => { resetSession(); learnIndex.value = 0 })
// ===================== 生命周期 =====================
onMounted(async () => {
  // 并行加载学习侧卡片、章节→知识点真实映射、推荐侧卡片与我的侧分类；我的侧卡片列表在切到该 Tab 时懒加载
  await Promise.all([loadChapterKnowledge().then(() => loadLearnCards()), loadReviewCards(), loadCategories2()])
  // §十八：全局键盘快捷键（进入学习会话后生效）
  await loadStatistics()
  try { await resumeSession() } catch (e) { notify(getApiError(e, '无法恢复学习会话，请重试加载'), 'error') }
  window.addEventListener('keydown', onKeydown)
})

// 首次切到「我的闪卡」时懒加载卡片
// UI/UX 5.1：immediate —— 支持 `?tab=mine` 直接落地时也要触发懒加载（否则深链进来是空的）
const mineLoadedOnce = ref(false)
watch(activeTab, async (tab) => {
  if (tab === 'mine' && !mineLoadedOnce.value) {
    mineLoadedOnce.value = true
    await reloadMine()
  }
}, { immediate: true })

onUnmounted(() => {
  if (flipTimer) clearTimeout(flipTimer)
  if (feedbackTimer) clearTimeout(feedbackTimer)
  if (reviewFeedbackTimer) clearTimeout(reviewFeedbackTimer)
  if (studyTimer) clearInterval(studyTimer)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.select-inline { width: auto; min-width: 0; }
/* ══════════ UI/UX 5.0 §九：学习执行页顶部条（进度 / 今日学习 / 待复习 + 二级入口）══════════ */
.learn-topbar {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3, 12px);
  flex-wrap: wrap;
  padding-bottom: var(--kf-space-2, 8px);
}
.learn-counter {
  font-size: var(--kf-fs-body-lg, 16px);
  font-weight: 600;
  color: var(--kf-foreground);
}
.learn-progress-track {
  flex: 1;
  min-width: 120px;
  height: 4px;
  border-radius: 999px;
  background: var(--kf-muted);
  overflow: hidden;
}
.learn-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--kf-primary);
  transition: width 0.3s ease;
}
.learn-stat {
  font-size: var(--kf-fs-caption, 12px);
  color: var(--kf-muted-foreground);
  white-space: nowrap;
}
.learn-stat b {
  color: var(--kf-foreground);
  font-weight: 600;
}
.learn-topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-1, 4px);
  margin-left: auto;
}
.learn-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md, 8px);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.learn-icon-btn:hover {
  color: var(--kf-foreground);
}
.learn-icon-btn.active {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.learn-icon-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kf-ring);
}
.learn-category {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md, 8px);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption, 12px);
}

/* 翻页按钮降为次要图标（§九：主区只允许一个视觉重心） */
.action-bar .btn-ghost-icon {
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md, 8px);
  background: transparent;
  color: var(--kf-muted-foreground);
}
.action-bar .btn-ghost-icon:hover:not(:disabled) {
  color: var(--kf-foreground);
  background: var(--kf-muted);
}
/* ========== 基础布局 ========== */
.flashcards-hub {
  padding: 24px 32px 80px;
  max-width: none;
  margin-inline: 0;
  color: var(--kf-foreground);
  display: flex;
  flex-direction: column;
  gap: 20px;

  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}
.animate-fade-in { animation: fadeIn 0.4s ease-out; }
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ========== 顶部 Hero ========== */
.hub-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
  padding: 20px 24px;
  border-radius: var(--kf-radius-lg);
  background: linear-gradient(135deg, var(--kf-primary-soft), var(--kf-card));
  border: 1px solid var(--kf-border);
}
.hero-left { display: flex; align-items: center; gap: 14px; }
.back-btn {
  width: 36px; height: 36px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.back-btn:hover { color: var(--kf-primary); border-color: var(--kf-primary); }
.title-text { display: flex; flex-direction: column; gap: 2px; }
.kf-h1 { margin: 0; font-size: var(--kf-fs-h2); font-weight: 600; }
.kf-body-sm { margin: 0; color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }

/* Hero 统计卡片组 */
.hero-stats { display: flex; gap: 14px; flex-wrap: wrap; }

/* ========== 标签页 ========== */
.tabs-bar {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--kf-radius-lg);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  align-self: flex-start;
  /* §二十八 窄屏兜底：宁可横向滚动，也不把中文标签压成「逐字竖排」 */
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}
.tabs-bar::-webkit-scrollbar { display: none; }
.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  border-radius: var(--kf-radius-md);
  border: none;
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  /* §二十八：禁止 flex 压缩导致 CJK 标签逐字折行 */
  flex-shrink: 0;
  white-space: nowrap;
}
.tab-btn:hover { color: var(--kf-foreground); }
.tab-btn.active {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.25);
}
.tab-count {
  min-width: 20px; height: 20px; padding: 0 6px;
  border-radius: 999px;
  background: var(--kf-muted);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-xs); font-weight: 600;
  display: inline-flex; align-items: center; justify-content: center;
}
.tab-btn.active .tab-count {
  background: rgba(255,255,255,0.25);
  color: var(--kf-primary-foreground);
}

/* ========== 全局搜索 ========== */
.global-search {
  position: relative;
  max-width: 520px;
}
.search-icon {
  position: absolute; left: 12px; top: 50%;
  transform: translateY(-50%);
  color: var(--kf-muted-foreground);
}
.search-input:not(.kf-form-control) {
  height: 40px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.search-input {
  width: 100%;
  padding: 0 36px 0 36px;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
}
.search-input:not(.kf-form-control):focus {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}
.clear-btn {
  position: absolute; right: 8px; top: 50%;
  transform: translateY(-50%);
  width: 24px; height: 24px;
  border-radius: var(--kf-radius-sm);
  border: none;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer;
}
.clear-btn:hover { color: var(--kf-foreground); }

/* ========== Tab 面板 ========== */
.tab-panel { display: flex; flex-direction: column; gap: 18px; }

/* ========== 推荐复习说明条 ========== */
.review-intro {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-radius: var(--kf-radius-lg);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-left: 3px solid var(--kf-primary);
}
.intro-icon { color: var(--kf-primary); flex-shrink: 0; }
.intro-text { flex: 1; min-width: 0; }
.intro-title { margin: 0; font-size: var(--kf-fs-body-md); font-weight: 600; color: var(--kf-foreground); }
.intro-desc { margin: 2px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); line-height: 1.5; }

/* ========== 学习侧：进度块 ========== */
.progress-block {
  display: flex; align-items: center; gap: 24px;
  padding: 16px 20px;
  border-radius: var(--kf-radius-lg);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
}
.progress-left { flex: 1; min-width: 0; }
.progress-meta {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8px;
}
.progress-label { font-size: var(--kf-fs-body-md); font-weight: 500; color: var(--kf-foreground); }
.progress-value { font-size: var(--kf-fs-body-md); font-weight: 600; color: var(--kf-primary); }
.progress-track {
  height: 10px; border-radius: 999px; overflow: hidden;
  background: var(--kf-muted);
}
.progress-fill {
  height: 100%; border-radius: 999px;
  background: var(--kf-primary);
  transition: width 0.3s ease;
}
.progress-right { display: flex; align-items: center; gap: 24px; }
.stat-cell { text-align: center; }
.stat-num { font-size: var(--kf-fs-h4); font-weight: 700; line-height: 1.2; }
.stat-known { color: var(--kf-accent); }
.stat-review { color: var(--kf-warning); }
.stat-new { color: var(--kf-destructive); }
.kf-caption { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); margin-top: 2px; }

/* ========== 学习侧工具行 ========== */
.learn-toolbar {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}

/* ========== 按钮通用 ==========
 * P2-UI-Flashcards 根因修复：`.btn-outline` 原本被漏在基础选择器之外，
 * 只命中了后面那条 `.btn-outline { border / background / color }` 变体规则，
 * 因此完全没有拿到 inline-flex / align-items / gap / height / padding / radius，
 * 退化成浏览器原生 <button>：display:block → 图标独占一行并压到文字上、
 * 无内边距与 gap、直角、高度由内容撑开。这里把它并入同一套尺寸体系，
 * 让 Primary 与 Secondary 共用「高度 36px · 圆角 var(--kf-radius-md) ·
 * 图标↔文字 gap 6px · 字号 14px」，只用 border/background 区分层级。
 */
.btn-primary, .btn-secondary, .btn-ghost, .btn-outline {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  height: 36px; padding: 0 14px;
  border-radius: var(--kf-radius-md);
  border: 1px solid transparent;
  font-size: var(--kf-fs-body-md); font-weight: 500;
  cursor: pointer; transition: all 0.15s;
  white-space: nowrap;
}
/* 图标不参与 flex 收缩：窄屏被压扁会让 icon 与文字重新错位 */
.btn-primary > svg, .btn-secondary > svg, .btn-ghost > svg, .btn-outline > svg {
  flex-shrink: 0;
}
/* 禁用态与 .btn-secondary 对齐；必须写在 .action-btn:disabled 之前，
   让操作条上的 .action-btn.btn-outline（44px）继续沿用 0.4 的弱化程度。 */
.btn-outline:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-primary { background: var(--kf-primary); color: var(--kf-primary-foreground); }
.btn-primary:hover { filter: brightness(1.08); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secondary {
  background: var(--kf-card); color: var(--kf-foreground);
  border-color: var(--kf-border);
}
.btn-secondary:hover { border-color: var(--kf-primary); color: var(--kf-primary); }
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-secondary.danger:hover { border-color: var(--kf-destructive); color: var(--kf-destructive); }
.btn-ghost {
  background: transparent; color: var(--kf-muted-foreground);
  padding: 0 10px; height: 36px;
}
.btn-ghost:hover { color: var(--kf-primary); background: rgba(59,111,224,0.08); }
.badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 6px;
  border-radius: 999px;
  background: var(--kf-primary); color: var(--kf-primary-foreground);
  font-size: var(--kf-fs-xs); font-weight: 600;
}

/* ========== 状态区 ========== */
.state-area {
  padding: 64px 24px; text-align: center;
  background: var(--kf-card);
  border: 1px dashed var(--kf-border);
  border-radius: var(--kf-radius-lg);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.state-icon { color: var(--kf-muted-foreground); opacity: 0.6; }
.state-text { margin: 8px 0 0; font-size: var(--kf-fs-body-emphasis); color: var(--kf-foreground); font-weight: 500; }
.state-hint { color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); margin: 0; }
.empty-actions { display: inline-flex; gap: 10px; margin-top: 8px; }
.loading-spinner {
  width: 32px; height: 32px;
  border: 3px solid var(--kf-muted);
  border-top-color: var(--kf-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 翻转卡 ========== */
.flip-area { display: flex; justify-content: center; }
.flip-card {
  width: 100%; max-width: calc(var(--kf-space-8) * 32); height: clamp(24rem, 58vh, 40rem);
  margin-inline: auto; touch-action: pan-y; transition: transform 180ms ease-out;
  perspective: 1000px; cursor: pointer;
}
.flip-card-inner {
  position: relative; width: 100%; height: 100%;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  transform-style: preserve-3d;
}
.flip-card.flipped .flip-card-inner { transform: rotateY(180deg); }
.flip-card-face {
  position: absolute; inset: 0;
  backface-visibility: hidden; -webkit-backface-visibility: hidden;
  border-radius: var(--kf-radius-lg);
  border: 2px solid var(--kf-border);
  background: var(--kf-card);
  display: flex; flex-direction: column;
  padding: 32px; overflow: hidden;
}
.flip-card-front { align-items: center; justify-content: flex-start; overflow-y: auto; }
.gesture-handle { position: absolute; z-index: 2; bottom: var(--kf-space-2); inset-inline: var(--kf-space-3); padding: var(--kf-space-2); background: var(--kf-muted); border-radius: var(--kf-radius-sm); color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption); text-align: center; touch-action: none; cursor: grab; }
.fsrs-rating { height: auto; min-height: calc(var(--kf-space-3) * 4); flex-direction: column; padding: var(--kf-space-2) var(--kf-space-4); gap: var(--kf-space-1); }
.fsrs-rating small { font-size: var(--kf-fs-caption); opacity: .85; }
.prep-controls { display:flex;flex-wrap:wrap;gap:var(--kf-space-4);align-items:center; }
.prep-schedule {display:flex;align-items:center;gap:var(--kf-space-2);min-width:0;font-size:var(--kf-fs-body-md)}
.prep-schedule>span{flex:none;white-space:nowrap}
.prep-schedule .select-inline {width:min(20rem,100%);min-width:0}
.prep-controls>.btn-outline{height:var(--kf-btn-height-md);min-height:var(--kf-btn-height-md)}
@media(max-width:639px){.prep-schedule{flex:1 1 100%}.prep-schedule .select-inline{flex:1;width:0}}
.flip-card.dragging { transition: none; touch-action: none; user-select: none; }
.drag-label { position: absolute; z-index: 2; top: var(--kf-space-3); right: var(--kf-space-3); background: var(--kf-primary); color: var(--kf-primary-foreground); padding: var(--kf-space-2); border-radius: var(--kf-radius-md); }
.markdown-body :deep(img) { max-width: 100%; max-height: 24rem; object-fit: contain; }
.markdown-body :deep(pre) { text-align: left; overflow-x: auto; }
@media (prefers-reduced-motion: reduce) { .flip-card { transition: none; } }
.flip-card-back {
  padding-bottom: calc(var(--kf-space-8) * 2);
  transform: rotateY(180deg);
  border-color: var(--kf-primary);
  justify-content: flex-start;
}
.face-tags { display: flex; align-items: center; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; justify-content: center; }
.face-tag { padding: 4px 10px; border-radius: 999px; font-size: var(--kf-fs-body-sm); font-weight: 500; }
.tag-category { background: rgba(59, 111, 224, 0.08); color: var(--kf-primary); }
.tag-sub { background: rgba(16, 185, 129, 0.08); color: var(--kf-accent); }
.tag-source { background: rgba(245, 158, 11, 0.1); color: var(--kf-warning); }
.face-question {
  font-size: var(--kf-fs-h2); font-weight: 700; text-align: center;
  color: var(--kf-foreground); margin: 0 0 16px 0;
  line-height: 1.4; word-break: keep-all; overflow-wrap: break-word;
}
.face-hint { text-align: center; font-size: var(--kf-fs-body-md); color: var(--kf-muted-foreground); margin: 0 0 32px 0; }
.flip-pill {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 8px 16px; border-radius: 999px;
  background: var(--kf-muted); color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-md);
}
.back-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.back-icon { color: var(--kf-warning); }
.back-label { font-size: var(--kf-fs-body-md); font-weight: 600; color: var(--kf-primary); }
.back-body { flex: 1; overflow: auto; }
.back-title { font-size: var(--kf-fs-h3); font-weight: 700; color: var(--kf-foreground); margin: 0 0 12px 0; }
.back-text { font-size: var(--kf-fs-body-md); line-height: 1.7; color: var(--kf-foreground); margin: 0 0 16px 0; white-space: pre-line; }
.back-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.back-tag { padding: 4px 10px; border-radius: 999px; font-size: var(--kf-fs-body-sm); background: rgba(59, 111, 224, 0.08); color: var(--kf-primary); }

/* ========== 学习侧操作按钮 ========== */
.action-bar { display: flex; justify-content: center; gap: 16px; flex-wrap: wrap; }
.action-btn {
  display: inline-flex; align-items: center; gap: 8px;
  height: 44px; padding: 0 24px;
  border-radius: var(--kf-radius-md);
  font-size: var(--kf-fs-body-md); font-weight: 500;
  cursor: pointer; transition: background-color 0.15s, color 0.15s, border-color 0.15s;
  border: none;
}
.action-btn:disabled { opacity: 0.4; cursor: not-allowed; }
/* Secondary 变体：与 .btn-secondary 视觉等价（白底 + 细边框 + 主色 hover），
   仅靠 border/background 弱于 Primary，尺寸体系与 Primary 完全一致（见上方基础规则）。 */
.btn-outline { border: 1px solid var(--kf-border); background: var(--kf-card); color: var(--kf-foreground); }
.btn-outline:not(:disabled):hover { border-color: var(--kf-primary); color: var(--kf-primary); }
.btn-danger { background: var(--kf-destructive); color: var(--kf-destructive-foreground); }
.btn-warning { background: var(--kf-warning); color: var(--kf-warning-foreground); }
.btn-success { background: var(--kf-accent); color: var(--kf-accent-foreground); }

/* ========== 反馈提示 ========== */
.feedback-text { text-align: center; font-size: var(--kf-fs-body-md); font-weight: 500; color: var(--kf-muted-foreground); margin: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ========== 我的侧工具栏 ========== */
.toolbar-card {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  padding: 16px 18px;
  display: flex; flex-direction: column; gap: 14px;
}
.filter-row, .action-row {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.action-row { justify-content: space-between; }
.action-left, .action-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.kf-select, .kf-input {
  height: 38px; padding: 0 12px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md); outline: none;
  min-width: 140px;
  transition: border-color 0.15s;
}
.kf-select:focus, .kf-input:focus:not(.kf-form-control) { border-color: var(--kf-primary); }
.kf-textarea:not(.kf-form-control) {
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background); outline: none;
  transition: border-color 0.15s;
}
.kf-textarea {
  width: 100%; padding: 10px 12px;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md); line-height: 1.55;
  resize: vertical; font-family: inherit;
}
.kf-textarea:not(.kf-form-control):focus { border-color: var(--kf-primary); }

.view-switch {
  display: inline-flex; padding: 3px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
}
.view-btn {
  height: 30px; padding: 0 12px;
  border-radius: var(--kf-radius-sm);
  border: none; background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  display: inline-flex; align-items: center; gap: 5px;
  cursor: pointer; transition: all 0.15s;
}
.view-btn:hover { color: var(--kf-foreground); }
.view-btn.active {
  background: var(--kf-primary); color: var(--kf-primary-foreground);
  box-shadow: 0 2px 6px rgba(59,111,224,0.25);
}
.spinning { animation: spin 1s linear infinite; }

/* ========== 列表视图 ========== */
.list-wrap {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  overflow: hidden;
  overflow-x: auto; /* 小屏允许表格横向滚动，避免列被裁切/页面溢出 */
}
.kf-table { width: 100%; border-collapse: collapse; font-size: var(--kf-fs-body-md); }
.kf-table thead th {
  text-align: left; padding: 12px 16px;
  font-weight: 600; font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  background: var(--kf-background);
  border-bottom: 1px solid var(--kf-border);
  white-space: nowrap;
}
.kf-table tbody td { padding: 14px 16px; border-bottom: 1px solid var(--kf-border); vertical-align: top; }
.kf-table tbody tr:last-child td { border-bottom: none; }
.kf-table tbody tr:hover { background: rgba(59,111,224,0.03); }

.cell-front, .cell-back { line-height: 1.55; }
.cell-front { font-weight: 500; }
.cell-back { color: var(--kf-muted-foreground); }
.text-muted { color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
.tabular-nums { font-variant-numeric: tabular-nums; }

.mini-tags { display: inline-flex; gap: 4px; margin-right: 6px; }
.mini-tag {
  font-size: var(--kf-fs-xs); color: var(--kf-primary);
  background: rgba(59,111,224,0.1);
  border-radius: 6px; padding: 1px 6px;
}

.diff-badge {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 2px 10px; border-radius: 999px;
  font-size: var(--kf-fs-caption); font-weight: 500;
}
.diff-easy { background: rgba(34,197,94,0.12); color: #16a34a; }
.diff-medium { background: rgba(59,111,224,0.12); color: var(--kf-primary); }
.diff-hard { background: rgba(239,68,68,0.12); color: var(--kf-danger-hover); }

.source-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 9px; border-radius: 8px;
  font-size: var(--kf-fs-caption); font-weight: 500;
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
}
.src-manual { color: #0f766e; border-color: rgba(15,118,110,0.25); background: rgba(20,184,166,0.08); }
.src-ai { color: #7c3aed; border-color: rgba(124,58,237,0.25); background: rgba(139,92,246,0.1); }
.src-imp { color: var(--kf-state-warning); border-color: rgba(180,83,9,0.25); background: rgba(217,119,6,0.08); }

.row-actions { display: inline-flex; gap: 4px; flex-wrap: wrap; }
.row-btn {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 4px 10px; border-radius: 8px;
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption); cursor: pointer;
  transition: all 0.15s;
}
.row-btn:hover { border-color: var(--kf-primary); color: var(--kf-primary); }
.row-btn.danger:hover { border-color: var(--kf-destructive); color: var(--kf-destructive); }

/* ========== 卡片视图 ========== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}
.fc-card { aspect-ratio: 3 / 2; perspective: 1200px; cursor: pointer; }
.fc-card-inner {
  position: relative; width: 100%; height: 100%;
  transition: transform 0.55s cubic-bezier(.22,.9,.28,1);
  transform-style: preserve-3d;
}
.fc-card.flipped .fc-card-inner { transform: rotateY(180deg); }
.fc-face {
  position: absolute; inset: 0;
  border-radius: 18px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  padding: 18px;
  backface-visibility: hidden; -webkit-backface-visibility: hidden;
  display: flex; flex-direction: column;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02), 0 8px 24px rgba(15,23,42,0.04);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.fc-card:hover .fc-face {
  border-color: rgba(59,111,224,0.35);
  box-shadow: 0 10px 30px rgba(59,111,224,0.08);
}
.fc-face-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fc-top-actions {
  margin-left: auto;
  display: inline-flex; gap: 4px;
  opacity: 0; transition: opacity 0.2s;
}
.fc-card:hover .fc-top-actions { opacity: 1; }
.icon-btn {
  width: 28px; height: 28px;
  border-radius: 8px;
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  color: var(--kf-muted-foreground);
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all 0.15s;
}
.icon-btn:hover { color: var(--kf-primary); border-color: var(--kf-primary); }
.icon-btn.danger:hover { color: var(--kf-destructive); border-color: var(--kf-destructive); }
.fc-front-title {
  flex: 1; margin: 16px 0;
  font-size: var(--kf-fs-h4); font-weight: 600; line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fc-back-content {
  flex: 1; margin: 14px 0;
  font-size: 14.5px; line-height: 1.75;
  color: var(--kf-foreground);
  white-space: pre-wrap; word-break: break-word;
  overflow: auto;
}
.fc-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.fc-tag {
  font-size: var(--kf-fs-caption); color: var(--kf-primary);
  background: rgba(59,111,224,0.1);
  padding: 2px 8px; border-radius: 999px;
}
.fc-face-bottom {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding-top: 12px;
  border-top: 1px dashed var(--kf-border);
}
.fc-flip-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 6px 12px; border-radius: 999px;
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  color: var(--kf-foreground);
  font-size: 12.5px;
  cursor: pointer; transition: all 0.15s;
}
.fc-flip-btn:hover {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  border-color: var(--kf-primary);
}
.fc-meta {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12.5px; color: var(--kf-muted-foreground);
}
.kf-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: 6px;
  font-size: var(--kf-fs-caption);
  background: rgba(59,111,224,0.1); color: var(--kf-primary);
}
.fc-back { transform: rotateY(180deg); }

/* ========== 弹窗 ========== */
.modal-mask {
  position: fixed; inset: 0;
  background: rgba(15,23,42,0.45);
  backdrop-filter: blur(2px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000; padding: 20px;
  animation: fade-in 0.15s ease;
}
@keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }
.modal {
  width: 100%;
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: 18px;
  box-shadow: 0 20px 60px rgba(15,23,42,0.25);
  display: flex; flex-direction: column;
  max-height: 90vh;
  animation: pop-in 0.18s ease;
}
@keyframes pop-in {
  from { opacity: 0; transform: translateY(8px) scale(0.98); }
  to { opacity: 1; transform: none; }
}
.modal-lg { max-width: 820px; }
.modal-header {
  padding: 18px 22px;
  border-bottom: 1px solid var(--kf-border);
  display: flex; align-items: center; justify-content: space-between;
}
.modal-header h3 {
  margin: 0; font-size: 17px; font-weight: 600;
  display: inline-flex; align-items: center; gap: 8px;
}
.modal-body { padding: 18px 22px; overflow: auto; flex: 1; }
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
}
.col-span-2 { grid-column: span 2; }
.form-item { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: var(--kf-fs-body-sm); font-weight: 500; color: var(--kf-foreground); }
.form-label .req { color: var(--kf-destructive); margin-left: 2px; }
.form-hint { margin: 4px 0 0; font-size: 12.5px; color: var(--kf-muted-foreground); }
.modal-footer {
  padding: 14px 22px;
  border-top: 1px solid var(--kf-border);
  display: flex; justify-content: flex-end; gap: 10px;
}

.seg-group {
  display: inline-flex; padding: 3px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  width: fit-content;
}
.seg-btn {
  padding: 7px 16px;
  border: none; background: transparent;
  color: var(--kf-muted-foreground);
  font-size: 13.5px;
  border-radius: 7px; cursor: pointer;
  transition: all 0.15s;
}
.seg-btn.active {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  box-shadow: 0 2px 6px rgba(59,111,224,0.25);
}

.count-stepper { display: inline-flex; align-items: center; gap: 8px; }
.stepper-btn {
  width: 34px; height: 34px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-lg); cursor: pointer;
  transition: all 0.15s;
}
.stepper-btn:hover:not(:disabled) { border-color: var(--kf-primary); color: var(--kf-primary); }
.stepper-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.stepper-input:not(.kf-form-control) { height: 34px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-background); outline: none;
}
.stepper-input {
  width: 72px;
  padding: 0 10px; text-align: center;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
  -moz-appearance: textfield;
}
.stepper-input::-webkit-outer-spin-button,
.stepper-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.stepper-hint { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }

.preview-wrap {
  display: flex; flex-direction: column; gap: 10px;
  max-height: 320px; overflow: auto; padding: 4px;
}
.preview-card {
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  border-radius: 12px;
  padding: 12px 14px;
  position: relative;
}
.preview-index {
  position: absolute; top: 10px; right: 12px;
  font-size: var(--kf-fs-xs); font-weight: 600;
  color: var(--kf-primary);
  background: rgba(59,111,224,0.1);
  padding: 2px 8px; border-radius: 999px;
}
.preview-front, .preview-back {
  font-size: 13.5px; line-height: 1.6;
  color: var(--kf-foreground);
}
.preview-front { margin-bottom: 4px; font-weight: 500; }
.preview-back { color: var(--kf-muted-foreground); }

.import-hint {
  background: var(--kf-background);
  border: 1px dashed var(--kf-border);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 14px;
  font-size: var(--kf-fs-body-sm); line-height: 1.7;
  color: var(--kf-muted-foreground);
}
.import-hint b { color: var(--kf-foreground); }
.import-hint code {
  background: rgba(59,111,224,0.1);
  color: var(--kf-primary);
  padding: 1px 6px; border-radius: 5px;
  font-size: 12.5px;
}
.sample-block {
  margin: 8px 0 0;
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  font-family: ui-monospace, Menlo, monospace;
  overflow-x: auto;
  color: var(--kf-foreground);
}
.parse-error {
  margin: 8px 0 0;
  color: var(--kf-danger-hover);
  font-size: var(--kf-fs-body-sm);
  display: inline-flex; align-items: center; gap: 5px;
}

/* ========== 交互反馈补齐（hover / active / focus-visible）==========
 * 焦点环由全局 style.css 统一覆盖（button / a / [tabindex] / [role=button]）。
 * 此处补齐：缺失的 hover 态、统一的 :active 按压反馈，以及翻转卡的可达状态。 */

/* 统一按压反馈：轻微缩放，禁用态不响应 */
.back-btn:active:not(:disabled),
.clear-btn:active,
.tab-btn:active:not(:disabled),
.btn-primary:active:not(:disabled),
.btn-secondary:active:not(:disabled),
.btn-ghost:active:not(:disabled),
.action-btn:active:not(:disabled),
.row-btn:active,
.icon-btn:active,
.view-btn:active,
.seg-btn:active,
.fc-flip-btn:active,
.stepper-btn:active:not(:disabled) {
  transform: scale(0.97);
}

/* 评分/操作按钮补 hover 态（原 btn-danger/warning/success 仅有底色，无反馈） */
.action-btn.btn-danger:not(:disabled):hover,
.action-btn.btn-warning:not(:disabled):hover,
.action-btn.btn-success:not(:disabled):hover {
  filter: brightness(1.06);
}

/* 分段控件补 hover 态（原仅有 .active 态） */
.seg-btn:not(.active):hover {
  color: var(--kf-foreground);
}

/* 可点击翻转卡（div[role=button]）：补 hover / active / focus-visible */
.flip-card:hover .flip-card-face {
  border-color: var(--kf-primary);
}
.flip-card:active {
  transform: scale(0.99);
}
.flip-card:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-radius: var(--kf-radius-lg);
}
.flip-card:focus-visible .flip-card-face {
  border-color: var(--kf-primary);
}

/* 我的闪卡卡片视图（div@click.self，已有 hover）：补按压反馈 */
.fc-card:active .fc-face {
  box-shadow: 0 4px 14px rgba(59, 111, 224, 0.06);
}

/* ========== 响应式 ========== */
@media (max-width: 900px) {
  .flashcards-hub { padding: 20px 16px 60px; }
  .hub-hero { flex-direction: column; align-items: flex-start; }
  .hero-stats { width: 100%; }
  .progress-block { flex-direction: column; align-items: stretch; }
  .progress-right { justify-content: space-around; gap: 16px; }
  .flip-card { height: 320px; }
  .flip-card-face { padding: 24px; }
  .face-question { font-size: var(--kf-fs-h3); }
  .action-bar { gap: 8px; }
  .action-btn { height: 40px; padding: 0 14px; font-size: var(--kf-fs-body-sm); }
  .form-grid { grid-template-columns: 1fr; }
  .col-span-2 { grid-column: span 1; }
  .card-grid { grid-template-columns: 1fr; }
}

/* ══════════════════════════════════════════════════════════════════
   闪卡学习工作台（本次新增）—— 字号/圆角一律走 --kf-fs-* / --kf-radius-*
   ══════════════════════════════════════════════════════════════════ */

/* §十八 键盘快捷键轻量提示（仅会话中显示，不长期占位） */
.kf-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 auto 8px;
  padding: 4px 10px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
  width: fit-content;
}
.kf-hint svg {
  flex-shrink: 0;
}

/* §二十二 骨架闪卡（替代空白 + Loading…） */
.skeleton-card {
  width: 100%;
  max-width: 640px;
  min-height: 300px;
  padding: 28px;
  border-radius: var(--kf-radius-lg, 16px);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sk-line {
  border-radius: var(--kf-radius-sm);
  background: linear-gradient(90deg, var(--kf-muted) 25%, var(--kf-border) 37%, var(--kf-muted) 63%);
  background-size: 400% 100%;
  animation: kf-skeleton 1.4s ease infinite;
}
.sk-tag { height: 20px; width: 128px; }
.sk-title { height: 28px; width: 72%; margin-top: 8px; }
.sk-body { height: 14px; width: 100%; }
.sk-body.short { width: 56%; }
@keyframes kf-skeleton {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
@media (prefers-reduced-motion: reduce) {
  .sk-line { animation: none; }
}

/* §二十三 错误态图标 */
.state-icon-error {
  color: var(--kf-destructive);
  opacity: 1;
}

/* §二十三 提交失败提示条（不静默失败，可重试） */
.rate-error {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 640px;
  margin: 12px auto 0;
  padding: 10px 12px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-destructive);
  background: var(--kf-destructive-subtle, rgba(220, 38, 38, 0.06));
  color: var(--kf-destructive);
  font-size: var(--kf-fs-body-sm);
}
.rate-error svg {
  flex-shrink: 0;
}
.rate-error-text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rate-error-text b {
  color: var(--kf-foreground);
  font-weight: 600;
}
.btn-sm {
  height: 32px;
  padding: 0 10px;
  font-size: var(--kf-fs-body-sm);
  flex-shrink: 0;
}

/* §十五 「为什么是这张卡」徽章（轻量，最多两条） */
.tag-why {
  font-weight: 600;
}
.tag-why-due { background: rgba(245, 158, 11, 0.1); color: var(--kf-warning); }
.tag-why-next { background: rgba(59, 111, 224, 0.12); color: var(--kf-primary); }
.tag-why-weak { background: rgba(220, 38, 38, 0.1); color: var(--kf-destructive); }
.tag-why-path { background: var(--kf-muted); color: var(--kf-foreground); }
.tag-why-new { background: rgba(16, 185, 129, 0.1); color: var(--kf-accent); }

/* §九 答案面：关联知识点 / KnowledgeId（真实绑定值，无上下文则不渲染） */
.back-tag-kb {
  background: var(--kf-muted);
  color: var(--kf-foreground);
  font-variant-numeric: tabular-nums;
}

/* §二十 学习完成页：真实自评分布 + 真实 Recall 表现 + 下一步动作 */
.session-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
.ss-stats {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}
.ss-item {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.ss-item b {
  font-weight: 700;
  margin-left: 2px;
}
.ss-mastered b { color: var(--kf-accent); }
.ss-familiar b { color: var(--kf-warning); }
.ss-unknown b { color: var(--kf-destructive); }
.ss-line {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.ss-note {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
}
.ss-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

/* ══════════════════════════════════════════════════════════════════
   §二十八 超窄屏（≤480px）—— 必须放在所有 width 媒体查询之后，
   否则会被上面 max-width:900px 的 .action-btn 规则按「后者胜出」覆盖。
   ══════════════════════════════════════════════════════════════════ */
@media (max-width: 480px) {
  /* Tab：去掉图标 + 收紧内边距，让三个标签在 390px 下完整单行显示 */
  .tab-btn { padding: 8px 10px; gap: var(--kf-space-2); font-size: var(--kf-fs-caption); }
  .tab-btn svg { display: none; }
  .tab-count { min-width: 18px; height: 18px; padding: 0 4px; }
  /* 操作条保持单行，避免「下一张」被挤到第二行造成视觉断层 */
  .action-bar { gap: 6px; flex-wrap: nowrap; }
  .action-btn { padding: 0 10px; font-size: var(--kf-fs-caption); white-space: nowrap; }
}

/* ══════════ UI/UX 5.1 §九：准备态（准备 → 学习 → 反馈 → 完成）══════════
 * 目标（§二十六）：用户 3 秒内知道「我在做什么 / 为什么做 / 下一步是什么」。
 * 采用「Page → Section」而非 Card 套 Card（§十三），全部走 --kf-* 令牌（§二十五）。
 */
.prep-panel {
  display: flex;
  justify-content: flex-start;
  padding: 0;
}
.flip-card {
  animation: flashcard-arrive 240ms ease-out;
}
@keyframes flashcard-arrive {
  from { opacity: 0; transform: translateY(var(--kf-space-3)) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .flip-card { animation: none; }
  .flip-card-inner { transition: none; }
}
.prep-card {
  width: 100%;
  max-width: none;
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  /* P2-UI-Flashcards：24px → 20/22，收紧上下留白；桌面端仍保留呼吸感，不做过度压缩 */
  padding: var(--kf-space-4);
  display: flex;
  flex-direction: column;
  /* 10px → 8px：层级之间更紧凑，但不贴身 */
  gap: 8px;
}
.prep-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  align-self: flex-start;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
  font-size: var(--kf-fs-caption);
  font-weight: 600;
}
/* 第一层：本次要复习多少张（唯一的主信息，字号走 --kf-fs-h4 令牌，不新增字号） */
.prep-title {
  margin: 0;
  font-size: var(--kf-fs-h4);
  font-weight: 700;
  line-height: 1.35;
  color: var(--kf-foreground);
}
.prep-title b {
  color: var(--kf-primary);
}
/* 第二层：来源 */
.prep-source {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.prep-source b {
  color: var(--kf-foreground);
  font-weight: 600;
}
/* 第三层：说明 / 数据（分割线以上是「学什么」，以下是「为什么」） */
.prep-meta {
  margin: 0;
  padding: 10px 0 0;
  border-top: 1px solid var(--kf-border);
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.prep-meta li {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.prep-meta li svg {
  flex-shrink: 0;
}
.prep-meta li b {
  color: var(--kf-foreground);
}
/* 第四层：Action Group —— 两个按钮等高、等圆角、等图标间距，间距 10px（8~12 区间） */
.prep-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}
/* 第五层：快捷键提示 —— 只做操作区下方的辅助信息，不抢按钮权重 */
.prep-hint {
  margin: 4px 0 0;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
@media (max-width: 480px) {
  .prep-card { padding: 18px; }
  .prep-actions { gap: 8px; }
  .prep-actions button { flex: 1 1 auto; justify-content: center; }
}
/* Phase C: opt-in session layout, no global learning-page overrides. */
.phase-c-flashcards .prep-title { font-family:inherit;font-size:var(--kf-section-title-size); }
.flip-card-face { border-width:var(--kf-studio-rule); }
.flip-card-back { border-color:var(--kf-border); }
.face-question { max-width:var(--kf-notes-reading-width);font-size:var(--kf-fs-h4);line-height:1.65;text-align:left;font-weight:500;word-break:normal;overflow-wrap:anywhere; }
.back-text { font-size:var(--kf-fs-body-lg);line-height:1.75;white-space:normal;overflow-wrap:anywhere; }
.markdown-body :deep(table) { display:block;max-width:100%;overflow-x:auto; }
.markdown-body :deep(pre) { max-width:100%;min-width:0; }
.action-bar { width:min(100%,var(--kf-notes-reading-width));margin-inline:auto;gap:var(--kf-space-2); }
.action-bar .fsrs-rating { display:flex;flex-direction:column;justify-content:center;gap:var(--kf-space-1);height:auto;line-height:1.4;flex:1;min-width:0;min-height:var(--kf-control-hit-size);white-space:normal;text-align:center; }
.action-bar .fsrs-rating small { color:inherit;font-size:var(--kf-fs-caption);line-height:1.4; }
.action-bar button:focus-visible { outline:var(--kf-studio-focus) solid var(--kf-ring);outline-offset:var(--kf-studio-focus); }
@media(max-width:640px){
 .action-bar { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--kf-space-2); }
 .action-bar .btn-ghost-icon { grid-row:3;width:100%; }
 .action-bar .fsrs-rating { padding:var(--kf-space-2); }
 .action-bar > .btn-primary:not(.fsrs-rating) { grid-column:1 / -1;grid-row:1; }
 .flip-card-face { padding:var(--kf-space-4); }
 .flip-card { height:clamp(18rem,50dvh,32rem); }
 .face-question { font-size:var(--kf-fs-body-lg); }
 .back-title { font-size:var(--kf-fs-body-md); }
}
/* V3: local reading stage; recommendation cards retain their original layout. */
.flashcard-stage { width:min(100%,calc(var(--kf-space-8) * 30));min-width:0;margin:var(--kf-space-4) auto; }
.session-mode { color:var(--kf-foreground);font-size:var(--kf-fs-body-sm);font-weight:600;white-space:nowrap; }
.session-status { display:flex;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:var(--kf-space-3);font-size:var(--kf-fs-caption);color:var(--kf-muted-foreground); }
.session-position,.session-receipt { margin:0 0 var(--kf-space-3);font-size:var(--kf-fs-caption);color:var(--kf-muted-foreground);overflow-wrap:anywhere; }
.session-receipt { color:var(--kf-foreground); }
.flashcard-stage .flip-card { height:auto;max-width:none;animation:none; }
.flashcard-stage .flip-card-inner { height:auto;transition:transform 220ms ease; }
.flashcard-stage .flip-card-face { position:relative;inset:auto;min-height:calc(var(--kf-space-8) * 8);height:auto;padding:var(--kf-space-6);overflow:visible;align-items:stretch;gap:var(--kf-space-4);border-color:var(--kf-border); }
.flashcard-stage .flip-card-face[aria-hidden="true"] { position:absolute;inset:0;visibility:hidden;overflow:hidden;pointer-events:none; }
.flashcard-stage .face-tags { justify-content:flex-start;margin:0;gap:var(--kf-space-2); }
.flashcard-stage .face-tag,.flashcard-stage .back-tag { padding:var(--kf-space-1) var(--kf-space-2);background:var(--kf-muted);color:var(--kf-muted-foreground);font-size:var(--kf-fs-caption);border-radius:var(--kf-radius-sm); }
.flashcard-stage .face-question { width:100%;max-width:none;margin:0;font-size:var(--kf-fs-h3);font-weight:600;line-height:1.6; }
.flashcard-stage .face-hint { text-align:left;margin:0;font-size:var(--kf-fs-body-sm); }
.flashcard-stage .flip-pill { align-self:flex-start;margin-top:auto;padding:var(--kf-space-2) var(--kf-space-3);border-radius:var(--kf-radius-sm);font-size:var(--kf-fs-caption); }
.flashcard-stage .back-header { margin:0;color:var(--kf-muted-foreground); }
.flashcard-stage .back-label,.flashcard-stage .back-icon { color:var(--kf-muted-foreground); }
.flashcard-stage .back-body { flex:initial;min-width:0;overflow:visible; }
.flashcard-stage .back-title { font-size:var(--kf-fs-body-lg);font-weight:600;line-height:1.6;overflow-wrap:anywhere; }
.flashcard-stage .back-text { margin-block:var(--kf-space-4);font-size:var(--kf-fs-body-lg);line-height:1.8; }
.flashcard-stage .back-text :deep(pre) { overflow-x:auto;overscroll-behavior-x:contain; }
.flashcard-stage .markdown-body :deep(ul) { list-style:disc;padding-inline-start:var(--kf-space-6);margin-block:var(--kf-space-3); }
.flashcard-stage .markdown-body :deep(ol) { list-style:decimal;padding-inline-start:var(--kf-space-6);margin-block:var(--kf-space-3); }
.flashcard-stage .markdown-body :deep(li + li) { margin-top:var(--kf-space-2); }
.flashcard-stage .markdown-body :deep(p + p) { margin-top:var(--kf-space-3); }
.flashcard-stage .markdown-body :deep(h1),.flashcard-stage .markdown-body :deep(h2) { font-size:var(--kf-fs-h3);font-weight:600;margin-block:var(--kf-space-4) var(--kf-space-3);line-height:1.5; }
.flashcard-stage .markdown-body :deep(h3),.flashcard-stage .markdown-body :deep(h4),.flashcard-stage .markdown-body :deep(h5),.flashcard-stage .markdown-body :deep(h6) { font-size:var(--kf-fs-body-lg);font-weight:600;margin-block:var(--kf-space-4) var(--kf-space-2);line-height:1.6; }
.flashcard-stage .markdown-body :deep(table) { margin-block:var(--kf-space-4);border-collapse:collapse; }
.flashcard-stage .markdown-body :deep(th),.flashcard-stage .markdown-body :deep(td) { border:var(--kf-studio-rule) solid var(--kf-border);padding:var(--kf-space-2) var(--kf-space-3);text-align:left; }
.flashcard-stage .markdown-body :deep(th) { font-weight:600;background:var(--kf-background); }
.flashcard-stage .gesture-handle { position:relative;inset:auto;margin-top:auto;touch-action:none;user-select:none;padding:var(--kf-space-3);background:var(--kf-background);border:var(--kf-studio-rule) dashed var(--kf-border); }
.flashcard-stage .drag-label { top:auto;bottom:100%;right:0;max-width:100%;pointer-events:none; }
.flashcard-stage .session-help { margin-top:var(--kf-space-4);font-size:var(--kf-fs-caption);color:var(--kf-muted-foreground);line-height:1.7; }
.session-help summary { cursor:pointer;width:fit-content;padding-block:var(--kf-space-2); }
.session-help p { margin-block:var(--kf-space-2); }
.session-help summary:focus-visible { outline:var(--kf-studio-focus) solid var(--kf-ring);outline-offset:var(--kf-studio-focus); }
.action-bar { align-items:center; }
.action-bar .fsrs-rating { --rating-tone:var(--kf-foreground);background:color-mix(in srgb,var(--rating-tone) 7%,var(--kf-card));color:var(--kf-foreground);border:var(--kf-studio-rule) solid color-mix(in srgb,var(--rating-tone) 24%,var(--kf-border));border-top:var(--kf-space-1) solid color-mix(in srgb,var(--rating-tone) 60%,var(--kf-border));min-height:calc(var(--kf-control-hit-size) + var(--kf-space-4));padding:var(--kf-space-2) var(--kf-space-3); }
.action-bar .rating-1 { --rating-tone:var(--kf-destructive); }
.action-bar .rating-2 { --rating-tone:var(--kf-warning); }
.action-bar .rating-3 { --rating-tone:var(--kf-accent); }
.action-bar .rating-4 { --rating-tone:var(--kf-primary); }
.action-bar .fsrs-rating:not(:disabled):hover { background:color-mix(in srgb,var(--rating-tone) 14%,var(--kf-card));border-color:var(--rating-tone); }
.action-bar .fsrs-rating:not(:disabled):active { background:color-mix(in srgb,var(--rating-tone) 20%,var(--kf-card)); }
.action-bar .fsrs-rating:disabled { opacity:.6; }
.action-bar .fsrs-rating[aria-busy="true"] { opacity:1;cursor:progress; }
.rating-title { display:flex;align-items:center;justify-content:center;gap:var(--kf-space-2);font-weight:600; }
.rating-title kbd { font:inherit;font-size:var(--kf-fs-caption);font-weight:400;color:var(--kf-muted-foreground); }
.action-bar .fsrs-rating small { color:var(--kf-muted-foreground); }
@media(max-width:640px) {
 .flashcard-stage { margin-block:0; }
 .flashcard-stage .flip-card-face { padding:var(--kf-space-4);min-height:calc(var(--kf-space-8) * 7);gap:var(--kf-space-3); }
 .flashcard-stage .face-question { font-size:var(--kf-fs-h4); }
 .flashcard-stage .back-text { font-size:var(--kf-fs-body-md); }
 .session-status { justify-content:space-between;gap:var(--kf-space-2); }
 .action-bar .fsrs-rating { min-height:calc(var(--kf-control-hit-size) + var(--kf-space-3)); }
}
@media(prefers-reduced-motion:reduce) { .flashcard-stage .flip-card-inner { transition:none; } }
/* V4 visual reference: local geometry and semantic colors, original data contracts. */
.review-brand { display:inline-flex;align-items:center;gap:var(--kf-space-2);padding-right:var(--kf-space-6);border-right:var(--kf-studio-rule) solid var(--kf-border);font-size:var(--kf-fs-h3);font-weight:750;letter-spacing:-.04em;color:var(--kf-foreground);white-space:nowrap; }
.review-brand > svg { font-size:var(--kf-space-8);color:var(--kf-primary); }
.session-statistics { display:inline-flex;align-items:center;gap:var(--kf-space-2);white-space:nowrap; }
.session-statistics b { font-weight:600;color:var(--kf-foreground); }
.review-settings { display:inline-flex;align-items:center;justify-content:center;gap:var(--kf-space-2);min-height:var(--kf-control-hit-size);padding:var(--kf-space-2) var(--kf-space-3);border-radius:var(--kf-radius-lg);background:var(--kf-background); }
.flashcard-stage { width:min(100%,calc(var(--kf-space-8) * 37));margin-block:auto;flex-shrink:0; }
.session-position { font-size:var(--kf-fs-caption);margin-bottom:var(--kf-space-3); }
.flashcard-stage .flip-card-face { border-radius:var(--kf-space-6);border:var(--kf-studio-rule) solid color-mix(in srgb,var(--kf-border) 70%,var(--kf-card));padding:calc(var(--kf-space-8) + var(--kf-space-2));gap:var(--kf-space-6);min-height:calc(var(--kf-space-8) * 14);box-shadow:0 var(--kf-space-3) var(--kf-space-8) color-mix(in srgb,var(--kf-primary) 8%,transparent); }
.flashcard-stage .flip-card-front { min-height:calc(var(--kf-space-8) * 12); }
.card-front-label { display:flex;align-items:center;gap:var(--kf-space-3);color:var(--kf-muted-foreground);font-size:var(--kf-fs-body-md); }
.flashcard-stage .face-question,.flashcard-stage .back-title { font-size:calc(var(--kf-fs-h4) + var(--kf-space-2));font-weight:700;line-height:1.5; }
.flashcard-stage .back-header { gap:var(--kf-space-1); }
.flashcard-stage .back-icon { box-sizing:content-box;width:var(--kf-space-6);height:var(--kf-space-6);padding:var(--kf-space-3);border-radius:50%;background:color-mix(in srgb,var(--kf-warning) 9%,var(--kf-card));color:var(--kf-state-warning); }
.flashcard-stage .back-label { font-size:var(--kf-fs-body-md);font-weight:600;padding:var(--kf-space-2) var(--kf-space-4);border-radius:var(--kf-space-6);background:color-mix(in srgb,var(--kf-warning) 9%,var(--kf-card));color:var(--kf-state-warning); }
.flashcard-stage .back-title { margin:0 0 var(--kf-space-6); }
.flashcard-stage .back-text { font-size:calc(var(--kf-fs-body-lg) + var(--kf-space-1) / 2);line-height:1.8;margin:0 0 var(--kf-space-6); }
.flashcard-stage .back-tags { border-top:var(--kf-studio-rule) solid var(--kf-border);padding-top:var(--kf-space-6);align-items:center;gap:var(--kf-space-3); }
.tag-heading { display:inline-flex;align-items:center;gap:var(--kf-space-3);margin-right:var(--kf-space-2);font-size:var(--kf-fs-body-sm);color:var(--kf-muted-foreground); }
.tag-heading > svg { font-size:var(--kf-space-6); }
.session-mobile-statistics { display:none; }
.flashcard-stage .face-tag,.flashcard-stage .back-tag { border-radius:var(--kf-space-6);padding:var(--kf-space-2) var(--kf-space-4);font-size:var(--kf-fs-body-sm); }
.flashcard-stage .tag-category,.flashcard-stage .back-tag-kb { background:var(--kf-primary-soft);color:var(--kf-state-info); }
.flashcard-stage .tag-sub { background:color-mix(in srgb,var(--kf-destructive) 8%,var(--kf-card));color:var(--kf-state-error); }
.flashcard-stage .gesture-handle { display:flex;align-items:center;justify-content:center;flex-wrap:wrap;gap:var(--kf-space-3);border:0;border-radius:var(--kf-radius-md);padding:var(--kf-space-4);background:color-mix(in srgb,var(--kf-muted) 55%,var(--kf-card));font-size:var(--kf-fs-body-sm);line-height:1.6; }
.gesture-handle > svg { font-size:var(--kf-space-6); }
.flashcard-stage .session-help { margin-top:var(--kf-space-2); }
.action-bar { width:min(100%,calc(var(--kf-space-8) * 37));gap:var(--kf-space-3);flex-wrap:nowrap; }
.action-bar .btn-ghost-icon { flex:none;justify-content:center;width:calc(var(--kf-space-8) * 1.75);height:calc(var(--kf-space-8) * 1.75);padding:0;border:var(--kf-studio-rule) solid var(--kf-border);border-radius:var(--kf-radius-lg);background:var(--kf-background);color:var(--kf-muted-foreground); }
.action-bar .fsrs-rating { --review-fill:var(--kf-primary);display:flex;flex-direction:row;align-items:center;justify-content:center;gap:var(--kf-space-4);min-height:calc(var(--kf-space-8) * 2 + var(--kf-space-3));border:var(--kf-studio-rule) solid color-mix(in srgb,var(--review-fill) 80%,var(--kf-card));border-radius:var(--kf-radius-lg);background:var(--review-fill);color:var(--kf-card);padding:var(--kf-space-3) var(--kf-space-4);box-shadow:0 var(--kf-space-2) var(--kf-space-4) color-mix(in srgb,var(--review-fill) 15%,transparent); }
.action-bar .rating-1 { --review-fill:var(--kf-state-error); }
.action-bar .rating-2 { --review-fill:var(--kf-state-warning); }
.action-bar .rating-3 { --review-fill:var(--kf-state-success); }
.action-bar .rating-4 { --review-fill:var(--kf-primary); }
.action-bar .fsrs-rating .rating-icon { width:var(--kf-space-8);height:var(--kf-space-8);font-size:var(--kf-space-8);flex-shrink:0; }
.rating-copy { display:flex;flex-direction:column;align-items:flex-start;gap:var(--kf-space-1);min-width:0; }
.rating-title { justify-content:flex-start;font-size:var(--kf-fs-body-lg);font-weight:600;overflow-wrap:anywhere; }
.action-bar .fsrs-rating small { font-size:var(--kf-fs-body-sm);color:inherit;line-height:1.4; }
.action-bar .fsrs-rating:not(:disabled):hover { background:color-mix(in srgb,var(--review-fill) 90%,var(--kf-foreground));border-color:var(--review-fill); }
.action-bar .fsrs-rating:not(:disabled):active { background:color-mix(in srgb,var(--review-fill) 82%,var(--kf-foreground)); }
.action-bar .fsrs-rating:disabled { opacity:.55;box-shadow:none; }
.action-bar .fsrs-rating[aria-busy="true"] { opacity:1; }
.is-saving { animation:review-saving 1s linear infinite; }
@keyframes review-saving { to { transform:rotate(360deg); } }
@media(max-width:1100px) {
 .daily-statistics { display:none; }
 .flashcard-stage .flip-card-face { padding:var(--kf-space-6); }
 .action-bar .fsrs-rating { gap:var(--kf-space-2);padding-inline:var(--kf-space-3); }
 .action-bar .rating-title { font-size:var(--kf-fs-body-sm); }
}
@media(max-width:640px) {
 .review-brand,.session-statistics { display:none; }
 .session-mobile-statistics { display:block;margin-bottom:var(--kf-space-3);color:var(--kf-muted-foreground);font-size:var(--kf-fs-caption);line-height:1.6; }
 .session-mobile-statistics summary { cursor:pointer;padding-block:var(--kf-space-2); }
 .session-mobile-statistics summary:focus-visible { outline:var(--kf-studio-focus) solid var(--kf-ring); }
 .review-settings > span { display:none; }
 .review-settings { width:var(--kf-control-hit-size);padding:var(--kf-space-2); }
 .flashcard-stage { margin-block:0; }
 .flashcard-stage .flip-card-face { min-height:calc(var(--kf-space-8) * 9);padding:var(--kf-space-5,var(--kf-space-4));gap:var(--kf-space-4);border-radius:var(--kf-radius-lg); }
 .flashcard-stage .face-question,.flashcard-stage .back-title { font-size:var(--kf-fs-h3); }
 .flashcard-stage .back-text { font-size:var(--kf-fs-body-lg); }
 .flashcard-stage .back-tags { padding-top:var(--kf-space-4);gap:var(--kf-space-2); }
 .tag-heading { width:100%; }
 .flashcard-stage .gesture-handle { padding:var(--kf-space-3);font-size:var(--kf-fs-caption); }
 .action-bar { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--kf-space-2); }
 .action-bar .fsrs-rating { min-height:calc(var(--kf-space-8) * 2);padding:var(--kf-space-2);gap:var(--kf-space-2); }
 .action-bar .rating-title { font-size:var(--kf-fs-body-sm); }
 .action-bar .btn-ghost-icon { width:100%;height:var(--kf-control-hit-size);border-radius:var(--kf-radius-md); }
}
@media(prefers-reduced-motion:reduce) { .is-saving { animation:none; } }
</style>
