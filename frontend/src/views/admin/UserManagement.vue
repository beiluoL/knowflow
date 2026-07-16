<template>
  <AppShell>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">用户管理</h1>
          <p class="text-gray-500 text-sm mt-1">管理系统中的所有用户账号</p>
        </div>
      </div>

      <Card padding="none">
        <div class="p-4 border-b border-gray-100">
          <div class="flex flex-col sm:flex-row sm:items-center gap-4">
            <div class="flex-1">
              <Input
                v-model="searchQuery"
                placeholder="搜索用户名、邮箱..."
                prefix-icon-name="search"
              />
            </div>
            <div class="flex items-center gap-3 flex-wrap">
              <div class="relative">
                <button
                  @click="showRoleFilter = !showRoleFilter"
                  class="inline-flex items-center gap-2 px-4 py-2 text-sm border border-gray-200 rounded-sm bg-white hover:bg-gray-50 transition-colors"
                >
                  <Icon name="filter" :size="16" />
                  <span>{{ selectedRole || '全部角色' }}</span>
                  <Icon name="chevron-down" :size="16" />
                </button>
                <div
                  v-if="showRoleFilter"
                  class="absolute top-full left-0 mt-1 w-40 bg-white border border-gray-200 rounded-sm shadow-lg z-10 py-1 animate-dropdown"
                >
                  <button
                    @click="selectedRole = ''; showRoleFilter = false"
                    class="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 transition-colors"
                    :class="{ 'bg-primary-50 text-primary-600': !selectedRole }"
                  >
                    全部角色
                  </button>
                  <button
                    v-for="role in roles"
                    :key="role"
                    @click="selectedRole = role; showRoleFilter = false"
                    class="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 transition-colors"
                    :class="{ 'bg-primary-50 text-primary-600': selectedRole === role }"
                  >
                    {{ role }}
                  </button>
                </div>
              </div>
              <Button icon-name="user-plus" @click="showAddModal = true">新增用户</Button>
            </div>
          </div>
        </div>

        <div class="hidden md:block overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">用户</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">邮箱</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">角色</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">等级</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">学习时长</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">注册时间</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">状态</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr
                v-for="user in filteredUsers"
                :key="user.id"
                class="hover:bg-gray-50 transition-colors table-row"
              >
                <td class="px-4 py-3">
                  <div class="flex items-center gap-3">
                    <Avatar :src="user.avatar" :name="user.username" size="md" :show-status="true" :status="user.online ? 'online' : 'offline'" />
                    <div>
                      <p class="text-sm font-medium text-gray-800">{{ user.username }}</p>
                      <p class="text-xs text-gray-400">ID: {{ user.id }}</p>
                    </div>
                  </div>
                </td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ user.email }}</td>
                <td class="px-4 py-3">
                  <Badge :variant="user.role === '管理员' ? 'primary' : 'default'">{{ user.role }}</Badge>
                </td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <div class="w-6 h-6 rounded-full bg-gradient-to-br from-yellow-400 to-orange-500 flex items-center justify-center">
                      <Icon name="star" :size="12" />
                    </div>
                    <span class="text-sm font-medium text-gray-700">Lv.{{ user.level }}</span>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <div class="w-32">
                    <Progress :percentage="user.studyProgress" :variant="user.level > 5 ? 'success' : 'primary'" />
                    <p class="text-xs text-gray-400 mt-1">{{ user.studyHours }}h</p>
                  </div>
                </td>
                <td class="px-4 py-3 text-sm text-gray-500">{{ user.registerTime }}</td>
                <td class="px-4 py-3">
                  <Badge :variant="user.status === '正常' ? 'success' : 'danger'">{{ user.status }}</Badge>
                </td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <button class="p-1 text-gray-400 hover:text-primary-500 transition-colors" title="编辑">
                      <Icon name="edit" :size="16" />
                    </button>
                    <button
                      class="p-1 text-gray-400 hover:text-warning-500 transition-colors"
                      :title="user.status === '正常' ? '禁用' : '启用'"
                    >
                      <Icon :name="user.status === '正常' ? 'ban' : 'check-circle'" :size="16" />
                    </button>
                    <button class="p-1 text-gray-400 hover:text-danger-500 transition-colors" title="删除">
                      <Icon name="trash-2" :size="16" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="md:hidden divide-y divide-gray-100">
          <div
            v-for="user in filteredUsers"
            :key="user.id"
            class="p-4 hover:bg-gray-50 transition-colors mobile-card"
          >
            <div class="flex items-start gap-3">
              <Avatar :src="user.avatar" :name="user.username" size="lg" :show-status="true" :status="user.online ? 'online' : 'offline'" />
              <div class="flex-1 min-w-0">
                <div class="flex items-start justify-between gap-2">
                  <div>
                    <h3 class="text-sm font-medium text-gray-800">{{ user.username }}</h3>
                    <p class="text-xs text-gray-400">{{ user.email }}</p>
                  </div>
                  <Badge :variant="user.status === '正常' ? 'success' : 'danger'">{{ user.status }}</Badge>
                </div>
                <div class="flex items-center gap-3 mt-3 flex-wrap">
                  <Badge :variant="user.role === '管理员' ? 'primary' : 'default'">{{ user.role }}</Badge>
                  <div class="flex items-center gap-1">
                    <div class="w-5 h-5 rounded-full bg-gradient-to-br from-yellow-400 to-orange-500 flex items-center justify-center">
                      <Icon name="star" :size="12" />
                    </div>
                    <span class="text-xs text-gray-600">Lv.{{ user.level }}</span>
                  </div>
                </div>
                <div class="mt-3">
                  <div class="flex items-center justify-between mb-1">
                    <span class="text-xs text-gray-500">学习时长</span>
                    <span class="text-xs text-gray-600">{{ user.studyHours }}h</span>
                  </div>
                  <Progress :percentage="user.studyProgress" :variant="user.level > 5 ? 'success' : 'primary'" />
                </div>
                <div class="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
                  <span class="text-xs text-gray-400">注册于 {{ user.registerTime }}</span>
                  <div class="flex items-center gap-2">
                    <button class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors">
                      <Icon name="edit" :size="16" />
                    </button>
                    <button class="p-1.5 text-gray-400 hover:text-warning-500 hover:bg-warning-50 rounded transition-colors">
                      <Icon :name="user.status === '正常' ? 'ban' : 'check-circle'" :size="16" />
                    </button>
                    <button class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors">
                      <Icon name="trash-2" :size="16" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="px-4 py-3 border-t border-gray-100 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p class="text-sm text-gray-500">
            共 <span class="font-medium text-gray-700">{{ totalUsers }}</span> 位用户
          </p>
          <div class="flex items-center gap-1">
            <button
              @click="currentPage = Math.max(1, currentPage - 1)"
              :disabled="currentPage === 1"
              class="p-2 rounded-sm border border-gray-200 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Icon name="chevron-left" :size="16" />
            </button>
            <button
              v-for="page in visiblePages"
              :key="page"
              @click="currentPage = page"
              :class="[
                'w-8 h-8 text-sm rounded-sm transition-colors',
                currentPage === page
                  ? 'bg-primary-500 text-white'
                  : 'border border-gray-200 text-gray-600 hover:bg-gray-50'
              ]"
            >
              {{ page }}
            </button>
            <button
              @click="currentPage = Math.min(totalPages, currentPage + 1)"
              :disabled="currentPage === totalPages"
              class="p-2 rounded-sm border border-gray-200 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Icon name="chevron-right" :size="16" />
            </button>
          </div>
        </div>
      </Card>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import AppShell from '@/components/layout/AppShell.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import Avatar from '@/components/ui/Avatar.vue'
import Progress from '@/components/ui/Progress.vue'

const searchQuery = ref('')
const selectedRole = ref('')
const showRoleFilter = ref(false)
const showAddModal = ref(false)
const currentPage = ref(1)

const roles = ['管理员', '编辑', '普通用户']

interface User {
  id: string
  username: string
  email: string
  avatar?: string
  role: string
  level: number
  studyHours: number
  studyProgress: number
  registerTime: string
  status: string
  online: boolean
}

const allUsers: User[] = [
  { id: 'U001', username: '张三', email: 'zhangsan@example.com', role: '管理员', level: 12, studyHours: 256, studyProgress: 75, registerTime: '2023-06-15', status: '正常', online: true },
  { id: 'U002', username: '李四', email: 'lisi@example.com', role: '编辑', level: 8, studyHours: 168, studyProgress: 45, registerTime: '2023-08-20', status: '正常', online: false },
  { id: 'U003', username: '王五', email: 'wangwu@example.com', role: '普通用户', level: 5, studyHours: 89, studyProgress: 30, registerTime: '2023-10-12', status: '正常', online: true },
  { id: 'U004', username: '赵六', email: 'zhaoliu@example.com', role: '普通用户', level: 3, studyHours: 45, studyProgress: 20, registerTime: '2024-01-05', status: '正常', online: false },
  { id: 'U005', username: '孙七', email: 'sunqi@example.com', role: '编辑', level: 10, studyHours: 198, studyProgress: 60, registerTime: '2023-07-18', status: '已禁用', online: false },
  { id: 'U006', username: '周八', email: 'zhouba@example.com', role: '普通用户', level: 7, studyHours: 134, studyProgress: 55, registerTime: '2023-09-22', status: '正常', online: true },
  { id: 'U007', username: '吴九', email: 'wujiu@example.com', role: '普通用户', level: 2, studyHours: 23, studyProgress: 15, registerTime: '2024-02-10', status: '正常', online: false },
  { id: 'U008', username: '郑十', email: 'zhengshi@example.com', role: '管理员', level: 15, studyHours: 386, studyProgress: 85, registerTime: '2023-03-08', status: '正常', online: true },
]

const filteredUsers = computed(() => {
  let result = [...allUsers]
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      user =>
        user.username.toLowerCase().includes(query) ||
        user.email.toLowerCase().includes(query)
    )
  }
  if (selectedRole.value) {
    result = result.filter(user => user.role === selectedRole.value)
  }
  return result
})

const totalUsers = computed(() => filteredUsers.value.length)
const totalPages = computed(() => Math.ceil(totalUsers.value / 10))

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = currentPage.value
  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)
  if (end - start + 1 < 5) {
    if (start === 1) {
      end = Math.min(5, total)
    } else {
      start = Math.max(1, total - 4)
    }
  }
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  return pages
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

.animate-dropdown {
  animation: dropdown 0.2s ease-out;
}

@keyframes dropdown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.table-row {
  animation: fadeInRow 0.3s ease-out;
}

@keyframes fadeInRow {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.mobile-card {
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
