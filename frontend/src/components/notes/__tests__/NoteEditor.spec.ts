// @vitest-environment happy-dom
import { mount } from "@vue/test-utils";
import { expect, it } from "vitest";
import NoteEditor from "../NoteEditor.vue";
it("读取未知学习块、公式与HTML不自动重写正文，外部更新原样显示", async () => {
  const text =
    '<!-- 原始注释 -->\n```quiz-run\n{"题":"保留"}\n```\n\n$$x^2$$\n\n<u>未知</u>\n';
  const w = mount(NoteEditor, { props: { modelValue: text } });
  expect(w.find("textarea").element.value).toBe(text);
  expect(w.emitted("update:modelValue")).toBeUndefined();
  await w.setProps({ modelValue: text + "外部更新" });
  expect(w.find("textarea").element.value).toBe(text + "外部更新");
  expect(w.emitted("update:modelValue")).toBeUndefined();
  w.unmount();
});
it("中文组合输入完成后才提交正文", async () => {
  const w = mount(NoteEditor, { props: { modelValue: "" } }),
    el = w.find("textarea");
  await el.trigger("compositionstart");
  await el.setValue("中文");
  expect(w.emitted("update:modelValue")).toBeUndefined();
  await el.trigger("compositionend");
  expect(w.emitted("update:modelValue")![0]).toEqual(["中文"]);
  w.unmount();
});
