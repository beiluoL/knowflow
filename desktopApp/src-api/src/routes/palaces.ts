import { FastifyInstance } from 'fastify';
import { db } from '../db';
import { wbPalace, wbPalaceLoci } from '../db/schema';
import { CURRENT_USER, nowIso } from '../db';
import { eq, and, asc, desc, sql } from 'drizzle-orm';

function palaceVO(p: any) {
  return {
    id: p.id,
    userId: p.userId,
    name: p.name,
    description: p.description,
    theme: p.theme,
    coverColor: p.coverColor,
    categoryId: p.categoryId,
    createTime: p.createdAt,
    updateTime: p.updatedAt,
  };
}

function lociVO(l: any) {
  return {
    id: l.id,
    userId: l.userId,
    palaceId: l.palaceId,
    captureId: l.captureId,
    noteId: l.noteId,
    categoryId: l.categoryId,
    name: l.name,
    knowledgePoint: l.knowledgePoint,
    imageHint: l.imageHint,
    icon: l.icon,
    posX: l.posX,
    posY: l.posY,
    sortOrder: l.sortOrder,
    createTime: l.createdAt,
    updateTime: l.updatedAt,
  };
}

export default async function (app: FastifyInstance) {
  app.get('/palaces', async () => {
    return db
      .select()
      .from(wbPalace)
      .where(eq(wbPalace.userId, CURRENT_USER))
      .orderBy(desc(wbPalace.updatedAt))
      .all()
      .map(palaceVO);
  });

  app.get('/palaces/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const palace = db.select().from(wbPalace).where(eq(wbPalace.id, id)).get() as any;
    if (!palace) return reply.code(404).send({ message: 'not found' });
    const loci = db
      .select()
      .from(wbPalaceLoci)
      .where(eq(wbPalaceLoci.palaceId, id))
      .orderBy(asc(wbPalaceLoci.sortOrder), asc(wbPalaceLoci.id))
      .all();
    return { ...palaceVO(palace), loci: loci.map(lociVO) };
  });

  app.post('/palaces', async (req, reply) => {
    const b = req.body as any;
    if (!b.name) return reply.code(400).send({ message: 'name required' });
    const now = nowIso();
    const row = db
      .insert(wbPalace)
      .values({
        userId: CURRENT_USER,
        name: b.name,
        description: b.description ?? null,
        theme: b.theme ?? 'ROOM',
        coverColor: b.coverColor ?? null,
        categoryId: b.categoryId ?? null,
        createdAt: now,
        updatedAt: now,
      })
      .returning()
      .get();
    return row.id;
  });

  app.put('/palaces/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const b = req.body as any;
    const ex = db.select().from(wbPalace).where(eq(wbPalace.id, id)).get() as any;
    if (!ex) return reply.code(404).send({ message: 'not found' });
    db.update(wbPalace)
      .set({
        name: b.name ?? ex.name,
        description: b.description ?? ex.description,
        theme: b.theme ?? ex.theme,
        coverColor: b.coverColor ?? ex.coverColor,
        categoryId: b.categoryId !== undefined ? b.categoryId : ex.categoryId,
        updatedAt: nowIso(),
      })
      .where(eq(wbPalace.id, id))
      .run();
    return palaceVO(db.select().from(wbPalace).where(eq(wbPalace.id, id)).get() as any);
  });

  app.delete('/palaces/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    db.delete(wbPalaceLoci).where(eq(wbPalaceLoci.palaceId, id)).run();
    db.delete(wbPalace).where(eq(wbPalace.id, id)).run();
    return reply.code(204).send();
  });

  app.get('/palaces/:palaceId/loci', async (req) => {
    const palaceId = Number((req.params as any).palaceId);
    return db
      .select()
      .from(wbPalaceLoci)
      .where(eq(wbPalaceLoci.palaceId, palaceId))
      .orderBy(asc(wbPalaceLoci.sortOrder), asc(wbPalaceLoci.id))
      .all()
      .map(lociVO);
  });

  app.post('/loci', async (req, reply) => {
    const b = req.body as any;
    if (!b.name || b.palaceId === undefined) {
      return reply.code(400).send({ message: 'palaceId & name required' });
    }
    const now = nowIso();
    const maxIdx = db
      .select({ m: sql<number>`COALESCE(MAX(${wbPalaceLoci.sortOrder}), -1)` })
      .from(wbPalaceLoci)
      .where(eq(wbPalaceLoci.palaceId, Number(b.palaceId)))
      .get() as any;
    const row = db
      .insert(wbPalaceLoci)
      .values({
        palaceId: Number(b.palaceId),
        userId: CURRENT_USER,
        name: b.name,
        knowledgePoint: b.knowledgePoint ?? null,
        imageHint: b.imageHint ?? null,
        icon: b.icon ?? null,
        posX: b.posX ?? 50,
        posY: b.posY ?? 50,
        sortOrder: b.sortOrder ?? (maxIdx?.m ?? -1) + 1,
        captureId: b.captureId ?? null,
        noteId: b.noteId ?? null,
        categoryId: b.categoryId ?? null,
        createdAt: now,
        updatedAt: now,
      })
      .returning()
      .get();
    return row.id;
  });

  app.put('/loci/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    const b = req.body as any;
    const ex = db.select().from(wbPalaceLoci).where(eq(wbPalaceLoci.id, id)).get() as any;
    if (!ex) return reply.code(404).send({ message: 'not found' });
    db.update(wbPalaceLoci)
      .set({
        palaceId: b.palaceId !== undefined ? Number(b.palaceId) : ex.palaceId,
        name: b.name ?? ex.name,
        knowledgePoint: b.knowledgePoint ?? ex.knowledgePoint,
        imageHint: b.imageHint ?? ex.imageHint,
        icon: b.icon ?? ex.icon,
        posX: b.posX !== undefined ? b.posX : ex.posX,
        posY: b.posY !== undefined ? b.posY : ex.posY,
        sortOrder: b.sortOrder !== undefined ? b.sortOrder : ex.sortOrder,
        captureId: b.captureId !== undefined ? b.captureId : ex.captureId,
        noteId: b.noteId !== undefined ? b.noteId : ex.noteId,
        categoryId: b.categoryId !== undefined ? b.categoryId : ex.categoryId,
        updatedAt: nowIso(),
      })
      .where(eq(wbPalaceLoci.id, id))
      .run();
    return lociVO(db.select().from(wbPalaceLoci).where(eq(wbPalaceLoci.id, id)).get() as any);
  });

  app.delete('/loci/:id', async (req, reply) => {
    const id = Number((req.params as any).id);
    db.delete(wbPalaceLoci).where(eq(wbPalaceLoci.id, id)).run();
    return reply.code(204).send();
  });
}
