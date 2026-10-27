// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { expect, it, vi } from "vitest";
const state = vi.hoisted(() => ({
  finish: null as null | (() => void),
  creating: false,
  destroy: vi.fn(),
}));
vi.mock("@milkdown/crepe", () => ({
  Crepe: class {
    static Feature={TopBar:"top-bar",ImageBlock:"image-block"};
    on() {}
    create() {
      state.creating = true;
      return new Promise<void>(
        (r) =>
          (state.finish = () => {
            state.creating = false;
            r();
          }),
      );
    }
    destroy() {
      state.destroy();
      return Promise.resolve();
    }
    getMarkdown() {
      return "原文";
    }
    setReadonly() {}
  },
}));
import NoteEditor from "../NoteEditor.vue";
it("加载可视化时快速返回源码，先完成创建再销毁且不改正文", async () => {
  const w = mount(NoteEditor, { props: { modelValue: "原文" } });
  await w.findAll("button")[1]!.trigger("click");
  await flushPromises();
  expect(state.creating).toBe(true);
  await w.findAll("button")[0]!.trigger("click");
  await flushPromises();
  expect(state.destroy).not.toHaveBeenCalled();
  state.finish!();
  await flushPromises();
  expect(state.destroy).toHaveBeenCalledOnce();
  expect(w.text()).not.toContain("加载编辑器");
  expect(w.find("textarea").element.value).toBe("原文");
  expect(w.emitted("update:modelValue")).toBeUndefined();
  w.unmount();
});
