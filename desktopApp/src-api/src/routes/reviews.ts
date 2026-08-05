import { FastifyInstance } from 'fastify';
import { db } from '../db';
import { wbReviewCard, wbReviewLog, categories } from '../db/schema';
import { CURRENT_USER, nowIso } from '../db';
import { eq, and, desc, gte, lte, sql } from 'drizzle-orm';
import { gradeCard } from '../services/sm2';

const DEFAULT_EF = 250;

/** 构建人类可读的「下次复习」提示（对齐 Web buildNextHint，按日期粒度计算）。 */
function buildNextHint(nextStr: string | null): string {
  if (!nextStr) return '待安排';
  const next = new Date(nextStr);
  if (Number.isNaN(next.getTime())) return '待安排';
  const today = new Date();
  const nextDay = Date.UTC(next.getUTCFullYear(), next.getUTCMonth(), next.getUTCDate());
  const todayDay = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
  const days = Math.round((nextDay - todayDay) / 86400000);
  if (days < 0) return `已逾期 ${-days} 天`;
  if (days === 0) return '今天';
  if (days === 1) return '明天';
  return `${days} 天后`;
}

function toVO(card: any) {
  const cat = card.categoryId
    ? db.select().from(categories).where(eq(categories.id, card.categoryId)).get()
    : null;
  return {
    ...card,
    easeFactorDecimal: (card.easeFactor ?? DEFAULT_EF) / 100,
    nextReviewHint: buildNextHint(card.nextReviewTime),
    categoryName: cat ? (cat as any).name : null,
  };
}

export default async function (app: FastifyInstance) {
  app.get('/reviews', async (req) => {
    const q = req.query as any;
    const conds: any[] = [eq(wbReviewCard.userId, CURRENT_USER)];
    if (q.categoryId) conds.push(eq(wbReviewCard.categoryId, Number(q.categoryId)));
    if (q.noteId) conds.push(eq(wbReviewCard.noteId, Number(q.noteId)));
    const rows = db
      .select()
      .from(wbReviewCard)
      .where(and(...conds))
      .orderBy(wbReviewCard.nextReviewTime)
      .all();
    return rows.map(toVO);
  });

  // 抽取待复习卡片：优先到期卡片（含明日到期窗口），不足则补未学过的新卡
  app.get('/reviews/draw', async (req) => {
    const limit = Math.max(1, Math.min(100, Number((req.query as any).limit) || 20));
    const windowEnd = new Date(Date.now() + 86400000).toISOString();
    const due = db
      .select()
      .from(wbReviewCard)
      .where(and(eq(wbReviewCard.userId, CURRENT_USER), eq(wbReviewCard.suspended, 0), lte(wbReviewCard.nextReviewTime, windowEnd)))
      .orderBy(wbReviewCard.nextReviewTime, wbReviewCard.id)
      .all();
    let pool = due as any[];
    if (pool.length < limit) {
      const dueIds = new Set(pool.map((c: any) => c.id));
      const fresh = db
        .select()
        .from(wbReviewCard)
        .where(and(eq(wbReviewCard.userId, CURRENT_USER), eq(wbReviewCard.suspended, 0), eq(wbReviewCard.repetitions, 0)))
        .orderBy(wbReviewCard.nextReviewTime, wbReviewCard.id)
        .all()
        .filter((c: any) => !dueIds.has(c.id));
      pool = pool.concat(fresh).slice(0, limit);
    }
    return pool.map(toVO);
  });

  // 待复习计数（供桌面端原生通知调度轮询）
  app.get('/reviews/due-count', async () => {
    const now = nowIso();
    const due = db
      .select({ id: wbReviewCard.id, front: wbReviewCard.front })
      .from(wbReviewCard)
      .where(and(eq(wbReviewCard.userId, CURRENT_USER), eq(wbReviewCard.suspended, 0), lte(wbReviewCard.nextReviewTime, now)))
      .all();
    const sample = (due as any[]).slice(0, 3).map((c: any) => c.front);
    return { count: due.length, sample };
  });

  app.post('/reviews', async (req, reply) => {
    const b = req.body as any;
    if (!b.front) return reply.code(400).send({ message: 'front required' });
    const now = nowIso();
    const row = db
      .insert(wbReviewCard)
      .values({
        userId: CURRENT_USER,
        captureId: b.captureId ?? null,
        noteId: b.noteId ?? null,
        categoryId: b.categoryId ?? null,
        front: b.front,
        back: b.back ?? '',
        cardType: b.cardType ?? 'basic',
        easeFactor: DEFAULT_EF,
        repetitions: 0,
        intervalDay: 0,
        reviewCount: 0,
        lapseCount: 0,
        nextReviewTime: now,
        lastReviewTime: null,
        suspended: 0,
      })
      .returning()
      .get();
    return row.id;
  });

  app.put('/reviews/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const b = req.body as any;
    const ex = db.select().from(wbReviewCard).where(eq(wbReviewCard.id, id)).get() as any;
    if (!ex) return reply.code(404).send({ message: 'not found' });
    db.update(wbReviewCard)
      .set({
        front: b.front ?? ex.front,
        back: b.back ?? ex.back,
        cardType: b.cardType ?? ex.cardType,
        captureId: b.captureId !== undefined ? b.captureId : ex.captureId,
        noteId: b.noteId !== undefined ? b.noteId : ex.noteId,
        categoryId: b.categoryId !== undefined ? b.categoryId : ex.categoryId,
      })
      .where(eq(wbReviewCard.id, id))
      .run();
    return toVO(db.select().from(wbReviewCard).where(eq(wbReviewCard.id, id)).get() as any);
  });

  app.delete('/reviews/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    db.delete(wbReviewCard).where(eq(wbReviewCard.id, id)).run();
    return reply.code(204).send();
  });

  // SM-2 评分（对齐 Web gradeReview）
  app.post('/reviews/:id/grade', async (req, reply) => {
    const id = Number((req.params as any).id);
    const b = req.body as any;
    if (b.quality === undefined || b.quality === null) {
      return reply.code(400).send({ message: 'quality required' });
    }
    const quality = Number(b.quality);
    if (quality < 0 || quality > 3) {
      return reply.code(400).send({ message: '评分质量需在 0~3 之间' });
    }
    const card = db.select().from(wbReviewCard).where(eq(wbReviewCard.id, id)).get() as any;
    if (!card) return reply.code(404).send({ message: 'not found' });

    const res = gradeCard(
      {
        easeFactor: card.easeFactor,
        repetitions: card.repetitions,
        intervalDay: card.intervalDay,
        lapseCount: card.lapseCount,
        reviewCount: card.reviewCount,
      },
      quality,
    );

    const now = new Date();
    const next = new Date(now.getTime() + res.nextReviewDays * 86400000);
    db.update(wbReviewCard)
      .set({
        easeFactor: res.easeFactor,
        repetitions: res.repetitions,
        intervalDay: res.intervalDay,
        reviewCount: res.reviewCount,
        lapseCount: res.lapseCount,
        nextReviewTime: next.toISOString(),
        lastReviewTime: now.toISOString(),
      })
      .where(eq(wbReviewCard.id, id))
      .run();

    db.insert(wbReviewLog)
      .values({
        userId: CURRENT_USER,
        cardId: id,
        quality: res.quality,
        intervalDay: res.intervalDay,
        easeFactor: res.easeFactor,
        costMs: b.costMs ?? null,
        reviewedAt: now.toISOString(),
      })
      .run();

    // nextReviewAt 与 Web 端语义一致（同一瞬时，前端按本地时区展示墙钟时间）
    return {
      cardId: id,
      quality: res.quality,
      repetitions: res.repetitions,
      intervalDay: res.intervalDay,
      easeFactor: res.easeFactor / 100,
      nextReviewAt: next.getTime(),
      lapsed: res.lapsed,
    };
  });

  app.put('/reviews/:id/suspend', async (req, reply) => {
    const id = Number((req.params as any).id);
    const row = db.select().from(wbReviewCard).where(eq(wbReviewCard.id, id)).get() as any;
    if (!row) return reply.code(404).send({ message: 'not found' });
    const suspended = row.suspended ? 0 : 1;
    db.update(wbReviewCard).set({ suspended }).where(eq(wbReviewCard.id, id)).run();
    return reply.code(200).send();
  });

  // 遗忘曲线：按日聚合复习量、遗忘量、遗忘率与新卡数（对齐 Web forgettingCurve）
  app.get('/reviews/forgetting-curve', async (req) => {
    const days = Math.max(1, Math.min(365, Number((req.query as any).days) || 30));
    const end = new Date();
    const start = new Date(end.getTime() - (days - 1) * 86400000);

    const fmt = (d: Date) => d.toISOString().slice(0, 10);
    const startDate = fmt(start);
    const endDate = fmt(end);

    const bucket: Record<string, { reviews: number; lapses: number; newCards: number }> = {};
    for (let i = 0; i < days; i++) {
      const d = new Date(start.getTime() + i * 86400000);
      const key = fmt(d);
      bucket[key] = { reviews: 0, lapses: 0, newCards: 0 };
    }

    const logs = db
      .select()
      .from(wbReviewLog)
      .where(and(eq(wbReviewLog.userId, CURRENT_USER), gte(wbReviewLog.reviewedAt, start.toISOString())))
      .all() as any[];

    const seenCards = new Set<number>();
    let totalReviews = 0;
    let totalLapses = 0;
    for (const log of logs) {
      const key = (log.reviewedAt || '').slice(0, 10);
      const p = bucket[key];
      if (!p) continue;
      p.reviews += 1;
      if (log.quality === 0) p.lapses += 1;
      if (log.cardId != null && !seenCards.has(log.cardId)) {
        seenCards.add(log.cardId);
        p.newCards += 1;
      }
      totalReviews += 1;
      if (log.quality === 0) totalLapses += 1;
    }

    const points = Object.keys(bucket).map((date) => {
      const p = bucket[date];
      return {
        date,
        reviews: p.reviews,
        lapses: p.lapses,
        lapseRate: p.reviews === 0 ? 0 : p.lapses / p.reviews,
        newCards: p.newCards,
      };
    });
    const overallLapseRate = totalReviews === 0 ? 0 : totalLapses / totalReviews;

    return {
      startDate,
      endDate,
      points,
      totalReviews,
      totalLapses,
      overallLapseRate,
    };
  });
}
