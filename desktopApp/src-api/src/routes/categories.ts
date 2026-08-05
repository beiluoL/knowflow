import { FastifyInstance } from 'fastify';
import { db } from '../db';
import { categories } from '../db/schema';
import { asc } from 'drizzle-orm';

// 本地分类树（替代线上 doc_category），前端收集箱/笔记/宫殿等归类下拉使用
export default async function (app: FastifyInstance) {
  app.get('/tree', async () => {
    return db.select().from(categories).orderBy(asc(categories.sort), asc(categories.id)).all();
  });

  app.post('/tree', async (req, reply) => {
    const b = req.body as any;
    if (!b.name) return reply.code(400).send({ error: 'name required' });
    const row = db.insert(categories).values({ name: b.name, parentId: b.parentId ?? 0, sort: b.sort ?? 0 }).returning().get();
    return reply.code(201).send(row);
  });
}
