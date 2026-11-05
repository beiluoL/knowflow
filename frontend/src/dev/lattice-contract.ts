// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const latticeContract = {
  "version": 1,
  "id": "latticeloader",
  "publicName": "KfLatticeLoader",
  "package": "@beiluol/knowflow-ui",
  "description": "Lattice Loader",
  "source": "packages/ui/src/components/lattice-loader/KfLatticeLoader.vue",
  "status": "implemented",
  "props": {
    "status": "working",
    "pattern": "orbit",
    "grid": 3,
    "shape": "round",
    "label": "思考中",
    "doneLabel": "已完成",
    "errorLabel": "处理失败",
    "showTimer": true,
    "elapsed": null,
    "resetKey": null,
    "cellSize": 4,
    "gap": 3,
    "step": 120,
    "idleOpacity": 0.2,
    "glow": false,
    "color": "var(--kf-foreground)",
    "doneColor": "var(--kf-state-success)",
    "errorColor": "var(--kf-state-error)"
  },
  "emits": [],
  "slots": [],
  "propTypes": {
    "status": "string",
    "pattern": "string",
    "grid": "number",
    "shape": "string",
    "label": "string",
    "doneLabel": "string",
    "errorLabel": "string",
    "showTimer": "boolean",
    "elapsed": "string",
    "resetKey": "string",
    "cellSize": "number",
    "gap": "number",
    "step": "number",
    "idleOpacity": "number",
    "glow": "boolean",
    "color": "string",
    "doneColor": "string",
    "errorColor": "string"
  },
  "stories": [
    "basic",
    "thinking",
    "processing",
    "done",
    "error",
    "grid3",
    "grid4",
    "round",
    "square",
    "orbit",
    "ripple",
    "snake",
    "controlled-elapsed",
    "internal-timer",
    "no-timer",
    "compact",
    "dark",
    "reduced-motion",
    "multiple-instances",
    "status-transition"
  ],
  "storyProps": {
    "basic": {},
    "thinking": {},
    "processing": {
      "label": "正在解析文档"
    },
    "done": {
      "status": "done"
    },
    "error": {
      "status": "error"
    },
    "grid3": {},
    "grid4": {
      "grid": 4
    },
    "round": {},
    "square": {
      "shape": "square"
    },
    "orbit": {},
    "ripple": {
      "pattern": "ripple"
    },
    "snake": {
      "pattern": "snake"
    },
    "controlled-elapsed": {
      "elapsed": 36.7
    },
    "internal-timer": {},
    "no-timer": {
      "showTimer": false
    },
    "compact": {
      "cellSize": 3,
      "gap": 2
    },
    "dark": {},
    "reduced-motion": {},
    "multiple-instances": {},
    "status-transition": {}
  }
} as const
