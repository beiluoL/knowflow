// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const markContract = {
  "version": 1,
  "id": "statusmark",
  "publicName": "KfStatusMark",
  "package": "@beiluol/knowflow-ui",
  "description": "五态任务标记、稳定SVG图层与真实0–1进度；纯展示，不执行任务。",
  "source": "packages/ui/src/components/status-mark/KfStatusMark.vue",
  "status": "implemented",
  "props": {
    "status": "pending",
    "progress": null,
    "label": "知识库索引任务",
    "size": 20,
    "strokeWidth": 2,
    "dashes": 8,
    "spinDuration": 1000,
    "arcLength": 0.28,
    "drawDuration": null,
    "strike": false,
    "strikeDelay": 0,
    "animated": true,
    "ariaLabel": ""
  },
  "propTypes": {
    "status": "string",
    "progress": "number",
    "label": "string",
    "size": "number",
    "strokeWidth": "number",
    "dashes": "number",
    "spinDuration": "number",
    "arcLength": "number",
    "drawDuration": "number",
    "strike": "boolean",
    "strikeDelay": "number",
    "animated": "boolean",
    "ariaLabel": "string"
  },
  "emits": [],
  "slots": [
    "default"
  ],
  "stories": [
    "basic",
    "pending",
    "running-indeterminate",
    "running-progress25",
    "running-progress50",
    "running-progress100",
    "done",
    "failed",
    "cancelled",
    "status-lifecycle",
    "with-label",
    "without-label",
    "with-strike",
    "without-strike",
    "small",
    "large",
    "custom-stroke",
    "custom-dashes",
    "slow-animation",
    "fast-animation",
    "reduced-motion",
    "dark-mode",
    "task-list",
    "agent-steps",
    "dynamic-progress",
    "multiple-instances"
  ],
  "storyProps": {
    "basic": {},
    "pending": {},
    "running-indeterminate": {
      "status": "running"
    },
    "running-progress25": {
      "status": "running",
      "progress": 0.25
    },
    "running-progress50": {
      "status": "running",
      "progress": 0.5
    },
    "running-progress100": {
      "status": "running",
      "progress": 1
    },
    "done": {
      "status": "done"
    },
    "failed": {
      "status": "failed"
    },
    "cancelled": {
      "status": "cancelled"
    },
    "status-lifecycle": {},
    "with-label": {
      "label": "中文 English 42 · a long task name with a naturally wrapping label that remains readable in narrow containers. 中文 English 42 · a long task name with a naturally wrapping label that remains readable in narrow containers. 中文 English 42 · a long task name with a naturally wrapping label that remains readable in narrow containers. "
    },
    "without-label": {
      "label": ""
    },
    "with-strike": {
      "status": "done",
      "strike": true,
      "strikeDelay": 100
    },
    "without-strike": {
      "status": "done",
      "strike": false
    },
    "small": {
      "size": 14
    },
    "large": {
      "size": 40
    },
    "custom-stroke": {
      "strokeWidth": 3.5,
      "size": 28
    },
    "custom-dashes": {
      "dashes": 12
    },
    "slow-animation": {
      "status": "running",
      "spinDuration": 2400,
      "drawDuration": 800
    },
    "fast-animation": {
      "status": "running",
      "spinDuration": 500,
      "drawDuration": 120
    },
    "reduced-motion": {
      "status": "running",
      "animated": false
    },
    "dark-mode": {},
    "task-list": {},
    "agent-steps": {},
    "dynamic-progress": {
      "status": "running",
      "progress": 0
    },
    "multiple-instances": {}
  }
} as const
