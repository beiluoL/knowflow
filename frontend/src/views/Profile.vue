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
              <Badge variant="primary" class="text-xs">Lv.{{ user.stats.level }}</Badge>
            </div>
            <p class="text-gray-500 mt-1">@{{ user.username }}</p>
            <p class="text-sm text-gray-400 mt-1">{{ user.bio }}</p>
          </div>
          <div class="flex gap-2">
            <Button variant="secondary" icon-name="settings">
              编辑资料
            </Button>
          </div>
        </div>
        
        <div class="mt-6">
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm text-gray-600">经验值</span>
            <span class="text-sm font-medium text-gray-700">{{ user.stats.experience }} / {{ nextLevelExp }}</span>
          </div>
          <Progress :percentage="expPercentage" variant="primary" />
          <p class="text-xs text-gray-400 mt-1">距离下一级还需 {{ nextLevelExp - user.stats.experience }} 经验</p>
        </div>
      </div>
    </Card>

    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <Card hoverable class="text-center">
        <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-primary-50 flex items-center justify-center">
          <Icon name="clock" :size="24" />
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ user.stats.totalDocs }}</p>
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
          <Icon name="calendar" :size="24" />
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ user.stats.studyDays }}</p>
        <p class="text-sm text-gray-500 mt-1">学习天数</p>
      </Card>
      
      <Card hoverable class="text-center">
        <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-danger-50 flex items-center justify-center">
          <Icon name="flame" :size="24" />
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ user.stats.streakDays }}</p>
        <p class="text-sm text-gray-500 mt-1">连续天数</p>
      </Card>
    </div>

    <Card padding="none">
      <div class="border-b border-gray-100">
        <div class="flex">
          <button
            v-for="tab in tabs"
            :key="tab.key"
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
              v-for="doc in favoriteDocs"
              :key="doc.id"
              class="flex items-start gap-4 p-4 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer group"
            >
              <div class="w-10 h-10 rounded-lg bg-primary-50 flex items-center justify-center flex-shrink-0">
                <Icon name="file-text" :size="20" />
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="font-medium text-gray-800 truncate group-hover:text-primary-600 transition-colors">{{ doc.title }}</h3>
                <p class="text-sm text-gray-500 mt-1 line-clamp-2">{{ doc.summary }}</p>
                <div class="flex items-center gap-3 mt-2">
                  <Badge variant="primary">{{ doc.categoryName }}</Badge>
                  <span class="text-xs text-gray-400">{{ formatDate(doc.updatedAt) }}</span>
                </div>
              </div>
              <button class="opacity-0 group-hover:opacity-100 p-2 rounded-lg hover:bg-gray-100 transition-all">
                <Icon name="x" :size="16" />
              </button>
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
              v-for="doc in historyDocs"
              :key="doc.id"
              class="flex items-start gap-4 p-4 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer group"
            >
              <div class="w-10 h-10 rounded-lg bg-success-50 flex items-center justify-center flex-shrink-0">
                <Icon name="file-text" :size="20" />
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="font-medium text-gray-800 truncate group-hover:text-primary-600 transition-colors">{{ doc.title }}</h3>
                <p class="text-sm text-gray-500 mt-1 line-clamp-2">{{ doc.summary }}</p>
                <div class="flex items-center gap-3 mt-2">
                  <Badge variant="default">{{ doc.categoryName }}</Badge>
                  <span class="text-xs text-gray-400">阅读于 {{ formatDate(doc.updatedAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'uploads'" class="animate-fade-in">
          <div v-if="myUploads.length === 0" class="text-center py-12">
            <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gray-100 flex items-center justify-center">
              <Icon name="upload" :size="32" />
            </div>
            <p class="text-gray-500 mb-4">暂无上传文档</p>
            <Button variant="primary" icon-name="plus">
              上传文档
            </Button>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="doc in myUploads"
              :key="doc.id"
              class="flex items-start gap-4 p-4 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer group"
            >
              <div class="w-10 h-10 rounded-lg bg-warning-50 flex items-center justify-center flex-shrink-0">
                <Icon name="file-text" :size="20" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <h3 class="font-medium text-gray-800 truncate group-hover:text-primary-600 transition-colors">{{ doc.title }}</h3>
                  <Badge :variant="doc.status === 'published' ? 'success' : 'warning'">
                    {{ doc.status === 'published' ? '已发布' : '草稿' }}
                  </Badge>
                </div>
                <p class="text-sm text-gray-500 mt-1 line-clamp-2">{{ doc.summary }}</p>
                <div class="flex items-center gap-4 mt-2">
                  <span class="text-xs text-gray-400 flex items-center gap-1">
                    <Icon name="eye" :size="12" />
                    {{ doc.viewCount }}
                  </span>
                  <span class="text-xs text-gray-400 flex items-center gap-1">
                    <Icon name="heart" :size="12" />
                    {{ doc.likeCount }}
                  </span>
                  <span class="text-xs text-gray-400 flex items-center gap-1">
                    <Icon name="star" :size="12" />
                    {{ doc.collectCount }}
                  </span>
                </div>
              </div>
              <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button class="p-2 rounded-lg hover:bg-gray-100 transition-colors">
                  <Icon name="edit" :size="16" />
                </button>
                <button class="p-2 rounded-lg hover:bg-gray-100 transition-colors">
                  <Icon name="trash-2" :size="16" />
                </button>
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
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">个人简介</label>
                  <textarea
                    v-model="settingsForm.bio"
                    rows="3"
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all resize-none"
                  ></textarea>
                </div>
              </div>
            </div>

            <div>
              <h3 class="text-lg font-semibold text-gray-800 mb-4">修改密码</h3>
              <div class="space-y-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">当前密码</label>
                  <input
                    v-model="settingsForm.currentPassword"
                    type="password"
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">新密码</label>
                  <input
                    v-model="settingsForm.newPassword"
                    type="password"
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">确认新密码</label>
                  <input
                    v-model="settingsForm.confirmPassword"
                    type="password"
                    class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                  />
                </div>
              </div>
            </div>

            <div class="flex justify-end gap-3">
              <Button variant="secondary" @click="resetSettings">
                重置
              </Button>
              <Button variant="primary" @click="saveSettings">
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
import { ref, computed, reactive } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import Avatar from '@/components/ui/Avatar.vue'
import Progress from '@/components/ui/Progress.vue'
import { mockUser } from '@/data/user'
import { docs } from '@/data/docs'
import type { Doc } from '@/types'

const user = ref(mockUser)

const activeTab = ref('favorites')

const tabs = [
  { key: 'favorites', label: '我的收藏', iconName: 'bookmark' },
  { key: 'history', label: '浏览历史', iconName: 'history' },
  { key: 'uploads', label: '我的上传', iconName: 'folder-up' },
  { key: 'settings', label: '账号设置', iconName: 'settings' },
]

const nextLevelExp = computed(() => {
  return (user.value.stats.level + 1) * 500
})

const expPercentage = computed(() => {
  const currentLevelExp = user.value.stats.level * 500
  const currentExp = user.value.stats.experience - currentLevelExp
  const neededExp = nextLevelExp.value - currentLevelExp
  return Math.min(Math.round((currentExp / neededExp) * 100), 100)
})

const favoriteDocs = ref<Doc[]>(docs.slice(0, 4))
const historyDocs = ref<Doc[]>(docs.slice(2, 7))
const myUploads = ref<Doc[]>([
  { ...docs[0], author: mockUser.nickname || '' },
  { ...docs[1], author: mockUser.nickname || '' },
  { ...docs[4], author: mockUser.nickname || '', status: 'draft' },
])

const settingsForm = reactive({
  nickname: mockUser.nickname || '',
  username: mockUser.username,
  email: mockUser.email,
  bio: mockUser.bio || '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
}

const resetSettings = () => {
  settingsForm.nickname = mockUser.nickname || ''
  settingsForm.email = mockUser.email
  settingsForm.bio = mockUser.bio || ''
  settingsForm.currentPassword = ''
  settingsForm.newPassword = ''
  settingsForm.confirmPassword = ''
}

const saveSettings = () => {
  alert('保存成功！')
}
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
