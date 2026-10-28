<template>
  <WorkbenchShell stage="capture" :revision="revision" @ready="initialize">
    <template #actions>
      <button
        type="button"
        data-test="capture-quick-toggle"
        class="kf-btn capture-action-btn"
        :class="{ 'is-active': quickOpen }"
        :aria-expanded="quickOpen"
        aria-controls="capture-quick-panel"
        @click="toggleQuick"
      >
        <Icon name="pencil" :size="16" aria-hidden="true" />
        快速记录
        <span v-if="quickDirty && !quickOpen" class="capture-draft-dot" aria-label="有未保存的快速记录草稿"></span>
      </button>
      <button type="button" data-test="capture-new" class="kf-btn kf-btn-primary capture-action-btn" @click="openCreate">
        <Icon name="plus" :size="16" aria-hidden="true" /> 新建条目
      </button>
    </template>

    <div class="capture-page">
      <Transition name="capture-reveal">
        <form
          v-if="quickOpen"
          id="capture-quick-panel"
          data-test="capture-quick-panel"
          class="capture-quick"
          @submit.prevent="quickSave"
          @keydown.esc="quickOpen = false"
        >
          <div class="capture-quick-head">
            <div class="capture-quick-heading">
              <span class="capture-quick-icon"><Icon name="pencil" :size="16" aria-hidden="true" /></span>
              <div>
                <strong>快速记录</strong>
                <p>随手记下灵感、问题或摘录，保存后再慢慢整理。</p>
              </div>
            </div>
            <button type="button" class="capture-ghost-btn capture-quick-collapse" @click="quickOpen = false">
              <Icon name="chevron-up" :size="14" aria-hidden="true" /> 收起
            </button>
          </div>

          <div class="capture-quick-primary">
            <div class="kf-field">
              <input
                v-model="quick.title"
                data-test="capture-quick-title"
                class="kf-input kf-form-control"
                maxlength="200"
                placeholder="标题（可选，默认取正文第一行）"
                aria-label="快速记录标题"
              />
            </div>
            <div class="kf-field">
              <textarea
                ref="quickInput"
                v-model="quick.content"
                data-test="capture-quick-input"
                class="kf-textarea capture-quick-textarea kf-form-control"
                rows="4"
                placeholder="输入想法、问题、摘录或链接…"
                aria-label="快速记录内容"
                @input="quickError = ''"
              ></textarea>
            </div>
          </div>

          <div class="capture-quick-meta">
            <div class="capture-quick-field">
              <span class="capture-label">来源</span>
              <Select v-model="quick.sourceType" :options="SOURCE_OPTIONS" :searchable="false" aria-label="快速记录来源" />
            </div>
            <div class="capture-quick-field">
              <span class="capture-label">分类</span>
              <CategoryTreeSelect
                v-model="quick.categoryId"
                :categories="categoryTree"
                placeholder="选择分类"
                empty-label="未分类"
                aria-label="快速记录分类"
              />
            </div>
            <div class="capture-quick-field">
              <span class="capture-label">标签</span>
              <div class="kf-field">
                <input v-model="quick.tags" class="kf-input kf-form-control" placeholder="逗号分隔，可选" aria-label="快速记录标签" />
              </div>
            </div>
            <div class="capture-quick-field">
              <span class="capture-label">来源链接</span>
              <div class="kf-field">
                <input
                  v-model="quick.sourceUrl"
                  class="kf-input kf-form-control"
                  inputmode="url"
                  placeholder="https://…（可选）"
                  aria-label="快速记录来源链接"
                  @input="quickError = ''"
                />
              </div>
            </div>
          </div>

          <p v-if="quickError" role="alert" class="capture-field-error capture-quick-error">{{ quickError }}</p>

          <div class="capture-quick-foot">
            <button type="button" class="capture-ghost-btn" :disabled="quickSaving || !quickDirty" @click="clearQuick">清空</button>
            <button type="submit" data-test="capture-quick-save" class="capture-primary-btn" :disabled="quickSaving">
              <Icon v-if="quickSaving" name="loader" :size="15" class="capture-spin" aria-hidden="true" />
              {{ quickSaving ? '保存中…' : '保存到收集箱' }}
            </button>
          </div>
        </form>
      </Transition>

      <div class="capture-toolbar">
        <div class="kf-filter-group capture-status-tabs" role="group" aria-label="按整理状态筛选">
          <button
            v-for="tab in statusTabs"
            :key="tab.value || 'ALL'"
            type="button"
            class="kf-filter-btn"
            :class="{ active: activeStatus === tab.value }"
            :aria-pressed="activeStatus === tab.value"
            :data-test="`capture-status-${tab.value || 'ALL'}`"
            @click="setStatus(tab.value)"
          >
            {{ tab.label }}<span class="filter-count">{{ counts[tab.value || 'ALL'] }}</span>
          </button>
        </div>

        <div class="capture-toolbar-right">
          <span v-if="loading" class="capture-sync" role="status">同步中…</span>
          <div class="kf-field capture-search">
            <Icon name="search" :size="16" class="capture-search-icon" aria-hidden="true" />
            <input
              v-model="keyword"
              data-test="capture-search"
              class="kf-input capture-search-input kf-form-control"
              placeholder="搜索标题、内容或标签…"
              aria-label="搜索收集条目"
              @input="page = 1"
            />
          </div>
          <div class="capture-filter">
            <Select
              :model-value="activeCategory ?? null"
              :options="categoryFilterOptions"
              :searchable="false"
              aria-label="按分类筛选"
              @update:model-value="onCategoryFilterChange"
            />
          </div>
          <div class="capture-filter capture-filter-sort">
            <Select v-model="sort" :options="sortOptions" :searchable="false" aria-label="排序方式" @update:model-value="page = 1" />
          </div>
          <div class="capture-view" role="group" aria-label="显示模式">
            <button
              type="button"
              class="capture-view-btn"
              :class="{ active: viewMode === 'list' }"
              :aria-pressed="viewMode === 'list'"
              aria-label="列表模式"
              data-test="capture-view-list"
              @click="viewMode = 'list'"
            >
              <Icon name="list" :size="16" aria-hidden="true" />
            </button>
            <button
              type="button"
              class="capture-view-btn"
              :class="{ active: viewMode === 'card' }"
              :aria-pressed="viewMode === 'card'"
              aria-label="卡片模式"
              data-test="capture-view-card"
              @click="viewMode = 'card'"
            >
              <Icon name="grid" :size="16" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>

      <div v-if="error" data-test="capture-error" role="alert" class="workbench-inline-error">
        {{ error }}<button type="button" class="kf-btn" @click="load">重试</button>
      </div>

      <div v-if="selectedIds.length" class="capture-batch" role="status">
        <strong>已选择 {{ selectedIds.length }} 项</strong>
        <div class="capture-batch-actions">
          <button type="button" class="capture-ghost-btn" :disabled="batchBusy" @click="batchStatus('PROCESSED')">
            <Icon name="check" :size="14" aria-hidden="true" /> 标记已整理
          </button>
          <button type="button" class="capture-ghost-btn" :disabled="batchBusy" @click="batchStatus('ARCHIVED')">
            <Icon name="archive" :size="14" aria-hidden="true" /> 归档
          </button>
          <button type="button" class="capture-ghost-btn capture-danger-btn" :disabled="batchBusy" @click="batchRemove">
            <Icon name="trash-2" :size="14" aria-hidden="true" /> 删除
          </button>
          <button type="button" class="capture-ghost-btn" @click="selectedIds = []">取消选择</button>
        </div>
      </div>

      <div v-if="loading && !rawItems.length" class="workbench-empty" role="status">正在加载收集内容…</div>
      <div v-else-if="!rawItems.length" class="capture-empty">
        <span class="capture-empty-icon"><Icon name="inbox" :size="28" aria-hidden="true" /></span>
        <h2 class="kf-text-card-title">{{ error ? '收集内容暂时无法读取' : activeCategory ? '这个分类还没有条目' : '收集箱还是空的' }}</h2>
        <p>{{ error ? '请检查连接后重试，已有内容不会丢失。' : '先记录一个想法，之后再慢慢整理。' }}</p>
        <div v-if="!error" class="capture-empty-actions">
          <button type="button" class="kf-btn" @click="toggleQuick"><Icon name="pencil" :size="15" aria-hidden="true" /> 快速记录</button>
          <button type="button" class="kf-btn kf-btn-primary" @click="openCreate"><Icon name="plus" :size="15" aria-hidden="true" /> 新建条目</button>
        </div>
      </div>
      <div v-else-if="!total" class="capture-empty">
        <span class="capture-empty-icon"><Icon name="filter" :size="28" aria-hidden="true" /></span>
        <h2 class="kf-text-card-title">没有符合条件的条目</h2>
        <p>可以换个关键词或状态，也可以清除筛选查看全部内容。</p>
        <div class="capture-empty-actions">
          <button type="button" class="kf-btn" @click="clearFilters">清除筛选</button>
        </div>
      </div>
      <template v-else>
        <div v-if="viewMode === 'list'" class="capture-list" data-test="capture-list" :aria-busy="loading || undefined">
          <div class="capture-list-head">
            <Checkbox
              :model-value="allPageSelected"
              :indeterminate="someSelected && !allPageSelected"
              @update:model-value="togglePageSelection"
            />
            <span>条目</span>
            <span>标签</span>
            <span>来源</span>
            <span>更新时间</span>
            <span class="capture-head-actions">操作</span>
          </div>
          <div
            v-for="item in pageItems"
            :key="item.id"
            class="capture-row"
            :class="{ 'is-selected': selectedIds.includes(item.id) }"
          >
            <Checkbox :model-value="selectedIds.includes(item.id)" @update:model-value="(value) => toggleSelect(item.id, value)" />
            <div class="capture-main">
              <div class="capture-title-line">
                <button type="button" class="capture-title" data-test="capture-edit" @click="openEdit(item)">{{ item.title }}</button>
                <span v-if="!activeStatus" class="capture-status" :class="`is-${(item.status || 'INBOX').toLowerCase()}`">{{ statusLabel(item.status) }}</span>
                <span v-if="item.starred" class="capture-star" aria-label="已标星"><Icon name="star" :size="13" aria-hidden="true" /></span>
              </div>
              <p class="capture-excerpt">{{ excerpt(item) }}</p>
              <div class="capture-mobile-meta">
                <span v-for="tag in splitTags(item.tags)" :key="tag" class="capture-tag">{{ tag }}</span>
                <span class="capture-mobile-source"><Icon :name="sourceIcon(item.sourceType)" :size="13" aria-hidden="true" />{{ sourceLabel(item.sourceType) }}</span>
                <time>{{ formatDate(item.updateTime || item.createTime) }}</time>
              </div>
            </div>
            <div class="capture-tags">
              <span v-for="tag in splitTags(item.tags)" :key="tag" class="capture-tag">{{ tag }}</span>
              <span v-if="!splitTags(item.tags).length" class="capture-muted">—</span>
            </div>
            <div class="capture-source">
              <Icon :name="sourceIcon(item.sourceType)" :size="14" aria-hidden="true" />
              <span>{{ sourceLabel(item.sourceType) }}</span>
            </div>
            <div class="capture-time">{{ formatDate(item.updateTime || item.createTime) }}</div>
            <div class="capture-actions">
              <button
                type="button"
                data-test="capture-to-note"
                class="capture-icon-btn capture-row-note-btn"
                title="整理为笔记"
                aria-label="整理为笔记"
                @click="toNote(item)"
              >
                <Icon name="notebook-pen" :size="16" aria-hidden="true" />
              </button>
              <button type="button" class="capture-icon-btn" title="更多操作" aria-label="更多操作" @click="openMenu($event, item)">
                <Icon name="more-horizontal" :size="16" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>

        <div v-else class="capture-cards" data-test="capture-cards">
          <article
            v-for="item in pageItems"
            :key="item.id"
            class="capture-card"
            :class="{ 'is-selected': selectedIds.includes(item.id) }"
          >
            <div class="capture-card-top">
              <Checkbox :model-value="selectedIds.includes(item.id)" @update:model-value="(value) => toggleSelect(item.id, value)" />
              <span v-if="!activeStatus" class="capture-status" :class="`is-${(item.status || 'INBOX').toLowerCase()}`">{{ statusLabel(item.status) }}</span>
              <span v-if="item.starred" class="capture-star" aria-label="已标星"><Icon name="star" :size="13" aria-hidden="true" /></span>
              <button type="button" class="capture-icon-btn" title="更多操作" aria-label="更多操作" @click="openMenu($event, item)">
                <Icon name="more-horizontal" :size="16" aria-hidden="true" />
              </button>
            </div>
            <button type="button" class="capture-card-title" @click="openEdit(item)">{{ item.title }}</button>
            <p class="capture-card-excerpt">{{ excerpt(item, 180) }}</p>
            <div class="capture-tags">
              <span v-for="tag in splitTags(item.tags)" :key="tag" class="capture-tag">{{ tag }}</span>
            </div>
            <div class="capture-card-foot">
              <span class="capture-card-source"><Icon :name="sourceIcon(item.sourceType)" :size="13" aria-hidden="true" />{{ sourceLabel(item.sourceType) }}</span>
              <time>{{ formatDate(item.updateTime || item.createTime) }}</time>
            </div>
            <button type="button" class="capture-card-note" @click="toNote(item)">
              <Icon name="notebook-pen" :size="14" aria-hidden="true" /> 整理为笔记
            </button>
          </article>
        </div>

        <div class="capture-pagination">
          <label class="capture-page-size">
            每页
            <select v-model.number="pageSize" class="kf-select kf-select-sm kf-form-control" aria-label="每页条数" @change="page = 1">
              <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }}</option>
            </select>
            条
          </label>
          <Pagination :page-num="safePage" :page-size="pageSize" :total="total" :show-info="true" @change="page = $event" />
        </div>
      </template>
    </div>

    <AppDialog
      :open="dialogOpen"
      :title="editingId ? '编辑收集条目' : '新增收集条目'"
      description="记录你的灵感、问题、文章、视频或文件，后续整理为笔记。"
      icon="inbox"
      width="640px"
      scrollable
      :loading="dialogSaving"
      :error="errors._form"
      @cancel="requestDialogClose"
    >
      <form class="capture-form" data-test="capture-dialog" @submit.prevent="saveEntry">
        <div class="capture-form-field">
          <label class="capture-label" for="capture-entry-title">标题 <span class="capture-req">*</span></label>
          <div class="kf-field" :class="{ 'kf-field--error': errors.title }">
            <input
              id="capture-entry-title"
              ref="titleInput"
              v-model="form.title"
              data-test="capture-title"
              class="kf-input kf-form-control"
              maxlength="200"
              placeholder="请输入标题，例如：RAG 在企业知识库中的实践"
              :aria-invalid="errors.title ? true : undefined"
              @input="errors.title = ''"
            />
          </div>
          <div class="capture-field-foot">
            <span v-if="errors.title" class="capture-field-error">{{ errors.title }}</span>
            <span v-else class="capture-field-hint">最多 200 个字符</span>
            <span class="capture-counter">{{ form.title.length }}/200</span>
          </div>
        </div>

        <div class="capture-form-field">
          <span class="capture-label">内容模式</span>
          <div class="capture-modes" role="group" aria-label="内容模式">
            <button
              v-for="mode in MODE_OPTIONS"
              :key="mode.value"
              type="button"
              class="capture-mode"
              :class="{ active: form.mode === mode.value }"
              :aria-pressed="form.mode === mode.value"
              @click="form.mode = mode.value"
            >
              <Icon :name="mode.icon" :size="15" aria-hidden="true" /> {{ mode.label }}
            </button>
          </div>
        </div>

        <div v-if="form.mode === 'FILE'" class="capture-form-field">
          <span class="capture-label">附件</span>
          <input ref="fileInput" type="file" class="capture-file-native" accept=".pdf,.doc,.docx,.ppt,.pptx,.txt,.md" @change="onFileChange" />
          <div class="capture-file" :class="{ 'is-error': errors.file }">
            <template v-if="form.file">
              <Icon name="file-text" :size="20" aria-hidden="true" />
              <div class="capture-file-info">
                <strong>{{ form.file.name }}</strong>
                <small>{{ formatSize(form.file.size) }}</small>
              </div>
              <button type="button" class="capture-icon-btn" aria-label="移除文件" @click="clearFile">
                <Icon name="x" :size="15" aria-hidden="true" />
              </button>
            </template>
            <template v-else-if="editingDocId">
              <Icon name="file-text" :size="20" aria-hidden="true" />
              <div class="capture-file-info">
                <strong>已关联文档 #{{ editingDocId }}</strong>
                <small>选择新文件将替换该附件，原文档不会被删除</small>
              </div>
              <button type="button" class="capture-ghost-btn" @click="pickFile">替换文件</button>
            </template>
            <template v-else>
              <Icon name="upload" :size="20" aria-hidden="true" />
              <div class="capture-file-info">
                <strong>选择文件</strong>
                <small>PDF / Word / PPT / TXT / Markdown，最大 50MB</small>
              </div>
              <button type="button" class="capture-ghost-btn" @click="pickFile">选择文件</button>
            </template>
          </div>
          <p v-if="dialogSaving && form.file" class="capture-field-hint">正在上传：{{ uploadProgress }}%</p>
          <p v-if="errors.file" class="capture-field-error">{{ errors.file }}</p>
        </div>

        <div v-if="form.mode !== 'FILE'" class="capture-form-field">
          <label class="capture-label" for="capture-entry-content">{{ form.mode === 'LINK' ? '补充说明' : '正文' }}</label>
          <div class="kf-field">
            <textarea
              id="capture-entry-content"
              v-model="form.content"
              data-test="capture-content"
              class="kf-textarea capture-content kf-form-control"
              rows="5"
              :placeholder="form.mode === 'LINK' ? '可选，记录链接里的关键内容或摘要…' : '输入或粘贴内容，支持长文、代码片段与中英文…'"
            ></textarea>
          </div>
          <div class="capture-field-foot">
            <span class="capture-field-hint">{{ form.mode === 'LINK' ? '可选，用于保存链接内容摘要' : '支持粘贴长文、代码片段与中英文内容' }}</span>
            <span class="capture-counter">{{ form.content.length }} 字</span>
          </div>
        </div>

        <div class="capture-form-field">
          <label class="capture-label" for="capture-entry-url">
            来源链接 <span v-if="form.mode === 'LINK'" class="capture-req">*</span>
          </label>
          <div class="kf-field" :class="{ 'kf-field--error': errors.sourceUrl }">
            <input
              id="capture-entry-url"
              v-model="form.sourceUrl"
              data-test="capture-source-url"
              class="kf-input kf-form-control"
              inputmode="url"
              placeholder="https://…"
              :aria-invalid="errors.sourceUrl ? true : undefined"
              @input="errors.sourceUrl = ''"
            />
          </div>
          <span v-if="errors.sourceUrl" class="capture-field-error">{{ errors.sourceUrl }}</span>
          <span v-else class="capture-field-hint">
            {{ form.mode === 'LINK' ? '仅支持 http/https 链接，打开前请确认来源可信' : '可选，仅支持 http/https 链接' }}
          </span>
        </div>

        <div class="capture-form-grid">
          <div class="capture-form-field">
            <span class="capture-label">来源类型</span>
            <Select v-model="form.sourceType" :options="SOURCE_OPTIONS" :searchable="false" aria-label="来源类型" />
          </div>
          <div class="capture-form-field">
            <span class="capture-label">分类</span>
            <CategoryTreeSelect
              v-model="form.categoryId"
              :categories="categoryTree"
              placeholder="选择分类"
              empty-label="未分类"
              aria-label="选择分类"
            />
          </div>
        </div>

        <div class="capture-form-field">
          <label class="capture-label" for="capture-entry-tags">标签</label>
          <div class="kf-field capture-tags-field">
            <Icon name="hash" :size="15" class="capture-tags-icon" aria-hidden="true" />
            <input
              id="capture-entry-tags"
              v-model="tagInput"
              class="kf-input kf-form-control"
              placeholder="输入标签后按 Enter 或逗号"
              @keydown="onTagKey"
              @blur="addTag"
            />
          </div>
          <div v-if="form.tags.length" class="capture-tags capture-tag-list">
            <span v-for="(tag, index) in form.tags" :key="tag" class="capture-tag">
              {{ tag }}
              <button type="button" class="capture-tag-remove" :aria-label="`移除标签 ${tag}`" @click="removeTag(index)">
                <Icon name="x" :size="11" aria-hidden="true" />
              </button>
            </span>
          </div>
          <span v-if="errors.tags" class="capture-field-error">{{ errors.tags }}</span>
          <span v-else class="capture-field-hint">标签总长度不超过 500 个字符</span>
        </div>

        <WorkbenchRelations v-if="editingId" :capture-id="editingId" :revision="revision" />
      </form>
      <template #footer>
        <div class="capture-dialog-foot">
          <div class="capture-continue">
            <Checkbox v-if="!editingId" v-model="continueAdd" label="保存后继续新增" data-test="capture-continue-add" />
          </div>
          <div class="capture-dialog-actions">
            <button type="button" class="capture-ghost-btn" :disabled="dialogSaving" @click="requestDialogClose">取消</button>
            <button type="button" data-test="capture-dialog-save" class="capture-primary-btn" :disabled="dialogSaving" @click="saveEntry">
              <Icon v-if="dialogSaving" name="loader" :size="15" class="capture-spin" aria-hidden="true" />
              {{ dialogSaving ? '保存中…' : editingId ? '保存修改' : '保存到收集箱' }}
            </button>
          </div>
        </div>
      </template>
    </AppDialog>

    <ContextMenu :open="menuOpen" :x="menuX" :y="menuY" :items="menuItems" label="收集条目操作" @close="menuOpen = false" />
  </WorkbenchShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import WorkbenchShell from '@/components/workbench/WorkbenchShell.vue'
import WorkbenchRelations from '@/components/workbench/WorkbenchRelations.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import ContextMenu from '@/components/ui/ContextMenu.vue'
import Icon from '@/components/ui/Icon.vue'
import Pagination from '@/components/ui/Pagination.vue'
import Select from '@/components/ui/Select.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { docsApi } from '@/api/docs'
import {
  listCaptures,
  createCapture,
  updateCapture,
  deleteCapture,
  setCaptureStatus,
  toggleCaptureStar,
  getCategoryTree,
} from '@/api/workbench'
import type { WbCapture, WbCapturePayload, CategoryVO, CategoryTreeNodeVO } from '@/api/types'

type SortKey = 'updated' | 'created' | 'title'
type ViewMode = 'list' | 'card'
type EntryMode = 'TEXT' | 'LINK' | 'FILE'
interface MenuAction {
  key: string
  label: string
  icon?: string
  danger?: boolean
  disabled?: boolean
  separator?: boolean
  selected?: boolean
  onSelect?: () => unknown
}

const router = useRouter()
const revision = ref(0)
const rawItems = ref<WbCapture[]>([])
const loading = ref(true)
const error = ref('')
const keyword = ref('')
const activeStatus = ref('INBOX')
const activeCategory = ref<number>()
const sort = ref<SortKey>('updated')
const viewMode = ref<ViewMode>('list')
const page = ref(1)
const pageSize = ref(10)
const selectedIds = ref<number[]>([])
const batchBusy = ref(false)
const PAGE_SIZES = [10, 20, 50]
const statusTabs = [
  { label: '待整理', value: 'INBOX' },
  { label: '已整理', value: 'PROCESSED' },
  { label: '已归档', value: 'ARCHIVED' },
  { label: '全部', value: '' },
]

const categoryTree = ref<CategoryVO[]>([])
const SOURCE_OPTIONS = [
  { label: '手记', value: 'MANUAL' },
  { label: '文档 / PDF', value: 'DOC' },
  { label: '网页文章', value: 'WEB' },
  { label: 'AI 生成', value: 'AI' },
  { label: '导入内容', value: 'IMPORT' },
]
const SOURCE_LABELS: Record<string, string> = { MANUAL: '手记', DOC: '文档 / PDF', WEB: '网页文章', AI: 'AI 生成', IMPORT: '导入内容' }
const MODE_OPTIONS: Array<{ value: EntryMode; label: string; icon: string }> = [
  { value: 'TEXT', label: '文本内容', icon: 'file-text' },
  { value: 'LINK', label: '链接 / 网页', icon: 'link' },
  { value: 'FILE', label: '上传文件', icon: 'upload' },
]
const sortOptions = [
  { label: '按更新时间', value: 'updated' },
  { label: '按创建时间', value: 'created' },
  { label: '按标题', value: 'title' },
]

/* ============================ 快速记录 ============================ */
const quickOpen = ref(false)
const quickSaving = ref(false)
const quickError = ref('')
const quickInput = ref<HTMLTextAreaElement | null>(null)
const quick = reactive({
  title: '',
  content: '',
  sourceType: 'MANUAL',
  sourceUrl: '',
  categoryId: undefined as number | undefined,
  tags: '',
})
const quickDirty = computed(() => !!(quick.title.trim() || quick.content.trim() || quick.sourceUrl.trim() || quick.tags.trim()))

/* ============================ 新建 / 编辑弹窗 ============================ */
const dialogOpen = ref(false)
const dialogSaving = ref(false)
const editingId = ref<number>()
const editingDocId = ref<number>()
const continueAdd = ref(false)
const titleInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const tagInput = ref('')
const uploadProgress = ref(0)
const dialogBaseline = ref('')
const form = reactive<{
  mode: EntryMode
  title: string
  content: string
  sourceType: string
  sourceUrl: string
  categoryId?: number
  tags: string[]
  file: File | null
}>({
  mode: 'TEXT',
  title: '',
  content: '',
  sourceType: 'MANUAL',
  sourceUrl: '',
  categoryId: undefined,
  tags: [],
  file: null,
})
const errors = reactive<{ title?: string; content?: string; sourceUrl?: string; tags?: string; file?: string; _form?: string }>({})

/* ============================ 菜单 ============================ */
const menuOpen = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuItem = ref<WbCapture>()

/* ============================ 列表派生数据 ============================ */
const counts = computed<Record<string, number>>(() => {
  const result: Record<string, number> = { INBOX: 0, PROCESSED: 0, ARCHIVED: 0, ALL: rawItems.value.length }
  for (const item of rawItems.value) {
    const status = item.status || 'INBOX'
    if (status in result) result[status] += 1
  }
  return result
})

const keywordMatched = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return rawItems.value
  return rawItems.value.filter((item) =>
    `${item.title}\n${item.content || ''}\n${item.tags || ''}`.toLowerCase().includes(value),
  )
})

const sortedItems = computed(() => {
  const items = keywordMatched.value.filter((item) => !activeStatus.value || (item.status || 'INBOX') === activeStatus.value)
  return [...items].sort((a, b) => {
    if (sort.value === 'title') return a.title.localeCompare(b.title, 'zh-CN')
    const aTime = timeOf(sort.value === 'created' ? a.createTime : a.updateTime || a.createTime)
    const bTime = timeOf(sort.value === 'created' ? b.createTime : b.updateTime || b.createTime)
    return bTime - aTime
  })
})

const total = computed(() => sortedItems.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const safePage = computed(() => Math.min(Math.max(1, page.value), totalPages.value))
const pageItems = computed(() => {
  const start = (safePage.value - 1) * pageSize.value
  return sortedItems.value.slice(start, start + pageSize.value)
})
const allPageSelected = computed(() => pageItems.value.length > 0 && pageItems.value.every((item) => selectedIds.value.includes(item.id)))
const someSelected = computed(() => selectedIds.value.length > 0)

const flatCategories = computed(() => {
  const result: Array<{ id: number; name: string; depth: number }> = []
  const walk = (nodes: CategoryVO[], depth: number) => {
    for (const node of nodes) {
      result.push({ id: node.id, name: node.name, depth })
      if (node.children?.length) walk(node.children, depth + 1)
    }
  }
  walk(categoryTree.value, 0)
  return result
})
const categoryFilterOptions = computed(() => [
  { label: '全部分类', value: null as number | null },
  ...flatCategories.value.map((item) => ({ label: `${'　'.repeat(item.depth)}${item.name}`, value: item.id as number | null })),
])

/* ============================ 数据加载 ============================ */
let listSequence = 0
async function load() {
  const sequence = ++listSequence
  loading.value = true
  error.value = ''
  try {
    const data = await listCaptures({ categoryId: activeCategory.value })
    if (sequence !== listSequence) return
    rawItems.value = data
    const visible = new Set(data.map((item) => item.id))
    selectedIds.value = selectedIds.value.filter((id) => visible.has(id))
  } catch (e) {
    if (sequence === listSequence) error.value = getApiError(e, '收集内容加载失败，请重试')
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}

async function loadCategories() {
  try {
    categoryTree.value = toCategoryVOs(await getCategoryTree())
  } catch {
    categoryTree.value = []
    notify('分类暂不可用，可以先记录内容', 'warning')
  }
}

function toCategoryVOs(nodes: CategoryTreeNodeVO[]): CategoryVO[] {
  return nodes.map((node) => ({
    id: node.id,
    name: node.name,
    parentId: node.parentId,
    children: node.children?.length ? toCategoryVOs(node.children) : undefined,
  }))
}

function initialize() {
  void load()
  void loadCategories()
}

async function afterMutation() {
  revision.value += 1
  await load()
  page.value = Math.min(page.value, totalPages.value)
}

function revealCreated() {
  activeStatus.value = 'INBOX'
  keyword.value = ''
  activeCategory.value = undefined
  page.value = 1
}

/* ============================ 筛选与视图 ============================ */
function setStatus(value: string) {
  activeStatus.value = value
  page.value = 1
}
function onCategoryFilterChange(value: unknown) {
  activeCategory.value = typeof value === 'number' ? value : undefined
  page.value = 1
  void load()
}
function clearFilters() {
  keyword.value = ''
  activeCategory.value = undefined
  activeStatus.value = ''
  page.value = 1
  void load()
}

/* ============================ 快速记录交互 ============================ */
async function toggleQuick() {
  quickOpen.value = !quickOpen.value
  if (quickOpen.value) {
    quickError.value = ''
    await nextTick()
    requestAnimationFrame(() => quickInput.value?.focus())
  }
}

async function quickSave() {
  if (quickSaving.value) return
  const content = quick.content.trim()
  if (!content) {
    quickError.value = '请先输入要记录的内容'
    quickInput.value?.focus()
    return
  }
  const rawUrl = quick.sourceUrl.trim()
  if (rawUrl && !isHttpUrl(rawUrl)) {
    quickError.value = '来源链接仅支持有效的 http/https 地址'
    return
  }
  let title = quick.title.trim()
  if (!title) {
    title = content.split('\n').map((line) => line.trim()).find(Boolean) || '快速记录'
    title = title.slice(0, 200)
  }
  quickSaving.value = true
  quickError.value = ''
  try {
    await createCapture({
      title,
      content,
      sourceType: quick.sourceType,
      sourceUrl: normalizeHttpUrl(rawUrl) || undefined,
      categoryId: quick.categoryId,
      tags: splitTags(quick.tags).join(',') || undefined,
    })
    quick.title = ''
    quick.content = ''
    quick.sourceUrl = ''
    quick.tags = ''
    revealCreated()
    await afterMutation()
    notify('已收进收集箱', 'success')
    await nextTick()
    requestAnimationFrame(() => quickInput.value?.focus())
  } catch (e) {
    quickError.value = getApiError(e, '保存失败，输入已保留')
  } finally {
    quickSaving.value = false
  }
}

async function clearQuick() {
  if (!quickDirty.value) return
  const ok = await confirmDialog('清空快速记录中尚未保存的内容？', {
    title: '清空草稿？',
    confirmText: '清空',
    cancelText: '保留',
    variant: 'danger',
    icon: 'trash-2',
  })
  if (!ok) return
  quick.title = ''
  quick.content = ''
  quick.sourceUrl = ''
  quick.tags = ''
  quickError.value = ''
  await nextTick()
  quickInput.value?.focus()
}

/* ============================ 新建 / 编辑弹窗交互 ============================ */
function snapshot() {
  return JSON.stringify({
    mode: form.mode,
    title: form.title,
    content: form.content,
    sourceType: form.sourceType,
    sourceUrl: form.sourceUrl,
    categoryId: form.categoryId ?? null,
    tags: form.tags.join(','),
    file: form.file ? `${form.file.name}:${form.file.size}` : '',
  })
}
const dialogDirty = computed(() => dialogOpen.value && snapshot() !== dialogBaseline.value)

function resetEntryForm(keepContext = false) {
  const sourceType = form.sourceType
  const categoryId = form.categoryId
  form.mode = 'TEXT'
  form.title = ''
  form.content = ''
  form.sourceType = keepContext ? sourceType : 'MANUAL'
  form.sourceUrl = ''
  form.categoryId = keepContext ? categoryId : undefined
  form.tags = []
  form.file = null
  tagInput.value = ''
  uploadProgress.value = 0
  errors.title = ''
  errors.content = ''
  errors.sourceUrl = ''
  errors.tags = ''
  errors.file = ''
  errors._form = ''
}

async function focusTitleSoon() {
  await nextTick()
  requestAnimationFrame(() => titleInput.value?.focus())
}

function openCreate() {
  editingId.value = undefined
  editingDocId.value = undefined
  continueAdd.value = false
  dialogSaving.value = false
  resetEntryForm()
  dialogOpen.value = true
  dialogBaseline.value = snapshot()
  void focusTitleSoon()
}

function openEdit(item: WbCapture) {
  editingId.value = item.id
  editingDocId.value = item.docId
  continueAdd.value = false
  dialogSaving.value = false
  form.mode = item.docId ? 'FILE' : item.sourceType === 'WEB' ? 'LINK' : 'TEXT'
  form.title = item.title
  form.content = item.content || ''
  form.sourceType = item.sourceType || 'MANUAL'
  form.sourceUrl = item.sourceUrl || ''
  form.categoryId = item.categoryId
  form.tags = splitTags(item.tags)
  form.file = null
  tagInput.value = ''
  uploadProgress.value = 0
  errors.title = ''
  errors.content = ''
  errors.sourceUrl = ''
  errors.tags = ''
  errors.file = ''
  errors._form = ''
  dialogOpen.value = true
  dialogBaseline.value = snapshot()
  void focusTitleSoon()
}

function closeDialog() {
  dialogOpen.value = false
  dialogSaving.value = false
  uploadProgress.value = 0
  tagInput.value = ''
  errors.title = ''
  errors.content = ''
  errors.sourceUrl = ''
  errors.tags = ''
  errors.file = ''
  errors._form = ''
}

async function requestDialogClose() {
  if (dialogSaving.value) return
  if (!dialogDirty.value) {
    closeDialog()
    return
  }
  const ok = await confirmDialog('当前条目还没有保存，确定放弃编辑吗？', {
    title: '放弃未保存的编辑？',
    confirmText: '放弃',
    cancelText: '继续编辑',
    variant: 'danger',
    icon: 'trash-2',
  })
  if (ok) closeDialog()
}

function validateEntry(): boolean {
  errors.title = ''
  errors.content = ''
  errors.sourceUrl = ''
  errors.tags = ''
  errors.file = ''
  errors._form = ''
  const title = form.title.trim()
  if (!title) {
    errors.title = '请输入标题'
    titleInput.value?.focus()
    return false
  }
  if (title.length > 200) {
    errors.title = '标题不能超过 200 个字符'
    titleInput.value?.focus()
    return false
  }
  const url = form.sourceUrl.trim()
  if (form.mode === 'LINK' && !url) {
    errors.sourceUrl = '链接模式需要填写来源链接'
    return false
  }
  if (url && !isHttpUrl(url)) {
    errors.sourceUrl = '请输入有效的 http/https 链接'
    return false
  }
  if (form.mode === 'FILE' && !form.file && !editingDocId.value) {
    errors.file = '请选择要上传的文件'
    return false
  }
  if (form.tags.join(',').length > 500) {
    errors.tags = '标签总长度不能超过 500 个字符'
    return false
  }
  return true
}

async function saveEntry() {
  if (dialogSaving.value) return
  if (!validateEntry()) return
  const editing = editingId.value
  const title = form.title.trim()
  const tags = form.tags.join(',')
  let uploadedDocId: number | undefined
  let saved = false
  dialogSaving.value = true
  uploadProgress.value = 0
  try {
    let docId = editingDocId.value
    let content = form.content.trim()
    let sourceType = form.sourceType
    if (form.mode === 'FILE') {
      if (form.file) {
        const doc = await docsApi.upload(
          form.file,
          { title, tags: tags || undefined, categoryId: form.categoryId },
          (percent) => {
            uploadProgress.value = percent
          },
        )
        uploadedDocId = doc.id
        docId = doc.id
        if (!content) content = (doc.summary || '').trim()
      }
      sourceType = 'DOC'
    }
    const payload: WbCapturePayload = {
      title,
      content: content || undefined,
      sourceType,
      sourceUrl: normalizeHttpUrl(form.sourceUrl) || undefined,
      categoryId: form.categoryId,
      tags: tags || undefined,
      docId,
    }
    if (editing) await updateCapture(editing, payload)
    else await createCapture(payload)
    saved = true
    notify(editing ? '条目已更新' : '已保存到收集箱', 'success')
    if (!editing) revealCreated()
    if (!editing && continueAdd.value) {
      resetEntryForm(true)
      dialogBaseline.value = snapshot()
      await afterMutation()
      void focusTitleSoon()
      return
    }
    closeDialog()
    await afterMutation()
  } catch (e) {
    if (uploadedDocId && !saved) {
      void docsApi.remove(uploadedDocId).catch(() => undefined)
    }
    errors._form = getApiError(e, '保存失败，已填写内容仍保留')
  } finally {
    dialogSaving.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (file.size > 50 * 1024 * 1024) {
    errors.file = '文件不能超过 50MB'
    form.file = null
    target.value = ''
    return
  }
  errors.file = ''
  form.file = file
  if (!form.title.trim()) form.title = file.name.replace(/\.[^.]+$/, '').slice(0, 200)
}

function clearFile() {
  form.file = null
  errors.file = ''
  uploadProgress.value = 0
  if (fileInput.value) fileInput.value.value = ''
}

/* ============================ 标签 ============================ */
function onTagKey(event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ',' || event.key === '，') {
    event.preventDefault()
    addTag()
  }
}
function addTag() {
  const value = tagInput.value.trim().replace(/[,，]$/, '').trim()
  if (!value) {
    tagInput.value = ''
    return
  }
  if (form.tags.includes(value)) {
    tagInput.value = ''
    return
  }
  if ([...form.tags, value].join(',').length > 500) {
    errors.tags = '标签总长度不能超过 500 个字符'
    return
  }
  form.tags.push(value)
  errors.tags = ''
  tagInput.value = ''
}
function removeTag(index: number) {
  form.tags.splice(index, 1)
}

/* ============================ 行操作 ============================ */
function toggleSelect(id: number, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id]
  } else {
    selectedIds.value = selectedIds.value.filter((item) => item !== id)
  }
}
function togglePageSelection(checked: boolean) {
  const ids = pageItems.value.map((item) => item.id)
  if (checked) selectedIds.value = [...new Set([...selectedIds.value, ...ids])]
  else selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
}

async function batchStatus(status: string) {
  if (batchBusy.value || !selectedIds.value.length) return
  batchBusy.value = true
  try {
    await Promise.all(selectedIds.value.map((id) => setCaptureStatus(id, status)))
    selectedIds.value = []
    await afterMutation()
    notify('所选条目状态已更新', 'success')
  } catch (e) {
    notify(getApiError(e, '批量操作失败，请重试'), 'error')
    await load()
  } finally {
    batchBusy.value = false
  }
}

async function batchRemove() {
  if (batchBusy.value || !selectedIds.value.length) return
  const ids = [...selectedIds.value]
  await confirmDialog(`删除选中的 ${ids.length} 条收集内容？关联笔记不会自动删除。`, {
    title: '确认批量删除',
    confirmText: '确认删除',
    cancelText: '取消',
    icon: 'trash-2',
    variant: 'danger',
    onConfirm: async () => {
      batchBusy.value = true
      try {
        const results = await Promise.allSettled(ids.map((id) => deleteCapture(id)))
        const failed = results.filter((result) => result.status === 'rejected').length
        selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
        await afterMutation()
        if (failed) notify(`${failed} 条删除失败，其余已删除`, 'error')
        else notify(`已删除 ${ids.length} 条`, 'success')
      } finally {
        batchBusy.value = false
      }
    },
  })
}

async function remove(item: WbCapture) {
  await confirmDialog(`删除「${item.title}」？关联笔记不会自动删除。`, {
    title: '确认删除',
    confirmText: '确认删除',
    cancelText: '取消',
    icon: 'trash-2',
    variant: 'danger',
    onConfirm: async () => {
      try {
        await deleteCapture(item.id)
        selectedIds.value = selectedIds.value.filter((id) => id !== item.id)
        await afterMutation()
        notify('条目已删除', 'success')
      } catch (e) {
        notify(getApiError(e, '删除失败'), 'error')
        throw e
      }
    },
  })
}

async function setItemStatus(item: WbCapture, status: string) {
  try {
    await setCaptureStatus(item.id, status)
    await afterMutation()
    notify('状态已更新', 'success')
  } catch (e) {
    notify(getApiError(e, '操作失败'), 'error')
  }
}

async function toggleStar(item: WbCapture) {
  try {
    await toggleCaptureStar(item.id)
    await afterMutation()
  } catch (e) {
    notify(getApiError(e, '操作失败'), 'error')
  }
}

function toNote(item: WbCapture) {
  void router.push({ path: '/workbench/notes/new', query: { captureId: String(item.id) } })
}

/* ============================ 更多菜单 ============================ */
function openMenu(event: MouseEvent, item: WbCapture) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  menuItem.value = item
  menuX.value = rect.right
  menuY.value = rect.bottom + 4
  menuOpen.value = true
}

const menuItems = computed<MenuAction[]>(() => {
  const item = menuItem.value
  if (!item) return []
  const status = item.status || 'INBOX'
  const items: MenuAction[] = [
    { key: 'edit', label: '编辑条目', icon: 'pencil', onSelect: () => openEdit(item) },
    { key: 'note', label: '整理为笔记', icon: 'notebook-pen', onSelect: () => toNote(item) },
    { key: 'star', label: item.starred ? '取消标星' : '标星', icon: 'star', selected: !!item.starred, onSelect: () => toggleStar(item) },
  ]
  if (item.sourceUrl && safeHttpUrl(item.sourceUrl)) {
    items.push({ key: 'source', label: '打开来源链接', icon: 'external-link', onSelect: () => openSource(item.sourceUrl!) })
  }
  items.push({ key: 'sep-status', label: '', separator: true })
  if (status !== 'PROCESSED') items.push({ key: 'process', label: '标记为已整理', icon: 'check', onSelect: () => setItemStatus(item, 'PROCESSED') })
  if (status !== 'INBOX') items.push({ key: 'inbox', label: '移回待整理', icon: 'rotate-ccw', onSelect: () => setItemStatus(item, 'INBOX') })
  if (status !== 'ARCHIVED') items.push({ key: 'archive', label: '归档', icon: 'archive', onSelect: () => setItemStatus(item, 'ARCHIVED') })
  items.push({ key: 'sep-delete', label: '', separator: true })
  items.push({ key: 'delete', label: '删除', icon: 'trash-2', danger: true, onSelect: () => remove(item) })
  return items
})

function openSource(url: string) {
  const href = safeHttpUrl(url)
  if (!href) return
  window.open(href, '_blank', 'noopener,noreferrer')
}

/* ============================ 工具函数 ============================ */
function splitTags(tags?: string): string[] {
  if (!tags) return []
  return [...new Set(tags.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean))]
}
function statusLabel(status?: string): string {
  return ({ INBOX: '待整理', PROCESSED: '已整理', ARCHIVED: '已归档' } as Record<string, string>)[status || ''] || '待整理'
}
function sourceLabel(type?: string): string {
  return SOURCE_LABELS[type || 'MANUAL'] || '其他'
}
function sourceIcon(type?: string): string {
  return ({ MANUAL: 'pencil', DOC: 'file-text', WEB: 'link', AI: 'sparkles', IMPORT: 'upload' } as Record<string, string>)[type || 'MANUAL'] || 'file'
}
function excerpt(item: WbCapture, max = 120): string {
  const text = (item.content || '').replace(/\s+/g, ' ').trim()
  return text ? text.slice(0, max) : '没有正文'
}
function timeOf(value?: string): number {
  if (!value) return 0
  const time = new Date(value.replace(' ', 'T')).getTime()
  return Number.isNaN(time) ? 0 : time
}
function formatDate(value?: string): string {
  if (!value) return '—'
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
function isHttpUrl(value: string): boolean {
  return !!safeHttpUrl(value)
}
function safeHttpUrl(value: string): string {
  try {
    const url = new URL(value.trim())
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}
function normalizeHttpUrl(value: string): string {
  return safeHttpUrl(value)
}
function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

/* ============================ 生命周期 ============================ */
onBeforeRouteLeave(async () => {
  if (dialogSaving.value) return false
  if (dialogOpen.value && dialogDirty.value) {
    return await confirmDialog('当前条目还没有保存，确认离开当前页面？', {
      title: '离开并放弃编辑？',
      confirmText: '放弃并离开',
      cancelText: '返回编辑',
      variant: 'danger',
      icon: 'log-out',
    })
  }
  if (quickDirty.value) {
    return await confirmDialog('快速记录中还有未保存的内容，确认离开？', {
      title: '离开并放弃草稿？',
      confirmText: '放弃并离开',
      cancelText: '返回编辑',
      variant: 'danger',
      icon: 'log-out',
    })
  }
  return true
})
onBeforeUnmount(() => {
  listSequence += 1
})
</script>

<style scoped>
/* ============================ 页面头部操作 ============================ */
.capture-action-btn {
  gap: var(--kf-space-2);
  padding-inline: var(--kf-space-4);
}
.capture-action-btn.is-active {
  background: var(--kf-primary-soft);
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.capture-draft-dot {
  width: var(--kf-space-2);
  height: var(--kf-space-2);
  border-radius: 999px;
  background: var(--kf-highlight);
  box-shadow: 0 0 0 2px var(--kf-highlight-soft);
}

/* ============================ 页面容器 ============================ */
.capture-page {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  min-width: 0;
}

/* ============================ 快速记录 ============================ */
.capture-quick {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  padding: var(--kf-space-5);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  box-shadow: var(--shadow-card);
}
.capture-quick-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-3);
}
.capture-quick-heading {
  display: flex;
  align-items: flex-start;
  gap: var(--kf-space-3);
  min-width: 0;
}
.capture-quick-heading strong {
  display: block;
  font-size: var(--kf-fs-body-lg);
  color: var(--kf-foreground);
}
.capture-quick-heading p {
  margin: var(--kf-space-1) 0 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-quick-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--kf-btn-height-lg);
  height: var(--kf-btn-height-lg);
  border-radius: var(--kf-radius-md);
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
}
.capture-quick-collapse {
  flex-shrink: 0;
  height: var(--kf-btn-height-sm);
  font-size: var(--kf-fs-caption);
}
.capture-quick-primary {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
}
.capture-quick-textarea {
  line-height: var(--kf-lh-body-md);
}
.capture-quick-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--kf-space-3);
}
.capture-quick-field {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-1);
  min-width: 0;
}
.capture-quick-field .capture-label {
  font-size: var(--kf-fs-caption);
}
.capture-quick-error {
  margin: 0;
}
.capture-quick-foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--kf-space-2);
  border-top: 1px solid var(--kf-border);
  padding-top: var(--kf-space-3);
}

/* 展开 / 收起动效 */
.capture-reveal-enter-active,
.capture-reveal-leave-active {
  transition: opacity var(--kf-control-motion) ease, transform var(--kf-control-motion) ease;
}
.capture-reveal-enter-from,
.capture-reveal-leave-to {
  opacity: 0;
  transform: translateY(calc(-1 * var(--kf-space-2)));
}

/* ============================ 工具栏 ============================ */
.capture-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.capture-toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: nowrap;
  min-width: 0;
}
.capture-sync {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-search {
  position: relative;
  width: min(100%, calc(var(--kf-space-8) * 7));
  min-width: calc(var(--kf-space-8) * 4);
  display: flex;
  align-items: center;
}
.capture-search-icon {
  position: absolute;
  left: var(--kf-space-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--kf-muted-foreground);
  pointer-events: none;
}
.capture-search .capture-search-input {
  padding-inline-start: calc(var(--kf-space-8) - var(--kf-space-1));
}
.capture-filter {
  width: calc(var(--kf-space-8) * 4);
  min-width: calc(var(--kf-space-8) * 3);
}
.capture-filter-sort {
  width: calc(var(--kf-space-8) * 3.4);
}
.capture-view {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
  padding: var(--kf-space-1);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
}
.capture-view-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--kf-btn-height-sm);
  height: var(--kf-btn-height-sm);
  border: 0;
  border-radius: var(--kf-radius-xs);
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  transition: background var(--kf-control-motion) ease, color var(--kf-control-motion) ease;
}
.capture-view-btn:hover {
  background: var(--kf-muted);
  color: var(--kf-foreground);
}
.capture-view-btn.active {
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
}
.capture-toolbar :deep(.kf-select-trigger) {
  height: var(--kf-btn-height-md);
  font-size: var(--kf-fs-body-sm);
}

/* ============================ 批量操作 ============================ */
.capture-batch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  padding: var(--kf-space-3) var(--kf-space-4);
  border: 1px solid var(--kf-primary);
  border-radius: var(--kf-radius-md);
  background: var(--kf-primary-soft);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-sm);
}
.capture-batch-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.capture-batch .capture-ghost-btn {
  height: var(--kf-btn-height-sm);
  padding-inline: var(--kf-space-3);
  font-size: var(--kf-fs-caption);
  background: var(--kf-card);
}
.capture-danger-btn {
  color: var(--kf-destructive);
}
.capture-danger-btn:hover:not(:disabled) {
  border-color: var(--kf-destructive);
  background: color-mix(in srgb, var(--kf-destructive) 8%, var(--kf-card));
}

/* ============================ 通用按钮 ============================ */
.capture-primary-btn,
.capture-ghost-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--kf-space-2);
  height: var(--kf-btn-height-md);
  padding-inline: var(--kf-space-4);
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  line-height: var(--kf-lh-body-md);
  cursor: pointer;
  transition: background var(--kf-control-motion) ease, border-color var(--kf-control-motion) ease, opacity var(--kf-control-motion) ease;
}
.capture-primary-btn {
  border: 1px solid var(--kf-primary);
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}
.capture-primary-btn:hover:not(:disabled) {
  background: var(--kf-primary-hover);
}
.capture-ghost-btn {
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
}
.capture-ghost-btn:hover:not(:disabled) {
  background: var(--kf-muted);
}
.capture-primary-btn:disabled,
.capture-ghost-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.capture-spin {
  animation: capture-spin 0.9s linear infinite;
}
@keyframes capture-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ============================ 空状态 ============================ */
.capture-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kf-space-2);
  padding: var(--kf-space-8);
  text-align: center;
  color: var(--kf-muted-foreground);
  background: var(--kf-card);
  border: 1px dashed var(--kf-border);
  border-radius: var(--kf-radius-lg);
}
.capture-empty h2 {
  margin: 0;
  color: var(--kf-foreground);
}
.capture-empty p {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
}
.capture-empty-icon {
  display: grid;
  place-items: center;
  width: calc(var(--kf-space-8) * 1.6);
  height: calc(var(--kf-space-8) * 1.6);
  border-radius: 999px;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
}
.capture-empty-actions {
  display: flex;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  justify-content: center;
  margin-top: var(--kf-space-2);
}

/* ============================ 列表 ============================ */
.capture-list {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.capture-list-head,
.capture-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(0, 9rem) minmax(0, 7rem) minmax(0, 8.5rem) auto;
  align-items: center;
  gap: var(--kf-space-3);
  padding: var(--kf-space-3) var(--kf-space-4);
}
.capture-list-head {
  border-bottom: 1px solid var(--kf-border);
  background: color-mix(in srgb, var(--kf-muted) 40%, var(--kf-card));
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  color: var(--kf-muted-foreground);
}
.capture-head-actions {
  text-align: right;
  padding-inline-end: var(--kf-space-1);
}
.capture-row {
  border-bottom: 1px solid var(--kf-border);
  transition: background var(--kf-control-motion) ease;
}
.capture-row:last-child {
  border-bottom: 0;
}
.capture-row:hover {
  background: color-mix(in srgb, var(--kf-muted) 45%, var(--kf-card));
}
.capture-row.is-selected {
  background: var(--kf-primary-soft);
}
.capture-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-1);
}
.capture-title-line {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  min-width: 0;
  flex-wrap: wrap;
}
.capture-title {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  overflow-wrap: anywhere;
}
.capture-title:hover {
  color: var(--kf-primary);
}
.capture-title:focus-visible:not(.kf-form-control) {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: var(--kf-space-1);
  border-radius: var(--kf-radius-xs);
}
.capture-excerpt {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  line-height: var(--kf-lh-body-sm);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.capture-mobile-meta {
  display: none;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-mobile-source {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
}
.capture-tags {
  display: flex;
  align-items: center;
  gap: var(--kf-space-1);
  flex-wrap: wrap;
  min-width: 0;
}
.capture-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
  padding: 0 var(--kf-space-2);
  min-height: var(--kf-btn-height-sm);
  border-radius: 999px;
  background: color-mix(in srgb, var(--kf-primary) 8%, var(--kf-card));
  color: var(--kf-state-info);
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  max-width: 100%;
  overflow-wrap: anywhere;
}
.capture-muted {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
}
.capture-source {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  min-width: 0;
}
.capture-source span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.capture-time {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.capture-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--kf-space-1);
}
.capture-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--kf-btn-height-sm);
  height: var(--kf-btn-height-sm);
  border: 0;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  transition: background var(--kf-control-motion) ease, color var(--kf-control-motion) ease;
}
.capture-icon-btn:hover {
  background: var(--kf-muted);
  color: var(--kf-foreground);
}
.capture-icon-btn:focus-visible:not(.kf-form-control) {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: 1px;
}
.capture-status {
  display: inline-flex;
  align-items: center;
  padding: 0 var(--kf-space-2);
  min-height: calc(var(--kf-space-5) - var(--kf-space-1));
  border-radius: 999px;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  white-space: nowrap;
}
.capture-status.is-inbox {
  background: color-mix(in srgb, var(--kf-warning) 14%, var(--kf-card));
  color: var(--kf-state-warning);
}
.capture-status.is-processed {
  background: color-mix(in srgb, var(--kf-accent) 14%, var(--kf-card));
  color: var(--kf-state-success);
}
.capture-status.is-archived {
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
}
.capture-star {
  display: inline-flex;
  color: var(--kf-warning);
}

/* ============================ 卡片 ============================ */
.capture-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(calc(var(--kf-space-8) * 7), 1fr));
  gap: var(--kf-space-3);
}
.capture-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  min-width: 0;
  padding: var(--kf-space-4);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  box-shadow: var(--shadow-card);
  transition: border-color var(--kf-control-motion) ease, box-shadow var(--kf-control-motion) ease;
}
.capture-card:hover {
  border-color: color-mix(in srgb, var(--kf-primary) 35%, var(--kf-border));
}
.capture-card.is-selected {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 1px var(--kf-primary);
}
.capture-card-top {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
}
.capture-card-top .capture-icon-btn {
  margin-left: auto;
}
.capture-card-title {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
  font-weight: 650;
  text-align: left;
  cursor: pointer;
  overflow-wrap: anywhere;
}
.capture-card-title:hover {
  color: var(--kf-primary);
}
.capture-card-title:focus-visible:not(.kf-form-control) {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: var(--kf-space-1);
  border-radius: var(--kf-radius-xs);
}
.capture-card-excerpt {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  line-height: var(--kf-lh-body-sm);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.capture-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
  margin-top: auto;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-card-source {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.capture-card-note {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
  align-self: flex-start;
  padding: var(--kf-space-1) var(--kf-space-2);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption);
  cursor: pointer;
}
.capture-card-note:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}

/* ============================ 分页 ============================ */
.capture-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.capture-page-size {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-page-size select {
  width: auto;
}

/* ============================ 编辑弹窗表单 ============================ */
.capture-form {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  min-width: 0;
}
.capture-form-field {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  min-width: 0;
}
.capture-label {
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  color: var(--kf-foreground);
}
.capture-req {
  color: var(--kf-destructive);
}
.capture-field-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-field-hint {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-field-error {
  font-size: var(--kf-fs-caption);
  color: var(--kf-state-error);
}
.capture-counter {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  white-space: nowrap;
}
.capture-content {
  line-height: var(--kf-lh-body-md);
}
.capture-modes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kf-space-2);
}
.capture-mode {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  min-height: var(--kf-btn-height-md);
  padding-inline: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background var(--kf-control-motion) ease, border-color var(--kf-control-motion) ease, color var(--kf-control-motion) ease;
}
.capture-mode:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.capture-mode.active {
  background: var(--kf-primary-soft);
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.capture-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-4);
}
.capture-tags-field {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  padding-inline: var(--kf-field-padding);
}
.capture-tags-field .kf-input {
  padding-inline: 0;
}
.capture-tags-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}
.capture-tag-list {
  margin-top: var(--kf-space-1);
}
.capture-tag-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  opacity: 0.65;
  cursor: pointer;
}
.capture-tag-remove:hover {
  opacity: 1;
}
.capture-file-native {
  display: none;
}
.capture-file {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  padding: var(--kf-space-3);
  border: 1px dashed var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-field-surface);
  color: var(--kf-muted-foreground);
}
.capture-file.is-error {
  border-color: var(--kf-destructive);
  background: var(--kf-field-error-bg);
}
.capture-file-info {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-1);
  min-width: 0;
  flex: 1;
}
.capture-file-info strong {
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-sm);
  overflow-wrap: anywhere;
}
.capture-file-info small {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.capture-dialog-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  width: 100%;
  flex-wrap: wrap;
}
.capture-continue {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
}
.capture-dialog-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  margin-left: auto;
}

/* ============================ 响应式 ============================ */
@media (max-width: 1280px) {
  .capture-quick-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 1100px) {
  .capture-list-head,
  .capture-row {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }
  .capture-list-head > span:nth-child(3),
  .capture-list-head > span:nth-child(4),
  .capture-list-head > span:nth-child(5),
  .capture-cell-tags,
  .capture-row > .capture-tags,
  .capture-row > .capture-source,
  .capture-row > .capture-time {
    display: none;
  }
  .capture-mobile-meta {
    display: flex;
  }
}
@media (max-width: 760px) {
  .capture-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .capture-toolbar-right {
    width: 100%;
    flex-wrap: wrap;
  }
  .capture-search {
    width: 100%;
    min-width: 0;
    order: -1;
  }
  .capture-filter,
  .capture-filter-sort {
    flex: 1 1 calc(50% - var(--kf-space-2));
    width: auto;
    min-width: calc(var(--kf-space-8) * 3);
  }
  .capture-view {
    margin-left: auto;
  }
  .capture-quick-meta,
  .capture-form-grid {
    grid-template-columns: 1fr;
  }
  .capture-quick-head {
    flex-direction: column;
    align-items: stretch;
  }
  .capture-quick-collapse {
    align-self: flex-end;
  }
  .capture-dialog-foot {
    align-items: stretch;
  }
  .capture-dialog-actions {
    width: 100%;
  }
  .capture-dialog-actions .capture-ghost-btn,
  .capture-dialog-actions .capture-primary-btn {
    flex: 1;
  }
  .capture-cards {
    grid-template-columns: 1fr;
  }
  .capture-pagination {
    align-items: stretch;
    flex-direction: column;
  }
  .capture-page-size {
    justify-content: flex-end;
  }
}
@media (max-width: 520px) {
  .capture-filter,
  .capture-filter-sort {
    flex: 1 1 100%;
  }
  .capture-view {
    margin-left: 0;
  }
  .capture-row {
    padding-inline: var(--kf-space-3);
  }
  .capture-row-note-btn {
    display: none;
  }
  .capture-quick-foot {
    flex-direction: column-reverse;
  }
  .capture-quick-foot .capture-primary-btn,
  .capture-quick-foot .capture-ghost-btn {
    width: 100%;
  }
}
@media (prefers-reduced-motion: reduce) {
  .capture-reveal-enter-active,
  .capture-reveal-leave-active,
  .capture-spin {
    transition: none;
    animation: none;
  }
}
</style>
