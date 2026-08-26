<template>
  <div class="lc-page animate-fade-in">
    <!-- ===== 页面头部：问候语 + 快捷统计 ===== -->
    <header class="lc-header">
      <div class="header-left">
        <h1 class="page-title">学习中心</h1>
        <p class="page-subtitle">{{ greeting }}，今天也要保持学习节奏哦~</p>
      </div>
      <div class="header-stats">
        <div class="hs-item">
          <Icon name="clock" :size="18" class="hs-icon" />
          <div>
            <span class="hs-value">{{ studyData.studyMinutes }}<small>分</small></span>
            <span class="hs-label">今日时长</span>
          </div>
        </div>
        <div class="hs-item">
          <Icon name="flame" :size="18" class="hs-icon hs-icon-flame" />
          <div>
            <span class="hs-value">{{ studyData.streakDays }}<small>天</small></span>
            <span class="hs-label">连续打卡</span>
          </div>
        </div>
        <div class="hs-item">
          <Icon name="star" :size="18" class="hs-icon hs-icon-star" />
          <div>
            <span class="hs-value">{{ totalPoints }}<small>pts</small></span>
            <span class="hs-label">学习积分</span>
          </div>
        </div>
      </div>
    </header>

    <!-- ===== Tab 导航 ===== -->
    <nav class="lc-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['lc-tab', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >
        <Icon :name="tab.icon" :size="18" />
        <span>{{ tab.label }}</span>
        <span v-if="tab.badge" class="tab-badge">{{ tab.badge }}</span>
      </button>
    </nav>

    <!-- ===== Tab 内容区 ===== -->
    <main class="lc-content">
      <!-- ========== 1. 仪表盘 ========== -->
      <section v-show="activeTab === 'dashboard'" class="tab-panel animate-fade-in">
        <!-- 第一行：环形进度 + 统计卡片 -->
        <div class="grid-3-2">
          <Card hoverable>
            <template #header>
              <div class="card-header-row">
                <div class="card-header-title">
                  <Icon name="target" :size="20" />
                  <h2>今日学习目标</h2>
                </div>
                <Badge variant="primary">{{ GOAL_MINUTES }}分钟/天</Badge>
              </div>
            </template>
            <div class="dashboard-hero">
              <!-- 环形进度 -->
              <div class="progress-ring">
                <svg class="ring-svg" viewBox="0 0 120 120">
                  <circle cx="60" cy="60" r="52" stroke="var(--kb-border)" stroke-width="8" fill="none" />
                  <circle
                    cx="60" cy="60" r="52"
                    stroke="url(#dashGradient)"
                    stroke-width="8"
                    fill="none"
                    stroke-linecap="round"
                    :stroke-dasharray="circumference"
                    :stroke-dashoffset="strokeDashoffset"
                    class="ring-progress"
                  />
                  <defs>
                    <linearGradient id="dashGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" style="stop-color:#3B6FE0" />
                      <stop offset="100%" style="stop-color:#6F9AF2" />
                    </linearGradient>
                  </defs>
                </svg>
                <div class="ring-center">
                  <span class="ring-value">{{ studyData.goalProgress }}%</span>
                  <span class="ring-label">目标完成</span>
                </div>
              </div>
              <!-- 统计数字 -->
              <div class="hero-stats">
                <div class="hero-stat">
                  <Icon name="check-circle" :size="20" class="stat-icon-green" />
                  <span class="stat-num">{{ studyData.completedTasks }}/{{ studyData.totalTasks }}</span>
                  <span class="stat-text">已完成任务</span>
                </div>
                <div class="hero-stat">
                  <Icon name="clock" :size="20" class="stat-icon-blue" />
                  <span class="stat-num">{{ studyData.studyMinutes }}</span>
                  <span class="stat-text">学习时长(分)</span>
                </div>
                <div class="hero-stat">
                  <Icon name="flame" :size="20" class="stat-icon-orange" />
                  <span class="stat-num">{{ studyData.streakDays }}</span>
                  <span class="stat-text">连续天数</span>
                </div>
                <div class="hero-stat">
                  <Icon name="zap" :size="20" class="stat-icon-purple" />
                  <span class="stat-num">{{ studyData.pomodorosCompleted }}</span>
                  <span class="stat-text">今日番茄</span>
                </div>
              </div>
            </div>
          </Card>

          <!-- 快捷入口 -->
          <Card hoverable>
            <template #header>
              <div class="card-header-title">
                <Icon name="rocket" :size="20" />
                <h2>快捷入口</h2>
              </div>
            </template>
            <div class="quick-actions">
              <button
                v-for="qa in quickActions"
                :key="qa.label"
                class="qa-btn"
                @click="qa.action"
              >
                <div :class="['qa-icon', qa.color]">
                  <Icon :name="qa.icon" :size="22" />
                </div>
                <span class="qa-label">{{ qa.label }}</span>
              </button>
            </div>
          </Card>
        </div>

        <!-- 第二行：周学习图表 + 最近活动 -->
        <div class="grid-2">
          <Card hoverable>
            <template #header>
              <div class="card-header-row">
                <div class="card-header-title">
                  <Icon name="bar-chart-2" :size="20" />
                  <h2>本周学习时长</h2>
                </div>
                <span class="text-sm" style="color: var(--kb-muted-foreground);">
                  总计 {{ weeklyTotal }} 分钟
                </span>
              </div>
            </template>
            <div class="weekly-chart">
              <div
                v-for="(day, i) in weeklyData"
                :key="i"
                class="chart-bar-wrapper"
              >
                <div class="chart-bar-container">
                  <div
                    class="chart-bar"
                    :style="{ height: `${(day.minutes / maxMinutes) * 100}%` }"
                    :class="{ 'is-today': day.isToday }"
                  >
                    <span v-if="day.minutes > 0" class="chart-bar-value">{{ day.minutes }}</span>
                  </div>
                </div>
                <span class="chart-label">{{ day.label }}</span>
              </div>
            </div>
          </Card>

          <Card hoverable>
            <template #header>
              <div class="card-header-title">
                  <Icon name="trending-up" :size="20" />
                  <h2>最近学习活动</h2>
                </div>
            </template>
            <div class="activity-timeline">
              <div v-for="(act, i) in recentActivities" :key="i" class="timeline-item">
                <div :class="['timeline-dot', act.type]">
                  <Icon :name="act.icon" :size="14" />
                </div>
                <div class="timeline-body">
                  <p class="timeline-title">{{ act.title }}</p>
                  <span class="timeline-time">{{ act.time }}</span>
                </div>
                <span v-if="act.points" class="timeline-points">+{{ act.points }}</span>
              </div>
            </div>
          </Card>
        </div>
      </section>

      <!-- ========== 2. 学习路径推荐 ========== -->
      <section v-show="activeTab === 'paths'" class="tab-panel animate-fade-in">
        <!-- AI 推荐横幅 -->
        <Card class="ai-path-banner" hoverable>
          <div class="banner-content">
            <div class="banner-icon">
              <Icon name="sparkles" :size="32" />
            </div>
            <div class="banner-text">
              <h2>AI 个性化学习路径</h2>
              <p>基于你的学习目标和当前水平，智能推荐最适合你的学习路线</p>
            </div>
            <Button variant="primary" @click="goToLearningPaths">
              <Icon name="arrow-right" :size="16" />
              生成路径
            </Button>
          </div>
        </Card>

        <!-- 进行中的路径 -->
        <div class="section-header">
          <h3>进行中的学习路径</h3>
          <span class="section-count">{{ inProgressPaths.length }} 个</span>
        </div>
        <div class="path-grid">
          <Card
            v-for="path in inProgressPaths"
            :key="path.id"
            hoverable
            class="path-card"
            @click="goToPath(path.id)"
          >
            <div :class="['path-cover', path.coverGradient]">
              <Icon :name="getPathIcon(path.icon)" :size="28" class="path-cover-icon" />
              <Badge :variant="path.difficulty === 'beginner' ? 'success' : path.difficulty === 'intermediate' ? 'warning' : 'danger'">
                {{ difficultyLabel(path.difficulty) }}
              </Badge>
            </div>
            <div class="path-body">
              <h4 class="path-title">{{ path.title }}</h4>
              <p class="path-desc">{{ path.description }}</p>
              <div class="path-progress">
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: `${path.progress}%` }" />
                </div>
                <span class="progress-text">{{ path.progress }}%</span>
              </div>
              <div class="path-meta">
                <span><Icon name="book" :size="14" /> {{ path.chaptersCount }} 章</span>
                <span><Icon name="clock" :size="14" /> {{ path.totalDuration }} 分</span>
              </div>
            </div>
          </Card>
        </div>

        <!-- 推荐路径 -->
        <div class="section-header" style="margin-top: 28px;">
          <h3>推荐学习路径</h3>
          <span class="section-count">{{ recommendedPaths.length }} 个</span>
        </div>
        <div class="path-grid">
          <Card
            v-for="path in recommendedPaths"
            :key="path.id"
            hoverable
            class="path-card"
            @click="goToPath(path.id)"
          >
            <div :class="['path-cover', path.coverGradient]">
              <Icon :name="getPathIcon(path.icon)" :size="28" class="path-cover-icon" />
              <Badge :variant="path.difficulty === 'beginner' ? 'success' : path.difficulty === 'intermediate' ? 'warning' : 'danger'">
                {{ difficultyLabel(path.difficulty) }}
              </Badge>
            </div>
            <div class="path-body">
              <h4 class="path-title">{{ path.title }}</h4>
              <p class="path-desc">{{ path.description }}</p>
              <p class="path-suitable"><Icon name="users" :size="14" /> {{ path.suitableFor }}</p>
              <div class="path-meta">
                <span><Icon name="book" :size="14" /> {{ path.chaptersCount }} 章</span>
                <span><Icon name="clock" :size="14" /> {{ path.totalDuration }} 分</span>
              </div>
            </div>
          </Card>
        </div>
      </section>

      <!-- ========== 3. 学习资源分类与检索 ========== -->
      <section v-show="activeTab === 'resources'" class="tab-panel animate-fade-in">
        <Card hoverable>
          <!-- 搜索栏 -->
          <div class="resource-search">
            <div class="search-input-wrapper">
              <Icon name="search" :size="20" class="search-icon" />
              <input
                v-model="resourceSearch"
                type="text"
                placeholder="搜索学习资源、文档、教程..."
                class="search-input"
              />
              <button v-if="resourceSearch" class="search-clear" @click="resourceSearch = ''">
                <Icon name="x" :size="16" />
              </button>
            </div>
          </div>
          <!-- 分类筛选 -->
          <div class="resource-filters">
            <button
              v-for="cat in resourceCategories"
              :key="cat"
              :class="['kb-filter-btn', { active: activeCategory === cat }]"
              @click="activeCategory = cat"
            >
              {{ cat }}
            </button>
          </div>
        </Card>

        <!-- 资源列表 -->
        <div class="section-header" style="margin-top: 20px;">
          <h3>{{ activeCategory === '全部' ? '全部资源' : activeCategory }}</h3>
          <span class="section-count">{{ filteredResources.length }} 个结果</span>
        </div>
        <div v-if="filteredResources.length > 0" class="resource-grid">
          <Card
            v-for="res in filteredResources"
            :key="res.id"
            hoverable
            class="resource-card"
            @click="openResource(res)"
          >
            <div class="resource-top">
              <div :class="['resource-type-icon', res.typeColor]">
                <Icon :name="res.typeIcon" :size="20" />
              </div>
              <Badge variant="default">{{ res.type }}</Badge>
            </div>
            <h4 class="resource-title">{{ res.title }}</h4>
            <p class="resource-desc">{{ res.description }}</p>
            <div class="resource-footer">
              <span class="resource-cat">{{ res.category }}</span>
              <div class="resource-stats">
                <span><Icon name="eye" :size="14" /> {{ res.views }}</span>
                <span><Icon name="thumbs-up" :size="14" /> {{ res.likes }}</span>
              </div>
            </div>
          </Card>
        </div>
        <div v-else class="empty-state-wrapper">
          <Icon name="search" :size="48" class="empty-icon" />
          <p class="empty-text">未找到匹配的学习资源</p>
          <p class="empty-hint">试试更换关键词或分类筛选</p>
        </div>
      </section>

      <!-- ========== 4. 学习成就与积分系统 ========== -->
      <section v-show="activeTab === 'achievements'" class="tab-panel animate-fade-in">
        <!-- 积分总览 -->
        <Card hoverable class="points-overview-card">
          <div class="points-overview">
            <div class="level-badge">
              <div class="level-circle">
                <span class="level-num">{{ achievementData.level }}</span>
                <span class="level-label">LV</span>
              </div>
              <div class="level-ring">
                <svg viewBox="0 0 100 100">
                  <circle cx="50" cy="50" r="46" stroke="var(--kb-border)" stroke-width="4" fill="none" />
                  <circle
                    cx="50" cy="50" r="46"
                    stroke="var(--kb-primary)"
                    stroke-width="4"
                    fill="none"
                    stroke-linecap="round"
                    :stroke-dasharray="levelCircumference"
                    :stroke-dashoffset="levelDashoffset"
                    class="level-ring-progress"
                  />
                </svg>
              </div>
            </div>
            <div class="points-info">
              <h2>{{ achievementData.title }}</h2>
              <p class="points-current">{{ achievementData.currentPoints }} / {{ achievementData.nextLevelPoints }} 积分</p>
              <div class="points-bar">
                <div class="points-fill" :style="{ width: `${levelProgress}%` }" />
              </div>
              <p class="points-hint">
                <Icon name="sparkles" :size="14" />
                距下一等级还需 {{ achievementData.nextLevelPoints - achievementData.currentPoints }} 积分
              </p>
            </div>
            <div class="points-stat-grid">
              <div class="ps-item">
                <span class="ps-value">{{ achievementData.totalBadges }}</span>
                <span class="ps-label">已获得徽章</span>
              </div>
              <div class="ps-item">
                <span class="ps-value">{{ achievementData.completedPaths }}</span>
                <span class="ps-label">完成路径</span>
              </div>
              <div class="ps-item">
                <span class="ps-value">{{ achievementData.studyDays }}</span>
                <span class="ps-label">累计学习天</span>
              </div>
            </div>
          </div>
        </Card>

        <!-- 徽章墙 -->
        <div class="section-header" style="margin-top: 24px;">
          <h3>成就徽章</h3>
          <span class="section-count">{{ unlockedBadges }}/{{ badges.length }}</span>
        </div>
        <div class="badge-grid">
          <div
            v-for="badge in badges"
            :key="badge.id"
            :class="['badge-item', { locked: !badge.unlocked }]"
          >
            <div :class="['badge-medal', `badge-${badge.rarity}`]">
              <Icon :name="badge.icon" :size="32" />
            </div>
            <span class="badge-name">{{ badge.name }}</span>
            <span class="badge-desc">{{ badge.unlocked ? badge.description : '???' }}</span>
          </div>
        </div>

        <!-- 积分明细 -->
        <div class="section-header" style="margin-top: 28px;">
          <h3>积分明细</h3>
        </div>
        <Card hoverable>
          <div class="points-history">
            <div v-for="(item, i) in pointsHistory" :key="i" class="ph-item">
              <div :class="['ph-icon', item.amount > 0 ? 'ph-plus' : 'ph-minus']">
                <Icon :name="item.icon" :size="16" />
              </div>
              <div class="ph-body">
                <p class="ph-title">{{ item.title }}</p>
                <span class="ph-time">{{ item.time }}</span>
              </div>
              <span :class="['ph-amount', item.amount > 0 ? 'plus' : 'minus']">
                {{ item.amount > 0 ? '+' : '' }}{{ item.amount }}
              </span>
            </div>
          </div>
        </Card>
      </section>

      <!-- ========== 5. 社区讨论区 ========== -->
      <section v-show="activeTab === 'community'" class="tab-panel animate-fade-in">
        <Card hoverable>
          <div class="community-header">
            <div class="community-search">
              <Icon name="search" :size="18" class="search-icon" />
              <input
                v-model="communitySearch"
                type="text"
                placeholder="搜索讨论话题..."
                class="search-input"
              />
            </div>
            <Button variant="primary" @click="showPostDialog = true">
              <Icon name="plus" :size="16" />
              发起讨论
            </Button>
          </div>
          <div class="resource-filters">
            <button
              v-for="tag in communityTags"
              :key="tag"
              :class="['kb-filter-btn', { active: activeCommunityTag === tag }]"
              @click="activeCommunityTag = tag"
            >
              {{ tag }}
            </button>
          </div>
        </Card>

        <!-- 热门话题 -->
        <div class="section-header" style="margin-top: 20px;">
          <h3><Icon name="flame" :size="18" /> 热门讨论</h3>
        </div>
        <div class="community-list">
          <Card
            v-for="topic in filteredTopics"
            :key="topic.id"
            hoverable
            class="topic-card"
          >
            <div class="topic-header">
              <Avatar :name="topic.author" size="sm" />
              <div class="topic-author-info">
                <span class="topic-author">{{ topic.author }}</span>
                <span class="topic-time">{{ topic.time }}</span>
              </div>
              <Badge v-if="topic.isHot" variant="danger">
                <Icon name="flame" :size="12" /> 热
              </Badge>
            </div>
            <h4 class="topic-title">{{ topic.title }}</h4>
            <p class="topic-excerpt">{{ topic.excerpt }}</p>
            <div class="topic-tags">
              <span v-for="tag in topic.tags" :key="tag" class="topic-tag">{{ tag }}</span>
            </div>
            <div class="topic-footer">
              <div class="topic-stat">
                <Icon name="eye" :size="16" />
                <span>{{ topic.views }}</span>
              </div>
              <div class="topic-stat">
                <Icon name="message-square" :size="16" />
                <span>{{ topic.replies }}</span>
              </div>
              <div class="topic-stat">
                <Icon name="thumbs-up" :size="16" />
                <span>{{ topic.likes }}</span>
              </div>
              <button class="topic-join-btn" @click="joinDiscussion(topic)">参与讨论</button>
            </div>
          </Card>
        </div>
      </section>

      <!-- ========== 6. 专注模式（原番茄钟+宠物+任务+排行） ========== -->
      <section v-show="activeTab === 'focus'" class="tab-panel animate-fade-in">
        <div class="grid-3-2">
          <div class="space-y-6">
            <!-- 番茄钟 -->
            <Card hoverable>
              <template #header>
                <div class="flex items-center justify-between">
                  <div class="card-header-title">
                    <Icon name="timer" :size="20" />
                    <h2>番茄钟</h2>
                  </div>
                  <div class="flex gap-1">
                    <button
                      v-for="mode in pomodoroModes"
                      :key="mode.value"
                      @click="switchMode(mode.value)"
                      :class="[
                        'px-3 py-1 text-[13px] rounded-full transition-colors duration-200',
                        currentMode === mode.value
                          ? 'bg-primary-500 text-white'
                          : 'bg-gray-100 text-muted-foreground hover:bg-gray-200',
                      ]"
                    >
                      {{ mode.label }}
                    </button>
                  </div>
                </div>
              </template>

              <div class="flex flex-col items-center py-6">
                <div
                  :class="[
                    'relative w-48 h-48 rounded-full flex items-center justify-center mb-6 transition-colors duration-500',
                    modeColors[currentMode].bg,
                  ]"
                >
                  <div class="absolute inset-2 rounded-full bg-card shadow-inner" />
                  <div class="relative z-10 text-center">
                    <div class="text-5xl font-bold font-mono" style="color: var(--kb-foreground);">
                      {{ formatTime(timeLeft) }}
                    </div>
                    <div class="text-sm mt-1" style="color: var(--kb-muted-foreground);">
                      {{ modeLabels[currentMode] }}
                    </div>
                  </div>
                  <svg class="absolute inset-0 w-full h-full -rotate-90" viewBox="0 0 200 200">
                    <circle cx="100" cy="100" r="94" stroke="#F3F4F6" stroke-width="6" fill="none" />
                    <circle
                      cx="100" cy="100" r="94"
                      :stroke="modeColors[currentMode].stroke"
                      stroke-width="6"
                      fill="none"
                      stroke-linecap="round"
                      :stroke-dasharray="pomodoroCircumference"
                      :stroke-dashoffset="pomodoroDashoffset"
                      class="transition-[stroke-dashoffset] duration-1000 ease-linear"
                    />
                  </svg>
                </div>

                <div class="flex items-center gap-3 mb-4">
                  <button @click="resetTimer" class="pomo-side-btn">
                    <Icon name="rotate-ccw" :size="20" />
                  </button>
                  <button
                    @click="toggleTimer"
                    :class="[
                      'w-16 h-16 rounded-full flex items-center justify-center transition-colors duration-200 shadow-lg',
                      isRunning
                        ? 'bg-warning-500 hover:bg-warning-600 text-white'
                        : 'bg-primary-500 hover:bg-primary-600 text-white',
                    ]"
                  >
                    <Icon name="pause" :size="28" v-if="isRunning" />
                    <Icon name="play" :size="28" v-else />
                  </button>
                  <button @click="skipTimer" class="pomo-side-btn">
                    <Icon name="skip-forward" :size="20" />
                  </button>
                </div>

                <div class="flex items-center gap-2">
                  <span class="text-sm text-muted-foreground">今日番茄数：</span>
                  <div class="flex gap-1">
                    <div
                      v-for="i in 6"
                      :key="i"
                      :class="[
                        'w-3 h-3 rounded-full transition-colors duration-300',
                        i <= studyData.pomodorosCompleted ? 'bg-red-400' : 'bg-gray-200',
                      ]"
                    />
                  </div>
                  <span class="text-sm font-medium text-foreground">{{ studyData.pomodorosCompleted }}/6</span>
                </div>
              </div>
            </Card>

            <!-- 学习任务 -->
            <Card hoverable>
              <template #header>
                <div class="flex items-center justify-between">
                  <div class="card-header-title">
                    <Icon name="list" :size="20" />
                    <h2>学习任务</h2>
                  </div>
                  <span class="text-sm text-muted-foreground">
                    {{ completedTasksCount }}/{{ tasks.length }} 已完成
                  </span>
                </div>
              </template>

              <div class="space-y-3">
                <div
                  v-for="task in tasks"
                  :key="task.id"
                  :class="[
                    'flex items-center gap-3 p-3 rounded-lg transition-colors duration-200 group',
                    task.completed ? 'bg-muted' : 'hover:bg-muted',
                  ]"
                >
                  <button
                    @click="toggleTask(task.id)"
                    :class="[
                      'w-5 h-5 rounded border-2 flex items-center justify-center flex-shrink-0 transition-colors duration-200',
                      task.completed
                        ? 'bg-primary-500 border-primary-500'
                        : 'border-border hover:border-primary-400',
                    ]"
                  >
                    <Icon name="check" :size="12" v-if="task.completed" />
                  </button>
                  <div class="flex-1 min-w-0">
                    <p :class="['text-sm transition-colors duration-200', task.completed ? 'text-muted-foreground line-through' : 'text-foreground']">
                      {{ task.title }}
                    </p>
                  </div>
                  <div class="flex items-center gap-2 flex-shrink-0">
                    <div class="flex items-center gap-2 text-[13px] text-muted-foreground">
                      <Icon name="clock" :size="16" />
                      <span>{{ task.duration }}分钟</span>
                    </div>
                    <button
                      @click="deleteTask(task.id)"
                      class="opacity-0 group-hover:opacity-100 p-1.5 rounded hover:bg-red-50 transition-[opacity,background-color]"
                    >
                      <Icon name="trash-2" :size="16" />
                    </button>
                  </div>
                </div>

                <div class="flex gap-2 pt-2">
                  <Input v-model="newTaskTitle" placeholder="添加新任务…" class="flex-1" @keyup.enter="addTask" />
                  <Button @click="addTask" :disabled="!newTaskTitle.trim()">
                    <Icon name="plus" :size="16" />
                    添加
                  </Button>
                </div>
              </div>
            </Card>
          </div>

          <div class="space-y-6">
            <!-- 学习伙伴 -->
            <Card hoverable>
              <template #header>
                <div class="card-header-title">
                  <Icon name="heart" :size="20" />
                  <h2>学习伙伴</h2>
                </div>
              </template>
              <div class="flex flex-col items-center">
                <div class="relative mb-4">
                  <div class="owl-container">
                    <div class="owl-body">
                      <div class="owl-belly" />
                      <div class="owl-eyes">
                        <div class="owl-eye"><div class="owl-pupil" /></div>
                        <div class="owl-eye"><div class="owl-pupil" /></div>
                      </div>
                      <div class="owl-beak" />
                      <div class="owl-wings">
                        <div class="owl-wing left" />
                        <div class="owl-wing right" />
                      </div>
                    </div>
                    <div class="owl-feet">
                      <div class="owl-foot" />
                      <div class="owl-foot" />
                    </div>
                  </div>
                </div>
                <div class="flex items-center gap-2 mb-3">
                  <span class="font-semibold text-foreground">{{ pet.name }}</span>
                  <Badge variant="primary">Lv.{{ pet.level }}</Badge>
                  <Badge variant="success">{{ pet.mood }}</Badge>
                </div>
                <div class="w-full space-y-3">
                  <div>
                    <div class="flex justify-between text-[13px] mb-1">
                      <span class="text-muted-foreground">体力值</span>
                      <span class="text-foreground font-medium">{{ pet.energy }}%</span>
                    </div>
                    <div class="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                      <div class="h-full bg-gradient-to-r from-green-400 to-emerald-500 rounded-full transition-[width] duration-500" :style="{ width: `${pet.energy}%` }" />
                    </div>
                  </div>
                  <div>
                    <div class="flex justify-between text-[13px] mb-1">
                      <span class="text-muted-foreground">经验值</span>
                      <span class="text-foreground font-medium">{{ pet.exp }}/{{ pet.maxExp }}</span>
                    </div>
                    <div class="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                      <div class="h-full bg-gradient-to-r from-blue-400 to-indigo-500 rounded-full transition-[width] duration-500" :style="{ width: `${(pet.exp / pet.maxExp) * 100}%` }" />
                    </div>
                  </div>
                </div>
                <div class="flex gap-2 mt-4 w-full">
                  <Button variant="secondary" size="sm" class="flex-1" @click="feedPet">
                    <Icon name="cookie" :size="16" /> 喂食
                  </Button>
                  <Button variant="secondary" size="sm" class="flex-1" @click="playWithPet">
                    <Icon name="gamepad-2" :size="16" /> 玩耍
                  </Button>
                </div>
              </div>
            </Card>

            <!-- 本周排行榜 -->
            <Card hoverable>
              <template #header>
                <div class="card-header-title">
                  <Icon name="trophy" :size="20" />
                  <h2>本周排行榜</h2>
                  <Badge variant="default" class="text-[12px]">演示</Badge>
                </div>
              </template>
              <div class="space-y-3">
                <div
                  v-for="item in rankList"
                  :key="item.id"
                  :class="[
                    'flex items-center gap-3 p-2 rounded-lg transition-[background-color,box-shadow] duration-200',
                    item.isCurrentUser ? 'bg-primary-50 ring-1 ring-primary-200' : 'hover:bg-muted',
                  ]"
                >
                  <div :class="['rank-circle', `rank-${item.rank}`]">{{ item.rank }}</div>
                  <Avatar :name="item.name" size="sm" />
                  <div class="flex-1 min-w-0">
                    <p :class="['text-sm font-medium truncate', item.isCurrentUser ? 'text-primary-600' : 'text-foreground']">
                      {{ item.name }}
                      <span v-if="item.isCurrentUser" class="text-[13px] text-primary-500">(我)</span>
                    </p>
                  </div>
                  <div class="flex items-center gap-2 text-sm">
                    <Icon name="clock" :size="16" />
                    <span class="font-medium text-foreground">{{ item.studyHours }}h</span>
                  </div>
                </div>
              </div>
            </Card>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
// 学习中心页：仪表盘 / 学习路径 / 资源中心 / 成就积分 / 社区讨论 / 专注模式 六大模块
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { learningPet, weeklyRank, learningPaths } from '@/data/learning'
import { learningApi } from '@/api'
import { notify } from '@/utils/toast'
import {
  loadSessions,
  addSession,
  todayMinutes,
  todayPomodoros,
  streakDays as calcStreak,
  loadPet,
  savePet,
  dateStr,
} from '@/utils/studySession'

const router = useRouter()

// ===== Tab 定义 =====
type TabKey = 'dashboard' | 'paths' | 'resources' | 'achievements' | 'community' | 'focus'
const activeTab = ref<TabKey>('dashboard')
const tabs: { key: TabKey; label: string; icon: string; badge?: string }[] = [
  { key: 'dashboard', label: '仪表盘', icon: 'pie-chart' },
  { key: 'paths', label: '学习路径', icon: 'route' },
  { key: 'resources', label: '资源中心', icon: 'book' },
  { key: 'achievements', label: '成就积分', icon: 'trophy' },
  { key: 'community', label: '社区讨论', icon: 'message-square', badge: '3' },
  { key: 'focus', label: '专注模式', icon: 'timer' },
]

// ===== 问候语 =====
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// ===== 学习数据（真实来源：本机番茄钟会话 + API 任务） =====
const GOAL_MINUTES = 120
const sessionVersion = ref(0)

interface StudyTask {
  id: number | string
  title: string
  duration: number
  completed: boolean
}

const tasks = ref<StudyTask[]>([])
const newTaskTitle = ref('')

const studyData = computed(() => {
  sessionVersion.value
  const sessions = loadSessions()
  const today = dateStr(new Date())
  const minutes = todayMinutes(sessions, today)
  return {
    completedTasks: tasks.value.filter((t) => t.completed).length,
    totalTasks: tasks.value.length,
    studyMinutes: minutes,
    streakDays: calcStreak(sessions),
    goalProgress: Math.min(100, Math.round((minutes / GOAL_MINUTES) * 100)),
    pomodorosCompleted: todayPomodoros(sessions, today),
  }
})

const totalPoints = computed(() => achievementData.value.currentPoints)

// ===== 仪表盘：环形进度 =====
const radius = 52
const circumference = 2 * Math.PI * radius
const strokeDashoffset = computed(() => {
  return circumference - (studyData.value.goalProgress / 100) * circumference
})

// ===== 仪表盘：快捷入口 =====
const quickActions = [
  { label: '开始专注', icon: 'timer', color: 'qa-red', action: () => { activeTab.value = 'focus' } },
  { label: '学习路径', icon: 'route', color: 'qa-blue', action: () => { activeTab.value = 'paths' } },
  { label: '资源中心', icon: 'book', color: 'qa-green', action: () => { activeTab.value = 'resources' } },
  { label: '我的成就', icon: 'trophy', color: 'qa-orange', action: () => { activeTab.value = 'achievements' } },
  { label: '社区讨论', icon: 'message-square', color: 'qa-purple', action: () => { activeTab.value = 'community' } },
  { label: '数据结构', icon: 'git-branch', color: 'qa-indigo', action: () => router.push('/learning/data-structures/red-black-tree') },
  { label: '学习报告', icon: 'bar-chart-2', color: 'qa-cyan', action: () => router.push('/learning/report') },
]

// ===== 仪表盘：周学习图表 =====
const weeklyData = computed(() => {
  sessionVersion.value
  const sessions = loadSessions()
  const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const today = new Date()
  const dayOfWeek = today.getDay() === 0 ? 6 : today.getDay() - 1
  return labels.map((label, i) => {
    const date = new Date(today)
    date.setDate(today.getDate() - (dayOfWeek - i))
    const ds = dateStr(date)
    const minutes = sessions
      .filter((s) => s.date === ds)
      .reduce((sum, s) => sum + s.minutes, 0)
    return { label, minutes, isToday: i === dayOfWeek }
  })
})
const weeklyTotal = computed(() => weeklyData.value.reduce((sum, d) => sum + d.minutes, 0))
const maxMinutes = computed(() => Math.max(60, ...weeklyData.value.map((d) => d.minutes)))

// ===== 仪表盘：最近活动（基于真实会话 + 任务构建） =====
const recentActivities = computed(() => {
  sessionVersion.value
  const sessions = loadSessions().slice(-5).reverse()
  const acts: { title: string; time: string; icon: string; type: string; points?: number }[] = []
  sessions.forEach((s) => {
    acts.push({
      title: `完成 ${s.minutes} 分钟专注学习`,
      time: s.date,
      icon: 'clock',
      type: 'study',
      points: s.minutes,
    })
  })
  tasks.value.filter((t) => t.completed).slice(-3).reverse().forEach((t) => {
    acts.push({
      title: `完成任务：${t.title}`,
      time: '今天',
      icon: 'check-circle',
      type: 'task',
      points: 10,
    })
  })
  if (acts.length === 0) {
    acts.push({ title: '开始你的第一次学习吧！', time: '现在', icon: 'rocket', type: 'start' })
  }
  return acts.slice(0, 6)
})

// ===== 学习路径 =====
const inProgressPaths = computed(() => learningPaths.filter((p) => p.progress > 0 && p.progress < 100))
const recommendedPaths = computed(() => learningPaths.filter((p) => p.progress === 0))
const goToPath = (id: string) => router.push(`/learning/paths?pathId=${id}`)
const goToLearningPaths = () => router.push('/learning/paths')
const difficultyLabel = (d: string) => d === 'beginner' ? '入门' : d === 'intermediate' ? '进阶' : '高级'
const getPathIcon = (iconName: string) => {
  const map: Record<string, string> = {
    'Code': 'code', 'FileCode': 'file', 'Brain': 'cpu', 'Layers': 'layers',
    'Server': 'server', 'Puzzle': 'grid',
  }
  return map[iconName] || 'book'
}

// ===== 资源中心 =====
interface ResourceItem {
  id: string
  title: string
  description: string
  category: string
  type: string
  typeIcon: string
  typeColor: string
  views: number
  likes: number
}
const resourceSearch = ref('')
const activeCategory = ref('全部')
const resourceCategories = ['全部', '前端开发', '后端开发', 'AI & 机器学习', '数据库', '算法', '工具效率']
const allResources: ResourceItem[] = [
  { id: 'r1', title: 'Vue 3 组合式 API 完全指南', description: '从 ref/reactive 到自定义 Hook，系统掌握 Composition API', category: '前端开发', type: '文档', typeIcon: 'file', typeColor: 'r-green', views: 1280, likes: 156 },
  { id: 'r2', title: 'TypeScript 类型体操进阶', description: '深入条件类型、映射类型、模板字面量类型等高级特性', category: '前端开发', type: '教程', typeIcon: 'book', typeColor: 'r-blue', views: 980, likes: 134 },
  { id: 'r3', title: 'Spring Boot 3 实战教程', description: '构建 RESTful API、集成数据库、安全认证一站式学习', category: '后端开发', type: '课程', typeIcon: 'server', typeColor: 'r-orange', views: 2100, likes: 289 },
  { id: 'r4', title: '大模型 Prompt Engineering', description: '学习提示工程最佳实践，提升 AI 应用效果', category: 'AI & 机器学习', type: '文档', typeIcon: 'cpu', typeColor: 'r-purple', views: 1560, likes: 312 },
  { id: 'r5', title: 'MySQL 性能优化实战', description: '索引优化、查询调优、分库分表等数据库性能提升技巧', category: '数据库', type: '教程', typeIcon: 'database', typeColor: 'r-cyan', views: 870, likes: 98 },
  { id: 'r6', title: '常见算法题解与思路', description: '动态规划、贪心、回溯等经典算法题型精讲', category: '算法', type: '题集', typeIcon: 'cpu', typeColor: 'r-red', views: 1340, likes: 201 },
  { id: 'r7', title: 'Git 工作流最佳实践', description: '分支管理、代码审查、冲突解决等团队协作技巧', category: '工具效率', type: '文档', typeIcon: 'grid', typeColor: 'r-indigo', views: 650, likes: 77 },
  { id: 'r8', title: 'React 18 并发特性详解', description: '深入理解 Concurrent Rendering、Suspense、useTransition', category: '前端开发', type: '教程', typeIcon: 'layers', typeColor: 'r-blue', views: 1100, likes: 145 },
  { id: 'r9', title: 'LangChain 应用开发指南', description: '使用 LangChain 构建 RAG、Agent 等 LLM 应用', category: 'AI & 机器学习', type: '课程', typeIcon: 'sparkles', typeColor: 'r-purple', views: 1890, likes: 267 },
]
const filteredResources = computed(() => {
  return allResources.filter((r) => {
    const matchCat = activeCategory.value === '全部' || r.category === activeCategory.value
    const matchSearch = !resourceSearch.value ||
      r.title.toLowerCase().includes(resourceSearch.value.toLowerCase()) ||
      r.description.toLowerCase().includes(resourceSearch.value.toLowerCase())
    return matchCat && matchSearch
  })
})
const openResource = (res: ResourceItem) => {
  notify(`正在打开：${res.title}`, 'info')
}

// ===== 成就与积分系统 =====
const achievementData = ref({
  level: 5,
  title: '学习达人',
  currentPoints: 850,
  nextLevelPoints: 1200,
  totalBadges: 6,
  completedPaths: 2,
  studyDays: 21,
})
const levelRadius = 46
const levelCircumference = 2 * Math.PI * levelRadius
const levelProgress = computed(() =>
  Math.round((achievementData.value.currentPoints / achievementData.value.nextLevelPoints) * 100),
)
const levelDashoffset = computed(() =>
  levelCircumference - (levelProgress.value / 100) * levelCircumference,
)

interface Badge {
  id: string
  name: string
  description: string
  icon: string
  rarity: 'common' | 'rare' | 'epic' | 'legendary'
  unlocked: boolean
}
const badges: Badge[] = [
  { id: 'b1', name: '初学者', description: '完成首次学习', icon: 'rocket', rarity: 'common', unlocked: true },
  { id: 'b2', name: '坚持七天', description: '连续学习7天', icon: 'flame', rarity: 'common', unlocked: true },
  { id: 'b3', name: '专注大师', description: '完成25个番茄钟', icon: 'zap', rarity: 'rare', unlocked: true },
  { id: 'b4', name: '路径探索者', description: '完成1条学习路径', icon: 'route', rarity: 'rare', unlocked: true },
  { id: 'b5', name: '知识沉淀', description: '创建10篇笔记', icon: 'book', rarity: 'rare', unlocked: true },
  { id: 'b6', name: '社区贡献者', description: '发起5次讨论', icon: 'message-square', rarity: 'rare', unlocked: true },
  { id: 'b7', name: '百日坚持', description: '累计学习100天', icon: 'crown', rarity: 'epic', unlocked: false },
  { id: 'b8', name: '全能学者', description: '完成5条学习路径', icon: 'award', rarity: 'epic', unlocked: false },
  { id: 'b9', name: '知识灯塔', description: '获得100个赞', icon: 'star', rarity: 'legendary', unlocked: false },
]
const unlockedBadges = computed(() => badges.filter((b) => b.unlocked).length)

interface PointsHistoryItem {
  title: string
  time: string
  amount: number
  icon: string
}
const pointsHistory: PointsHistoryItem[] = [
  { title: '完成番茄钟专注', time: '今天 14:30', amount: 25, icon: 'zap' },
  { title: '完成学习任务', time: '今天 13:15', amount: 10, icon: 'check-circle' },
  { title: '连续打卡奖励', time: '今天 09:00', amount: 15, icon: 'flame' },
  { title: '发表社区讨论', time: '昨天 20:22', amount: 20, icon: 'message-square' },
  { title: '完成学习路径章节', time: '昨天 16:45', amount: 30, icon: 'book' },
  { title: '兑换学习宠物道具', time: '2天前', amount: -50, icon: 'gift' },
]

// ===== 社区讨论区 =====
const communitySearch = ref('')
const activeCommunityTag = ref('全部')
const communityTags = ['全部', '求助', '分享', '讨论', '项目展示', '职业发展']
interface Topic {
  id: string
  author: string
  time: string
  title: string
  excerpt: string
  tags: string[]
  views: number
  replies: number
  likes: number
  isHot: boolean
}
const allTopics: Topic[] = [
  { id: 't1', author: '学霸君', time: '2小时前', title: 'Vue 3 的 ref 和 reactive 到底该用哪个？', excerpt: '在使用 Vue 3 开发时，ref 和 reactive 的选择经常让人困惑。大家在实际项目中是怎么选择的？有什么最佳实践吗？', tags: ['讨论', '前端开发'], views: 328, replies: 24, likes: 45, isHot: true },
  { id: 't2', author: '努力的小明', time: '5小时前', title: '分享：我是如何用3个月自学前端的', excerpt: '从零基础到能独立开发项目，分享一下我的学习路线、用到的资源和踩过的坑，希望对新手有帮助...', tags: ['分享', '职业发展'], views: 512, replies: 38, likes: 89, isHot: true },
  { id: 't3', author: '代码小能手', time: '昨天', title: 'Spring Boot 集成 JWT 认证完整步骤', excerpt: '最近在项目中实现了 JWT 认证，整理了一份完整的步骤文档，包含代码示例和常见问题解决...', tags: ['分享', '后端开发'], views: 256, replies: 15, likes: 67, isHot: false },
  { id: 't4', author: '前端小白', time: '昨天', title: 'TypeScript 报错求助：类型不兼容', excerpt: '在用 TS 写一个泛型组件时遇到了类型不兼容的问题，报错信息是 Type A is not assignable to Type B...', tags: ['求助', '前端开发'], views: 134, replies: 8, likes: 12, isHot: false },
  { id: 't5', author: '后端大佬', time: '2天前', title: '大家都在用什么 AI 辅助编程工具？', excerpt: '最近 Copilot、Cursor、Claude 等工具很火，大家在实际开发中都在用哪些？体验如何？', tags: ['讨论', '工具效率'], views: 423, replies: 56, likes: 78, isHot: true },
]
const filteredTopics = computed(() => {
  return allTopics.filter((t) => {
    const matchTag = activeCommunityTag.value === '全部' || t.tags.includes(activeCommunityTag.value)
    const matchSearch = !communitySearch.value ||
      t.title.toLowerCase().includes(communitySearch.value.toLowerCase()) ||
      t.excerpt.toLowerCase().includes(communitySearch.value.toLowerCase())
    return matchTag && matchSearch
  })
})
const showPostDialog = ref(false)
const joinDiscussion = (topic: Topic) => {
  notify(`正在打开：${topic.title}`, 'info')
}

// ===== 番茄钟（原有功能） =====
const pomodoroRadius = 94
const pomodoroCircumference = 2 * Math.PI * pomodoroRadius
type PomodoroMode = 'focus' | 'shortBreak' | 'longBreak'
const pomodoroModes = [
  { value: 'focus' as const, label: '专注', duration: 25 * 60 },
  { value: 'shortBreak' as const, label: '短休', duration: 5 * 60 },
  { value: 'longBreak' as const, label: '长休', duration: 15 * 60 },
]
const modeLabels: Record<PomodoroMode, string> = { focus: '专注时间', shortBreak: '短休息', longBreak: '长休息' }
const modeColors: Record<PomodoroMode, { bg: string; stroke: string }> = {
  focus: { bg: 'bg-red-50', stroke: '#EF4444' },
  shortBreak: { bg: 'bg-green-50', stroke: '#10B981' },
  longBreak: { bg: 'bg-blue-50', stroke: '#3B6FE0' },
}
const currentMode = ref<PomodoroMode>('focus')
const isRunning = ref(false)
const timeLeft = ref(25 * 60)
let timerInterval: number | null = null
const pomodoroDashoffset = computed(() => {
  const totalTime = pomodoroModes.find((m) => m.value === currentMode.value)?.duration || 25 * 60
  const progress = timeLeft.value / totalTime
  return pomodoroCircumference * (1 - progress)
})
const formatTime = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}
const switchMode = (mode: PomodoroMode) => {
  currentMode.value = mode
  isRunning.value = false
  if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
  const modeData = pomodoroModes.find((m) => m.value === mode)
  timeLeft.value = modeData?.duration || 25 * 60
}
const toggleTimer = () => {
  if (isRunning.value) {
    if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
  } else {
    timerInterval = window.setInterval(() => {
      if (timeLeft.value > 0) {
        timeLeft.value--
        if (timeLeft.value === 0) {
          if (currentMode.value === 'focus') {
            const focusMin = Math.round((pomodoroModes.find((m) => m.value === 'focus')?.duration ?? 1500) / 60)
            addSession(dateStr(new Date()), focusMin)
            sessionVersion.value++
            notify('专注完成，已记录到本机', 'success')
          }
          if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
          isRunning.value = false
        }
      }
    }, 1000)
  }
  isRunning.value = !isRunning.value
}
const resetTimer = () => {
  isRunning.value = false
  if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
  const modeData = pomodoroModes.find((m) => m.value === currentMode.value)
  timeLeft.value = modeData?.duration || 25 * 60
}
const skipTimer = () => {
  const currentIndex = pomodoroModes.findIndex((m) => m.value === currentMode.value)
  const nextIndex = (currentIndex + 1) % pomodoroModes.length
  switchMode(pomodoroModes[nextIndex].value)
}

// ===== 学习任务（原有功能） =====
const loadTasks = async () => {
  try {
    const list = await learningApi.tasks()
    tasks.value = list.map((t) => ({ id: t.id, title: t.title, duration: 25, completed: false }))
  } catch {
    tasks.value = []
  }
}
const completedTasksCount = computed(() => tasks.value.filter((t) => t.completed).length)
const toggleTask = (id: string | number) => {
  const task = tasks.value.find((t) => t.id === id)
  if (task) task.completed = !task.completed
}
const deleteTask = (id: string | number) => {
  tasks.value = tasks.value.filter((t) => t.id !== id)
}
const addTask = () => {
  if (!newTaskTitle.value.trim()) return
  tasks.value.push({ id: Date.now().toString(), title: newTaskTitle.value.trim(), duration: 25, completed: false })
  newTaskTitle.value = ''
}

// ===== 学习伙伴（原有功能） =====
const pet = ref(loadPet(learningPet))
const feedPet = () => {
  pet.value.energy = Math.min(100, pet.value.energy + 10)
  pet.value.exp = Math.min(pet.value.maxExp, pet.value.exp + 20)
  if (pet.value.exp >= pet.value.maxExp) {
    pet.value.level++
    pet.value.exp = 0
    pet.value.maxExp = Math.floor(pet.value.maxExp * 1.5)
  }
  savePet(pet.value)
}
const playWithPet = () => {
  pet.value.energy = Math.max(0, pet.value.energy - 15)
  pet.value.exp = Math.min(pet.value.maxExp, pet.value.exp + 30)
  if (pet.value.exp >= pet.value.maxExp) {
    pet.value.level++
    pet.value.exp = 0
    pet.value.maxExp = Math.floor(pet.value.maxExp * 1.5)
  }
  savePet(pet.value)
}

const rankList = weeklyRank

// ===== 生命周期 =====
onMounted(loadTasks)
onUnmounted(() => {
  if (timerInterval) clearInterval(timerInterval)
})
</script>

<style scoped>
/* ===== 页面基础 ===== */
.lc-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 页面头部 ===== */
.lc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin-bottom: 4px;
}
.page-subtitle {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.header-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.hs-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  background: var(--kb-card);
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.hs-icon {
  color: var(--kb-primary);
}
.hs-icon-flame { color: #F59E0B; }
.hs-icon-star { color: #EAB308; }
.hs-item > div {
  display: flex;
  flex-direction: column;
}
.hs-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
}
.hs-value small {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  margin-left: 2px;
}
.hs-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ===== Tab 导航 ===== */
.lc-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 24px;
  padding: 6px;
  background: var(--kb-card);
  border-radius: 14px;
  border: 1px solid var(--kb-border);
  overflow-x: auto;
}
.lc-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 18px;
  border: none;
  background: transparent;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
  position: relative;
}
.lc-tab:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.lc-tab.active {
  background: var(--kb-primary);
  color: #fff;
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.3);
}
.lc-tab.active .tab-badge {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.1));
  color: var(--kb-primary);
  font-size: 11px;
  font-weight: 600;
}

/* ===== Tab 内容 ===== */
.tab-panel {
  min-height: 400px;
}

/* ===== 通用网格 ===== */
.grid-3-2 {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
.space-y-6 > * + * {
  margin-top: 20px;
}

/* ===== 卡片头部通用 ===== */
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-header-title h2 {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.card-header-title h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}

/* ===== 仪表盘：英雄区 ===== */
.dashboard-hero {
  display: flex;
  align-items: center;
  gap: 32px;
}
.progress-ring {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}
.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}
.ring-progress {
  transition: stroke-dashoffset 1s ease-out;
}
.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.ring-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--kb-primary);
}
.ring-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.hero-stats {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.hero-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px;
  border-radius: 10px;
  background: var(--kb-muted);
}
.stat-icon-green { color: #10B981; }
.stat-icon-blue { color: #3B6FE0; }
.stat-icon-orange { color: #F59E0B; }
.stat-icon-purple { color: #8B5CF6; }
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
}
.stat-text {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ===== 快捷入口 ===== */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.qa-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  background: var(--kb-card);
  cursor: pointer;
  transition: all 0.2s;
}
.qa-btn:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 4px 12px rgba(59, 111, 224, 0.12);
  transform: translateY(-2px);
}
.qa-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: #fff;
}
.qa-red { background: linear-gradient(135deg, #EF4444, #F87171); }
.qa-blue { background: linear-gradient(135deg, #3B6FE0, #6F9AF2); }
.qa-green { background: linear-gradient(135deg, #10B981, #34D399); }
.qa-orange { background: linear-gradient(135deg, #F59E0B, #FBBF24); }
.qa-purple { background: linear-gradient(135deg, #8B5CF6, #A78BFA); }
.qa-cyan { background: linear-gradient(135deg, #06B6D4, #22D3EE); }
.qa-indigo { background: linear-gradient(135deg, #6366F1, #818CF8); }
.qa-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

/* ===== 周学习图表 ===== */
.weekly-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 180px;
  gap: 8px;
  padding-top: 20px;
}
.chart-bar-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  height: 100%;
}
.chart-bar-container {
  flex: 1;
  display: flex;
  align-items: flex-end;
  width: 100%;
  justify-content: center;
}
.chart-bar {
  width: 60%;
  max-width: 40px;
  min-height: 4px;
  background: linear-gradient(180deg, #6F9AF2, #3B6FE0);
  border-radius: 6px 6px 0 0;
  position: relative;
  transition: height 0.6s ease-out;
  display: flex;
  justify-content: center;
}
.chart-bar.is-today {
  background: linear-gradient(180deg, #FBBF24, #F59E0B);
}
.chart-bar-value {
  position: absolute;
  top: -20px;
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.chart-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 6px;
}

/* ===== 活动时间线 ===== */
.activity-timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.timeline-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--kb-border);
}
.timeline-item:last-child {
  border-bottom: none;
}
.timeline-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex-shrink: 0;
  color: #fff;
}
.timeline-dot.study { background: #3B6FE0; }
.timeline-dot.task { background: #10B981; }
.timeline-dot.start { background: #F59E0B; }
.timeline-body {
  flex: 1;
  min-width: 0;
}
.timeline-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 2px;
}
.timeline-time {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.timeline-points {
  font-size: 13px;
  font-weight: 600;
  color: #F59E0B;
}

/* ===== Section Header ===== */
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.section-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-count {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* ===== AI 推荐横幅 ===== */
.ai-path-banner {
  margin-bottom: 24px;
}
.banner-content {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 0;
}
.banner-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, #3B6FE0, #8B5CF6);
  color: #fff;
  flex-shrink: 0;
}
.banner-text {
  flex: 1;
}
.banner-text h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 4px;
}
.banner-text p {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}

/* ===== 学习路径卡片 ===== */
.path-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.path-card {
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}
.path-card:hover {
  transform: translateY(-4px);
}
.path-cover {
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  margin: -16px -16px 12px;
}
.path-cover-icon {
  color: rgba(255, 255, 255, 0.9);
}
.path-cover .badge {
  position: absolute;
  top: 8px;
  right: 8px;
}
.path-body {
  padding: 0;
}
.path-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}
.path-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.path-suitable {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 10px;
}
.path-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.progress-bar {
  flex: 1;
  height: 6px;
  background: var(--kb-muted);
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #3B6FE0, #6F9AF2);
  border-radius: 3px;
  transition: width 0.6s ease-out;
}
.progress-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-primary);
}
.path-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.path-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ===== 资源中心 ===== */
.resource-search {
  margin-bottom: 16px;
}
.search-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}
.search-icon {
  position: absolute;
  left: 14px;
  color: var(--kb-muted-foreground);
  pointer-events: none;
}
.search-input {
  width: 100%;
  padding: 12px 40px 12px 44px;
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  font-size: 14px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.2s;
}
.search-input:focus {
  border-color: var(--kb-primary);
}
.search-clear {
  position: absolute;
  right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: var(--kb-muted);
  border-radius: 50%;
  color: var(--kb-muted-foreground);
  cursor: pointer;
}
.resource-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.kb-filter-btn {
  padding: 6px 14px;
  border: 1px solid var(--kb-border);
  border-radius: 20px;
  background: var(--kb-card);
  font-size: 13px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.2s;
}
.kb-filter-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.kb-filter-btn.active {
  background: var(--kb-primary);
  color: #fff;
  border-color: var(--kb-primary);
}
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.resource-card {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.resource-card:hover {
  transform: translateY(-2px);
}
.resource-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.resource-type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  color: #fff;
}
.r-green { background: #10B981; }
.r-blue { background: #3B6FE0; }
.r-orange { background: #F59E0B; }
.r-purple { background: #8B5CF6; }
.r-cyan { background: #06B6D4; }
.r-red { background: #EF4444; }
.r-indigo { background: #6366F1; }
.resource-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}
.resource-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.resource-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.resource-cat {
  padding: 2px 8px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.08));
  color: var(--kb-primary);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.resource-stats {
  display: flex;
  gap: 10px;
}
.resource-stats span {
  display: flex;
  align-items: center;
  gap: 3px;
}

/* ===== 空状态 ===== */
.empty-state-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  text-align: center;
}
.empty-icon {
  color: var(--kb-muted-foreground);
  opacity: 0.4;
  margin-bottom: 16px;
}
.empty-text {
  font-size: 15px;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}
.empty-hint {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* ===== 成就积分 ===== */
.points-overview-card {
  margin-bottom: 0;
}
.points-overview {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}
.level-badge {
  position: relative;
  width: 100px;
  height: 100px;
  flex-shrink: 0;
}
.level-circle {
  position: absolute;
  inset: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3B6FE0, #8B5CF6);
  border-radius: 50%;
  color: #fff;
  z-index: 1;
}
.level-num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}
.level-label {
  font-size: 11px;
  opacity: 0.85;
}
.level-ring {
  position: absolute;
  inset: 0;
}
.level-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}
.level-ring-progress {
  transition: stroke-dashoffset 1s ease-out;
}
.points-info {
  flex: 1;
  min-width: 200px;
}
.points-info h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}
.points-current {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  margin-bottom: 8px;
}
.points-bar {
  height: 8px;
  background: var(--kb-muted);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}
.points-fill {
  height: 100%;
  background: linear-gradient(90deg, #3B6FE0, #8B5CF6);
  border-radius: 4px;
  transition: width 0.8s ease-out;
}
.points-hint {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  gap: 4px;
}
.points-stat-grid {
  display: flex;
  gap: 20px;
}
.ps-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.ps-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-primary);
}
.ps-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ===== 徽章墙 ===== */
.badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 16px;
}
.badge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 8px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  text-align: center;
  transition: transform 0.2s;
}
.badge-item:hover {
  transform: translateY(-2px);
}
.badge-item.locked {
  opacity: 0.45;
  filter: grayscale(1);
}
.badge-medal {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  color: #fff;
  margin-bottom: 4px;
}
.badge-common { background: linear-gradient(135deg, #6B7280, #9CA3AF); }
.badge-rare { background: linear-gradient(135deg, #3B6FE0, #6F9AF2); }
.badge-epic { background: linear-gradient(135deg, #8B5CF6, #A78BFA); }
.badge-legendary { background: linear-gradient(135deg, #F59E0B, #FBBF24); }
.badge-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.badge-desc {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  line-height: 1.3;
}

/* ===== 积分明细 ===== */
.points-history {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ph-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--kb-border);
}
.ph-item:last-child {
  border-bottom: none;
}
.ph-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  flex-shrink: 0;
  color: #fff;
}
.ph-plus { background: #10B981; }
.ph-minus { background: #EF4444; }
.ph-body {
  flex: 1;
}
.ph-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 2px;
}
.ph-time {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.ph-amount {
  font-size: 15px;
  font-weight: 700;
}
.ph-amount.plus { color: #10B981; }
.ph-amount.minus { color: #EF4444; }

/* ===== 社区讨论 ===== */
.community-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.community-search {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 200px;
}
.community-search .search-icon {
  position: absolute;
  left: 12px;
  color: var(--kb-muted-foreground);
  pointer-events: none;
}
.community-search .search-input {
  padding: 10px 16px 10px 40px;
}
.community-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.topic-card {
  cursor: pointer;
  transition: transform 0.15s;
}
.topic-card:hover {
  transform: translateX(2px);
}
.topic-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.topic-author-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.topic-author {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.topic-time {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.topic-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}
.topic-excerpt {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.topic-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}
.topic-tag {
  padding: 2px 10px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.08));
  color: var(--kb-primary);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.topic-footer {
  display: flex;
  align-items: center;
  gap: 16px;
}
.topic-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.topic-join-btn {
  margin-left: auto;
  padding: 6px 14px;
  border: 1px solid var(--kb-primary);
  background: transparent;
  color: var(--kb-primary);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.topic-join-btn:hover {
  background: var(--kb-primary);
  color: #fff;
}

/* ===== 番茄钟侧边按钮 ===== */
.pomo-side-btn {
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: #F3F4F6;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
}
.pomo-side-btn:hover {
  background: #E5E7EB;
}

/* ===== 排行榜圆圈 ===== */
.rank-circle {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  flex-shrink: 0;
  color: #fff;
}
.rank-1 { background: linear-gradient(135deg, #FCD34D, #F59E0B); }
.rank-2 { background: linear-gradient(135deg, #D1D5DB, #9CA3AF); }
.rank-3 { background: linear-gradient(135deg, #FDBA74, #F97316); }
.rank-circle:not(.rank-1):not(.rank-2):not(.rank-3) {
  background: #F3F4F6;
  color: var(--kb-muted-foreground);
}

/* ===== 猫头鹰宠物动画 ===== */
.owl-container {
  width: 100px;
  height: 120px;
  position: relative;
  animation: bounce 2s ease-in-out infinite;
}
@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
.owl-body {
  width: 80px;
  height: 90px;
  background: linear-gradient(180deg, #8B5CF6 0%, #7C3AED 100%);
  border-radius: 40px 40px 35px 35px;
  position: absolute;
  top: 5px;
  left: 50%;
  transform: translateX(-50%);
  box-shadow: 0 4px 15px rgba(139, 92, 246, 0.3);
}
.owl-belly {
  width: 50px;
  height: 55px;
  background: linear-gradient(180deg, #FDF2F8 0%, #FCE7F3 100%);
  border-radius: 25px 25px 20px 20px;
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
}
.owl-eyes {
  display: flex;
  gap: 12px;
  position: absolute;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
}
.owl-eye {
  width: 22px;
  height: 22px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.owl-pupil {
  width: 10px;
  height: 10px;
  background: #1F2937;
  border-radius: 50%;
  position: relative;
  animation: lookAround 4s ease-in-out infinite;
}
.owl-pupil::after {
  content: '';
  width: 4px;
  height: 4px;
  background: white;
  border-radius: 50%;
  position: absolute;
  top: 1px;
  right: 1px;
}
@keyframes lookAround {
  0%, 100% { transform: translate(0, 0); }
  25% { transform: translate(2px, 0); }
  50% { transform: translate(0, 2px); }
  75% { transform: translate(-2px, 0); }
}
.owl-beak {
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 8px solid #F59E0B;
  position: absolute;
  top: 38px;
  left: 50%;
  transform: translateX(-50%);
}
.owl-wings {
  position: absolute;
  top: 40px;
  width: 100%;
}
.owl-wing {
  width: 18px;
  height: 35px;
  background: linear-gradient(180deg, #7C3AED 0%, #6D28D9 100%);
  border-radius: 50%;
  position: absolute;
}
.owl-wing.left {
  left: -6px;
  transform: rotate(-10deg);
  animation: wingLeft 3s ease-in-out infinite;
}
.owl-wing.right {
  right: -6px;
  transform: rotate(10deg);
  animation: wingRight 3s ease-in-out infinite;
}
@keyframes wingLeft {
  0%, 100% { transform: rotate(-10deg); }
  50% { transform: rotate(-20deg); }
}
@keyframes wingRight {
  0%, 100% { transform: rotate(10deg); }
  50% { transform: rotate(20deg); }
}
.owl-feet {
  display: flex;
  gap: 16px;
  position: absolute;
  bottom: 2px;
  left: 50%;
  transform: translateX(-50%);
}
.owl-foot {
  width: 14px;
  height: 8px;
  background: #F59E0B;
  border-radius: 4px;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .lc-page {
    padding: 16px 12px 32px;
  }
  .lc-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-stats {
    width: 100%;
    justify-content: space-between;
  }
  .hs-item {
    flex: 1;
    padding: 8px 12px;
  }
  .grid-3-2 {
    grid-template-columns: 1fr;
  }
  .grid-2 {
    grid-template-columns: 1fr;
  }
  .dashboard-hero {
    flex-direction: column;
    gap: 20px;
  }
  .hero-stats {
    grid-template-columns: repeat(2, 1fr);
    width: 100%;
  }
  .quick-actions {
    grid-template-columns: repeat(3, 1fr);
  }
  .points-overview {
    flex-direction: column;
    text-align: center;
  }
  .points-stat-grid {
    justify-content: center;
  }
  .path-grid {
    grid-template-columns: 1fr;
  }
  .resource-grid {
    grid-template-columns: 1fr;
  }
  .badge-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .lc-tabs {
    padding: 4px;
  }
  .lc-tab {
    padding: 8px 12px;
    font-size: 13px;
  }
  .lc-tab span:not(.tab-badge) {
    display: none;
  }
  .lc-tab.active span:not(.tab-badge) {
    display: inline;
  }
}

@media (max-width: 480px) {
  .quick-actions {
    grid-template-columns: repeat(2, 1fr);
  }
  .badge-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .hero-stats {
    grid-template-columns: 1fr;
  }
}
</style>
