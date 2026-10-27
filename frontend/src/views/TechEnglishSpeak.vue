<template>
  <div class="te-page">
    <PageHeader
      title="专业英语 · 跟读练习"
      description="先听已核验的参考发音，再录下自己的跟读，本地回放对照。录音只保存在当前浏览器，不上传，也不做自动发音评分。"
    >
      <template #actions>
        <button type="button" class="te-action-btn" @click="backHome">
          <Icon name="arrow-left" :size="16" aria-hidden="true" />
          <span class="te-action-label">返回词汇大厅</span>
        </button>
      </template>
    </PageHeader>

    <section class="te-picker" aria-label="选择练习词条">
      <div class="te-search">
        <Icon name="search" :size="16" class="te-search-icon" aria-hidden="true" />
        <input
          v-model="q"
          class="kb-input te-search-input"
          type="search"
          placeholder="搜索要跟读的术语，例如 Idempotency"
          aria-label="搜索跟读词条"
          @input="onSearchInput"
        />
      </div>
      <p v-if="searchError" class="te-inline-error" role="alert">{{ searchError }}</p>
      <ul v-if="results.length" class="te-search-results">
        <li v-for="item in results" :key="item.id">
          <button type="button" class="te-result" @click="selectTerm(item.id)">
            <span class="te-result-word">{{ item.headword }}</span>
            <span class="te-result-meaning">{{ item.meaningCn }}</span>
          </button>
        </li>
      </ul>
    </section>

    <StateView
      :status="detailState"
      :rows="4"
      type="list"
      error-icon="alert-circle"
      error-title="词条加载失败"
      :error-description="detailError"
      error-hint="检查网络后重试，或重新搜索词条"
      empty-icon="mic"
      empty-title="先选择一个词条"
      empty-description="从上方搜索词条，或在词汇详情页点击「跟读练习」进入。"
      @retry="loadDetail"
    >
      <section v-if="detail" class="te-practice" aria-label="跟读练习面板">
        <header class="te-practice-head">
          <div>
            <h2 class="te-word">{{ detail.headword }}</h2>
            <p class="te-meaning">{{ detail.meaningCn }}</p>
            <p v-if="detail.fullForm" class="te-meta">全称：{{ detail.fullForm }}</p>
            <p v-if="detail.ipaUs || detail.ipaUk" class="te-meta">
              <span v-if="detail.ipaUs">美 {{ detail.ipaUs }}</span>
              <span v-if="detail.ipaUk">英 {{ detail.ipaUk }}</span>
            </p>
          </div>
          <RouterLink
            class="te-action-btn"
            :to="{ name: 'TechEnglishDetail', params: { id: detail.id } }"
          >查看词条详情</RouterLink>
        </header>

        <TermPronunciation
          :term-id="detail.id"
          :term-type="detail.termType"
          :pronunciations="detail.pronunciations || []"
        />

        <section class="te-record" aria-label="跟读录音">
          <div class="te-record-head">
            <h3 class="te-section-title">跟读录音</h3>
            <span class="te-record-state" :class="{ 'is-recording': recording }">
              {{ recording ? `录音中 ${recordingSeconds}s / ${MAX_SECONDS}s` : hasRecording ? '已录音' : '未录音' }}
            </span>
          </div>

          <p v-if="!mediaSupported" class="te-warning" role="alert">
            当前浏览器不支持录音（缺少 MediaRecorder 或麦克风接口）。你仍可以播放参考发音，但没有本地录音功能。
          </p>
          <p v-else class="te-note">
            点击开始后会请求麦克风权限；录音最长 {{ MAX_SECONDS }} 秒，离开页面会自动停止并释放麦克风。
          </p>

          <p v-if="recordError" class="te-inline-error" role="alert">{{ recordError }}</p>

          <div class="te-record-actions">
            <button
              v-if="!recording"
              type="button"
              class="te-action-btn te-action-btn-primary"
              :disabled="!mediaSupported"
              @click="startRecording"
            >
              <Icon name="mic" :size="16" aria-hidden="true" />
              <span class="te-action-label">{{ hasRecording ? '重新录制' : '开始录音' }}</span>
            </button>
            <button v-else type="button" class="te-action-btn" @click="stopRecording">
              <Icon name="square" :size="16" aria-hidden="true" />
              <span class="te-action-label">停止录音</span>
            </button>
            <button
              v-if="hasRecording && !recording"
              type="button"
              class="te-action-btn"
              @click="clearRecording"
            >
              <Icon name="rotate-ccw" :size="16" aria-hidden="true" />
              <span class="te-action-label">清除录音</span>
            </button>
            <button
              v-if="detail.pronunciations?.length"
              type="button"
              class="te-action-btn"
              @click="replayReference"
            >
              <Icon name="volume-2" :size="16" aria-hidden="true" />
              <span class="te-action-label">再听一遍参考发音</span>
            </button>
          </div>

          <div v-if="hasRecording && recordingUrl" class="te-playback">
            <p class="te-note">本地回放（仅当前浏览器持有，刷新页面即释放）：</p>
            <audio :src="recordingUrl" controls preload="metadata"></audio>
          </div>

          <ol class="te-steps">
            <li>先听参考发音，注意重音与连读。</li>
            <li>点击「开始录音」，跟读 {{ detail.headword }}。</li>
            <li>停止后本地回放，自行与原音对照。</li>
            <li>需要时重新录制；本页不给出任何准确率评分。</li>
          </ol>
        </section>
      </section>
    </StateView>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import StateView from '@/components/ui/StateView.vue'
import Icon from '@/components/ui/Icon.vue'
import TermPronunciation from '@/components/tech-english/TermPronunciation.vue'
import { techEnglishApi, type TermDetail, type TermSummary } from '@/api/techEnglish'
import { useTermSpeech } from '@/composables/useTermSpeech'
import { getApiError } from '@/utils/toast'

const MAX_SECONDS = 60

const route = useRoute()
const router = useRouter()

const q = ref('')
const results = ref<TermSummary[]>([])
const searchError = ref('')

const detail = ref<TermDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const mediaSupported = computed(() => {
  if (typeof window === 'undefined') return false
  return (
    typeof MediaRecorder !== 'undefined' &&
    !!navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === 'function'
  )
})

const recording = ref(false)
const recordingSeconds = ref(0)
const hasRecording = ref(false)
const recordingUrl = ref('')
const recordError = ref('')

const { speechError, play, stop } = useTermSpeech()

const detailState = computed(() => {
  if (detailError.value) return 'error' as const
  if (detailLoading.value) return 'loading' as const
  return detail.value ? ('ready' as const) : ('empty' as const)
})

const currentTermId = computed(() => {
  const raw = Number(route.query.termId)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})

let searchTimer: number | undefined
let searchSeq = 0

function onSearchInput() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    void runSearch()
  }, 320)
}

async function runSearch() {
  const keyword = q.value.trim()
  if (!keyword) {
    results.value = []
    searchError.value = ''
    return
  }
  const seq = ++searchSeq
  try {
    const page = await techEnglishApi.searchTerms({ q: keyword, pageSize: 8 })
    if (seq !== searchSeq) return
    results.value = page.records
    searchError.value = ''
  } catch (e: unknown) {
    if (seq !== searchSeq) return
    searchError.value = getApiError(e)
    results.value = []
  }
}

async function selectTerm(id: number) {
  results.value = []
  q.value = ''
  await router.replace({ name: 'TechEnglishSpeak', query: { termId: String(id) } })
  await loadDetail()
}

async function loadDetail() {
  if (!currentTermId.value) {
    detail.value = null
    detailError.value = ''
    return
  }
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await techEnglishApi.getTerm(currentTermId.value)
  } catch (e: unknown) {
    detail.value = null
    detailError.value = getApiError(e)
  } finally {
    detailLoading.value = false
  }
}

function replayReference() {
  const list = detail.value?.pronunciations ?? []
  const pron = list.find((p) => p.variant === 'WORD_US') ?? list.find((p) => p.variant === 'ABBR') ?? list[0]
  if (pron) void play(pron, pron.variant)
}

/* ============================ 录音（本地，不上传） ============================ */

let recorder: MediaRecorder | null = null
let stream: MediaStream | null = null
let chunks: Blob[] = []
let tickTimer: number | undefined
let disposed = false

function pickMimeType(): string | undefined {
  if (typeof MediaRecorder.isTypeSupported !== 'function') return undefined
  return ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus'].find((type) =>
    MediaRecorder.isTypeSupported(type),
  )
}

function mapMediaError(name?: string): string {
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return '麦克风权限被拒绝，请在浏览器地址栏的权限设置中允许麦克风后重试。'
  }
  if (name === 'NotFoundError' || name === 'OverconstrainedError') {
    return '未检测到可用的麦克风设备。'
  }
  if (name === 'NotReadableError') {
    return '麦克风被其它程序占用，请关闭后重试。'
  }
  return '无法开始录音，请检查麦克风权限与设备。'
}

function clearTimers() {
  if (tickTimer) {
    window.clearInterval(tickTimer)
    tickTimer = undefined
  }
}

function releaseStream() {
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
}

function revokeRecording() {
  if (recordingUrl.value) {
    URL.revokeObjectURL(recordingUrl.value)
    recordingUrl.value = ''
  }
  hasRecording.value = false
}

async function startRecording() {
  if (!mediaSupported.value || recording.value) return
  recordError.value = ''
  speechError.value = ''
  stop()
  revokeRecording()
  chunks = []
  recordingSeconds.value = 0
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch (e: unknown) {
    const name = (e as { name?: string })?.name
    recordError.value = mapMediaError(name)
    releaseStream()
    return
  }
  const mimeType = pickMimeType()
  try {
    recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
  } catch {
    recordError.value = '当前浏览器无法初始化录音编码，请更换浏览器后重试。'
    releaseStream()
    recorder = null
    return
  }
  recorder.ondataavailable = (event) => {
    if (event.data && event.data.size > 0) chunks.push(event.data)
  }
  recorder.onerror = () => {
    recordError.value = '录音过程出错，请停止后重试。'
  }
  recorder.onstop = () => {
    const type = recorder?.mimeType || mimeType || 'audio/webm'
    const blob = new Blob(chunks, { type })
    chunks = []
    releaseStream()
    recording.value = false
    clearTimers()
    if (disposed) return
    if (!blob.size) {
      recordError.value = '没有录到有效音频，请检查麦克风后重试。'
      return
    }
    recordingUrl.value = URL.createObjectURL(blob)
    hasRecording.value = true
  }
  recorder.start()
  recording.value = true
  tickTimer = window.setInterval(() => {
    recordingSeconds.value += 1
    if (recordingSeconds.value >= MAX_SECONDS) {
      stopRecording()
    }
  }, 1000)
}

function stopRecording() {
  clearTimers()
  if (recorder && recorder.state !== 'inactive') {
    recorder.stop()
  } else {
    recording.value = false
    releaseStream()
  }
}

function clearRecording() {
  revokeRecording()
  recordError.value = ''
  recordingSeconds.value = 0
}

function backHome() {
  void router.push({ name: 'TechEnglishHome' })
}

onUnmounted(() => {
  disposed = true
  clearTimers()
  try {
    if (recorder && recorder.state !== 'inactive') recorder.stop()
  } catch {
    // 页面退出时录音器可能已被浏览器回收
  }
  recorder = null
  releaseStream()
  revokeRecording()
  stop()
})

void loadDetail()
</script>

<style scoped>
.te-page {
  display: grid;
  gap: var(--kb-space-4);
  min-width: 0;
}
.te-picker {
  display: grid;
  gap: var(--kb-space-2);
}
.te-search {
  position: relative;
  max-width: 480px;
}
.te-search-icon {
  position: absolute;
  left: var(--kb-space-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
  pointer-events: none;
}
.te-search-input {
  padding-left: calc(var(--kb-space-3) * 2 + 16px);
}
.te-inline-error {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-state-error);
}
.te-search-results {
  display: grid;
  gap: var(--kb-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
  max-width: 560px;
}
.te-result {
  display: flex;
  align-items: baseline;
  gap: var(--kb-space-2);
  width: 100%;
  padding: var(--kb-space-2) var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  color: var(--kb-foreground);
  text-align: left;
  cursor: pointer;
}
.te-result:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.te-result-word {
  font-weight: 600;
  overflow-wrap: anywhere;
}
.te-result-meaning {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-practice {
  display: grid;
  gap: var(--kb-space-4);
  padding: var(--kb-space-4);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
}
.te-practice-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
}
.te-practice-head > div {
  display: grid;
  gap: var(--kb-space-1);
  min-width: 0;
}
.te-word {
  margin: 0;
  font-size: var(--kb-fs-h2, 28px);
  color: var(--kb-foreground);
  overflow-wrap: anywhere;
}
.te-meaning {
  margin: 0;
  color: var(--kb-muted-foreground);
}
.te-meta {
  margin: 0;
  display: flex;
  gap: var(--kb-space-2);
  flex-wrap: wrap;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-record {
  display: grid;
  gap: var(--kb-space-3);
  padding: var(--kb-space-4);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-background);
}
.te-record-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
}
.te-section-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}
.te-record-state {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-record-state.is-recording {
  color: var(--kb-state-error);
}
.te-note {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-warning {
  margin: 0;
  padding: var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-muted);
  font-size: var(--kb-fs-caption);
  color: var(--kb-state-warning);
}
.te-record-actions {
  display: flex;
  gap: var(--kb-space-2);
  flex-wrap: wrap;
}
.te-playback {
  display: grid;
  gap: var(--kb-space-2);
}
.te-playback audio {
  width: 100%;
  max-width: 420px;
}
.te-steps {
  margin: 0;
  padding-left: var(--kb-space-5, 20px);
  display: grid;
  gap: var(--kb-space-1);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
</style>
