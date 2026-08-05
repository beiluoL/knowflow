// SM-2 间隔重复算法 —— 精确平移自 Web 端 WorkbenchServiceImpl.gradeReview
// 常量与公式保持一致，确保复习排程行为跨端一致（避免算法漂移 R2 风险）

export const DEFAULT_EF = 250; // 2.50
export const MIN_EF = 130; // 1.30
export const PASS_QUALITY = 2; // quality >= 2 视为通过，< 2 视为遗忘

export interface Sm2State {
  easeFactor: number;
  repetitions: number;
  intervalDay: number;
  lapseCount: number;
  reviewCount: number;
}

export interface Sm2Result extends Sm2State {
  quality: number;
  lapsed: boolean;
  nextReviewDays: number;
}

export function gradeCard(state: Sm2State, qualityInput: number): Sm2Result {
  const quality = Math.min(3, Math.max(0, Math.round(qualityInput)));
  let ef = state.easeFactor ?? DEFAULT_EF;
  let repetitions = state.repetitions ?? 0;
  let interval = state.intervalDay ?? 0;
  const lapsed = quality < PASS_QUALITY;

  // EF' = EF + (0.1 - (5-q)(0.08 + (5-q)*0.02))，下限 MIN_EF
  const q = quality;
  const efDouble = ef / 100 + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
  ef = Math.round(Math.max(MIN_EF / 100, efDouble) * 100);

  if (lapsed) {
    repetitions = 0;
    interval = 1;
  } else {
    repetitions += 1;
    if (repetitions === 1) {
      interval = 1;
    } else if (repetitions === 2) {
      interval = 6;
    } else {
      interval = Math.round(interval * (ef / 100));
    }
  }

  const lapseCount = (state.lapseCount ?? 0) + (lapsed ? 1 : 0);
  const reviewCount = (state.reviewCount ?? 0) + 1;

  return {
    easeFactor: ef,
    repetitions,
    intervalDay: interval,
    lapseCount,
    reviewCount,
    quality,
    lapsed,
    nextReviewDays: interval,
  };
}
