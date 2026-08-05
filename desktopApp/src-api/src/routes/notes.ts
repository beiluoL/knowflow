import { FastifyInstance } from 'fastify';
import { db } from '../db';
import { wbNote, wbCapture } from '../db/schema';
import { CURRENT_USER, nowIso } from '../db';
import { eq, and, desc, like, sql } from 'drizzle-orm';

function toVO(r: any) {
  return {
    id: r.id,
    userId: r.userId,
    captureId: r.captureId,
    categoryId: r.categoryId,
    title: r.title,
    cueColumn: r.cueColumn,
    noteColumn: r.noteColumn,
    summaryColumn: r.summaryColumn,
    tags: r.tags,
    mastery: r.mastery,
    createTime: r.createdAt,
    updateTime: r.updatedAt,
  };
}

/** 整理笔记时把来源收集箱流转为「已整理」(PROCESSED)。 */
function markCaptureProcessed(captureId: number) {
  const c = db.select().from(wbCapture).where(eq(wbCapture.id, captureId)).get() as any;
  if (c && c.status !== 'ARCHIVED') {
    db.update(wbCapture).set({ status: 'PROCESSED', updatedAt: nowIso() }).where(eq(wbCapture.id, captureId)).run();
  }
}

export default async function (app: FastifyInstance) {
  app.get('/notes', async (req) => {
    const q = req.query as any;
    const conds: any[] = [eq(wbNote.userId, CURRENT_USER)];
    if (q.captureId) conds.push(eq(wbNote.captureId, Number(q.captureId)));
    if (q.categoryId) conds.push(eq(wbNote.categoryId, Number(q.categoryId)));
    if (q.keyword) conds.push(like(wbNote.title, `%${q.keyword}%`));
    const rows = db.select().from(wbNote).where(and(...conds)).orderBy(desc(wbNote.updatedAt)).all();
    return rows.map(toVO);
  });

  app.get('/notes/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const row = db.select().from(wbNote).where(eq(wbNote.id, id)).get() as any;
    if (!row) return reply.code(404).send({ message: 'not found' });
    return toVO(row);
  });

  app.post('/notes', async (req, reply) => {
    const b = req.body as any;
    if (!b.title) return reply.code(400).send({ message: 'title required' });
    const now = nowIso();
    const row = db
      .insert(wbNote)
      .values({
        userId: CURRENT_USER,
        captureId: b.captureId ?? null,
        categoryId: b.categoryId ?? null,
        title: b.title,
        cueColumn: b.cueColumn ?? '',
        noteColumn: b.noteColumn ?? '',
        summaryColumn: b.summaryColumn ?? '',
        tags: b.tags ?? null,
        mastery: b.mastery ?? 0,
        createdAt: now,
        updatedAt: now,
      })
      .returning()
      .get();
    if (b.captureId != null) markCaptureProcessed(Number(b.captureId));
    return row.id;
  });

  app.put('/notes/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const b = req.body as any;
    const ex = db.select().from(wbNote).where(eq(wbNote.id, id)).get() as any;
    if (!ex) return reply.code(404).send({ message: 'not found' });
    db.update(wbNote)
      .set({
        captureId: b.captureId !== undefined ? b.captureId : ex.captureId,
        categoryId: b.categoryId !== undefined ? b.categoryId : ex.categoryId,
        title: b.title ?? ex.title,
        cueColumn: b.cueColumn ?? ex.cueColumn,
        noteColumn: b.noteColumn ?? ex.noteColumn,
        summaryColumn: b.summaryColumn ?? ex.summaryColumn,
        tags: b.tags !== undefined ? b.tags : ex.tags,
        mastery: b.mastery !== undefined ? b.mastery : ex.mastery,
        updatedAt: nowIso(),
      })
      .where(eq(wbNote.id, id))
      .run();
    if (b.captureId != null) markCaptureProcessed(Number(b.captureId));
    return toVO(db.select().from(wbNote).where(eq(wbNote.id, id)).get() as any);
  });

  app.delete('/notes/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    db.delete(wbNote).where(eq(wbNote.id, id)).run();
    return reply.code(204).send();
  });
}
