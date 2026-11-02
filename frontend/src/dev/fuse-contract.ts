// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const fuseContract = {
  "version": 1,
  "id": "fusebutton",
  "publicName": "KfFuseButton",
  "package": "@beiluol/knowflow-ui",
  "description": "引线撤销窗口与宿主异步提交；事件是意图，完成和补偿结果由宿主回调确认。",
  "source": "packages/ui/src/components/fuse-button/KfFuseButton.vue",
  "status": "implemented",
  "props": {
    "label": "归档草稿",
    "undoLabel": "撤销",
    "doneLabel": "已完成",
    "errorLabel": "操作失败，重试",
    "size": "md",
    "disabled": false,
    "undoWindow": 4000,
    "fusePosition": "outline",
    "fuseThickness": 2,
    "commitOn": "fuseEnd",
    "settle": "stay",
    "pauseOnHover": false,
    "pauseOnFocus": false,
    "crossfadeMs": null,
    "animated": true,
    "operationId": "demo-operation",
    "ariaLabel": "",
    "commitAction": null,
    "undoAction": null
  },
  "propTypes": {
    "label": "string",
    "undoLabel": "string",
    "doneLabel": "string",
    "errorLabel": "string",
    "size": "string",
    "disabled": "boolean",
    "undoWindow": "number",
    "fusePosition": "string",
    "fuseThickness": "number",
    "commitOn": "string",
    "settle": "string",
    "pauseOnHover": "boolean",
    "pauseOnFocus": "boolean",
    "crossfadeMs": "number",
    "animated": "boolean",
    "operationId": "string",
    "ariaLabel": "string",
    "commitAction": "KfFuseAction",
    "undoAction": "KfFuseAction"
  },
  "emits": [
    "commit",
    "undo",
    "fuse-end",
    "phase-change"
  ],
  "slots": [
    "default",
    "icon",
    "undo-icon",
    "done-icon"
  ],
  "stories": [
    "basic",
    "archive",
    "delete-draft",
    "send-action",
    "outline-fuse",
    "top-fuse",
    "bottom-fuse",
    "commit-on-press",
    "commit-on-fuse-end",
    "click-undo",
    "escape-undo",
    "hover-pause",
    "focus-pause",
    "reset-after",
    "stay-done",
    "disabled",
    "async-success",
    "async-error",
    "undo-failure",
    "double-click-protection",
    "keyboard-navigation",
    "reduced-motion",
    "dark-mode",
    "mobile",
    "multiple-instances",
    "long-labels",
    "dynamic-props"
  ],
  "storyProps": {
    "basic": {},
    "archive": {
      "label": "归档笔记"
    },
    "delete-draft": {
      "label": "移除草稿"
    },
    "send-action": {
      "label": "发送草稿"
    },
    "outline-fuse": {},
    "top-fuse": {
      "fusePosition": "top"
    },
    "bottom-fuse": {
      "fusePosition": "bottom"
    },
    "commit-on-press": {
      "commitOn": "press"
    },
    "commit-on-fuse-end": {},
    "click-undo": {},
    "escape-undo": {},
    "hover-pause": {
      "pauseOnHover": true
    },
    "focus-pause": {
      "pauseOnFocus": true
    },
    "reset-after": {
      "settle": "reset"
    },
    "stay-done": {},
    "disabled": {
      "disabled": true
    },
    "async-success": {},
    "async-error": {},
    "undo-failure": {
      "commitOn": "press"
    },
    "double-click-protection": {},
    "keyboard-navigation": {},
    "reduced-motion": {
      "animated": false
    },
    "dark-mode": {},
    "mobile": {},
    "multiple-instances": {},
    "long-labels": {
      "label": "归档这个具有较长中文及 English 标题的本地草稿",
      "undoLabel": "撤销本次归档操作",
      "doneLabel": "宿主已确认归档完成"
    },
    "dynamic-props": {}
  }
} as const
