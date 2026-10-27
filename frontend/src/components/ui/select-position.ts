/** 只计算视口几何；业务、焦点和生命周期由各容器管理。 */
export interface SelectRect {
  left: number;
  right: number;
  top: number;
  bottom: number;
  width: number;
}
const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(value, Math.max(min, max)));
export interface PanelViewport {
  width: number;
  height: number;
  /** visualViewport 在布局视口中的偏移，普通页面缩放时为 0。 */
  left?: number;
  top?: number;
}

export interface AnchoredPanelPlacement {
  left: number;
  top: number;
  width: number;
  maxHeight: number;
  placement: 'top' | 'bottom';
}

/**
 * 日期/时间浮层统一几何合同：以真实输入框为锚点，下方优先，空间不足翻转上方，
 * 右缘向左修正，四周保留安全间距，并把面板高度压进可见区域（内部滚动）。
 * 只做几何计算，事件监听、ResizeObserver 与焦点由 useAnchoredPanel 负责。
 */
export function placeAnchoredPanel(
  anchor: SelectRect,
  width: number,
  height: number,
  viewport: PanelViewport,
  options: { gap?: number; margin?: number } = {},
): AnchoredPanelPlacement {
  const margin = options.margin ?? 8;
  const gap = options.gap ?? 4;
  const leftBound = viewport.left ?? 0;
  const topBound = viewport.top ?? 0;
  const rightBound = leftBound + viewport.width;
  const bottomBound = topBound + viewport.height;
  const maxWidth = Math.max(0, viewport.width - margin * 2);
  const maxHeight = Math.max(0, viewport.height - margin * 2);
  const panelWidth = Math.min(width, maxWidth);
  const panelHeight = Math.min(height, maxHeight);
  const spaceBelow = bottomBound - anchor.bottom - gap - margin;
  const spaceAbove = anchor.top - topBound - gap - margin;
  const placement: 'top' | 'bottom' =
    spaceBelow >= panelHeight || spaceBelow >= spaceAbove ? 'bottom' : 'top';
  const available = Math.max(0, placement === 'bottom' ? spaceBelow : spaceAbove);
  const finalHeight = Math.min(panelHeight, available);
  const top =
    placement === 'bottom' ? anchor.bottom + gap : anchor.top - gap - finalHeight;
  return {
    left: clamp(anchor.left, leftBound + margin, rightBound - panelWidth - margin),
    top: clamp(top, topBound + margin, bottomBound - finalHeight - margin),
    width: panelWidth,
    maxHeight: finalHeight,
    placement,
  };
}

/** 读取当前可见视口；实验环境无 visualViewport 时退回 window。 */
export function viewportBounds(): PanelViewport {
  const visual = typeof window === 'undefined' ? null : window.visualViewport;
  if (visual) {
    return {
      width: visual.width,
      height: visual.height,
      left: visual.offsetLeft,
      top: visual.offsetTop,
    };
  }
  return {
    width: typeof window === 'undefined' ? 0 : window.innerWidth,
    height: typeof window === 'undefined' ? 0 : window.innerHeight,
  };
}

export function placeSelect(
  anchor: SelectRect,
  width: number,
  height: number,
  viewport: { width: number; height: number },
  nested = false,
  preferLeft = false,
) {
  const margin = 8,
    gap = 4,
    w = Math.min(width, Math.max(0, viewport.width - margin * 2)),
    h = Math.min(height, Math.max(0, viewport.height - margin * 2));
  const left = nested
    ? preferLeft && anchor.left - gap - w >= margin
      ? anchor.left - gap - w
      : anchor.right + gap + w <= viewport.width - margin
        ? anchor.right + gap
        : anchor.left - gap - w
    : anchor.left;
  const below = viewport.height - anchor.bottom - gap - margin,
    above = anchor.top - gap - margin;
  const availableHeight = nested ? h : Math.max(0, Math.min(h, Math.max(below, above)));
  const top = nested
    ? anchor.top
    : below < h && above > below
      ? anchor.top - gap - availableHeight
      : anchor.bottom + gap;
  return {
    left: clamp(left, margin, viewport.width - w - margin),
    top: clamp(top, margin, viewport.height - availableHeight - margin),
    width: w,
    maxHeight: availableHeight,
  };
}
