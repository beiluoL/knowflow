<template>
  <!-- 闪卡大厅：统一入口，整合「学习闪卡 / 推荐复习 / 我的闪卡」三个模块。 -->
  <div class="flashcards-hub animate-fade-in">
    <!-- ===== 顶部 Hero：标题 + 全局统计 ===== -->
    <header class="hub-hero">
      <div class="hero-left">
        <button type="button" class="back-btn" title="返回" @click="goBack">
          <Icon name="arrow-left" :size="16" />
        </button>
        <div class="title-text">
          <h1 class="kb-h1">闪卡大厅</h1>
          <p class="kb-body-sm">系统闪卡 · 智能推荐 · 个人卡片，一站式复习</p>
        </div>
      </div>
      <!-- 全局统计卡片：四个核心指标 -->
      <div class="hero-stats">
        <div class="hero-stat">
          <div class="hero-stat-icon icon-primary"><Icon name="layers" :size="16" /></div>
          <div class="hero-stat-body">
            <div class="hero-stat-num tabular-nums">{{ learnTotal }}</div>
            <div class="hero-stat-label">系统闪卡</div>
          </div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat-icon icon-accent"><Icon name="bookmark" :size="16" /></div>
          <div class="hero-stat-body">
            <div class="hero-stat-num tabular-nums">{{ mineTotal }}</div>
            <div class="hero-stat-label">我的闪卡</div>
          </div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat-icon icon-warning"><Icon name="repeat" :size="16" /></div>
          <div class="hero-stat-body">
            <div class="hero-stat-num tabular-nums">{{ reviewTotal }}</div>
            <div class="hero-stat-label">待复习</div>
          </div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat-icon icon-success"><Icon name="check-circle" :size="16" /></div>
          <div class="hero-stat-body">
            <div class="hero-stat-num tabular-nums">{{ todayLearned }}</div>
            <div class="hero-stat-label">今日已学</div>
          </div>
        </div>
      </div>
    </header>

    <!-- ===== Tab 切换 ===== -->
    <div class="tabs-bar" role="tablist" aria-label="闪卡模式切换">
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

    <!-- ===== 统一搜索框（跨模式过滤） ===== -->
    <div class="global-search">
      <Icon name="search" :size="14" class="search-icon" />
      <input
        v-model="globalKeyword"
        type="text"
        :placeholder="searchPlaceholder"
        class="search-input"
        @keyup.enter="onGlobalSearch"
      />
      <button v-if="globalKeyword" type="button" class="clear-btn" @click="clearSearch" aria-label="清除">
        <Icon name="x" :size="14" />
      </button>
    </div>

    <!-- ===== Tab 1: 学习闪卡 ===== -->
    <section v-show="activeTab === 'learn'" class="tab-panel">
      <!-- 进度块 -->
      <div v-if="!learnLoading && learnFiltered.length > 0" class="progress-block">
        <div class="progress-left">
          <div class="progress-meta">
            <span class="progress-label">学习进度</span>
            <span class="progress-value tabular-nums">{{ learnIndex + 1 }} / {{ learnFiltered.length }}</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: `${learnProgress}%` }"></div>
          </div>
        </div>
        <div class="progress-right">
          <div class="stat-cell"><div class="stat-num stat-known tabular-nums">{{ knownCount }}</div><div class="kb-caption">已记住</div></div>
          <div class="stat-cell"><div class="stat-num stat-review tabular-nums">{{ reviewCount }}</div><div class="kb-caption">需复习</div></div>
          <div class="stat-cell"><div class="stat-num stat-new tabular-nums">{{ newCount }}</div><div class="kb-caption">待学习</div></div>
        </div>
      </div>

      <!-- 分类 + 随机排序 工具行 -->
      <div v-if="!learnLoading && learnFiltered.length > 0" class="learn-toolbar">
        <select v-model="selectedCategory" class="kb-select">
          <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
        </select>
        <button type="button" class="btn-primary" @click="shuffleCards">
          <Icon name="shuffle" :size="14" /><span>随机排序</span>
        </button>
      </div>

      <!-- 加载态 -->
      <div v-if="learnLoading" class="state-area">
        <div class="loading-spinner"></div>
        <p class="state-text">加载中…</p>
      </div>

      <!-- 空态 -->
      <div v-else-if="learnFiltered.length === 0" class="state-area">
        <Icon name="layers" :size="48" class="state-icon" />
        <p class="state-text">暂无系统闪卡</p>
        <p class="state-hint">{{ globalKeyword ? '没有匹配的卡片，试试清除搜索条件' : '去「我的闪卡」创建或导入一批吧' }}</p>
        <div v-if="!globalKeyword" class="empty-actions">
          <button type="button" class="btn-primary" @click="activeTab = 'mine'">
            <Icon name="plus" :size="14" /><span>前往创建</span>
          </button>
        </div>
      </div>

      <!-- 闪卡翻转展示区 -->
      <div v-else class="flip-area">
        <div
          class="flip-card"
          :class="{ flipped: isFlipped }"
          role="button"
          tabindex="0"
          aria-label="翻转卡片查看答案"
          @click="flipCard"
          @keydown.enter.prevent="$event.currentTarget.click()"
          @keydown.space.prevent="$event.currentTarget.click()"
        >
          <div class="flip-card-inner">
            <div class="flip-card-face flip-card-front">
              <div class="face-tags">
                <span class="face-tag tag-category">{{ currentCard?.category }}</span>
                <span class="face-tag tag-sub">{{ getDifficultyLabel(currentCard?.difficulty) }}</span>
              </div>
              <h2 class="face-question">{{ currentCard?.question }}</h2>
              <p class="face-hint">请点击卡片查看答案</p>
              <div class="flip-pill"><Icon name="rotate-ccw" :size="14" /><span>点击翻转</span></div>
            </div>
            <div class="flip-card-face flip-card-back">
              <div class="back-header">
                <Icon name="lightbulb" :size="16" class="back-icon" />
                <span class="back-label">答案</span>
              </div>
              <div class="back-body">
                <h3 class="back-title">{{ currentCard?.question }}</h3>
                <p class="back-text">{{ currentCard?.answer }}</p>
                <div v-if="currentCard?.category" class="back-tags">
                  <span class="back-tag">{{ currentCard.category }}</span>
                  <span class="back-tag">{{ getDifficultyLabel(currentCard.difficulty) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div v-if="!learnLoading && learnFiltered.length > 0" class="action-bar">
        <button type="button" class="action-btn btn-outline" :disabled="learnIndex === 0" @click="prevCard">
          <Icon name="skip-back" :size="14" /><span>上一张</span>
        </button>
        <button type="button" class="action-btn btn-danger" :disabled="reviewing" @click="rateCard('unknown')">
          <Icon name="x" :size="14" /><span>不熟悉</span>
        </button>
        <button type="button" class="action-btn btn-warning" :disabled="reviewing" @click="rateCard('familiar')">
          <Icon name="rotate-ccw" :size="14" /><span>模糊</span>
        </button>
        <button type="button" class="action-btn btn-success" :disabled="reviewing" @click="rateCard('mastered')">
          <Icon name="check" :size="14" /><span>记住了</span>
        </button>
        <button type="button" class="action-btn btn-outline" :disabled="learnIndex >= learnFiltered.length - 1" @click="nextCard">
          <span>下一张</span><Icon name="skip-forward" :size="14" />
        </button>
      </div>

      <transition name="fade">
        <p v-if="feedback" class="feedback-text">{{ feedback }}</p>
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
            <div class="stat-cell"><div class="stat-num stat-known tabular-nums">{{ reviewMasteredCount }}</div><div class="kb-caption">已掌握</div></div>
            <div class="stat-cell"><div class="stat-num stat-review tabular-nums">{{ reviewFiltered.length - reviewIndex - 1 }}</div><div class="kb-caption">剩余</div></div>
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
            @keydown.enter.prevent="$event.currentTarget.click()"
            @keydown.space.prevent="$event.currentTarget.click()"
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
          <select v-model="filters.difficulty" class="kb-select" @change="reloadMine">
            <option :value="undefined">全部难度</option>
            <option :value="1">简单</option>
            <option :value="2">中等</option>
            <option :value="3">困难</option>
          </select>
          <select v-model="filters.sourceType" class="kb-select" @change="reloadMine">
            <option value="">全部来源</option>
            <option value="MANUAL">手动创建</option>
            <option value="AI_DOC">AI · 文档</option>
            <option value="AI_KB">AI · 知识库</option>
            <option value="IMPORT">批量导入</option>
          </select>
          <select v-model="filters.categoryId" class="kb-select" @change="reloadMine">
            <option :value="undefined">全部知识库</option>
            <option v-for="c in categories2" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="action-row">
          <div class="action-left">
            <button type="button" class="btn-primary" @click="openCreate()">
              <Icon name="plus" :size="14" /><span>新增闪卡</span>
            </button>
            <button type="button" class="btn-secondary" @click="openGenerate">
              <Icon name="sparkles" :size="14" /><span>AI 生成</span>
            </button>
            <button type="button" class="btn-secondary" @click="openImport">
              <Icon name="upload" :size="14" /><span>导入</span>
            </button>
            <button type="button" class="btn-secondary" :disabled="mineCards.length === 0" @click="doExport">
              <Icon name="download" :size="14" /><span>导出</span>
            </button>
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
            <button type="button" class="btn-ghost" @click="reloadMine" title="刷新">
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
          <p class="state-text">还没有闪卡</p>
          <p class="state-hint">手动创建一张，或选择知识库/文档让 AI 一键生成。</p>
          <div class="empty-actions">
            <button type="button" class="btn-primary" @click="openCreate">
              <Icon name="plus" :size="14" /><span>新增闪卡</span>
            </button>
            <button type="button" class="btn-secondary" @click="openGenerate">
              <Icon name="sparkles" :size="14" /><span>AI 生成</span>
            </button>
          </div>
        </div>
      </template>

      <!-- 列表视图 -->
      <template v-else-if="viewMode === 'list'">
        <div class="list-wrap">
          <table class="kb-table">
            <thead>
              <tr>
                <th style="width: 44px;">
                  <input type="checkbox" :checked="allSelected" :indeterminate.prop="someSelected" @change="toggleAll" />
                </th>
                <th style="width: 56px;">#</th>
                <th>正面（问题）</th>
                <th>背面（答案）</th>
                <th style="width: 100px;">难度</th>
                <th style="width: 120px;">来源</th>
                <th style="width: 150px;">更新时间</th>
                <th style="width: 150px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(c, idx) in mineCards" :key="c.id">
                <td><input type="checkbox" :checked="selectedIds.includes(c.id)" @change="toggleId(c.id)" /></td>
                <td class="tabular-nums text-muted">{{ idx + 1 }}</td>
                <td>
                  <div class="cell-front" :title="c.front">
                    <span v-if="c.tags" class="mini-tags">
                      <span v-for="t in splitTags(c.tags).slice(0, 2)" :key="t" class="mini-tag">#{{ t }}</span>
                    </span>
                    {{ truncate(c.front || '', 60) }}
                  </div>
                </td>
                <td><div class="cell-back" :title="c.back">{{ truncate(c.back || '', 70) }}</div></td>
                <td><span class="diff-badge" :class="diffClass(c.difficulty)">{{ diffLabel(c.difficulty) }}</span></td>
                <td>
                  <span class="source-badge" :class="sourceClass(c.sourceType)">
                    <Icon :name="sourceIcon(c.sourceType)" :size="12" />{{ sourceLabel(c.sourceType) }}
                  </span>
                </td>
                <td class="text-muted tabular-nums">{{ formatTime(c.updateTime || c.createTime) }}</td>
                <td>
                  <div class="row-actions">
                    <button type="button" class="row-btn" @click="flipInRow(c)">
                      <Icon name="rotate-ccw" :size="13" /><span>{{ flippedId === c.id ? '正面' : '答案' }}</span>
                    </button>
                    <button type="button" class="row-btn" @click="openEdit(c)">
                      <Icon name="edit" :size="13" /><span>编辑</span>
                    </button>
                    <button type="button" class="row-btn danger" @click="removeOne(c)">
                      <Icon name="trash-2" :size="13" /><span>删除</span>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <!-- 卡片视图 -->
      <template v-else>
        <div class="card-grid">
          <div
            v-for="c in mineCards"
            :key="c.id"
            class="fc-card"
            :class="{ flipped: flippedId === c.id }"
            @click.self="toggleFlip(c.id)"
          >
            <div class="fc-card-inner">
              <div class="fc-face fc-front">
                <div class="fc-face-top">
                  <span class="diff-badge" :class="diffClass(c.difficulty)">{{ diffLabel(c.difficulty) }}</span>
                  <span class="source-badge" :class="sourceClass(c.sourceType)">
                    <Icon :name="sourceIcon(c.sourceType)" :size="11" />{{ sourceLabel(c.sourceType) }}
                  </span>
                  <div class="fc-top-actions" @click.stop>
                    <button type="button" class="icon-btn" title="编辑" @click.stop="openEdit(c)">
                      <Icon name="edit" :size="13" />
                    </button>
                    <button type="button" class="icon-btn danger" title="删除" @click.stop="removeOne(c)">
                      <Icon name="trash-2" :size="13" />
                    </button>
                  </div>
                </div>
                <h3 class="fc-front-title">{{ c.front }}</h3>
                <div class="fc-face-bottom">
                  <div class="fc-tags">
                    <span v-for="t in splitTags(c.tags || '')" :key="t" class="fc-tag">#{{ t }}</span>
                  </div>
                  <button type="button" class="fc-flip-btn" @click.stop="toggleFlip(c.id)">
                    <Icon name="rotate-ccw" :size="13" />查看答案
                  </button>
                </div>
              </div>
              <div class="fc-face fc-back">
                <div class="fc-face-top">
                  <span v-if="c.categoryName" class="kb-chip"><Icon name="bookmark" :size="11" />{{ c.categoryName }}</span>
                  <div class="fc-top-actions" @click.stop>
                    <button type="button" class="icon-btn" title="编辑" @click.stop="openEdit(c)">
                      <Icon name="edit" :size="13" />
                    </button>
                    <button type="button" class="icon-btn danger" title="删除" @click.stop="removeOne(c)">
                      <Icon name="trash-2" :size="13" />
                    </button>
                  </div>
                </div>
                <div class="fc-back-content">{{ c.back }}</div>
                <div class="fc-face-bottom">
                  <div class="fc-meta">
                    <Icon name="calendar" :size="12" /><span>更新于 {{ formatTime(c.updateTime || c.createTime) }}</span>
                  </div>
                  <button type="button" class="fc-flip-btn" @click.stop="toggleFlip(c.id)">
                    <Icon name="rotate-ccw" :size="13" />返回正面
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- ===== 新增 / 编辑弹窗 ===== -->
      <div v-if="formDialog.visible" class="modal-mask" @click.self="closeForm">
        <div class="modal modal-lg">
          <header class="modal-header">
            <h3>{{ formDialog.mode === 'edit' ? '编辑闪卡' : '新增闪卡' }}</h3>
            <button type="button" class="icon-btn" @click="closeForm" title="关闭"><Icon name="x" :size="16" /></button>
          </header>
          <div class="modal-body form-grid">
            <div class="form-item col-span-2">
              <label class="form-label">正面 · 问题 / 概念 <span class="req">*</span></label>
              <textarea v-model="formData.front" rows="3" placeholder="例如：什么是闭包（Closure）？" class="kb-textarea" />
            </div>
            <div class="form-item col-span-2">
              <label class="form-label">背面 · 答案 / 解释 <span class="req">*</span></label>
              <textarea v-model="formData.back" rows="4" placeholder="用要点化的方式给出解释，便于记忆。" class="kb-textarea" />
            </div>
            <div class="form-item">
              <label class="form-label">难度</label>
              <select v-model.number="formData.difficulty" class="kb-select">
                <option :value="1">简单</option>
                <option :value="2">中等</option>
                <option :value="3">困难</option>
              </select>
            </div>
            <div class="form-item">
              <label class="form-label">自定义分类</label>
              <input v-model="formData.category" type="text" class="kb-input" placeholder="如：前端基础" />
            </div>
            <div class="form-item">
              <label class="form-label">关联知识库</label>
              <select v-model="formData.categoryId" class="kb-select">
                <option :value="undefined">不关联</option>
                <option v-for="c in categories2" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
            </div>
            <div class="form-item">
              <label class="form-label">关联文档</label>
              <select v-model="formData.docId" class="kb-select" :disabled="!formData.categoryId">
                <option :value="undefined">不关联</option>
                <option v-for="d in dialogDocs" :key="d.id" :value="d.id">{{ d.title }}</option>
              </select>
            </div>
            <div class="form-item col-span-2">
              <label class="form-label">标签（逗号分隔）</label>
              <input v-model="formData.tags" type="text" class="kb-input" placeholder="例如：闭包,作用域,面试题" />
            </div>
          </div>
          <footer class="modal-footer">
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
              <select v-model.number="genDialog.difficultyPreference" class="kb-select">
                <option :value="0">均衡</option>
                <option :value="1">偏简单</option>
                <option :value="2">偏中等</option>
                <option :value="3">偏困难</option>
              </select>
            </div>
            <div v-if="genDialog.source === 'kb'" class="form-item col-span-2">
              <label class="form-label">选择知识库 <span class="req">*</span></label>
              <select v-model="genDialog.categoryId" class="kb-select">
                <option :value="undefined">请选择知识库</option>
                <option v-for="c in categories2" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
              <p class="form-hint">将聚合该知识库下已发布的文档（最多 15 篇），基于整体内容生成综合闪卡。</p>
            </div>
            <div v-if="genDialog.source === 'doc'" class="form-item">
              <label class="form-label">选择知识库</label>
              <select v-model="genDialog.categoryId" class="kb-select" @change="genDialog.docId = undefined">
                <option :value="undefined">全部</option>
                <option v-for="c in categories2" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
            </div>
            <div v-if="genDialog.source === 'doc'" class="form-item">
              <label class="form-label">选择文档 <span class="req">*</span></label>
              <select v-model="genDialog.docId" class="kb-select">
                <option :value="undefined">请选择文档</option>
                <option v-for="d in dialogDocs" :key="d.id" :value="d.id">{{ truncate(d.title, 36) }}</option>
              </select>
            </div>
            <div class="form-item">
              <label class="form-label">生成数量</label>
              <div class="count-stepper">
                <button type="button" class="stepper-btn" :disabled="genDialog.count <= 3" @click="genDialog.count -= 1">−</button>
                <input v-model.number="genDialog.count" type="number" min="1" max="30" class="stepper-input" />
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
            <textarea v-model="importDialog.jsonText" rows="14" class="kb-textarea" placeholder="粘贴 JSON 数组…"></textarea>
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
</template>

<script setup lang="ts">
/**
 * 闪卡大厅（FlashCardsHub）
 * 整合「学习闪卡 / 推荐复习 / 我的闪卡」三个功能模块：
 * - 学习闪卡 Tab：调用 learningApi.flashcards() 拉取系统闪卡，支持翻转/三档评分/进度统计
 * - 推荐复习 Tab：调用 mistakesApi.list({ mastered: 0 }) 取未掌握错题，前端过滤 source=flashcard，
 *   支持翻转 / 标记掌握（同步调 mistakesApi.markMastered）
 * - 我的闪卡 Tab：调用 learningApi.myFlashcards() 等 CRUD 接口，支持增删改查/导入导出/AI 生成
 * - 顶部统一搜索框：按当前 Tab 过滤（学习/推荐前端过滤、我的侧同步后端参数）
 * 设计遵循项目 --kb-* CSS 变量体系与组件规范，Toast/确认弹窗统一走 @/utils/toast。
 */
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api/learning'
import { categoriesApi } from '@/api/categories'
import { docsApi } from '@/api'
import { mistakesApi } from '@/api/mistakes'
import type { FlashcardVO, FlashcardInput, CategoryVO, DocVO, MistakeVO } from '@/api/types'
import { markReviewed, dateStr } from '@/utils/studySession'
import { notify, confirmDialog, getApiError } from '@/utils/toast'

const router = useRouter()

// ===================== Tab 切换 =====================
const activeTab = ref<'learn' | 'review' | 'mine'>('learn')

// ===================== 全局搜索 =====================
const globalKeyword = ref('')
const searchPlaceholder = computed(() => {
  if (activeTab.value === 'learn') return '在系统闪卡中搜索问题/答案…'
  if (activeTab.value === 'review') return '在推荐复习中搜索问题/答案…'
  return '在个人闪卡中搜索正面/背面/标签…'
})
const onGlobalSearch = () => {
  // 我的侧需同步后端参数；学习/推荐侧为 computed 前端过滤
  if (activeTab.value === 'mine') reloadMine()
}
const clearSearch = () => {
  globalKeyword.value = ''
  if (activeTab.value === 'mine') reloadMine()
}

// ===================== 学习闪卡 State =====================
interface FlashCard {
  id: number
  category: string
  difficulty: number
  question: string
  answer: string
}
type Rating = 'unknown' | 'familiar' | 'mastered'

const learnLoading = ref(false)
const reviewing = ref(false)
const feedback = ref('')
let feedbackTimer: ReturnType<typeof setTimeout> | undefined

const categories = ref<string[]>(['全部'])
const selectedCategory = ref('全部')
const learnIndex = ref(0)
const isFlipped = ref(false)
const learnCards = ref<FlashCard[]>([])

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

// 将后端 FlashcardVO 映射为本地展示用的闪卡结构（缺省字段兜底）
const mapCard = (f: FlashcardVO): FlashCard => ({
  id: f.id,
  category: f.category || '通用',
  difficulty: f.difficulty || 1,
  question: f.front || '',
  answer: f.back || '',
})

// 学习侧：分类 + 关键词前端过滤
const learnFiltered = computed(() => {
  return learnCards.value.filter((card) => {
    const catOk = selectedCategory.value === '全部' || card.category === selectedCategory.value
    if (!catOk) return false
    const kw = globalKeyword.value.trim().toLowerCase()
    if (!kw) return true
    return card.question.toLowerCase().includes(kw) || card.answer.toLowerCase().includes(kw)
  })
})

const currentCard = computed(() => learnFiltered.value[learnIndex.value] || null)

const learnProgress = computed(() => {
  if (learnFiltered.value.length === 0) return 0
  return Math.round(((learnIndex.value + 1) / learnFiltered.value.length) * 100)
})

const newCountComputed = computed(() => {
  return Math.max(0, learnFiltered.value.length - knownCount.value - reviewCount.value)
})
watch(newCountComputed, (v) => { newCount.value = v })

watch(selectedCategory, () => {
  learnIndex.value = 0
  isFlipped.value = false
})
watch(globalKeyword, () => {
  learnIndex.value = 0
  isFlipped.value = false
})

const getDifficultyLabel = (difficulty?: number) => {
  if (difficulty === 2) return '困难'
  if (difficulty === 3) return '高级'
  return '基础'
}
const flipCard = () => { isFlipped.value = !isFlipped.value }
const prevCard = () => {
  if (learnIndex.value > 0) { isFlipped.value = false; learnIndex.value-- }
}
const nextCard = () => {
  if (learnIndex.value < learnFiltered.value.length - 1) { isFlipped.value = false; learnIndex.value++ }
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
}

const ratingToQuality = (rating: Rating): number => {
  if (rating === 'unknown') return 1
  if (rating === 'familiar') return 3
  return 5
}

const showFeedback = (msg: string) => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedback.value = msg
  feedbackTimer = setTimeout(() => { feedback.value = '' }, 1600)
}

// 提交评分：调用后端 SM-2 复习接口，更新本地统计并推进到下一张
const rateCard = async (rating: Rating) => {
  if (reviewing.value || !currentCard.value) return
  reviewing.value = true
  try {
    await learningApi.reviewFlashcard(currentCard.value.id, ratingToQuality(rating))
    markReviewed(dateStr(new Date()), String(currentCard.value.id))
    todayCount.value++
    todayLearned.value++
    if (rating === 'mastered') { knownCount.value++; correctCount.value++ }
    else if (rating === 'familiar') { reviewCount.value++; correctCount.value++ }
    else { reviewCount.value++ }
    // 未完全掌握的卡片归集到错题本，便于后续针对性复习
    if (rating !== 'mastered' && currentCard.value.question) {
      mistakesApi.add({
        question: currentCard.value.question,
        wrongAnswer: '未完全掌握该知识点',
        correctAnswer: currentCard.value.answer,
        category: currentCard.value.category,
        difficulty: currentCard.value.difficulty,
        source: 'flashcard',
      }).catch(() => { /* 归集失败不阻断复习 */ })
    }
    if (rating === 'unknown') showFeedback('已记录 · 明天再复习这张')
    else if (rating === 'familiar') showFeedback('已记录 · 继续保持')
    else showFeedback('已记录 · 掌握得不错')
    if (learnIndex.value < learnFiltered.value.length - 1) {
      isFlipped.value = false
      learnIndex.value++
    }
  } catch (e: unknown) {
    notify.error(getApiError(e, '评分提交失败'))
    if (learnIndex.value < learnFiltered.value.length - 1) {
      isFlipped.value = false
      learnIndex.value++
    }
  } finally {
    reviewing.value = false
  }
}

const loadLearnCards = async () => {
  learnLoading.value = true
  try {
    const list = await learningApi.flashcards()
    learnCards.value = list.map(mapCard)
    learnTotal.value = list.length
    const uniqueCats = Array.from(new Set(list.map((f) => f.category || '通用')))
    categories.value = ['全部', ...uniqueCats]
    newCount.value = learnFiltered.value.length
    studyTimer = setInterval(() => { studySeconds.value++ }, 1000)
  } catch (e: unknown) {
    notify.error(getApiError(e, '加载系统闪卡失败'))
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
    showReviewFeedback('已记录 · 继续保持')
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
    notify.error(getApiError(e, '标记掌握失败'))
  } finally {
    markingMastered.value = false
  }
}

const loadReviewCards = async () => {
  reviewLoading.value = true
  try {
    // 取未掌握错题（mastered=0），前端过滤 source=flashcard
    const res = await mistakesApi.list({ mastered: 0, pageNum: 1, pageSize: 200 })
    const all = res?.list || []
    reviewCards.value = all.filter((m) => m.source === 'flashcard')
    reviewIndex.value = 0
    isReviewFlipped.value = false
    reviewMasteredCount.value = 0
  } catch (e: unknown) {
    notify.error(getApiError(e, '加载推荐复习失败'))
    reviewCards.value = []
  } finally {
    reviewLoading.value = false
  }
}

// ===================== 我的闪卡 State =====================
const mineLoading = ref(false)
const submitting = ref(false)
const generating = ref(false)
const importing = ref(false)
const mineCards = ref<FlashcardVO[]>([])
const categories2 = ref<CategoryVO[]>([])
const dialogDocs = ref<DocVO[]>([])
const previewList = ref<FlashcardVO[]>([])
const flippedId = ref<number | null>(null)
const viewMode = ref<'list' | 'card'>('card')
const selectedIds = ref<number[]>([])

const mineTotal = ref(0)

const filters = reactive({
  difficulty: undefined as number | undefined,
  sourceType: '' as string,
  categoryId: undefined as number | undefined,
})

const allSelected = computed(() => mineCards.value.length > 0 && selectedIds.value.length === mineCards.value.length)
const someSelected = computed(() => selectedIds.value.length > 0 && !allSelected.value)

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
const formData = reactive<FlashcardInput>({
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

const reloadMine = async () => {
  mineLoading.value = true
  try {
    mineCards.value = await learningApi.myFlashcards({
      keyword: globalKeyword.value || undefined,
      difficulty: filters.difficulty,
      categoryId: filters.categoryId,
      sourceType: filters.sourceType || undefined,
    })
    mineTotal.value = mineCards.value.length
    selectedIds.value = selectedIds.value.filter(id => mineCards.value.some(c => c.id === id))
  } catch (e: unknown) {
    notify.error(getApiError(e, '加载我的闪卡失败'))
  } finally {
    mineLoading.value = false
  }
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
  Object.assign(formData, {
    front: '', back: '', difficulty: 1, category: '', tags: '',
    categoryId: undefined, docId: undefined,
  })
}
const openCreate = () => {
  resetForm()
  formDialog.mode = 'create'
  formDialog.id = undefined
  formDialog.visible = true
}
const openEdit = (c: FlashcardVO) => {
  resetForm()
  formDialog.mode = 'edit'
  formDialog.id = c.id
  Object.assign(formData, {
    front: c.front || '', back: c.back || '', difficulty: c.difficulty || 1,
    category: c.category || '', tags: c.tags || '',
    categoryId: c.categoryId, docId: c.docId,
  })
  formDialog.visible = true
}
const closeForm = () => { formDialog.visible = false }

const submitForm = async () => {
  if (!formData.front.trim() || !formData.back.trim()) {
    notify.error('正面和背面内容必填')
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
    if (formDialog.mode === 'edit' && formDialog.id) {
      await learningApi.updateMyFlashcard(formDialog.id, payload)
      notify.success('已更新')
    } else {
      await learningApi.createMyFlashcard(payload)
      notify.success('已新增')
    }
    closeForm()
    await reloadMine()
  } catch (e: unknown) {
    notify.error(getApiError(e, '保存失败'))
  } finally {
    submitting.value = false
  }
}

const removeOne = async (c: FlashcardVO) => {
  const ok = await confirmDialog(`确认删除「${truncate(c.front || '', 40)}」？`)
  if (!ok) return
  try {
    await learningApi.deleteMyFlashcard(c.id)
    notify.success('已删除')
    reloadMine()
  } catch (e: unknown) {
    notify.error(getApiError(e, '删除失败'))
  }
}

const batchDelete = async () => {
  if (selectedIds.value.length === 0) return
  const ok = await confirmDialog(`确认删除选中的 ${selectedIds.value.length} 张闪卡？`)
  if (!ok) return
  try {
    await learningApi.deleteMyFlashcards(selectedIds.value)
    notify.success(`已删除 ${selectedIds.value.length} 张`)
    selectedIds.value = []
    reloadMine()
  } catch (e: unknown) {
    notify.error(getApiError(e, '删除失败'))
  }
}

// ===================== Flip =====================
const toggleFlip = (id: number) => {
  flippedId.value = flippedId.value === id ? null : id
}
const flipInRow = (c: FlashcardVO) => toggleFlip(c.id)

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
  if (!genReady.value) { notify.error('请选择生成来源'); return }
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
    notify.success(`成功生成 ${list.length} 张闪卡`)
    await reloadMine()
  } catch (e: unknown) {
    notify.error(getApiError(e, 'AI 生成失败'))
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
    notify.success(`成功导入 ${r.inserted} 张`)
    closeImport()
    reloadMine()
  } catch (e: unknown) {
    notify.error(getApiError(e, '导入失败'))
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
    notify.success(`已导出 ${list.length} 张`)
  } catch (e: unknown) {
    notify.error(getApiError(e, '导出失败'))
  }
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
  s === 'MANUAL' ? '手动' : s === 'AI_DOC' ? 'AI·文档' : s === 'AI_KB' ? 'AI·知识库' : s === 'IMPORT' ? '导入' : '未知'
const sourceIcon = (s?: string) =>
  s === 'MANUAL' ? 'pencil' : (s === 'AI_DOC' || s === 'AI_KB') ? 'sparkles' : s === 'IMPORT' ? 'upload' : 'help-circle'
const sourceClass = (s?: string) =>
  s === 'MANUAL' ? 'src-manual' : (s === 'AI_DOC' || s === 'AI_KB') ? 'src-ai' : s === 'IMPORT' ? 'src-imp' : ''
const formatTime = (t?: string) => {
  if (!t) return '—'
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ===================== 生命周期 =====================
onMounted(async () => {
  // 并行加载学习侧卡片、推荐侧卡片与我的侧分类；我的侧卡片列表在切到该 Tab 时懒加载
  await Promise.all([loadLearnCards(), loadReviewCards(), loadCategories2()])
})

// 首次切到「我的闪卡」时懒加载卡片
const mineLoadedOnce = ref(false)
watch(activeTab, async (tab) => {
  if (tab === 'mine' && !mineLoadedOnce.value) {
    mineLoadedOnce.value = true
    await reloadMine()
  }
})

onUnmounted(() => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  if (reviewFeedbackTimer) clearTimeout(reviewFeedbackTimer)
  if (studyTimer) clearInterval(studyTimer)
})
</script>

<style scoped>
/* ========== 基础布局 ========== */
.flashcards-hub {
  padding: 24px 32px 80px;
  max-width: 1400px;
  margin: 0 auto;
  color: var(--kb-foreground);
  display: flex;
  flex-direction: column;
  gap: 20px;
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
  border-radius: var(--kb-radius-lg);
  background: linear-gradient(135deg, var(--kb-primary-soft), var(--kb-card));
  border: 1px solid var(--kb-border);
}
.hero-left { display: flex; align-items: center; gap: 14px; }
.back-btn {
  width: 36px; height: 36px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.back-btn:hover { color: var(--kb-primary); border-color: var(--kb-primary); }
.title-text { display: flex; flex-direction: column; gap: 2px; }
.kb-h1 { margin: 0; font-size: 22px; font-weight: 600; }
.kb-body-sm { margin: 0; color: var(--kb-muted-foreground); font-size: 13px; }

/* Hero 统计卡片组 */
.hero-stats { display: flex; gap: 14px; flex-wrap: wrap; }
.hero-stat {
  display: inline-flex; align-items: center; gap: 10px;
  padding: 10px 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  min-width: 120px;
}
.hero-stat-icon {
  width: 32px; height: 32px;
  border-radius: var(--kb-radius-sm);
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.icon-primary { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }
.icon-accent { background: rgba(16, 185, 129, 0.1); color: var(--kb-accent); }
.icon-warning { background: rgba(245, 158, 11, 0.1); color: var(--kb-warning); }
.icon-success { background: rgba(16, 185, 129, 0.12); color: var(--kb-accent); }
.hero-stat-body { display: flex; flex-direction: column; line-height: 1.2; }
.hero-stat-num { font-size: 18px; font-weight: 700; color: var(--kb-foreground); }
.hero-stat-label { font-size: 12px; color: var(--kb-muted-foreground); margin-top: 2px; }

/* ========== 标签页 ========== */
.tabs-bar {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  align-self: flex-start;
}
.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  border-radius: var(--kb-radius-md);
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.tab-btn:hover { color: var(--kb-foreground); }
.tab-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.25);
}
.tab-count {
  min-width: 20px; height: 20px; padding: 0 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  color: var(--kb-foreground);
  font-size: 11px; font-weight: 600;
  display: inline-flex; align-items: center; justify-content: center;
}
.tab-btn.active .tab-count {
  background: rgba(255,255,255,0.25);
  color: var(--kb-primary-foreground);
}

/* ========== 全局搜索 ========== */
.global-search {
  position: relative;
  max-width: 520px;
}
.search-icon {
  position: absolute; left: 12px; top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
}
.search-input {
  width: 100%;
  height: 40px;
  padding: 0 36px 0 36px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.search-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}
.clear-btn {
  position: absolute; right: 8px; top: 50%;
  transform: translateY(-50%);
  width: 24px; height: 24px;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer;
}
.clear-btn:hover { color: var(--kb-foreground); }

/* ========== Tab 面板 ========== */
.tab-panel { display: flex; flex-direction: column; gap: 18px; }

/* ========== 推荐复习说明条 ========== */
.review-intro {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-left: 3px solid var(--kb-primary);
}
.intro-icon { color: var(--kb-primary); flex-shrink: 0; }
.intro-text { flex: 1; min-width: 0; }
.intro-title { margin: 0; font-size: 14px; font-weight: 600; color: var(--kb-foreground); }
.intro-desc { margin: 2px 0 0; font-size: 13px; color: var(--kb-muted-foreground); line-height: 1.5; }

/* ========== 学习侧：进度块 ========== */
.progress-block {
  display: flex; align-items: center; gap: 24px;
  padding: 16px 20px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
}
.progress-left { flex: 1; min-width: 0; }
.progress-meta {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8px;
}
.progress-label { font-size: 14px; font-weight: 500; color: var(--kb-foreground); }
.progress-value { font-size: 14px; font-weight: 600; color: var(--kb-primary); }
.progress-track {
  height: 10px; border-radius: 999px; overflow: hidden;
  background: var(--kb-muted);
}
.progress-fill {
  height: 100%; border-radius: 999px;
  background: var(--kb-primary);
  transition: width 0.3s ease;
}
.progress-right { display: flex; align-items: center; gap: 24px; }
.stat-cell { text-align: center; }
.stat-num { font-size: 18px; font-weight: 700; line-height: 1.2; }
.stat-known { color: var(--kb-accent); }
.stat-review { color: var(--kb-warning); }
.stat-new { color: var(--kb-destructive); }
.kb-caption { font-size: 12px; color: var(--kb-muted-foreground); margin-top: 2px; }

/* ========== 学习侧工具行 ========== */
.learn-toolbar {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}

/* ========== 按钮通用 ========== */
.btn-primary, .btn-secondary, .btn-ghost {
  display: inline-flex; align-items: center; gap: 6px;
  height: 36px; padding: 0 14px;
  border-radius: var(--kb-radius-md);
  border: 1px solid transparent;
  font-size: 14px; font-weight: 500;
  cursor: pointer; transition: all 0.15s;
  white-space: nowrap;
}
.btn-primary { background: var(--kb-primary); color: var(--kb-primary-foreground); }
.btn-primary:hover { filter: brightness(1.08); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secondary {
  background: var(--kb-card); color: var(--kb-foreground);
  border-color: var(--kb-border);
}
.btn-secondary:hover { border-color: var(--kb-primary); color: var(--kb-primary); }
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-secondary.danger:hover { border-color: var(--kb-destructive); color: var(--kb-destructive); }
.btn-ghost {
  background: transparent; color: var(--kb-muted-foreground);
  padding: 0 10px; height: 36px;
}
.btn-ghost:hover { color: var(--kb-primary); background: rgba(59,111,224,0.08); }
.badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 6px;
  border-radius: 999px;
  background: var(--kb-primary); color: var(--kb-primary-foreground);
  font-size: 11px; font-weight: 600;
}

/* ========== 状态区 ========== */
.state-area {
  padding: 64px 24px; text-align: center;
  background: var(--kb-card);
  border: 1px dashed var(--kb-border);
  border-radius: var(--kb-radius-lg);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.state-icon { color: var(--kb-muted-foreground); opacity: 0.6; }
.state-text { margin: 8px 0 0; font-size: 15px; color: var(--kb-foreground); font-weight: 500; }
.state-hint { color: var(--kb-muted-foreground); font-size: 13px; margin: 0; }
.empty-actions { display: inline-flex; gap: 10px; margin-top: 8px; }
.loading-spinner {
  width: 32px; height: 32px;
  border: 3px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 翻转卡 ========== */
.flip-area { display: flex; justify-content: center; }
.flip-card {
  width: 100%; max-width: 900px; height: 360px;
  perspective: 1000px; cursor: pointer;
}
.flip-card-inner {
  position: relative; width: 100%; height: 100%;
  transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  transform-style: preserve-3d;
}
.flip-card.flipped .flip-card-inner { transform: rotateY(180deg); }
.flip-card-face {
  position: absolute; inset: 0;
  backface-visibility: hidden; -webkit-backface-visibility: hidden;
  border-radius: var(--kb-radius-lg);
  border: 2px solid var(--kb-border);
  background: var(--kb-card);
  display: flex; flex-direction: column;
  padding: 32px; overflow: hidden;
}
.flip-card-front { align-items: center; justify-content: center; }
.flip-card-back {
  transform: rotateY(180deg);
  border-color: var(--kb-primary);
  justify-content: flex-start;
}
.face-tags { display: flex; align-items: center; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; justify-content: center; }
.face-tag { padding: 4px 10px; border-radius: 999px; font-size: 13px; font-weight: 500; }
.tag-category { background: rgba(59, 111, 224, 0.08); color: var(--kb-primary); }
.tag-sub { background: rgba(16, 185, 129, 0.08); color: var(--kb-accent); }
.tag-source { background: rgba(245, 158, 11, 0.1); color: var(--kb-warning); }
.face-question {
  font-size: 24px; font-weight: 700; text-align: center;
  color: var(--kb-foreground); margin: 0 0 16px 0;
  line-height: 1.4; word-break: keep-all; overflow-wrap: break-word;
}
.face-hint { text-align: center; font-size: 14px; color: var(--kb-muted-foreground); margin: 0 0 32px 0; }
.flip-pill {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 8px 16px; border-radius: 999px;
  background: var(--kb-muted); color: var(--kb-muted-foreground);
  font-size: 14px;
}
.back-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.back-icon { color: var(--kb-warning); }
.back-label { font-size: 14px; font-weight: 600; color: var(--kb-primary); }
.back-body { flex: 1; overflow: auto; }
.back-title { font-size: 20px; font-weight: 700; color: var(--kb-foreground); margin: 0 0 12px 0; }
.back-text { font-size: 14px; line-height: 1.7; color: var(--kb-foreground); margin: 0 0 16px 0; white-space: pre-line; }
.back-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.back-tag { padding: 4px 10px; border-radius: 999px; font-size: 13px; background: rgba(59, 111, 224, 0.08); color: var(--kb-primary); }

/* ========== 学习侧操作按钮 ========== */
.action-bar { display: flex; justify-content: center; gap: 16px; flex-wrap: wrap; }
.action-btn {
  display: inline-flex; align-items: center; gap: 8px;
  height: 44px; padding: 0 24px;
  border-radius: var(--kb-radius-md);
  font-size: 14px; font-weight: 500;
  cursor: pointer; transition: background-color 0.15s, color 0.15s, border-color 0.15s;
  border: none;
}
.action-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-outline { border: 1px solid var(--kb-border); background: var(--kb-card); color: var(--kb-foreground); }
.btn-outline:not(:disabled):hover { border-color: var(--kb-primary); color: var(--kb-primary); }
.btn-danger { background: var(--kb-destructive); color: var(--kb-destructive-foreground); }
.btn-warning { background: var(--kb-warning); color: var(--kb-warning-foreground); }
.btn-success { background: var(--kb-accent); color: var(--kb-accent-foreground); }

/* ========== 反馈提示 ========== */
.feedback-text { text-align: center; font-size: 14px; font-weight: 500; color: var(--kb-muted-foreground); margin: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ========== 我的侧工具栏 ========== */
.toolbar-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 16px 18px;
  display: flex; flex-direction: column; gap: 14px;
}
.filter-row, .action-row {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.action-row { justify-content: space-between; }
.action-left, .action-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.kb-select, .kb-input {
  height: 38px; padding: 0 12px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px; outline: none;
  min-width: 140px;
  transition: border-color 0.15s;
}
.kb-select:focus, .kb-input:focus { border-color: var(--kb-primary); }
.kb-textarea {
  width: 100%; padding: 10px 12px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px; line-height: 1.55;
  resize: vertical; outline: none;
  transition: border-color 0.15s; font-family: inherit;
}
.kb-textarea:focus { border-color: var(--kb-primary); }

.view-switch {
  display: inline-flex; padding: 3px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.view-btn {
  height: 30px; padding: 0 12px;
  border-radius: var(--kb-radius-sm);
  border: none; background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 13px;
  display: inline-flex; align-items: center; gap: 5px;
  cursor: pointer; transition: all 0.15s;
}
.view-btn:hover { color: var(--kb-foreground); }
.view-btn.active {
  background: var(--kb-primary); color: var(--kb-primary-foreground);
  box-shadow: 0 2px 6px rgba(59,111,224,0.25);
}
.spinning { animation: spin 1s linear infinite; }

/* ========== 列表视图 ========== */
.list-wrap {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  overflow: hidden;
  overflow-x: auto; /* 小屏允许表格横向滚动，避免列被裁切/页面溢出 */
}
.kb-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.kb-table thead th {
  text-align: left; padding: 12px 16px;
  font-weight: 600; font-size: 13px;
  color: var(--kb-muted-foreground);
  background: var(--kb-background);
  border-bottom: 1px solid var(--kb-border);
  white-space: nowrap;
}
.kb-table tbody td { padding: 14px 16px; border-bottom: 1px solid var(--kb-border); vertical-align: top; }
.kb-table tbody tr:last-child td { border-bottom: none; }
.kb-table tbody tr:hover { background: rgba(59,111,224,0.03); }
.kb-table input[type=checkbox] { accent-color: var(--kb-primary); width: 15px; height: 15px; }
.cell-front, .cell-back { line-height: 1.55; }
.cell-front { font-weight: 500; }
.cell-back { color: var(--kb-muted-foreground); }
.text-muted { color: var(--kb-muted-foreground); font-size: 13px; }
.tabular-nums { font-variant-numeric: tabular-nums; }

.mini-tags { display: inline-flex; gap: 4px; margin-right: 6px; }
.mini-tag {
  font-size: 11px; color: var(--kb-primary);
  background: rgba(59,111,224,0.1);
  border-radius: 6px; padding: 1px 6px;
}

.diff-badge {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 2px 10px; border-radius: 999px;
  font-size: 12px; font-weight: 500;
}
.diff-easy { background: rgba(34,197,94,0.12); color: #16a34a; }
.diff-medium { background: rgba(59,111,224,0.12); color: var(--kb-primary); }
.diff-hard { background: rgba(239,68,68,0.12); color: #dc2626; }

.source-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 9px; border-radius: 8px;
  font-size: 12px; font-weight: 500;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.src-manual { color: #0f766e; border-color: rgba(15,118,110,0.25); background: rgba(20,184,166,0.08); }
.src-ai { color: #7c3aed; border-color: rgba(124,58,237,0.25); background: rgba(139,92,246,0.1); }
.src-imp { color: #b45309; border-color: rgba(180,83,9,0.25); background: rgba(217,119,6,0.08); }

.row-actions { display: inline-flex; gap: 4px; flex-wrap: wrap; }
.row-btn {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 4px 10px; border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 12px; cursor: pointer;
  transition: all 0.15s;
}
.row-btn:hover { border-color: var(--kb-primary); color: var(--kb-primary); }
.row-btn.danger:hover { border-color: var(--kb-destructive); color: var(--kb-destructive); }

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
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
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
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all 0.15s;
}
.icon-btn:hover { color: var(--kb-primary); border-color: var(--kb-primary); }
.icon-btn.danger:hover { color: var(--kb-destructive); border-color: var(--kb-destructive); }
.fc-front-title {
  flex: 1; margin: 16px 0;
  font-size: 18px; font-weight: 600; line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fc-back-content {
  flex: 1; margin: 14px 0;
  font-size: 14.5px; line-height: 1.75;
  color: var(--kb-foreground);
  white-space: pre-wrap; word-break: break-word;
  overflow: auto;
}
.fc-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.fc-tag {
  font-size: 12px; color: var(--kb-primary);
  background: rgba(59,111,224,0.1);
  padding: 2px 8px; border-radius: 999px;
}
.fc-face-bottom {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding-top: 12px;
  border-top: 1px dashed var(--kb-border);
}
.fc-flip-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 6px 12px; border-radius: 999px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 12.5px;
  cursor: pointer; transition: all 0.15s;
}
.fc-flip-btn:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}
.fc-meta {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12.5px; color: var(--kb-muted-foreground);
}
.kb-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: 6px;
  font-size: 12px;
  background: rgba(59,111,224,0.1); color: var(--kb-primary);
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
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
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
  border-bottom: 1px solid var(--kb-border);
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
.form-label { font-size: 13px; font-weight: 500; color: var(--kb-foreground); }
.form-label .req { color: var(--kb-destructive); margin-left: 2px; }
.form-hint { margin: 4px 0 0; font-size: 12.5px; color: var(--kb-muted-foreground); }
.modal-footer {
  padding: 14px 22px;
  border-top: 1px solid var(--kb-border);
  display: flex; justify-content: flex-end; gap: 10px;
}

.seg-group {
  display: inline-flex; padding: 3px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  width: fit-content;
}
.seg-btn {
  padding: 7px 16px;
  border: none; background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 13.5px;
  border-radius: 7px; cursor: pointer;
  transition: all 0.15s;
}
.seg-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  box-shadow: 0 2px 6px rgba(59,111,224,0.25);
}

.count-stepper { display: inline-flex; align-items: center; gap: 8px; }
.stepper-btn {
  width: 34px; height: 34px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 16px; cursor: pointer;
  transition: all 0.15s;
}
.stepper-btn:hover:not(:disabled) { border-color: var(--kb-primary); color: var(--kb-primary); }
.stepper-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.stepper-input {
  width: 72px; height: 34px;
  padding: 0 10px; text-align: center;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px; outline: none;
  -moz-appearance: textfield;
}
.stepper-input::-webkit-outer-spin-button,
.stepper-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.stepper-hint { font-size: 12px; color: var(--kb-muted-foreground); }

.preview-wrap {
  display: flex; flex-direction: column; gap: 10px;
  max-height: 320px; overflow: auto; padding: 4px;
}
.preview-card {
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  border-radius: 12px;
  padding: 12px 14px;
  position: relative;
}
.preview-index {
  position: absolute; top: 10px; right: 12px;
  font-size: 11px; font-weight: 600;
  color: var(--kb-primary);
  background: rgba(59,111,224,0.1);
  padding: 2px 8px; border-radius: 999px;
}
.preview-front, .preview-back {
  font-size: 13.5px; line-height: 1.6;
  color: var(--kb-foreground);
}
.preview-front { margin-bottom: 4px; font-weight: 500; }
.preview-back { color: var(--kb-muted-foreground); }

.import-hint {
  background: var(--kb-background);
  border: 1px dashed var(--kb-border);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 14px;
  font-size: 13px; line-height: 1.7;
  color: var(--kb-muted-foreground);
}
.import-hint b { color: var(--kb-foreground); }
.import-hint code {
  background: rgba(59,111,224,0.1);
  color: var(--kb-primary);
  padding: 1px 6px; border-radius: 5px;
  font-size: 12.5px;
}
.sample-block {
  margin: 8px 0 0;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  font-family: ui-monospace, Menlo, monospace;
  overflow-x: auto;
  color: var(--kb-foreground);
}
.parse-error {
  margin: 8px 0 0;
  color: #dc2626;
  font-size: 13px;
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
  color: var(--kb-foreground);
}

/* 可点击翻转卡（div[role=button]）：补 hover / active / focus-visible */
.flip-card:hover .flip-card-face {
  border-color: var(--kb-primary);
}
.flip-card:active {
  transform: scale(0.99);
}
.flip-card:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: var(--kb-radius-lg);
}
.flip-card:focus-visible .flip-card-face {
  border-color: var(--kb-primary);
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
  .face-question { font-size: 20px; }
  .action-bar { gap: 8px; }
  .action-btn { height: 40px; padding: 0 14px; font-size: 13px; }
  .form-grid { grid-template-columns: 1fr; }
  .col-span-2 { grid-column: span 1; }
  .card-grid { grid-template-columns: 1fr; }
}
</style>
