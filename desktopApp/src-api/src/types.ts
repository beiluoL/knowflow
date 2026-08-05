// 学习工作台前后端共享类型（与 Web 端 api/workbench.ts 契约对齐）

export interface CategoryVO {
  id: number;
  name: string;
  parentId: number;
  sort: number;
}

export interface WbCapture {
  id: number;
  userId: number;
  title: string | null;
  content: string | null;
  source: string | null;
  type: string;
  status: string;
  starred: number;
  categoryId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface WbCapturePayload {
  title?: string;
  content?: string;
  source?: string;
  type?: string;
  status?: string;
  categoryId?: number | null;
}

export interface WbNote {
  id: number;
  userId: number;
  captureId: number | null;
  categoryId: number | null;
  title: string;
  cue: string;
  mainNotes: string;
  summary: string;
  createdAt: string;
  updatedAt: string;
}

export interface WbNotePayload {
  captureId?: number | null;
  categoryId?: number | null;
  title: string;
  cue?: string;
  mainNotes?: string;
  summary?: string;
}

export interface WbReviewCard {
  id: number;
  userId: number;
  captureId: number | null;
  noteId: number | null;
  categoryId: number | null;
  front: string;
  back: string;
  cardType: string;
  easeFactor: number;
  repetitions: number;
  intervalDay: number;
  reviewCount: number;
  lapseCount: number;
  nextReviewTime: string;
  lastReviewTime: string | null;
  suspended: number;
}

export interface WbReviewCardVO extends WbReviewCard {
  categoryName?: string | null;
}

export interface WbReviewCardPayload {
  captureId?: number | null;
  noteId?: number | null;
  categoryId?: number | null;
  front: string;
  back: string;
  cardType?: string;
}

export interface WbReviewGradePayload {
  quality: number;
  costMs?: number;
}

export interface WbReviewGradeResult {
  cardId: number;
  quality: number;
  repetitions: number;
  intervalDay: number;
  easeFactor: number;
  nextReviewAt: number;
  lapsed: boolean;
}

export interface WbPalace {
  id: number;
  userId: number;
  name: string;
  description: string | null;
}

export interface WbPalacePayload {
  name: string;
  description?: string;
}

export interface WbPalaceLoci {
  id: number;
  palaceId: number;
  userId: number;
  index: number;
  label: string;
  description: string | null;
}

export interface WbPalaceLociPayload {
  index?: number;
  label: string;
  description?: string;
}

export interface WbRecallSession {
  id: number;
  userId: number;
  title: string;
  sourceText: string;
  rounds: RecallRound[];
  createdAt: string;
  updatedAt: string;
}

export interface RecallRound {
  round: number;
  userText: string;
  score: number;
  createdAt: string;
}

export interface WbRecallSessionPayload {
  title?: string;
  sourceText?: string;
  round?: number;
  userText?: string;
}

export interface WbStory {
  id: number;
  userId: number;
  title: string;
  content: string;
  status: string;
  categoryId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface WbStoryPayload {
  title: string;
  content?: string;
  status?: string;
  categoryId?: number | null;
}

export interface WbForgettingCurve {
  days: number;
  labels: string[];
  reviewCounts: number[];
  lapseCounts: number[];
}

export interface WorkbenchOverview {
  captureTotal: number;
  captureInbox: number;
  captureStarred: number;
  noteTotal: number;
  reviewTotal: number;
  reviewDue: number;
  palaceTotal: number;
  storyTotal: number;
  recallTotal: number;
}
