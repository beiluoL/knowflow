<template>
  <!-- 上传文档页：左侧拖拽上传区 + 已上传文件列表（含进度条），右侧上传设置表单 -->
  <div class="upload-doc-page animate-fade-in">
    <h1 class="kf-h1 page-title">上传文档</h1>

    <div class="upload-layout">
      <!-- ===== 左侧：上传区 + 文件列表 ===== -->
      <div class="upload-left">
        <!-- 拖拽上传区 -->
        <div
          class="upload-zone"
          role="button"
          tabindex="0"
          :class="{ dragging: isDragging, hasfiles: files.length > 0 }"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
          @click="triggerFileInput"
          @keydown.enter.prevent="($event.target as HTMLElement).click()"
        >
          <input
            ref="fileInputRef"
            type="file"
            class="hidden-file-input"
            accept=".pdf,.doc,.docx,.md,.markdown,.txt,.ppt,.pptx"
            multiple
            @change="handleFileSelect"
          />
          <div class="upload-zone-icon">
            <Icon name="upload" :size="24" />
          </div>
          <p class="upload-zone-title">
            {{ isDragging ? '释放文件以上传' : '拖拽文件到此处，或点击选择文件' }}
          </p>
          <p class="upload-zone-hint">支持 PDF、Markdown、Word、TXT 等格式，单个文件最大 50 MB</p>
        </div>

        <!-- 已上传文件列表（含进度条） -->
        <div v-if="files.length > 0" class="file-list-card">
          <div class="file-list-header">
            <h3 class="kf-h3">已上传文件</h3>
            <span class="file-list-count">{{ files.length }} 个文件</span>
          </div>
          <div class="file-list-body">
            <div
              v-for="(file, idx) in files"
              :key="file.id"
              class="file-item"
            >
              <!-- 文件图标（按类型着色） -->
              <div class="file-icon" :class="getFileTypeColorClass(file.name)">
                <Icon :name="getFileTypeIcon(file.name)" :size="16" />
              </div>
              <!-- 文件信息：名称 + 大小 -->
              <div class="file-info">
                <p class="file-name">{{ file.name }}</p>
                <p class="file-size">{{ formatFileSize(file.size) }}</p>
              </div>
              <!-- 进度条 -->
              <div class="file-progress">
                <div class="upload-bar">
                  <div
                    class="upload-bar-fill"
                    :class="getProgressStatusClass(file)"
                    :style="{ width: `${file.progress}%` }"
                  ></div>
                </div>
              </div>
              <!-- 状态文字 -->
              <span class="file-status" :class="getStatusTextClass(file)">
                {{ getStatusLabel(file) }}
              </span>
              <!-- 删除按钮 -->
              <button
                type="button"
                class="file-remove-btn"
                title="移除"
                :aria-label="`移除 ${file.name}`"
                @click.stop="removeFile(idx)"
              >
                <Icon name="trash-2" :size="14" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 右侧：上传设置表单 ===== -->
      <aside class="upload-right">
        <h3 class="kf-h3 form-title">上传设置</h3>
        <div class="form-body">
          <!-- 文档标题 -->
          <div class="form-row">
            <label class="form-label">
              文档标题 <span class="required-mark">*</span>
            </label>
            <input
              v-model="formData.title"
              type="text"
              placeholder="请输入文档标题"
              class="form-input kf-input kf-form-control"
            />
            <p class="form-hint">默认取第一个文件名，可手动修改</p>
          </div>
          <!-- 目标知识库 -->
          <div class="form-row">
            <label class="form-label">
              目标知识库 <span class="required-mark">*</span>
            </label>
            <CategoryTreeSelect
              v-model="formData.kbId"
              :categories="allCategories"
              placeholder="请选择知识库"
              empty-label="请选择知识库"
            />
          </div>
          <!-- 分类 -->
          <div class="form-row">
            <label class="form-label">
              分类 <span class="required-mark">*</span>
            </label>
            <CategoryTreeSelect
              v-model="formData.categoryId"
              :categories="allCategories"
              placeholder="请选择分类"
              empty-label="请选择分类"
            />
          </div>
          <!-- 标签 -->
          <div class="form-row">
            <label class="form-label">标签</label>
            <div class="tags-input">
              <span
                v-for="(tag, idx) in formData.tags"
                :key="idx"
                class="tag-chip"
              >
                {{ tag }}
                <button
                  type="button"
                  class="tag-remove"
                  @click="removeTag(idx)"
                  :title="`移除 ${tag}`"
                >
                  <Icon name="x" :size="12" />
                </button>
              </span>
              <input
                v-model="tagInput"
                type="text"
                placeholder="添加标签后回车"
                class="tag-text-input kf-input kf-form-control"
                @keydown.enter.prevent="addTag"
              />
            </div>
          </div>
          <!-- 描述 -->
          <div class="form-row">
            <label class="form-label">描述</label>
            <textarea
              v-model="formData.description"
              rows="3"
              placeholder="请输入文档描述..."
              class="form-textarea kf-textarea kf-form-control"
            ></textarea>
          </div>
        </div>
        <!-- 底部操作按钮 -->
        <div class="form-actions">
          <button type="button" class="btn-secondary" @click="handleCancel">
            <span>取消</span>
          </button>
          <button
            type="button"
            class="btn-primary"
            :disabled="!canSubmit || uploading"
            @click="handleUpload"
          >
            <Icon name="upload" :size="14" />
            <span>{{ uploading ? '上传中...' : '开始上传' }}</span>
          </button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 文档上传页：支持多文件拖拽上传 + 实时进度条 + 元信息表单（标题/知识库/分类/标签/描述）。
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue';
import { categoriesApi, docsApi } from '@/api';
import type { CategoryVO } from '@/api/types';
import { notify, getApiError } from '@/utils/toast';

const router = useRouter();

// ===== 类型定义 =====
interface UploadFile {
  id: string;
  file: File;
  name: string;
  size: number;
  progress: number; // 0-100
  status: 'pending' | 'uploading' | 'done' | 'error';
}

// ===== 状态 =====
const fileInputRef = ref<HTMLInputElement | null>(null);
const isDragging = ref(false);
const files = ref<UploadFile[]>([]);
const uploading = ref(false);
const tagInput = ref('');
const allCategories = ref<CategoryVO[]>([]);

const formData = ref({
  title: '',
  kbId: null as number | null,
  categoryId: null as number | null,
  tags: [] as string[],
  description: '',
  difficulty: null as number | null,
});

// ===== 计算属性 =====
// 是否可提交：至少 1 个文件 + 标题 + 知识库 + 分类
const canSubmit = computed(() => {
  return (
    files.value.length > 0 &&
    formData.value.title.trim() !== '' &&
    formData.value.kbId !== null &&
    formData.value.categoryId !== null
  );
});

// ===== 文件选择/拖拽 =====
function triggerFileInput(): void {
  fileInputRef.value?.click();
}

function handleFileSelect(e: Event): void {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    appendFiles(Array.from(target.files));
    // 重置 input value 允许重复选择同一文件
    target.value = '';
  }
}

function handleDrop(e: DragEvent): void {
  isDragging.value = false;
  if (e.dataTransfer?.files && e.dataTransfer.files.length > 0) {
    appendFiles(Array.from(e.dataTransfer.files));
  }
}

// 添加文件到列表（去重 + 大小校验），首文件自动填充标题
function appendFiles(fileList: File[]): void {
  const MAX_SIZE = 50 * 1024 * 1024; // 50 MB
  for (const f of fileList) {
    if (f.size > MAX_SIZE) {
      notify(`${f.name} 超过 50 MB 限制`, 'warning');
      continue;
    }
    // 同名文件去重
    if (files.value.some((x) => x.name === f.name && x.size === f.size)) {
      continue;
    }
    files.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      file: f,
      name: f.name,
      size: f.size,
      progress: 0,
      status: 'pending',
    });
  }
  // 首个文件自动填充标题（若标题为空）
  if (!formData.value.title && files.value.length > 0) {
    const firstName = files.value[0].name;
    formData.value.title = firstName.substring(0, firstName.lastIndexOf('.')) || firstName;
  }
}

function removeFile(idx: number): void {
  files.value.splice(idx, 1);
  // 若所有文件移除完毕，清空标题（避免残留）
  if (files.value.length === 0) {
    formData.value.title = '';
  }
}

// ===== 标签管理 =====
function addTag(): void {
  const tag = tagInput.value.trim();
  if (tag && !formData.value.tags.includes(tag)) {
    formData.value.tags.push(tag);
  }
  tagInput.value = '';
}

function removeTag(idx: number): void {
  formData.value.tags.splice(idx, 1);
}

// ===== 文件类型辅助 =====
function getFileTypeIcon(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase();
  if (ext === 'pdf') return 'file-text';
  if (ext === 'doc' || ext === 'docx') return 'file-text';
  if (ext === 'md' || ext === 'markdown') return 'file-text';
  if (ext === 'txt') return 'file-text';
  if (ext === 'ppt' || ext === 'pptx') return 'file-text';
  return 'file';
}

function getFileTypeColorClass(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase();
  if (ext === 'pdf') return 'type-pdf';
  if (ext === 'md' || ext === 'markdown') return 'type-md';
  if (ext === 'doc' || ext === 'docx') return 'type-doc';
  if (ext === 'ppt' || ext === 'pptx') return 'type-ppt';
  return 'type-default';
}

// ===== 进度/状态辅助 =====
function getProgressStatusClass(file: UploadFile): string {
  if (file.status === 'done') return 'fill-success';
  if (file.status === 'error') return 'fill-error';
  return 'fill-primary';
}

function getStatusLabel(file: UploadFile): string {
  if (file.status === 'done') return '完成';
  if (file.status === 'error') return '失败';
  if (file.status === 'uploading') return `${file.progress}%`;
  return '等待中';
}

function getStatusTextClass(file: UploadFile): string {
  if (file.status === 'done') return 'status-success';
  if (file.status === 'error') return 'status-error';
  return 'status-muted';
}

// ===== 文件大小格式化 =====
// 以 1024 为底取对数，将字节数转换为带单位的文件大小
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// ===== 上传逻辑 =====
async function handleUpload(): Promise<void> {
  if (!canSubmit.value || uploading.value) return;
  uploading.value = true;

  try {
    let okCount = 0;
    let totalWords = 0;
    let warnEmpty = 0;

    // 逐个文件上传（更新进度条），正文抽取交由后端 Tika 处理（PDF/DOC/DOCX/PPT 全覆盖）
    for (const uploadFile of files.value) {
      uploadFile.status = 'uploading';
      uploadFile.progress = 10;

      // 标题：单文件用 formData.title，多文件用各文件名（去扩展名）
      const titleForThis = files.value.length === 1
        ? formData.value.title.trim()
        : (uploadFile.name.substring(0, uploadFile.name.lastIndexOf('.')) || uploadFile.name);

      try {
        const created = await docsApi.upload(
          uploadFile.file,
          {
            title: titleForThis,
            summary: formData.value.description,
            categoryId: Number(formData.value.categoryId),
            tags: formData.value.tags.join(','),
            difficulty: Number(formData.value.difficulty) || undefined,
            status: 1,
          },
          (percent) => {
            // 进度映射：10% ~ 100%（已包含真实上传进度 + 后端解析等待）
            uploadFile.progress = 10 + Math.round((percent / 100) * 90);
          }
        );

        const words = (created as any)?.wordCount ?? 0;
        totalWords += words;
        if (words === 0) warnEmpty++;
        okCount++;
        uploadFile.progress = 100;
        uploadFile.status = 'done';
      } catch (e) {
        uploadFile.status = 'error';
        uploadFile.progress = 100;
        notify(`${uploadFile.name} 上传失败：${getApiError(e, '请稍后再试')}`, 'error');
      }
    }

    const hasError = files.value.some((f) => f.status === 'error');
    if (!hasError) {
      // 给出解析结果反馈：总字数 + 可能的扫描件/纯图片 PDF 警告
      const parts: string[] = [`成功上传 ${okCount} 个文档`];
      parts.push(`共抽取正文 ${totalWords.toLocaleString()} 字`);
      const extraMsg = parts.join(' · ');
      notify(extraMsg, 'success', 4000);
      if (warnEmpty > 0) {
        setTimeout(() => {
          notify(
            `有 ${warnEmpty} 个文档未抽到正文，可能是扫描件或纯图片 PDF。建议先使用 OCR 转可编辑文本后再上传。`,
            'warning',
            6000,
          );
        }, 1200);
      }
      setTimeout(() => router.push('/'), 1800);
    } else {
      notify('部分文件上传失败，请重试', 'warning');
    }
  } finally {
    uploading.value = false;
  }
}

function handleCancel(): void {
  files.value = [];
  formData.value = {
    title: '',
    kbId: null,
    categoryId: null,
    tags: [],
    description: '',
    difficulty: null,
  };
  if (fileInputRef.value) {
    fileInputRef.value.value = '';
  }
}

// ===== 生命周期 =====
onMounted(async () => {
  try {
    const tree = await categoriesApi.tree();
    allCategories.value = tree || [];
  } catch (e) {
    console.warn(getApiError(e, '分类加载失败'));
    allCategories.value = [];
  }
});
</script>

<style scoped>
/* ===== 页面容器 ===== */
.upload-doc-page {
  display: flex;
  flex-direction: column;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.page-title {
  margin-bottom: 24px;
}

/* ===== 双栏布局（设计稿：1fr 360px） ===== */
.upload-layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 24px;
  align-items: flex-start;
}
@media (max-width: 1024px) {
  .upload-layout { grid-template-columns: 1fr; }
}

.upload-left {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ===== 拖拽上传区（设计稿 upload-zone） ===== */
.upload-zone {
  border: 2px dashed var(--kf-border);
  border-radius: var(--kf-radius-lg);
  padding: 48px 24px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, transform 0.15s;
}
.upload-zone:hover {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.03);
}
.upload-zone:active {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.06);
  transform: scale(0.995);
}
.upload-zone:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-color: var(--kf-primary);
}
.upload-zone.dragging {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.06);
  transform: scale(1.01);
}
.upload-zone-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--kf-radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
}
.upload-zone-title {
  font-size: var(--kf-fs-body-lg);
  line-height: var(--kf-lh-body-lg);
  font-weight: 500;
  color: var(--kf-foreground);
  margin-bottom: var(--kf-space-1);
}
.upload-zone-hint {
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-muted-foreground);
}

.hidden-file-input {
  display: none;
}

/* ===== 已上传文件列表（含进度条） ===== */
.file-list-card {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  overflow: hidden;
}
.file-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  padding: 12px 16px;
  border-bottom: 1px solid var(--kf-border);
}
.file-list-header .kf-h3 {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-list-count {
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.file-list-body {
  display: flex;
  flex-direction: column;
}
.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kf-border);
}
.file-item:last-child { border-bottom: none; }

/* 文件类型图标 */
.file-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--kf-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.type-pdf { background: rgba(239, 68, 68, 0.1); color: var(--kf-destructive); }
.type-md { background: rgba(16, 185, 129, 0.1); color: var(--kf-accent); }
.type-doc { background: rgba(59, 111, 224, 0.08); color: var(--kf-primary); }
.type-ppt { background: rgba(245, 158, 11, 0.1); color: var(--kf-warning); }
.type-default { background: rgba(59, 111, 224, 0.08); color: var(--kf-primary); }

/* 文件信息 */
.file-info {
  flex: 1;
  min-width: 0;
}
.file-name {
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
  margin-top: 2px;
}

/* 进度条 */
.file-progress {
  width: 128px;
  flex-shrink: 0;
}
.upload-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--kf-muted);
  overflow: hidden;
}
.upload-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s;
}
.fill-primary { background: var(--kf-primary); }
.fill-success { background: var(--kf-accent); }
.fill-error { background: var(--kf-destructive); }

/* 状态文字 */
.file-status {
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  flex-shrink: 0;
  min-width: 48px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.status-success { color: var(--kf-accent); }
.status-error { color: var(--kf-destructive); }
.status-muted { color: var(--kf-muted-foreground); }

/* 删除按钮 */
.file-remove-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kf-muted-foreground);
  border-radius: 4px;
  flex-shrink: 0;
  transition: all 0.15s;
}
.file-remove-btn:hover {
  background: rgba(239, 68, 68, 0.08);
  color: var(--kf-destructive);
}
.file-remove-btn:active {
  background: rgba(239, 68, 68, 0.16);
  color: var(--kf-destructive);
  transform: scale(0.94);
}
.file-remove-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  color: var(--kf-destructive);
}

/* ===== 右侧：上传设置表单 ===== */
.upload-right {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  padding: 20px;
  position: sticky;
  top: 80px;
}
.form-title {
  margin-bottom: 20px;
}
.form-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.form-row {
  display: flex;
  flex-direction: column;
}
.form-label {
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
  margin-bottom: 6px;
}
.required-mark {
  color: var(--kf-destructive);
}
.form-hint {
  margin-top: var(--kf-space-1);
  font-size: var(--kf-fs-xs);
  line-height: var(--kf-lh-xs);
  color: var(--kf-muted-foreground);
}
.form-input,
.form-select {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  background: var(--kf-background);
  color: var(--kf-foreground);
  border: 1px solid var(--kf-border);
  outline: none;
  transition: border-color 0.15s;
}
.form-select {
  padding-right: 32px;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236B7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
}
.form-input:focus:not(.kf-form-control),
.form-select:focus { border-color: var(--kf-ring); }

.form-textarea:not(.kf-form-control) {
  border-radius: var(--kf-radius-sm);
  background: var(--kf-background);
  border: 1px solid var(--kf-border);
  outline: none;
  transition: border-color 0.15s;
}

.form-textarea {
  width: 100%;
  padding: 8px 12px;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-foreground);
  resize: none;
  font-family: inherit;
}
.form-textarea:not(.kf-form-control):focus { border-color: var(--kf-ring); }

/* 标签输入 */
.tags-input {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 10px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-field-surface);
  min-height: 36px;
  border: 0;
}
.tags-input:focus-within {
  background: var(--kf-field-active); }
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  font-weight: 500;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
  max-width: 100%;
}
.tag-remove {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kf-primary);
  padding: 0;
  display: inline-flex;
  align-items: center;
  opacity: 0.7;
  border-radius: var(--kf-radius-sm);
  flex-shrink: 0;
  transition: opacity 0.15s, transform 0.15s;
}
.tag-remove:hover { opacity: 1; }
.tag-remove:active { opacity: 1; transform: scale(0.9); }
.tag-remove:focus-visible {
  opacity: 1;
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}
.tag-text-input:not(.kf-form-control) {
  height: 24px;
  background: transparent;
  border: none;
  outline: none;
}
.tag-text-input {
  flex: 1;
  min-width: 80px;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.tag-text-input::placeholder { color: var(--kf-muted-foreground); }

/* 底部操作按钮 */
.form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}
.btn-primary,
.btn-secondary {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  min-width: 0;
  white-space: nowrap;
  transition: all 0.15s;
}
.btn-primary:focus-visible,
.btn-secondary:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}
.btn-primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  border: none;
}
.btn-primary:hover { opacity: 0.9; }
.btn-primary:active:not(:disabled) { opacity: 0.9; transform: scale(0.98); }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary {
  background: var(--kf-card);
  color: var(--kf-sidebar-foreground);
  border: 1px solid var(--kf-border);
}
.btn-secondary:hover { background: var(--kf-muted); }
.btn-secondary:active { background: var(--kf-muted); transform: scale(0.98); }

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .file-item { flex-wrap: wrap; }
  .file-progress { width: 100%; order: 3; }
  .file-status { order: 4; min-width: auto; }
  .file-remove-btn { order: 5; }
}
</style>

<style scoped>
.tags-input:has(input:focus-visible){outline:0}.tags-input input.kf-form-control,.tags-input input.kf-form-control:focus,.tags-input input.kf-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}
</style>
