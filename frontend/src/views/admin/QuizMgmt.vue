<template>
  <div class="space-y-6 animate-fade-in">
    <nav class="flex items-center gap-2 text-sm text-gray-500">
      <span class="text-primary-500 font-medium">内容管理</span>
      <Icon name="chevron-right" :size="14" />
      <span class="text-gray-700 font-medium">智能题库</span>
    </nav>

    <!-- 页面标题 + 顶部按钮 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">智能出题管理</h1>
        <p class="text-gray-500 text-sm mt-1">AI 智能出题与手动题库管理</p>
      </div>
      <div class="flex items-center gap-3">
        <Button icon-name="sparkles" @click="openAiModal">AI 出题</Button>
        <Button variant="secondary" icon-name="plus" @click="openCreate">手动新增</Button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-blue-50">
            <Icon name="file-text" :size="20" class="text-blue-500" />
          </div>
          <span class="text-sm text-gray-600">题目总数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ totalCount }} <span class="text-sm font-normal text-gray-500">道</span></p>
      </div>

      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-purple-50">
            <Icon name="sparkles" :size="20" class="text-purple-500" />
          </div>
          <span class="text-sm text-gray-600">AI 生成数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ aiCount }} <span class="text-sm font-normal text-gray-500">道</span></p>
      </div>

      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-green-50">
            <Icon name="edit-2" :size="20" class="text-green-500" />
          </div>
          <span class="text-sm text-gray-600">手动新增数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ manualCount }} <span class="text-sm font-normal text-gray-500">道</span></p>
      </div>

      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-orange-50">
            <Icon name="check-circle" :size="20" class="text-orange-500" />
          </div>
          <span class="text-sm text-gray-600">已发布数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ publishedCount }} <span class="text-sm font-normal text-gray-500">道</span></p>
      </div>
    </div>

    <!-- 搜索 + 筛选 + 视图切换 -->
    <div class="bg-white border border-[#E2E6EC] rounded-xl px-6 py-4 flex flex-wrap gap-4 items-center">
      <div class="flex-1 min-w-[220px] max-w-md">
        <Input v-model="searchKeyword" placeholder="搜索题目标题 / 内容..." prefix-icon-name="search" @keyup.enter="onSearch" />
      </div>
      <select
        v-model="filterType"
        class="h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        @change="onFilterChange"
      >
        <option value="">全部题型</option>
        <option v-for="t in QUESTION_TYPE_KEYS" :key="t" :value="t">{{ QUESTION_TYPE_LABEL[t] }}</option>
      </select>
      <select
        v-model="filterDifficulty"
        class="h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        @change="onFilterChange"
      >
        <option value="">全部难度</option>
        <option value="1">简单</option>
        <option value="2">中等</option>
        <option value="3">困难</option>
      </select>
      <select
        v-model="filterSource"
        class="h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        @change="onFilterChange"
      >
        <option value="">全部来源</option>
        <option value="AI">AI 生成</option>
        <option value="MANUAL">手动新增</option>
      </select>
      <div class="flex items-center gap-2">
        <button
          @click="viewMode = 'list'"
          :class="[
            'px-3 h-9 rounded-md text-sm border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 flex items-center gap-1.5',
            viewMode === 'list'
              ? 'border-primary-500 bg-primary-50 text-primary-600 font-medium'
              : 'border-[#E2E6EC] text-gray-500 hover:border-gray-300 hover:text-gray-700',
          ]"
        >
          <Icon name="list" :size="16" aria-hidden="true" /> 列表
        </button>
        <button
          @click="viewMode = 'card'"
          :class="[
            'px-3 h-9 rounded-md text-sm border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 flex items-center gap-1.5',
            viewMode === 'card'
              ? 'border-primary-500 bg-primary-50 text-primary-600 font-medium'
              : 'border-[#E2E6EC] text-gray-500 hover:border-gray-300 hover:text-gray-700',
          ]"
        >
          <Icon name="grid" :size="16" aria-hidden="true" /> 卡片
        </button>
      </div>
    </div>

    <!-- ========= 列表视图 ========= -->
    <div v-if="viewMode === 'list'" class="bg-white border border-[#E2E6EC] rounded-xl overflow-hidden">
      <div v-if="loading" class="px-6 py-12 flex flex-col items-center justify-center gap-3">
        <div class="w-8 h-8 border-2 border-[#E2E6EC] border-t-primary-500 rounded-full animate-spin"></div>
        <p class="text-sm text-gray-400">加载题目列表...</p>
      </div>

      <div v-else-if="questions.length === 0" class="px-6 py-12 flex flex-col items-center justify-center gap-3">
        <div class="w-14 h-14 rounded-full bg-gray-50 flex items-center justify-center">
          <Icon name="file-text" :size="28" class="text-gray-300" />
        </div>
        <p class="text-sm text-gray-400">暂无题目，点击「AI 出题」或「手动新增」开始创建</p>
      </div>

      <template v-else>
        <div class="px-6 py-4 border-b border-[#E2E6EC]">
          <div class="grid grid-cols-12 gap-4 text-xs text-gray-500 font-medium">
            <div class="col-span-3">题目标题</div>
            <div class="col-span-1 text-center">题型</div>
            <div class="col-span-1 text-center">难度</div>
            <div class="col-span-1 text-center">来源</div>
            <div class="col-span-1 text-center">状态</div>
            <div class="col-span-2 text-center">创建时间</div>
            <div class="col-span-3 text-center">操作</div>
          </div>
        </div>

        <div class="divide-y divide-[#E2E6EC]/50">
          <div
            v-for="q in questions"
            :key="q.id"
            class="px-6 py-4 hover:bg-gray-50 transition-colors group"
          >
            <div class="grid grid-cols-12 gap-4 items-center">
              <div class="col-span-3 min-w-0">
                <p class="font-medium text-gray-800 truncate">{{ q.title }}</p>
                <p class="text-xs text-gray-400 truncate mt-0.5">{{ q.tags || '无标签' }}</p>
              </div>
              <div class="col-span-1 text-center">
                <span :class="['inline-block px-2 py-0.5 rounded-full text-xs font-medium', QUESTION_TYPE_STYLE[q.questionType]]">
                  {{ QUESTION_TYPE_LABEL[q.questionType] || q.questionType }}
                </span>
              </div>
              <div class="col-span-1 text-center">
                <span :class="['inline-block px-2 py-0.5 rounded-full text-xs font-medium', DIFFICULTY_STYLE[q.difficulty]]">
                  {{ DIFFICULTY_LABEL[q.difficulty] || '—' }}
                </span>
              </div>
              <div class="col-span-1 text-center">
                <span :class="['inline-block px-2 py-0.5 rounded-full text-xs font-medium', q.source === 'AI' ? 'bg-purple-50 text-purple-600' : 'bg-gray-100 text-gray-500']">
                  {{ q.source === 'AI' ? 'AI 生成' : '手动' }}
                </span>
              </div>
              <div class="col-span-1 text-center">
                <span :class="['inline-block px-2 py-0.5 rounded-full text-xs font-medium', q.status === 1 ? 'bg-green-50 text-green-600' : 'bg-gray-100 text-gray-500']">
                  {{ q.status === 1 ? '已发布' : '草稿' }}
                </span>
              </div>
              <div class="col-span-2 text-center">
                <span class="text-sm text-gray-500">{{ formatDate(q.createTime) }}</span>
              </div>
              <div class="col-span-3 flex items-center justify-center gap-1">
                <button
                  class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                  @click="openEdit(q)"
                  title="编辑"
                >
                  <Icon name="edit-2" :size="16" aria-hidden="true" />
                </button>
                <button
                  v-if="q.status !== 1"
                  class="p-1.5 text-gray-400 hover:text-green-500 hover:bg-green-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                  @click="publishQuestion(q)"
                  title="发布"
                >
                  <Icon name="trending-up" :size="16" aria-hidden="true" />
                </button>
                <button
                  v-else
                  class="p-1.5 text-gray-400 hover:text-orange-500 hover:bg-orange-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                  @click="unpublishQuestion(q)"
                  title="下架"
                >
                  <Icon name="trending-down" :size="16" aria-hidden="true" />
                </button>
                <button
                  class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                  @click="deleteQuestion(q)"
                  title="删除"
                >
                  <Icon name="trash-2" :size="16" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="px-6 py-3 border-t border-[#E2E6EC]/50 flex items-center justify-between">
          <span class="text-sm text-gray-400">共 {{ totalCount }} 道，第 {{ pageNum }} / {{ totalPages }} 页</span>
          <div class="flex items-center gap-2">
            <button
              :disabled="pageNum <= 1 || loading"
              class="px-3 h-8 rounded-md text-sm border border-[#E2E6EC] text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              @click="goPage(pageNum - 1)"
            >上一页</button>
            <button
              :disabled="pageNum >= totalPages || loading"
              class="px-3 h-8 rounded-md text-sm border border-[#E2E6EC] text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              @click="goPage(pageNum + 1)"
            >下一页</button>
          </div>
        </div>
      </template>
    </div>

    <!-- ========= 卡片视图 ========= -->
    <div v-else>
      <div v-if="loading" class="py-12 flex flex-col items-center justify-center gap-3">
        <div class="w-8 h-8 border-2 border-[#E2E6EC] border-t-primary-500 rounded-full animate-spin"></div>
        <p class="text-sm text-gray-400">加载题目列表...</p>
      </div>
      <div v-else-if="questions.length === 0" class="py-12 flex flex-col items-center justify-center gap-3">
        <div class="w-14 h-14 rounded-full bg-gray-50 flex items-center justify-center">
          <Icon name="file-text" :size="28" class="text-gray-300" />
        </div>
        <p class="text-sm text-gray-400">暂无题目，点击「AI 出题」或「手动新增」开始创建</p>
      </div>
      <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
        <div
          v-for="q in questions"
          :key="q.id"
          class="bg-white border border-[#E2E6EC] rounded-2xl p-5 hover:shadow-lg hover:-translate-y-0.5 transition-shadow transition-transform duration-200 group flex flex-col"
        >
          <div class="flex items-center gap-2 flex-wrap mb-3">
            <span :class="['inline-block px-2 py-0.5 rounded-full text-xs font-medium', QUESTION_TYPE_STYLE[q.questionType]]">
              {{ QUESTION_TYPE_LABEL[q.questionType] || q.questionType }}
            </span>
            <span :class="['inline-block px-2 py-0.5 rounded-full text-xs font-medium', DIFFICULTY_STYLE[q.difficulty]]">
              {{ DIFFICULTY_LABEL[q.difficulty] || '—' }}
            </span>
            <span :class="['inline-block px-2 py-0.5 rounded-full text-xs font-medium', q.source === 'AI' ? 'bg-purple-50 text-purple-600' : 'bg-gray-100 text-gray-500']">
              {{ q.source === 'AI' ? 'AI 生成' : '手动' }}
            </span>
          </div>
          <h3 class="font-semibold text-gray-800 text-[15px] leading-tight line-clamp-1 group-hover:text-primary-600 transition-colors">
            {{ q.title }}
          </h3>
          <p class="text-xs text-gray-400 line-clamp-2 mt-2 flex-1">{{ q.content || '暂无题干内容' }}</p>
          <div class="mt-4 pt-4 border-t border-[#E2E6EC]/70 flex items-center justify-between">
            <span class="text-xs text-gray-400">{{ formatDate(q.createTime) }}</span>
            <div class="flex items-center gap-1">
              <button
                class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="openEdit(q)"
                title="编辑"
              >
                <Icon name="edit-2" :size="14" />
              </button>
              <button
                v-if="q.status !== 1"
                class="p-1.5 text-gray-400 hover:text-green-500 hover:bg-green-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="publishQuestion(q)"
                title="发布"
              >
                <Icon name="trending-up" :size="14" />
              </button>
              <button
                v-else
                class="p-1.5 text-gray-400 hover:text-orange-500 hover:bg-orange-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="unpublishQuestion(q)"
                title="下架"
              >
                <Icon name="trending-down" :size="14" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="deleteQuestion(q)"
                title="删除"
              >
                <Icon name="trash-2" :size="14" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========= AI 出题弹窗 ========= -->
    <div
      v-if="showAiModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
      role="button"
      tabindex="0"
      @click.self="closeAiModal"
      @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
    >
      <div class="bg-white rounded-xl w-full max-w-md p-6 animate-scale-in max-h-[90vh] overflow-y-auto">
        <div class="flex items-start justify-between mb-4">
          <div>
            <h3 class="text-lg font-semibold text-gray-800">AI 出题</h3>
            <p class="text-sm text-gray-500 mt-0.5">基于知识库或文档智能生成题目</p>
          </div>
          <button @click="closeAiModal" class="text-gray-400 hover:text-gray-600 p-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2">
            <Icon name="x" :size="20" />
          </button>
        </div>

        <div class="space-y-4">
          <!-- 来源选择 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">生成来源</label>
            <div class="flex items-center gap-4">
              <label class="flex items-center gap-2 cursor-pointer">
                <input type="radio" value="category" v-model="aiForm.source" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
                <span class="text-sm text-gray-700">按知识库</span>
              </label>
              <label class="flex items-center gap-2 cursor-pointer">
                <input type="radio" value="doc" v-model="aiForm.source" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
                <span class="text-sm text-gray-700">按文档</span>
              </label>
            </div>
          </div>

          <!-- 知识库下拉 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">知识库</label>
            <select
              v-model="aiForm.categoryId"
              class="w-full h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              @change="onAiCategoryChange"
            >
              <option :value="null">请选择知识库</option>
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
            </select>
          </div>

          <!-- 文档下拉 -->
          <div v-if="aiForm.source === 'doc'">
            <label class="block text-sm font-medium text-gray-700 mb-1.5">文档</label>
            <select
              v-model="aiForm.docId"
              :disabled="!aiForm.categoryId"
              class="w-full h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 disabled:bg-gray-50 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <option :value="null">{{ aiForm.categoryId ? '请选择文档' : '请先选择知识库' }}</option>
              <option v-for="doc in aiDocs" :key="doc.id" :value="doc.id">{{ doc.title }}</option>
            </select>
          </div>

          <!-- 题型 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">题型</label>
            <select
              v-model="aiForm.questionType"
              class="w-full h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <option v-for="t in QUESTION_TYPE_KEYS" :key="t" :value="t">{{ QUESTION_TYPE_LABEL[t] }}</option>
            </select>
          </div>

          <!-- 生成数量 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">生成数量</label>
            <input
              v-model.number="aiForm.count"
              type="number"
              min="3"
              max="20"
              class="w-full h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            />
            <p class="text-xs text-gray-400 mt-1">范围 3-20，默认 5</p>
          </div>
        </div>

        <div class="flex justify-end gap-3 mt-6">
          <Button variant="secondary" @click="closeAiModal">取消</Button>
          <Button :loading="generating" @click="generateByAi">
            <Icon v-if="!generating" name="sparkles" :size="16" class="mr-2" aria-hidden="true" />
            {{ generating ? '生成中...' : '开始生成' }}
          </Button>
        </div>
      </div>
    </div>

    <!-- ========= 手动新增 / 编辑弹窗 ========= -->
    <div
      v-if="showEditModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
      role="button"
      tabindex="0"
      @click.self="closeEditModal"
      @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
    >
      <div class="bg-white rounded-xl w-full max-w-2xl p-6 animate-scale-in max-h-[90vh] overflow-y-auto">
        <div class="flex items-start justify-between mb-4">
          <h3 class="text-lg font-semibold text-gray-800">
            {{ editingId ? '编辑题目' : '手动新增题目' }}
          </h3>
          <button @click="closeEditModal" class="text-gray-400 hover:text-gray-600 p-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2">
            <Icon name="x" :size="20" />
          </button>
        </div>

        <div class="space-y-4">
          <!-- 题目标题 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">题目标题 <span class="text-danger-500">*</span></label>
            <Input v-model="form.title" placeholder="请输入题目标题" />
          </div>

          <!-- 题型 + 难度 -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">题型</label>
              <select
                v-model="form.questionType"
                class="w-full h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @change="onFormTypeChange"
              >
                <option v-for="t in QUESTION_TYPE_KEYS" :key="t" :value="t">{{ QUESTION_TYPE_LABEL[t] }}</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">难度</label>
              <select
                v-model.number="form.difficulty"
                class="w-full h-10 px-3 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              >
                <option :value="1">简单</option>
                <option :value="2">中等</option>
                <option :value="3">困难</option>
              </select>
            </div>
          </div>

          <!-- 题干内容 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">题干内容 <span class="text-danger-500">*</span></label>
            <textarea
              v-model="form.content"
              rows="3"
              placeholder="请输入题干内容..."
              class="w-full px-3 py-2 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 resize-y transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            ></textarea>
          </div>

          <!-- 选项编辑（仅选择题显示） -->
          <div v-if="isChoiceType">
            <div class="flex items-center justify-between mb-1.5">
              <label class="block text-sm font-medium text-gray-700">选项</label>
              <button
                type="button"
                class="text-xs flex items-center gap-1 px-2 py-1 rounded-md bg-primary-50 text-primary-600 hover:bg-primary-100 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="addOption"
                :disabled="form.options.length >= 6"
              >
                <Icon name="plus" :size="12" aria-hidden="true" /> 添加选项
              </button>
            </div>
            <p class="text-xs text-gray-400 mb-2">最多 6 个选项，最少 2 个</p>
            <div class="space-y-2">
              <div
                v-for="(opt, idx) in form.options"
                :key="idx"
                class="flex items-center gap-2"
              >
                <span :class="['w-6 h-6 rounded-full text-xs font-medium flex items-center justify-center flex-shrink-0', opt.trim() ? 'bg-primary-50 text-primary-600' : 'bg-gray-100 text-gray-500']">
                  {{ String.fromCharCode(65 + idx) }}
                </span>
                <Input v-model="form.options[idx]" :placeholder="`选项 ${String.fromCharCode(65 + idx)}`" />
                <button
                  type="button"
                  class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 flex-shrink-0"
                  :disabled="form.options.length <= 2"
                  @click="removeOption(idx)"
                  title="删除选项"
                >
                  <Icon name="x" :size="16" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>

          <!-- 正确答案：根据题型动态显示 -->
          <!-- 单选 -->
          <div v-if="form.questionType === 'SINGLE_CHOICE'">
            <label class="block text-sm font-medium text-gray-700 mb-1.5">正确答案</label>
            <div class="flex flex-col gap-2">
              <label
                v-for="(opt, idx) in form.options"
                :key="idx"
                class="flex items-center gap-2 cursor-pointer px-3 py-2 rounded-lg border border-[#E2E6EC] hover:border-primary-500 transition-colors"
                :class="{ 'border-primary-500 bg-primary-50': form.singleAnswer === String(idx) }"
              >
                <input type="radio" :value="String(idx)" v-model="form.singleAnswer" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
                <span class="text-sm text-gray-700 truncate">{{ opt || `选项 ${String.fromCharCode(65 + idx)}` }}</span>
              </label>
            </div>
          </div>

          <!-- 多选 -->
          <div v-else-if="form.questionType === 'MULTIPLE_CHOICE'">
            <label class="block text-sm font-medium text-gray-700 mb-1.5">正确答案（可多选）</label>
            <div class="flex flex-col gap-2">
              <label
                v-for="(opt, idx) in form.options"
                :key="idx"
                class="flex items-center gap-2 cursor-pointer px-3 py-2 rounded-lg border border-[#E2E6EC] hover:border-primary-500 transition-colors"
                :class="{ 'border-primary-500 bg-primary-50': form.multipleAnswer.includes(idx) }"
              >
                <input type="checkbox" :value="idx" v-model="form.multipleAnswer" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
                <span class="text-sm text-gray-700 truncate">{{ opt || `选项 ${String.fromCharCode(65 + idx)}` }}</span>
              </label>
            </div>
          </div>

          <!-- 填空 -->
          <div v-else-if="form.questionType === 'FILL_BLANK'">
            <label class="block text-sm font-medium text-gray-700 mb-1.5">正确答案</label>
            <Input v-model="form.fillAnswer" placeholder="请输入填空答案" />
          </div>

          <!-- 判断 -->
          <div v-else-if="form.questionType === 'TRUE_FALSE'">
            <label class="block text-sm font-medium text-gray-700 mb-1.5">正确答案</label>
            <div class="flex items-center gap-4">
              <label class="flex items-center gap-2 cursor-pointer px-4 py-2 rounded-lg border border-[#E2E6EC] hover:border-primary-500 transition-colors" :class="{ 'border-primary-500 bg-primary-50': form.trueFalseAnswer === 'true' }">
                <input type="radio" value="true" v-model="form.trueFalseAnswer" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
                <span class="text-sm text-gray-700">正确</span>
              </label>
              <label class="flex items-center gap-2 cursor-pointer px-4 py-2 rounded-lg border border-[#E2E6EC] hover:border-primary-500 transition-colors" :class="{ 'border-primary-500 bg-primary-50': form.trueFalseAnswer === 'false' }">
                <input type="radio" value="false" v-model="form.trueFalseAnswer" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
                <span class="text-sm text-gray-700">错误</span>
              </label>
            </div>
          </div>

          <!-- 简答 -->
          <div v-else-if="form.questionType === 'SHORT_ANSWER'">
            <label class="block text-sm font-medium text-gray-700 mb-1.5">参考答案</label>
            <textarea
              v-model="form.shortAnswer"
              rows="3"
              placeholder="请输入参考答案..."
              class="w-full px-3 py-2 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 resize-y transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            ></textarea>
          </div>

          <!-- 答案解析 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">答案解析</label>
            <textarea
              v-model="form.explanation"
              rows="2"
              placeholder="请输入答案解析（选填）..."
              class="w-full px-3 py-2 rounded-lg border border-[#E2E6EC] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 resize-y transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            ></textarea>
          </div>

          <!-- 标签 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">标签（逗号分隔）</label>
            <Input v-model="form.tags" placeholder="例如：基础,概念,入门" />
          </div>
        </div>

        <div class="flex justify-end gap-3 mt-6">
          <Button variant="secondary" @click="closeEditModal">取消</Button>
          <Button :loading="saving" @click="saveQuestion">{{ saving ? '保存中...' : '保存' }}</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-智能题库管理：题目 CRUD、发布/下架、AI 智能出题、手动新增，支持列表/卡片视图切换。
import { ref, reactive, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import { adminApi } from '@/api'
import type { QuizQuestionVO, QuizQuestionInput } from '@/api/admin'
import type { CategoryVO, DocVO } from '@/api/types'
import { notify, getApiError, confirmDialog } from '@/utils/toast'

// ========== 题型 / 难度常量映射 ==========
const QUESTION_TYPE_LABEL: Record<string, string> = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  FILL_BLANK: '填空题',
  TRUE_FALSE: '判断题',
  SHORT_ANSWER: '简答题',
}

const QUESTION_TYPE_STYLE: Record<string, string> = {
  SINGLE_CHOICE: 'bg-blue-50 text-blue-600',
  MULTIPLE_CHOICE: 'bg-purple-50 text-purple-600',
  FILL_BLANK: 'bg-cyan-50 text-cyan-600',
  TRUE_FALSE: 'bg-green-50 text-green-600',
  SHORT_ANSWER: 'bg-orange-50 text-orange-600',
}

const QUESTION_TYPE_KEYS = Object.keys(QUESTION_TYPE_LABEL)

const DIFFICULTY_LABEL: Record<number, string> = {
  1: '简单',
  2: '中等',
  3: '困难',
}

const DIFFICULTY_STYLE: Record<number, string> = {
  1: 'bg-green-50 text-green-600',
  2: 'bg-orange-50 text-orange-600',
  3: 'bg-red-50 text-red-600',
}

const CHOICE_TYPES = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE']

// ========== 列表状态 ==========
const questions = ref<QuizQuestionVO[]>([])
const loading = ref(false)
const saving = ref(false)
const generating = ref(false)
const totalCount = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const viewMode = ref<'list' | 'card'>('list')
const searchKeyword = ref('')
const filterType = ref('')
const filterDifficulty = ref('')
const filterSource = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize.value)))

// ========== 统计 ==========
const aiCount = computed(() => questions.value.filter((q) => q.source === 'AI').length)
const manualCount = computed(() => questions.value.filter((q) => q.source === 'MANUAL').length)
const publishedCount = computed(() => questions.value.filter((q) => q.status === 1).length)

// ========== 表单状态 ==========
const showEditModal = ref(false)
const editingId = ref<number | null>(null)
const isChoiceType = computed(() => CHOICE_TYPES.includes(form.questionType))

interface QuizForm {
  title: string
  questionType: string
  content: string
  options: string[]
  singleAnswer: string
  multipleAnswer: number[]
  fillAnswer: string
  trueFalseAnswer: string
  shortAnswer: string
  explanation: string
  difficulty: number
  tags: string
}

const defaultForm = (): QuizForm => ({
  title: '',
  questionType: 'SINGLE_CHOICE',
  content: '',
  options: ['', '', '', ''],
  singleAnswer: '',
  multipleAnswer: [],
  fillAnswer: '',
  trueFalseAnswer: 'true',
  shortAnswer: '',
  explanation: '',
  difficulty: 1,
  tags: '',
})

const form = reactive<QuizForm>(defaultForm())

// ========== AI 出题弹窗状态 ==========
const showAiModal = ref(false)
const categories = ref<CategoryVO[]>([])
const aiDocs = ref<DocVO[]>([])
const aiForm = reactive({
  source: 'category' as 'category' | 'doc',
  categoryId: null as number | null,
  docId: null as number | null,
  questionType: 'SINGLE_CHOICE',
  count: 5,
})

// ========== 工具函数 ==========
const formatDate = (dateStr?: string): string => {
  if (!dateStr) return '—'
  const date = new Date(dateStr)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// ========== 数据加载 ==========
const loadQuestions = async () => {
  loading.value = true
  try {
    const params: {
      page: number
      pageSize: number
      keyword?: string
      questionType?: string
      difficulty?: number
      source?: string
    } = {
      page: pageNum.value,
      pageSize: pageSize.value,
    }
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    if (filterType.value) params.questionType = filterType.value
    if (filterDifficulty.value) params.difficulty = Number(filterDifficulty.value)
    if (filterSource.value) params.source = filterSource.value
    const res = await adminApi.quizQuestions(params)
    questions.value = res.records ?? []
    totalCount.value = res.total ?? 0
  } catch (e: unknown) {
    notify('加载题目失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  pageNum.value = 1
  loadQuestions()
}

const onFilterChange = () => {
  pageNum.value = 1
  loadQuestions()
}

const goPage = (p: number) => {
  if (p < 1 || p > totalPages.value || loading.value) return
  pageNum.value = p
  loadQuestions()
}

// ========== 表单操作 ==========
const resetForm = () => {
  Object.assign(form, defaultForm())
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  showEditModal.value = true
}

const openEdit = (q: QuizQuestionVO) => {
  editingId.value = q.id
  form.title = q.title || ''
  form.questionType = q.questionType || 'SINGLE_CHOICE'
  form.content = q.content || ''
  form.explanation = q.explanation || ''
  form.difficulty = q.difficulty ?? 1
  form.tags = q.tags || ''
  // 解析选项（字符串 → 数组）
  try {
    const parsed = q.options ? JSON.parse(q.options) : []
    form.options = Array.isArray(parsed) && parsed.length
      ? parsed.map((o: unknown) => String(o))
      : ['', '', '', '']
  } catch {
    form.options = ['', '', '', '']
  }
  // 解析答案
  form.singleAnswer = ''
  form.multipleAnswer = []
  form.fillAnswer = ''
  form.trueFalseAnswer = 'true'
  form.shortAnswer = ''
  const answer = q.answer || ''
  switch (q.questionType) {
    case 'SINGLE_CHOICE':
      form.singleAnswer = answer
      break
    case 'MULTIPLE_CHOICE':
      form.multipleAnswer = answer
        ? answer.split(',').map((s) => Number(s)).filter((n) => !Number.isNaN(n))
        : []
      break
    case 'FILL_BLANK':
      form.fillAnswer = answer
      break
    case 'TRUE_FALSE':
      form.trueFalseAnswer = answer === 'false' ? 'false' : 'true'
      break
    case 'SHORT_ANSWER':
      form.shortAnswer = answer
      break
  }
  showEditModal.value = true
}

const closeEditModal = () => {
  showEditModal.value = false
  editingId.value = null
}

const onFormTypeChange = () => {
  // 切换题型时，重置对应答案字段并保证选项就绪
  if (isChoiceType.value && form.options.length === 0) {
    form.options = ['', '', '', '']
  }
  form.singleAnswer = ''
  form.multipleAnswer = []
  form.fillAnswer = ''
  form.trueFalseAnswer = 'true'
  form.shortAnswer = ''
}

const addOption = () => {
  if (form.options.length >= 6) return
  form.options.push('')
}

const removeOption = (idx: number) => {
  if (form.options.length <= 2) return
  form.options.splice(idx, 1)
  // 同步修正答案索引
  if (form.questionType === 'SINGLE_CHOICE') {
    const cur = Number(form.singleAnswer)
    if (cur === idx) form.singleAnswer = ''
    else if (cur > idx) form.singleAnswer = String(cur - 1)
  } else if (form.questionType === 'MULTIPLE_CHOICE') {
    form.multipleAnswer = form.multipleAnswer
      .filter((i) => i !== idx)
      .map((i) => (i > idx ? i - 1 : i))
  }
}

// 构建选项存储字符串
const buildOptions = (): string | null => {
  if (!isChoiceType.value) return null
  const filtered = form.options.map((o) => o.trim()).filter(Boolean)
  return filtered.length ? JSON.stringify(filtered) : null
}

// 构建答案字符串
const buildAnswer = (): string => {
  switch (form.questionType) {
    case 'SINGLE_CHOICE':
      return form.singleAnswer
    case 'MULTIPLE_CHOICE':
      return [...form.multipleAnswer].sort((a, b) => a - b).join(',')
    case 'FILL_BLANK':
      return form.fillAnswer
    case 'TRUE_FALSE':
      return form.trueFalseAnswer
    case 'SHORT_ANSWER':
      return form.shortAnswer
    default:
      return ''
  }
}

const saveQuestion = async () => {
  if (!form.title.trim()) {
    notify('请填写题目标题', 'warning')
    return
  }
  if (!form.content.trim()) {
    notify('请填写题干内容', 'warning')
    return
  }
  if (isChoiceType.value) {
    const validOptions = form.options.map((o) => o.trim()).filter(Boolean)
    if (validOptions.length < 2) {
      notify('选择题至少需要 2 个有效选项', 'warning')
      return
    }
  }
  const answer = buildAnswer()
  if (!answer) {
    notify('请设置正确答案', 'warning')
    return
  }

  saving.value = true
  try {
    const payload: QuizQuestionInput = {
      title: form.title.trim(),
      content: form.content.trim(),
      questionType: form.questionType,
      options: buildOptions(),
      answer,
      explanation: form.explanation.trim(),
      difficulty: form.difficulty,
      tags: form.tags.trim(),
      source: 'MANUAL',
      status: editingId.value ? undefined : 0,
    }
    if (editingId.value) {
      await adminApi.updateQuizQuestion(editingId.value, payload)
      notify('题目已更新', 'success')
    } else {
      await adminApi.createQuizQuestion(payload)
      notify('题目已创建', 'success')
    }
    closeEditModal()
    await loadQuestions()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const deleteQuestion = async (q: QuizQuestionVO) => {
  if (!(await confirmDialog(`确定删除题目「${q.title}」吗？此操作不可恢复。`))) return
  try {
    await adminApi.removeQuizQuestion(q.id)
    notify('题目已删除', 'success')
    await loadQuestions()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const publishQuestion = async (q: QuizQuestionVO) => {
  if (!(await confirmDialog(`确定发布题目「${q.title}」吗？`))) return
  try {
    await adminApi.publishQuizQuestion(q.id)
    notify('题目已发布', 'success')
    await loadQuestions()
  } catch (e: unknown) {
    notify('发布失败：' + getApiError(e), 'error')
  }
}

const unpublishQuestion = async (q: QuizQuestionVO) => {
  if (!(await confirmDialog(`确定下架题目「${q.title}」吗？`))) return
  try {
    await adminApi.unpublishQuizQuestion(q.id)
    notify('题目已下架', 'info')
    await loadQuestions()
  } catch (e: unknown) {
    notify('下架失败：' + getApiError(e), 'error')
  }
}

// ========== AI 出题 ==========
const openAiModal = async () => {
  aiForm.source = 'category'
  aiForm.categoryId = null
  aiForm.docId = null
  aiForm.questionType = 'SINGLE_CHOICE'
  aiForm.count = 5
  aiDocs.value = []
  showAiModal.value = true
  // 加载知识库列表
  if (categories.value.length === 0) {
    try {
      categories.value = await adminApi.learningCategories()
    } catch (e: unknown) {
      // 静默处理，不影响弹窗使用
    }
  }
}

const closeAiModal = () => {
  showAiModal.value = false
}

const onAiCategoryChange = async () => {
  aiForm.docId = null
  aiDocs.value = []
  if (!aiForm.categoryId) return
  try {
    aiDocs.value = await adminApi.learningDocs(aiForm.categoryId)
  } catch (e: unknown) {
    notify('加载文档列表失败：' + getApiError(e), 'error')
  }
}

const generateByAi = async () => {
  const categoryId = aiForm.categoryId
  if (!categoryId) {
    notify('请选择知识库', 'warning')
    return
  }
  // 按文档模式需校验 docId；提取为已收窄的变量供后续 payload 使用
  let validDocId: number | undefined
  if (aiForm.source === 'doc') {
    const docId = aiForm.docId
    if (!docId) {
      notify('请选择文档', 'warning')
      return
    }
    validDocId = docId
  }
  const count = Math.min(20, Math.max(3, aiForm.count || 5))
  generating.value = true
  try {
    notify('AI 正在生成题目，请稍候...', 'info', 5000)
    const payload: { categoryId?: number; docId?: number; questionType: string; count: number } = {
      questionType: aiForm.questionType,
      count,
    }
    if (aiForm.source === 'category') {
      payload.categoryId = categoryId
    } else {
      payload.categoryId = categoryId
      payload.docId = validDocId
    }
    const list = await adminApi.aiGenerateQuizQuestions(payload)
    notify(`AI 生成完成，已创建 ${list?.length ?? count} 道题目`, 'success')
    closeAiModal()
    pageNum.value = 1
    await loadQuestions()
  } catch (e: unknown) {
    notify('AI 生成失败：' + getApiError(e), 'error')
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.5s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
.animate-scale-in { animation: scaleIn 0.2s ease-out; }
@keyframes scaleIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
.line-clamp-1 { display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; }
.line-clamp-2 { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
</style>
