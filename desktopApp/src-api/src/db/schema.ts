import { sqliteTable, integer, text, real } from 'drizzle-orm/sqlite-core';

// ===== 本地分类（替代线上 doc_category）=====
export const categories = sqliteTable('categories', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  name: text('name').notNull(),
  parentId: integer('parent_id').notNull().default(0),
  sort: integer('sort').notNull().default(0),
});

// ===== 模块一：收集箱（字段与 Web 端 WbCapture 对齐）=====
export const wbCapture = sqliteTable('wb_capture', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: integer('user_id').notNull().default(1),
  title: text('title').notNull(),
  content: text('content'),
  // Web: sourceType(MANUAL/DOC/WEB/AI/IMPORT) / sourceUrl / docId / tags
  sourceType: text('source_type'),
  sourceUrl: text('source_url'),
  docId: integer('doc_id'),
  categoryId: integer('category_id'),
  tags: text('tags'),
  // 状态大写对齐 Web：INBOX / PROCESSED / ARCHIVED
  status: text('status').notNull().default('INBOX'),
  starred: integer('starred').notNull().default(0),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
});

// ===== 模块二：康奈尔笔记（字段与 Web 端 WbNote 对齐）=====
export const wbNote = sqliteTable('wb_note', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: integer('user_id').notNull().default(1),
  captureId: integer('capture_id'),
  categoryId: integer('category_id'),
  title: text('title').notNull(),
  // Web: cueColumn / noteColumn / summaryColumn / tags / mastery
  cueColumn: text('cue_column').notNull().default(''),
  noteColumn: text('note_column').notNull().default(''),
  summaryColumn: text('summary_column').notNull().default(''),
  tags: text('tags'),
  mastery: integer('mastery').notNull().default(0),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
});

// ===== 模块三：间隔重复（SM-2）=====
export const wbReviewCard = sqliteTable('wb_review_card', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: integer('user_id').notNull().default(1),
  captureId: integer('capture_id'),
  noteId: integer('note_id'),
  categoryId: integer('category_id'),
  front: text('front').notNull(),
  back: text('back').notNull().default(''),
  cardType: text('card_type').notNull().default('basic'),
  easeFactor: integer('ease_factor').notNull().default(250),
  repetitions: integer('repetitions').notNull().default(0),
  intervalDay: integer('interval_day').notNull().default(0),
  reviewCount: integer('review_count').notNull().default(0),
  lapseCount: integer('lapse_count').notNull().default(0),
  nextReviewTime: text('next_review_time').notNull(),
  lastReviewTime: text('last_review_time'),
  suspended: integer('suspended').notNull().default(0),
});

export const wbReviewLog = sqliteTable('wb_review_log', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: integer('user_id').notNull().default(1),
  cardId: integer('card_id').notNull(),
  quality: integer('quality').notNull(),
  intervalDay: integer('interval_day').notNull(),
  easeFactor: integer('ease_factor').notNull(),
  costMs: integer('cost_ms'),
  reviewedAt: text('reviewed_at').notNull(),
});

// ===== 模块三扩展：记忆宫殿（字段与 Web 端 WbPalace / WbPalaceLoci 对齐）=====
export const wbPalace = sqliteTable('wb_palace', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: integer('user_id').notNull().default(1),
  name: text('name').notNull(),
  description: text('description'),
  // Web: theme(ROOM/STREET/CAMPUS/CUSTOM) / coverColor / categoryId
  theme: text('theme'),
  coverColor: text('cover_color'),
  categoryId: integer('category_id'),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
});

export const wbPalaceLoci = sqliteTable('wb_palace_loci', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  palaceId: integer('palace_id').notNull(),
  userId: integer('user_id').notNull().default(1),
  // Web: name / knowledgePoint / imageHint / icon / posX / posY / sortOrder
  name: text('name').notNull(),
  knowledgePoint: text('knowledge_point'),
  imageHint: text('image_hint'),
  icon: text('icon'),
  posX: real('pos_x'),
  posY: real('pos_y'),
  sortOrder: integer('sort_order').notNull().default(0),
  captureId: integer('capture_id'),
  noteId: integer('note_id'),
  categoryId: integer('category_id'),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
});

// ===== 模块三扩展：主动回忆（三轮闭卷默写，字段与 Web 端 WbRecallSession 对齐）=====
export const wbRecallSession = sqliteTable('wb_recall_session', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: integer('user_id').notNull().default(1),
  noteId: integer('note_id'),
  cardId: integer('card_id'),
  title: text('title').notNull(),
  sourceText: text('source_text').notNull().default(''),
  // 三轮默写展开列（对齐 Web round1/2/3Text + Score）
  round1Text: text('round1_text'),
  round1Score: integer('round1_score'),
  round2Text: text('round2_text'),
  round2Score: integer('round2_score'),
  round3Text: text('round3_text'),
  round3Score: integer('round3_score'),
  currentRound: integer('current_round').notNull().default(1),
  // 状态大写：IN_PROGRESS / COMPLETED
  status: text('status').notNull().default('IN_PROGRESS'),
  round3DueTime: text('round3_due_time'),
  completedTime: text('completed_time'),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
});

// ===== 模块四：费曼故事（字段与 Web 端 WbStory 对齐）=====
export const wbStory = sqliteTable('wb_story', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: integer('user_id').notNull().default(1),
  captureId: integer('capture_id'),
  noteId: integer('note_id'),
  categoryId: integer('category_id'),
  title: text('title').notNull(),
  // Web: audience / metaphor / gapNote / clarityScore / wordCount
  audience: text('audience'),
  metaphor: text('metaphor'),
  content: text('content').notNull().default(''),
  gapNote: text('gap_note'),
  status: text('status').notNull().default('DRAFT'),
  clarityScore: integer('clarity_score'),
  wordCount: integer('word_count'),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
});
