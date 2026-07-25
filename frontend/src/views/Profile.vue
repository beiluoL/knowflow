<template>
  <div class="max-w-3xl mx-auto space-y-4 animate-fade-in">
    <!-- Profile Header Card (设计稿：蓝紫渐变 + 居中头像 + 等级徽章 + 三栏统计) -->
    <section
      class="rounded-lg p-6"
      style="background: linear-gradient(135deg, #3B6FE0 0%, #5B8FE8 100%)"
    >
      <div class="flex flex-col items-center gap-3">
        <div
          class="flex h-16 w-16 items-center justify-center rounded-full text-xl font-bold"
          style="background: rgba(255,255,255,0.25); color: #FFFFFF;"
        >
          {{ (user.nickname || 'U').charAt(0).toUpperCase() }}
        </div>
        <div class="text-[18px] font-semibold text-white">{{ user.nickname || '探索者' }}</div>
        <div
          class="inline-flex items-center gap-1 rounded-md px-2.5 py-1"
          style="background: rgba(255,255,255,0.2)"
        >
          <Icon name="award" :size="14" class="text-white" />
          <span class="text-[12px] font-medium whitespace-nowrap text-white">
            Lv.{{ user.level ?? 1 }} {{ levelTitle }}
          </span>
        </div>
        <div class="mt-1 flex w-full items-center justify-around">
          <div class="flex flex-col items-center gap-0.5">
            <span class="text-[18px] font-bold text-white">{{ user.readDocsCount ?? 0 }}</span>
            <span class="text-[11px]" style="color: rgba(255,255,255,0.75)">阅读</span>
          </div>
          <div class="h-8 w-px" style="background: rgba(255,255,255,0.2)"></div>
          <div class="flex flex-col items-center gap-0.5">
            <span class="text-[18px] font-bold text-white">{{ user.totalStudyHours ?? 0 }}h</span>
            <span class="text-[11px]" style="color: rgba(255,255,255,0.75)">学习时长</span>
          </div>
          <div class="h-8 w-px" style="background: rgba(255,255,255,0.2)"></div>
          <div class="flex flex-col items-center gap-0.5">
            <span class="text-[18px] font-bold text-white">{{ user.streakDays ?? 0 }} 天</span>
            <span class="text-[11px]" style="color: rgba(255,255,255,0.75)">连续打卡</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Level Progress Card (设计稿：独立等级进度卡) -->
    <Card padding="md">
      <div class="flex items-center justify-between">
        <span class="text-[15px] font-semibold text-gray-800">等级进度</span>
        <span class="text-[12px] font-medium text-primary-600">
          {{ user.exp ?? 0 }} / {{ nextLevelExp }} XP
        </span>
      </div>
      <div class="mt-3 h-2 w-full overflow-hidden rounded-full bg-[#E8ECF1]">
        <div
          class="h-full rounded-full transition-all duration-500"
          :style="{ width: `${expPercentage}%`, background: 'var(--kb-primary)' }"
        ></div>
      </div>
      <div class="mt-2 flex items-center justify-between">
        <span class="text-[12px] text-gray-500">Lv.{{ user.level ?? 1 }}</span>
        <span class="text-[12px] text-gray-500">Lv.{{ (user.level ?? 1) + 1 }}</span>
      </div>
    </Card>

    <!-- Achievement Badges (设计稿：横滚徽章) -->
    <Card padding="md">
      <template #header>
        <div class="flex items-center justify-between">
          <h3 class="font-semibold text-gray-800">成就徽章</h3>
          <button class="flex items-center gap-1 text-sm text-primary-500 hover:text-primary-600 transition-colors">
            全部
            <Icon name="chevron-right" :size="14" />
          </button>
        </div>
      </template>
      <div v-if="badges.length === 0" class="badges-empty">
        <Icon name="award" :size="32" class="text-gray-300" />
        <p class="text-sm text-gray-500 mt-2">暂无徽章，继续努力吧！</p>
      </div>
      <div v-else class="flex flex-nowrap gap-4 overflow-x-auto no-scrollbar pb-1">
        <div
          v-for="badge in badges" :key="badge.id"
          class="flex shrink-0 flex-col items-center gap-1.5"
        >
          <div
            class="w-12 h-12 rounded-full flex items-center justify-center"
            :style="badge.unlocked ? { background: badge.gradient } : { background: '#E8ECF1' }"
          >
            <Icon
              :name="badge.icon"
              :size="20"
              :class="badge.unlocked ? 'text-white' : 'text-gray-400'"
            />
          </div>
          <span
            class="text-[11px] text-center whitespace-nowrap"
            :class="badge.unlocked ? 'text-gray-700' : 'text-gray-400'"
          >{{ badge.label }}</span>
        </div>
      </div>
    </Card>

    <!-- Menu List (设计稿：菜单列表) -->
    <Card padding="none">
      <button
        v-for="(item, idx) in menuItems"
        :key="item.key"
        type="button"
        class="flex w-full items-center justify-between px-4 py-3.5 transition-colors hover:bg-gray-50"
        :class="idx < menuItems.length - 1 ? 'border-b border-gray-100' : ''"
        @click="handleMenuClick(item.key)"
      >
        <div class="flex items-center gap-3">
          <div class="flex h-8 w-8 items-center justify-center rounded-md bg-[#E8ECF1]">
            <Icon :name="item.icon" :size="16" class="text-gray-700" />
          </div>
          <span class="text-[14px] font-medium text-gray-800">{{ item.label }}</span>
        </div>
        <div v-if="item.toggle" class="flex items-center">
          <div
            class="h-6 w-11 rounded-full p-0.5 transition-colors"
            :class="darkMode ? 'bg-primary-500' : 'bg-[#E8ECF1]'"
          >
            <div
              class="h-5 w-5 rounded-full bg-white transition-transform"
              :class="darkMode ? 'translate-x-5' : 'translate-x-0'"
            ></div>
          </div>
        </div>
        <Icon v-else name="chevron-right" :size="16" class="text-gray-400" />
      </button>
    </Card>

    <!-- Tab Content Area -->
    <Card padding="none">
      <div class="border-b border-gray-100">
        <div class="flex">
          <button
            v-for="tab in tabs" :key="tab.key"
            :class="[
              'px-6 py-3 text-sm font-medium transition-all relative',
              activeTab === tab.key
                ? 'text-primary-600'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-50'
            ]"
            @click="activeTab = tab.key"
          >
            <Icon :name="tab.iconName" :size="16" class="inline-block mr-2 -mt-0.5" />
            {{ tab.label }}
            <div
              v-if="activeTab === tab.key"
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-primary-500"
            ></div>
          </button>
        </div>
      </div>

      <div class="p-6">
        <div v-if="activeTab === 'favorites'" class="animate-fade-in">
          <div v-if="favoriteDocs.length === 0" class="text-center py-12">
            <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gray-100 flex items-center justify-center">
              <Icon name="heart" :size="32" />
            </div>
            <p class="text-gray-500">暂无收藏文档</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="doc in favoriteDocs" :key="doc.id"
              class="flex items-start gap-4 p-4 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer group"
              @click="goToDoc(doc.id)"
            >
              <div class="w-10 h-10 rounded-lg bg-primary-50 flex items-center justify-center flex-shrink-0">
                <Icon name="file-text" :size="20" />
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="font-medium text-gray-800 truncate group-hover:text-primary-600 transition-colors">{{ doc.title }}</h3>
                <p class="text-sm text-gray-500 mt-1 line-clamp-2">{{ doc.summary }}</p>
                <div class="flex items-center gap-3 mt-2">
                  <Badge variant="primary">{{ doc.categoryName }}</Badge>
                  <span class="text-xs text-gray-400">{{ formatDate(doc.createTime) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'history'" class="animate-fade-in">
          <div v-if="historyDocs.length === 0" class="text-center py-12">
            <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gray-100 flex items-center justify-center">
              <Icon name="clock" :size="32" />
            </div>
            <p class="text-gray-500">暂无浏览记录</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="doc in historyDocs" :key="doc.id"
              class="flex items-start gap-4 p-4 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer group"
              @click="goToDoc(doc.id)"
            >
              <div class="w-10 h-10 rounded-lg bg-success-50 flex items-center justify-center flex-shrink-0">
                <Icon name="file-text" :size="20" />
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="font-medium text-gray-800 truncate group-hover:text-primary-600 transition-colors">{{ doc.title }}</h3>
                <p class="text-sm text-gray-500 mt-1 line-clamp-2">{{ doc.summary }}</p>
                <div class="flex items-center gap-3 mt-2">
                  <Badge variant="default">{{ doc.categoryName }}</Badge>
                  <span class="text-xs text-gray-400">阅读于 {{ formatDate(doc.createTime) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'settings'" class="animate-fade-in">
          <div class="max-w-xl space-y-8">
            <div>
              <h3 class="text-lg font-semibold text-gray-800 mb-4">个人信息</h3>
              <div class="space-y-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">昵称</label>
                  <input
                    v-model="settingsForm.nickname"
                    type="text"
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">用户名</label>
                  <input
                    v-model="settingsForm.username"
                    type="text"
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all bg-gray-50"
                    disabled
                  />
                  <p class="text-xs text-gray-400 mt-1">用户名不可修改</p>
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">邮箱</label>
                  <input
                    v-model="settingsForm.email"
                    type="email"
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">头像地址</label>
                  <input
                    v-model="settingsForm.avatar"
                    type="text"
                    placeholder="https://..."
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                  />
                </div>
              </div>
            </div>

            <div class="flex justify-end gap-3">
              <Button variant="secondary" @click="resetSettings">
                重置
              </Button>
              <Button variant="primary" @click="saveSettings" :loading="saving">
                保存修改
              </Button>
            </div>
          </div>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { notify } from '@/utils/toast'
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { userApi, docsApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { UserVO, DocVO } from '@/api/types'

const router = useRouter()
const auth = useAuthStore()

const user = ref<UserVO>({
  id: 0,
  username: '',
  nickname: '学习者',
  email: '',
  level: 1,
  exp: 0,
  totalStudyHours: 0,
  readDocsCount: 0,
  streakDays: 0,
  favoriteCount: 0,
})

const activeTab = ref('favorites')

const tabs = [
  { key: 'favorites', label: '我的收藏', iconName: 'bookmark' },
  { key: 'history', label: '浏览历史', iconName: 'history' },
  { key: 'settings', label: '账号设置', iconName: 'settings' },
]

const darkMode = ref(false)

const levelTitle = computed(() => {
  const lv = user.value.level ?? 1
  if (lv >= 15) return '知识大师'
  if (lv >= 10) return '代码探索者'
  if (lv >= 5) return '学习者'
  return '新手'
})

interface MenuItem {
  key: string
  label: string
  icon: string
  toggle?: boolean
}

const menuItems: MenuItem[] = [
  { key: 'stats', label: '学习统计', icon: 'bar-chart-2' },
  { key: 'favorites', label: '我的收藏', icon: 'bookmark' },
  { key: 'settings', label: '学习设置', icon: 'settings' },
  { key: 'darkMode', label: '深色模式', icon: 'moon', toggle: true },
  { key: 'about', label: '关于我们', icon: 'info' },
]

const handleMenuClick = (key: string) => {
  if (key === 'darkMode') {
    darkMode.value = !darkMode.value
    notify(darkMode.value ? '已切换到深色模式' : '已切换到浅色模式', 'info')
    return
  }
  if (key === 'stats') {
    router.push('/learning/center')
    return
  }
  if (key === 'favorites') {
    activeTab.value = 'favorites'
    return
  }
  if (key === 'settings') {
    activeTab.value = 'settings'
    return
  }
  if (key === 'about') {
    notify('KnowFlow v1.0 - 一站式知识学习平台', 'info')
  }
}

const nextLevelExp = computed(() => ((user.value.level ?? 1) + 1) * 500)
const expPercentage = computed(() => Math.min(100, Math.round(((user.value.exp ?? 0) / nextLevelExp.value) * 100)))

const favoriteDocs = ref<DocVO[]>([])
const historyDocs = ref<DocVO[]>([])
const saving = ref(false)

interface Badge {
  id: number
  label: string
  icon: string
  gradient: string
  unlocked: boolean
}

const badges = ref<Badge[]>([
  { id: 1, label: '连续7天', icon: 'flame', gradient: 'linear-gradient(135deg, #F59E0B 0%, #FBBF24 100%)', unlocked: true },
  { id: 2, label: '百篇阅读', icon: 'book-open', gradient: 'linear-gradient(135deg, #3B6FE0 0%, #5B8FE8 100%)', unlocked: true },
  { id: 3, label: '代码达人', icon: 'code', gradient: 'linear-gradient(135deg, #10B981 0%, #34D399 100%)', unlocked: true },
  { id: 4, label: '学习先锋', icon: 'award', gradient: 'linear-gradient(135deg, #8B5CF6 0%, #A78BFA 100%)', unlocked: true },
  { id: 5, label: '知识探索', icon: 'compass', gradient: 'linear-gradient(135deg, #06B6D4 0%, #22D3EE 100%)', unlocked: false },
  { id: 6, label: '坚持大师', icon: 'star', gradient: 'linear-gradient(135deg, #EC4899 0%, #F472B6 100%)', unlocked: false },
  { id: 7, label: '闪卡王者', icon: 'layers', gradient: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)', unlocked: false },
])

const settingsForm = reactive({
  nickname: '',
  username: '',
  email: '',
  avatar: '',
})

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
}

const goToDoc = (docId: number) => router.push(`/doc/${docId}`)

const resetSettings = () => {
  settingsForm.nickname = user.value.nickname || ''
  settingsForm.username = user.value.username
  settingsForm.email = user.value.email || ''
  settingsForm.avatar = user.value.avatar || ''
}

const saveSettings = async () => {
  saving.value = true
  try {
    const updated = await userApi.updateProfile({
      nickname: settingsForm.nickname,
      email: settingsForm.email,
      avatar: settingsForm.avatar,
    })
    user.value = { ...user.value, ...updated }
    auth.setSession(auth.token || '', { ...auth.user, ...updated } as UserVO)
    notify('保存成功！', 'success')
  } catch {
    notify('保存失败，请稍后再试', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const profile = await userApi.profile()
    user.value = profile
    auth.setSession(auth.token || '', profile)
  } catch {
    /* 使用默认值 */
  }
  try {
    favoriteDocs.value = await docsApi.favorites()
  } catch {
    favoriteDocs.value = []
  }
  try {
    historyDocs.value = await docsApi.recent()
  } catch {
    historyDocs.value = []
  }
  resetSettings()
})
</script>

<style scoped>
@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in {
  animation: fade-in 0.3s ease-out;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}

.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.badges-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  text-align: center;
}
</style>
