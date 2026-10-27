// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { describe, it, expect, vi, afterEach } from "vitest";
import Cascader from "../CascaderSelect.vue";
import CategoryTreeSelect from "../CategoryTreeSelect.vue";
import AppDialog from "../AppDialog.vue";
import { h } from "vue";
const options = [
  {
    id: 1,
    value: 1,
    label: "Java",
    children: [
      {
        id: 2,
        value: 2,
        label: "并发",
        children: [{ id: 3, value: 3, label: "线程" }],
      },
      { id: 4, value: 4, label: "不可选", disabled: true },
    ],
  },
];
const mounted: ReturnType<typeof mount>[] = [];
function render(props: Record<string, unknown> = {}) {
  const w = mount(Cascader, {
    attachTo: document.body,
    props: { modelValue: null, options, ...props },
    global: { stubs: { Icon: true } },
  });
  mounted.push(w);
  return w;
}
const tick = () => flushPromises();
afterEach(() => {
  mounted.forEach((w) => w.unmount());
  mounted.length = 0;
  vi.useRealTimers();
});
describe("Cascader V3", () => {
  it("opens nested hover without submitting, and selects only the leaf", async () => {
    vi.useFakeTimers();
    const w = render();
    await w.get("button").trigger("click");
    await tick();
    const parent = document.querySelector<HTMLElement>("[role=treeitem]")!;
    parent.dispatchEvent(new MouseEvent("pointerenter", { bubbles: false }));
    await vi.advanceTimersByTimeAsync(160);
    await tick();
    expect(document.querySelectorAll("[data-cascade-column]")).toHaveLength(2);
    expect(w.emitted("update:modelValue")).toBeUndefined();
    const child = [
      ...document.querySelectorAll<HTMLElement>("[role=treeitem]"),
    ].find((e) => e.textContent?.includes("并发"))!;
    child.click();
    await tick();
    expect(document.querySelectorAll("[data-cascade-column]")).toHaveLength(3);
    [...document.querySelectorAll<HTMLElement>("[role=treeitem]")]
      .find((e) => e.textContent?.includes("线程"))!
      .click();
    await tick();
    expect(w.emitted("update:modelValue")).toEqual([[3]]);
  });
  it("navigates right/left/escape and separates choosing a parent from expanding it", async () => {
    const w = render();
    await w.get("button").trigger("keydown", { key: "ArrowDown" });
    await tick();
    document.activeElement!.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }),
    );
    await tick();
    expect(document.querySelectorAll("[data-cascade-column]")).toHaveLength(2);
    document.activeElement!.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true }),
    );
    await tick();
    expect(document.querySelectorAll("[data-cascade-column]")).toHaveLength(1);
    document
      .querySelector<HTMLButtonElement>('[aria-label="选择 Java"]')!
      .click();
    await tick();
    expect(w.emitted("update:modelValue")).toEqual([[1]]);
  });
  it("filters paths without writing and prevents disabled or stale values", async () => {
    const w = render({ searchThreshold: 0 });
    await w.get("button").trigger("click");
    await tick();
    const input =
      document.querySelector<HTMLInputElement>("input[type=search]")!;
    input.value = "线程";
    input.dispatchEvent(new Event("input", { bubbles: true }));
    await tick();
    expect(document.body.textContent).toContain("Java / 并发 / 线程");
    expect(w.emitted("update:modelValue")).toBeUndefined();
    await w.setProps({ modelValue: 99 });
    expect(w.text()).toContain("不可用");
  });
  it("adapts numeric category ids, empty undefined and excluded parent selection", async () => {
    const w = mount(CategoryTreeSelect, {
      attachTo: document.body,
      props: {
        modelValue: 2,
        categories: [
          { id: 1, name: "Java", children: [{ id: 2, name: "并发" }] },
        ],
        excludeIds: [1],
      },
      global: { stubs: { Icon: true } },
    });
    mounted.push(w);
    await w.get("button").trigger("click");
    await tick();
    expect(
      document.querySelector<HTMLButtonElement>('[aria-label="选择 Java"]')!
        .disabled,
    ).toBe(true);
    document.querySelector<HTMLButtonElement>("[data-cascade-empty]")!.click();
    await tick();
    expect(w.emitted("update:modelValue")).toEqual([[undefined]]);
  });
});

describe("cascade lifecycle boundaries", () => {
  it("keeps child columns while crossing the gap, closes outside and rejects disabled leaves", async () => {
    vi.useFakeTimers();
    const w = render();
    await w.get("button").trigger("click");
    await tick();
    document
      .querySelector<HTMLElement>('[data-node="1"]')!
      .dispatchEvent(new Event("pointerenter"));
    await vi.advanceTimersByTimeAsync(130);
    await tick();
    document
      .querySelector<HTMLElement>("[role=tree]")!
      .dispatchEvent(new Event("pointerleave"));
    expect(document.querySelectorAll("[data-cascade-column]")).toHaveLength(2);
    const disabled =
      document.querySelector<HTMLButtonElement>('[data-node="4"]')!;
    expect(disabled.disabled).toBe(true);
    disabled.click();
    expect(w.emitted("update:modelValue")).toBeUndefined();
    document.body.dispatchEvent(new Event("pointerdown", { bubbles: true }));
    await tick();
    expect(document.querySelector("[role=tree]")).toBeNull();
  });
  it("does not run pending hover or retain a tree after unmount", async () => {
    vi.useFakeTimers();
    const w = render();
    await w.get("button").trigger("click");
    await tick();
    document
      .querySelector<HTMLElement>('[data-node="1"]')!
      .dispatchEvent(new Event("pointerenter"));
    w.unmount();
    mounted.pop();
    await vi.advanceTimersByTimeAsync(200);
    expect(document.querySelector("[role=tree]")).toBeNull();
  });
});

it("keeps branch expansion separate from parent selection in search results", async () => {
  const w = render({ searchThreshold: 0 });
  await w.get("button").trigger("click");
  await tick();
  const input = document.querySelector<HTMLInputElement>("input[type=search]")!;
  input.value = "Java";
  input.dispatchEvent(new Event("input", { bubbles: true }));
  await tick();
  document.querySelector<HTMLButtonElement>('[data-node="1"]')!.click();
  await tick();
  expect(w.emitted("update:modelValue")).toBeUndefined();
  expect(document.querySelectorAll("[data-cascade-column]")).toHaveLength(2);
});

it("Escape closes the owned cascade before its containing dialog", async () => {
  const w = mount(AppDialog, {
    attachTo: document.body,
    props: { open: true, scrollable: true, title: "分类设置" },
    slots: { default: () => h(Cascader, { modelValue: null, options }) },
    global: { stubs: { Icon: true } },
  });
  mounted.push(w);
  await w
    .findComponent({ name: "CascaderSelect" })
    .get("button")
    .trigger("click");
  await tick();
  document.activeElement!.dispatchEvent(
    new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
  );
  await tick();
  expect(w.emitted("cancel")).toBeUndefined();
  expect(document.querySelector("[role=tree]")).toBeNull();
});

it('Escape closes all levels and returns focus to the trigger',async()=>{const w=render();await w.get('button').trigger('click');await tick();document.querySelector<HTMLButtonElement>('[data-node="1"]')!.click();await tick();document.querySelector<HTMLElement>('[data-cascade-column="1"]')!.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await tick();expect(document.querySelector('[role=tree]')).toBeNull();expect(document.activeElement).toBe(w.get('button').element)})

it('exposes loading and retry separately from an empty result',async()=>{const retry=vi.fn(),w=render({options:[],loading:true,onRetry:retry});await w.get('button').trigger('click');await tick();expect(document.querySelector('[role=status]')?.textContent).toContain('加载');await w.setProps({loading:false,loadError:'知识库加载失败'});await tick();expect(document.querySelector('[role=alert]')?.textContent).toContain('知识库加载失败');document.querySelector<HTMLButtonElement>('[data-cascade-retry]')!.click();expect(retry).toHaveBeenCalledOnce();await w.setProps({loadError:''});await tick();expect(document.querySelector('[role=status]')?.textContent).toContain('暂无选项')})
