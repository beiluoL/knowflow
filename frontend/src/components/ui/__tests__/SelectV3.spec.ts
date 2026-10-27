// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { describe, it, expect, vi, afterEach } from "vitest";
import Select from "../Select.vue";
const options = Array.from({ length: 12 }, (_, i) => ({
  value: i,
  label: "Choice " + i,
}));
const mounts: ReturnType<typeof mount>[] = [];
function render(props: Record<string, unknown> = {}) {
  const w = mount(Select, {
    attachTo: document.body,
    props: { modelValue: null, options, ...props },
    global: { stubs: { Icon: true } },
  });
  mounts.push(w);
  return w;
}
afterEach(() => {
  mounts.forEach((w) => w.unmount());
  mounts.length = 0;
  vi.useRealTimers();
});
async function open(w: ReturnType<typeof render>) {
  await w.get("button").trigger("click");
  await flushPromises();
}
describe("Select V3", () => {
  it("automatically searches at threshold, with explicit override and no value writes", async () => {
    const w = render();
    await open(w);
    expect(document.querySelector("[role=combobox]")).not.toBeNull();
    expect(w.emitted("update:modelValue")).toBeUndefined();
    w.unmount();
    mounts.pop();
    const small = render({ searchable: false });
    await open(small);
    expect(document.querySelector("[role=combobox]")).toBeNull();
  });
  it("debounces async options and ignores an old response and unmount", async () => {
    vi.useFakeTimers();
    const pending: {
      query: string;
      signal: AbortSignal;
      resolve: (v: typeof options) => void;
    }[] = [];
    const loadOptions = vi.fn(
      (query: string, signal: AbortSignal) =>
        new Promise<typeof options>((resolve) =>
          pending.push({ query, signal, resolve }),
        ),
    );
    const w = render({ loadOptions, debounceMs: 40 });
    await open(w);
    await vi.advanceTimersByTimeAsync(1);
    expect(pending).toHaveLength(1);
    const input = document.querySelector<HTMLInputElement>("[role=combobox]")!;
    input.value = "new";
    input.dispatchEvent(new Event("input", { bubbles: true }));
    await vi.advanceTimersByTimeAsync(45);
    expect(pending).toHaveLength(2);
    expect(pending[0]!.signal.aborted).toBe(true);
    pending[1]!.resolve([{ value: 2, label: "Newest" }]);
    await flushPromises();
    pending[0]!.resolve([{ value: 1, label: "Stale" }]);
    await flushPromises();
    expect(document.body.textContent).toContain("Newest");
    expect(document.body.textContent).not.toContain("Stale");
    expect(w.emitted("update:modelValue")).toBeUndefined();
    input.value = "later";
    input.dispatchEvent(new Event("input", { bubbles: true }));
    await vi.advanceTimersByTimeAsync(45);
    w.unmount();
    mounts.pop();
    expect(pending.at(-1)!.signal.aborted).toBe(true);
  });
  it("preserves native required form validation for custom controls", async () => {
    const w = render({
      required: true,
      options: [
        { value: "", label: "Choose" },
        { value: 0, label: "Zero" },
      ],
      modelValue: "",
    });
    expect(
      (w.element as Element)
        .querySelector<HTMLSelectElement>("select")
        ?.checkValidity(),
    ).toBe(false);
    await w.setProps({ modelValue: 0 });
    expect(
      (w.element as Element)
        .querySelector<HTMLSelectElement>("select")
        ?.checkValidity(),
    ).toBe(true);
  });
});

it("remote result filtering keeps the selected label and native validity without declaring it deleted", async () => {
  const w = render({ remote: true, required: true, modelValue: 0 });
  await w.setProps({ options: [{ value: 9, label: "Other result" }] });
  expect(w.get("button").text()).toContain("Choice 0");
  expect(w.text()).not.toContain("不可用");
  expect(
    (w.element as Element)
      .querySelector<HTMLSelectElement>("select")
      ?.checkValidity(),
  ).toBe(true);
});
