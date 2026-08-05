import { FastifyInstance } from 'fastify';
import { db } from '../db';
import {
  wbCapture,
  wbNote,
  wbReviewCard,
  wbPalace,
  wbPalaceLoci,
  wbStory,
  wbRecallSession,
  wbReviewLog,
} from '../db/schema';
import { CURRENT_USER } from '../db';
import { eq, and, sql } from 'drizzle-orm';

export default async function (app: FastifyInstance) {
  app.get('/overview', async () => {
    const now = new Date();
    const iso = now.toISOString();
    const since7 = new Date(now.getTime() - 7 * 86400000).toISOString();

    const count = (tbl: any, extra: any[] = []) => {
      const conds = [eq(tbl.userId, CURRENT_USER), ...extra];
      const r = db.select({ c: sql<number>`COUNT(*)` }).from(tbl).where(and(...conds)).get() as any;
      return r?.c ?? 0;
    };
    const countWhere = (tbl: any, where: any) => {
      const r = db.select({ c: sql<number>`COUNT(*)` }).from(tbl).where(where).get() as any;
      return r?.c ?? 0;
    };

    const captureTotal = count(wbCapture);
    const captureInbox = count(wbCapture, [eq(wbCapture.status, 'INBOX')]);
    const captureStarred = count(wbCapture, [eq(wbCapture.starred, 1)]);
    const noteTotal = count(wbNote);
    const reviewDue = countWhere(
      wbReviewCard,
      and(eq(wbReviewCard.userId, CURRENT_USER), eq(wbReviewCard.suspended, 0), sql`${wbReviewCard.nextReviewTime} <= ${iso}`),
    );
    const reviewCountRow = db
      .select({ s: sql<number>`COALESCE(SUM(${wbReviewCard.reviewCount}), 0)` })
      .from(wbReviewCard)
      .where(eq(wbReviewCard.userId, CURRENT_USER))
      .get() as any;
    const reviewCount = reviewCountRow?.s ?? 0;
    const palaceTotal = count(wbPalace);
    const lociTotal = count(wbPalaceLoci);
    const storyTotal = count(wbStory);
    const storyDraft = countWhere(
      wbStory,
      and(eq(wbStory.userId, CURRENT_USER), sql`${wbStory.status} IN ('DRAFT','DONE')`),
    );
    const reviewLast7d = countWhere(
      wbReviewLog,
      and(eq(wbReviewLog.userId, CURRENT_USER), sql`${wbReviewLog.reviewedAt} >= ${since7}`),
    );

    return {
      captureTotal,
      captureInbox,
      captureStarred,
      noteTotal,
      reviewDue,
      reviewCount,
      palaceTotal,
      lociTotal,
      storyTotal,
      storyDraft,
      reviewLast7d,
    };
  });
}
