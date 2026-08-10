<template>
  <div class="knowledge-create-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <button type="button" class="back-btn" @click="goBack">
        <Icon name="arrow-left" :size="16" />
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
              <button
                v-for="icon in availableIcons"
                :key="icon.name"
                type="button"
                class="icon-item"
                :class="{ active: formData.icon === icon.name }"
                @click="formData.icon = icon.name"
                :title="icon.name"
                :aria-label="icon.name"
                :aria-pressed="formData.icon === icon.name"
              >
                <Icon :name="icon.name" :size="20" />
              </button>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">知识库颜色</label>
            <div class="color-picker">
              <button
                v-for="color in availableColors"
                :key="color.value"
                type="button"
                class="color-item"
                :class="{ active: formData.color === color.value }"
                :style="{ background: color.value }"
                @click="formData.color = color.value"
                :aria-label="color.value"
                :aria-pressed="formData.color === color.value"
              >
                <Icon v-if="formData.color === color.value" name="check" :size="14" />
              </button>
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
                  <button type="button" class="member-remove" aria-label="移除成员" @click="removeMember(member.id)" v-if="member.id !== currentUserId">
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
                  <button type="button" class="invite-btn" @click="inviteMember">
                    <Icon name="user-plus" :size="14" />
                    <span>邀请</span>
                  </button>
                </div>
                <p class="form-hint">邀请的成员将以「编辑者」身份加入，可修改知识库内容</p>
              </div>
            </div>
          </div>

          <div class="form-actions">
            <button type="button" class="btn-secondary" @click="goBack">取消</button>
            <button type="button" class="btn-primary" :disabled="!canSubmit" @click="handleCreate">
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
              <Icon :name="formData.icon" :size="24" />
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
import { categoriesApi, adminApi } from '@/api'
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
    
    const created = await categoriesApi.create(data)
    notify(`知识库「${formData.value.name}」创建成功！`, 'success')
    
    // 若有邀请成员（编辑者），创建成功后调用后端邀请接口逐个绑定。
    // 跳过 isOwner 的成员（创建者已由后端自动绑定为 OWNER）。
    const toInvite = members.value.filter(m => !m.isOwner && m.email)
    if (created?.id && toInvite.length > 0) {
      for (const m of toInvite) {
        // 前端 role 为中文「编辑者」，后端只认 EDITOR/OWNER/READER，需映射
        const role = m.role === '编辑者' ? 'EDITOR' : 'READER'
        await adminApi.addKbMember({
          categoryId: created.id,
          email: m.email,
          role,
        })
      }
      notify(`已邀请 ${toInvite.length} 位成员加入知识库`, 'success')
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
  gap: var(--kb-space-2);
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.back-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.back-btn:active {
  transform: scale(0.98);
}

.back-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.page-title {
  font-size: var(--kb-fs-h4);
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
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.required-mark {
  color: var(--kb-destructive);
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 14px;
  border-radius: var(--kb-radius-md);
  font-size: var(--kb-fs-body-md);
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
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  margin: var(--kb-space-2) 0 0;
  font-variant-numeric: tabular-nums;
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
  padding: 0;
  border-radius: var(--kb-radius-md);
  cursor: pointer;
  background: var(--kb-background);
  border: 2px solid transparent;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
  color: var(--kb-foreground);
}

.icon-item:hover {
  background: var(--kb-muted);
}

.icon-item:active {
  transform: scale(0.96);
}

.icon-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.icon-item.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kb-space-3);
}

.color-item {
  width: 32px;
  height: 32px;
  padding: 0;
  flex-shrink: 0;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  color: #FFFFFF;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.color-item:hover {
  transform: scale(1.1);
}

.color-item:active {
  transform: scale(0.95);
}

.color-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
  gap: var(--kb-space-3);
  margin-bottom: var(--kb-space-4);
}

.member-item {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3);
  padding: var(--kb-space-2);
  border-radius: 8px;
  background: var(--kb-card);
}

.member-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #FFFFFF;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
}

.member-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.member-name {
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-remove {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  border-radius: 4px;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}

.member-remove:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-destructive);
}

.member-remove:active {
  transform: scale(0.92);
}

.member-remove:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.invite-section {
  border-top: 1px solid var(--kb-border);
  padding-top: var(--kb-space-4);
}

.invite-input-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kb-space-2);
}

.invite-input-row .form-input {
  flex: 1;
  min-width: 0;
}

.invite-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  padding: 0 16px;
  height: 40px;
  border-radius: 8px;
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.15s ease;
  flex-shrink: 0;
}

.invite-btn:hover {
  opacity: 0.9;
}

.invite-btn:active {
  transform: scale(0.98);
}

.invite-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.form-actions {
  display: flex;
  gap: var(--kb-space-3);
  margin-top: var(--kb-space-6);
  padding-top: var(--kb-space-5);
  border-top: 1px solid var(--kb-border);
}

.btn-primary,
.btn-secondary {
  flex: 1;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--kb-space-2);
  height: 40px;
  padding: 0 16px;
  border-radius: var(--kb-radius-md);
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s ease, background 0.15s ease, transform 0.15s ease;
  border: none;
  font-family: inherit;
}

.btn-primary:focus-visible,
.btn-secondary:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.btn-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:active:not(:disabled) {
  transform: scale(0.98);
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

.btn-secondary:active {
  transform: scale(0.98);
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
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 var(--kb-space-4);
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
  flex-shrink: 0;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--kb-space-1);
}

.preview-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-1);
}

.preview-name {
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
  overflow-wrap: anywhere;
}

.preview-desc {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  margin: 0;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.preview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kb-space-4);
  padding-top: var(--kb-space-3);
  border-top: 1px solid var(--kb-border);
}

.preview-stat {
  display: flex;
  align-items: center;
  gap: var(--kb-space-1);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.preview-stat svg {
  flex-shrink: 0;
}

.preview-tips {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--kb-border);
}

.tips-title {
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 var(--kb-space-3);
}

.tips-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-2);
}

.tips-list li {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}

.tips-list li svg {
  flex-shrink: 0;
}

.tips-list li span {
  min-width: 0;
}

@media (max-width: 768px) {
  .knowledge-create-page {
    padding: 0 var(--kb-space-4) 40px;
  }
  
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
