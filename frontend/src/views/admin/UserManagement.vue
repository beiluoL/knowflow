<template>
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
                  class="inline-flex items-center gap-2 px-4 py-2 text-sm border border-gray-200 rounded-sm bg-white hover:bg-gray-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                >
                  <Icon name="filter" :size="16" aria-hidden="true" />
                  <span>{{ selectedRole || '全部角色' }}</span>
                  <Icon name="chevron-down" :size="16" aria-hidden="true" />
                </button>
                <div
                  v-if="showRoleFilter"
                  class="absolute top-full left-0 mt-1 w-40 bg-white border border-gray-200 rounded-sm shadow-lg z-10 py-1 animate-dropdown"
                >
                  <button
                    @click="selectedRole = ''; showRoleFilter = false"
                    class="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                    :class="{ 'bg-primary-50 text-primary-600': !selectedRole }"
                  >
                    全部角色
                  </button>
                  <button
                    v-for="role in roles" :key="role.value"
                    @click="selectedRole = role.value; showRoleFilter = false"
                    class="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                    :class="{ 'bg-primary-50 text-primary-600': selectedRole === role.value }"
                  >
                    {{ role.label }}
                  </button>
                </div>
              </div>
              <Button icon-name="user-plus" @click="openCreate">新增用户</Button>
            </div>
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">用户</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">邮箱</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">角色</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">等级</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">学习时长</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">注册时间</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr
                v-for="user in pagedUsers" :key="user.id"
                class="hover:bg-gray-50 transition-colors table-row"
              >
                <td class="px-4 py-3">
                  <div class="flex items-center gap-3">
                    <Avatar :src="user.avatar" :name="user.username" size="md" />
                    <div>
                      <p class="text-sm font-medium text-gray-800">{{ user.username }}</p>
                      <p class="text-xs text-gray-400">ID: {{ user.id }}</p>
                    </div>
                  </div>
                </td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ user.email || '—' }}</td>
                <td class="px-4 py-3">
                  <Badge :variant="user.role === 'ADMIN' ? 'primary' : 'default'">{{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}</Badge>
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
                  <div class="flex items-center gap-2">
                    <button class="p-1 text-gray-400 hover:text-primary-500 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" title="编辑" @click="openEdit(user)">
                      <Icon name="edit" :size="16" aria-hidden="true" />
                    </button>
                    <button class="p-1 text-gray-400 hover:text-danger-500 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" title="删除" @click="removeUser(user)">
                      <Icon name="trash-2" :size="16" aria-hidden="true" />
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="pagedUsers.length === 0">
                <td colspan="7" class="px-4 py-12 text-center text-gray-400 text-sm">暂无用户数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="px-4 py-3 border-t border-gray-100 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p class="text-sm text-gray-500">
            共 <span class="font-medium text-gray-700">{{ totalUsers }}</span> 位用户
          </p>
          <div class="flex items-center gap-1">
            <button
              @click="currentPage = Math.max(1, currentPage - 1)"
              :disabled="currentPage === 1"
              class="p-2 rounded-sm border border-gray-200 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <Icon name="chevron-left" :size="16" aria-hidden="true" />
            </button>
            <button
              v-for="page in visiblePages" :key="page"
              @click="currentPage = page"
              :class="[
                'w-8 h-8 text-sm rounded-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
                currentPage === page
                  ? 'bg-primary-500 text-white hover:bg-primary-600'
                  : 'border border-gray-200 text-gray-600 hover:bg-gray-50'
              ]"
            >
              {{ page }}
            </button>
            <button
              @click="currentPage = Math.min(totalPages, currentPage + 1)"
              :disabled="currentPage === totalPages"
              class="p-2 rounded-sm border border-gray-200 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <Icon name="chevron-right" :size="16" aria-hidden="true" />
            </button>
          </div>
        </div>
      </Card>
    </div>

    <!-- 新增/编辑弹窗 -->
    <div
      v-if="showModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeModal"
    >
      <div class="bg-white rounded-xl w-full max-w-md shadow-xl animate-dropdown">
        <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 class="text-lg font-semibold text-gray-800">{{ editingId ? '编辑用户' : '新增用户' }}</h3>
          <button class="p-1 hover:bg-gray-100 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" aria-label="关闭" @click="closeModal">
            <Icon name="x" :size="20" aria-hidden="true" />
          </button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">用户名</label>
            <Input v-model="form.username" placeholder="请输入用户名" :disabled="!!editingId" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">邮箱</label>
            <Input v-model="form.email" placeholder="请输入邮箱" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">昵称</label>
            <Input v-model="form.nickname" placeholder="请输入昵称" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">角色</label>
            <select v-model="form.role" class="w-full px-3 py-2 border border-gray-200 rounded-sm text-sm hover:border-gray-300 transition-colors focus:outline-none focus:border-primary-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2">
              <option value="USER">普通用户</option>
              <option value="ADMIN">管理员</option>
            </select>
          </div>
          <div v-if="!editingId">
            <label class="block text-sm font-medium text-gray-700 mb-1.5">密码</label>
            <Input v-model="form.password" type="password" placeholder="请输入初始密码" />
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100">
          <Button variant="secondary" @click="closeModal">取消</Button>
          <Button :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</Button>
        </div>
      </div>
    </div>
</template>

<script setup lang="ts">
// 管理后台-用户管理：维护用户账号的增删改查、角色分配与分页检索。
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import Avatar from '@/components/ui/Avatar.vue'
import Progress from '@/components/ui/Progress.vue'
import { adminApi } from '@/api'
import type { UserVO } from '@/api/types'

interface UserRow {
  id: number
  username: string
  email?: string
  avatar?: string
  role: string
  level: number
  studyHours: number
  studyProgress: number
  registerTime: string
  raw: UserVO
}

const searchQuery = ref('')
const selectedRole = ref('')
const showRoleFilter = ref(false)
const currentPage = ref(1)
const pageSize = 10

const roles = [
  { label: '管理员', value: 'ADMIN' },
  { label: '普通用户', value: 'USER' },
]

const allUsers = ref<UserRow[]>([])
const loading = ref(false)

const formatDate = (v?: string): string => {
  if (!v) return '—'
  return v.slice(0, 10)
}

const filteredUsers = computed(() => {
  let result = [...allUsers.value]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(
      (u) => u.username.toLowerCase().includes(q) || (u.email ?? '').toLowerCase().includes(q)
    )
  }
  if (selectedRole.value) {
    result = result.filter((u) => u.role === selectedRole.value)
  }
  return result
})

const totalUsers = computed(() => filteredUsers.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalUsers.value / pageSize)))
const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredUsers.value.slice(start, start + pageSize)
})

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = currentPage.value
  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)
  if (end - start + 1 < 5) {
    if (start === 1) end = Math.min(5, total)
    else start = Math.max(1, total - 4)
  }
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

// ===== 弹窗 / 表单 =====
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref<{ username: string; email: string; nickname: string; role: string; password: string }>({
  username: '',
  email: '',
  nickname: '',
  role: 'USER',
  password: '',
})

const openCreate = () => {
  editingId.value = null
  form.value = { username: '', email: '', nickname: '', role: 'USER', password: '' }
  showModal.value = true
}

const openEdit = (user: UserRow) => {
  editingId.value = user.id
  form.value = {
    username: user.username,
    email: user.email ?? '',
    nickname: user.raw.nickname ?? '',
    role: user.role,
    password: '',
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  editingId.value = null
}

const save = async () => {
  if (!form.value.username.trim()) {
    notify('请填写用户名', 'warning')
    return
  }
  if (!editingId.value && !form.value.password.trim()) {
    notify('请填写初始密码', 'warning')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await adminApi.updateUser(editingId.value, {
        email: form.value.email,
        nickname: form.value.nickname,
        role: form.value.role,
      })
    } else {
      await adminApi.createUser({
        username: form.value.username,
        email: form.value.email,
        nickname: form.value.nickname,
        role: form.value.role,
        password: form.value.password,
      })
    }
    notify(editingId.value ? '更新成功' : '创建成功', 'success')
    closeModal()
    await loadUsers()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const removeUser = async (user: UserRow) => {
  if (!(await confirmDialog(`确定删除用户「${user.username}」吗？此操作不可恢复。`))) return
  try {
    await adminApi.removeUser(user.id)
    notify('删除成功', 'success')
    if (pagedUsers.value.length === 1 && currentPage.value > 1) currentPage.value -= 1
    await loadUsers()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

const loadUsers = async () => {
  loading.value = true
  try {
    const page = await adminApi.users({ pageSize: 200 })
    const records = (page.records ?? []) as UserVO[]
    allUsers.value = records.map((u) => ({
      id: u.id,
      username: u.username,
      email: u.email,
      avatar: u.avatar,
      role: u.role ?? 'USER',
      level: u.level ?? 0,
      studyHours: u.totalStudyHours ?? 0,
      // 按等级粗略估算学习进度百分比（每级约 7%，上限 100）
      studyProgress: Math.min(100, (u.level ?? 0) * 7),
      registerTime: formatDate(u.createTime),
      raw: u,
    }))
  } catch (e: unknown) {
    notify('加载用户失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)
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
</style>
