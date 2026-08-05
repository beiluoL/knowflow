import path from 'node:path';
import fs from 'node:fs';
import os from 'node:os';

/**
 * 数据目录解析优先级：
 * 1. 环境变量 KNOWFLOW_DATA_DIR
 * 2. Tauri 传入的 --data-dir（生产）
 * 3. 兜底：<项目>/data（开发，便于调试）
 */
export function resolveDataDir(): string {
  if (process.env.KNOWFLOW_DATA_DIR) return process.env.KNOWFLOW_DATA_DIR;
  const idx = process.argv.indexOf('--data-dir');
  if (idx >= 0 && process.argv[idx + 1]) return process.argv[idx + 1];
  const fallback = path.resolve(__dirname, '..', '..', 'data');
  fs.mkdirSync(fallback, { recursive: true });
  return fallback;
}

/** 前端构建产物目录（由 Node 后端同源托管），生产由 Tauri 通过 --web-dir 传入 */
export function resolveWebDir(): string | null {
  const idx = process.argv.indexOf('--web-dir');
  if (idx >= 0 && process.argv[idx + 1]) return process.argv[idx + 1];
  const local = path.resolve(__dirname, '..', '..', '..', 'src-ui', 'dist');
  return fs.existsSync(local) ? local : null;
}

/** 解析监听端口，默认 8787，支持 --port 覆盖 */
export function resolvePort(): number {
  const idx = process.argv.indexOf('--port');
  if (idx >= 0 && process.argv[idx + 1]) {
    const p = parseInt(process.argv[idx + 1], 10);
    if (!Number.isNaN(p)) return p;
  }
  return 8787;
}
