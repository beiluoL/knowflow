// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { beforeEach, it, expect, vi } from "vitest";
const list = vi.hoisted(() => vi.fn());
vi.mock("@/api", () => ({
  categoriesApi: {},
  docsApi: {},
  knowledgeImportApi: { listEditableKbs: list },
}));
vi.mock("vue-router", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/composables/usePageNavigation", () => ({
  usePageNavigation: () => ({ goBack: vi.fn() }),
}));
vi.mock("@/utils/toast", () => ({
  notify: vi.fn(),
  getApiError: () => "读取失败",
}));
import Page from "../KnowledgeUpload.vue";
beforeEach(() => {
  list.mockReset();
});
it("keeps knowledge and category numeric ids and clears only the dependent category on user change", async () => {
  list.mockResolvedValue([
    { id: 101, name: "知识库", children: [{ id: 202, name: "分类" }] },
  ]);
  const w = mount(Page, {
    global: { stubs: { Icon: true, RouterLink: true } },
  });
  await flushPromises();
  const selects = w.findAllComponents({ name: "Select" });
  expect(selects).toHaveLength(1);
  expect(selects[0]!.props("panelWidth")).toBe(340);
  selects[0]!.vm.$emit("update:modelValue", 101);
  await flushPromises();
  w.findComponent({name:"CategoryTreeSelect"}).vm.$emit("update:modelValue", 202);
  await flushPromises();
  expect(w.findComponent({name:"CategoryTreeSelect"}).props("modelValue")).toBe(
    202,
  );
  selects[0]!.vm.$emit("update:modelValue", 101);
  await flushPromises();
  expect(w.findComponent({name:"CategoryTreeSelect"}).props("modelValue")).toBe(
    null,
  );
  expect(list).toHaveBeenCalledOnce();
  w.unmount();
});
it("exposes async list failure and retries instead of presenting it as an empty result", async () => {
  list.mockRejectedValueOnce(Error("offline")).mockResolvedValueOnce([]);
  const w = mount(Page, {
    global: { stubs: { Icon: true, RouterLink: true } },
  });
  await flushPromises();
  const select = w.findAllComponents({ name: "Select" })[0]!;
  expect(select.props("loadError")).toContain("读取失败");
  select.vm.$emit("retry");
  await flushPromises();
  expect(list).toHaveBeenCalledTimes(2);
  expect(w.findAllComponents({ name: "Select" })[0]!.props("loadError")).toBe(
    "",
  );
  w.unmount();
});
