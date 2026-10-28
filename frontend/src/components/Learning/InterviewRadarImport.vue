<template>
  <Button variant="secondary" @click="open = true">导入面试雷达</Button>
  <AppDialog :open="open" title="导入面试雷达来源教材" :loading="busy" hide-footer @cancel="open = false">
    <div class="radar-import-content">
    <p>同号合并并保留全部原文。仅写入本人私有路径；题目保留待审，不导入历史分数。</p>
    <label for="radar-source-file">选择扫描清单 JSON</label>
    <input id="radar-source-file" type="file" accept=".json,application/json" :disabled="busy" @change="selectSource" />
    <p v-if="busy" role="status">正在处理，请稍候…</p>
    <p v-if="preview" role="status">{{ preview.chapters }} 个章节 · {{ preview.questionCandidates }} 道含答案候选 · {{ preview.imageAssets ?? 0 }} 张来源图片 · {{ preview.referenceDocuments ?? 0 }} 份参考索引</p>
    <ul v-if="preview?.conflicts.length"><li v-for="conflict in preview.conflicts" :key="conflict">{{ conflict }}</li></ul>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="result" role="status">导入完成：新增 {{ result.chaptersCreated }} 章，更新 {{ result.chaptersUpdated }} 章，新增 {{ result.pendingQuestionsCreated }} 道待审题，新增 {{ result.imagesCreated ?? 0 }} 张来源图片与 {{ result.referenceDocumentsCreated ?? 0 }} 份参考索引。历史成绩导入 0 条。</p>
    <Button :loading="busy" :disabled="!preview || !!preview.conflicts.length || !!result" @click="apply">导入本人学习路径</Button>
    </div>
  </AppDialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import Button from '@/components/ui/Button.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import { interviewRadarApi, type RadarImportPreview, type RadarImportResult } from '@/api/interviewRadar'
import { getApiError } from '@/utils/toast'
const emit = defineEmits<{ imported: [result: RadarImportResult] }>()
const open = ref(false), busy = ref(false), error = ref('')
const preview = ref<RadarImportPreview | null>(null), result = ref<RadarImportResult | null>(null)
let source: unknown
async function selectSource(event: Event) {
 const file = (event.target as HTMLInputElement).files?.[0]
 preview.value = null; result.value = null; error.value = ''; source = undefined
 if (!file) return
 if (file.size > 16 * 1024 * 1024) { error.value = '清单超过 16 MB，请拆分后导入'; return }
 busy.value = true
 try { source = JSON.parse(await file.text()); preview.value = await interviewRadarApi.preview(source) }
 catch (cause) { error.value = getApiError(cause, '读取或预览失败，请重新选择清单重试') }
 finally { busy.value = false }
}
async function apply() {
 if (busy.value || !preview.value || preview.value.conflicts.length || result.value) return
 busy.value = true; error.value = ''
 try { result.value = await interviewRadarApi.apply(source); emit('imported', result.value) }
 catch (cause) { error.value = getApiError(cause, '导入失败，请重试') }
 finally { busy.value = false }
}
</script>

<style scoped>
.radar-import-content { display: grid; gap: var(--kf-space-3); min-width: 0; }
.radar-import-content input { min-width: 0; max-width: 100%; width: 100%; box-sizing: border-box; }
</style>
