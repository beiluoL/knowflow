import fs from 'node:fs';
import path from 'node:path';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import * as schema from './schema';
import { resolveDataDir } from '../lib/paths';

const dataDir = resolveDataDir();
fs.mkdirSync(dataDir, { recursive: true });
const dbPath = process.env.KNOWFLOW_DB || path.join(dataDir, 'workbench.db');

export const sqlite = new Database(dbPath);
// 单用户桌面应用：开 WAL 提升并发与崩溃安全
sqlite.pragma('journal_mode = WAL');
sqlite.pragma('foreign_keys = OFF'); // 逻辑外键，由应用层保证

// ===== DDL（幂等，首次运行建表；字段与 Web 端 Wb* 实体对齐）=====
sqlite.exec(`
CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  parent_id INTEGER NOT NULL DEFAULT 0,
  sort INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS wb_capture (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL DEFAULT 1,
  title TEXT NOT NULL,
  content TEXT,
  source_type TEXT,
  source_url TEXT,
  doc_id INTEGER,
  category_id INTEGER,
  tags TEXT,
  status TEXT NOT NULL DEFAULT 'INBOX',
  starred INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS wb_note (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL DEFAULT 1,
  capture_id INTEGER,
  category_id INTEGER,
  title TEXT NOT NULL,
  cue_column TEXT NOT NULL DEFAULT '',
  note_column TEXT NOT NULL DEFAULT '',
  summary_column TEXT NOT NULL DEFAULT '',
  tags TEXT,
  mastery INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS wb_review_card (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL DEFAULT 1,
  capture_id INTEGER,
  note_id INTEGER,
  category_id INTEGER,
  front TEXT NOT NULL,
  back TEXT NOT NULL DEFAULT '',
  card_type TEXT NOT NULL DEFAULT 'basic',
  ease_factor INTEGER NOT NULL DEFAULT 250,
  repetitions INTEGER NOT NULL DEFAULT 0,
  interval_day INTEGER NOT NULL DEFAULT 0,
  review_count INTEGER NOT NULL DEFAULT 0,
  lapse_count INTEGER NOT NULL DEFAULT 0,
  next_review_time TEXT NOT NULL,
  last_review_time TEXT,
  suspended INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS wb_review_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL DEFAULT 1,
  card_id INTEGER NOT NULL,
  quality INTEGER NOT NULL,
  interval_day INTEGER NOT NULL,
  ease_factor INTEGER NOT NULL,
  cost_ms INTEGER,
  reviewed_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS wb_palace (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL DEFAULT 1,
  name TEXT NOT NULL,
  description TEXT,
  theme TEXT,
  cover_color TEXT,
  category_id INTEGER,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS wb_palace_loci (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  palace_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL DEFAULT 1,
  name TEXT NOT NULL,
  knowledge_point TEXT,
  image_hint TEXT,
  icon TEXT,
  pos_x REAL,
  pos_y REAL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  capture_id INTEGER,
  note_id INTEGER,
  category_id INTEGER,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS wb_recall_session (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL DEFAULT 1,
  note_id INTEGER,
  card_id INTEGER,
  title TEXT NOT NULL,
  source_text TEXT NOT NULL DEFAULT '',
  round1_text TEXT,
  round1_score INTEGER,
  round2_text TEXT,
  round2_score INTEGER,
  round3_text TEXT,
  round3_score INTEGER,
  current_round INTEGER NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
  round3_due_time TEXT,
  completed_time TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS wb_story (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL DEFAULT 1,
  capture_id INTEGER,
  note_id INTEGER,
  category_id INTEGER,
  title TEXT NOT NULL,
  audience TEXT,
  metaphor TEXT,
  content TEXT NOT NULL DEFAULT '',
  gap_note TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  clarity_score INTEGER,
  word_count INTEGER,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
`);

// ===== 向后兼容：旧库增量补齐新列（PRAGMA 探测存在性，幂等安全）=====
function addColumn(table: string, col: string, def: string) {
  const cols = sqlite.prepare(`PRAGMA table_info(${table})`).all() as Array<{ name: string }>;
  if (cols.some((c) => c.name === col)) return;
  sqlite.exec(`ALTER TABLE ${table} ADD COLUMN ${col} ${def}`);
}

addColumn('wb_capture', 'source_type', 'TEXT');
addColumn('wb_capture', 'source_url', 'TEXT');
addColumn('wb_capture', 'doc_id', 'INTEGER');
addColumn('wb_capture', 'tags', 'TEXT');
addColumn('wb_note', 'cue_column', "TEXT NOT NULL DEFAULT ''");
addColumn('wb_note', 'note_column', "TEXT NOT NULL DEFAULT ''");
addColumn('wb_note', 'summary_column', "TEXT NOT NULL DEFAULT ''");
addColumn('wb_note', 'tags', 'TEXT');
addColumn('wb_note', 'mastery', 'INTEGER NOT NULL DEFAULT 0');
addColumn('wb_palace', 'theme', 'TEXT');
addColumn('wb_palace', 'cover_color', 'TEXT');
addColumn('wb_palace', 'category_id', 'INTEGER');
addColumn('wb_palace', 'created_at', 'TEXT');
addColumn('wb_palace', 'updated_at', 'TEXT');
addColumn('wb_palace_loci', 'name', 'TEXT');
addColumn('wb_palace_loci', 'knowledge_point', 'TEXT');
addColumn('wb_palace_loci', 'image_hint', 'TEXT');
addColumn('wb_palace_loci', 'icon', 'TEXT');
addColumn('wb_palace_loci', 'pos_x', 'REAL');
addColumn('wb_palace_loci', 'pos_y', 'REAL');
addColumn('wb_palace_loci', 'sort_order', 'INTEGER NOT NULL DEFAULT 0');
addColumn('wb_palace_loci', 'capture_id', 'INTEGER');
addColumn('wb_palace_loci', 'note_id', 'INTEGER');
addColumn('wb_palace_loci', 'category_id', 'INTEGER');
addColumn('wb_palace_loci', 'created_at', 'TEXT');
addColumn('wb_palace_loci', 'updated_at', 'TEXT');
addColumn('wb_recall_session', 'note_id', 'INTEGER');
addColumn('wb_recall_session', 'card_id', 'INTEGER');
addColumn('wb_recall_session', 'round1_text', 'TEXT');
addColumn('wb_recall_session', 'round1_score', 'INTEGER');
addColumn('wb_recall_session', 'round2_text', 'TEXT');
addColumn('wb_recall_session', 'round2_score', 'INTEGER');
addColumn('wb_recall_session', 'round3_text', 'TEXT');
addColumn('wb_recall_session', 'round3_score', 'INTEGER');
addColumn('wb_recall_session', 'current_round', 'INTEGER NOT NULL DEFAULT 1');
addColumn('wb_recall_session', 'status', "TEXT NOT NULL DEFAULT 'IN_PROGRESS'");
addColumn('wb_recall_session', 'round3_due_time', 'TEXT');
addColumn('wb_recall_session', 'completed_time', 'TEXT');
addColumn('wb_story', 'capture_id', 'INTEGER');
addColumn('wb_story', 'note_id', 'INTEGER');
addColumn('wb_story', 'audience', 'TEXT');
addColumn('wb_story', 'metaphor', 'TEXT');
addColumn('wb_story', 'gap_note', 'TEXT');
addColumn('wb_story', 'clarity_score', 'INTEGER');
addColumn('wb_story', 'word_count', 'INTEGER');

// 初始化默认分类（首次运行时）
const catCount = (sqlite.prepare('SELECT COUNT(*) AS c FROM categories').get() as { c: number }).c;
if (catCount === 0) {
  const insert = sqlite.prepare('INSERT INTO categories (name, parent_id, sort) VALUES (?, 0, ?)');
  ['未分类', '工作', '学习', '生活'].forEach((name, i) => insert.run(name, i));
}

export const db = drizzle(sqlite, { schema });

// 单本地用户
export const CURRENT_USER = 1;

export function nowIso(): string {
  return new Date().toISOString();
}
