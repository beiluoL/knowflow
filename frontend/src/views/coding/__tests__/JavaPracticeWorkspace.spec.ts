// @vitest-environment happy-dom
import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { beforeEach, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({
  workspace: vi.fn(),
  save: vi.fn(),
  job: vi.fn(),
  submit: vi.fn(),
  retry: vi.fn(),
  tutor: vi.fn(),
  draft: vi.fn(),
  confirm: vi.fn(),
  leave: vi.fn(),
  update: vi.fn(),
}));
vi.mock("@/api/javaPractice", () => ({ javaPracticeApi: mocks }));
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
  onBeforeRouteLeave: mocks.leave,
  onBeforeRouteUpdate: mocks.update,
}));
vi.mock("@/utils/toast", () => ({
  confirmDialog: mocks.confirm,
  getApiError: (e: Error) => e.message,
}));
vi.mock("@/composables/useCodeFormatting", () => ({
  useCodeFormatting: () => ({
    settings: ref({ enabled: true, save: true, paste: true, input: false }),
    formatting: ref(false),
    message: ref("语法错误，原代码保留"),
    persistenceError: ref(""),
    format: vi.fn(),
    beforeSave: async () => false,
    onInput: vi.fn(),
    onPaste: vi.fn(),
    onCompositionStart: vi.fn(),
    onCompositionEnd: vi.fn(),
    handleHistory: vi.fn(),
    cancelAll: vi.fn(),
  }),
}));
import Workspace from "../JavaPracticeWorkspace.vue";
const snapshot = {
  id: 9,
  questionId: 5,
  title: "旧题目",
  content: "旧题意",
  sourceCode: "class Frozen {}",
  type: "SUBMIT",
  questionVersion: "1:v1",
  judgeStatus: "JUDGE_UNAVAILABLE",
  score: null,
  executionEnabled: false,
  message: "安全评测环境尚未就绪",
  attempts: [],
};
const create = () =>
  mount(Workspace, {
    props: { problemId: 5 },
    global: {
      stubs: {
        NotesTutor: {
          props: ["noteId"],
          template: '<section aria-label="复用导师">笔记 {{noteId}}</section>',
        },
        RouterLink: { template: "<a><slot /></a>" },
      },
    },
  });
beforeEach(() => {
  vi.clearAllMocks();
  mocks.workspace.mockResolvedValue({
    problem: {
      id: 5,
      title: "Java题目",
      content: "题意",
      constraints: "约束",
      publicCases: [],
      draftCode: "class Draft {}",
    },
    draftRevision: 1,
    draftHash: "hash",
    executionEnabled: false,
    message: "安全评测环境尚未就绪",
    drafts: [{ revision: 1 }],
    jobs: [
      {
        id: 9,
        judge_status: "JUDGE_UNAVAILABLE",
        submission_type: "SUBMIT",
        problem_version: "1:v1",
      },
    ],
  });
  mocks.save.mockResolvedValue({ draftRevision: 2, draftHash: "next" });
  mocks.job.mockResolvedValue(snapshot);
  mocks.submit.mockResolvedValue(snapshot);
  mocks.confirm.mockResolvedValue(false);
});
it("语法格式化失败仍能保存原代码，禁用执行不禁用留档", async () => {
  const w = create();
  await flushPromises();
  await w.get("textarea").setValue("class {");
  await w
    .findAll("button")
    .find((x) => x.text() === "保存草稿")!
    .trigger("click");
  await flushPromises();
  expect(mocks.save.mock.calls[0]![1].sourceCode).toBe("class {");
  expect(
    w
      .findAll("button")
      .find((x) => x.text() === "运行示例")!
      .attributes("disabled"),
  ).toBeDefined();
  expect(
    w
      .findAll("button")
      .find((x) => x.text() === "提交并保留代码")!
      .attributes("disabled"),
  ).toBeUndefined();
  w.unmount();
});
it("读取旧提交保持当前草稿，平台失败显示未计分，切题有保护", async () => {
  const w = create();
  await flushPromises();
  await w
    .findAll("button")
    .find((x) => x.text().includes("#9"))!
    .trigger("click");
  await flushPromises();
  expect(w.get("textarea").element.value).toBe("class Draft {}");
  expect(w.get('[aria-label="冻结的提交源码"]').text()).toBe("class Frozen {}");
  expect(w.text()).toContain("未计分");
  expect(mocks.update).toHaveBeenCalled();
  w.unmount();
});
it("提交响应丢失后重试原编号和源码，不把当前新编辑混入旧请求", async () => {
  mocks.submit
    .mockRejectedValueOnce(new Error("连接中断"))
    .mockResolvedValueOnce(snapshot);
  const w = create();
  await flushPromises();
  await w
    .findAll("button")
    .find((x) => x.text() === "提交并保留代码")!
    .trigger("click");
  await flushPromises();
  await w.get("textarea").setValue("class Newer {}");
  await w
    .findAll("button")
    .find((x) => x.text() === "重试原提交请求")!
    .trigger("click");
  await flushPromises();
  expect(mocks.submit.mock.calls[0]![1]).toEqual(
    mocks.submit.mock.calls[1]![1],
  );
  expect(mocks.submit.mock.calls[1]![1].sourceCode).toBe("class Draft {}");
  expect(w.get("textarea").element.value).toBe("class Newer {}");
  w.unmount();
});

it("点击AI仅准备原Tutor上下文，保留当前草稿和客观结果", async () => {
  mocks.tutor.mockResolvedValue({ noteId: 22, conversationId: 33 });
  const w = create();
  await flushPromises();
  await w
    .findAll("button")
    .find((x) => x.text().includes("#9"))!
    .trigger("click");
  await flushPromises();
  await w
    .findAll("button")
    .find((x) => x.text() === "问AI：为什么没有通过？")!
    .trigger("click");
  await flushPromises();
  expect(mocks.tutor).toHaveBeenCalledWith(9);
  expect(w.text()).toContain("未计分");
  expect(w.get("textarea").element.value).toBe("class Draft {}");
  expect(w.get('[aria-label="复用导师"]').text()).toContain("22");
  w.unmount();
});
