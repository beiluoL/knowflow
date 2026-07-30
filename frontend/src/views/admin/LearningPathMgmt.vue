<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm" style="color: var(--kb-muted-foreground);">
      <span style="color: var(--kb-primary);" class="font-medium">学习管理</span>
      <Icon name="chevron-right" :size="14" />
      <span style="color: var(--kb-foreground);" class="font-medium">学习路径</span>
    </nav>

    <!-- 页面标题 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--kb-foreground);">学习路径管理</h1>
        <p class="text-sm mt-1" style="color: var(--kb-muted-foreground);">
          配置学习路径、发布章节，或使用 AI 基于知识库自动生成路径与章节
        </p>
      </div>
      <div class="flex items-center gap-3 flex-wrap">
        <Button variant="secondary" icon-name="sparkles" @click="openAiGenerate">AI 生成路径</Button>
        <Button icon-name="plus" @click="openCreatePath">新建路径</Button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(59,111,224,0.08);">
            <Icon name="route" :size="20" style="color: var(--kb-primary);" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">路径总数</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.total }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">条</span>
        </p>
      </div>
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(16,185,129,0.08);">
            <Icon name="check-circle" :size="20" style="color: var(--kb-accent);" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">已发布</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.published }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">条</span>
        </p>
      </div>
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(245,158,11,0.08);">
            <Icon name="edit" :size="20" style="color: #f59e0b;" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">草稿</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.draft }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">条</span>
        </p>
      </div>
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(139,92,246,0.08);">
            <Icon name="list-ordered" :size="20" style="color: #8b5cf6;" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">章节总数</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.totalChapters }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">节</span>
        </p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex flex-col sm:flex-row sm:items-center gap-3">
        <div class="flex-1 relative">
          <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2" style="color: var(--kb-muted-foreground);" />
          <input
            v-model="searchQuery"
            placeholder="搜索路径标题或描述..."
            class="w-full h-10 pl-10 pr-3 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)]"
            style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
          />
        </div>
        <select
          v-model="filterLevel"
          class="h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
          style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
        >
          <option value="">全部难度</option>
          <option value="入门">入门</option>
          <option value="进阶">进阶</option>
          <option value="高级">高级</option>
        </select>
        <select
          v-model="filterStatus"
          class="h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
          style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
        >
          <option value="">全部状态</option>
          <option value="1">已发布</option>
          <option value="0">草稿</option>
        </select>
      </div>
    </div>

    <!-- 路径列表 -->
    <div class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
      <!-- 加载状态 -->
      <div v-if="loading" class="p-12 flex flex-col items-center justify-center gap-3">
        <div class="w-8 h-8 border-2 rounded-full animate-spin" style="border-color: var(--kb-border); border-top-color: var(--kb-primary);"></div>
        <p class="text-sm" style="color: var(--kb-muted-foreground);">加载学习路径...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="filteredPaths.length === 0" class="p-12 flex flex-col items-center justify-center gap-3">
        <div class="w-14 h-14 rounded-full flex items-center justify-center" style="background: var(--kb-muted);">
          <Icon name="route" :size="28" style="color: var(--kb-muted-foreground);" />
        </div>
        <p class="text-sm" style="color: var(--kb-muted-foreground);">
          {{ paths.length === 0 ? '暂无学习路径，点击「新建路径」或「AI 生成路径」开始' : '没有符合条件的路径' }}
        </p>
      </div>

      <!-- 表头 -->
      <div v-else>
        <div class="px-6 py-3 grid grid-cols-12 gap-4 text-xs font-medium border-b" style="color: var(--kb-muted-foreground); border-color: var(--kb-border); background: var(--kb-muted);">
          <div class="col-span-4">路径标题</div>
          <div class="col-span-1 text-center">难度</div>
          <div class="col-span-2 text-center">章节/时长</div>
          <div class="col-span-2 text-center">报名人数</div>
          <div class="col-span-1 text-center">状态</div>
          <div class="col-span-2 text-center">操作</div>
        </div>
        <div class="divide-y" style="border-color: var(--kb-border);">
          <div
            v-for="path in filteredPaths"
            :key="path.id"
            class="px-6 py-4 grid grid-cols-12 gap-4 items-center transition-colors hover:bg-[var(--kb-muted)]/40"
          >
            <!-- 标题 -->
            <div class="col-span-4 min-w-0">
              <p class="font-medium truncate" style="color: var(--kb-foreground);">{{ path.title }}</p>
              <p class="text-xs truncate mt-0.5" style="color: var(--kb-muted-foreground);">
                {{ path.description || '暂无描述' }}
              </p>
            </div>
            <!-- 难度 -->
            <div class="col-span-1 text-center">
              <span
                class="inline-block px-2 py-0.5 rounded-full text-xs font-medium"
                :style="getLevelStyle(path.level)"
              >{{ path.level || '入门' }}</span>
            </div>
            <!-- 章节/时长 -->
            <div class="col-span-2 text-center text-sm tabular-nums" style="color: var(--kb-foreground);">
              <div>{{ path.chapterCount || 0 }} 章节</div>
              <div class="text-xs" style="color: var(--kb-muted-foreground);">
                {{ Math.round((path.totalDuration || 0) / 60) || 0 }} 小时
              </div>
            </div>
            <!-- 报名人数 -->
            <div class="col-span-2 text-center">
              <span class="inline-flex items-center gap-1 text-sm tabular-nums" style="color: var(--kb-foreground);">
                <Icon name="users" :size="14" style="color: var(--kb-muted-foreground);" />
                {{ path.enrolledCount || 0 }}
              </span>
            </div>
            <!-- 状态 -->
            <div class="col-span-1 text-center">
              <span
                class="inline-block px-2 py-0.5 rounded-full text-xs font-medium"
                :style="path.status === 1 ? { background: 'rgba(16,185,129,0.1)', color: 'var(--kb-accent)' } : { background: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' }"
              >{{ path.status === 1 ? '已发布' : '草稿' }}</span>
            </div>
            <!-- 操作 -->
            <div class="col-span-2 flex items-center justify-center gap-1">
              <button
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-primary)]/10"
                style="color: var(--kb-primary);"
                title="管理章节"
                @click="openChapterMgmt(path)"
              >
                <Icon name="list-ordered" :size="16" />
              </button>
              <button
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-primary)]/10"
                style="color: var(--kb-primary);"
                title="编辑"
                @click="openEditPath(path)"
              >
                <Icon name="edit" :size="16" />
              </button>
              <button
                v-if="path.status !== 1"
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-accent)]/10"
                style="color: var(--kb-accent);"
                title="发布"
                @click="publishPath(path)"
              >
                <Icon name="trending-up" :size="16" />
              </button>
              <button
                v-else
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-warning)]/10"
                style="color: #f59e0b;"
                title="下架"
                @click="unpublishPath(path)"
              >
                <Icon name="trending-down" :size="16" />
              </button>
              <button
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-destructive)]/10"
                style="color: var(--kb-destructive);"
                title="删除"
                @click="removePath(path)"
              >
                <Icon name="trash-2" :size="16" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑路径弹窗 -->
    <div
      v-if="showPathModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closePathModal"
    >
      <div class="rounded-xl w-full max-w-2xl shadow-xl animate-dropdown" style="background: var(--kb-card); border: 1px solid var(--kb-border);">
        <div class="flex items-center justify-between px-6 py-4 border-b" style="border-color: var(--kb-border);">
          <h3 class="text-lg font-semibold" style="color: var(--kb-foreground);">
            {{ editingPathId ? '编辑学习路径' : '新建学习路径' }}
          </h3>
          <button class="p-1 rounded transition-colors hover:bg-[var(--kb-muted)]" @click="closePathModal">
            <Icon name="x" :size="20" style="color: var(--kb-muted-foreground);" />
          </button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">路径标题 <span style="color: var(--kb-destructive);">*</span></label>
            <input
              v-model="pathForm.title"
              placeholder="例如：Java 全栈工程师学习路径"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">路径描述</label>
            <textarea
              v-model="pathForm.description"
              rows="3"
              placeholder="简要描述本路径的学习目标、适用人群..."
              class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            ></textarea>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">难度级别</label>
              <select
                v-model="pathForm.level"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              >
                <option value="入门">入门</option>
                <option value="进阶">进阶</option>
                <option value="高级">高级</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">排序权重</label>
              <input
                v-model.number="pathForm.sortOrder"
                type="number"
                min="0"
                placeholder="0"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              />
            </div>
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t" style="border-color: var(--kb-border);">
          <Button variant="secondary" @click="closePathModal">取消</Button>
          <Button :disabled="savingPath" @click="savePath">{{ savingPath ? '保存中...' : '保存' }}</Button>
        </div>
      </div>
    </div>

    <!-- AI 生成路径弹窗 -->
    <div
      v-if="showAiModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeAiModal"
    >
      <div class="rounded-xl w-full max-w-2xl shadow-xl animate-dropdown" style="background: var(--kb-card); border: 1px solid var(--kb-border);">
        <div class="flex items-center justify-between px-6 py-4 border-b" style="border-color: var(--kb-border);">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 rounded-lg flex items-center justify-center" style="background: rgba(59,111,224,0.1);">
              <Icon name="sparkles" :size="18" style="color: var(--kb-primary);" />
            </div>
            <div>
              <h3 class="text-lg font-semibold" style="color: var(--kb-foreground);">AI 自动生成学习路径</h3>
              <p class="text-xs" style="color: var(--kb-muted-foreground);">基于知识库文档内容，AI 将自动设计路径与章节</p>
            </div>
          </div>
          <button class="p-1 rounded transition-colors hover:bg-[var(--kb-muted)]" @click="closeAiModal">
            <Icon name="x" :size="20" style="color: var(--kb-muted-foreground);" />
          </button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <!-- 提示信息 -->
          <div class="rounded-lg p-3 flex items-start gap-2" style="background: rgba(59,111,224,0.06);">
            <Icon name="lightbulb" :size="16" class="mt-0.5 shrink-0" style="color: var(--kb-primary);" />
            <p class="text-xs leading-relaxed" style="color: var(--kb-foreground);">
              AI 将从知识库中检索相关文档作为参考，自动生成符合主题的学习路径与章节。生成结果默认为「草稿」状态，可在章节管理中进一步编辑后发布。
            </p>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">学习主题 <span style="color: var(--kb-destructive);">*</span></label>
            <input
              v-model="aiForm.topic"
              placeholder="例如：Spring Boot 微服务开发"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">补充说明</label>
            <textarea
              v-model="aiForm.description"
              rows="2"
              placeholder="可补充学习目标、重点内容、适用人群等（可选）"
              class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            ></textarea>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">参考知识库</label>
            <select
              v-model="aiForm.categoryId"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            >
              <option :value="undefined">全部知识库（不筛选）</option>
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
            </select>
            <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">
              选择知识库后，AI 将基于该库中的文档生成路径，内容更精准
            </p>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">目标难度</label>
              <select
                v-model="aiForm.level"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              >
                <option value="入门">入门</option>
                <option value="进阶">进阶</option>
                <option value="高级">高级</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">章节数量</label>
              <input
                v-model.number="aiForm.chapterCount"
                type="number"
                min="1"
                max="15"
                placeholder="5"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              />
              <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">建议 3-10 章，过多会影响生成质量</p>
            </div>
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t" style="border-color: var(--kb-border);">
          <Button variant="secondary" @click="closeAiModal">取消</Button>
          <Button :disabled="generatingAi || !aiForm.topic.trim()" @click="generatePathByAi">
            {{ generatingAi ? 'AI 生成中，请稍候...' : '开始生成' }}
          </Button>
        </div>
      </div>
    </div>

    <!-- 章节管理弹窗 -->
    <div
      v-if="showChapterModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeChapterModal"
    >
      <div class="rounded-xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-xl animate-dropdown" style="background: var(--kb-card); border: 1px solid var(--kb-border);">
        <div class="flex items-center justify-between px-6 py-4 border-b shrink-0" style="border-color: var(--kb-border);">
          <div class="min-w-0">
            <h3 class="text-lg font-semibold truncate" style="color: var(--kb-foreground);">章节管理</h3>
            <p class="text-xs mt-0.5 truncate" style="color: var(--kb-muted-foreground);">
              {{ currentPath?.title }} · 共 {{ chapters.length }} 章
            </p>
          </div>
          <div class="flex items-center gap-2">
            <Button size="sm" variant="secondary" icon-name="sparkles" @click="batchGenerateFlashcards" :disabled="batchGenerating">
              {{ batchGenerating ? '生成闪卡中...' : 'AI 批量生成闪卡' }}
            </Button>
            <button class="p-1 rounded transition-colors hover:bg-[var(--kb-muted)]" @click="closeChapterModal">
              <Icon name="x" :size="20" style="color: var(--kb-muted-foreground);" />
            </button>
          </div>
        </div>

        <div class="overflow-y-auto flex-1 px-6 py-4">
          <!-- 加载状态 -->
          <div v-if="loadingChapters" class="py-12 flex flex-col items-center gap-3">
            <div class="w-8 h-8 border-2 rounded-full animate-spin" style="border-color: var(--kb-border); border-top-color: var(--kb-primary);"></div>
            <p class="text-sm" style="color: var(--kb-muted-foreground);">加载章节...</p>
          </div>

          <!-- 空状态 -->
          <div v-else-if="chapters.length === 0" class="py-12 flex flex-col items-center gap-3">
            <div class="w-14 h-14 rounded-full flex items-center justify-center" style="background: var(--kb-muted);">
              <Icon name="list-ordered" :size="28" style="color: var(--kb-muted-foreground);" />
            </div>
            <p class="text-sm" style="color: var(--kb-muted-foreground);">该路径暂无章节</p>
            <Button size="sm" icon-name="plus" @click="openCreateChapter">添加第一章</Button>
          </div>

          <!-- 章节列表 -->
          <div v-else class="space-y-3">
            <div
              v-for="(chapter, idx) in chapters"
              :key="chapter.id"
              class="rounded-lg border p-4 transition-all"
              style="border-color: var(--kb-border); background: var(--kb-background);"
            >
              <div class="flex items-start gap-3">
                <div class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 text-sm font-bold tabular-nums" style="background: rgba(59,111,224,0.1); color: var(--kb-primary);">
                  {{ chapter.sortOrder ?? idx + 1 }}
                </div>
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 flex-wrap mb-1">
                    <p class="font-medium" style="color: var(--kb-foreground);">{{ chapter.title }}</p>
                    <span class="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full" style="background: var(--kb-muted); color: var(--kb-muted-foreground);">
                      <Icon name="clock" :size="12" />
                      {{ chapter.duration || 30 }} 分钟
                    </span>
                  </div>
                  <p v-if="chapter.content" class="text-xs line-clamp-2 mt-1" style="color: var(--kb-muted-foreground);">
                    {{ chapter.content }}
                  </p>
                </div>
                <div class="flex items-center gap-1 shrink-0">
                  <button
                    type="button"
                    class="p-1.5 rounded transition-colors hover:bg-[var(--kb-primary)]/10"
                    style="color: var(--kb-primary);"
                    title="AI 生成内容"
                    :disabled="generatingChapterId === chapter.id"
                    @click="generateChapterContent(chapter)"
                  >
                    <Icon :name="generatingChapterId === chapter.id ? 'loader' : 'sparkles'" :size="16" />
                  </button>
                  <button
                    type="button"
                    class="p-1.5 rounded transition-colors hover:bg-[var(--kb-primary)]/10"
                    style="color: var(--kb-primary);"
                    title="编辑"
                    @click="openEditChapter(chapter)"
                  >
                    <Icon name="edit" :size="16" />
                  </button>
                  <button
                    type="button"
                    class="p-1.5 rounded transition-colors hover:bg-[var(--kb-destructive)]/10"
                    style="color: var(--kb-destructive);"
                    title="删除"
                    @click="removeChapter(chapter)"
                  >
                    <Icon name="trash-2" :size="16" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="!loadingChapters" class="flex items-center justify-between px-6 py-4 border-t shrink-0" style="border-color: var(--kb-border);">
          <p class="text-xs" style="color: var(--kb-muted-foreground);">
            <Icon name="lightbulb" :size="12" class="inline mr-1" />
            点击 <Icon name="sparkles" :size="12" class="inline" /> 让 AI 为章节生成详细内容
          </p>
          <Button size="sm" icon-name="plus" @click="openCreateChapter">添加章节</Button>
        </div>
      </div>
    </div>

    <!-- 章节编辑弹窗 -->
    <div
      v-if="showChapterFormModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeChapterFormModal"
    >
      <div class="rounded-xl w-full max-w-2xl shadow-xl animate-dropdown" style="background: var(--kb-card); border: 1px solid var(--kb-border);">
        <div class="flex items-center justify-between px-6 py-4 border-b" style="border-color: var(--kb-border);">
          <h3 class="text-lg font-semibold" style="color: var(--kb-foreground);">
            {{ editingChapterId ? '编辑章节' : '新增章节' }}
          </h3>
          <button class="p-1 rounded transition-colors hover:bg-[var(--kb-muted)]" @click="closeChapterFormModal">
            <Icon name="x" :size="20" style="color: var(--kb-muted-foreground);" />
          </button>
        </div>
        <div class="px-6 py-4 space-y-4 max-h-[60vh] overflow-y-auto">
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">章节标题 <span style="color: var(--kb-destructive);">*</span></label>
            <input
              v-model="chapterForm.title"
              placeholder="例如：第一章 入门基础"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">学习时长（分钟）</label>
              <input
                v-model.number="chapterForm.duration"
                type="number"
                min="1"
                placeholder="30"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">排序序号</label>
              <input
                v-model.number="chapterForm.sortOrder"
                type="number"
                min="0"
                placeholder="1"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              />
            </div>
          </div>
          <!-- 参考文档选择 -->
          <div>
            <div class="flex items-center justify-between mb-1.5">
              <label class="text-sm font-medium" style="color: var(--kb-foreground);">参考文档（可选）</label>
              <button
                type="button"
                class="text-xs font-medium hover:opacity-80"
                style="color: var(--kb-primary);"
                @click="showDocPicker = !showDocPicker"
              >
                {{ showDocPicker ? '收起选择' : '选择文档' }}
              </button>
            </div>
            <p class="text-xs mb-2" style="color: var(--kb-muted-foreground);">
              选择知识库文档后，AI 将基于这些文档内容生成章节内容，质量更高
            </p>
            <!-- 已选文档 -->
            <div v-if="selectedDocs.length > 0" class="flex flex-wrap gap-1.5 mb-2">
              <span
                v-for="doc in selectedDocs"
                :key="doc.id"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs"
                style="background: rgba(59,111,224,0.1); color: var(--kb-primary);"
              >
                {{ doc.title }}
                <button type="button" class="hover:opacity-70" @click="removeSelectedDoc(doc.id)">
                  <Icon name="x" :size="12" />
                </button>
              </span>
            </div>
            <!-- 文档选择器 -->
            <div v-if="showDocPicker" class="rounded-lg border p-3 space-y-3" style="border-color: var(--kb-border); background: var(--kb-background);">
              <div class="flex items-center gap-2">
                <select
                  v-model="docPickerCategoryId"
                  class="flex-1 h-9 px-2 rounded text-xs border outline-none focus:border-[var(--kb-primary)]"
                  style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
                  @change="loadPickerDocs"
                >
                  <option :value="undefined">全部知识库</option>
                  <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
                </select>
                <div class="relative flex-1">
                  <Icon name="search" :size="14" class="absolute left-2 top-1/2 -translate-y-1/2" style="color: var(--kb-muted-foreground);" />
                  <input
                    v-model="docPickerKeyword"
                    placeholder="搜索文档..."
                    class="w-full h-9 pl-7 pr-2 rounded text-xs border outline-none focus:border-[var(--kb-primary)]"
                    style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
                  />
                </div>
              </div>
              <div class="max-h-48 overflow-y-auto space-y-1">
                <div
                  v-for="doc in filteredPickerDocs"
                  :key="doc.id"
                  class="flex items-center gap-2 p-2 rounded cursor-pointer transition-colors"
                  :style="isDocSelected(doc.id) ? { background: 'rgba(59,111,224,0.08)' } : {}"
                  @click="toggleDocSelection(doc)"
                >
                  <div
                    class="w-4 h-4 rounded border flex items-center justify-center shrink-0"
                    :style="isDocSelected(doc.id)
                      ? { background: 'var(--kb-primary)', borderColor: 'var(--kb-primary)' }
                      : { borderColor: 'var(--kb-border)' }"
                  >
                    <Icon v-if="isDocSelected(doc.id)" name="check" :size="12" style="color: white;" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-xs font-medium truncate" style="color: var(--kb-foreground);">{{ doc.title }}</p>
                    <p class="text-xs truncate" style="color: var(--kb-muted-foreground);">{{ doc.summary || '暂无摘要' }}</p>
                  </div>
                </div>
                <p v-if="pickerDocs.length === 0" class="text-xs text-center py-6" style="color: var(--kb-muted-foreground);">
                  暂无文档
                </p>
              </div>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">章节内容</label>
            <textarea
              v-model="chapterForm.content"
              rows="8"
              placeholder="可填写章节描述，或选择上方参考文档后点击「AI 生成」让 AI 自动生成详细内容（支持 Markdown）"
              class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y font-mono"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            ></textarea>
          </div>
        </div>
        <div class="flex items-center justify-between gap-3 px-6 py-4 border-t" style="border-color: var(--kb-border);">
          <Button
            size="sm"
            variant="ghost"
            icon-name="sparkles"
            :disabled="!chapterForm.title.trim() || generatingContent"
            @click="generateContentInForm"
          >
            {{ generatingContent ? 'AI 生成中...' : 'AI 生成内容' }}
          </Button>
          <div class="flex items-center gap-3">
            <Button variant="secondary" @click="closeChapterFormModal">取消</Button>
            <Button :disabled="savingChapter" @click="saveChapter">{{ savingChapter ? '保存中...' : '保存' }}</Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 后台-学习路径管理：路径 CRUD、章节管理、发布/下架、AI 自动生成路径与章节内容、批量生成闪卡
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { adminApi } from '@/api'
import type { AiGeneratePathPayload } from '@/api/admin'
import type {
  LearningPathVO,
  LearningChapterVO,
  LearningPathInput,
  ChapterInput,
  CategoryVO,
  DocVO,
} from '@/api/types'
import { notify, confirmDialog, getApiError } from '@/utils/toast'

const router = useRouter()

// ===== 知识库与文档 =====
const categories = ref<CategoryVO[]>([])
const loadingCategories = ref(false)

const loadCategories = async () => {
  if (categories.value.length > 0) return
  loadingCategories.value = true
  try {
    categories.value = await adminApi.learningCategories()
  } catch (e: unknown) {
    // 知识库加载失败不影响主流程，静默处理
  } finally {
    loadingCategories.value = false
  }
}

// ===== 路径列表 =====
const paths = ref<LearningPathVO[]>([])
const loading = ref(false)
const searchQuery = ref('')
const filterLevel = ref('')
const filterStatus = ref('')

const stats = computed(() => ({
  total: paths.value.length,
  published: paths.value.filter((p) => p.status === 1).length,
  draft: paths.value.filter((p) => p.status !== 1).length,
  totalChapters: paths.value.reduce((sum, p) => sum + (p.chapterCount || 0), 0),
}))

const filteredPaths = computed(() => {
  let result = [...paths.value]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(
      (p) =>
        (p.title || '').toLowerCase().includes(q) ||
        (p.description || '').toLowerCase().includes(q),
    )
  }
  if (filterLevel.value) {
    result = result.filter((p) => (p.level || '入门') === filterLevel.value)
  }
  if (filterStatus.value !== '') {
    result = result.filter((p) => String(p.status ?? 0) === filterStatus.value)
  }
  return result
})

const getLevelStyle = (level?: string) => {
  switch (level) {
    case '入门':
      return { background: 'rgba(16,185,129,0.1)', color: 'var(--kb-accent)' }
    case '进阶':
      return { background: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' }
    case '高级':
      return { background: 'rgba(239,68,68,0.1)', color: 'var(--kb-destructive)' }
    default:
      return { background: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' }
  }
}

const loadPaths = async () => {
  loading.value = true
  try {
    paths.value = await adminApi.learningPaths()
  } catch (e: unknown) {
    notify('加载学习路径失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

// ===== 路径 CRUD =====
const showPathModal = ref(false)
const editingPathId = ref<number | null>(null)
const savingPath = ref(false)
const pathForm = ref<LearningPathInput>({
  title: '',
  description: '',
  level: '入门',
  sortOrder: 0,
  status: 0,
})

const openCreatePath = () => {
  editingPathId.value = null
  pathForm.value = { title: '', description: '', level: '入门', sortOrder: 0, status: 0 }
  showPathModal.value = true
}

const openEditPath = (path: LearningPathVO) => {
  editingPathId.value = path.id
  pathForm.value = {
    title: path.title,
    description: path.description || '',
    level: path.level || '入门',
    sortOrder: path.sortOrder ?? 0,
    status: path.status ?? 0,
  }
  showPathModal.value = true
}

const closePathModal = () => {
  showPathModal.value = false
  editingPathId.value = null
}

const savePath = async () => {
  if (!pathForm.value.title.trim()) {
    notify('请填写路径标题', 'warning')
    return
  }
  savingPath.value = true
  try {
    if (editingPathId.value) {
      await adminApi.updateLearningPath(editingPathId.value, pathForm.value)
      notify('路径已更新', 'success')
    } else {
      await adminApi.createLearningPath(pathForm.value)
      notify('路径已创建', 'success')
    }
    closePathModal()
    await loadPaths()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    savingPath.value = false
  }
}

const removePath = async (path: LearningPathVO) => {
  if (!(await confirmDialog(`确定要删除路径「${path.title}」吗？该操作将连同其下章节一并删除。`))) return
  try {
    await adminApi.removeLearningPath(path.id)
    notify('删除成功', 'success')
    await loadPaths()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const publishPath = async (path: LearningPathVO) => {
  try {
    await adminApi.publishLearningPath(path.id)
    notify('路径已发布', 'success')
    await loadPaths()
  } catch (e: unknown) {
    notify('发布失败：' + getApiError(e), 'error')
  }
}

const unpublishPath = async (path: LearningPathVO) => {
  try {
    await adminApi.unpublishLearningPath(path.id)
    notify('路径已下架', 'success')
    await loadPaths()
  } catch (e: unknown) {
    notify('下架失败：' + getApiError(e), 'error')
  }
}

// ===== AI 生成路径 =====
const showAiModal = ref(false)
const generatingAi = ref(false)
const aiForm = ref<AiGeneratePathPayload>({
  topic: '',
  description: '',
  level: '入门',
  chapterCount: 5,
})

const openAiGenerate = () => {
  aiForm.value = { topic: '', description: '', level: '入门', chapterCount: 5 }
  showAiModal.value = true
  loadCategories()
}

const closeAiModal = () => {
  showAiModal.value = false
}

const generatePathByAi = async () => {
  if (!aiForm.value.topic.trim()) {
    notify('请填写学习主题', 'warning')
    return
  }
  generatingAi.value = true
  try {
    notify('AI 正在生成学习路径，请稍候...', 'info', 5000)
    const payload: AiGeneratePathPayload = {
      topic: aiForm.value.topic.trim(),
      level: aiForm.value.level,
      chapterCount: aiForm.value.chapterCount || 5,
    }
    if (aiForm.value.description?.trim()) {
      payload.description = aiForm.value.description.trim()
    }
    if (aiForm.value.categoryId) {
      payload.categoryId = aiForm.value.categoryId
    }
    await adminApi.aiGeneratePath(payload)
    notify('AI 生成完成，已创建为草稿路径', 'success')
    closeAiModal()
    await loadPaths()
  } catch (e: unknown) {
    notify('AI 生成失败：' + getApiError(e), 'error')
  } finally {
    generatingAi.value = false
  }
}

// ===== 章节管理 =====
const showChapterModal = ref(false)
const currentPath = ref<LearningPathVO | null>(null)
const chapters = ref<LearningChapterVO[]>([])
const loadingChapters = ref(false)

const openChapterMgmt = async (path: LearningPathVO) => {
  currentPath.value = path
  showChapterModal.value = true
  await loadChapters(path.id)
}

const closeChapterModal = () => {
  showChapterModal.value = false
  currentPath.value = null
  chapters.value = []
}

const loadChapters = async (pathId: number) => {
  loadingChapters.value = true
  try {
    chapters.value = await adminApi.learningChapters(pathId)
  } catch (e: unknown) {
    notify('加载章节失败：' + getApiError(e), 'error')
  } finally {
    loadingChapters.value = false
  }
}

// 章节表单
const showChapterFormModal = ref(false)
const editingChapterId = ref<number | null>(null)
const savingChapter = ref(false)
const chapterForm = ref<ChapterInput>({
  pathId: 0,
  title: '',
  content: '',
  sortOrder: 1,
  duration: 30,
})

// 文档选择器
const showDocPicker = ref(false)
const docPickerCategoryId = ref<number | undefined>(undefined)
const docPickerKeyword = ref('')
const pickerDocs = ref<DocVO[]>([])
const selectedDocs = ref<DocVO[]>([])
const loadingPickerDocs = ref(false)

const loadPickerDocs = async () => {
  loadingPickerDocs.value = true
  try {
    pickerDocs.value = await adminApi.learningDocs(docPickerCategoryId.value, 50)
  } catch (e: unknown) {
    notify('加载文档列表失败：' + getApiError(e), 'error')
  } finally {
    loadingPickerDocs.value = false
  }
}

const filteredPickerDocs = computed(() => {
  if (!docPickerKeyword.value.trim()) return pickerDocs.value
  const q = docPickerKeyword.value.toLowerCase()
  return pickerDocs.value.filter(
    (d) =>
      d.title.toLowerCase().includes(q) ||
      (d.summary || '').toLowerCase().includes(q),
  )
})

const isDocSelected = (docId: number) => {
  return selectedDocs.value.some((d) => d.id === docId)
}

const toggleDocSelection = (doc: DocVO) => {
  const idx = selectedDocs.value.findIndex((d) => d.id === doc.id)
  if (idx >= 0) {
    selectedDocs.value.splice(idx, 1)
  } else {
    selectedDocs.value.push(doc)
  }
}

const removeSelectedDoc = (docId: number) => {
  const idx = selectedDocs.value.findIndex((d) => d.id === docId)
  if (idx >= 0) selectedDocs.value.splice(idx, 1)
}



const openCreateChapter = () => {
  if (!currentPath.value) return
  router.push({ path: '/admin/learning/chapters/new', query: { pathId: String(currentPath.value.id) } })
}

const openEditChapter = (chapter: LearningChapterVO) => {
  router.push({ path: `/admin/learning/chapters/${chapter.id}/edit`, query: { pathId: String(chapter.pathId) } })
}

const closeChapterFormModal = () => {
  showChapterFormModal.value = false
  editingChapterId.value = null
}

const saveChapter = async () => {
  if (!chapterForm.value.title.trim()) {
    notify('请填写章节标题', 'warning')
    return
  }
  savingChapter.value = true
  try {
    if (editingChapterId.value) {
      await adminApi.updateChapter(editingChapterId.value, chapterForm.value)
      notify('章节已更新', 'success')
    } else {
      await adminApi.createChapter(chapterForm.value)
      notify('章节已创建', 'success')
    }
    closeChapterFormModal()
    if (currentPath.value) await loadChapters(currentPath.value.id)
    await loadPaths() // 刷新章节数统计
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    savingChapter.value = false
  }
}

const removeChapter = async (chapter: LearningChapterVO) => {
  if (!(await confirmDialog(`确定要删除章节「${chapter.title}」吗？`))) return
  try {
    await adminApi.removeChapter(chapter.id)
    notify('删除成功', 'success')
    if (currentPath.value) await loadChapters(currentPath.value.id)
    await loadPaths()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

// ===== AI 生成章节内容 =====
const generatingChapterId = ref<number | null>(null)
const generatingContent = ref(false)

const generateChapterContent = async (chapter: LearningChapterVO) => {
  generatingChapterId.value = chapter.id
  try {
    notify('AI 正在生成章节内容...', 'info', 5000)
    await adminApi.aiGenerateChapterContent(chapter.id)
    notify('内容已生成', 'success')
    if (currentPath.value) await loadChapters(currentPath.value.id)
  } catch (e: unknown) {
    notify('AI 生成失败：' + getApiError(e), 'error')
  } finally {
    generatingChapterId.value = null
  }
}

const generateContentInForm = async () => {
  if (!chapterForm.value.title.trim()) {
    notify('请先填写章节标题', 'warning')
    return
  }
  // 若是新建章节，需先保存后才能调用 AI（后端按章节 ID 检索文档生成）
  if (!editingChapterId.value) {
    notify('请先保存章节，再使用 AI 生成内容', 'warning')
    return
  }
  generatingContent.value = true
  try {
    const docIds = selectedDocs.value.length > 0 ? selectedDocs.value.map((d) => d.id) : undefined
    notify('AI 正在生成章节内容...', 'info', 5000)
    const updated = await adminApi.aiGenerateChapterContent(editingChapterId.value, docIds)
    chapterForm.value.content = updated.content || ''
    // 更新已选文档（如果后端同步了 docIds）
    if (updated.docIds) {
      const ids = updated.docIds.split(',').map((s) => parseInt(s.trim(), 10)).filter((n) => !isNaN(n))
      selectedDocs.value = pickerDocs.value.filter((d) => ids.includes(d.id))
    }
    notify('内容已生成，可点击保存', 'success')
  } catch (e: unknown) {
    notify('AI 生成失败：' + getApiError(e), 'error')
  } finally {
    generatingContent.value = false
  }
}

// ===== AI 批量生成闪卡 =====
const batchGenerating = ref(false)

const batchGenerateFlashcards = async () => {
  if (!currentPath.value) return
  if (chapters.value.length === 0) {
    notify('请先添加章节', 'warning')
    return
  }
  if (!(await confirmDialog(`将为「${currentPath.value.title}」的全部章节批量生成闪卡，是否继续？`))) return
  batchGenerating.value = true
  try {
    notify('AI 正在批量生成闪卡，请稍候...', 'info', 5000)
    const cards = await adminApi.aiGenerateFlashcards(currentPath.value.id)
    notify(`已生成 ${cards.length} 张闪卡`, 'success')
  } catch (e: unknown) {
    notify('AI 生成闪卡失败：' + getApiError(e), 'error')
  } finally {
    batchGenerating.value = false
  }
}

onMounted(loadPaths)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-dropdown {
  animation: dropdown 0.2s ease-out;
}

@keyframes dropdown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
