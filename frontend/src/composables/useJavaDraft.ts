import { computed, onScopeDispose, ref } from "vue";
import { javaPracticeApi, type DraftInput } from "@/api/javaPractice";
import { getApiError } from "@/utils/toast";

// 保存请求串行化；网络不确定时先复用原幂等编号，旧响应只确认它自己的源码快照。
export function useJavaDraft(
  id: number,
  send: typeof javaPracticeApi.save = javaPracticeApi.save,
) {
  const source = ref(""),
    revision = ref(0),
    savedSource = ref(""),
    saving = ref(false),
    error = ref("");
  const pending = ref<DraftInput | null>(null);
  // 回包丢失时即使撤回到旧文本也仍有未确认写入，必须保留离开保护。
  const dirty = computed(
    () => source.value !== savedSource.value || pending.value !== null,
  );
  let baseHash: string | undefined,
    disposed = false;
  function initialize(code: string, version: number, hash: string) {
    source.value = code;
    savedSource.value = code;
    revision.value = version;
    baseHash = hash;
    pending.value = null;
    error.value = "";
  }
  async function save() {
    if (saving.value || disposed) return false;
    if (!dirty.value && !pending.value) return true;
    saving.value = true;
    error.value = "";
    pending.value ||= {
      requestId: crypto.randomUUID(),
      expectedRevision: revision.value,
      language: "java",
      sourceCode: source.value,
      baseHash,
    };
    const request = pending.value;
    try {
      const result = await send(id, request);
      if (disposed) return false;
      revision.value = result.draftRevision;
      savedSource.value = request.sourceCode;
      baseHash =
        typeof result.draftHash === "string" ? result.draftHash : undefined;
      pending.value = null;
      return !dirty.value;
    } catch (e) {
      if (!disposed)
        error.value = getApiError(
          e,
          "保存失败，原代码保留；可重试或先复制代码",
        );
      return false;
    } finally {
      saving.value = false;
    }
  }
  onScopeDispose(() => {
    disposed = true;
  });
  return { source, revision, dirty, saving, error, initialize, save };
}
