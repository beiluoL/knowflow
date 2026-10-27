// @vitest-environment happy-dom
import { mount, flushPromises, enableAutoUnmount } from "@vue/test-utils";
import { describe, it, expect, vi, afterEach } from "vitest";
import Select from "../Select.vue";
enableAutoUnmount(afterEach);
describe("select presentation separates selection and focus", () => {
  it("uses a check for selection and reserves the active outline for keyboard input", async () => {
    const w = mount(Select, {
      attachTo: document.body,
      props: {
        modelValue: "",
        options: [
          { value: "", label: "全部" },
          { value: "one", label: "单选" },
        ],
      },
      global: { stubs: { Icon: true } },
    });
    await w.get("button").trigger("click");
    await flushPromises();
    const selected = document.querySelector<HTMLElement>(
      "[role=option][aria-selected=true]",
    )!;
    expect(selected.classList.contains("kb-select-option-active")).toBe(false);
    expect(selected.querySelector("[name=check]")).not.toBeNull();
    expect(selected.classList.contains("select-option-focused")).toBe(false);
    await w.get("button").trigger("keydown", { key: "ArrowDown" });
    await flushPromises();
    expect(selected.classList.contains("select-option-focused")).toBe(true);
    selected.dispatchEvent(new Event("pointerdown", { bubbles: true }));
    await flushPromises();
    expect(selected.classList.contains("select-option-focused")).toBe(false);
    expect(selected.getAttribute("aria-selected")).toBe("true");
    w.unmount();
  });
  it("accepts a compact panel width independent from a wide field", async () => {
    const w = mount(Select, {
      attachTo: document.body,
      props: {
        modelValue: "",
        panelWidth: 240,
        options: [{ value: "", label: "全部" }],
      },
      global: { stubs: { Icon: true } },
    });
    vi.spyOn(w.get("button").element, "getBoundingClientRect").mockReturnValue({
      left: 20,
      right: 620,
      top: 100,
      bottom: 138,
      width: 600,
      height: 38,
      toJSON: () => ({}),
    } as DOMRect);
    await w.get("button").trigger("click");
    await flushPromises();
    expect(
      (document.querySelector(".select-panel-v2") as HTMLElement).style.width,
    ).toBe("240px");
    w.unmount();
  });
});

it('bounds default searchable popup independently of a full width field',async()=>{const w=mount(Select,{attachTo:document.body,props:{modelValue:7,searchable:true,options:[{value:7,label:'真实数字'}]}});vi.spyOn(w.get('button').element,'getBoundingClientRect').mockReturnValue({left:20,right:620,top:100,bottom:138,width:600,height:38} as DOMRect);await w.get('button').trigger('click');await flushPromises();expect((document.querySelector('.select-panel-v2') as HTMLElement).style.width).toBe('380px');w.unmount()})
it('preserves the full label as a title without changing numeric identity',async()=>{const label='用于验收非常长的知识库完整名称';const w=mount(Select,{attachTo:document.body,props:{modelValue:7,searchable:true,options:[{value:7,label},{value:8,label:'其他'}]}});await w.get('button').trigger('click');await flushPromises();const option=document.querySelector<HTMLElement>('[role=option][aria-selected=true]')!;expect(option.title).toBe(label);expect(option.textContent).toContain(label);await w.setProps({modelValue:8});option.click();expect(w.emitted('update:modelValue')?.at(-1)).toEqual([7]);w.unmount()})
