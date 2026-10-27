// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
const api = vi.hoisted(() => ({
  create: vi.fn(),
  update: vi.fn(),
  detail: vi.fn(),
}));
vi.mock("@/api/notes", () => ({ notesApi: api }));
import { useNoteSession } from "../useNoteSession";
let session: ReturnType<typeof useNoteSession>, text: import("vue").Ref<string>;
function setup() {
  return mount(
    defineComponent({
      setup() {
        text = ref("原文");
        session = useNoteSession(
          7,
          5,
          () => ({ title: "标题", content: text.value }),
          (n) => {
            text.value = n.content;
          },
        );
        session.start({ id: 5, revision: 1, content: "原文" } as any);
        return () => null;
      },
    }),
  );
}
beforeEach(() => {
  vi.useFakeTimers();
  vi.resetAllMocks();
  localStorage.clear();
  api.update.mockImplementation(async (id, p) => ({
    ...p,
    id,
    revision: p.revision + 1,
  }));
  api.detail.mockResolvedValue({ id: 5, revision: 2, content: "服务器" });
});
afterEach(() => vi.useRealTimers());
it("防抖保存，携带版本且只在响应成功后显示已保存", async () => {
  const w = setup();
  text.value = "甲";
  await flushPromises();
  text.value = "乙";
  await flushPromises();
  expect(api.update).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(900);
  expect(api.update).toHaveBeenCalledOnce();
  expect(api.update.mock.calls[0]![1]).toMatchObject({
    content: "乙",
    revision: 1,
  });
  expect(session.status.value).toContain("已保存");
  expect(session.revision.value).toBe(2);
  w.unmount();
});
it("失败保留账户草稿，冲突不能自动重试覆盖", async () => {
  const w = setup();
  api.update.mockRejectedValue(Object.assign(Error("冲突"), { status: 409 }));
  text.value = "本地";
  await flushPromises();
  await vi.advanceTimersByTimeAsync(900);
  expect(session.conflict.value?.content).toBe("服务器");
  expect(localStorage.getItem("notes-recovery:7:5")).toContain("本地");
  text.value = "继续写";
  await flushPromises();
  await vi.advanceTimersByTimeAsync(3000);
  expect(api.update).toHaveBeenCalledOnce();
  w.unmount();
});
it("请求期间继续输入会串行保存后续内容", async () => {
  const w = setup();
  let resolve!: (x: any) => void;
  api.update.mockImplementationOnce(() => new Promise((r) => (resolve = r)));
  text.value = "第一次";
  await flushPromises();
  await vi.advanceTimersByTimeAsync(900);
  text.value = "第二次";
  await flushPromises();
  resolve({ id: 5, revision: 2, content: "第一次" });
  await flushPromises();
  await vi.advanceTimersByTimeAsync(900);
  expect(api.update).toHaveBeenCalledTimes(2);
  expect(api.update.mock.calls[1]![1]).toMatchObject({
    content: "第二次",
    revision: 2,
  });
  w.unmount();
});

it("切换账户令牌后停止自动上传并保留原账户副本", async () => {
  const w = setup();
  localStorage.setItem("token", "另一个账户");
  text.value = "不能上传给新账户";
  await flushPromises();
  await vi.advanceTimersByTimeAsync(900);
  expect(api.update).not.toHaveBeenCalled();
  expect(session.status.value).toContain("账户已变化");
  expect(localStorage.getItem("notes-recovery:7:5")).toContain("不能上传");
  w.unmount();
});

it('首次创建期间继续输入，旧草稿入口绑定服务器ID，刷新不重复新建',async()=>{
 let done!:(n:any)=>void;
 api.create.mockImplementationOnce(()=>new Promise(r=>done=r));
 const w=mount(defineComponent({setup(){text=ref('创建快照');session=useNoteSession(7,null,()=>({title:'新笔记',content:text.value}),()=>{},undefined,'new:isolated');session.start(undefined,true);return()=>null}}));
 const saving=session.save();await flushPromises();text.value='创建期间的新输入';await flushPromises();done({id:88,revision:1});await saving;await flushPromises();
 const old=JSON.parse(localStorage.getItem('notes-recovery:7:new:isolated')!);
 expect(old.id).toBe(88);expect(old.saved).toBe(true);
 const pending=JSON.parse(localStorage.getItem('notes-recovery:7:88')!);
 expect(pending.payload.content).toBe('创建期间的新输入');expect(pending.saved).not.toBe(true);w.unmount();
});
