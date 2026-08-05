import { FastifyInstance } from 'fastify';
import { db } from '../db';
import { CURRENT_USER, nowIso } from '../db';
import {
  wbCapture,
  wbNote,
  wbReviewCard,
  wbReviewLog,
  wbPalace,
  wbPalaceLoci,
  wbRecallSession,
  wbStory,
} from '../db/schema';
import { eq, sql } from 'drizzle-orm';

// ===== 工具函数 =====

/** 时间统一转 ISO 字符串；桌面端 nextReviewTime 按字符串比较。 */
function normIso(x: any): string | null {
  if (x === undefined || x === null || x === '') return null;
  const d = new Date(x as string);
  return Number.isNaN(d.getTime()) ? String(x) : d.toISOString();
}

/** 数字解析，非法值回退默认值。 */
function num(x: any, def = 0): number {
  const n = Number(x);
  return x === undefined || x === null || x === '' || Number.isNaN(n) ? def : n;
}

/** 可空数字（如复习耗时）。 */
function numOrNull(x: any): number | null {
  if (x === undefined || x === null || x === '') return null;
  const n = Number(x);
  return Number.isNaN(n) ? null : n;
}

type AnyRow = Record<string, any>;

/** 导入前检查目标用户是否已有数据，避免重复导入产生脏数据。 */
function hasExistingData(): boolean {
  const tables = [wbCapture, wbNote, wbReviewCard, wbPalace, wbStory, wbRecallSession, wbReviewLog, wbPalaceLoci];
  for (const t of tables) {
    const res = db
      .select({ c: sql<number>`cast(count(*) as int)` })
      .from(t)
      .where(eq((t as any).userId, CURRENT_USER))
      .get() as any;
    if (res && res.c > 0) return true;
  }
  return false;
}

/**
 * 数据迁移导入端点：消费 Web 端 GET /api/workbench/export 产出的 JSON，
 * 将工作台四模块数据落库到本地 SQLite。两端字段已对齐（均为 Wb* 契约），
 * 关联内部自增 id 经映射表重定向；categoryId 在桌面端无对应分类体系，统一置空。
 */
export default async function (app: FastifyInstance) {
  app.post('/import', async (req, reply) => {
    const body = (req.body || {}) as any;
    const data: AnyRow = body && body.data && typeof body.data === 'object' ? body.data : body;
    if (!data || typeof data !== 'object') {
      return reply.code(400).send({ code: 400, message: 'invalid payload: 缺少 data 对象' });
    }
    if (
      !Array.isArray(data.captures) &&
      !Array.isArray(data.notes) &&
      !Array.isArray(data.stories) &&
      !Array.isArray(data.palaces)
    ) {
      return reply
        .code(400)
        .send({ code: 400, message: 'invalid payload: data 至少应包含 captures/notes/stories/palaces 之一' });
    }

    if (hasExistingData()) {
      return reply
        .code(409)
        .send({ code: 409, message: '本地已有工作台数据，重复导入会导致脏数据。请先清空本地数据后再导入。' });
    }

    const summary = db.transaction((tx) => {
      const capMap = new Map<number, number>();
      const noteMap = new Map<number, number>();
      const cardMap = new Map<number, number>();
      const palaceMap = new Map<number, number>();

      // 1) 收集箱
      for (const r of (data.captures || []) as AnyRow[]) {
        const row = tx
          .insert(wbCapture)
          .values({
            userId: CURRENT_USER,
            title: r.title ?? '',
            content: r.content ?? null,
            sourceType: r.sourceType ?? 'MANUAL',
            sourceUrl: r.sourceUrl ?? null,
            docId: r.docId ?? null,
            categoryId: null,
            tags: r.tags ?? null,
            status: r.status ?? 'INBOX',
            starred: r.starred ? 1 : 0,
            createdAt: normIso(r.createTime) ?? nowIso(),
            updatedAt: normIso(r.updateTime) ?? nowIso(),
          })
          .returning()
          .get();
        capMap.set(Number(r.id), row.id);
      }

      // 2) 康奈尔笔记
      for (const r of (data.notes || []) as AnyRow[]) {
        const row = tx
          .insert(wbNote)
          .values({
            userId: CURRENT_USER,
            captureId: r.captureId != null ? capMap.get(Number(r.captureId)) ?? null : null,
            categoryId: null,
            title: r.title ?? '',
            cueColumn: r.cueColumn ?? '',
            noteColumn: r.noteColumn ?? '',
            summaryColumn: r.summaryColumn ?? '',
            tags: r.tags ?? null,
            mastery: num(r.mastery, 0),
            createdAt: normIso(r.createTime) ?? nowIso(),
            updatedAt: normIso(r.updateTime) ?? nowIso(),
          })
          .returning()
          .get();
        noteMap.set(Number(r.id), row.id);
      }

      // 3) 记忆宫殿
      for (const r of (data.palaces || []) as AnyRow[]) {
        const row = tx
          .insert(wbPalace)
          .values({
            userId: CURRENT_USER,
            name: r.name ?? '未命名宫殿',
            description: r.description ?? null,
            theme: r.theme ?? 'ROOM',
            coverColor: r.coverColor ?? null,
            categoryId: null,
            createdAt: normIso(r.createTime) ?? nowIso(),
            updatedAt: normIso(r.updateTime) ?? nowIso(),
          })
          .returning()
          .get();
        palaceMap.set(Number(r.id), row.id);
      }

      // 4) 间隔重复卡片（SM-2 调度状态原样迁移）
      for (const r of (data.reviewCards || []) as AnyRow[]) {
        const row = tx
          .insert(wbReviewCard)
          .values({
            userId: CURRENT_USER,
            captureId: r.captureId != null ? capMap.get(Number(r.captureId)) ?? null : null,
            noteId: r.noteId != null ? noteMap.get(Number(r.noteId)) ?? null : null,
            categoryId: null,
            front: r.front ?? '',
            back: r.back ?? '',
            cardType: r.cardType ?? 'basic',
            easeFactor: num(r.easeFactor, 250),
            repetitions: num(r.repetitions, 0),
            intervalDay: num(r.intervalDay, 0),
            reviewCount: num(r.reviewCount, 0),
            lapseCount: num(r.lapseCount, 0),
            nextReviewTime: normIso(r.nextReviewTime) ?? nowIso(),
            lastReviewTime: normIso(r.lastReviewTime),
            suspended: r.suspended ? 1 : 0,
          })
          .returning()
          .get();
        cardMap.set(Number(r.id), row.id);
      }

      // 5) 复习日志
      for (const r of (data.reviewLogs || []) as AnyRow[]) {
        tx.insert(wbReviewLog)
          .values({
            userId: CURRENT_USER,
            cardId: cardMap.get(Number(r.cardId)) ?? 0,
            quality: num(r.quality, 0),
            intervalDay: num(r.intervalDay, 0),
            easeFactor: num(r.easeFactor, 0),
            costMs: numOrNull(r.costMs),
            reviewedAt: normIso(r.createTime) ?? nowIso(),
          })
          .run();
      }

      // 6) 记忆宫殿位点
      for (const r of (data.palaceLoci || []) as AnyRow[]) {
        const now = nowIso();
        tx.insert(wbPalaceLoci)
          .values({
            palaceId: palaceMap.get(Number(r.palaceId)) ?? 0,
            userId: CURRENT_USER,
            name: r.name ?? '',
            knowledgePoint: r.knowledgePoint ?? null,
            imageHint: r.imageHint ?? null,
            icon: r.icon ?? null,
            posX: r.posX ?? 50,
            posY: r.posY ?? 50,
            sortOrder: num(r.sortOrder, 0),
            captureId: null,
            noteId: null,
            categoryId: null,
            createdAt: now,
            updatedAt: now,
          })
          .run();
      }

      // 7) 主动回忆会话（三轮字段直接落库，对齐 Web 契约）
      for (const r of (data.recallSessions || []) as AnyRow[]) {
        tx.insert(wbRecallSession)
          .values({
            userId: CURRENT_USER,
            noteId: r.noteId != null ? noteMap.get(Number(r.noteId)) ?? null : null,
            cardId: r.cardId != null ? cardMap.get(Number(r.cardId)) ?? null : null,
            title: r.title ?? '未命名会话',
            sourceText: r.sourceText ?? '',
            round1Text: r.round1Text ?? null,
            round1Score: numOrNull(r.round1Score),
            round2Text: r.round2Text ?? null,
            round2Score: numOrNull(r.round2Score),
            round3Text: r.round3Text ?? null,
            round3Score: numOrNull(r.round3Score),
            currentRound: num(r.currentRound, 1),
            status: r.status ?? 'IN_PROGRESS',
            round3DueTime: normIso(r.round3DueTime),
            completedTime: normIso(r.completedTime),
            createdAt: normIso(r.createTime) ?? nowIso(),
            updatedAt: normIso(r.updateTime) ?? nowIso(),
          })
          .run();
      }

      // 8) 费曼故事
      for (const r of (data.stories || []) as AnyRow[]) {
        tx.insert(wbStory)
          .values({
            userId: CURRENT_USER,
            captureId: r.captureId != null ? capMap.get(Number(r.captureId)) ?? null : null,
            noteId: r.noteId != null ? noteMap.get(Number(r.noteId)) ?? null : null,
            categoryId: null,
            title: r.title ?? '未命名故事',
            audience: r.audience ?? 'CHILD',
            metaphor: r.metaphor ?? null,
            content: r.content ?? '',
            gapNote: r.gapNote ?? null,
            status: r.status ?? 'DRAFT',
            clarityScore: numOrNull(r.clarityScore),
            wordCount: numOrNull(r.wordCount),
            createdAt: normIso(r.createTime) ?? nowIso(),
            updatedAt: normIso(r.updateTime) ?? nowIso(),
          })
          .run();
      }

      return {
        captures: capMap.size,
        notes: noteMap.size,
        palaces: palaceMap.size,
        reviewCards: cardMap.size,
        reviewLogs: (data.reviewLogs || []).length,
        palaceLoci: (data.palaceLoci || []).length,
        recallSessions: (data.recallSessions || []).length,
        stories: (data.stories || []).length,
      };
    });

    return reply.code(200).send({ code: 200, data: { ok: true, imported: summary } });
  });
}
