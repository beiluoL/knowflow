<template>
  <aside
    :class="[
      'fixed left-0 top-0 h-screen bg-white border-r border-gray-100 flex flex-col z-30 transition-all duration-300 ease-in-out',
      collapsed ? 'w-16' : 'w-64',
    ]"
  >
    <div class="h-16 flex items-center justify-center border-b border-gray-100 flex-shrink-0">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-md bg-primary-500 flex items-center justify-center text-white font-bold">
          知
        </div>
        <span
          v-if="!collapsed"
          class="text-lg font-semibold text-gray-800 whitespace-nowrap transition-opacity duration-200"
        >
          知识库
        </span>
      </div>
    </div>

    <nav class="flex-1 py-4 overflow-y-auto">
      <ul class="space-y-1 px-3">
        <li>
          <router-link
            to="/"
            class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
            :class="route.path === '/' ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
          >
            <Icon name="home" :size="20" class="flex-shrink-0" />
            <span v-if="!collapsed" class="whitespace-nowrap transition-opacity duration-200">首页</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/categories"
            class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
            :class="route.path.startsWith('/categories') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
          >
            <Icon name="file-text" :size="20" class="flex-shrink-0" />
            <span v-if="!collapsed" class="whitespace-nowrap transition-opacity duration-200">文档</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/upload"
            class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
            :class="route.path.startsWith('/upload') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
          >
            <Icon name="upload" :size="20" class="flex-shrink-0" />
            <span v-if="!collapsed" class="whitespace-nowrap transition-opacity duration-200">上传文档</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/categories"
            class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
            :class="route.path.startsWith('/categories') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
          >
            <Icon name="folder-open" :size="20" class="flex-shrink-0" />
            <span v-if="!collapsed" class="whitespace-nowrap transition-opacity duration-200">分类</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/chat"
            class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
            :class="route.path.startsWith('/chat') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
          >
            <Icon name="message-circle" :size="20" class="flex-shrink-0" />
            <span v-if="!collapsed" class="whitespace-nowrap transition-opacity duration-200">AI问答</span>
            <span v-if="!collapsed" class="ml-auto px-2 py-0.5 text-xs font-medium bg-primary-100 text-primary-600 rounded-full">New</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/learning/center"
            class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
            :class="route.path.startsWith('/learning') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
          >
            <Icon name="graduation-cap" :size="20" class="flex-shrink-0" />
            <span v-if="!collapsed" class="whitespace-nowrap transition-opacity duration-200">学习中心</span>
          </router-link>
        </li>
      </ul>

      <div v-if="!collapsed" class="mt-6 px-3">
        <div class="text-xs font-medium text-gray-400 uppercase tracking-wider mb-2 px-3">
          学习工具
        </div>
        <ul class="space-y-1">
          <li>
            <router-link
              to="/learning/paths"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/learning/paths') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="route" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">学习路径</span>
            </router-link>
          </li>
          <li>
            <router-link
              to="/learning/flashcards"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/learning/flashcards') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="layers" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">闪卡</span>
            </router-link>
          </li>
          <li>
            <router-link
              to="/learning/review"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/learning/review') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="calendar" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">复习计划</span>
            </router-link>
          </li>

          <div class="mt-3 px-3">
            <div class="text-xs font-medium text-gray-400 uppercase tracking-wider">
              智能练习
            </div>
          </div>
          <li>
            <router-link
              to="/learning/mode"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/learning/mode') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="book-open" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">学习模式</span>
            </router-link>
          </li>
          <li>
            <router-link
              to="/learning/quiz"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/learning/quiz') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="help-circle" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">智能测验</span>
            </router-link>
          </li>
          <li>
            <router-link
              to="/learning/writing"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/learning/writing') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="edit" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">智能写作</span>
            </router-link>
          </li>
          <li>
            <router-link
              to="/learning/code-practice"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/learning/code-practice') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="code" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">代码练习</span>
            </router-link>
          </li>
          <li>
            <router-link
              to="/profile"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/profile') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="user" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">个人中心</span>
            </router-link>
          </li>
          <li>
            <router-link
              to="/admin/overview"
              class="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group"
              :class="route.path.startsWith('/admin') ? 'bg-primary-50 text-primary-600' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'"
            >
              <Icon name="settings" :size="20" class="flex-shrink-0" />
              <span class="whitespace-nowrap transition-opacity duration-200">管理后台</span>
            </router-link>
            <ul v-if="!collapsed && isAdmin" class="mt-1 space-y-1 pl-6 border-l border-gray-100 ml-3">
              <li>
                <router-link
                  to="/admin/docs"
                  class="flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-all duration-200 group"
                  :class="route.path.startsWith('/admin/docs') ? 'bg-primary-50 text-primary-600' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'"
                >
                  <Icon name="file-text" :size="16" class="flex-shrink-0" />
                  <span class="whitespace-nowrap">文档管理</span>
                </router-link>
              </li>
              <li>
                <router-link
                  to="/admin/knowledge"
                  class="flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-all duration-200 group"
                  :class="route.path.startsWith('/admin/knowledge') ? 'bg-primary-50 text-primary-600' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'"
                >
                  <Icon name="database" :size="16" class="flex-shrink-0" />
                  <span class="whitespace-nowrap">知识库管理</span>
                </router-link>
              </li>
              <li>
                <router-link
                  to="/admin/users"
                  class="flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-all duration-200 group"
                  :class="route.path.startsWith('/admin/users') ? 'bg-primary-50 text-primary-600' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'"
                >
                  <Icon name="users" :size="16" class="flex-shrink-0" />
                  <span class="whitespace-nowrap">用户管理</span>
                </router-link>
              </li>
              <li>
                <router-link
                  to="/admin/chat-config"
                  class="flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-all duration-200 group"
                  :class="route.path.startsWith('/admin/chat-config') ? 'bg-primary-50 text-primary-600' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'"
                >
                  <Icon name="message-square" :size="16" class="flex-shrink-0" />
                  <span class="whitespace-nowrap">对话配置</span>
                </router-link>
              </li>
              <li>
                <router-link
                  to="/admin/flashcards"
                  class="flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-all duration-200 group"
                  :class="route.path.startsWith('/admin/flashcards') ? 'bg-primary-50 text-primary-600' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'"
                >
                  <Icon name="layers" :size="16" class="flex-shrink-0" />
                  <span class="whitespace-nowrap">闪卡管理</span>
                </router-link>
              </li>
            </ul>
          </li>
        </ul>
      </div>
    </nav>

    <div class="border-t border-gray-100 p-3 flex-shrink-0">
      <button
        @click="$emit('toggle-collapse')"
        class="w-full flex items-center justify-center gap-2 px-3 py-2 text-sm text-gray-500 hover:text-gray-700 hover:bg-gray-50 rounded-md transition-colors"
      >
        <Icon v-if="!collapsed" name="chevron-left" :size="20" />
        <Icon v-else name="chevron-right" :size="20" />
        <span v-if="!collapsed">收起菜单</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{
  collapsed: boolean
}>()

defineEmits<{
  'toggle-collapse': []
}>()

const route = useRoute()
const auth = useAuthStore()
const isAdmin = computed(() => auth.isAdmin)
</script>
