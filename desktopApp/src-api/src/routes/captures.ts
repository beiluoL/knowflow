import { FastifyInstance } from 'fastify';
import { db } from '../db';
import { wbCapture } from '../db/schema';
import { CURRENT_USER, nowIso } from '../db';
import { eq, and, desc, like } from 'drizzle-orm';

function toVO(r: any) {
  return {
    id: r.id,
    userId: r.userId,
    title: r.title,
    content: r.content,
    sourceType: r.sourceType,
    sourceUrl: r.sourceUrl,
    docId: r.docId,
    categoryId: r.categoryId,
    tags: r.tags,
    status: r.status,
    starred: r.starred,
    createTime: r.createdAt,
    updateTime: r.updatedAt,
  };
}

export default async function (app: FastifyInstance) {
  app.get('/captures', async (req) => {
    const q = req.query as any;
    const conds: any[] = [eq(wbCapture.userId, CURRENT_USER)];
    if (q.status) conds.push(eq(wbCapture.status, String(q.status)));
    if (q.categoryId) conds.push(eq(wbCapture.categoryId, Number(q.categoryId)));
    if (q.keyword) conds.push(like(wbCapture.title, `%${q.keyword}%`));
    const rows = db
      .select()
      .from(wbCapture)
      .where(and(...conds))
      .orderBy(desc(wbCapture.starred), desc(wbCapture.createdAt))
      .all();
    return rows.map(toVO);
  });

  app.get('/captures/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const row = db
      .select()
      .from(wbCapture)
      .where(and(eq(wbCapture.id, id), eq(wbCapture.userId, CURRENT_USER)))
      .get() as any;
    if (!row) return reply.code(404).send({ message: 'not found' });
    return toVO(row);
  });

  app.post('/captures', async (req, reply) => {
    const b = req.body as any;
    if (!b.title) return reply.code(400).send({ message: 'title required' });
    const now = nowIso();
    const row = db
      .insert(wbCapture)
      .values({
        userId: CURRENT_USER,
        title: b.title,
        content: b.content ?? null,
        sourceType: b.sourceType ?? 'MANUAL',
        sourceUrl: b.sourceUrl ?? null,
        docId: b.docId ?? null,
        categoryId: b.categoryId ?? null,
        tags: b.tags ?? null,
        status: 'INBOX',
        starred: b.starred ?? 0,
        createdAt: now,
        updatedAt: now,
      })
      .returning()
      .get();
    return row.id;
  });

  app.put('/captures/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const b = req.body as any;
    const ex = db.select().from(wbCapture).where(eq(wbCapture.id, id)).get() as any;
    if (!ex) return reply.code(404).send({ message: 'not found' });
    db.update(wbCapture)
      .set({
        title: b.title ?? ex.title,
        content: b.content ?? ex.content,
        sourceType: b.sourceType ?? ex.sourceType,
        sourceUrl: b.sourceUrl ?? ex.sourceUrl,
        docId: b.docId !== undefined ? b.docId : ex.docId,
        categoryId: b.categoryId !== undefined ? b.categoryId : ex.categoryId,
        tags: b.tags !== undefined ? b.tags : ex.tags,
        status: b.status ?? ex.status,
        starred: b.starred !== undefined ? b.starred : ex.starred,
        updatedAt: nowIso(),
      })
      .where(eq(wbCapture.id, id))
      .run();
    return toVO(db.select().from(wbCapture).where(eq(wbCapture.id, id)).get() as any);
  });

  app.delete('/captures/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    db.delete(wbCapture).where(eq(wbCapture.id, id)).run();
    return reply.code(204).send();
  });

  app.put('/captures/:id/status', async (req, reply) => {
    const id = Number((req.params as any).id);
    const status = (req.query as any).status;
    if (!status) return reply.code(400).send({ message: 'status required' });
    db.update(wbCapture).set({ status: String(status), updatedAt: nowIso() }).where(eq(wbCapture.id, id)).run();
    return reply.code(200).send();
  });

  app.put('/captures/:id/star', async (req, reply) => {
    const id = Number((req.params as any).id);
    const row = db.select().from(wbCapture).where(eq(wbCapture.id, id)).get() as any;
    if (!row) return reply.code(404).send({ message: 'not found' });
    const starred = row.starred ? 0 : 1;
    db.update(wbCapture).set({ starred, updatedAt: nowIso() }).where(eq(wbCapture.id, id)).run();
    return reply.code(200).send();
  });
}
