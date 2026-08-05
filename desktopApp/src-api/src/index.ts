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
  if (err.validation) return reply.code(400).send({ code: 400, message: 'invalid request' });
  const status = (err as any).statusCode || 500;
  if (status >= 500) console.error(err);
  return reply.code(status).send({ code: status, message: err.message || 'internal error' });
});

// 容忍空请求体：部分客户端（如带 Content-Type: application/json 的空 body PUT）
// 会触发 Fastify 默认 JSON 解析器报错；对齐 Web 端忽略空体的行为，统一解析为 {}。
app.addContentTypeParser('application/json', { parseAs: 'string' }, (req, body, done) => {
  if (body === '' || body == null) return done(null, {});
  try {
    done(null, JSON.parse(body as string));
  } catch (e) {
    done(e as Error, undefined);
  }
});

/**
 * 统一响应信封：所有成功（2xx）的 JSON 响应包成 { code: 200, data }，
 * 与 Web 端 Result<T> 契约一致（前端 request.ts 解包 .data）。
 * 错误响应（>=400）与 204/非 JSON 响应保持原样透传。
 */
app.addHook('onSend', (req, reply, payload, done) => {
  const status = reply.statusCode;
  if (status >= 400) return done(null, payload as any);
  if (status === 204 || payload === undefined || payload === '') return done(null, payload as any);
  const ct = reply.getHeader('content-type');
  if (typeof payload !== 'string' || !String(ct).includes('application/json')) {
    return done(null, payload as any);
  }
  try {
    const parsed = JSON.parse(payload);
    if (
      parsed &&
      typeof parsed === 'object' &&
      !Array.isArray(parsed) &&
      'code' in parsed &&
      'data' in parsed
    ) {
      return done(null, payload);
    }
    return done(null, JSON.stringify({ code: 200, data: parsed }));
  } catch {
    return done(null, payload as any);
  }
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
    if (req.url.startsWith('/api/')) return reply.code(404).send({ code: 404, message: 'not found' });
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
