<template>
  <AppDialog
    :open="open"
    title="候选词条审核"
    description="个人提取的候选词条只有审核通过后才会进入正式词库；默认仅通过不上架。"
    width="820px"
    scrollable
    :hide-footer="true"
    @cancel="emit('close')"
  >
    <section class="cr">
      <div class="cr-bar">
        <label class="cr-publish kf-checkbox-row">
          <Checkbox as="span" v-model="publishOnApprove" />
          <span>审核通过时同时发布到公共词库（PUBLISHED）</span>
        </label>
        <button type="button" class="cr-btn" :disabled="loading" @click="load">
          <Icon name="refresh-cw" :size="14" />
          刷新
        </button>
      </div>

      <p v-if="loading" class="cr-state" role="status">正在读取待审核候选…</p>
      <p v-else-if="error" class="cr-state cr-error" role="alert">
        {{ error }}
        <button type="button" class="cr-link" @click="load">重试</button>
      </p>
      <p v-else-if="!items.length" class="cr-state">暂无待审核候选。</p>

      <ul v-else class="cr-list">
        <li v-for="item in items" :key="item.id" class="cr-item">
          <div class="cr-head">
            <span class="cr-word" :class="{ 'is-acronym': item.termType === 'ACRONYM' }">{{ item.headword }}</span>
            <span class="cr-tag">{{ termTypeLabel(item.termType) }}</span>
            <span class="cr-tag">{{ domainLabel(item.domain) }}</span>
            <span class="cr-tag" :class="item.extractor === 'AI' ? 'is-ai' : 'is-rule'">
              {{ item.extractor === 'AI' ? 'AI 建议' : '规则提取' }}
            </span>
            <span v-if="item.duplicateTermId" class="cr-tag is-warn">词库已存在</span>
          </div>
          <p v-if="item.fullForm" class="cr-full">{{ item.fullForm }}</p>
          <label class="cr-field">
            <span class="cr-field-label">中文释义</span>
            <input
              v-model="meanings[item.id]"
              class="kf-input cr-meaning-input"
              maxlength="200"
              aria-label="中文释义"
              placeholder="审核通过前必须补全中文释义"
            />
          </label>
          <p v-if="item.definitionCn" class="cr-def">{{ item.definitionCn }}</p>
          <p class="cr-meta">
            来源：{{ item.sourceTitle || item.sourceType }}{{ item.sourceId ? ` #${item.sourceId}` : '' }}
            <span v-if="item.sourceContext"> · 原文：{{ item.sourceContext }}</span>
          </p>
          <div class="cr-actions">
            <input
              v-model="notes[item.id]"
              class="kf-input cr-note"
              maxlength="200"
              :placeholder="`审核备注（可选）`"
              :aria-label="`${item.headword} 审核备注`"
            />
            <button
              type="button"
              class="cr-btn cr-btn-approve"
              :disabled="pendingId === item.id"
              @click="review(item, true)"
            >通过</button>
            <button
              type="button"
              class="cr-btn cr-btn-reject"
              :disabled="pendingId === item.id"
              @click="review(item, false)"
            >拒绝</button>
          </div>
        </li>
      </ul>
    </section>
  </AppDialog>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

// 管理端候选审核：通过时才调用既有词条创建链路，重复审核由服务端幂等处理。
import { ref, watch } from 'vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import Icon from '@/components/ui/Icon.vue'
import { adminTechEnglishApi, type CandidateVO } from '@/api/techEnglish'
import { domainLabel, termTypeLabel } from './term-labels'
import { getApiError, notify } from '@/utils/toast'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; reviewed: [] }>()

const items = ref<CandidateVO[]>([])
const loading = ref(false)
const error = ref('')
const pendingId = ref<number | null>(null)
const publishOnApprove = ref(false)
const notes = ref<Record<number, string>>({})
const meanings = ref<Record<number, string>>({})

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await adminTechEnglishApi.listCandidates('PENDING')
    notes.value = {}
    meanings.value = Object.fromEntries(items.value.map((row) => [row.id, row.meaningCn || '']))
  } catch (e) {
    error.value = getApiError(e, '候选加载失败')
    items.value = []
  } finally {
    loading.value = false
  }
}

async function review(item: CandidateVO, approve: boolean) {
  if (pendingId.value != null) return
  pendingId.value = item.id
  try {
    await adminTechEnglishApi.reviewCandidate(item.id, {
      approve,
      publish: approve ? publishOnApprove.value : false,
      meaningCn: meanings.value[item.id]?.trim() || item.meaningCn || null,
      domain: item.domain,
      reviewNote: notes.value[item.id]?.trim() || null,
    })
    notify(approve ? `已通过：${item.headword}` : `已拒绝：${item.headword}`, 'success')
    // 审核完成后从待审核列表移除，再次打开重新拉取服务端状态
    items.value = items.value.filter((row) => row.id !== item.id)
    emit('reviewed')
  } catch (e) {
    notify(getApiError(e, '审核失败，请稍后重试'), 'error')
  } finally {
    pendingId.value = null
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) void load()
  },
)
</script>

<style scoped>
.cr {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cr-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.cr-publish {
  display: flex;
  align-items: center;
  gap: var(--kf-checkbox-gap);
  font-size: var(--kf-fs-body-md);
  color: var(--kf-muted-foreground, #6b7280);
}

.cr-state {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 13px;
}

.cr-error {
  color: var(--kf-danger, #d93025);
}

.cr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cr-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--kf-border, #e2e6ec);
  border-radius: var(--kf-radius-md, 12px);
}

.cr-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cr-word {
  font-size: 16px;
  font-weight: 600;
}

.cr-word.is-acronym {
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
}

.cr-tag {
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--kf-muted, #f7f8fa);
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 11px;
}

.cr-tag.is-ai {
  color: var(--kf-primary, #3b6fe0);
  background: var(--kf-primary-soft, #eef3fd);
}

.cr-tag.is-warn {
  color: var(--kf-warning, #b45309);
}

.cr-full,
.cr-def,
.cr-meta {
  margin: 0;
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
  line-height: 1.6;
}

.cr-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cr-field-label {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
}

.cr-meaning-input {
  flex: 1;
  min-width: 200px;
  height: 30px;
  font-size: 13px;
}

.cr-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.cr-note {
  flex: 1;
  min-width: 200px;
  height: 30px;
  font-size: 12px;
}

.cr-btn {
  height: 30px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: var(--kf-radius-sm, 8px);
  border: 1px solid var(--kf-border, #e2e6ec);
  background: var(--kf-card, #ffffff);
  color: var(--kf-foreground, #1a1d23);
  font-size: 12px;
  cursor: pointer;
}

.cr-btn-approve {
  border-color: transparent;
  background: var(--kf-primary, #3b6fe0);
  color: #ffffff;
}

.cr-btn-reject {
  color: var(--kf-danger, #d93025);
}

.cr-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cr-link {
  border: none;
  background: none;
  color: var(--kf-primary, #3b6fe0);
  cursor: pointer;
  font-size: 13px;
}
</style>
