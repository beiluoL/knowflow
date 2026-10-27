import {
  computed,
  ref,
  watch,
  onBeforeUnmount,
  toValue,
  type MaybeRef,
} from "vue";
import { notesApi, type NotePayload, type ServerNote } from "@/api/notes";
/** 防抖与串行保存；草稿必须携带 owner、笔记ID和基础 revision，禁止盲目恢复覆盖。 */
export function useNoteSession(
  owner: MaybeRef<number>,
  initialId: number | null,
  read: () => NotePayload,
  apply: (note: ServerNote) => void,
  onSaved?: (note:ServerNote)=>void,
  draftIdentity?:string,
) {
  const sessionToken = localStorage.getItem("token");
  const userId = computed(() => toValue(owner));
  const id = ref(initialId),
    revision = ref(1),
    ready = ref(false),
    paused = ref(false),
    recoveryRevision = ref<number | null>(null),
    saved = ref(""),
    status = ref("尚未保存"),
    conflict = ref<ServerNote | null>(null),
    busy = ref(false),
    error = ref("");
  const fingerprint = () => JSON.stringify(read()),
    dirty = computed(() => fingerprint() !== saved.value);
  const key = computed(
    () => `notes-recovery:${userId.value}:${id.value ?? draftIdentity ?? "new"}`,
  );
  let timer: ReturnType<typeof setTimeout> | undefined,
    pending: Promise<boolean> | null = null,
    disposed = false;
  function backup() {
    try {
      localStorage.setItem(
        key.value,
        JSON.stringify({
          userId: userId.value,
          id: id.value,
          revision: recoveryRevision.value ?? revision.value,
          needsReview: paused.value,
          payload: read(),
          savedAt: new Date().toISOString(),
        }),
      );
      return true;
    } catch {
      error.value = "本地备份失败，请立即导出修改副本";
      return false;
    }
  }
  async function save(): Promise<boolean> {
    if (timer) clearTimeout(timer);
    if (localStorage.getItem("token") !== sessionToken) {
      status.value = "账户已变化，已停止自动保存";
      error.value = "请导出当前修改后重新登录";
      backup();
      return false;
    }
    if (pending) {
      const ok = await pending;
      if (!ok) return false;
      return dirty.value ? save() : true;
    }
    if (!ready.value || paused.value || conflict.value || !read().title.trim())
      return false;
    if (!dirty.value) return true;
    backup();
    const previousKey=key.value;
    const payload = { ...read(), revision: revision.value },
      snapshot = JSON.stringify(read());
    busy.value = true;
    status.value = "保存中…";
    error.value = "";
    pending = (async () => {
      try {
        const n = id.value
          ? await notesApi.update(id.value, payload)
          : await notesApi.create(payload);
        id.value = n.id;
        revision.value = n.revision;
        saved.value = snapshot;
        if(!disposed)onSaved?.(n);
        // 首次创建的旧入口只保留服务端ID指针，后续修改存入该ID的恢复副本。
        if(previousKey!==key.value)try{localStorage.setItem(previousKey,JSON.stringify({userId:userId.value,id:n.id,revision:n.revision,payload,saved:true}))}catch{/* 当前ID副本仍在下方备份 */}
        if (dirty.value) {
          backup();
          status.value = "仍有待保存修改";
        } else {
          status.value = "已保存到服务器";
          try {
            if(previousKey!==key.value)localStorage.setItem(previousKey,JSON.stringify({userId:userId.value,id:n.id,revision:n.revision,payload,saved:true}));
            localStorage.setItem(
              key.value,
              JSON.stringify({
                userId: userId.value,
                id: n.id,
                revision: n.revision,
                payload,
                saved: true,
              }),
            );
          } catch {
            /* 服务端成功不被备份失败伪装为失败 */
          }
        }
        return true;
      } catch (e: unknown) {
        status.value = "保存失败，本地修改仍保留";
        error.value = e instanceof Error ? e.message : "保存失败";
        if ((e as { status?: number }).status === 409 && id.value) {
          paused.value = true;
          status.value = "这篇笔记已在其他位置修改";
          try {
            conflict.value = await notesApi.detail(id.value);
          } catch {
            error.value += "；无法读取服务器版本，请重试";
          }
        }
        return false;
      } finally {
        busy.value = false;
        pending = null;
      }
    })();
    const ok = await pending;
    if (ok && dirty.value && !disposed)
      timer = setTimeout(() => void save(), 900);
    return ok;
  }
  function acceptServer(n: ServerNote) {
    apply(n);
    id.value = n.id;
    revision.value = n.revision;
    saved.value = fingerprint();
    conflict.value = null;
    paused.value = false;
    recoveryRevision.value = null;
    status.value = "已加载服务器版本";
    error.value = "";
  }
  function start(n?: ServerNote, newPrefill = false) {
    if (n) acceptServer(n);
    else saved.value = newPrefill ? "" : fingerprint();
    ready.value = true;
  }
  watch(fingerprint, () => {
    if (!ready.value) return;
    if (timer) clearTimeout(timer);
    if (dirty.value) {
      status.value = "待保存";
      backup();
      if (!paused.value && !conflict.value)
        timer = setTimeout(() => void save(), 900);
    }
  });
  onBeforeUnmount(() => {
    disposed = true;
    if (timer) clearTimeout(timer);
    if (ready.value && dirty.value) backup();
  });
  return {
    id,
    revision,
    paused,
    recoveryRevision,
    ready,
    dirty,
    status,
    conflict,
    busy,
    error,
    key,
    save,
    backup,
    start,
    acceptServer,
  };
}
