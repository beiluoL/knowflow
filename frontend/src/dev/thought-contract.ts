// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const thoughtContract = {
  "version": 1,
  "id": "thoughtline",
  "publicName": "KfThoughtLine",
  "package": "@beiluol/knowflow-ai",
  "description": "AI 执行状态摘要：宿主五态、真实观察计时、轻量步骤与可折叠轨迹，不生成推理内容",
  "source": "packages/ai/src/thought-line/KfThoughtLine.vue",
  "status": "implemented",
  "props": {
    "status": "working",
    "runId": "fixture-1",
    "label": "处理中",
    "idleLabel": "等待处理",
    "doneLabel": "已完成处理",
    "errorLabel": "处理失败",
    "cancelledLabel": "已停止",
    "glyph": "sparkle",
    "steps": [
      {
        "id": "retrieve",
        "label": "检索演示资料",
        "status": "done",
        "elapsed": 1.5
      },
      {
        "id": "parallel",
        "label": "整理演示证据",
        "status": "running"
      },
      {
        "id": "answer",
        "label": "等待演示结果",
        "status": "pending"
      }
    ],
    "open": null,
    "defaultOpen": true,
    "collapsible": true,
    "collapseOnSettle": true,
    "showTimer": true,
    "elapsed": null,
    "formatElapsed": null,
    "breathPeriod": 1.8,
    "breathDepth": 0.25,
    "shimmer": true,
    "shimmerDuration": 2.4,
    "settleDuration": 240,
    "animated": true
  },
  "propTypes": {
    "status": "KfThoughtLineStatus",
    "runId": "string | number",
    "label": "string",
    "idleLabel": "string",
    "doneLabel": "string",
    "errorLabel": "string",
    "cancelledLabel": "string",
    "glyph": "KfThoughtGlyph",
    "steps": "KfThoughtStep[]",
    "open": "boolean | undefined",
    "defaultOpen": "boolean",
    "collapsible": "boolean",
    "collapseOnSettle": "boolean",
    "showTimer": "boolean",
    "elapsed": "number | undefined",
    "formatElapsed": "(seconds: number) => string",
    "breathPeriod": "number",
    "breathDepth": "number",
    "shimmer": "boolean",
    "shimmerDuration": "number",
    "settleDuration": "number",
    "animated": "boolean"
  },
  "emits": [
    "update:open",
    "open-change",
    "settle"
  ],
  "slots": [
    "glyph",
    "working-label",
    "settled-label",
    "step",
    "trace",
    "empty"
  ],
  "stories": [
    "basic",
    "working",
    "done",
    "failed",
    "cancelled",
    "with-timer",
    "without-timer",
    "controlled-elapsed",
    "internal-timer",
    "sparkle-glyph",
    "dot-glyph",
    "no-glyph",
    "breathing-animation",
    "shimmer-enabled",
    "shimmer-disabled",
    "with-steps",
    "dynamic-steps",
    "collapsible",
    "expanded",
    "collapsed",
    "collapse-on-settle",
    "keep-expanded-on-settle",
    "custom-labels",
    "long-labels",
    "controlled-open",
    "run-id-reset",
    "trace-slot",
    "agent-trace-composition",
    "rag-trace",
    "error-recovery",
    "light-mode",
    "dark-mode",
    "reduced-motion",
    "mobile",
    "keyboard-navigation",
    "multiple-instances"
  ],
  "storyProps": {
    "basic": {},
    "working": {},
    "done": {
      "status": "done",
      "elapsed": 4.2,
      "defaultOpen": false,
      "steps": [
        {
          "id": "retrieve",
          "label": "检索演示资料",
          "status": "done",
          "elapsed": 1.5
        },
        {
          "id": "parallel",
          "label": "整理演示证据",
          "status": "done"
        },
        {
          "id": "answer",
          "label": "等待演示结果",
          "status": "pending"
        }
      ]
    },
    "failed": {
      "status": "failed",
      "elapsed": 4.2,
      "defaultOpen": false,
      "steps": [
        {
          "id": "retrieve",
          "label": "检索演示资料",
          "status": "done",
          "elapsed": 1.5
        },
        {
          "id": "parallel",
          "label": "整理演示证据",
          "status": "failed"
        },
        {
          "id": "answer",
          "label": "等待演示结果",
          "status": "pending"
        }
      ]
    },
    "cancelled": {
      "status": "cancelled",
      "elapsed": 4.2,
      "defaultOpen": false,
      "steps": [
        {
          "id": "retrieve",
          "label": "检索演示资料",
          "status": "done",
          "elapsed": 1.5
        },
        {
          "id": "parallel",
          "label": "整理演示证据",
          "status": "cancelled"
        },
        {
          "id": "answer",
          "label": "等待演示结果",
          "status": "pending"
        }
      ]
    },
    "with-timer": {},
    "without-timer": {
      "showTimer": false
    },
    "controlled-elapsed": {
      "elapsed": 72.4
    },
    "internal-timer": {},
    "sparkle-glyph": {},
    "dot-glyph": {
      "glyph": "dot"
    },
    "no-glyph": {
      "glyph": "none"
    },
    "breathing-animation": {
      "shimmer": false,
      "breathDepth": 0.3
    },
    "shimmer-enabled": {},
    "shimmer-disabled": {
      "shimmer": false
    },
    "with-steps": {},
    "dynamic-steps": {},
    "collapsible": {},
    "expanded": {
      "defaultOpen": true
    },
    "collapsed": {
      "defaultOpen": false
    },
    "collapse-on-settle": {},
    "keep-expanded-on-settle": {
      "collapseOnSettle": false
    },
    "custom-labels": {
      "label": "正在整理资料",
      "doneLabel": "资料整理就绪",
      "errorLabel": "整理失败",
      "cancelledLabel": "已取消整理"
    },
    "long-labels": {
      "label": "处理中 Processing multilingual evidence collection",
      "doneLabel": "已完成资料整理与来源核对",
      "steps": [
        {
          "id": "long",
          "label": "这是一个超长中英文混合步骤标签 Long trace label 日本語 텍스트 📚 需要在窄屏完整换行而不产生横向滚动",
          "status": "running"
        }
      ]
    },
    "controlled-open": {
      "open": true
    },
    "run-id-reset": {},
    "trace-slot": {},
    "agent-trace-composition": {},
    "rag-trace": {
      "steps": [
        {
          "id": "search",
          "label": "Fixture：检索文档",
          "status": "done"
        },
        {
          "id": "rerank",
          "label": "Fixture：重排序候选",
          "status": "running"
        },
        {
          "id": "citations",
          "label": "Fixture：生成引用列表",
          "status": "pending"
        }
      ]
    },
    "error-recovery": {
      "status": "failed",
      "elapsed": 4.2,
      "defaultOpen": true,
      "steps": [
        {
          "id": "retrieve",
          "label": "检索演示资料",
          "status": "done",
          "elapsed": 1.5
        },
        {
          "id": "parallel",
          "label": "整理演示证据",
          "status": "failed"
        },
        {
          "id": "answer",
          "label": "等待演示结果",
          "status": "pending"
        }
      ]
    },
    "light-mode": {},
    "dark-mode": {},
    "reduced-motion": {
      "animated": false
    },
    "mobile": {},
    "keyboard-navigation": {},
    "multiple-instances": {}
  }
} as const
