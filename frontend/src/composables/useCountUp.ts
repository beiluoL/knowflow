import { ref, watch, type Ref } from 'vue';

/**
 * 数字 count-up 动画组合式函数
 *
 * 让一个数字从 0 平滑滚动到目标值，配合 requestAnimationFrame 实现 60fps 流畅动画。
 * 支持：自定义时长、缓动函数、 reduced-motion 守护（系统减少动效时直接跳到终值）。
 *
 * @param source 响应式数字源（Ref<number>），变化时自动重新滚动
 * @param duration 动画时长（毫秒），默认 1200ms
 * @returns { display } 响应式展示值，绑定到模板即可
 *
 * @example
 * const studyHours = ref(0);
 * const { display } = useCountUp(studyHours);
 * // 模板：{{ display }} 小时
 */
export function useCountUp(source: Ref<number>, duration = 1200) {
  const display = ref(0);
  let rafId = 0;

  // 缓动函数：easeOutExpo，让数字末段减速，更优雅
  const easeOutExpo = (t: number) => (t === 1 ? 1 : 1 - Math.pow(2, -10 * t));

  function animate(from: number, to: number) {
    if (from === to) {
      display.value = to;
      return;
    }

    // reduced-motion 守护：用户设置「减少动效」时直接跳到终值
    const prefersReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
      display.value = to;
      return;
    }

    cancelAnimationFrame(rafId);
    const start = performance.now();

    const tick = (now: number) => {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      const eased = easeOutExpo(progress);
      display.value = Math.round(from + (to - from) * eased);

      if (progress < 1) {
        rafId = requestAnimationFrame(tick);
      } else {
        display.value = to;
      }
    };

    rafId = requestAnimationFrame(tick);
  }

  watch(
    source,
    (newVal, oldVal) => {
      animate(oldVal ?? 0, newVal ?? 0);
    },
    { immediate: true },
  );

  return { display };
}
