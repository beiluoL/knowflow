// @vitest-environment happy-dom
import { describe, it, expect } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import SelectDialog from "../SelectDialog.vue";
const options = [
  { value: 1, label: "数字", group: "第一组" },
  { value: "1", label: "字符串", group: "第二组" },
  { value: 2, label: "禁用", disabled: true },
];
const render = () =>
  mount(SelectDialog, {
    props: { open: true, modelValue: [1], multiple: true, options },
    global: { stubs: { Teleport: true, Icon: true } },
  });
const click = async (w: ReturnType<typeof render>, text: string) =>
  w
    .findAll("button")
    .find((b) => b.text() === text)!
    .trigger("click");
describe("selection dialog drafts", () => {
  it("isolates cancellation and resets draft on reopen", async () => {
    const w = render();
    await click(w, "字符串");
    expect(w.emitted("update:modelValue")).toBeUndefined();
    await click(w, "取消");
    expect(w.emitted("cancel")).toHaveLength(1);
    await w.setProps({ open: false });
    await w.setProps({ open: true });
    expect(w.findAll("[aria-selected=true]")).toHaveLength(1);
    w.unmount();
  });
  it("confirms distinct typed values and exposes multi-select groups", async () => {
    const w = render();
    expect(w.get("[role=listbox]").attributes("aria-multiselectable")).toBe(
      "true",
    );
    expect(w.findAll("[role=group]")).toHaveLength(2);
    await click(w, "字符串");
    await click(w, "确定");
    expect(w.emitted("confirm")).toEqual([[[1, "1"]]]);
    expect(w.emitted("update:modelValue")).toEqual([[[1, "1"]]]);
    w.unmount();
  });
  it("filters and cannot select a disabled option", async () => {
    const w = render();
    await click(w, "禁用");
    await w.get("input").setValue("字符串");
    expect(w.findAll("[role=option]")).toHaveLength(1);
    await click(w, "确定");
    expect(w.emitted("confirm")).toEqual([[[1]]]);
    w.unmount();
  });
  it("shows real loading/errors and retries without confirming", async () => {
    const w = render();
    await w.setProps({ loading: true });
    expect(w.text()).toContain("正在加载");
    await click(w, "确定");
    expect(w.emitted("confirm")).toBeUndefined();
    await w.setProps({ loading: false, loadError: "读取失败" });
    await click(w, "重试");
    expect(w.emitted("retry")).toHaveLength(1);
    expect(w.find("[role=listbox]").exists()).toBe(false);
    await flushPromises();
    w.unmount();
  });
});

it("does not confirm stale ids removed by a later options response", async () => {
  const w = render();
  await w.setProps({ options: [options[1]!] });
  expect(w.text()).toContain("选项已不可用");
  await click(w, "确定");
  expect(w.emitted("confirm")).toBeUndefined();
  w.unmount();
});
