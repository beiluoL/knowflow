<template>
  <div class="space-y-6 animate-fade-in">
    <nav class="flex items-center gap-2 text-sm text-gray-500">
      <span class="text-primary-500 font-medium">知识库</span>
      <Icon name="chevron-right" :size="14" />
      <span class="text-gray-700 font-medium">管理</span>
    </nav>

    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">知识库管理</h1>
        <p class="text-gray-500 text-sm mt-1">创建、编辑和管理你的知识库集合</p>
      </div>
      <div class="flex items-center gap-3">
        <Button variant="secondary" icon-name="upload">导入</Button>
        <Button icon-name="plus" @click="openCreate">新建知识库</Button>
      </div>
    </div>

    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-primary-50">
            <Icon name="database" :size="20" class="text-primary-500" />
          </div>
          <span class="text-sm text-gray-600">知识库总数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ totalKbCount }} <span class="text-sm font-normal text-gray-500">个</span></p>
      </div>

      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-blue-50">
            <Icon name="file-text" :size="20" class="text-blue-500" />
          </div>
          <span class="text-sm text-gray-600">文档总数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ totalDocCount }} <span class="text-sm font-normal text-gray-500">篇</span></p>
      </div>

      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-purple-50">
            <Icon name="hard-drive" :size="20" class="text-purple-500" />
          </div>
          <span class="text-sm text-gray-600">总存储量</span>
        </div>
        <p class="text-2xl font-bold text-gray-800 mb-2">{{ totalStorage }} <span class="text-sm font-normal text-gray-500">/ 10 GB</span></p>
        <div class="h-2 bg-gray-100 rounded-full overflow-hidden">
          <div class="h-full rounded-full bg-primary-500" :style="{ width: storagePercent + '%' }"></div>
        </div>
      </div>

      <div class="bg-white border border-[#E2E6EC] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-green-50">
            <Icon name="users" :size="20" class="text-green-500" />
          </div>
          <span class="text-sm text-gray-600">团队成员</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ memberCount }} <span class="text-sm font-normal text-gray-500">人</span></p>
      </div>
    </div>

    <div class="bg-white border border-[#E2E6EC] rounded-xl overflow-hidden">
      <div class="px-6 py-4 border-b border-[#E2E6EC]">
        <div class="grid grid-cols-12 gap-4 text-xs text-gray-500 font-medium">
          <div class="col-span-4">知识库名称</div>
          <div class="col-span-2 text-center">文档数量</div>
          <div class="col-span-2 text-center">存储占用</div>
          <div class="col-span-2 text-center">创建时间</div>
          <div class="col-span-1 text-center">成员数</div>
          <div class="col-span-1 text-center">操作</div>
        </div>
      </div>

      <div class="divide-y divide-[#E2E6EC]/50">
        <div
          v-for="kb in knowledgeBases" :key="kb.id"
          class="px-6 py-4 hover:bg-gray-50 transition-colors group"
        >
          <div class="grid grid-cols-12 gap-4 items-center">
            <div class="col-span-4 flex items-center gap-3">
              <div
                class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 cursor-pointer"
                :style="{ backgroundColor: getKbColor(kb.icon) + '15' }"
                @click="goToKbDetail(kb)"
              >
                <Icon
                  :name="getCategoryIconName(kb.icon || 'folder')"
                  :size="20"
                  :style="{ color: getKbColor(kb.icon) }"
                />
              </div>
              <div class="min-w-0 cursor-pointer" @click="goToKbDetail(kb)">
                <p class="font-medium text-gray-800 truncate hover:text-primary-500 transition-colors">{{ kb.name }}</p>
                <p class="text-xs text-gray-400 truncate">{{ kb.description || '暂无描述' }}</p>
              </div>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-sm text-gray-700 font-medium">{{ getDocCount(kb) }} 篇</span>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-sm text-gray-700">{{ formatStorage(getDocCount(kb), kb.id) }}</span>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-sm text-gray-500">{{ formatDate(kb.createTime) }}</span>
            </div>
            <div class="col-span-1 text-center">
              <span class="text-sm text-gray-700">{{ getMemberCount(kb.id) }}</span>
            </div>
            <div class="col-span-1 flex items-center justify-center gap-1">
              <button
                class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors"
                @click="editKb(kb)"
                title="编辑"
              >
                <Icon name="edit-2" :size="16" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors"
                @click="deleteKb(kb)"
                title="删除"
              >
                <Icon name="trash-2" :size="16" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="px-6 py-3 text-center border-t border-[#E2E6EC]/50">
        <span class="text-sm text-gray-400">显示全部 {{ knowledgeBases.length }} 个知识库</span>
      </div>
    </div>

    <div>
      <h3 class="text-base font-semibold text-gray-800 mb-4">快捷操作</h3>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="bg-white border border-[#E2E6EC] rounded-xl p-5 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-all">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center bg-primary-50 flex-shrink-0">
              <Icon name="upload" :size="22" class="text-primary-500" />
            </div>
            <div>
              <h4 class="font-medium text-gray-800 mb-1">批量导入文档</h4>
              <p class="text-sm text-gray-500">支持 PDF、Markdown、Word 等格式</p>
            </div>
          </div>
        </div>

        <div class="bg-white border border-[#E2E6EC] rounded-xl p-5 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-all">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center bg-blue-50 flex-shrink-0">
              <Icon name="shield" :size="22" class="text-blue-500" />
            </div>
            <div>
              <h4 class="font-medium text-gray-800 mb-1">设置权限管理</h4>
              <p class="text-sm text-gray-500">管理成员角色与访问权限</p>
            </div>
          </div>
        </div>

        <div class="bg-white border border-[#E2E6EC] rounded-xl p-5 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-all">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center bg-purple-50 flex-shrink-0">
              <Icon name="download" :size="22" class="text-purple-500" />
            </div>
            <div>
              <h4 class="font-medium text-gray-800 mb-1">导出知识库</h4>
              <p class="text-sm text-gray-500">将知识库导出为标准格式</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="showModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40"
      @click.self="closeModal"
    >
      <div class="bg-white rounded-xl w-full max-w-md p-6 animate-scale-in">
        <h3 class="text-lg font-semibold text-gray-800 mb-4">
          {{ editingKb ? '编辑知识库' : '新建知识库' }}
        </h3>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">知识库名称</label>
            <Input v-model="kbForm.name" placeholder="请输入知识库名称" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">描述</label>
            <Input v-model="kbForm.description" placeholder="请输入知识库描述" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">图标</label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="icon in iconOptions" :key="icon"
                @click="kbForm.icon = icon"
                :class="[
                  'w-10 h-10 rounded-lg flex items-center justify-center border transition-all',
                  kbForm.icon === icon
                    ? 'border-primary-500 bg-primary-50 text-primary-500'
                    : 'border-[#E2E6EC] text-gray-500 hover:border-gray-300',
                ]"
              >
                <Icon :name="getCategoryIconName(icon)" :size="18" />
              </button>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-3 mt-6">
          <Button variant="secondary" @click="closeModal">取消</Button>
          <Button :disabled="saving" @click="saveKb">{{ saving ? '保存中...' : '确定' }}</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-知识库管理：维护知识库（分类）的增删改查与统计概览。
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import { categoriesApi, adminApi } from '@/api'
import type { CategoryVO } from '@/api/types'

const router = useRouter()

/** 点击知识库跳转到文档管理（按知识库筛选） */
const goToKbDetail = (kb: CategoryVO) => {
  router.push(`/admin/docs?categoryId=${kb.id}`)
}

const knowledgeBases = ref<CategoryVO[]>([])
const showModal = ref(false)
const editingKb = ref<CategoryVO | null>(null)
const saving = ref(false)

/** 真实统计数据：从后端 overview 接口获取 */
const realTotalDocs = ref(0)
const realTotalUsers = ref(0)
/** 所有文档的 wordCount 总和（用于真实存储量计算） */
const totalWordCount = ref(0)
/** 按分类汇总的 wordCount（用于列表存储占用显示） */
const categoryWordCounts = ref<Map<number, number>>(new Map())
/** 按分类实时统计的文档数量（避免 doc_count 字段维护不一致） */
const categoryDocCounts = ref<Map<number, number>>(new Map())

const iconOptions = ['code', 'server', 'database', 'brain', 'layout', 'settings', 'book-open', 'folder', 'layers', 'message-circle']

const iconColors: Record<string, string> = {
  code: '#3B6FE0',
  server: '#10B981',
  database: '#F59E0B',
  brain: '#8B5CF6',
  layout: '#EC4899',
  settings: '#06B6D4',
  'book-open': '#3B6FE0',
  folder: '#6B7280',
  layers: '#F97316',
  'message-circle': '#84CC16',
}

const kbForm = reactive({
  name: '',
  description: '',
  icon: 'book-open',
})

const getCategoryIconName = (iconName: string): string => {
  return iconOptions.includes(iconName) ? iconName : 'folder'
}

const getKbColor = (iconName?: string): string => {
  return iconColors[iconName || ''] || '#6B7280'
}

const totalKbCount = computed(() => knowledgeBases.value.length)

/** 文档总数：使用后端 overview 真实数据 */
const totalDocCount = computed(() => realTotalDocs.value)

/** 总存储量：基于所有文档的真实 wordCount 计算（1 字符 ≈ 2 字节） */
const totalStorage = computed(() => {
  const bytes = totalWordCount.value * 2
  if (bytes < 1024) return bytes + ' B'
  const kb = bytes / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB'
  const mb = kb / 1024
  if (mb < 1024) return mb.toFixed(1) + ' MB'
  return (mb / 1024).toFixed(1) + ' GB'
})

const storagePercent = computed(() => {
  const bytes = totalWordCount.value * 2
  const mb = bytes / (1024 * 1024)
  return Math.min(100, Math.round((mb / (10 * 1024)) * 100))
})

/** 团队成员数：使用后端真实用户数 */
const memberCount = computed(() => realTotalUsers.value)

/** 知识库级别的成员数：后端暂不支持，显示全平台成员数 */
const getMemberCount = (_id: number): string => {
  return String(realTotalUsers.value)
}

/** 获取分类的真实文档数量（优先使用实时统计，回退到 docCount 字段） */
const getDocCount = (kb: CategoryVO): number => {
  const realCount = categoryDocCounts.value.get(kb.id) ?? 0
  return realCount > 0 ? realCount : (kb.docCount ?? 0)
}

/** 按分类的真实 wordCount 计算存储占用 */
const formatStorage = (docCount: number, categoryId?: number): string => {
  // 优先使用该分类的真实 wordCount 总和
  const wordCount = categoryId ? (categoryWordCounts.value.get(categoryId) ?? 0) : 0
  const bytes = wordCount > 0 ? wordCount * 2 : docCount * 2000
  if (bytes < 1024) return bytes + ' B'
  const kb = bytes / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB'
  const mb = kb / 1024
  if (mb < 1024) return mb.toFixed(1) + ' MB'
  return (mb / 1024).toFixed(1) + ' GB'
}

const formatDate = (dateStr?: string): string => {
  if (!dateStr) return '—'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).replace(/\//g, '-')
}

const openCreate = () => {
  editingKb.value = null
  kbForm.name = ''
  kbForm.description = ''
  kbForm.icon = 'book-open'
  showModal.value = true
}

const editKb = (kb: CategoryVO) => {
  editingKb.value = kb
  kbForm.name = kb.name
  kbForm.description = kb.description || ''
  kbForm.icon = kb.icon || 'book-open'
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  editingKb.value = null
}

const saveKb = async () => {
  if (!kbForm.name.trim()) {
    notify('请填写知识库名称', 'warning')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: kbForm.name,
      description: kbForm.description,
      icon: kbForm.icon,
      sortOrder: 0,
    }
    if (editingKb.value?.id) {
      await adminApi.updateCategory(editingKb.value.id, payload)
      notify('知识库已更新', 'success')
    } else {
      await adminApi.createCategory(payload)
      notify('知识库已创建', 'success')
    }
    await loadKbs()
    closeModal()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const deleteKb = async (kb: CategoryVO) => {
  if (!(await confirmDialog(`确定删除知识库「${kb.name}」吗？`))) return
  try {
    await adminApi.removeCategory(kb.id)
    notify('删除成功', 'success')
    await loadKbs()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const loadKbs = async () => {
  try {
    knowledgeBases.value = await categoriesApi.tree()
  } catch (e: unknown) {
    notify('加载失败：' + getApiError(e), 'error')
  }
}

/** 加载真实统计数据：overview + 用户数 + 所有文档（用于 wordCount 计算） */
const loadStats = async () => {
  try {
    const [overview, users, docPage] = await Promise.all([
      adminApi.overview(),
      adminApi.users({ pageSize: 1 }),
      adminApi.docs({ pageSize: 1000 }),
    ])
    realTotalDocs.value = overview.totalDocs ?? 0
    realTotalUsers.value = users.total ?? 0

    // 计算所有文档的 wordCount 总和，并按分类汇总
    const records = (docPage.records ?? []) as Array<{ wordCount?: number; categoryId?: number }>
    let totalWc = 0
    const catWcMap = new Map<number, number>()
    const catDocMap = new Map<number, number>()
    records.forEach((d) => {
      const wc = d.wordCount ?? 0
      totalWc += wc
      if (d.categoryId) {
        catWcMap.set(d.categoryId, (catWcMap.get(d.categoryId) ?? 0) + wc)
        catDocMap.set(d.categoryId, (catDocMap.get(d.categoryId) ?? 0) + 1)
      }
    })
    totalWordCount.value = totalWc
    categoryWordCounts.value = catWcMap
    categoryDocCounts.value = catDocMap
  } catch (e: unknown) {
    // 统计数据加载失败不影响主流程
    console.error('加载统计数据失败:', e)
  }
}

onMounted(() => {
  loadKbs()
  loadStats()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-scale-in {
  animation: scaleIn 0.2s ease-out;
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
