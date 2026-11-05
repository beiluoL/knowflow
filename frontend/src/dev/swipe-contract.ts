// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const swipeContract = {
  "version": 1,
  "id": "swiperow",
  "publicName": "KfSwipeRow",
  "package": "@beiluol/knowflow-ui",
  "description": "双向滑动操作列表行，键盘替代入口与安全完整滑动",
  "source": "packages/ui/src/components/swipe-row/KfSwipeRow.vue",
  "status": "implemented",
  "props": {
    "actions": [
      {
        "id": "archive",
        "label": "归档",
        "side": "right",
        "primary": true,
        "fullSwipe": true
      },
      {
        "id": "pin",
        "label": "置顶",
        "side": "left"
      }
    ],
    "open": null,
    "defaultOpen": false,
    "disabled": false,
    "loading": false,
    "fullSwipe": true,
    "commitThreshold": 240,
    "openThreshold": 0.45,
    "velocityThreshold": 0.5,
    "actionWidth": 88,
    "resistance": 0.55,
    "snapDuration": 220,
    "closeOnAction": true,
    "group": "demo",
    "label": "学习条目",
    "direction": "both",
    "commitAt": 0.6,
    "snapBounce": 0.2,
    "height": 64,
    "radius": 16,
    "rowColor": "",
    "drawerColor": "",
    "actionColor": "",
    "textColor": "",
    "haptic": true
  },
  "propTypes": {
    "actions": "SwipeAction[]",
    "open": "false | 'left' | 'right'",
    "defaultOpen": "false | 'left' | 'right'",
    "disabled": "boolean",
    "loading": "boolean",
    "fullSwipe": "boolean",
    "commitThreshold": "number",
    "openThreshold": "number",
    "velocityThreshold": "number",
    "actionWidth": "number",
    "resistance": "number",
    "snapDuration": "number",
    "closeOnAction": "boolean",
    "group": "string",
    "label": "string",
    "direction": "'both' | 'left' | 'right'",
    "commitAt": "number",
    "snapBounce": "number",
    "height": "number",
    "radius": "number",
    "rowColor": "string",
    "drawerColor": "string",
    "actionColor": "string",
    "textColor": "string",
    "haptic": "boolean"
  },
  "emits": [
    "update:open",
    "open-change",
    "action",
    "commit",
    "close"
  ],
  "slots": [
    "default",
    "action"
  ],
  "stories": [
    "basic",
    "delete",
    "multiple-actions",
    "left-swipe",
    "right-swipe",
    "full-swipe",
    "controlled",
    "group",
    "disabled",
    "long-content",
    "custom-content",
    "async-action",
    "reduced-motion",
    "keyboard",
    "mobile",
    "failure-recovery"
  ],
  "storyProps": {
    "basic": {},
    "delete": {
      "actions": [
        {
          "id": "delete",
          "label": "删除",
          "variant": "danger"
        }
      ]
    },
    "multiple-actions": {},
    "left-swipe": {
      "defaultOpen": "right"
    },
    "right-swipe": {
      "defaultOpen": "left"
    },
    "full-swipe": {
      "fullSwipe": true
    },
    "controlled": {
      "open": false
    },
    "group": {},
    "disabled": {
      "disabled": true
    },
    "long-content": {},
    "custom-content": {},
    "async-action": {},
    "reduced-motion": {
      "snapDuration": 0
    },
    "keyboard": {},
    "mobile": {},
    "failure-recovery": {}
  }
} as const
