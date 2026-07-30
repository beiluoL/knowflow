<template>
  <div class="knowledge-create-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <Icon name="arrow-left" :size="18" />
        <span>返回知识库</span>
      </button>
      <h1 class="page-title">新建知识库</h1>
      <div class="header-right"></div>
    </div>

    <div class="create-layout">
      <!-- 左侧：基本信息表单 -->
      <div class="create-left">
        <div class="form-card">
          <h2 class="form-section-title">基本信息</h2>
          
          <div class="form-group">
            <label class="form-label">
              知识库名称 <span class="required-mark">*</span>
            </label>
            <input
              v-model="formData.name"
              type="text"
              placeholder="请输入知识库名称"
              class="form-input"
              maxlength="50"
            />
            <p class="form-hint">{{ formData.name.length }} / 50 字符</p>
          </div>

          <div class="form-group">
            <label class="form-label">知识库图标</label>
            <div class="icon-grid">
              <div
                v-for="icon in availableIcons"
                :key="icon.name"
                class="icon-item"
                :class="{ active: formData.icon === icon.name }"
                @click="formData.icon = icon.name"
                :title="icon.name"
              >
                <Icon :name="icon.name" :size="20" />
              </div>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">知识库颜色</label>
            <div class="color-picker">
              <div
                v-for="color in availableColors"
                :key="color.value"
                class="color-item"
                :class="{ active: formData.color === color.value }"
                :style="{ background: color.value }"
                @click="formData.color = color.value"
              >
                <Icon v-if="formData.color === color.value" name="check" :size="14" />
              </div>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">知识库描述</label>
            <textarea
              v-model="formData.description"
              rows="4"
              placeholder="请输入知识库的描述信息，帮助团队成员了解其用途..."
              class="form-textarea"
              maxlength="200"
            ></textarea>
            <p class="form-hint">{{ formData.description.length }} / 200 字符</p>
          </div>

          <div class="form-group">
            <label class="form-label">知识库成员</label>
            <div class="member-section">
              <div class="member-list">
                <div v-for="member in members" :key="member.id" class="member-item">
                  <div class="member-avatar" :style="{ background: getMemberColor(member.name) }">
                    {{ member.name.charAt(0).toUpperCase() }}
                  </div>
                  <div class="member-info">
                    <span class="member-name">{{ member.name }}</span>
                    <span class="member-role">{{ member.role }}</span>
                  </div>
                  <button class="member-remove" @click="removeMember(member.id)" v-if="member.id !== currentUserId">
                    <Icon name="x" :size="14" />
                  </button>
                </div>
              </div>
              
              <div class="invite-section">
                <div class="invite-input-row">
                  <input
                    v-model="inviteEmail"
                    type="email"
                    placeholder="输入成员邮箱邀请加入"
                    class="form-input"
                  />
                  <button class="invite-btn" @click="inviteMember">
                    <Icon name="user-plus" :size="14" />
                    <span>邀请</span>
                  </button>
                </div>
                <p class="form-hint">邀请的成员将以「编辑者」身份加入，可修改知识库内容</p>
              </div>
            </div>
          </div>

          <div class="form-actions">
            <button class="btn-secondary" @click="goBack">取消</button>
            <button class="btn-primary" :disabled="!canSubmit" @click="handleCreate">
              <Icon name="check" :size="14" />
              <span>创建知识库</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 右侧：预览面板 -->
      <div class="create-right">
        <div class="preview-card">
          <h3 class="preview-title">预览效果</h3>
          
          <div class="preview-knowledge" :style="{ borderColor: formData.color + '40' }">
            <div class="preview-icon" :style="{ background: formData.color + '15', color: formData.color }">
              <Icon :name="formData.icon" :size="28" />
            </div>
            <div class="preview-info">
              <h4 class="preview-name">{{ formData.name || '知识库名称' }}</h4>
              <p class="preview-desc">{{ formData.description || '知识库描述将显示在这里...' }}</p>
            </div>
            <div class="preview-stats">
              <div class="preview-stat">
                <Icon name="file-text" :size="14" />
                <span>0 文档</span>
              </div>
              <div class="preview-stat">
                <Icon name="users" :size="14" />
                <span>{{ members.length }} 成员</span>
              </div>
            </div>
          </div>

          <div class="preview-tips">
            <h4 class="tips-title">创建后可执行：</h4>
            <ul class="tips-list">
              <li>
                <Icon name="folder-plus" :size="14" />
                <span>创建多级分类结构组织文档</span>
              </li>
              <li>
                <Icon name="upload" :size="14" />
                <span>上传 PDF、Word、Markdown 等文档</span>
              </li>
              <li>
                <Icon name="user-plus" :size="14" />
                <span>邀请团队成员协作编辑</span>
              </li>
              <li>
                <Icon name="share" :size="14" />
                <span>分享知识库链接给其他成员</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi } from '@/api'
import type { CategoryInput } from '@/api/types'
import { notify, getApiError } from '@/utils/toast'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

interface Member {
  id: number
  name: string
  email?: string
  role: string
  isOwner?: boolean
}

const availableIcons = [
  { name: 'folder' },
  { name: 'code' },
  { name: 'server' },
  { name: 'database' },
  { name: 'brain' },
  { name: 'layout' },
  { name: 'palette' },
  { name: 'container' },
  { name: 'shield' },
  { name: 'binary' },
  { name: 'book-open' },
  { name: 'cpu' },
  { name: 'target' },
  { name: 'bar-chart-2' },
  { name: 'briefcase' },
  { name: 'layers' },
  { name: 'file-code' },
  { name: 'router' },
]

const availableColors = [
  { value: '#3B6FE0' },
  { value: '#8B5CF6' },
  { value: '#EC4899' },
  { value: '#EF4444' },
  { value: '#F59E0B' },
  { value: '#10B981' },
  { value: '#06B6D4' },
  { value: '#6366F1' },
]

const formData = ref({
  name: '',
  icon: 'folder',
  color: '#3B6FE0',
  description: '',
})

const members = ref<Member[]>([])
const inviteEmail = ref('')

const currentUserId = computed(() => auth.user?.id || 0)

const canSubmit = computed(() => {
  return formData.value.name.trim().length > 0
})

function getMemberColor(name: string): string {
  const colors = ['#3B6FE0', '#8B5CF6', '#EC4899', '#F59E0B', '#10B981', '#06B6D4']
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

function inviteMember(): void {
  const email = inviteEmail.value.trim()
  if (!email) {
    notify('请输入成员邮箱', 'warning')
    return
  }
  
  const existing = members.value.find(m => m.email === email)
  if (existing) {
    notify('该成员已在知识库中', 'warning')
    return
  }
  
  const newMember: Member = {
    id: Date.now(),
    name: email.split('@')[0],
    email,
    role: '编辑者',
  }
  members.value.push(newMember)
  inviteEmail.value = ''
  notify(`已邀请 ${email} 加入知识库`, 'success')
}

function removeMember(id: number): void {
  members.value = members.value.filter(m => m.id !== id)
}

async function handleCreate(): Promise<void> {
  if (!canSubmit.value) return
  
  try {
    const data: CategoryInput = {
      name: formData.value.name.trim(),
      icon: formData.value.icon,
      description: formData.value.description.trim(),
    }
    
    const result = await categoriesApi.create(data)
    notify(`知识库「${formData.value.name}」创建成功！`, 'success')
    
    // 如果有邀请成员，这里可以调用后端邀请接口
    if (members.value.length > 0) {
      // TODO: 调用后端邀请接口邀请成员加入知识库
      // 遍历 members.value，跳过 isOwner 的成员
    }
    
    setTimeout(() => {
      router.push('/knowledge')
    }, 1500)
  } catch (e) {
    notify(`创建失败：${getApiError(e, '请稍后再试')}`, 'error')
  }
}

function goBack(): void {
  router.push('/knowledge')
}
</script>

<style scoped>
.knowledge-create-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px 40px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  margin-bottom: 8px;
}

.header-left,
.header-right {
  width: 140px;
}

.header-right {
  display: flex;
  justify-content: flex-end;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.back-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}

.create-layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 24px;
  align-items: start;
}

@media (max-width: 1024px) {
  .create-layout {
    grid-template-columns: 1fr;
  }
}

.form-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  padding: 24px;
}

.form-section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.required-mark {
  color: #EF4444;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  font-family: inherit;
}

.form-input:focus,
.form-textarea:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

.form-textarea {
  resize: none;
  min-height: 100px;
}

.form-hint {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 6px 0 0;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  gap: 8px;
}

.icon-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  cursor: pointer;
  background: var(--kb-background);
  border: 2px solid transparent;
  transition: all 0.15s;
  color: var(--kb-foreground);
}

.icon-item:hover {
  background: var(--kb-muted);
}

.icon-item.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

.color-picker {
  display: flex;
  gap: 10px;
}

.color-item {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  color: white;
  transition: all 0.15s;
}

.color-item:hover {
  transform: scale(1.1);
}

.color-item.active {
  border-color: var(--kb-foreground);
  transform: scale(1.1);
}

.member-section {
  background: var(--kb-background);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--kb-border);
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  background: var(--kb-card);
}

.member-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 13px;
  font-weight: 600;
}

.member-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.member-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.member-role {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.member-remove {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  border-radius: 4px;
  transition: all 0.15s;
}

.member-remove:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.invite-section {
  border-top: 1px solid var(--kb-border);
  padding-top: 16px;
}

.invite-input-row {
  display: flex;
  gap: 8px;
}

.invite-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  height: 40px;
  border-radius: 8px;
  border: none;
  background: var(--kb-primary);
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.invite-btn:hover {
  opacity: 0.9;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--kb-border);
}

.btn-primary,
.btn-secondary {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
  font-family: inherit;
}

.btn-primary {
  background: var(--kb-primary);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
}

.btn-secondary:hover {
  background: var(--kb-muted);
}

/* 预览面板 */
.preview-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  padding: 20px;
  position: sticky;
  top: 80px;
}

.preview-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 16px;
}

.preview-knowledge {
  border: 2px solid;
  border-radius: 14px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}

.preview-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.preview-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0;
  line-height: 1.5;
}

.preview-stats {
  display: flex;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--kb-border);
}

.preview-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.preview-tips {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--kb-border);
}

.tips-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 10px;
}

.tips-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tips-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

@media (max-width: 768px) {
  .create-layout {
    grid-template-columns: 1fr;
  }
  
  .preview-card {
    position: static;
  }
  
  .icon-grid {
    grid-template-columns: repeat(6, 1fr);
  }
  
  .page-header {
    flex-direction: column;
    gap: 12px;
  }
  
  .header-left,
  .header-right {
    width: auto;
  }
}
</style>
