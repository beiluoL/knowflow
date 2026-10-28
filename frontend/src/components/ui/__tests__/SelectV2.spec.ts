// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { describe, it, expect, vi } from "vitest";
import Select from "../Select.vue";
const options = [
  { value: 1, label: "Alpha", group: "常用" },
  { value: "1", label: "Beta", group: "其他" },
  { value: 3, label: "Gamma", disabled: true },
];
function render(props: Record<string, unknown> = {}) {
  return mount(Select, {
    attachTo: document.body,
    props: { modelValue: 1, options, ...props },
    global: { stubs: { Teleport: true, Icon: true } },
  });
}
async function open(w: ReturnType<typeof render>) {
  await w.get(".kf-select-trigger").trigger("click");
  await flushPromises();
}
describe("Select V2 value and overlay contract", () => {
  it("opens without saving and keeps number/string values distinct", async () => {
    const w = render();
    await open(w);
    expect(w.emitted("update:modelValue")).toBeUndefined();
    await w.findAll("[role=option]")[1]!.trigger("click");
    expect(w.emitted("update:modelValue")).toEqual([["1"]]);
    expect(w.emitted("change")).toHaveLength(1);
    w.unmount();
  });
  it("keeps multiple selections open and does not duplicate ids", async () => {
    const w = render({ modelValue: [1, 1], multiple: true });
    await open(w);
    await w.findAll("[role=option]")[1]!.trigger("click");
    expect(w.emitted("update:modelValue")).toEqual([[[1, "1"]]]);
    expect(w.get("[role=listbox]").attributes("aria-multiselectable")).toBe(
      "true",
    );
    expect(w.get(".kf-select-trigger").attributes("aria-expanded")).toBe(
      "true",
    );
    w.unmount();
  });
  it("filters without committing and preserves a selected value removed by async options", async () => {
    const w = render({ searchable: true });
    await open(w);
    await w.get("[role=combobox]").setValue("beTA");
    expect(w.findAll("[role=option]")).toHaveLength(1);
    expect(w.emitted("update:modelValue")).toBeUndefined();
    await w.setProps({ options: [options[1]!] });
    expect(w.text()).toContain("选项已不可用");
    expect(w.emitted("update:modelValue")).toBeUndefined();
    w.unmount();
  });
  it("does not select while composing in the search input", async () => {
    const w = render({ searchable: true });
    await open(w);
    await w
      .get("[role=combobox]")
      .trigger("keydown", { key: "Enter", isComposing: true });
    expect(w.emitted("update:modelValue")).toBeUndefined();
    w.unmount();
  });
  it("shows loading/error/retry and never commits disabled or stale choices", async () => {
    const w = render({ loading: true });
    await open(w);
    expect(w.get("[role=status]").text()).toContain("加载");
    expect(w.findAll("[role=option]")).toHaveLength(0);
    await w.setProps({ loading: false, loadError: "读取失败" });
    expect(w.get("[role=alert]").text()).toContain("读取失败");
    await w.get("[data-select-retry]").trigger("click");
    expect(w.emitted("retry")).toHaveLength(1);
    expect(w.emitted("update:modelValue")).toBeUndefined();
    w.unmount();
  });
  it("supports groups, disabled choices and controlled reset", async () => {
    const w = render();
    await open(w);
    expect(w.findAll("[role=group]")).toHaveLength(2);
    await w.findAll("[role=option]")[2]!.trigger("click");
    expect(w.emitted("update:modelValue")).toBeUndefined();
    await w.setProps({ modelValue: null });
    expect(w.get(".kf-select-trigger").text()).toContain("请选择");
    w.unmount();
  });
  it("removes listeners and pending focus work after unmount", async () => {
    const add = vi.spyOn(document, "addEventListener"),
      remove = vi.spyOn(document, "removeEventListener");
    const w = render();
    await open(w);
    w.unmount();
    for (const [type, fn] of add.mock.calls.filter(
      ([type]) => type === "pointerdown",
    ))
      expect(remove.mock.calls.some(([t, f]) => t === type && f === fn)).toBe(
        true,
      );
    add.mockRestore();
    remove.mockRestore();
  });
});

it("navigates grouped choices in their rendered order", async () => {
  const w = mount(Select, {
    attachTo: document.body,
    props: {
      modelValue: 1,
      options: [
        { value: 1, label: "A1", group: "A" },
        { value: 2, label: "B1", group: "B" },
        { value: 3, label: "A2", group: "A" },
      ],
    },
    global: { stubs: { Icon: true } },
  });
  await w.get(".kf-select-trigger").trigger("keydown", { key: "ArrowDown" });
  await flushPromises();
  document
    .querySelector("[role=listbox]")!
    .dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }),
    );
  await flushPromises();
  expect(document.activeElement?.textContent).toContain("A2");
  w.unmount();
});
