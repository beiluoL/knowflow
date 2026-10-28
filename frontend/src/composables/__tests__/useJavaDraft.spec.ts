import { describe, it, expect, vi } from "vitest";
import { effectScope, nextTick } from "vue";
import { useJavaDraft } from "../useJavaDraft";

describe("Java草稿事务", () => {
  it("保存中的新编辑不能被旧回复标成已保存", async () => {
    let complete!: (value: { draftRevision: number }) => void;
    const save = vi.fn(
      () =>
        new Promise<{ draftRevision: number }>((resolve) => {
          complete = resolve;
        }),
    );
    const scope = effectScope();
    const draft = scope.run(() => useJavaDraft(5, save))!;
    draft.initialize("class A {}", 0, "hash");
    draft.source.value = "class B {}";
    const pending = draft.save();
    draft.source.value = "class C {}";
    complete({ draftRevision: 1 });
    await pending;
    expect(draft.dirty.value).toBe(true);
    expect(draft.source.value).toBe("class C {}");
    expect(draft.revision.value).toBe(1);
    scope.stop();
  });
  it("网络失败重试复用相同请求，冲突时保留本地源码", async () => {
    const save = vi
      .fn()
      .mockRejectedValueOnce(new Error("连接失败"))
      .mockResolvedValueOnce({ draftRevision: 1 })
      .mockRejectedValueOnce(new Error("版本冲突"));
    const scope = effectScope();
    const draft = scope.run(() => useJavaDraft(5, save))!;
    draft.initialize("", 0, "hash");
    draft.source.value = "class A {}";
    await draft.save();
    await draft.save();
    expect(save.mock.calls[0]![1].requestId).toBe(
      save.mock.calls[1]![1].requestId,
    );
    draft.source.value = "class B {}";
    await nextTick();
    await draft.save();
    expect(draft.source.value).toBe("class B {}");
    expect(draft.error.value).toContain("冲突");
    expect(draft.dirty.value).toBe(true);
    scope.stop();
  });
});
it("保存回包丢失后撤回原文仍视为未确认，并按原请求确认后再保存当前文", async () => {
  const send = vi
    .fn()
    .mockRejectedValueOnce(new Error("响应丢失"))
    .mockResolvedValueOnce({ draftRevision: 1 })
    .mockResolvedValueOnce({ draftRevision: 2 });
  const scope = effectScope(),
    draft = scope.run(() => useJavaDraft(5, send))!;
  draft.initialize("class A {}", 0, "hash");
  draft.source.value = "class B {}";
  await draft.save();
  draft.source.value = "class A {}";
  expect(draft.dirty.value).toBe(true);
  await draft.save();
  expect(draft.dirty.value).toBe(true);
  await draft.save();
  expect(draft.dirty.value).toBe(false);
  expect(send.mock.calls[0]![1].requestId).toBe(
    send.mock.calls[1]![1].requestId,
  );
  expect(send.mock.calls[2]![1].sourceCode).toBe("class A {}");
  scope.stop();
});
