<template>
  <section
    v-if="scan.entries.length || scan.errors.length"
    class="legacy-notes p-3 border rounded-lg"
  >
    <button v-if="!embedded" class="kf-btn" @click="open = !open">
      检测到旧版本地笔记（{{ scan.entries.length }} 项）
    </button>
    <div v-if="open" class="flex flex-col gap-3">
      <p>
        已保存
        {{ scan.entries.filter((e) => e.kind === "saved").length }}
        项；草稿及预填
        {{ scan.entries.filter((e) => e.kind !== "saved").length }}
        项。原始数据不会删除。
      </p>
      <p v-for="error in scan.errors" :key="error" role="alert">{{ error }}</p>
      <button class="kf-btn" @click="backup">导出原始备份</button>
      <p v-if="account">
        目标账户：{{ account.username }}（ID {{ account.id }}）
      </p>
      <p v-if="accountError" role="alert">{{ accountError }}</p>
      <label class="kf-checkbox-row"
        ><Checkbox as="span"
          v-model="confirmed"
          :disabled="busy" />我确认这些本地数据属于此账户，并同意上传</label
      >
      <div class="legacy-preview max-h-64 overflow-auto">
        <details v-for="entry in scan.entries" :key="entry.key">
          <summary>
            {{ entry.kind === "saved" ? "笔记" : "草稿/预填" }}：{{
              entry.payload.title
            }}
          </summary>
          <pre class="whitespace-pre-wrap">{{ entry.payload.content }}</pre>
        </details>
      </div>
      <button
        class="kf-btn kf-btn-primary"
        :disabled="!confirmed || !account || busy"
        @click="migrate"
      >
        {{ busy ? "正在核对导入…" : "确认迁移 / 重试失败项" }}
      </button>
      <p
        v-for="result in results"
        :key="result.key"
        :role="result.error ? 'alert' : 'status'"
      >
        {{ result.key }}：{{ result.error || `已核对保存 #${result.id}` }}
      </p>
    </div>
  </section>
</template>
<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { ref, onMounted } from "vue";
import { authApi } from "@/api/auth";
import { notesApi } from "@/api/notes";
import { scanLegacy, importEntries } from "@/notes/legacy-migration";
import { getApiError } from "@/utils/toast";
const props=defineProps<{embedded?:boolean}>();
const emit = defineEmits<{ imported: [] }>();
const scan = scanLegacy(localStorage),
  open = ref(props.embedded??false),
  confirmed = ref(false),
  busy = ref(false);
const account = ref<{ id: number; username: string } | null>(null),
  accountError = ref("");
const results = ref<{ key: string; id?: number; error?: string }[]>([]);
onMounted(async () => {
  if (!scan.entries.length) return;
  try {
    account.value = await authApi.me();
    for (const entry of scan.entries) {
      const raw = localStorage.getItem(
        `notes-import-receipt:${account.value.id}:${encodeURIComponent(entry.key)}`,
      );
      if (!raw) continue;
      try {
        const receipt = JSON.parse(raw);
        if (
          receipt.title !== entry.payload.title ||
          receipt.content !== entry.payload.content
        )
          continue;
        const n = await notesApi.detail(receipt.id);
        if (
          n.title === entry.payload.title &&
          n.content === entry.payload.content
        )
          results.value.push({ key: entry.key, id: n.id });
      } catch {
        /* 无法读回时不展示迁移成功，用户仍可重试核对 */
      }
    }
  } catch (e) {
    accountError.value = getApiError(e, "无法确认目标账户，请刷新重试");
  }
});
function backup() {
  const url = URL.createObjectURL(
    new Blob(
      [
        JSON.stringify(
          { exportedAt: new Date().toISOString(), raw: scan.raw },
          null,
          2,
        ),
      ],
      { type: "application/json" },
    ),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = "knowflow-legacy-notes-backup.json";
  a.click();
  URL.revokeObjectURL(url);
}
async function migrate() {
  if (!account.value || !confirmed.value) return;
  busy.value = true;
  accountError.value = "";
  try {
    const current = await authApi.me();
    if (current.id !== account.value.id)
      throw Error("账户已变化，请刷新并重新确认");
    const successful = new Set(
      results.value.filter((r) => !r.error).map((r) => r.key),
    );
    const entries = scan.entries.filter((e) => !successful.has(e.key));
    const token = localStorage.getItem("token");
    const guardedApi = {
      importLegacy: async (p: Parameters<typeof notesApi.importLegacy>[0]) => {
        if (localStorage.getItem("token") !== token)
          throw Error("账户已变化，请刷新并重新确认");
        return notesApi.importLegacy(p);
      },
      detail: notesApi.detail,
    };
    const incoming = await importEntries(
      entries,
      current.id,
      confirmed.value,
      guardedApi,
      localStorage,
    );
    const next = new Map(results.value.map((r) => [r.key, r]));
    incoming.forEach((r) => next.set(r.key, r));
    results.value = [...next.values()];
    emit("imported");
  } catch (e) {
    accountError.value = getApiError(e, "迁移失败，请重试");
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.legacy-notes {
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
}
.legacy-notes > div {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding-top: var(--kf-space-3);
}
.legacy-notes .kf-btn {
  padding: var(--kf-space-2) var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  text-align: left;
}
.legacy-notes button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.legacy-preview {
  max-height: 16rem;
  overflow: auto;
}
.legacy-preview pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.legacy-notes label {
  display: flex;
  align-items: flex-start;
  gap: var(--kf-space-2);
}
</style>
