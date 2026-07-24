<template>
  <div class="max-w-5xl mx-auto space-y-6">
    <Card class="overflow-hidden">
      <div class="bg-gradient-to-r from-primary-500 to-primary-600 h-32 -m-6 mb-0"></div>
      <div class="px-6 pb-6 -mt-10">
        <div class="flex flex-col md:flex-row md:items-end gap-4">
          <Avatar
            :name="user.nickname"
            size="xl"
            class="ring-4 ring-white shadow-lg"
          />
          <div class="flex-1">
            <div class="flex items-center gap-2 flex-wrap">
              <h1 class="text-2xl font-bold text-gray-800">{{ user.nickname }}</h1>
              <Badge variant="primary" class="text-xs">Lv.{{ user.level }}</Badge>
            </div>
            <p class="text-gray-500 mt-1">@{{ user.username }}</p>
            <p class="text-sm text-gray-400 mt-1">{{ user.role === 'admin' ? '管理员' : '注册用户' }}</p>
          </div>
          <div class="flex gap-2">
            <Button variant="secondary" icon-name="settings" @click="activeTab = 'settings'">
              编辑资料
            </Button>
          </div>
        </div>

        <div class="mt-6">
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm text-gray-600">经验值</span>
            <span class="text-sm font-medium text-gray-700">{{ user.exp }} / {{ nextLevelExp }}</span>
          </div>
          <Progress :percentage="expPercentage" variant="primary" />
          <p class="text-xs text-gray-400 mt-1">距离下一级还需 {{ nextLevelExp - (user.exp ?? 0) }} 经验</p>
        </div>
      </div>
    </Card>

    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <Card hoverable class="text-center">
        <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-primary-50 flex items-center justify-center">
          <Icon name="clock" :size="24" />
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ user.readDocsCount }}</p>
        <p class="text-sm text-gray-500 mt-1">已读文档</p>
      </Card>

      <Card hoverable class="text-center">
        <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-success-50 flex items-center justify-center">
          <Icon name="heart" :size="24" />
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ favoriteDocs.length }}</p>
        <p class="text-sm text-gray-500 mt-1">收藏数</p>
      </Card>

      <Card hoverable class="text-center">
        <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-warning-50 flex items-center justify-center">
          <Icon name="book-open" :size="24" />
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ user.totalStudyHours }}h</p>
        <p class="text-sm text-gray-500 mt-1">学习时长</p>
      </Card>

      <Card hoverable class="text-center">
        <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-danger-50 flex items-center justify-center">
          <Icon name="flame" :size="24" />
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ user.streakDays }}</p>
        <p class="text-sm text-gray-500 mt-1">连续天数</p>
      </Card>
    </div>

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
import Avatar from '@/components/ui/Avatar.vue'
import Progress from '@/components/ui/Progress.vue'
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

const nextLevelExp = computed(() => ((user.value.level ?? 1) + 1) * 500)
const expPercentage = computed(() => Math.min(100, Math.round(((user.value.exp ?? 0) / nextLevelExp.value) * 100)))

const favoriteDocs = ref<DocVO[]>([])
const historyDocs = ref<DocVO[]>([])
const saving = ref(false)

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
</style>
