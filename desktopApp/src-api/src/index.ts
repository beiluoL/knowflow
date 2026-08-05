import Fastify from 'fastify';
import cors from '@fastify/cors';
import staticPlugin from '@fastify/static';
import fs from 'node:fs';
import path from 'node:path';

import { resolvePort, resolveWebDir } from './lib/paths';
import captures from './routes/captures';
import notes from './routes/notes';
import reviews from './routes/reviews';
import palaces from './routes/palaces';
import recall from './routes/recall';
import stories from './routes/stories';
import overview from './routes/overview';
import categories from './routes/categories';
import migration from './routes/migration';

const app = Fastify({ logger: false });

app.register(cors, { origin: true });

app.setErrorHandler((err, _req, reply) => {
  if (err.validation) return reply.code(400).send({ error: 'invalid request', detail: err.message });
  if ((err as any).statusCode) return reply.code((err as any).statusCode).send({ error: err.message });
  console.error(err);
  return reply.code(500).send({ error: 'internal error' });
});

// ===== 注册学习工作台路由（与 Web 端 /api/workbench/* 契约对齐）=====
const wbPrefix = '/api/workbench';
for (const r of [overview, captures, notes, reviews, palaces, recall, stories, migration]) {
  app.register(r, { prefix: wbPrefix });
}
app.register(categories, { prefix: '/api/categories' });

// ===== 同源托管前端构建产物（生产由 Tauri 传入 --web-dir）=====
const webDir = resolveWebDir();
if (webDir && fs.existsSync(webDir)) {
  app.register(staticPlugin, { root: webDir, wildcard: false });
  app.setNotFoundHandler((req, reply) => {
    if (req.url.startsWith('/api/')) return reply.code(404).send({ error: 'not found' });
    return reply.sendFile('index.html');
  });
} else {
  app.get('/', async () => ({ app: 'KnowFlow 学习工作台桌面后端', status: 'ok', note: '前端未构建或 --web-dir 未指定' }));
}

// ===== 启动：仅绑定回环地址，动态端口避让占用 =====
async function start() {
  let port = resolvePort();
  for (let i = 0; i < 20; i++) {
    try {
      await app.listen({ port, host: '127.0.0.1' });
      console.log(`[knowflow-desktop] API on http://127.0.0.1:${port}`);
      console.log(`[knowflow-desktop] web dir: ${webDir ?? '(none)'}`);
      return;
    } catch (e: any) {
      if (e.code === 'EADDRINUSE') {
        port += 1;
        continue;
      }
      throw e;
    }
  }
  throw new Error('无法找到可用端口');
}

start().catch((e) => {
  console.error(e);
  process.exit(1);
});

export default app;
