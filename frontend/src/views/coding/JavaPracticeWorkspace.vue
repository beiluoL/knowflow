<template>
  <div class="java-workspace">
    <header>
      <RouterLink to="/coding/bank">← 算法题库</RouterLink>
      <h1>{{ data?.problem.title || "Java 编程练习" }}</h1>
      <span>Java</span>
    </header>
    <p v-if="loading" role="status">正在读取题目与个人草稿…</p>
    <div v-if="loadError" role="alert">
      {{ loadError }} <Button @click="load">重试读取</Button>
    </div>
    <template v-if="data">
      <aside v-if="!data.executionEnabled" class="notice">
        <strong>安全评测环境尚未就绪</strong>
        <p>
          可编辑、保存草稿和提交留档。当前不会编译或执行代码，也不会计分或记入错题。
        </p>
      </aside>
      <main>
        <section class="problem" aria-label="题目说明">
          <h2>题目说明</h2>
          <p>{{ data.problem.content }}</p>
          <h3>约束条件</h3>
          <p>{{ data.problem.constraints || "暂无额外约束" }}</p>
          <h3>函数签名</h3>
          <pre>{{ data.problem.functionSignature }}</pre>
          <h3>公开示例</h3>
          <article v-for="item in data.problem.publicCases" :key="item.orderNo">
            <p>输入</p>
            <pre>{{ item.input }}</pre>
            <p>期望输出</p>
            <pre>{{ item.expectedOutput }}</pre>
          </article>
          <p>
            时间限制：{{ data.problem.timeLimitMs }} ms · 内存限制：{{
              data.problem.memoryLimitMb
            }}
            MB
          </p>
        </section>
        <section class="editor" aria-label="Java 编辑器">
          <div class="toolbar">
            <h2>我的代码</h2>
            <span role="status">{{
              saving
                ? "保存中…"
                : saveError
                  ? "保存失败"
                  : dirty
                    ? "有未保存修改"
                    : `已保存 · 修订 ${revision}`
            }}</span
            ><Button :disabled="busy || formatting" @click="format('manual')"
              >格式化</Button
            ><Button :disabled="busy || saving" @click="saveCode"
              >保存草稿</Button
            >
          </div>
          <div class="preferences">
            <label
              ><input
                v-model="settings.enabled"
                type="checkbox"
              />自动格式化</label
            ><label
              ><input v-model="settings.save" type="checkbox" />保存时</label
            ><label
              ><input v-model="settings.paste" type="checkbox" />粘贴时</label
            ><label
              ><input v-model="settings.input" type="checkbox" />输入后</label
            >
          </div>
          <textarea
            ref="area"
            v-model="source"
            aria-label="Java源码"
            spellcheck="false"
            :disabled="busy || loading"
            @input="onInput"
            @paste="onPaste"
            @compositionstart="onCompositionStart"
            @compositionend="onCompositionEnd"
            @keydown="handleHistory"
          />
          <p v-if="formatMessage" role="status">{{ formatMessage }}</p>
          <p v-if="persistenceError" role="alert">{{ persistenceError }}</p>
          <div v-if="saveError" role="alert">
            {{ saveError }}
            <p>请先复制本地代码，再重新加载服务端版本以解决冲突。</p>
            <Button @click="saveCode">重试保存</Button
            ><Button @click="reload">重新加载版本</Button>
          </div>
          <div class="toolbar">
            <Button
              :disabled="busy || !data.executionEnabled"
              :title="data.message"
              @click="submit('RUN')"
              >运行示例</Button
            ><span v-if="!data.executionEnabled">安全评测环境尚未就绪</span
            ><Button
              :disabled="busy || saving || formatting || !!pendingJob"
              @click="submit('SUBMIT')"
              >{{
                data.executionEnabled ? "正式提交" : "提交并保留代码"
              }}</Button
            ><Button v-if="pendingJob" :disabled="busy" @click="retrySubmission"
              >重试原提交请求</Button
            >
          </div>
          <p v-if="error" role="alert">{{ error }}</p>
        </section>
      </main>
      <section class="history">
        <div>
          <h2>历史草稿</h2>
          <p v-if="!data.drafts.length">尚无保存版本。</p>
          <Button
            v-for="item in data.drafts"
            :key="item.revision"
            variant="text"
            :disabled="busy || saving"
            @click="restore(item.revision)"
            >恢复修订 {{ item.revision }}</Button
          >
        </div>
        <div>
          <h2>提交历史</h2>
          <p>历史只读；读取旧代码不会覆盖当前草稿。</p>
          <p v-if="!data.jobs.length">尚无提交记录。</p>
          <Button
            v-for="item in data.jobs"
            :key="item.id"
            variant="text"
            :disabled="busy"
            @click="readJob(item.id)"
            >#{{ item.id }} · {{ item.submission_type }} ·
            {{ labels[item.judge_status] }} · 题版
            {{ item.problem_version }}</Button
          >
        </div>
      </section>
      <section v-if="job" class="result" aria-label="评测结果">
        <h2>#{{ job.id }} · {{ labels[job.judgeStatus] }}</h2>
        <p>{{ job.message }}</p>
        <p>
          题版：{{ job.questionVersion }} · 得分：{{
            job.score === null ? "未计分" : job.score
          }}
        </p>
        <p v-if="job.executionTimeMs != null">
          运行耗时：{{ job.executionTimeMs }} ms
        </p>
        <pre aria-label="冻结的提交源码">{{ job.sourceCode }}</pre>
        <Button :disabled="busy" @click="readJob(job.id)">刷新任务状态</Button
        ><Button
          v-if="['SYSTEM_ERROR', 'JUDGE_UNAVAILABLE'].includes(job.judgeStatus)"
          :disabled="busy"
          @click="retryJob"
          >重试评测（复用原代码）</Button
        ><Button :disabled="busy" @click="askTutor"
          >问AI：为什么没有通过？</Button
        >
        <p>
          AI
          建议不能修改客观评测结果。点击后仅向原笔记导师准备公开题意、本人代码和状态。过长内容会在导师笔记中明确标注节选，原提交完整保留。
        </p>
        <p v-for="attempt in job.attempts" :key="attempt.attempt_no">
          尝试 {{ attempt.attempt_no }}：{{ labels[attempt.state] }}
        </p>
        <template v-if="tutorContext?.jobId === job.id">
          <RouterLink :to="`/notes/${tutorContext.noteId}/edit`"
            >查看本次代码导师笔记</RouterLink
          >
          <NotesTutor
            :key="tutorContext.noteId"
            :note-id="tutorContext.noteId"
          />
        </template>
      </section>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
import Button from "@/components/ui/Button.vue";
import NotesTutor from "@/components/notes/NotesTutor.vue";
import {
  javaPracticeApi as api,
  type Workspace,
  type PracticeJob,
  type JobInput,
} from "@/api/javaPractice";
import { JUDGE_STATUS_LABELS as labels } from "@/api/codeBank";
import { useJavaDraft } from "@/composables/useJavaDraft";
import { useCodeFormatting } from "@/composables/useCodeFormatting";
import { confirmDialog, getApiError } from "@/utils/toast";
const props = defineProps<{ problemId: number }>();
const data = ref<Workspace | null>(null),
  job = ref<PracticeJob | null>(null);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  loadError = ref(""),
  area = ref<HTMLTextAreaElement | null>(null);
const {
  source,
  revision,
  dirty,
  saving,
  error: saveError,
  initialize,
  save,
} = useJavaDraft(props.problemId);
const context = computed(() => `java-workspace:${props.problemId}`),
  language = ref("java");
const {
  settings,
  formatting,
  message: formatMessage,
  persistenceError,
  format,
  beforeSave,
  onInput,
  onPaste,
  onCompositionStart,
  onCompositionEnd,
  handleHistory,
  cancelAll,
} = useCodeFormatting({ source, language, context, area, blocked: busy });
const pendingJob = ref<JobInput | null>(null);
const tutorContext = ref<{ jobId: number; noteId: number } | null>(null);
let retryKey: { id: number; key: string } | null = null,
  timer: ReturnType<typeof setTimeout> | undefined,
  alive = true;
async function refresh() {
  const value = await api.workspace(props.problemId);
  if (alive) data.value = value;
}
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    await refresh();
    if (data.value && alive) {
      initialize(
        data.value.problem.draftCode ?? "",
        data.value.draftRevision,
        data.value.draftHash,
      );
      if (data.value.problem.draftCode == null)
        source.value = data.value.problem.starterCode ?? "";
    }
  } catch (e) {
    if (alive) loadError.value = getApiError(e, "题目读取失败");
  } finally {
    loading.value = false;
  }
}
async function reload() {
  if (
    await confirmDialog(
      "重新加载会替换当前编辑内容，请先复制需要保留的本地代码。",
    )
  )
    await load();
}
async function saveCode() {
  clearTimeout(timer);
  await beforeSave();
  const ok = await save();
  if (ok) await refresh().catch(() => {});
  return ok;
}
// 自动保存失败不无限重试；保存中新增的编辑在前一个请求完成后再次调度。
function schedule() {
  clearTimeout(timer);
  if (dirty.value && !saveError.value && !busy.value && !saving.value)
    timer = setTimeout(() => void saveCode(), 1800);
}
watch(source, schedule);
watch(saving, (value) => {
  if (!value) schedule();
});
async function restore(version: number) {
  if (
    !(await confirmDialog(
      `恢复修订 ${version} 到编辑器？当前未保存内容会被替换，历史版本仍保留。`,
    ))
  )
    return;
  busy.value = true;
  cancelAll();
  clearTimeout(timer);
  const original = source.value;
  try {
    const result = await api.draft(props.problemId, version);
    if (alive && source.value === original) source.value = result.sourceCode;
  } catch (e) {
    error.value = getApiError(e, "历史草稿读取失败");
  } finally {
    busy.value = false;
    schedule();
  }
}
async function submit(type: "RUN" | "SUBMIT") {
  if (busy.value || saving.value || formatting.value || pendingJob.value)
    return;
  await beforeSave();
  pendingJob.value = {
    requestId: crypto.randomUUID(),
    language: "java",
    sourceCode: source.value,
    type,
  };
  await retrySubmission();
}
async function retrySubmission() {
  if (!pendingJob.value || busy.value) return;
  busy.value = true;
  error.value = "";
  cancelAll();
  clearTimeout(timer);
  try {
    const result = await api.submit(props.problemId, pendingJob.value);
    if (alive) {
      job.value = result;
      pendingJob.value = null;
      await refresh();
    }
  } catch (e) {
    error.value = getApiError(
      e,
      "提交结果未知，请重试原提交请求；代码不会丢失",
    );
  } finally {
    busy.value = false;
    schedule();
  }
}
async function readJob(id: number) {
  busy.value = true;
  error.value = "";
  try {
    const result = await api.job(id);
    if (alive) job.value = result;
  } catch (e) {
    error.value = getApiError(e, "提交读取失败");
  } finally {
    busy.value = false;
    schedule();
  }
}
async function retryJob() {
  if (!job.value) return;
  busy.value = true;
  error.value = "";
  retryKey =
    retryKey?.id === job.value.id
      ? retryKey
      : { id: job.value.id, key: crypto.randomUUID() };
  try {
    job.value = await api.retry(retryKey.id, retryKey.key);
    retryKey = null;
    await refresh();
  } catch (e) {
    error.value = getApiError(e, "重试失败，可再次重试同一请求");
  } finally {
    busy.value = false;
  }
}
async function askTutor() {
  if (!job.value) return;
  busy.value = true;
  error.value = "";
  try {
    const result = await api.tutor(job.value.id);
    tutorContext.value = { jobId: job.value.id, noteId: result.noteId };
  } catch (e) {
    error.value = getApiError(e, "导师暂不可用；客观提交已保留");
  } finally {
    busy.value = false;
  }
}
function unload(event: BeforeUnloadEvent) {
  if (dirty.value || saving.value || pendingJob.value) {
    event.preventDefault();
    event.returnValue = "";
  }
}
async function protect() {
  if (!dirty.value && !pendingJob.value && !saving.value) return true;
  return confirmDialog(
    "还有未保存代码或未确认提交，是否离开？请先保存或复制源码。",
  );
}
onBeforeRouteLeave(protect);
onBeforeRouteUpdate(protect);
onMounted(() => {
  void load();
  window.addEventListener("beforeunload", unload);
});
onBeforeUnmount(() => {
  alive = false;
  clearTimeout(timer);
  window.removeEventListener("beforeunload", unload);
});
</script>
<style scoped>
.java-workspace {
  background: #f7f8fa;
  min-height: 100vh;
  padding: 24px;
  color: #202936;
}
header,
.toolbar,
.preferences {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
header {
  margin-bottom: 20px;
}
h1 {
  font-size: var(--kf-fs-h3);
  font-weight: 650;
  flex: 1;
}
h2 {
  font-size: var(--kf-fs-body-lg);
  font-weight: 600;
  margin: 10px 0;
}
h3 {
  font-weight: 600;
  margin-top: 24px;
}
p {
  white-space: pre-wrap;
  line-height: 1.8;
  margin: 8px 0;
}
.notice {
  background: #fff8e8;
  border: 1px solid #e8ce8e;
  padding: 16px;
  border-radius: var(--kf-radius-lg);
  margin-bottom: 18px;
}
main {
  display: grid;
  grid-template-columns: minmax(250px, 1fr) minmax(350px, 1.6fr);
  gap: 20px;
}
.problem,
.editor,
.history,
.result {
  background: white;
  border: 1px solid #e1e5eb;
  border-radius: var(--kf-radius-lg);
  padding: 20px;
  min-width: 0;
}
.toolbar h2 {
  flex: 1;
}
.preferences {
  font-size: var(--kf-fs-body-md);
  margin: 12px 0;
}
.preferences label {
  display: flex;
  align-items: center;
  gap: 5px;
}
textarea {
  width: 100%;
  min-height: 420px;
  resize: vertical;
  background: #f9fafc;
  border: 1px solid #ccd3dd;
  border-radius: var(--kf-radius-md);
  padding: 16px;
  font:
    14px/1.7 ui-monospace,
    monospace;
  tab-size: 2;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #f4f6f9;
  padding: 12px;
  border-radius: 6px;
  font:
    13px/1.7 ui-monospace,
    monospace;
}
.history {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 24px;
  margin-top: 20px;
}
.result {
  margin-top: 20px;
}
[role="alert"] {
  color: #b42318;
}
@media (max-width: 850px) {
  .java-workspace {
    padding: 12px;
  }
  main,
  .history {
    grid-template-columns: 1fr;
  }
  textarea {
    min-height: 340px;
  }
}
</style>
