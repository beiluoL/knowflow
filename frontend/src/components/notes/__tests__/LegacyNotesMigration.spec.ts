// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { it, expect, vi } from "vitest";
const api = vi.hoisted(() => ({ importLegacy: vi.fn(), detail: vi.fn() }));
vi.mock("@/api/auth", () => ({
  authApi: { me: async () => ({ id: 7, username: "测试账户" }) },
}));
vi.mock("@/api/notes", () => ({ notesApi: api }));
import LegacyNotesMigration from "../LegacyNotesMigration.vue";
import { scanLegacy } from "@/notes/legacy-migration";
it("刷新后存在成功回执时，仍迁移所有尚未成功的条目", async () => {
  localStorage.clear();
  const entries = [
    { id: 1, title: "已成功", content: "甲" },
    { id: 2, title: "待重试", content: "乙" },
  ];
  localStorage.setItem("note-list", JSON.stringify(entries));
  const key = scanLegacy(localStorage).entries[0]!.key;
  localStorage.setItem(
    `notes-import-receipt:7:${encodeURIComponent(key)}`,
    JSON.stringify({ id: 1, title: "已成功", content: "甲" }),
  );
  api.detail.mockImplementation(async (id) => entries[id - 1]);
  api.importLegacy.mockResolvedValue({ id: 2, revision: 1 });
  const w = mount(LegacyNotesMigration);
  await flushPromises();
  await w.find("button").trigger("click");
  await w.find("input[type=checkbox]").setValue(true);
  await w
    .findAll("button")
    .find((b) => b.text() === "确认迁移 / 重试失败项")!
    .trigger("click");
  await flushPromises();
  expect(api.importLegacy).toHaveBeenCalledOnce();
  expect(api.importLegacy.mock.calls[0]![0].title).toBe("待重试");
  w.unmount();
});
