import type { NotePayload, ServerNote } from "@/api/notes";
export interface LegacyEntry {
  key: string;
  kind: "saved" | "draft" | "prefill";
  payload: NotePayload & { legacyKey: string };
}
/** 只读扫描；每个来源独立保留，避免同名或不同草稿互相覆盖。 */
export function scanLegacy(storage: Storage) {
  const entries: LegacyEntry[] = [],
    errors: string[] = [],
    raw: Record<string, string> = {};
  function add(key: string, data: unknown, kind: LegacyEntry["kind"]) {
    if (!data || typeof data !== "object") throw Error("记录格式错误");
    const n = data as Record<string, unknown>;
    if (n.isExample === true || (typeof n.id === "number" && n.id < 0)) return;
    if (
      typeof n.content !== "string" ||
      (n.title !== undefined && typeof n.title !== "string")
    )
      throw Error("标题或正文格式错误");
    entries.push({
      key,
      kind,
      payload: {
        legacyKey: key,
        title:
          (n.title as string) ||
          (typeof n.fromDocTitle === "string"
            ? `${n.fromDocTitle} - 学习笔记`
            : "未命名草稿"),
        content: n.content,
        tags: Array.isArray(n.tags)
          ? n.tags.filter((t) => typeof t === "string").join(",")
          : "",
        sourceTitle:
          typeof n.relatedDoc === "string"
            ? n.relatedDoc
            : typeof n.fromDocTitle === "string"
              ? n.fromDocTitle
              : "",
        status: kind === "saved" ? "SAVED" : "DRAFT",
      },
    });
  }
  for (let i = 0; i < storage.length; i++) {
    const key = storage.key(i)!;
    if (!/^(note-list|note-draft|note-\d+(?:-draft)?|note-from-doc)$/.test(key))
      continue;
    const value = storage.getItem(key)!;
    raw[key] = value;
    try {
      const data: unknown = JSON.parse(value);
      if (key === "note-list") {
        if (!Array.isArray(data)) throw Error("列表格式错误");
        data.forEach((n, index) => {
          try {
            add(
              `${key}:${(n as { id?: unknown })?.id ?? index}:${index}`,
              n,
              "saved",
            );
          } catch {
            errors.push(`${key} 第${index + 1}项格式错误，已保留`);
          }
        });
      } else add(key, data, key === "note-from-doc" ? "prefill" : "draft");
    } catch {
      errors.push(`${key} 无法解析，原数据已保留`);
    }
  }
  return { entries, errors, raw };
}
interface ImportApi {
  importLegacy: (
    p: NotePayload & { legacyKey: string },
  ) => Promise<Pick<ServerNote, "id" | "revision">>;
  detail: (id: number) => Promise<Pick<ServerNote, "id" | "content" | "title">>;
}
/** 回执按账户隔离；即使回执写失败，服务器仍通过内容指纹保证重试幂等。 */
export async function importEntries(
  entries: LegacyEntry[],
  userId: number,
  confirmed: boolean,
  api: ImportApi,
  storage: Storage,
) {
  if (!confirmed || !Number.isSafeInteger(userId) || userId <= 0)
    throw Error("请先确认迁移目标账户及数据归属");
  const results: { key: string; id?: number; error?: string }[] = [];
  for (const entry of entries)
    try {
      const saved = await api.importLegacy(entry.payload),
        read = await api.detail(saved.id);
      if (
        read.content !== entry.payload.content ||
        read.title !== entry.payload.title
      )
        throw Error("服务端读回不一致，请核对后重试");
      storage.setItem(
        `notes-import-receipt:${userId}:${encodeURIComponent(entry.key)}`,
        JSON.stringify({
          id: saved.id,
          revision: saved.revision,
          content: entry.payload.content,
          title: entry.payload.title,
        }),
      );
      results.push({ key: entry.key, id: saved.id });
    } catch (e: unknown) {
      results.push({
        key: entry.key,
        error: e instanceof Error ? e.message : "迁移失败",
      });
    }
  return results;
}
