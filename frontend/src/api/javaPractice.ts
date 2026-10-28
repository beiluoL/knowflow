import { apiGet, apiPost } from "./request";
import type { CodeProblemDetail, JudgeStatusCode } from "./codeBank";
// F11沿用CodeBank及原提交编号；不向前端定义隐藏用例或参考实现字段。
export interface Workspace {
  problem: CodeProblemDetail;
  draftRevision: number;
  draftHash: string;
  executionEnabled: boolean;
  message: string;
  drafts: { revision: number; created_at: string }[];
  jobs: {
    id: number;
    judge_status: JudgeStatusCode;
    submission_type: string;
    problem_version: string;
  }[];
}
export interface DraftInput {
  requestId: string;
  expectedRevision: number;
  language: "java";
  sourceCode: string;
  baseHash?: string;
}
export interface JobInput {
  requestId: string;
  language: "java";
  sourceCode: string;
  type: "RUN" | "SUBMIT";
}
export interface PracticeJob {
  id: number;
  questionId: number;
  questionVersion: string;
  title: string;
  content: string;
  sourceCode: string;
  type: string;
  judgeStatus: JudgeStatusCode;
  score: number | null;
  executionEnabled: boolean;
  message: string;
  passedCount?: number;
  totalCount?: number;
  executionTimeMs?: number;
  memoryUsedKb?: number;
  attempts: { attempt_no: number; state: JudgeStatusCode }[];
}
export const javaPracticeApi = {
  workspace: (id: number) =>
    apiGet<Workspace>(`/code-bank/problems/${id}/workspace`),
  save: (id: number, body: DraftInput) =>
    apiPost<{ draftRevision: number; draftHash?: string }>(
      `/code-bank/problems/${id}/workspace/drafts`,
      body,
    ),
  draft: (id: number, revision: number) =>
    apiGet<{ sourceCode: string }>(
      `/code-bank/problems/${id}/workspace/drafts/${revision}`,
    ),
  submit: (id: number, body: JobInput) =>
    apiPost<PracticeJob>(`/code-bank/problems/${id}/workspace/jobs`, body),
  job: (id: number) => apiGet<PracticeJob>(`/code-bank/workspace/jobs/${id}`),
  retry: (id: number, requestId: string) =>
    apiPost<PracticeJob>(`/code-bank/workspace/jobs/${id}/retry`, {
      requestId,
    }),
  tutor: (id: number) =>
    apiPost<{ noteId: number; conversationId: number }>(
      `/code-bank/workspace/jobs/${id}/tutor`,
    ),
};
