import { FastifyInstance } from 'fastify';
import { db } from '../db';
import { wbStory } from '../db/schema';
import { CURRENT_USER, nowIso } from '../db';
import { eq, and, desc, like } from 'drizzle-orm';

/** 粗略统计中文字符 + 英文单词数（对齐 Web calcWords）。 */
function calcWords(content: string | null): number {
  if (!content) return 0;
  const text = content.replace(/[#>*`\-|\[\]()!]/g, ' ').replace(/\s+/g, ' ');
  let cjk = 0;
  let en = 0;
  for (const token of text.split(' ')) {
    if (!token) continue;
    if (/[一-龥]/.test(token)) cjk += token.length;
    else en += 1;
  }
  return cjk + en;
}

function toVO(r: any) {
  return {
    id: r.id,
    userId: r.userId,
    captureId: r.captureId,
    noteId: r.noteId,
    categoryId: r.categoryId,
    title: r.title,
    audience: r.audience,
    metaphor: r.metaphor,
    content: r.content,
    gapNote: r.gapNote,
    status: r.status,
    clarityScore: r.clarityScore,
    wordCount: r.wordCount,
    createTime: r.createdAt,
    updateTime: r.updatedAt,
  };
}

export default async function (app: FastifyInstance) {
  app.get('/stories', async (req) => {
    const q = req.query as any;
    const conds: any[] = [eq(wbStory.userId, CURRENT_USER)];
    if (q.status) conds.push(eq(wbStory.status, String(q.status)));
    if (q.categoryId) conds.push(eq(wbStory.categoryId, Number(q.categoryId)));
    if (q.keyword) conds.push(like(wbStory.title, `%${q.keyword}%`));
    const rows = db.select().from(wbStory).where(and(...conds)).orderBy(desc(wbStory.updatedAt)).all();
    return rows.map(toVO);
  });

  app.get('/stories/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const row = db.select().from(wbStory).where(eq(wbStory.id, id)).get() as any;
    if (!row) return reply.code(404).send({ message: 'not found' });
    return toVO(row);
  });

  app.post('/stories', async (req, reply) => {
    const b = req.body as any;
    if (!b.title) return reply.code(400).send({ message: 'title required' });
    const now = nowIso();
    const row = db
      .insert(wbStory)
      .values({
        userId: CURRENT_USER,
        captureId: b.captureId ?? null,
        noteId: b.noteId ?? null,
        categoryId: b.categoryId ?? null,
        title: b.title,
        audience: b.audience ?? 'CHILD',
        metaphor: b.metaphor ?? null,
        content: b.content ?? '',
        gapNote: b.gapNote ?? null,
        status: b.status ?? 'DRAFT',
        clarityScore: b.clarityScore ?? null,
        wordCount: calcWords(b.content ?? ''),
        createdAt: now,
        updatedAt: now,
      })
      .returning()
      .get();
    return row.id;
  });

  app.put('/stories/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const b = req.body as any;
    const ex = db.select().from(wbStory).where(eq(wbStory.id, id)).get() as any;
    if (!ex) return reply.code(404).send({ message: 'not found' });
    const content = b.content ?? ex.content;
    db.update(wbStory)
      .set({
        captureId: b.captureId !== undefined ? b.captureId : ex.captureId,
        noteId: b.noteId !== undefined ? b.noteId : ex.noteId,
        categoryId: b.categoryId !== undefined ? b.categoryId : ex.categoryId,
        title: b.title ?? ex.title,
        audience: b.audience ?? ex.audience,
        metaphor: b.metaphor ?? ex.metaphor,
        content,
        gapNote: b.gapNote ?? ex.gapNote,
        status: b.status ?? ex.status,
        clarityScore: b.clarityScore !== undefined ? b.clarityScore : ex.clarityScore,
        wordCount: calcWords(content),
        updatedAt: nowIso(),
      })
      .where(eq(wbStory.id, id))
      .run();
    return toVO(db.select().from(wbStory).where(eq(wbStory.id, id)).get() as any);
  });

  app.delete('/stories/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    db.delete(wbStory).where(eq(wbStory.id, id)).run();
    return reply.code(204).send();
  });
}
