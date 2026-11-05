// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const folderContract = {
  "version": 1,
  "id": "folderfloat",
  "publicName": "KfFolderFloat",
  "package": "@beiluol/knowflow-ui",
  "description": "浮动资源文件夹：3D 前盖、纸张浮出、交错资源卡片；可选有界拖拽惯性（无碰撞）",
  "source": "packages/ui/src/components/folder-float/KfFolderFloat.vue",
  "status": "implemented",
  "props": {
    "items": [
      {
        "value": "0",
        "label": "学习笔记",
        "iconName": "file-text"
      },
      {
        "value": "1",
        "label": "知识卡片",
        "iconName": "file-text"
      },
      {
        "value": "2",
        "label": "复习计划",
        "iconName": "file-text"
      },
      {
        "value": "3",
        "label": "参考资料",
        "iconName": "file-text"
      }
    ],
    "label": "学习资料",
    "sublabel": "打开资源集合",
    "open": null,
    "defaultOpen": false,
    "trigger": "click",
    "closeOnSelect": true,
    "physics": false,
    "drift": 2,
    "width": 240,
    "height": 140,
    "spread": 560,
    "lift": 24,
    "tilt": 5,
    "flapAngle": 24,
    "openDuration": null,
    "stagger": 35,
    "animated": true,
    "disabled": false,
    "maxItems": 12
  },
  "propTypes": {
    "items": "Array<string | KfFolderFloatItem>",
    "label": "string",
    "sublabel": "string",
    "open": "boolean | undefined",
    "defaultOpen": "boolean",
    "trigger": "'hover' | 'click'",
    "closeOnSelect": "boolean",
    "physics": "boolean",
    "drift": "number",
    "width": "number",
    "height": "number",
    "spread": "number",
    "lift": "number",
    "tilt": "number",
    "flapAngle": "number",
    "openDuration": "number | undefined",
    "stagger": "number",
    "animated": "boolean",
    "disabled": "boolean",
    "maxItems": "number"
  },
  "emits": [
    "update:open",
    "open-change",
    "select"
  ],
  "slots": [
    "item",
    "empty"
  ],
  "stories": [
    "basic",
    "hover-open",
    "click-open",
    "default-open",
    "controlled-open",
    "close-on-select",
    "keep-open-on-select",
    "one-item",
    "four-items",
    "eight-items",
    "empty-folder",
    "dynamic-items",
    "long-labels",
    "custom-icons",
    "folder-flap",
    "paper-reveal",
    "stagger-animation",
    "floating-notes",
    "physics-disabled",
    "physics-enabled",
    "drag-and-release",
    "custom-spread",
    "custom-lift",
    "custom-tilt",
    "compact",
    "learning-resources",
    "knowledge-collection",
    "light-mode",
    "dark-mode",
    "reduced-motion",
    "mobile",
    "keyboard-navigation",
    "multiple-instances"
  ],
  "storyProps": {
    "basic": {},
    "hover-open": {
      "trigger": "hover"
    },
    "click-open": {},
    "default-open": {
      "defaultOpen": true
    },
    "controlled-open": {
      "open": false
    },
    "close-on-select": {},
    "keep-open-on-select": {
      "closeOnSelect": false,
      "defaultOpen": true
    },
    "one-item": {
      "items": [
        {
          "value": "0",
          "label": "学习笔记",
          "iconName": "file-text"
        }
      ],
      "defaultOpen": true
    },
    "four-items": {
      "defaultOpen": true
    },
    "eight-items": {
      "items": [
        {
          "value": "0",
          "label": "资源 1"
        },
        {
          "value": "1",
          "label": "资源 2"
        },
        {
          "value": "2",
          "label": "资源 3"
        },
        {
          "value": "3",
          "label": "资源 4"
        },
        {
          "value": "4",
          "label": "资源 5"
        },
        {
          "value": "5",
          "label": "资源 6"
        },
        {
          "value": "6",
          "label": "资源 7"
        },
        {
          "value": "7",
          "label": "资源 8"
        }
      ],
      "defaultOpen": true
    },
    "empty-folder": {
      "items": [],
      "defaultOpen": true
    },
    "dynamic-items": {},
    "long-labels": {
      "items": [
        {
          "value": "long",
          "label": "中英文混合超长资源标题 Long resource name 📚 带图标和说明"
        },
        {
          "value": "disabled",
          "label": "不可用资源",
          "disabled": true
        }
      ],
      "defaultOpen": true
    },
    "custom-icons": {},
    "folder-flap": {
      "defaultOpen": true
    },
    "paper-reveal": {
      "defaultOpen": true
    },
    "stagger-animation": {
      "defaultOpen": true
    },
    "floating-notes": {
      "defaultOpen": true
    },
    "physics-disabled": {
      "physics": false,
      "defaultOpen": true
    },
    "physics-enabled": {
      "physics": true,
      "defaultOpen": true,
      "closeOnSelect": false
    },
    "drag-and-release": {
      "physics": true,
      "defaultOpen": true,
      "closeOnSelect": false
    },
    "custom-spread": {
      "spread": 800,
      "defaultOpen": true
    },
    "custom-lift": {
      "lift": 48,
      "defaultOpen": true
    },
    "custom-tilt": {
      "tilt": 10,
      "defaultOpen": true
    },
    "compact": {
      "width": 160,
      "height": 100,
      "spread": 320
    },
    "learning-resources": {
      "label": "学习路径",
      "defaultOpen": true
    },
    "knowledge-collection": {
      "label": "知识集合",
      "defaultOpen": true
    },
    "light-mode": {
      "defaultOpen": true
    },
    "dark-mode": {
      "defaultOpen": true
    },
    "reduced-motion": {
      "animated": false,
      "defaultOpen": true
    },
    "mobile": {},
    "keyboard-navigation": {
      "defaultOpen": true
    },
    "multiple-instances": {}
  }
} as const
