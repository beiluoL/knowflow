// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const shredContract = {
  "version": 1,
  "id": "shredder",
  "publicName": "KfShredder",
  "package": "@beiluol/knowflow-ui",
  "description": "可排序文本纸张粉碎机，受控数据事件与安全演示",
  "source": "packages/ui/src/components/shredder/KfShredder.vue",
  "status": "implemented",
  "props": {
    "items": [
      {
        "id": "one",
        "label": "课程笔记",
        "description": "整理好的学习资料"
      },
      {
        "id": "two",
        "label": "复习清单"
      }
    ],
    "disabled": false,
    "stripWidth": 10,
    "curl": 18,
    "feedSpeed": 180,
    "bite": 18,
    "autoFeed": true,
    "dragTilt": 6,
    "fallHeight": 140,
    "autoAnimate": false,
    "loop": false,
    "loopAfterDelete": false,
    "label": "纸张粉碎机",
    "width": 340,
    "height": 460,
    "inset": 14,
    "gap": 10,
    "slitHeight": 4,
    "lift": 1.02,
    "slitColor": "",
    "color": ""
  },
  "propTypes": {
    "items": "ShredderItem[]",
    "disabled": "boolean",
    "stripWidth": "number",
    "curl": "number",
    "feedSpeed": "number",
    "bite": "number",
    "autoFeed": "boolean",
    "dragTilt": "number",
    "fallHeight": "number",
    "autoAnimate": "boolean",
    "loop": "boolean",
    "loopAfterDelete": "boolean",
    "label": "string",
    "width": "number",
    "height": "number",
    "inset": "number",
    "gap": "number",
    "slitHeight": "number",
    "lift": "number",
    "slitColor": "string",
    "color": "string"
  },
  "emits": [
    "shred",
    "reorder",
    "cancel",
    "demo-complete"
  ],
  "slots": [
    "item"
  ],
  "stories": [
    "basic",
    "file-list",
    "note-list",
    "drag-to-shred",
    "auto-feed",
    "manual-feed",
    "reorder",
    "strip-variants",
    "curl-variants",
    "feed-speed",
    "loop",
    "auto-animate",
    "disabled",
    "long-content",
    "custom-content",
    "keyboard",
    "reduced-motion",
    "mobile",
    "empty-state",
    "rapid-interaction"
  ],
  "storyProps": {
    "basic": {},
    "file-list": {},
    "note-list": {},
    "drag-to-shred": {},
    "auto-feed": {
      "autoFeed": true
    },
    "manual-feed": {
      "autoFeed": false
    },
    "reorder": {},
    "strip-variants": {
      "stripWidth": 8
    },
    "curl-variants": {
      "curl": 35
    },
    "feed-speed": {
      "feedSpeed": 1700
    },
    "loop": {
      "autoAnimate": true,
      "loop": true
    },
    "auto-animate": {
      "autoAnimate": true
    },
    "disabled": {
      "disabled": true
    },
    "long-content": {
      "items": [
        {
          "id": "long",
          "label": "学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习中文长文本与 English 学习"
        }
      ]
    },
    "custom-content": {},
    "keyboard": {},
    "reduced-motion": {},
    "mobile": {},
    "empty-state": {
      "items": []
    },
    "rapid-interaction": {}
  }
} as const
