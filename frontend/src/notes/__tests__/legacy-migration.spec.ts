// @vitest-environment happy-dom
import { beforeEach, expect, it } from "vitest";
import { scanLegacy, importEntries } from "../legacy-migration";
beforeEach(() => localStorage.clear());
it("区分草稿、预填与正式笔记，排除示例并保留损坏原始数据", () => {
  localStorage.setItem(
    "note-list",
    JSON.stringify([
      { id: -1, title: "示例", content: "假" },
      { id: 3, title: "旧笔记", content: "正文" },
    ]),
  );
  localStorage.setItem(
    "note-3-draft",
    JSON.stringify({ title: "草稿", content: "修改" }),
  );
  localStorage.setItem(
    "note-from-doc",
    JSON.stringify({ fromDocTitle: "来源", content: "预填" }),
  );
  localStorage.setItem("note-4", "{broken");
  const r = scanLegacy(localStorage);
  expect(r.entries).toHaveLength(3);
  expect(r.entries.filter((e) => e.payload.status === "DRAFT")).toHaveLength(2);
  expect(r.errors).toHaveLength(1);
  expect(localStorage.getItem("note-4")).toBe("{broken");
});
it("要求主动确认并逐项重试，只在读回一致后记录回执", async () => {
  localStorage.setItem(
    "note-list",
    JSON.stringify([
      { id: 3, title: "甲", content: "正文甲" },
      { id: 4, title: "乙", content: "正文乙" },
    ]),
  );
  const r = scanLegacy(localStorage);
  let attempts = 0;
  const api = {
    importLegacy: async (p: any) => {
      if (p.title === "乙" && attempts++ === 0) throw Error("断网");
      return { ...p, id: p.title === "甲" ? 1 : 2, revision: 1 };
    },
    detail: async (id: number) => ({
      ...r.entries[id - 1]!.payload,
      id,
      revision: 1,
    }),
  };
  await expect(
    importEntries(r.entries, 7, false, api, localStorage),
  ).rejects.toThrow("确认");
  const first = await importEntries(r.entries, 7, true, api, localStorage);
  expect(first.filter((x) => x.error)).toHaveLength(1);
  const second = await importEntries(r.entries, 7, true, api, localStorage);
  expect(second.every((x) => !x.error)).toBe(true);
  expect(localStorage.getItem("note-list")).toContain("正文甲");
});
