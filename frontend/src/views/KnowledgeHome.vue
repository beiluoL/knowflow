<template>
  <div class="knowledge-layout knowledge-v2" @keydown.esc="sidebarOpen = false">
    <!-- 移动端遮罩层 -->
    <div
      v-if="sidebarOpen"
      class="sidebar-overlay"
      @click="sidebarOpen = false"
    ></div>

    <!-- 左侧边栏 -->
    <KnowledgeSidebar
      ref="sidebarRef"
      :inert="isCompact && !sidebarOpen"
      :role="isCompact && sidebarOpen ? 'dialog' : undefined"
      :aria-modal="isCompact && sidebarOpen ? true : undefined"
      aria-label="知识分类"
      @keydown.tab="trapKnowledgeFocus"
      mode="home"
      :active-key="activeKey"
      :active-category-id="activeCategoryId"
      :mobile-open="sidebarOpen"
      @category-click="onCategoryClick"
      @all-click="onAllClick"
      @close-mobile="sidebarOpen = false"
    />

    <!-- 主内容区 -->
    <main class="knowledge-main" :inert="isCompact && sidebarOpen">
      <!-- 顶部搜索栏 -->
      <div class="top-bar">
        <!-- 移动端菜单按钮 -->
        <button class="mobile-menu-btn" type="button" ref="categoryTrigger" aria-label="打开知识分类" @click="sidebarOpen = true">
          <Icon name="menu" :size="20" />
        </button>
        <div class="search-box kf-input-surface">
          <Icon name="search" :size="18" class="search-icon" />
          <input
            v-model="searchKeyword"
            type="search"
            aria-label="搜索知识文档"
            placeholder="搜索知识文档..."
            class="search-input kf-input kf-form-control"
            @keydown.enter="handleSearchKey"
            @focus="showSearchHistory = true"
            @blur="hideSearchHistory"
          />
          <kbd class="search-shortcut">Enter</kbd>
          <!-- 搜索历史下拉 -->
          <div
            v-if="showSearchHistory && searchHistory.length > 0 && !searchKeyword"
            class="search-history-dropdown"
          >
            <div class="history-header">
              <span class="history-title">搜索历史</span>
              <button class="history-clear" @click="clearSearchHistory">清除</button>
            </div>
            <div class="history-items">
              <button
                v-for="item in searchHistory"
                :key="item"
                class="history-item"
                @click="searchFromHistory(item)"
              >
                <Icon name="clock" :size="12" class="history-icon" />
                <span>{{ item }}</span>
              </button>
            </div>
          </div>
        </div>
        <!-- UI/UX 5.0 §十二：顶部动作收敛为 Search + [导入知识]（上传/粘贴/目录/本地阅读统一入口）；
             AI 整理移入文档上下文的分类操作区，不再占顶栏。 -->
        <div class="kf-import-dropdown" ref="importMenuRef">
          <button class="upload-btn" type="button" :aria-expanded="importMenuOpen" aria-haspopup="menu"
                  @click="importMenuOpen = !importMenuOpen" @keydown.esc="importMenuOpen = false">
            <Icon name="plus" :size="16" />
            <span>导入知识</span>
            <Icon :name="importMenuOpen ? 'chevron-up' : 'chevron-down'" :size="14" />
          </button>
          <div v-if="importMenuOpen" class="kf-import-menu" role="menu">
            <button type="button" role="menuitem" @click="goUpload">
              <Icon name="upload" :size="15" /><span>上传文件</span>
            </button>
            <button type="button" role="menuitem" @click="goProcessPaste">
              <Icon name="file-plus" :size="15" /><span>粘贴文本</span>
            </button>
            <button type="button" role="menuitem" @click="goImport">
              <Icon name="folder-tree" :size="15" /><span>导入目录</span>
            </button>
            <button type="button" role="menuitem" @click="goReader">
              <Icon name="book-open" :size="15" /><span>本地阅读</span>
            </button>
          </div>
        </div>
      </div>

      <StateView :status="homeStatus" :error-title="homeError?.title" :error-scope="homeError?.scope" :error-hint="homeError?.hint" :show-retry="homeError?.retryable ?? true" empty-title="还没有知识资料" empty-description="先上传资料或粘贴文本，在知识库中建立自己的内容底座。" type="card" :rows="3" @retry="loadData(true)">
      <template #empty-action><button type="button" class="kf-btn kf-btn-primary" @click="goUpload">上传第一份资料</button></template>
      <template #default>
      <!-- ============ 视图 1: 全部文档 (首页) ============ -->
      <template v-if="activeKey === 'all' && !activeCategoryId">
        <!-- 问候语 + 引言 -->
        <section class="greeting-section">
          <h1 class="greeting-title">知识中心</h1>
          <p class="knowledge-intro">{{ greetingText }}。按分类找到资料，继续阅读，再把理解付诸练习。</p>
          <nav class="knowledge-destinations" aria-label="知识学习入口">
            <router-link to="/docs"><Icon name="file-text" :size="16" />浏览文档</router-link>
            <router-link to="/learning/mastery"><Icon name="search" :size="16" />检索知识点</router-link>
            <router-link v-if="graphEntry" :to="graphEntry.path"><Icon :name="graphEntry.icon" :size="16" />查看知识关联</router-link>
            <router-link to="/learning/center"><Icon name="book-open" :size="16" />继续学习计划</router-link>
          </nav>
        </section>

        <section v-if="auth.isLoggedIn" class="section knowledge-continuity" aria-label="继续学习">
          <h2 class="section-title">继续学习</h2>
          <div v-if="learning.error" role="alert"><p>{{ learning.error }}</p><button type="button" class="kf-state-retry" @click="learning.load(true)">重试</button></div>
          <NextActionCard v-else-if="learning.loaded" :action="learning.nextAction" />
          <p v-else role="status">正在读取学习计划…</p>
        </section>
        <section v-if="auth.isLoggedIn && readingStatus !== 'empty'" class="section">
          <h2 class="section-title">最近学习 · 继续阅读</h2>
          <StateView :status="readingStatus" :error-title="readingError?.title" :error-hint="readingError?.hint" :show-retry="readingError?.retryable" @retry="readingResource.run(true)">
            <div class="knowledge-reading-list"><router-link v-for="doc in continueReadingDocs" :key="doc.id" :to="`/doc/${doc.id}`"><Icon name="book-open" :size="16"/><span>{{ doc.title }}</span></router-link></div>
          </StateView>
        </section>

        <!-- 最近浏览 -->
        <section class="section">
          <div class="section-header">
            <h2 class="section-title">最近文档</h2>
            <router-link to="/docs" class="view-all-link">查看全部 <Icon name="arrow-right" :size="14" /></router-link>
          </div>
          <StateView :status="latestStatus" :error-title="latestError?.title" :error-scope="latestError?.scope" :error-hint="latestError?.hint" :show-retry="latestError?.retryable ?? true" empty-title="还没有最近文档" empty-description="上传资料后，可以在这里快速找到最近的内容。" :rows="1" type="card" @retry="loadLatest(true)">
          <template #empty-action><button type="button" class="kf-btn" @click="goUpload">上传资料</button></template>
          <div v-if="latestDocs.length > 0" class="recent-grid">
            <div
              v-for="(doc, idx) in latestDocs"
              :key="doc.id"
              class="recent-card"
              role="button"
              tabindex="0"
              @click="goToDoc(doc.id)"
              @keydown.enter.prevent="($event.target as HTMLElement).click()"
              @keydown.space.prevent="($event.target as HTMLElement).click()"
            >
              <DocTypeBadge :file-url="doc.fileUrl" :content="doc.content" :size="36" />
              <div class="recent-info">
                <h4 class="recent-title" :title="doc.title">{{ doc.title }}</h4>
                <p class="recent-meta">
                  <span class="recent-tag" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || '未分类' }}</span>
                  <span class="recent-time">{{ formatRelativeTime(doc.createTime) }}</span>
                </p>
              </div>
            </div>
          </div>
          </StateView>
        </section>

        <!-- 快速访问 -->
        <section class="section">
          <h2 class="section-title">知识分类</h2>
          <div class="quick-access-grid">
            <div
              v-for="(cat, idx) in topCategories"
              :key="cat.id"
              class="quick-card"
              role="button"
              tabindex="0"
              @click="onCategoryClick(cat)"
              @keydown.enter.prevent="($event.target as HTMLElement).click()"
              @keydown.space.prevent="($event.target as HTMLElement).click()"
            >
              <div class="quick-icon" :style="{ background: `${kbColors[idx % kbColors.length]}1A` }">
                <Icon :name="getCategoryIcon(cat.icon)" :size="24" :style="{ color: kbColors[idx % kbColors.length] }" />
              </div>
              <div class="quick-info">
                <h3 class="quick-name">{{ cat.name }}</h3>
                <p class="quick-count">{{ cat.docCount || 0 }} 篇文档</p>
              </div>
            </div>
          </div>
        </section>

        <!-- 3.2 §41/§44：已启用的知识工具（数据只来自 UserApp，未启用则不渲染） -->
        <section v-if="knowledgeApps.length > 0" class="section">
          <div class="section-header">
            <h2 class="section-title">我的知识工具</h2>
            <router-link to="/apps" class="view-all-link">应用中心 <Icon name="arrow-right" :size="14" /></router-link>
          </div>
          <div class="kf-app-grid">
            <router-link
              v-for="app in knowledgeApps"
              :key="app.path"
              :to="app.path"
              class="kf-app-card"
            >
              <span class="kf-app-icon"><Icon :name="app.icon" :size="18" /></span>
              <span class="kf-app-name">{{ app.label }}</span>
              <Icon name="arrow-right" :size="14" class="kf-app-arrow" />
            </router-link>
          </div>
        </section>

        <!-- 学习概览 -->
        <section class="section">
          <h2 class="section-title">学习概览</h2>
          <StateView :status="statsStatus" :error-title="statsError?.title" :error-scope="statsError?.scope" :error-hint="statsError?.hint" :show-retry="statsError?.retryable ?? true" empty-title="登录后查看学习概览" empty-description="学习统计属于个人数据，需要登录后查看。" type="card" :rows="1" @retry="loadStats(true)">
          <template #empty-action><button type="button" class="kf-btn" @click="router.push('/login')">登录</button></template>
          <div class="stats-grid">
            <StatCard
              icon="clock"
              icon-color="var(--kf-primary)"
              icon-bg="rgba(59, 111, 224, 0.1)"
              :value="userStats?.totalStudyHours != null ? userStats.totalStudyHours + 'h' : '—'"
              label="累计学习时长"
              layout="horizontal"
            />
            <StatCard
              icon="file-text"
              icon-color="var(--kf-accent)"
              icon-bg="rgba(16, 185, 129, 0.1)"
              :value="userStats?.readDocsCount != null ? userStats.readDocsCount + ' 篇' : '—'"
              label="已阅读文档"
              layout="horizontal"
            />
            <StatCard
              icon="flame"
              icon-color="var(--kf-warning)"
              icon-bg="rgba(245, 158, 11, 0.1)"
              :value="userStats?.streakDays != null ? userStats.streakDays + ' 天' : '—'"
              label="连续学习"
              layout="horizontal"
            />
            <StatCard
              icon="star"
              icon-color="#8B5CF6"
              icon-bg="rgba(139, 92, 246, 0.1)"
              :value="userStats?.favoriteCount != null ? userStats.favoriteCount + ' 个' : '—'"
              label="知识收藏"
              layout="horizontal"
            />
          </div>
          </StateView>
        </section>

      </template>

      <!-- ============ 视图 2: 分类下有子分类 → 显示子分类卡片 ============ -->
      <template v-else-if="currentCategory && hasChildren(currentCategory)">
        <section class="category-view">
          <!-- 面包屑 -->
          <div class="category-header">
            <div class="breadcrumb">
              <button type="button" class="breadcrumb-link" @click="onAllClick">全部文档</button>
              <Icon name="chevron-right" :size="12" class="sep" />
              <template v-if="currentParentCategory">
                <button type="button" class="breadcrumb-link" @click="onCategoryClick(currentParentCategory)">{{ currentParentCategory.name }}</button>
                <Icon name="chevron-right" :size="12" class="sep" />
              </template>
              <span class="breadcrumb-current">{{ currentCategory.name }}</span>
            </div>
            <div class="header-meta">
              <span class="meta-pill">
                <Icon name="folder" :size="12" />
                {{ currentCategory.children?.length || 0 }} 个子分类
              </span>
              <span class="meta-pill" v-if="currentCategory.docCount">
                <Icon name="file-text" :size="12" />
                {{ currentCategory.docCount }} 篇文档
              </span>
            </div>
          </div>

          <!-- 分类图标标题区 -->
          <div class="category-hero" :style="{ background: `${getCategoryColor(currentCategory)}10`, borderColor: `${getCategoryColor(currentCategory)}20` }">
            <div class="hero-icon" :style="{ background: `${getCategoryColor(currentCategory)}20`, color: getCategoryColor(currentCategory) }">
              <Icon :name="getCategoryIcon(currentCategory.icon)" :size="24" />
            </div>
            <div class="hero-info">
              <h1 class="hero-title">{{ currentCategory.name }}</h1>
              <p class="hero-desc">{{ currentCategory.description || `浏览 ${currentCategory.name} 下的子分类内容` }}</p>
            </div>
          </div>

          <!-- 子分类网格 -->
          <div class="sub-category-section">
            <h2 class="section-title">子分类</h2>
            <div class="sub-category-grid">
              <div
                v-for="(sub, idx) in currentCategory.children"
                :key="sub.id"
                class="sub-category-card"
                role="button"
                tabindex="0"
                @click="onCategoryClick(sub)"
                @keydown.enter.prevent="($event.target as HTMLElement).click()"
              @keydown.space.prevent="($event.target as HTMLElement).click()"
              >
                <div class="sub-icon" :style="{ background: `${subColors[idx % subColors.length]}14` }">
                  <Icon :name="getCategoryIcon(sub.icon)" :size="24" :style="{ color: subColors[idx % subColors.length] }" />
                </div>
                <div class="sub-info">
                  <h3 class="sub-name">{{ sub.name }}</h3>
                  <p class="sub-meta">
                    <span class="sub-count">{{ sub.docCount || 0 }} 篇文档</span>
                    <template v-if="(sub.children?.length ?? 0) > 0">
                      <span class="sub-divider">·</span>
                      <span class="sub-sub">{{ sub.children?.length }} 个子分类</span>
                    </template>
                  </p>
                  <p class="sub-desc">{{ sub.description || getSubDescription(sub.name) }}</p>
                </div>
                <Icon name="arrow-right" :size="16" class="sub-arrow" />
              </div>
            </div>
          </div>

          <!-- 该分类下的热门文档 -->
          <div v-if="categoryDocs.length > 0" class="docs-section">
            <div class="section-header">
              <h2 class="section-title">{{ currentCategory.name }} 下的文档</h2>
              <router-link to="/docs" class="view-all-link">查看全部 <Icon name="arrow-right" :size="14" /></router-link>
            </div>
            <div class="docs-list">
              <div
                v-for="(doc, idx) in categoryDocs.slice(0, 6)"
                :key="doc.id"
                class="doc-row"
                role="button"
                tabindex="0"
                @click="goToDoc(doc.id)"
                @keydown.enter.prevent="($event.target as HTMLElement).click()"
              @keydown.space.prevent="($event.target as HTMLElement).click()"
              >
                <DocTypeBadge :file-url="doc.fileUrl" :content="doc.content" :size="36" />
                <div class="doc-row-info">
                  <h4 class="doc-row-title" :title="doc.title">{{ doc.title }}</h4>
                  <p class="doc-row-meta">
                    <span class="doc-row-tag" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || currentCategory.name }}</span>
                    <span class="doc-row-time">{{ formatRelativeTime(doc.createTime) }}</span>
                  </p>
                </div>
                <Icon name="arrow-right" :size="14" class="doc-row-arrow" />
              </div>
            </div>
          </div>
        </section>
      </template>

      <!-- ============ 视图 3: 叶子分类 → 显示文章列表 ============ -->
      <template v-else-if="currentCategory">
        <section class="category-view">
          <!-- 面包屑 -->
          <div class="category-header">
            <div class="breadcrumb">
              <button type="button" class="breadcrumb-link" @click="onAllClick">全部文档</button>
              <Icon name="chevron-right" :size="12" class="sep" />
              <template v-if="currentParentCategory">
                <button type="button" class="breadcrumb-link" @click="onCategoryClick(currentParentCategory)">{{ currentParentCategory.name }}</button>
                <Icon name="chevron-right" :size="12" class="sep" />
              </template>
              <span class="breadcrumb-current">{{ currentCategory.name }}</span>
            </div>
            <div class="header-meta">
              <span class="meta-pill">
                <Icon name="file-text" :size="12" />
                {{ currentCategory.docCount || categoryDocs.length }} 篇文档
              </span>
            </div>
          </div>

          <!-- 分类标题 + 操作 -->
          <div class="leaf-header" :style="{ background: `${getCategoryColor(currentCategory)}08`, borderColor: `${getCategoryColor(currentCategory)}18` }">
            <div class="leaf-info">
              <div class="leaf-icon" :style="{ background: `${getCategoryColor(currentCategory)}20`, color: getCategoryColor(currentCategory) }">
                <Icon :name="getCategoryIcon(currentCategory.icon)" :size="24" />
              </div>
              <div>
                <h1 class="leaf-title">{{ currentCategory.name }}</h1>
                <p class="leaf-desc">{{ currentCategory.description || `浏览 ${currentCategory.name} 分类下的所有文档` }}</p>
              </div>
            </div>
            <div class="leaf-actions">
              <button class="btn-ghost small" @click="openInviteDialog">
                <Icon name="user-plus" :size="14" />
                <span>邀请成员</span>
              </button>
              <!-- UI/UX 5.0 §十二：AI 整理 = 文档上下文的 contextual action（不占顶栏主入口） -->
              <button class="btn-ghost small" @click="goProcess">
                <Icon name="sparkles" :size="14" />
                <span>AI 整理</span>
              </button>
              <button class="upload-btn small" @click="goUpload">
                <Icon name="upload" :size="14" />
                <span>上传文档</span>
              </button>
            </div>
          </div>

          <!-- 标签筛选 -->
          <div v-if="categoryTags.length > 0 && !loading" class="tag-filter-bar">
            <button
              class="tag-filter-btn"
              :class="{ active: !selectedTag }"
              @click="selectedTag = null"
            >
              全部
            </button>
            <button
              v-for="tag in categoryTags"
              :key="tag"
              class="tag-filter-btn"
              :class="{ active: selectedTag === tag }"
              @click="selectedTag = selectedTag === tag ? null : tag"
            >
              {{ tag }}
            </button>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="docs-loading" aria-busy="true" aria-label="文档加载中">
            <div v-for="i in 5" :key="i" class="loading-row">
              <div class="loading-icon"></div>
              <div class="loading-lines">
                <div class="loading-line w-3/4"></div>
                <div class="loading-line w-1/2"></div>
              </div>
            </div>
          </div>

          <!-- 错误态（UI/UX 5.1 §五/§六：加载失败**不得**显示成「暂无文档」空态） -->
          <div v-else-if="docsErrorDesc" class="docs-empty" role="alert">
            <Icon name="alert-circle" :size="32" class="empty-icon" />
            <h3>{{ docsErrorDesc.title }}</h3>
            <p>{{ docsErrorDesc.scope }}</p>
            <p>{{ docsErrorDesc.hint }}</p>
            <button type="button" class="docs-retry-btn" @click="retryCategoryDocs">
              <Icon name="refresh-cw" :size="14" />
              <span>重新加载</span>
            </button>
          </div>

          <!-- 空态 -->
          <div v-else-if="categoryDocs.length === 0" class="docs-empty">
            <Icon name="file-question" :size="32" class="empty-icon" />
            <h3>该分类下暂无文档</h3>
            <p>点击右上角「上传文档」添加内容</p>
          </div>

          <!-- 文档列表 -->
          <div v-else class="docs-list">
            <div
              v-for="(doc, idx) in sortedCategoryDocs"
              :key="doc.id"
              class="doc-row"
              role="button"
              tabindex="0"
              @click="goToDoc(doc.id)"
              @keydown.enter.prevent="($event.target as HTMLElement).click()"
              @keydown.space.prevent="($event.target as HTMLElement).click()"
            >
              <div class="doc-row-icon" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">
                <Icon name="file-text" :size="16" />
              </div>
              <div class="doc-row-info">
                  <h4 class="doc-row-title" :title="doc.title">{{ doc.title }}</h4>
                  <p class="doc-row-summary">{{ doc.summary || '暂无摘要...' }}</p>
                <p class="doc-row-meta">
                  <span class="doc-row-tag" :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }">{{ doc.categoryName || currentCategory.name }}</span>
                  <span class="doc-row-time">{{ formatRelativeTime(doc.createTime) }}</span>
                  <span class="doc-row-views"><Icon name="eye" :size="12" /> {{ doc.viewCount || 0 }}</span>
                </p>
              </div>
              <Icon name="arrow-right" :size="14" class="doc-row-arrow" />
            </div>
          </div>

          <!-- 分页 -->
          <div v-if="!loading && categoryTotal > categoryPageSize" class="docs-pagination">
            <button
              class="page-btn"
              :disabled="categoryPage <= 1"
              @click="changePage(categoryPage - 1)"
            >
              <Icon name="chevron-left" :size="14" />
            </button>
            <span class="page-info">{{ categoryPage }} / {{ categoryTotalPages }}</span>
            <button
              class="page-btn"
              :disabled="categoryPage >= categoryTotalPages"
              @click="changePage(categoryPage + 1)"
            >
              <Icon name="chevron-right" :size="14" />
            </button>
          </div>
        </section>
      </template>
      </template>
      </StateView>
    </main>
  </div>

  <!-- 邀请成员对话框 -->
  <Teleport to="body">
    <div v-if="inviteDialogVisible" class="invite-dialog-overlay" @click.self="closeInviteDialog">
      <div class="invite-dialog">
        <div class="invite-dialog-header">
          <h3>邀请成员加入知识库</h3>
          <button class="invite-close" aria-label="关闭邀请弹窗" @click="closeInviteDialog">
            <Icon name="x" :size="18" />
          </button>
        </div>
        <div class="invite-dialog-body">
          <p class="invite-desc">
            邀请成员加入「{{ currentCategory?.name }}」知识库，共同协作编辑和管理文档
          </p>
          
          <div class="invite-form-group">
            <label>成员邮箱</label>
            <input
              v-model="inviteForm.email"
              type="email"
              placeholder="请输入成员的邮箱地址"
              class="form-input kf-input kf-form-control"
            />
          </div>
          
          <div class="invite-form-group">
            <label>成员角色</label>
            <div class="role-options">
              <label 
                v-for="role in roleOptions" 
                :key="role.value"
                class="role-option"
                :class="{ active: inviteForm.role === role.value }"
              >
                <input type="radio" :value="role.value" v-model="inviteForm.role" />
                <div class="role-info">
                  <span class="role-name">{{ role.label }}</span>
                  <span class="role-desc">{{ role.description }}</span>
                </div>
              </label>
            </div>
          </div>
        </div>
        <div class="invite-dialog-footer">
          <button class="btn-secondary" @click="closeInviteDialog">取消</button>
          <button class="btn-primary" @click="handleInvite">
            <Icon name="send" :size="14" />
            <span>发送邀请</span>
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useAuthStore } from '@/stores/auth'
import StatCard from '@/components/ui/StatCard.vue'
import KnowledgeSidebar from '@/components/layout/KnowledgeSidebar.vue'
import { docsApi, adminApi, userApi } from '@/api'
import type { CategoryVO, DocVO, UserStatsVO } from '@/api/types'
import DocTypeBadge from '@/components/doc/DocTypeBadge.vue'
import { notify, getApiError } from '@/utils/toast'
// UI/UX 5.1 §六：统一错误契约（不向用户暴露 err.message / status code）
import { describePageError } from '@/utils/error-message'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useAppStore } from '@/stores/apps'
import { resolveAppNavChildren } from '@/apps/navigation'
import { getAppByKey } from '@/apps/registry'
import { useLearningStore } from '@/stores/learning'
import NextActionCard from '@/components/workflow/NextActionCard.vue'

const knowledgeStore = useKnowledgeStore()
const auth = useAuthStore()
const homeResource = useAsyncResource(() => knowledgeStore.fetchTree(true), cats => cats.length === 0, '知识首页加载失败')
const latestResource = useAsyncResource(async () => (await docsApi.list({ pageSize: 6, sort: 'time' })).records, docs => docs.length === 0, '最近文档加载失败', 'section')
const { status: latestStatus, error: latestError } = latestResource
const homeStatus = computed(() => {
  if (homeResource.status.value !== 'empty') return homeResource.status.value
  // 树成功且为空，但最近文档仍在请求或失败，不能把整页当作没有任何资料。
  if (latestStatus.value === 'loading' || latestStatus.value === 'retrying') return latestStatus.value
  return latestStatus.value === 'empty' ? 'empty' : 'ready'
})
async function loadLatest(retry = false) {
  await latestResource.run(retry)
  if (latestStatus.value !== 'error' && latestResource.data.value) latestDocs.value = latestResource.data.value.slice(0, 4)
}
const homeError = homeResource.error
const statsResource = useAsyncResource(() => userApi.stats(), () => false, '学习概览加载失败', 'section')
const statsStatus = statsResource.status
const statsError = statsResource.error
async function loadStats(retry = false) {
  if (!auth.isLoggedIn) { statsStatus.value = 'empty'; return }
  await statsResource.run(retry)
  if (statsStatus.value === 'ready') userStats.value = statsResource.data.value
}
const appStore = useAppStore()

/**
 * UI/UX 3.2 §41：知识首页承担「知识图谱 / 思维导图」等知识工具的入口。
 * 这里**不是固定菜单** —— 数据完全来自 UserApp（§44：App 菜单只来自 UserApp），
 * 未启用任何知识 App 时整块不渲染（§119：知识首页不做第二个 Sidebar）。
 */
const knowledgeApps = computed(() => resolveAppNavChildren(appStore.userApps).knowledge)
const graphEntry = computed(() => knowledgeApps.value.find(app => app.path === getAppByKey('knowledge-graph')?.route))
const learning = useLearningStore()
const readingResource = useAsyncResource(() => docsApi.recent(), items => items.length === 0, '最近阅读加载失败', 'section')
const readingStatus = readingResource.status, readingError = readingResource.error
const continueReadingDocs = computed(() => (readingResource.data.value || []).slice(0,3))

const router = useRouter()
const route = useRoute()

const sidebarRef = ref<InstanceType<typeof KnowledgeSidebar> | null>(null)
const categoryTrigger = ref<HTMLButtonElement | null>(null)
const isCompact = ref(false)
let compactMedia: MediaQueryList | undefined
function syncCompact() { isCompact.value = compactMedia?.matches ?? false; if (!isCompact.value) sidebarOpen.value = false }
function trapKnowledgeFocus(event: KeyboardEvent) {
 if (!isCompact.value || !sidebarOpen.value) return
 const root = (sidebarRef.value as unknown as {$el: HTMLElement})?.$el
 const items = root?.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],[tabindex="0"]')
 if (!items?.length) return
 const first=items[0],last=items[items.length-1]
 if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus()}
 else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}
}
const searchKeyword = ref('')
const allCategories = ref<CategoryVO[]>([])
const topCategories = ref<CategoryVO[]>([])
const latestDocs = ref<DocVO[]>([])
const categoryDocs = ref<DocVO[]>([])
const loading = ref(false)
// UI/UX 5.1 §五/§六：分类文档区独立的错误态。
// 存原始异常对象，渲染层只消费 docsErrorDesc 翻译后的三段式文案。
const docsError = ref<unknown>(null)
const docsErrorDesc = computed(() =>
  docsError.value ? describePageError(docsError.value, { subject: '文档加载失败' }) : null,
)

// 移动端抽屉
const sidebarOpen = ref(false)
watch(sidebarOpen, async open => { await nextTick(); if (!isCompact.value) return; if(open) (sidebarRef.value as unknown as {$el: HTMLElement})?.$el?.querySelector<HTMLElement>('button')?.focus(); else categoryTrigger.value?.focus() })

// 搜索框 ref（⌘K 快捷键聚焦）

// 搜索历史
const searchHistory = ref<string[]>([])
const showSearchHistory = ref(false)

// 标签筛选
const selectedTag = ref<string | null>(null)

// 学习概览统计数据
const userStats = ref<UserStatsVO | null>(null)

// 分页
const categoryPage = ref(1)
const categoryPageSize = 10
const categoryTotal = ref(0)
const categoryTotalPages = computed(() => Math.ceil(categoryTotal.value / categoryPageSize) || 1)

const activeKey = ref<'all'>('all')
const activeCategoryId = ref<number | null>(null)

const kbColors = ['var(--kf-primary)', 'var(--kf-success)', 'var(--kf-warning)', 'var(--kf-danger)', '#8B5CF6', 'var(--kf-muted-foreground)']
const docIconColors = ['var(--kf-danger)', 'var(--kf-primary)', '#8B5CF6', 'var(--kf-success)', 'var(--kf-warning)', 'var(--kf-muted-foreground)']
const subColors = ['var(--kf-primary)', 'var(--kf-success)', 'var(--kf-warning)', 'var(--kf-danger)', '#8B5CF6', '#06B6D4', '#F97316', '#EC4899']

const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好，注意休息'
  if (hour < 12) return '早上好，开始今天的学习吧'
  if (hour < 18) return '下午好，开始今天的探索吧'
  return '晚上好，开始今天的学习吧'
})

// 当前选中的分类
const currentCategory = computed<CategoryVO | null>(() => {
  if (!activeCategoryId.value) return null
  return findCategoryById(allCategories.value, activeCategoryId.value)
})

// 当前分类的父级（用于面包屑）
const currentParentCategory = computed<CategoryVO | null>(() => {
  if (!activeCategoryId.value) return null
  return findParentCategory(allCategories.value, activeCategoryId.value)
})

// 从当前分类文档中提取所有标签
const categoryTags = computed(() => {
  const tagSet = new Set<string>()
  categoryDocs.value.forEach(doc => {
    if (doc.tags) {
      doc.tags.split(',').forEach(t => {
        const trimmed = t.trim()
        if (trimmed) tagSet.add(trimmed)
      })
    }
  })
  return Array.from(tagSet).slice(0, 12)
})

const sortedCategoryDocs = computed(() => {
  let docs = [...categoryDocs.value]
  // 标签筛选
  if (selectedTag.value) {
    docs = docs.filter(d => {
      if (!d.tags) return false
      return d.tags.split(',').map(t => t.trim()).includes(selectedTag.value!)
    })
  }
  // 按时间排序
  return docs.sort((a, b) =>
    new Date(b.createTime || '').getTime() - new Date(a.createTime || '').getTime()
  )
})

function findCategoryById(cats: CategoryVO[], id: number): CategoryVO | null {
  for (const cat of cats) {
    if (cat.id === id) return cat
    if (cat.children && cat.children.length > 0) {
      const found = findCategoryById(cat.children, id)
      if (found) return found
    }
  }
  return null
}

function findParentCategory(cats: CategoryVO[], id: number, parent: CategoryVO | null = null): CategoryVO | null {
  for (const cat of cats) {
    if (cat.id === id) return parent
    if (cat.children && cat.children.length > 0) {
      const found = findParentCategory(cat.children, id, cat)
      if (found !== null || cat.children.some(c => c.id === id)) {
        if (cat.children.some(c => c.id === id)) return cat
        if (found) return found
      }
    }
  }
  return null
}

function hasChildren(cat: CategoryVO): boolean {
  return (cat.children?.length ?? 0) > 0
}

const validIcons = [
  'code', 'server', 'database', 'brain', 'layout', 'palette',
  'container', 'shield', 'binary', 'book-open', 'cpu', 'bot',
  'target', 'bar-chart-2', 'briefcase', 'git-branch', 'layers',
  'file-code', 'router', 'monitor', 'wifi', 'lock', 'smartphone',
]

const iconColorMap: Record<string, string> = {
  '人工智能': '#8B5CF6', '前端开发': 'var(--kf-primary)', '后端技术': 'var(--kf-success)',
  '数据科学': 'var(--kf-warning)', '移动开发': 'var(--kf-danger)', '数据库': '#06B6D4',
  'DevOps': '#F97316', '网络安全': '#6366F1', '算法与数据结构': '#64748B',
  '产品设计': '#EC4899', '编程语言': '#14B8A6', '项目管理': '#8B5CF6',
}

const nameIconMap: Record<string, string> = {
  '人工智能': 'brain', '前端开发': 'layout', '后端技术': 'server',
  '数据科学': 'bar-chart-2', '移动开发': 'smartphone', '数据库': 'database',
  'DevOps': 'container', '网络安全': 'shield', '算法与数据结构': 'binary',
  '产品设计': 'palette', '编程语言': 'file-code', '项目管理': 'briefcase',
}

function getCategoryIcon(icon?: string): string {
  if (icon && validIcons.includes(icon)) return icon
  return 'folder'
}

function getCategoryColor(cat: CategoryVO | null): string {
  if (!cat) return 'var(--kf-primary)'
  if (cat.icon && iconColorMap[cat.icon]) return iconColorMap[cat.icon]
  if (nameIconMap[cat.name]) return iconColorMap[cat.name] || 'var(--kf-primary)'
  return 'var(--kf-primary)'
}

function getSubDescription(name: string): string {
  const map: Record<string, string> = {
    '机器学习': 'ML 基础、模型训练与评估',
    '深度学习': '神经网络、CNN、RNN、Transformer',
    'NLP': '自然语言处理、文本分析、对话系统',
    '计算机视觉': '图像识别、目标检测、图像分割',
    '强化学习': 'RL 算法、策略优化、奖励机制',
  }
  return map[name] || `${name}相关内容`
}

let categoryLoadToken = 0
async function loadCategoryDocs(categoryId: number) {
  const token = ++categoryLoadToken
  loading.value = true
  docsError.value = null
  try {
    const cat = findCategoryById(allCategories.value, categoryId)
    const hasChildren = cat && (cat.children?.length ?? 0) > 0

    if (hasChildren) {
      // 父分类：加载所有子分类的前几条用于预览（不分页）
      const ids = getAllChildIds(categoryId)
      let merged: DocVO[] = []
      for (const id of ids) {
        const res = await docsApi.list({ categoryId: id, pageSize: 50 } as any)
        if (token !== categoryLoadToken) return
        merged = merged.concat(res.records || [])
      }
      if (token !== categoryLoadToken) return
      const seen = new Set<number>()
      categoryDocs.value = merged.filter(d => {
        if (seen.has(d.id)) return false
        seen.add(d.id)
        return true
      })
      categoryTotal.value = categoryDocs.value.length
    } else {
      // 叶子分类：后端分页
      const res = await docsApi.list({
        categoryId,
        pageNum: categoryPage.value,
        pageSize: categoryPageSize,
      } as any)
      if (token !== categoryLoadToken) return
      categoryDocs.value = res.records || []
      categoryTotal.value = res.total || 0
    }
  } catch (e: unknown) {
    if (token !== categoryLoadToken) return
    categoryDocs.value = []
    categoryTotal.value = 0
    // UI/UX 5.1 §六：记录原始异常，由 docsErrorDesc 统一翻译（渲染层不碰 err.message），
    // 并且**必须**让 UI 区分「失败」与「这个分类确实没文档」。
    docsError.value = e
    notify(getApiError(e, '加载文档失败'), 'error')
  } finally {
    if (token === categoryLoadToken) loading.value = false
  }
}

/** 错误态重试：重新拉取当前选中的分类 */
function retryCategoryDocs() {
  const cid = activeCategoryId.value
  if (cid == null) return
  void loadCategoryDocs(cid)
}

function changePage(page: number) {
  if (page < 1 || page > categoryTotalPages.value) return
  categoryPage.value = page
  if (activeCategoryId.value) {
    loadCategoryDocs(activeCategoryId.value)
  }
}

function getAllChildIds(id: number): number[] {
  const result: number[] = [id]
  const cat = findCategoryById(allCategories.value, id)
  if (cat?.children) {
    for (const child of cat.children) {
      result.push(...getAllChildIds(child.id))
    }
  }
  return result
}

function onCategoryClick(cat: CategoryVO) {
  activeCategoryId.value = cat.id
  activeKey.value = 'all'
  categoryPage.value = 1
  sidebarOpen.value = false
  router.replace({ path: '/knowledge', query: { categoryId: String(cat.id) } })
}

function onAllClick() {
  activeCategoryId.value = null
  activeKey.value = 'all'
  categoryPage.value = 1
  sidebarOpen.value = false
  router.replace({ path: '/knowledge' })
}

function handleSearchKey(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  goSearch()
}

const goSearch = () => {
  const kw = searchKeyword.value.trim()
  if (kw) {
    saveSearchHistory(kw)
  }
  showSearchHistory.value = false
  router.push({ path: '/search', query: { q: kw } })
}

// ===== 搜索历史 =====
function loadSearchHistory() {
  try {
    const stored = localStorage.getItem('kb_search_history')
    if (stored) {
      searchHistory.value = JSON.parse(stored)
    }
  } catch {
    searchHistory.value = []
  }
}

function saveSearchHistory(keyword: string) {
  const updated = [keyword, ...searchHistory.value.filter(k => k !== keyword)].slice(0, 10)
  searchHistory.value = updated
  try {
    localStorage.setItem('kb_search_history', JSON.stringify(updated))
  } catch {
    // localStorage 不可用时静默失败
  }
}

function clearSearchHistory() {
  searchHistory.value = []
  localStorage.removeItem('kb_search_history')
}

function searchFromHistory(keyword: string) {
  searchKeyword.value = keyword
  showSearchHistory.value = false
  goSearch()
}

function hideSearchHistory() {
  setTimeout(() => { showSearchHistory.value = false }, 200)
}

const goToDoc = (id: number) => {
  router.push(`/doc/${id}`)
}

const importMenuOpen = ref(false)
const importMenuRef = ref<HTMLElement | null>(null)

/** 点击外部关闭导入菜单（§十八：交互反馈） */
function onDocClick(e: MouseEvent): void {
  if (importMenuOpen.value && importMenuRef.value && !importMenuRef.value.contains(e.target as Node)) {
    importMenuOpen.value = false
  }
}

const goUpload = () => {
  importMenuOpen.value = false
  router.push('/knowledge/upload')
}

const goImport = () => {
  importMenuOpen.value = false
  router.push('/knowledge/import')
}

const goReader = () => {
  importMenuOpen.value = false
  router.push('/knowledge/reader')
}

/** §十二：粘贴文本 → AI 整理页「粘贴新资料」tab（该页为唯一真实的文本导入通道） */
const goProcessPaste = () => {
  importMenuOpen.value = false
  router.push({ path: '/knowledge/process', query: { source: 'paste' } })
}

const goProcess = () => {
  importMenuOpen.value = false
  router.push('/knowledge/process')
}

const formatRelativeTime = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = (now.getTime() - date.getTime()) / 1000
  if (diff < 3600) return `${Math.max(1, Math.floor(diff / 60))} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 172800) return '昨天'
  if (diff < 604800) return `${Math.floor(diff / 86400)} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const loadData = async (retry = false) => {
    void loadStats(retry)
    void loadLatest(retry)
    await homeResource.run(retry)
    if (homeStatus.value === 'error' || !homeResource.data.value) return
    const cats = homeResource.data.value
    allCategories.value = cats
    topCategories.value = cats.slice(0, 6)

    // 如果 URL 有 categoryId，选中对应分类（由 watch(activeCategoryId) 自动加载）
    const catId = route.query.categoryId
    if (catId && typeof catId === 'string') {
      activeCategoryId.value = Number(catId)
    }
}

// 路由 categoryId 变化时，仅同步选中状态；实际加载交给 watch(activeCategoryId)
watch(
  () => route.query.categoryId,
  (newId) => {
    if (newId && typeof newId === 'string') {
      const id = Number(newId)
      if (activeCategoryId.value !== id) {
        activeCategoryId.value = id
      }
    } else if (activeCategoryId.value !== null) {
      activeCategoryId.value = null
    }
  }
)

// 选中分类变化 → 统一加载文档；配合 categoryLoadToken，只有最新一次点击的结果会被采用
watch(activeCategoryId, (id) => {
  categoryPage.value = 1
  selectedTag.value = null
  if (id) {
    loadCategoryDocs(id)
  } else {
    categoryDocs.value = []
    categoryTotal.value = 0
  }
})

// ===== 邀请成员功能 =====
const inviteDialogVisible = ref(false)
const inviteForm = ref({
  email: '',
  role: 'READER' as 'OWNER' | 'EDITOR' | 'READER',
})

const roleOptions = [
  {
    value: 'READER',
    label: '阅读者',
    description: '只能查看和搜索文档',
  },
  {
    value: 'EDITOR',
    label: '编辑者',
    description: '可以编辑和创建文档',
  },
  {
    value: 'OWNER',
    label: '管理员',
    description: '拥有完整管理权限',
  },
]

function openInviteDialog() {
  if (!activeCategoryId.value) {
    notify('请先选择一个知识库', 'warning')
    return
  }
  inviteForm.value = { email: '', role: 'READER' }
  inviteDialogVisible.value = true
}

function closeInviteDialog() {
  inviteDialogVisible.value = false
}

async function handleInvite() {
  const email = inviteForm.value.email.trim()
  if (!email) {
    notify('请输入成员邮箱', 'warning')
    return
  }
  if (!email.includes('@')) {
    notify('请输入有效的邮箱地址', 'warning')
    return
  }

  try {
    await adminApi.addKbMember({
      categoryId: activeCategoryId.value!,
      email,
      role: inviteForm.value.role,
    })
    const roleLabel = roleOptions.find(r => r.value === inviteForm.value.role)?.label
    notify(`已邀请 ${email} 加入知识库，角色：${roleLabel}`, 'success')
    closeInviteDialog()
  } catch (e: unknown) {
    notify(getApiError(e, '邀请失败，请稍后再试'), 'error')
  }
}

// ⌘K / Ctrl+K 快捷键聚焦搜索框
onMounted(() => {
  compactMedia = window.matchMedia?.('(max-width: 1024px)')
  syncCompact()
  compactMedia?.addEventListener('change', syncCompact)
  void learning.load()
  if (auth.isLoggedIn) void readingResource.run()
  loadSearchHistory()
  loadData()
})

onUnmounted(() => {
  compactMedia?.removeEventListener('change', syncCompact)
  document.removeEventListener('click', onDocClick)
})
</script>

<style scoped>
/* 全屏布局 */
.knowledge-layout {
  display: flex;
  /* UI/UX 5.1 §二十：负边距必须与父容器 padding **同单位、同断点**。
     旧写法写死 -24px，而父级用的是 Tailwind `px-6`（= 1.5rem）；本应用根字号是 14px，
     于是父级 padding 实际为 21px、这里却外扩 24px —— 右侧恒定多出 3px，
     在 1440/1280/1024 三档宽度都能测到横向溢出。
     `px-4`（<640px）与 `px-6` 的换档由文件末尾的媒体查询承接。 */
  margin: -1.5rem -1.5rem 0;
  min-height: calc(100vh - 56px);
}

.knowledge-main {
  flex: 1;
  min-width: 0;
  padding: 24px 32px 40px;
  overflow-y: auto;
  height: calc(100vh - 56px);
}

/* 顶部搜索栏 */
.top-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
}

.search-box {
  position: relative;
  flex: 1;
  min-width: 0;
  max-width: 560px;
}

.search-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kf-muted-foreground);
}

.search-input:not(.kf-form-control) {
  height: 44px;
  border-radius: 10px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.search-input {
  width: 100%;
  padding-left: 44px;
  padding-right: 64px;
  font-size: var(--kf-fs-body-md);
  color: var(--kf-foreground);
}

.search-input:not(.kf-form-control):hover {
  border-color: var(--kf-primary);
}

.search-input:not(.kf-form-control):focus,
.search-input:not(.kf-form-control):focus-visible {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

.search-shortcut {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: var(--kf-fs-xs);
  font-family: var(--font-mono);
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-weight: 500;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 18px;
  border-radius: 10px;
  border: none;
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.15s, box-shadow 0.15s;
  white-space: nowrap;
}

.upload-btn:hover {
  opacity: 0.9;
}

.upload-btn:active {
  opacity: 0.9;
  transform: scale(0.98);
}

.upload-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

.upload-btn.small {
  height: 36px;
  padding: 0 14px;
  font-size: var(--kf-fs-body-sm);
}

.import-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.import-btn:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}

.import-btn:active {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.98);
}

.import-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

/* 问候语 */
.greeting-section {
  margin-bottom: 36px;
}

.greeting-title {
  font-size: var(--kf-fs-h3);
  line-height: var(--kf-lh-h3);
  /* UI/UX V1.0：页面主标题字重改走令牌（原 700），与全站 h1 一致 */
  font-weight: var(--kf-fw-h1);
  color: var(--kf-foreground);
  margin-bottom: 16px;
}

.quote-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 20px 24px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.04), rgba(139, 92, 246, 0.04));
  border: 1px solid rgba(59, 111, 224, 0.1);
}

.quote-icon {
  color: var(--kf-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.quote-text {
  font-size: var(--kf-fs-body-lg);
  line-height: var(--kf-lh-body-lg);
  font-weight: 500;
  color: var(--kf-foreground);
  margin-bottom: 6px;
}

.quote-author {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}

/* 通用区块 */
.section {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: var(--kf-fs-body-lg);
  line-height: var(--kf-lh-body-lg);
  font-weight: 600;
  color: var(--kf-foreground);
  margin-bottom: 16px;
}

.section-header .section-title {
  margin-bottom: 0;
}

.view-all-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  margin-right: -8px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-primary);
  text-decoration: none;
  white-space: nowrap;
  transition: background 0.15s, opacity 0.15s;
}

.view-all-link:hover {
  opacity: 0.8;
  background: rgba(59, 111, 224, 0.08);
}

.view-all-link:active {
  opacity: 1;
  background: rgba(59, 111, 224, 0.14);
}

.view-all-link:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

/* 快速访问 */
.quick-access-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}

/* ===== 3.2 §41：我的知识工具（已启用 App，动态渲染） ===== */
.kf-app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.kf-app-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.kf-app-card:hover {
  border-color: var(--kf-primary);
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.kf-app-card:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-color: var(--kf-primary);
}

.kf-app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 8px;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kf-primary);
}

.kf-app-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kf-app-arrow {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s, background 0.15s;
}

.quick-card:hover {
  border-color: var(--kf-primary);
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.quick-card:active {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.04);
  transform: translateY(0) scale(0.98);
}

.quick-card:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-radius: 12px;
  border-color: var(--kf-primary);
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-info {
  min-width: 0;
  max-width: 100%;
}

.quick-name {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-count {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}

/* 学习概览统计 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

/* 最近浏览 */
.recent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.recent-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s, transform 0.15s;
}

.recent-card:hover {
  border-color: var(--kf-primary);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.06);
}

.recent-card:active {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.04);
  transform: scale(0.98);
}

.recent-card:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-radius: 10px;
  border-color: var(--kf-primary);
}

.recent-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.recent-info {
  flex: 1;
  min-width: 0;
}

.recent-title {
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  color: var(--kf-foreground);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  font-size: var(--kf-fs-caption);
}

.recent-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.empty-hint {
  padding: 40px 0;
  text-align: center;
  font-size: var(--kf-fs-body-md);
  color: var(--kf-muted-foreground);
}

/* ============ 分类视图 ============ */
.category-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 面包屑 */
.category-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  gap: 6px;
  font-size: var(--kf-fs-body-sm);
}

.breadcrumb-link {
  padding: 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  border-radius: var(--kf-radius-sm);
  transition: color 0.15s;
}

.breadcrumb-link:hover {
  color: var(--kf-primary);
}

.breadcrumb-link:active {
  color: var(--kf-primary);
  opacity: 0.75;
}

.breadcrumb-link:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  color: var(--kf-primary);
}

.breadcrumb-current {
  color: var(--kf-foreground);
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breadcrumb .sep {
  color: var(--kf-muted-foreground);
  opacity: 0.5;
}

.header-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
}

/* 分类 hero 区 */
.category-hero {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 24px 28px;
  border-radius: 14px;
  border: 1px solid;
}

.hero-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-title {
  font-size: var(--kf-fs-h4);
  line-height: var(--kf-lh-h4);
  font-weight: 700;
  color: var(--kf-foreground);
  margin: 0 0 6px;
}

.hero-desc {
  font-size: var(--kf-fs-body-md);
  color: var(--kf-muted-foreground);
  margin: 0;
}

/* 子分类网格 */
.sub-category-section {
  margin-bottom: 8px;
}

.sub-category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.sub-category-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 12px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s, background 0.15s;
}

.sub-category-card:hover {
  border-color: var(--kf-primary);
  box-shadow: 0 4px 14px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}

.sub-category-card:active {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.04);
  transform: translateY(0) scale(0.98);
}

.sub-category-card:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-color: var(--kf-primary);
}

.sub-category-card:focus-visible .sub-arrow {
  opacity: 1;
  color: var(--kf-primary);
}

.sub-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sub-info {
  flex: 1;
  min-width: 0;
}

.sub-name {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  margin: 0 0 4px;
  font-variant-numeric: tabular-nums;
}

.sub-divider {
  opacity: 0.5;
}

.sub-sub {
  color: var(--kf-muted-foreground);
}

.sub-desc {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-arrow {
  color: var(--kf-muted-foreground);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  flex-shrink: 0;
  margin-top: 4px;
}

.sub-category-card:hover .sub-arrow {
  opacity: 1;
  color: var(--kf-primary);
}

/* 文档列表 */
.docs-section {
  margin-top: 4px;
}

.docs-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--kf-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--kf-card);
}

.doc-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kf-border);
  cursor: pointer;
  transition: background 0.12s;
}

.doc-row:last-child {
  border-bottom: none;
}

.doc-row:hover {
  background: rgba(59, 111, 224, 0.03);
}

.doc-row:active {
  background: rgba(59, 111, 224, 0.08);
}

.doc-row:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
  background: rgba(59, 111, 224, 0.03);
}

.doc-row:focus-visible .doc-row-arrow {
  opacity: 1;
  color: var(--kf-primary);
}

.doc-row-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-row-info {
  flex: 1;
  min-width: 0;
}

.doc-row-title {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row-summary {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  margin: 0 0 6px;
  line-height: var(--kf-lh-body-sm);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-row-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  gap: 8px;
  font-size: var(--kf-fs-caption);
  margin: 0;
}

.doc-row-tag {
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row-time {
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.doc-row-views {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.doc-row-arrow {
  color: var(--kf-muted-foreground);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  flex-shrink: 0;
  margin-top: 6px;
}

.doc-row:hover .doc-row-arrow {
  opacity: 1;
  color: var(--kf-primary);
}

/* 叶子分类头 */
.leaf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px;
  border-radius: 14px;
  border: 1px solid;
  flex-wrap: wrap;
}

.leaf-info {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 0;
}

.leaf-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.leaf-title {
  font-size: var(--kf-fs-h4);
  line-height: var(--kf-lh-h4);
  font-weight: 700;
  color: var(--kf-foreground);
  margin: 0 0 4px;
}

.leaf-desc {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  margin: 0;
}

/* 加载/空态 */
.docs-loading,
.docs-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 24px;
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: 12px;
  text-align: center;
}

.docs-empty h3 {
  font-size: var(--kf-fs-body-lg);
  line-height: var(--kf-lh-body-lg);
  font-weight: 600;
  color: var(--kf-foreground);
  margin: 0;
}

.docs-empty p {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  margin: 0;
}

.empty-icon {
  color: var(--kf-muted-foreground);
  opacity: 0.6;
}

/* UI/UX 5.1 §六：错误态给「下一步」，不只是报错 */
.docs-retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 8px 14px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
  background: var(--kf-muted);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  cursor: pointer;
  transition: background-color 160ms ease, color 160ms ease;
}

.docs-retry-btn:hover {
  background: var(--kf-accent);
}

.docs-retry-btn:focus-visible {
  outline: 2px solid var(--kf-primary);
  outline-offset: 2px;
}

.loading-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kf-border);
  background: var(--kf-card);
}

.loading-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--kf-muted);
  animation: pulse 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.loading-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.loading-line {
  height: 12px;
  border-radius: 4px;
  background: var(--kf-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

@media (max-width: 768px) {
  .knowledge-layout {
    /* UI/UX 5.1 §二十：此处父级仍用 Tailwind `px-6`（= 1.5rem，本应用根字号 14px → 21px）。
       旧写法写死 -16px，与父级 padding 不符（既造成横向溢出、又留下内缩缝隙）。 */
    margin: -1.5rem -1.5rem 0;
  }
  .knowledge-main {
    padding: 16px;
  }
  .top-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .upload-btn {
    height: 40px;
    justify-content: center;
  }
  .greeting-title {
    font-size: var(--kf-fs-h4);
    line-height: var(--kf-lh-h4);
  }
  .quick-access-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .sub-category-grid {
    grid-template-columns: 1fr;
  }
  .leaf-header,
  .category-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* UI/UX 5.1 §二十：<640px 时父级 Tailwind padding 换成 `px-4`（= 1rem = 14px），
   负边距必须同步换档，否则会多外扩 7px 造成横向溢出。
   必须写在上面 768 媒体块**之后**（同为 max-width 命中时后者生效）。 */
@media (max-width: 639px) {
  .knowledge-layout {
    margin: -1.5rem -1rem 0;
  }
}

/* ===== 邀请成员对话框 ===== */
.invite-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.invite-dialog {
  width: 100%;
  max-width: 480px;
  background: var(--kf-card);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.invite-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--kf-border);
}

.invite-dialog-header h3 {
  margin: 0;
  font-size: var(--kf-fs-h4);
  line-height: var(--kf-lh-h4);
  font-weight: 600;
  color: var(--kf-foreground);
}

.invite-close {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kf-muted-foreground);
  padding: 4px;
  border-radius: 6px;
  transition: all 0.15s;
}

.invite-close:hover {
  background: var(--kf-muted);
  color: var(--kf-foreground);
}

.invite-close:active {
  background: var(--kf-muted);
  color: var(--kf-foreground);
  transform: scale(0.94);
}

.invite-close:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  color: var(--kf-foreground);
}

.invite-dialog-body {
  padding: 24px;
}

.invite-desc {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-md);
  margin: 0 0 20px;
  line-height: var(--kf-lh-body-md);
}

.invite-form-group {
  margin-bottom: 20px;
}

.invite-form-group label {
  display: block;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
  margin-bottom: 8px;
}

.invite-form-group .form-input:not(.kf-form-control) {
  border-radius: 10px;
  background: var(--kf-background);
  border: 1px solid var(--kf-border);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.invite-form-group .form-input {
  width: 100%;
  padding: 10px 14px;
  font-size: var(--kf-fs-body-md);
  color: var(--kf-foreground);
  box-sizing: border-box;
}

.invite-form-group .form-input:not(.kf-form-control):hover {
  border-color: var(--kf-primary);
}

.invite-form-group .form-input:not(.kf-form-control):focus,
.invite-form-group .form-input:not(.kf-form-control):focus-visible {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

.role-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.role-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--kf-border);
  cursor: pointer;
  transition: all 0.15s;
}

.role-option:hover {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.04);
}

.role-option:active {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.1);
}

.role-option:focus-within {
  border-color: var(--kf-primary);
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

.role-option.active {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.08);
}

.role-option input[type="radio"] {
  margin: 0;
  accent-color: var(--kf-primary);
  margin-top: 3px;
}

.role-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.role-name {
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  color: var(--kf-foreground);
}

.role-desc {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.invite-dialog-footer {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid var(--kf-border);
}

.invite-dialog-footer .btn-secondary,
.invite-dialog-footer .btn-primary {
  flex: 1;
  height: 40px;
  border-radius: 10px;
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.invite-dialog-footer .btn-secondary:focus-visible,
.invite-dialog-footer .btn-primary:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

.invite-dialog-footer .btn-secondary {
  background: var(--kf-background);
  color: var(--kf-foreground);
  border: 1px solid var(--kf-border);
}

.invite-dialog-footer .btn-secondary:hover {
  background: var(--kf-muted);
}

.invite-dialog-footer .btn-secondary:active {
  background: var(--kf-muted);
  border-color: var(--kf-primary);
  transform: scale(0.98);
}

.invite-dialog-footer .btn-primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}

.invite-dialog-footer .btn-primary:hover {
  opacity: 0.9;
}

.invite-dialog-footer .btn-primary:active {
  opacity: 0.9;
  transform: scale(0.98);
}

/* leaf-header 操作区 */
.leaf-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.btn-ghost.small {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  background: var(--kf-card);
  color: var(--kf-foreground);
  border: 1px solid var(--kf-border);
  transition: all 0.15s;
}

.btn-ghost.small:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}

.btn-ghost.small:active {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.98);
}

.btn-ghost.small:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .leaf-actions {
    flex-direction: column;
    width: 100%;
  }

  .btn-ghost.small,
  .upload-btn.small {
    width: 100%;
    justify-content: center;
  }
}

/* ===== 移动端侧边栏抽屉 ===== */
.sidebar-overlay {
  display: none;
}

.mobile-menu-btn {
  display: none;
}

@media (max-width: 1024px) {
  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 1000;
  }

  .mobile-menu-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 8px;
    border: 1px solid var(--kf-border);
    background: var(--kf-card);
    color: var(--kf-foreground);
    cursor: pointer;
    flex-shrink: 0;
    transition: border-color 0.15s, color 0.15s, background 0.15s, transform 0.15s;
  }

  .mobile-menu-btn:hover {
    border-color: var(--kf-primary);
    color: var(--kf-primary);
  }

  .mobile-menu-btn:active {
    border-color: var(--kf-primary);
    color: var(--kf-primary);
    background: rgba(59, 111, 224, 0.08);
    transform: scale(0.96);
  }

  .mobile-menu-btn:focus-visible {
    outline: 2px solid var(--kf-ring);
    outline-offset: 2px;
    border-color: var(--kf-primary);
  }
}

/* ===== 分页 ===== */
.docs-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 0;
}

.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  cursor: pointer;
  transition: all 0.15s;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}

.page-btn:active:not(:disabled) {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.96);
}

.page-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-color: var(--kf-primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-muted-foreground);
  min-width: 60px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* ===== 搜索历史下拉 ===== */
.search-history-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  z-index: 100;
  overflow: hidden;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px 6px;
}

.history-title {
  font-size: var(--kf-fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--kf-muted-foreground);
}

.history-clear {
  background: transparent;
  border: none;
  font-size: var(--kf-fs-xs);
  color: var(--kf-primary);
  cursor: pointer;
  padding: 0;
  border-radius: var(--kf-radius-sm);
  transition: color 0.15s, opacity 0.15s;
}

.history-clear:hover {
  text-decoration: underline;
}

.history-clear:active {
  text-decoration: underline;
  opacity: 0.75;
}

.history-clear:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  text-decoration: underline;
}

.history-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 14px 12px;
}

.history-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: var(--kf-fs-caption);
  max-width: 100%;
  min-width: 0;
  background: var(--kf-muted);
  color: var(--kf-foreground);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}

.history-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
  border-color: var(--kf-primary);
}

.history-item:active {
  background: rgba(59, 111, 224, 0.16);
  color: var(--kf-primary);
  border-color: var(--kf-primary);
  transform: scale(0.97);
}

.history-item:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  color: var(--kf-primary);
  border-color: var(--kf-primary);
}

.history-icon {
  color: var(--kf-muted-foreground);
}

/* ===== 标签筛选栏 ===== */
.tag-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 0;
}

.tag-filter-btn {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  border: 1px solid var(--kf-border);
  cursor: pointer;
  transition: all 0.15s;
}

.tag-filter-btn:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}

.tag-filter-btn:active {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.97);
}

.tag-filter-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-color: var(--kf-primary);
}

.tag-filter-btn.active {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  border-color: var(--kf-primary);
}

/* Architecture-4.3：AI 整理资料 入口（知识处理，次级强调） */
.import-btn.kp-ai {
  color: var(--kf-primary);
  border-color: rgba(59, 111, 224, 0.35);
  background: rgba(59, 111, 224, 0.05);
}

/* ══════════ UI/UX 5.0 §十二：导入知识下拉（顶部唯一主操作）══════════ */
.kf-import-dropdown {
  position: relative;
  flex-shrink: 0;
}
.kf-import-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 60;
  min-width: 168px;
  padding: 6px;
  background: var(--kf-card, #ffffff);
  border: 1px solid var(--kf-border, #e5e7eb);
  border-radius: var(--kf-radius-md, 8px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kf-import-menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: var(--kf-radius-sm, 6px);
  background: transparent;
  color: var(--kf-foreground, #1f2937);
  font-size: var(--kf-fs-body-sm, 13px);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}
.kf-import-menu button:hover {
  background: var(--kf-muted, #f3f4f6);
}
.kf-import-menu button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kf-ring, rgba(59, 111, 224, 0.4));
}
/* Phase B: embedded workspace follows Phase A shell spacing, without negative margins. */
.knowledge-layout.knowledge-v2 { margin:0;min-height:0;min-width:0; }
.knowledge-v2 .knowledge-main { padding:var(--kf-space-5);height:calc(100dvh - var(--kf-nav-height) - var(--kf-space-6) * 2); }
.knowledge-v2 .greeting-title { font-family:inherit;font-size:var(--kf-page-title-size);line-height:1.3; }
.knowledge-intro { margin-top:var(--kf-space-3);color:var(--kf-muted-foreground);line-height:var(--kf-lh-body-md); }
.knowledge-destinations { display:flex;flex-wrap:wrap;gap:var(--kf-space-3);margin-top:var(--kf-space-5); }
.knowledge-destinations a { display:inline-flex;align-items:center;gap:var(--kf-space-2);min-height:var(--kf-control-hit-size);padding:var(--kf-space-2) var(--kf-space-3);border:1px solid var(--kf-border);border-radius:var(--kf-radius-md);color:var(--kf-foreground);background:var(--kf-card); }
.knowledge-v2 :is(a,button):focus-visible { outline:2px solid var(--kf-ring);outline-offset:2px; }
.knowledge-v2 .section-title { font-family:inherit;font-size:var(--kf-section-title-size); }
.knowledge-v2 :is(.quick-access-grid,.recent-grid) { grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr)); }
.knowledge-v2 .quick-card { box-shadow:none; }
@media(max-width:768px){.knowledge-v2 .knowledge-main{padding:var(--kf-space-3);height:auto;min-height:70dvh}.knowledge-v2 .search-box{width:100%;max-width:none}.knowledge-destinations{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.knowledge-destinations a{font-size:var(--kf-fs-body-sm)}}
.knowledge-continuity :deep(.next-action-card) { margin-top:var(--kf-space-3); }
.knowledge-continuity :deep(button.bg-primary-500.text-white) { color:var(--kf-primary-foreground); }
.knowledge-reading-list { display:grid;gap:var(--kf-space-2);margin-top:var(--kf-space-3); }
.knowledge-reading-list a { display:flex;align-items:center;gap:var(--kf-space-3);padding:var(--kf-space-3);border-bottom:1px solid var(--kf-border);overflow-wrap:anywhere;color:var(--kf-foreground); }
</style>
