// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const callContract = {
  "version": 1,
  "id": "callchip",
  "publicName": "KfCallChip",
  "package": "@beiluol/knowflow-ui",
  "description": "单次操作状态、单调计时与真实进度；预测填充不代表实际百分比，重试仅派发事件。",
  "source": "packages/ui/src/components/call-chip/KfCallChip.vue",
  "status": "implemented",
  "props": {
    "name": "searchKnowledge",
    "argument": "Java 并发机制",
    "iconName": "search",
    "status": "idle",
    "expectedMs": 2500,
    "progress": null,
    "elapsedMs": null,
    "showTimer": true,
    "runId": 0,
    "size": "md",
    "retryable": false,
    "retryDisabled": false,
    "retryBusy": false,
    "animated": true,
    "ariaLabel": ""
  },
  "propTypes": {
    "name": "string",
    "argument": "string",
    "iconName": "string",
    "status": "string",
    "expectedMs": "number",
    "progress": "number",
    "elapsedMs": "number",
    "showTimer": "boolean",
    "runId": "number",
    "size": "string",
    "retryable": "boolean",
    "retryDisabled": "boolean",
    "retryBusy": "boolean",
    "animated": "boolean",
    "ariaLabel": "string"
  },
  "emits": [
    "retry"
  ],
  "slots": [
    "default",
    "icon",
    "argument",
    "status"
  ],
  "stories": [
    "basic",
    "idle",
    "running",
    "done",
    "error",
    "retryable-error",
    "with-argument",
    "without-argument",
    "terminal-icon",
    "search-icon",
    "file-icon",
    "custom-icon",
    "expected-duration",
    "controlled-elapsed",
    "real-progress",
    "long-running",
    "compact",
    "large",
    "dark-mode",
    "reduced-motion",
    "multiple-instances",
    "agent-trace-composition",
    "status-transitions"
  ],
  "storyProps": {
    "basic": {},
    "idle": {},
    "running": {
      "status": "running"
    },
    "done": {
      "status": "done",
      "elapsedMs": 1830
    },
    "error": {
      "status": "error",
      "elapsedMs": 4200
    },
    "retryable-error": {
      "status": "error",
      "retryable": true,
      "elapsedMs": 4200
    },
    "with-argument": {
      "argument": "Java 并发机制 / transactions / workspace / a deliberately long argument summary with safe truncation"
    },
    "without-argument": {
      "argument": ""
    },
    "terminal-icon": {
      "iconName": "code"
    },
    "search-icon": {},
    "file-icon": {
      "iconName": "file-text"
    },
    "custom-icon": {
      "iconName": "settings"
    },
    "expected-duration": {
      "status": "running",
      "expectedMs": 1000
    },
    "controlled-elapsed": {
      "status": "running",
      "elapsedMs": 1830
    },
    "real-progress": {
      "status": "running",
      "progress": 45
    },
    "long-running": {
      "status": "running",
      "elapsedMs": 120000,
      "expectedMs": 1000
    },
    "compact": {
      "size": "sm"
    },
    "large": {
      "size": "lg"
    },
    "dark-mode": {},
    "reduced-motion": {
      "animated": false,
      "status": "running"
    },
    "multiple-instances": {},
    "agent-trace-composition": {},
    "status-transitions": {
      "retryable": true
    }
  }
} as const
