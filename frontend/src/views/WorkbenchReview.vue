<template>
  <WorkbenchShell stage="review" :revision="revision" @ready="initialize">
    <template #actions>
      <button data-test="review-create" class="kb-btn" :class="{ 'kb-btn-active': dialogOpen }" @click="openCreate()">
        <Icon name="pencil" :size="16" aria-hidden="true" />新建复习卡
      </button>
      <button data-test="review-start" class="kb-btn kb-btn-primary" :disabled="loading || !!error || active || grading || !dueCards.length" @click="startReview(false)">
        开始到期复习 · {{ dueCards.length }}
      </button>
    </template>
    <WorkbenchReviewMethods active="spaced-review" />

    <div v-if="noteId" class="workbench-panel workbench-toolbar">
      <span>当前笔记：{{ sourceNote?.title || '正在读取来源' }}</span>
      <RouterLink :to="'/workbench/notes/' + noteId" class="kb-btn">补充笔记</RouterLink>
      <RouterLink to="/workbench/review" class="kb-btn">查看全部复习卡</RouterLink>
      <p v-if="sourceError" role="status">{{ sourceError }}</p>
    </div>

    <div v-if="error" role="alert" class="workbench-inline-error">{{ error }}<button class="kb-btn" @click="load">重试</button></div>
    <div v-else-if="loading" class="workbench-empty" role="status">正在加载复习队列…</div>

    <template v-else>
      <section class="review-stats" aria-label="复习进度">
        <div class="review-stat"><span class="review-stat-label">今日到期</span><strong class="review-stat-value">{{ dueCards.length }}</strong><small>张等待复习</small></div>
        <div class="review-stat"><span class="review-stat-label">全部卡片</span><strong class="review-stat-value">{{ cards.length }}</strong><small>张已创建</small></div>
        <div class="review-stat"><span class="review-stat-label">本次已复习</span><strong class="review-stat-value">{{ completedCount }}</strong><small>{{ active ? '张本轮完成' : '张最近一轮' }}</small></div>
        <div class="review-stat"><span class="review-stat-label">下一次复习</span><strong class="review-stat-value review-stat-value--time">{{ nextUpcomingText }}</strong><small>{{ nextUpcoming ? '最近到期卡片' : '暂无排程' }}</small></div>
      </section>

      <section v-if="active && current" data-test="review-session" class="workbench-panel">
        <header class="workbench-detail-head">
          <div class="review-session-heading">
            <span class="review-type-badge" :class="'is-' + currentType.toLowerCase()">{{ typeLabel(current) }}</span>
            <h2 class="kb-text-section-title">先回忆，再看答案</h2>
          </div>
          <div class="review-session-tools">
            <span>{{ index + 1 }} / {{ queue.length }}</span>
            <button data-test="review-pause" class="kb-btn" :disabled="grading" @click="pauseReview">暂停 / 退出</button>
          </div>
        </header>
        <progress class="workbench-progress" :value="completedCount" :max="queue.length" aria-label="本轮复习进度"></progress>

        <div :key="current.id" class="workbench-session workbench-study-face">
          <!-- 问答卡：问题 → 显示答案 → 自评 -->
          <template v-if="currentType === 'BASIC'">
            <span class="workbench-badge">问题</span>
            <p class="workbench-study-text">{{ current.front }}</p>
            <button v-if="current.hint && !showHint" class="review-hint-toggle" @click="showHint = true">查看提示</button>
            <p v-if="showHint && current.hint" class="review-hint">提示：{{ current.hint }}</p>
            <template v-if="!revealed">
              <button data-test="review-reveal" class="kb-btn kb-btn-primary" @click="reveal">显示答案</button>
            </template>
            <template v-else>
              <hr class="kb-divider" />
              <span class="workbench-badge">标准答案</span>
              <p class="workbench-preview">{{ current.back }}</p>
              <p class="workbench-muted">按实际回忆情况自评，系统将安排下次复习。</p>
              <div class="workbench-grades">
                <button v-for="choice in grades" :key="choice.value" :data-quality="choice.value" class="kb-btn" :class="{ 'kb-btn-primary': choice.value === 3 }" :disabled="grading" @click="grade(choice.value)">{{ grading ? '提交中…' : choice.label }}</button>
              </div>
            </template>
          </template>

          <!-- 挖空卡：带空格的句子 + 逐空输入（本地文本比对）→ 显示答案 → 自评 -->
          <template v-else-if="currentType === 'CLOZE'">
            <span class="workbench-badge">挖空</span>
            <p class="workbench-study-text review-cloze-text">{{ currentCloze.masked }}</p>
            <div v-if="currentCloze.blanks.length" class="review-cloze-fields">
              <label v-for="(blank, i) in currentCloze.blanks" :key="i" class="review-cloze-field">
                <span>空 {{ i + 1 }}</span>
                <input v-model="clozeAnswers[i]" class="kb-input" :placeholder="revealed ? blank.answer : '回忆缺失内容…'" :disabled="revealed || grading" />
              </label>
            </div>
            <p v-else class="workbench-muted">这张挖空卡没有可识别的挖空标记，请编辑卡片补充。</p>
            <button v-if="current.hint && !showHint" class="review-hint-toggle" @click="showHint = true">查看提示</button>
            <p v-if="showHint && current.hint" class="review-hint">提示：{{ current.hint }}</p>
            <template v-if="!revealed">
              <button data-test="review-reveal" class="kb-btn kb-btn-primary" @click="reveal">显示答案</button>
            </template>
            <template v-else>
              <hr class="kb-divider" />
              <span class="workbench-badge">完整内容</span>
              <p class="workbench-preview review-cloze-full">
                <template v-for="(segment, i) in currentCloze.segments" :key="i"><mark v-if="segment.answer" class="review-cloze-answer">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template>
              </p>
              <p v-if="clozeAnswers.some((answer, i) => (answer || '').trim() && (answer || '').trim() !== currentCloze.blanks[i]?.answer.trim())" class="workbench-muted review-compare">本地文本比对：部分输入与挖空答案不完全一致，请核对。此处仅做文本比对，不自动评分。</p>
              <p v-else-if="clozeAnswers.some(answer => (answer || '').trim())" class="workbench-muted review-compare">本地文本比对：已填写的挖空与答案一致。此处仅做文本比对，不自动评分。</p>
              <p v-if="current.back" class="workbench-preview">{{ current.back }}</p>
              <p class="workbench-muted">按实际回忆情况自评，系统将安排下次复习。</p>
              <div class="workbench-grades">
                <button v-for="choice in grades" :key="choice.value" :data-quality="choice.value" class="kb-btn" :class="{ 'kb-btn-primary': choice.value === 3 }" :disabled="grading" @click="grade(choice.value)">{{ grading ? '提交中…' : choice.label }}</button>
              </div>
            </template>
          </template>

          <!-- 主动回忆卡：任务 → 复述 → 核对要点 → 参考讲解 → 自评 -->
          <template v-else>
            <span class="workbench-badge">主动回忆</span>
            <p class="workbench-study-text">{{ current.front }}</p>
            <button v-if="current.hint && !showHint" class="review-hint-toggle" @click="showHint = true">查看提示</button>
            <p v-if="showHint && current.hint" class="review-hint">提示：{{ current.hint }}</p>
            <textarea v-model="recallDraft" class="kb-input kb-textarea review-recall-draft" rows="5" placeholder="不看资料，写下你的复述。内容仅保留在本次会话，不写入复习记录。" :disabled="revealed || grading"></textarea>
            <template v-if="!recallCheckpointsShown">
              <button data-test="review-reveal" class="kb-btn kb-btn-primary" @click="recallCheckpointsShown = true">完成回忆，查看要点</button>
            </template>
            <template v-else>
              <div v-if="currentCheckpoints.length" class="review-checkpoints">
                <span class="workbench-badge">关键检查要点</span>
                <p class="workbench-muted">逐项核对并勾选。勾选只代表本次自检，不自动计入掌握度。</p>
                <Checkbox v-for="(point, i) in currentCheckpoints" :key="i" v-model="checkedCheckpoints[i]" :label="point" />
              </div>
              <p v-else class="workbench-muted">这张卡没有登记检查要点，可直接查看参考讲解。</p>
              <template v-if="!revealed">
                <button data-test="review-reveal-answer" class="kb-btn kb-btn-primary" @click="reveal">查看参考讲解</button>
              </template>
              <template v-else>
                <hr class="kb-divider" />
                <span class="workbench-badge">参考讲解</span>
                <p class="workbench-preview">{{ current.back || '暂无参考讲解。' }}</p>
                <p class="workbench-muted">根据复述和要点核对情况自评，系统将安排下次复习。</p>
                <div class="workbench-grades">
                  <button v-for="choice in grades" :key="choice.value" :data-quality="choice.value" class="kb-btn" :class="{ 'kb-btn-primary': choice.value === 3 }" :disabled="grading" @click="grade(choice.value)">{{ grading ? '提交中…' : choice.label }}</button>
                </div>
              </template>
            </template>
          </template>

          <RouterLink v-if="current.noteId" :to="'/workbench/notes/' + current.noteId" class="workbench-source-link">查看关联笔记</RouterLink>
        </div>
      </section>

      <section v-else-if="sessionCompleted" data-test="review-complete" class="workbench-panel workbench-state" aria-live="polite">
        <Icon name="check-circle" :size="32" />
        <h2 class="kb-text-section-title">本轮复习完成</h2>
        <p>已完成 {{ completedCount }} 张，反馈已保存。下次复习按新的排程进行。</p>
        <p v-if="dueCards.length" class="workbench-muted">还有 {{ dueCards.length }} 张到期卡片，可以开始下一轮。</p>
        <RouterLink v-if="noteId" :to="'/workbench/notes/' + noteId" class="kb-btn">返回关联笔记</RouterLink>
        <RouterLink to="/workbench/story" class="kb-btn">去输出</RouterLink>
      </section>

      <section v-else-if="!dueCards.length" class="workbench-panel workbench-empty">
        <h2 class="kb-text-section-title">{{ cards.length ? '今天的复习已完成' : '还没有复习卡' }}</h2>
        <p>{{ cards.length ? '没有到期的卡片。也可以自主练习，或继续整理和输出。' : '把笔记中的问题整理成卡片，也可以直接新建。' }}</p>
      </section>

      <p v-if="lastResult" class="workbench-muted" aria-live="polite">上张反馈已保存：下次 {{ lastResult.intervalDay }} 天后复习{{ lastResult.lapsed ? '，本次遗忘已记录' : '' }}。</p>

      <div class="review-tabs" role="tablist" aria-label="复习范围">
        <button v-for="tab in tabs" :key="tab.value" role="tab" class="review-tab" :class="{ active: mode === tab.value }" :aria-selected="mode === tab.value" @click="mode = tab.value">{{ tab.label }}<span v-if="tab.count !== undefined">{{ tab.count }}</span></button>
        <RouterLink to="/workbench/recall" class="review-tab review-tab-link">主动回忆<Icon name="brain" :size="14" aria-hidden="true" /></RouterLink>
      </div>

      <div class="review-filters">
        <label class="review-search">
          <Icon name="search" :size="16" aria-hidden="true" />
          <input v-model="keyword" class="kb-input" type="search" placeholder="搜索问题、答案或标签…" aria-label="搜索复习卡" />
        </label>
        <Select v-model="typeFilter" :options="TYPE_FILTER_OPTIONS" :searchable="false" aria-label="按类型筛选" />
        <Select v-model="statusFilter" :options="STATUS_FILTER_OPTIONS" :searchable="false" aria-label="按状态筛选" />
        <Select v-model="sourceFilter" :options="SOURCE_FILTER_OPTIONS" :searchable="false" aria-label="按来源筛选" />
        <div class="review-view-toggle" role="group" aria-label="显示模式">
          <button type="button" class="review-view-btn" :class="{ active: viewMode === 'list' }" :aria-pressed="viewMode === 'list'" aria-label="列表模式" @click="viewMode = 'list'"><Icon name="list" :size="16" /></button>
          <button type="button" class="review-view-btn" :class="{ active: viewMode === 'card' }" :aria-pressed="viewMode === 'card'" aria-label="卡片模式" @click="viewMode = 'card'"><Icon name="layout-grid" :size="16" /></button>
        </div>
      </div>

      <div v-if="active || grading" class="review-note">复习进行中，列表操作暂不可用。</div>
      <div v-else-if="!filteredCards.length" class="workbench-panel workbench-empty">
        <h2 class="kb-text-section-title">{{ cards.length ? '没有匹配的复习卡' : '还没有复习卡' }}</h2>
        <p>{{ cards.length ? '调整搜索、类型或状态筛选后再试。' : '点击右上角新建复习卡，把重要知识变成可复习的问题。' }}</p>
      </div>

      <div v-else-if="viewMode === 'list'" class="workbench-list" data-test="review-list">
        <article v-for="card in pagedCards" :key="card.id" class="review-row">
          <div class="review-row-main">
            <div class="review-row-top">
              <span class="review-type-badge" :class="'is-' + (card.cardType || 'BASIC').toLowerCase()">{{ typeLabel(card) }}</span>
              <span class="review-status" :class="'is-' + statusTone(card)">{{ statusLabel(card) }}</span>
            </div>
            <button type="button" class="review-row-title" :title="card.front" @click="openEdit(card)">{{ card.front }}</button>
            <p class="review-row-excerpt" :title="excerpt(card)">{{ excerpt(card) }}</p>
            <div class="review-row-meta">
              <span v-for="tag in tagList(card)" :key="tag" class="review-tag">#{{ tag }}</span>
              <span>{{ sourceLabel(card) }}</span>
              <span>复习 {{ card.reviewCount || 0 }} 次</span>
              <span>{{ nextText(card) }}</span>
            </div>
          </div>
          <div class="review-row-actions">
            <RouterLink v-if="card.noteId" :to="'/workbench/notes/' + card.noteId" class="review-icon-btn" aria-label="查看关联笔记"><Icon name="book-open" :size="16" /></RouterLink>
            <button type="button" class="review-icon-btn" :aria-label="'编辑：' + card.front" @click="openEdit(card)"><Icon name="pencil" :size="16" /></button>
            <button type="button" class="review-icon-btn" :aria-label="'更多操作：' + card.front" :disabled="active || grading" @click="openMenu($event, card)"><Icon name="more-horizontal" :size="16" /></button>
          </div>
        </article>
      </div>

      <div v-else class="review-card-grid" data-test="review-cards">
        <article v-for="card in pagedCards" :key="card.id" class="review-card">
          <div class="review-row-top">
            <span class="review-type-badge" :class="'is-' + (card.cardType || 'BASIC').toLowerCase()">{{ typeLabel(card) }}</span>
            <span class="review-status" :class="'is-' + statusTone(card)">{{ statusLabel(card) }}</span>
          </div>
          <button type="button" class="review-card-title" :title="card.front" @click="openEdit(card)">{{ card.front }}</button>
          <p class="review-row-excerpt" :title="excerpt(card)">{{ excerpt(card) }}</p>
          <div class="review-row-meta">
            <span v-for="tag in tagList(card)" :key="tag" class="review-tag">#{{ tag }}</span>
            <span>{{ sourceLabel(card) }}</span>
            <span>复习 {{ card.reviewCount || 0 }} 次</span>
          </div>
          <footer class="review-card-foot">
            <span>{{ nextText(card) }}</span>
            <div class="review-row-actions">
              <button type="button" class="review-icon-btn" :aria-label="'编辑：' + card.front" @click="openEdit(card)"><Icon name="pencil" :size="16" /></button>
              <button type="button" class="review-icon-btn" :aria-label="'更多操作：' + card.front" :disabled="active || grading" @click="openMenu($event, card)"><Icon name="more-horizontal" :size="16" /></button>
            </div>
          </footer>
        </article>
      </div>

      <div v-if="filteredCards.length" class="review-pagination">
        <Pagination :page-num="page" :page-size="pageSize" :total="filteredCards.length" :max-visible-pages="5" @change="page = $event" />
        <label class="review-page-size">每页
          <select v-model.number="pageSize" class="kb-select kb-select-sm" aria-label="每页条数">
            <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }}</option>
          </select>
          条
        </label>
      </div>

      <div class="review-method-actions">
        <button class="kb-btn" :disabled="active || grading || !usableCards.length" @click="startReview(true)">开始自主练习 · {{ usableCards.length }}</button>
        <RouterLink to="/workbench/recall" class="kb-btn">三轮主动回忆</RouterLink>
      </div>

      <details class="workbench-panel" @toggle="onCurveToggle">
        <summary>复习记录与遗忘反馈趋势</summary>
        <p class="workbench-muted">统计真实评分记录，不预测你的掌握度。</p>
        <div class="workbench-toolbar"><button v-for="days in [14, 30, 90]" :key="days" class="kb-btn" @click="curveDays = days; loadCurve()">{{ days }} 天</button></div>
        <p v-if="curveLoading">加载统计中…</p>
        <p v-else-if="curveError" role="alert">{{ curveError }}<button class="kb-btn" @click="loadCurve">重试</button></p>
        <template v-else-if="curve">
          <p class="workbench-muted">复习 {{ curve.totalReviews }} 次 · 遗忘反馈比例 {{ (curve.overallLapseRate * 100).toFixed(1) }}%</p>
          <svg v-if="curve.points.some(p => p.reviews)" viewBox="0 0 720 180" class="workbench-chart" role="img" aria-label="每日复习量与遗忘反馈比例">
            <line x1="24" y1="150" x2="700" y2="150" stroke="var(--kb-border)" />
            <rect v-for="(p, i) in chartPoints" :key="i" :x="p.x" :y="p.y" :width="p.width" :height="150 - p.y" fill="var(--kb-primary)" opacity=".3" />
            <polyline :points="chartPoints.map(p => p.x + ',' + p.lapseY).join(' ')" fill="none" stroke="var(--kb-warning)" stroke-width="2" />
            <text x="24" y="175" fill="var(--kb-muted-foreground)" font-size="12">{{ curve.points[0]?.date }}</text>
            <text x="700" y="175" text-anchor="end" fill="var(--kb-muted-foreground)" font-size="12">{{ curve.points.at(-1)?.date }}</text>
          </svg>
          <p v-else>暂无复习记录。</p>
          <p class="workbench-muted">蓝色：每日复习量 · 橙色：遗忘反馈比例</p>
        </template>
      </details>
    </template>

    <AppDialog
      :open="dialogOpen"
      :title="dialogTitle"
      :description="dialogDescription"
      icon="layers"
      width="720px"
      scrollable
      :loading="saving"
      :error="errors._form"
      @cancel="requestDialogClose"
    >
      <form data-test="review-card-form" class="review-form" @submit.prevent="saveCard">
        <div class="review-field">
          <span class="review-label">类型</span>
          <div class="review-types" role="group" aria-label="复习卡类型">
            <button v-for="item in CARD_TYPES" :key="item.value" type="button" class="review-type-option" :class="{ active: form.type === item.value }" :aria-pressed="form.type === item.value" @click="form.type = item.value">
              <Icon :name="item.icon" :size="15" aria-hidden="true" />{{ item.label }}
            </button>
          </div>
        </div>

        <template v-if="form.type === 'BASIC'">
          <div class="review-field">
            <label class="review-label" for="review-card-front">问题 / 线索 <span class="review-req">*</span></label>
            <textarea id="review-card-front" ref="firstFieldEl" v-model="form.front" data-test="review-front" class="kb-input kb-textarea review-textarea" rows="4" placeholder="输入一个明确的问题，例如：HashMap 为什么使用扰动函数？" :class="{ 'is-error': errors.front }" @input="errors.front = ''"></textarea>
            <span v-if="errors.front" class="review-field-error">{{ errors.front }}</span>
          </div>
          <div class="review-field">
            <label class="review-label" for="review-card-answer">标准答案 <span class="review-req">*</span></label>
            <textarea id="review-card-answer" v-model="form.back" data-test="review-back" class="kb-input kb-textarea review-textarea" rows="5" placeholder="输入完整答案、关键原理或解题步骤…" :class="{ 'is-error': errors.back }" @input="errors.back = ''"></textarea>
            <div class="review-field-foot"><span v-if="errors.back" class="review-field-error">{{ errors.back }}</span><span v-else class="review-field-hint">支持多行文本、代码片段与中英文</span><span class="review-counter">{{ form.back.length }} 字</span></div>
          </div>
        </template>

        <template v-else-if="form.type === 'CLOZE'">
          <div class="review-field">
            <label class="review-label" for="review-card-cloze">完整知识内容 <span class="review-req">*</span></label>
            <textarea id="review-card-cloze" ref="firstFieldEl" v-model="form.front" data-test="review-cloze" class="kb-input kb-textarea review-textarea" rows="5" placeholder="输入完整句子，例如：HashMap 的默认负载因子是 0.75。" :class="{ 'is-error': errors.front || errors.cloze }" @input="errors.front = ''; errors.cloze = ''"></textarea>
            <div class="review-toolbar">
              <button type="button" class="review-ghost-btn" @click="makeCloze"><Icon name="pencil" :size="14" aria-hidden="true" />设为挖空</button>
              <button type="button" class="review-ghost-btn" :disabled="!clozeBlanks.length" @click="form.front = stripAllCloze(form.front)">移除全部挖空</button>
              <span class="review-field-hint">先选中要隐藏的文字，再点击「设为挖空」</span>
            </div>
            <span v-if="errors.front" class="review-field-error">{{ errors.front }}</span>
            <span v-if="errors.cloze" class="review-field-error">{{ errors.cloze }}</span>
          </div>
          <div class="review-field">
            <span class="review-label">复习预览</span>
            <div class="review-cloze-preview" data-test="review-cloze-preview">
              <p v-if="clozeBlanks.length" class="review-cloze-preview-text">{{ form.front ? maskedCloze(form.front) : '设置挖空后在这里预览。' }}</p>
              <p v-else class="review-field-hint">尚未设置挖空。选中正文中的关键词后点击「设为挖空」。</p>
              <div v-if="clozeBlanks.length" class="review-blank-list">
                <span v-for="(blank, i) in clozeBlanks" :key="i" class="review-blank-chip">空 {{ i + 1 }}：{{ blank }}<button type="button" class="review-chip-x" :aria-label="'移除挖空 ' + (i + 1)" @click="removeClozeAt(i)"><Icon name="x" :size="12" /></button></span>
              </div>
            </div>
          </div>
          <div class="review-field">
            <label class="review-label" for="review-card-cloze-explain">补充解释（可选）</label>
            <textarea id="review-card-cloze-explain" v-model="form.back" data-test="review-back" class="kb-input kb-textarea review-textarea" rows="3" placeholder="解释该知识点的作用、背景或工程影响…"></textarea>
          </div>
        </template>

        <template v-else>
          <div class="review-field">
            <label class="review-label" for="review-card-recall">回忆任务 <span class="review-req">*</span></label>
            <textarea id="review-card-recall" ref="firstFieldEl" v-model="form.front" data-test="review-recall-task" class="kb-input kb-textarea review-textarea" rows="3" placeholder="例如：不看资料，解释 MySQL MVCC 如何实现一致性读。" :class="{ 'is-error': errors.front }" @input="errors.front = ''"></textarea>
            <span v-if="errors.front" class="review-field-error">{{ errors.front }}</span>
          </div>
          <div class="review-field">
            <span class="review-label">关键检查要点 <span class="review-req">*</span></span>
            <div class="review-checkpoint-list">
              <div v-for="(point, i) in form.checkpoints" :key="i" class="review-checkpoint-row">
                <input :value="point" class="kb-input" :placeholder="'要点 ' + (i + 1) + '，例如：Undo Log'" @input="updateCheckpoint(i, $event)" />
                <button type="button" class="review-icon-btn" :disabled="i === 0" aria-label="上移要点" @click="moveCheckpoint(i, -1)"><Icon name="arrow-up" :size="14" /></button>
                <button type="button" class="review-icon-btn" :disabled="i === form.checkpoints.length - 1" aria-label="下移要点" @click="moveCheckpoint(i, 1)"><Icon name="arrow-down" :size="14" /></button>
                <button type="button" class="review-icon-btn" :disabled="form.checkpoints.length === 1" aria-label="删除要点" @click="removeCheckpoint(i)"><Icon name="x" :size="14" /></button>
              </div>
            </div>
            <button type="button" class="review-ghost-btn review-add-point" @click="addCheckpoint"><Icon name="plus" :size="14" aria-hidden="true" />添加检查要点</button>
            <span v-if="errors.checkpoints" class="review-field-error">{{ errors.checkpoints }}</span>
          </div>
          <div class="review-field">
            <label class="review-label" for="review-card-recall-explain">参考讲解（可选）</label>
            <textarea id="review-card-recall-explain" v-model="form.back" data-test="review-back" class="kb-input kb-textarea review-textarea" rows="4" placeholder="输入完整解释，复习时先隐藏…"></textarea>
          </div>
        </template>

        <div class="review-field">
          <label class="review-label" for="review-card-hint">记忆提示（可选）</label>
          <input id="review-card-hint" v-model="form.hint" class="kb-input" placeholder="输入帮助回忆的轻量提示…" maxlength="500" />
        </div>

        <div class="review-field">
          <label class="review-label" for="review-card-tags">标签（可选）</label>
          <KfTagsInput
            id="review-card-tags"
            v-model="form.tags"
            placeholder="输入标签后按 Enter"
            aria-label="添加标签"
            :max-tag-length="40"
            clearable
          />
        </div>

        <div class="review-field">
          <span class="review-label">分类</span>
          <CategoryTreeSelect v-model="form.categoryId" :categories="categoryTree" placeholder="选择分类" empty-label="未分类" aria-label="选择分类" />
        </div>

        <p v-if="form.noteId" class="review-source-note"><Icon name="book-open" :size="14" aria-hidden="true" />关联笔记：{{ sourceNote?.title || '笔记 #' + form.noteId }}</p>
      </form>
      <template #footer>
        <div class="review-dialog-foot">
          <div class="review-continue">
            <Checkbox v-if="!editingId" v-model="continueAdd" label="保存后继续新增" data-test="review-continue-add" />
          </div>
          <div class="review-dialog-actions">
            <button type="button" class="review-ghost-btn" :disabled="saving" @click="requestDialogClose">取消</button>
            <button type="button" data-test="review-create-save" class="review-primary-btn" :disabled="saving" @click="saveCard">{{ saving ? '保存中…' : editingId ? '保存修改' : '保存复习卡' }}</button>
          </div>
        </div>
      </template>
    </AppDialog>

    <ContextMenu :open="menuOpen" :x="menuX" :y="menuY" :items="menuItems" label="复习卡操作" @close="menuOpen = false" />
  </WorkbenchShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import ContextMenu from '@/components/ui/ContextMenu.vue'
import KfTagsInput from '@/components/ui/KfTagsInput.vue'
import Pagination from '@/components/ui/Pagination.vue'
import Select from '@/components/ui/Select.vue'
import WorkbenchShell from '@/components/workbench/WorkbenchShell.vue'
import WorkbenchReviewMethods from '@/components/workbench/WorkbenchReviewMethods.vue'
import { positiveId } from '@/workbench/workflow'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { listReviews, createReview, updateReview, gradeReview, deleteReview, toggleReviewSuspend, getForgettingCurve, getNote, getCategoryTree } from '@/api/workbench'
import type { WbReviewCardVO, WbReviewGradeResult, WbForgettingCurve, WbNote, WbReviewCardPayload, CategoryVO, CategoryTreeNodeVO } from '@/api/types'

type ReviewType = 'BASIC' | 'CLOZE' | 'RECALL'
type ViewMode = 'list' | 'card'
type TabKey = 'due' | 'all' | 'practice'
interface MenuItem { key: string; label: string; icon?: string; danger?: boolean; disabled?: boolean; separator?: boolean; onSelect?: () => unknown }

const route = useRoute()
const router = useRouter()
const noteId = computed(() => positiveId(route.query.noteId))
const sourceNote = ref<WbNote>(), sourceError = ref('')
const cards = ref<WbReviewCardVO[]>([]), loading = ref(true), error = ref(''), revision = ref(0)
const mode = ref<TabKey>('due')
const queue = ref<WbReviewCardVO[]>([]), index = ref(0), active = ref(false), revealed = ref(false), grading = ref(false), sessionCompleted = ref(false), completedCount = ref(0)
const current = computed(() => queue.value[index.value])
const lastResult = ref<WbReviewGradeResult>()
const showHint = ref(false), recallCheckpointsShown = ref(false), recallDraft = ref('')
const checkedCheckpoints = ref<boolean[]>([]), clozeAnswers = ref<string[]>([])
const keyword = ref(''), typeFilter = ref('ALL'), statusFilter = ref('ALL'), sourceFilter = ref('ALL'), viewMode = ref<ViewMode>('list')
const page = ref(1), pageSize = ref(10)
const PAGE_SIZES = [10, 20, 50]
const grades = [{ value: 0, label: '忘了' }, { value: 1, label: '困难' }, { value: 2, label: '一般' }, { value: 3, label: '容易' }]
const CARD_TYPES: Array<{ value: ReviewType; label: string; icon: string }> = [
  { value: 'BASIC', label: '问答', icon: 'book-open' },
  { value: 'CLOZE', label: '挖空', icon: 'pencil' },
  { value: 'RECALL', label: '主动回忆', icon: 'brain' },
]
const TYPE_FILTER_OPTIONS = [{ label: '全部类型', value: 'ALL' }, ...CARD_TYPES.map(item => ({ label: item.label, value: item.value }))]
const STATUS_FILTER_OPTIONS = [{ label: '全部状态', value: 'ALL' }, { label: '今日到期', value: 'DUE' }, { label: '未到期', value: 'FUTURE' }, { label: '已暂停', value: 'SUSPENDED' }]
const SOURCE_FILTER_OPTIONS = [{ label: '全部来源', value: 'ALL' }, { label: '来自笔记', value: 'NOTE' }, { label: '手动创建', value: 'MANUAL' }]

/* ============================ 创建 / 编辑弹窗 ============================ */
const dialogOpen = ref(false), saving = ref(false), editingId = ref<number>()
const continueAdd = ref(false)
const firstFieldEl = ref<HTMLTextAreaElement | null>(null)
const categoryTree = ref<CategoryVO[]>([])
const form = reactive({
  type: 'BASIC' as ReviewType,
  front: '',
  back: '',
  hint: '',
  tags: [] as string[],
  checkpoints: [''],
  categoryId: undefined as number | undefined,
  noteId: undefined as number | undefined,
  captureId: undefined as number | undefined,
})
const errors = reactive<{ front?: string; back?: string; cloze?: string; checkpoints?: string; _form?: string }>({})
const dialogBaseline = ref('')
const snapshot = () => JSON.stringify(form)
const dialogDirty = computed(() => dialogOpen.value && snapshot() !== dialogBaseline.value)
const dialogTitle = computed(() => editingId.value ? '编辑复习卡' : form.noteId ? '确认复习卡' : '新建复习卡')
const dialogDescription = computed(() => editingId.value
  ? '修改问题、答案与提示。既有排程与复习记录保持不变。'
  : '将重要知识转化为适合主动回忆的复习卡。')

const clozeBlanks = computed(() => parseCloze(form.front).blanks.map(blank => blank.answer))

/* ============================ 列表与统计 ============================ */
function due(card: WbReviewCardVO) { return !!card.nextReviewTime && new Date(card.nextReviewTime.replace(' ', 'T')).getTime() <= Date.now() }
const usableCards = computed(() => cards.value.filter(card => !card.suspended))
const dueCards = computed(() => usableCards.value.filter(due))
const upcomingCard = computed(() => usableCards.value
  .filter(card => !due(card) && card.nextReviewTime)
  .sort((a, b) => timeOf(a.nextReviewTime) - timeOf(b.nextReviewTime))[0])
const nextUpcoming = computed(() => upcomingCard.value)
const nextUpcomingText = computed(() => nextUpcoming.value ? formatNext(nextUpcoming.value.nextReviewTime) : '—')
const tabs = computed(() => [
  { label: '今日到期', value: 'due' as TabKey, count: dueCards.value.length },
  { label: '全部卡片', value: 'all' as TabKey, count: cards.value.length },
  { label: '自主练习', value: 'practice' as TabKey, count: usableCards.value.length },
])
const baseCards = computed(() => mode.value === 'due' ? dueCards.value : mode.value === 'practice' ? usableCards.value : cards.value)
const filteredCards = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return baseCards.value.filter(card => {
    if (typeFilter.value !== 'ALL' && (card.cardType || 'BASIC') !== typeFilter.value) return false
    if (statusFilter.value === 'DUE' && (card.suspended || !due(card))) return false
    if (statusFilter.value === 'FUTURE' && (due(card) || !!card.suspended)) return false
    if (statusFilter.value === 'SUSPENDED' && !card.suspended) return false
    if (sourceFilter.value === 'NOTE' && !card.noteId) return false
    if (sourceFilter.value === 'MANUAL' && card.noteId) return false
    if (value && !`${card.front}\n${card.back || ''}\n${card.tags || ''}`.toLowerCase().includes(value)) return false
    return true
  })
})
const pagedCards = computed(() => filteredCards.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

watch([mode, typeFilter, statusFilter, sourceFilter, keyword, pageSize], () => { page.value = 1 })
watch(() => filteredCards.value.length, total => { if (page.value > Math.max(1, Math.ceil(total / pageSize.value))) page.value = Math.max(1, Math.ceil(total / pageSize.value)) })

/* ============================ 会话运行状态 ============================ */
const currentType = computed<ReviewType>(() => (current.value?.cardType as ReviewType) || 'BASIC')
const currentCloze = computed(() => parseCloze(current.value?.front || ''))
const currentCheckpoints = computed(() => parseCheckpoints(current.value))

function resetCardState() {
  revealed.value = false
  showHint.value = false
  recallCheckpointsShown.value = false
  recallDraft.value = ''
  checkedCheckpoints.value = []
  clozeAnswers.value = []
}
function reveal() { if (!grading.value) revealed.value = true }

/* ============================ 初始化与加载 ============================ */
let sequence = 0, sourceSequence = 0, curveSequence = 0, disposed = false
async function load() {
  const seq = ++sequence
  loading.value = true
  error.value = ''
  try {
    if (route.query.noteId && !noteId.value) throw new Error('Invalid note ID')
    const data = await listReviews({ noteId: noteId.value })
    if (seq === sequence) cards.value = noteId.value ? data.filter(card => card.noteId === noteId.value) : data
  } catch (e) {
    if (seq === sequence) error.value = getApiError(e, '复习队列加载失败')
  } finally {
    if (seq === sequence) loading.value = false
  }
}
async function loadCategories() {
  try { categoryTree.value = toCategoryVOs(await getCategoryTree()) }
  catch { categoryTree.value = []; notify('分类暂不可用，不影响保存复习卡', 'warning') }
}
async function initialize() {
  const seq = ++sourceSequence, id = noteId.value
  sourceNote.value = undefined
  sourceError.value = ''
  void load()
  void loadCategories()
  if (id) {
    try { const note = await getNote(id); if (seq !== sourceSequence || disposed) return; sourceNote.value = note }
    catch { if (seq !== sourceSequence || disposed) return; sourceError.value = '关联笔记暂不可访问，但已有复习卡仍保留。' }
  }
  if (seq !== sourceSequence || disposed) return
  if (route.query.create === '1') {
    openCreate({
      front: typeof route.query.front === 'string' ? route.query.front : '',
      back: typeof route.query.back === 'string' ? route.query.back : '',
    })
  }
}

/* ============================ 复习会话 ============================ */
function startReview(practice = false) {
  if (loading.value || error.value || active.value || grading.value) return
  queue.value = (practice ? usableCards.value : dueCards.value).slice(0, 20).map(card => ({ ...card }))
  if (!queue.value.length) return
  index.value = 0
  completedCount.value = 0
  active.value = true
  sessionCompleted.value = false
  lastResult.value = undefined
  resetCardState()
}
function pauseReview() {
  if (grading.value) return
  active.value = false
  revealed.value = false
}
async function grade(quality: number) {
  if (!active.value || !revealed.value || grading.value || !current.value) return
  grading.value = true
  const card = current.value
  try {
    const result = await gradeReview(card.id, { quality })
    if (disposed) return
    lastResult.value = result
    const original = cards.value.find(item => item.id === card.id)
    if (original) Object.assign(original, { nextReviewTime: new Date(result.nextReviewAt).toISOString(), intervalDay: result.intervalDay, easeFactorDecimal: result.easeFactor, reviewCount: (original.reviewCount || 0) + 1, nextReviewHint: result.intervalDay + ' 天后复习' })
    completedCount.value += 1
    if (index.value + 1 < queue.value.length) {
      index.value += 1
      resetCardState()
    } else {
      active.value = false
      sessionCompleted.value = true
    }
    revision.value += 1
  } catch (e) {
    notify(getApiError(e, '评分保存失败，请重试'), 'error')
  } finally {
    grading.value = false
  }
}
/* ============================ 弹窗：打开、校验与保存 ============================ */
function openCreate(prefill?: { front?: string; back?: string }) {
  editingId.value = undefined
  Object.assign(form, {
    type: 'BASIC',
    front: prefill?.front || '',
    back: prefill?.back || '',
    hint: '',
    tags: [],
    checkpoints: [''],
    categoryId: sourceNote.value?.categoryId,
    noteId: noteId.value,
    captureId: sourceNote.value?.captureId,
  })
  clearErrors()
  continueAdd.value = false
  dialogOpen.value = true
  dialogBaseline.value = snapshot()
  void nextTick(() => firstFieldEl.value?.focus())
}
function openEdit(card: WbReviewCardVO) {
  if (active.value || grading.value) return
  editingId.value = card.id
  Object.assign(form, {
    type: (card.cardType as ReviewType) || 'BASIC',
    front: card.front || '',
    back: card.back || '',
    hint: card.hint || '',
    tags: splitTags(card.tags),
    checkpoints: parseCheckpoints(card).length ? parseCheckpoints(card) : [''],
    categoryId: card.categoryId,
    noteId: card.noteId,
    captureId: card.captureId,
  })
  clearErrors()
  dialogOpen.value = true
  dialogBaseline.value = snapshot()
  void nextTick(() => firstFieldEl.value?.focus())
}
function clearErrors() { Object.assign(errors, { front: undefined, back: undefined, cloze: undefined, checkpoints: undefined, _form: undefined }) }
async function requestDialogClose() {
  if (saving.value) return
  if (dialogDirty.value) {
    const ok = await confirmDialog('放弃未保存的复习卡？已填写的内容不会保留。', { title: '放弃编辑', confirmText: '放弃', cancelText: '继续编辑', icon: 'alert-circle', variant: 'danger' })
    if (!ok) return
  }
  dialogOpen.value = false
}
function resetAfterCreate() {
  Object.assign(form, { front: '', back: '', hint: '', checkpoints: [''] })
  clearErrors()
  dialogBaseline.value = snapshot()
  void nextTick(() => firstFieldEl.value?.focus())
}
function normalizeCheckpoints() { return form.checkpoints.map(point => point.trim()).filter(Boolean) }
function buildPayload(): WbReviewCardPayload {
  const checkpoints = normalizeCheckpoints()
  return {
    front: form.front.trim(),
    back: form.type === 'BASIC' ? form.back.trim() : form.back,
    hint: form.hint.trim(),
    tags: form.tags.join(','),
    cardType: form.type,
    extraJson: form.type === 'RECALL' ? JSON.stringify({ checkpoints }) : '',
    categoryId: form.categoryId,
    noteId: form.noteId,
    captureId: form.captureId,
  }
}
async function saveCard() {
  if (saving.value) return
  clearErrors()
  if (!form.front.trim()) errors.front = form.type === 'CLOZE' ? '请输入完整知识内容' : form.type === 'RECALL' ? '请输入回忆任务' : '请输入问题或线索'
  if (form.type === 'BASIC' && !form.back.trim()) errors.back = '请输入标准答案'
  if (form.type === 'CLOZE' && !parseCloze(form.front).blanks.length) errors.cloze = '请至少设置一个挖空'
  if (form.type === 'RECALL' && !normalizeCheckpoints().length) errors.checkpoints = '请至少添加一个关键检查要点'
  if (Object.values(errors).some(Boolean)) return
  if (route.query.noteId && !noteId.value) return notify('关联笔记标识无效', 'warning')
  saving.value = true
  const payload = buildPayload()
  try {
    if (editingId.value) {
      await updateReview(editingId.value, payload)
      dialogOpen.value = false
      revision.value += 1
      await load()
      notify('复习卡已更新', 'success')
      return
    }
    await createReview(payload)
    revision.value += 1
    await load()
    if (continueAdd.value) {
      resetAfterCreate()
      notify('复习卡已保存，可继续新增', 'success')
    } else {
      dialogOpen.value = false
      notify('复习卡已保存', 'success')
    }
  } catch (e) {
    errors._form = getApiError(e, '保存失败，草稿已保留')
  } finally {
    saving.value = false
  }
}

/* ============================ 标签 / 挖空 / 要点编辑 ============================ */
function splitTags(value?: string) { return (value || '').split(',').map(tag => tag.trim()).filter((tag, i, list) => !!tag && list.indexOf(tag) === i) }
function addCheckpoint() { form.checkpoints.push(''); errors.checkpoints = undefined }
function updateCheckpoint(index: number, event: Event) {
  const target = event.target as HTMLInputElement
  form.checkpoints[index] = target.value
  errors.checkpoints = undefined
}
function removeCheckpoint(index: number) { if (form.checkpoints.length > 1) form.checkpoints.splice(index, 1) }
function moveCheckpoint(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= form.checkpoints.length) return
  const list = [...form.checkpoints]
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
  form.checkpoints = list
}
function makeCloze() {
  const el = firstFieldEl.value
  if (!el) return
  const start = el.selectionStart ?? 0, end = el.selectionEnd ?? 0
  if (end <= start) { errors.cloze = '请先在完整知识内容中选中要隐藏的文字'; return }
  const selected = form.front.slice(start, end)
  if (!selected.trim()) { errors.cloze = '选中的内容不能为空'; return }
  const count = (form.front.match(/\{\{c\d+::/g) || []).length
  const marker = `{{c${count + 1}::${selected}}}`
  form.front = form.front.slice(0, start) + marker + form.front.slice(end)
  errors.cloze = undefined
  errors.front = undefined
  void nextTick(() => {
    el.focus()
    const position = start + marker.length
    el.setSelectionRange(position, position)
  })
}
function removeClozeAt(index: number) {
  let i = 0
  form.front = form.front.replace(/\{\{c\d+::([\s\S]*?)\}\}/g, (match, answer: string) => (i++ === index ? answer : match))
}
function stripAllCloze(value: string) { return value.replace(/\{\{c\d+::([\s\S]*?)\}\}/g, '$1') }
function maskedCloze(value: string) { return parseCloze(value).masked }

/* ============================ 列表操作 ============================ */
async function suspend(card: WbReviewCardVO) {
  try { await toggleReviewSuspend(card.id); revision.value += 1; await load() }
  catch (e) { notify(getApiError(e, '操作失败'), 'error') }
}
async function remove(card: WbReviewCardVO) {
  await confirmDialog('删除这张复习卡？已有复习记录会保留在统计中。', {
    title: '确认删除', confirmText: '确认删除', cancelText: '取消', icon: 'trash-2', variant: 'danger',
    onConfirm: async () => {
      try { await deleteReview(card.id); revision.value += 1; await load() }
      catch (e) { notify(getApiError(e, '删除失败'), 'error'); throw e }
    },
  })
}

/* ============================ 更多菜单 ============================ */
const menuOpen = ref(false), menuX = ref(0), menuY = ref(0), menuCard = ref<WbReviewCardVO>()
const menuItems = computed<MenuItem[]>(() => {
  const card = menuCard.value
  if (!card) return []
  const items: MenuItem[] = [
    { key: 'edit', label: '编辑复习卡', icon: 'pencil', onSelect: () => openEdit(card) },
    { key: 'suspend', label: card.suspended ? '恢复复习' : '暂停复习', icon: card.suspended ? 'rotate-ccw' : 'pause', onSelect: () => suspend(card) },
  ]
  if (card.noteId) items.push({ key: 'note', label: '查看关联笔记', icon: 'book-open', onSelect: () => router.push('/workbench/notes/' + card.noteId) })
  items.push({ key: 'sep', label: '', separator: true })
  items.push({ key: 'delete', label: '删除复习卡', icon: 'trash-2', danger: true, onSelect: () => remove(card) })
  return items
})
function openMenu(event: MouseEvent, card: WbReviewCardVO) {
  if (active.value || grading.value) return
  menuCard.value = card
  menuX.value = event.clientX
  menuY.value = event.clientY
  menuOpen.value = true
}

/* ============================ 展示工具 ============================ */
const TYPE_LABELS: Record<ReviewType, string> = { BASIC: '问答', CLOZE: '挖空', RECALL: '主动回忆' }
function typeLabel(card?: WbReviewCardVO) { return TYPE_LABELS[((card?.cardType as ReviewType) || 'BASIC')] || '问答' }
function statusTone(card: WbReviewCardVO) { return card.suspended ? 'suspended' : due(card) ? 'due' : 'future' }
function statusLabel(card: WbReviewCardVO) { return card.suspended ? '已暂停' : due(card) ? '今日到期' : '未到期' }
function nextText(card: WbReviewCardVO) {
  if (card.suspended) return '已暂停'
  if (due(card)) return '今日到期'
  return card.nextReviewHint || (card.nextReviewTime ? formatNext(card.nextReviewTime) + '复习' : '等待首次复习')
}
function formatNext(value?: string) {
  if (!value) return '—'
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return '—'
  if (date.getTime() <= Date.now()) return '今日'
  return `${date.getMonth() + 1}月${date.getDate()}日`
}
function timeOf(value?: string) { return value ? new Date(value.replace(' ', 'T')).getTime() || 0 : 0 }
function excerpt(card: WbReviewCardVO) {
  const type = (card.cardType as ReviewType) || 'BASIC'
  if (type === 'CLOZE') {
    const parsed = parseCloze(card.front)
    return [parsed.masked, card.back].filter(Boolean).join(' · ')
  }
  if (type === 'RECALL') {
    const points = parseCheckpoints(card)
    return [card.back, points.length ? `检查要点：${points.slice(0, 4).join(' / ')}` : ''].filter(Boolean).join(' · ') || '直接打开查看完整内容'
  }
  return card.back || '暂无答案'
}
function tagList(card: WbReviewCardVO) { return splitTags(card.tags).slice(0, 4) }
function sourceLabel(card: WbReviewCardVO) { return card.noteId ? '来自笔记' : '手动创建' }

/* ============================ 解析工具 ============================ */
interface ClozeBlank { answer: string; start: number; end: number }
interface ClozeSegment { text: string; answer: boolean }
function parseCloze(text: string) {
  const blanks: ClozeBlank[] = [], segments: ClozeSegment[] = []
  let masked = '', last = 0
  const re = /\{\{c\d+::([\s\S]*?)\}\}/g
  let match: RegExpExecArray | null
  while ((match = re.exec(text))) {
    const before = text.slice(last, match.index)
    if (before) { masked += before; segments.push({ text: before, answer: false }) }
    masked += '[____]'
    blanks.push({ answer: match[1], start: match.index, end: match.index + match[0].length })
    segments.push({ text: match[1], answer: true })
    last = match.index + match[0].length
  }
  const tail = text.slice(last)
  if (tail) { masked += tail; segments.push({ text: tail, answer: false }) }
  return { blanks, masked, segments }
}
function parseCheckpoints(card?: WbReviewCardVO): string[] {
  if (!card?.extraJson) return []
  try {
    const parsed = JSON.parse(card.extraJson) as { checkpoints?: unknown }
    if (!Array.isArray(parsed.checkpoints)) return []
    return parsed.checkpoints.map(point => String(point).trim()).filter(Boolean)
  } catch { return [] }
}
function toCategoryVOs(nodes: CategoryTreeNodeVO[]): CategoryVO[] {
  return nodes.map(node => ({ id: node.id, name: node.name, parentId: node.parentId, children: node.children?.length ? toCategoryVOs(node.children) : undefined }))
}

/* ============================ 遗忘曲线 ============================ */
const curve = ref<WbForgettingCurve>(), curveLoading = ref(false), curveError = ref(''), curveDays = ref(30)
const chartPoints = computed(() => {
  const points = curve.value?.points || [], max = Math.max(1, ...points.map(p => p.reviews)), step = 676 / Math.max(points.length, 1)
  return points.map((p, i) => ({ x: 24 + i * step, y: 150 - p.reviews / max * 126, width: Math.max(1, step - 2), lapseY: 150 - p.lapseRate * 126 }))
})
async function loadCurve() {
  const seq = ++curveSequence
  curveLoading.value = true
  curveError.value = ''
  try { const result = await getForgettingCurve(curveDays.value); if (seq === curveSequence) curve.value = result }
  catch (e) { if (seq === curveSequence) curveError.value = getApiError(e, '统计加载失败') }
  finally { if (seq === curveSequence) curveLoading.value = false }
}
function onCurveToggle(event: Event) { if ((event.target as HTMLDetailsElement).open) void loadCurve() }

watch(() => current.value?.id, () => { if (active.value) resetCardState() })
watch(() => route.fullPath, () => {
  active.value = false
  sessionCompleted.value = false
  dialogOpen.value = false
  sourceNote.value = undefined
  resetCardState()
  void initialize()
})
onBeforeRouteLeave(() => {
  if (grading.value || saving.value) return false
  return active.value ? confirmDialog('结束本轮复习？已经提交的反馈会保留。', { title: '结束本轮复习？', confirmText: '结束学习', cancelText: '继续学习', icon: 'layers', variant: 'default' }) : true
})
onBeforeUnmount(() => { disposed = true; sequence++; sourceSequence++; curveSequence++ })
</script>

<style scoped>
.review-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--kb-space-3); }
.review-stat { display: flex; flex-direction: column; gap: var(--kb-space-1); padding: var(--kb-space-4); background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-md); box-shadow: var(--shadow-card); min-width: 0; }
.review-stat-label { font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.review-stat-value { font-size: var(--kb-fs-h2); line-height: var(--kb-lh-h2); color: var(--kb-foreground); overflow-wrap: anywhere; }
.review-stat-value--time { font-size: var(--kb-fs-h4); line-height: var(--kb-lh-h4); }
.review-stat small { font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }

.review-session-heading { display: flex; align-items: center; gap: var(--kb-space-3); min-width: 0; flex-wrap: wrap; }
.review-session-heading h2 { margin: 0; }
.review-session-tools { display: flex; align-items: center; gap: var(--kb-space-3); color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-sm); }
.review-type-badge { display: inline-flex; align-items: center; gap: var(--kb-space-1); padding: 2px var(--kb-space-2); border-radius: var(--kb-radius-sm); background: color-mix(in srgb, var(--kb-primary) 12%, var(--kb-card)); color: var(--kb-primary); font-size: var(--kb-fs-caption); font-weight: 600; white-space: nowrap; }
.review-type-badge.is-cloze { background: color-mix(in srgb, var(--kb-warning) 16%, var(--kb-card)); color: var(--kb-warning); }
.review-type-badge.is-recall { background: color-mix(in srgb, var(--kb-state-success) 14%, var(--kb-card)); color: var(--kb-state-success); }
.review-hint-toggle { border: 0; background: transparent; color: var(--kb-primary); font-size: var(--kb-fs-body-sm); cursor: pointer; padding: 0; width: fit-content; }
.review-hint { margin: 0; padding: var(--kb-space-3); border-left: 2px solid var(--kb-primary); background: var(--kb-muted); border-radius: var(--kb-radius-sm); color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-sm); white-space: pre-wrap; overflow-wrap: anywhere; }
.review-cloze-text { white-space: pre-wrap; }
.review-cloze-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--kb-space-3); width: 100%; }
.review-cloze-field { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); min-width: 0; }
.review-cloze-field input { flex: 1; min-width: 0; }
.review-cloze-full { white-space: pre-wrap; }
.review-cloze-answer { background: color-mix(in srgb, var(--kb-primary) 18%, var(--kb-card)); color: var(--kb-primary); padding: 0 2px; border-radius: 3px; }
.review-compare { margin: 0; }
.review-recall-draft { width: 100%; min-height: 120px; resize: vertical; }
.review-checkpoints { display: flex; flex-direction: column; gap: var(--kb-space-2); width: 100%; }
.review-checkpoints p { margin: 0; }

.review-tabs { display: flex; gap: var(--kb-space-2); flex-wrap: wrap; align-items: center; }
.review-tab { display: inline-flex; align-items: center; gap: var(--kb-space-2); padding: var(--kb-space-2) var(--kb-space-4); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); background: var(--kb-card); color: var(--kb-foreground); font-size: var(--kb-fs-body-md); cursor: pointer; text-decoration: none; }
.review-tab:hover { background: var(--kb-muted); }
.review-tab.active { background: var(--kb-primary); border-color: var(--kb-primary); color: var(--kb-primary-foreground); }
.review-tab span { font-size: var(--kb-fs-caption); opacity: .75; }
.review-tab-link { margin-left: auto; }

.review-filters { display: flex; align-items: center; gap: var(--kb-space-2); flex-wrap: wrap; }
.review-search { display: flex; align-items: center; gap: var(--kb-space-2); flex: 1 1 220px; min-width: 180px; padding: 0 var(--kb-space-3); background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); color: var(--kb-muted-foreground); }
.review-search input { border: 0; background: transparent; flex: 1; min-width: 0; padding-inline: 0; }
.review-search input:focus { outline: none; }
.review-search:focus-within { border-color: var(--kb-primary); outline: 2px solid var(--kb-ring); outline-offset: 1px; }
.review-filters :deep(.select-control) { width: 148px; }
.review-view-toggle { display: inline-flex; border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); overflow: hidden; background: var(--kb-card); }
.review-view-btn { display: inline-flex; align-items: center; justify-content: center; width: var(--kb-btn-height-md); height: var(--kb-btn-height-md); border: 0; background: transparent; color: var(--kb-muted-foreground); cursor: pointer; }
.review-view-btn.active { background: var(--kb-primary); color: var(--kb-primary-foreground); }
.review-note { font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); }

.review-row { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kb-space-4); padding: var(--kb-space-4); border-bottom: 1px solid var(--kb-border); }
.review-row:last-child { border-bottom: 0; }
.review-row:hover { background: color-mix(in srgb, var(--kb-primary) 5%, var(--kb-card)); }
.review-row-main { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: var(--kb-space-1); }
.review-row-top { display: flex; align-items: center; gap: var(--kb-space-2); flex-wrap: wrap; }
.review-row-title, .review-card-title { border: 0; background: transparent; padding: 0; text-align: left; color: var(--kb-foreground); font-size: var(--kb-fs-body-md); font-weight: 600; cursor: pointer; overflow-wrap: anywhere; }
.review-row-title:hover, .review-card-title:hover { color: var(--kb-primary); }
.review-row-excerpt { margin: 0; color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-sm); line-height: var(--kb-lh-body-sm); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.review-row-meta { display: flex; align-items: center; gap: var(--kb-space-3); flex-wrap: wrap; color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); }
.review-row-actions { display: flex; align-items: center; gap: var(--kb-space-1); flex-shrink: 0; }
.review-icon-btn { display: inline-flex; align-items: center; justify-content: center; width: var(--kb-btn-height-sm); height: var(--kb-btn-height-sm); border: 0; border-radius: var(--kb-radius-sm); background: transparent; color: var(--kb-muted-foreground); cursor: pointer; }
.review-icon-btn:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.review-icon-btn:disabled { opacity: .45; cursor: not-allowed; }
.review-status { display: inline-flex; align-items: center; padding: 1px var(--kb-space-2); border-radius: var(--kb-radius-sm); background: var(--kb-muted); color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); }
.review-status.is-due { background: color-mix(in srgb, var(--kb-primary) 12%, var(--kb-card)); color: var(--kb-primary); }
.review-status.is-suspended { background: color-mix(in srgb, var(--kb-state-warning) 14%, var(--kb-card)); color: var(--kb-state-warning); }
.review-tag { display: inline-flex; align-items: center; gap: 2px; color: var(--kb-muted-foreground); background: var(--kb-muted); border-radius: var(--kb-radius-sm); padding: 1px var(--kb-space-2); font-size: var(--kb-fs-caption); }

.review-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--kb-space-4); }
.review-card { display: flex; flex-direction: column; gap: var(--kb-space-2); padding: var(--kb-space-4); background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-md); min-width: 0; }
.review-card-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-2); margin-top: auto; padding-top: var(--kb-space-2); border-top: 1px solid var(--kb-border); color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); }

.review-pagination { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-3); flex-wrap: wrap; }
.review-page-size { display: inline-flex; align-items: center; gap: var(--kb-space-2); color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-sm); }
.review-method-actions { display: flex; gap: var(--kb-space-2); flex-wrap: wrap; }

/* ============================ 弹窗 ============================ */
.review-form { display: flex; flex-direction: column; gap: var(--kb-space-4); }
.review-field { display: flex; flex-direction: column; gap: var(--kb-space-2); min-width: 0; }
.review-label { font-size: var(--kb-fs-body-sm); font-weight: 600; color: var(--kb-foreground); }
.review-req { color: var(--kb-destructive); }
.review-textarea { width: 100%; resize: vertical; }
.review-field-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-2); font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.review-field-hint { font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.review-field-error { font-size: var(--kb-fs-caption); color: var(--kb-destructive); }
.review-counter { margin-left: auto; color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); }
.review-textarea.is-error { border-color: var(--kb-destructive); }
.review-types { display: flex; gap: var(--kb-space-2); flex-wrap: wrap; }
.review-type-option { display: inline-flex; align-items: center; gap: var(--kb-space-2); padding: var(--kb-space-2) var(--kb-space-4); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); background: var(--kb-card); color: var(--kb-foreground); font-size: var(--kb-fs-body-sm); cursor: pointer; }
.review-type-option.active { border-color: var(--kb-primary); background: color-mix(in srgb, var(--kb-primary) 10%, var(--kb-card)); color: var(--kb-primary); }
.review-toolbar { display: flex; align-items: center; gap: var(--kb-space-2); flex-wrap: wrap; }
.review-ghost-btn { display: inline-flex; align-items: center; justify-content: center; gap: var(--kb-space-2); min-height: var(--kb-btn-height-sm); padding: var(--kb-space-1) var(--kb-space-3); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); background: var(--kb-card); color: var(--kb-foreground); font-size: var(--kb-fs-body-sm); cursor: pointer; }
.review-ghost-btn:hover { background: var(--kb-muted); }
.review-ghost-btn:disabled { opacity: .5; cursor: not-allowed; }
.review-primary-btn { display: inline-flex; align-items: center; justify-content: center; min-height: var(--kb-btn-height-md); padding: var(--kb-space-2) var(--kb-space-5); border: 1px solid var(--kb-primary); border-radius: var(--kb-radius-sm); background: var(--kb-primary); color: var(--kb-primary-foreground); font-size: var(--kb-fs-body-md); font-weight: 600; cursor: pointer; }
.review-primary-btn:hover { background: var(--kb-primary-hover); }
.review-primary-btn:disabled { opacity: .6; cursor: not-allowed; }
.review-cloze-preview { padding: var(--kb-space-4); background: var(--kb-muted); border: 1px dashed var(--kb-border); border-radius: var(--kb-radius-md); min-height: 64px; display: flex; flex-direction: column; gap: var(--kb-space-2); }
.review-cloze-preview-text { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--kb-foreground); font-size: var(--kb-fs-body-md); }
.review-blank-list { display: flex; gap: var(--kb-space-2); flex-wrap: wrap; }
.review-blank-chip { display: inline-flex; align-items: center; gap: var(--kb-space-1); padding: 2px var(--kb-space-2); border-radius: var(--kb-radius-sm); background: var(--kb-card); border: 1px solid var(--kb-border); font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); overflow-wrap: anywhere; }
.review-chip-x { display: inline-flex; align-items: center; justify-content: center; border: 0; background: transparent; color: inherit; cursor: pointer; padding: 0; opacity: .7; }
.review-chip-x:hover { opacity: 1; }
.review-checkpoint-list { display: flex; flex-direction: column; gap: var(--kb-space-2); }
.review-checkpoint-row { display: flex; align-items: center; gap: var(--kb-space-1); }
.review-checkpoint-row input { flex: 1; min-width: 0; }
.review-add-point { width: fit-content; }
.review-source-note { display: inline-flex; align-items: center; gap: var(--kb-space-2); margin: 0; color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-sm); }
.review-dialog-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-3); width: 100%; flex-wrap: wrap; }
.review-dialog-actions { display: flex; align-items: center; gap: var(--kb-space-2); margin-left: auto; }

@media (max-width: 1024px) {
  .review-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .review-filters :deep(.select-control) { width: 100%; flex: 1 1 140px; }
  .review-filters { align-items: stretch; }
  .review-row { flex-direction: column; }
  .review-row-actions { align-self: flex-end; }
  .review-session-tools { width: 100%; justify-content: space-between; }
}
@media (max-width: 639px) {
  .review-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .review-stat { padding: var(--kb-space-3); }
  .workbench-grades { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .review-tab-link { margin-left: 0; }
  .review-dialog-foot { flex-direction: column; align-items: stretch; }
  .review-dialog-actions { margin-left: 0; }
  .review-dialog-actions > * { flex: 1; }
  .review-field-foot { flex-wrap: wrap; }
}
</style>
