// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const glideContract = {
  "version": 1,
  "id": "glideselect",
  "publicName": "KfGlideSelect",
  "package": "@beiluol/knowflow-ui",
  "category": "表单 / 选择器",
  "description": "滑动高亮选择器 · Glide Select",
  "props": {
    "modelValue": null,
    "defaultValue": "vue",
    "options": [
      {
        "value": "vue",
        "label": "Vue",
        "tag": "推荐"
      },
      {
        "value": "ts",
        "label": "TypeScript",
        "description": "类型安全与可靠的公共合同"
      },
      {
        "value": "off",
        "label": "不可选项",
        "disabled": true
      },
      {
        "value": 0,
        "label": "Zero"
      }
    ],
    "placeholder": "选择技术",
    "size": "md",
    "disabled": false,
    "readonly": false,
    "placement": "auto",
    "align": "start",
    "menuWidth": 240,
    "maxHeight": 280,
    "rememberHighlight": false,
    "ariaLabel": "滑动高亮选择器",
    "iconName": ""
  },
  "emits": [
    "update:modelValue",
    "change",
    "open-change",
    "select"
  ],
  "slots": [
    "trigger",
    "option",
    "empty",
    "prefix",
    "suffix"
  ],
  "stories": [
    "basic",
    "with-tags",
    "controlled",
    "default-value",
    "placement-top",
    "placement-bottom",
    "remember-highlight",
    "disabled",
    "long-options",
    "keyboard-navigation",
    "pointer-drag",
    "reduced-motion",
    "light-dark",
    "mobile"
  ],
  "status": "implemented",
  "source": "packages/ui/src/components/glide-select/KfGlideSelect.vue",
  "storyProps": {
    "basic": {
      "options": [
        {
          "value": "vue",
          "label": "Vue",
          "disabled": false
        },
        {
          "value": "ts",
          "label": "TypeScript",
          "disabled": false
        },
        {
          "value": "off",
          "label": "不可选项",
          "disabled": true
        },
        {
          "value": 0,
          "label": "Zero",
          "disabled": false
        }
      ]
    },
    "with-tags": {
      "options": [
        {
          "value": "vue",
          "label": "Vue",
          "tag": "推荐"
        },
        {
          "value": "ts",
          "label": "TypeScript",
          "description": "类型安全与可靠的公共合同"
        },
        {
          "value": "off",
          "label": "不可选项",
          "disabled": true
        },
        {
          "value": 0,
          "label": "Zero"
        }
      ]
    },
    "controlled": {
      "modelValue": "ts"
    },
    "default-value": {
      "defaultValue": 0
    },
    "placement-top": {
      "placement": "top"
    },
    "placement-bottom": {
      "placement": "bottom"
    },
    "remember-highlight": {
      "rememberHighlight": true
    },
    "disabled": {
      "disabled": true
    },
    "long-options": {
      "options": [
        {
          "value": 0,
          "label": "资源 0：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 1,
          "label": "资源 1：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 2,
          "label": "资源 2：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 3,
          "label": "资源 3：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 4,
          "label": "资源 4：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 5,
          "label": "资源 5：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 6,
          "label": "资源 6：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 7,
          "label": "资源 7：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 8,
          "label": "资源 8：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 9,
          "label": "资源 9：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 10,
          "label": "资源 10：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 11,
          "label": "资源 11：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 12,
          "label": "资源 12：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 13,
          "label": "资源 13：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 14,
          "label": "资源 14：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 15,
          "label": "资源 15：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 16,
          "label": "资源 16：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 17,
          "label": "资源 17：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 18,
          "label": "资源 18：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 19,
          "label": "资源 19：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 20,
          "label": "资源 20：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 21,
          "label": "资源 21：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 22,
          "label": "资源 22：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 23,
          "label": "资源 23：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 24,
          "label": "资源 24：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 25,
          "label": "资源 25：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 26,
          "label": "资源 26：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 27,
          "label": "资源 27：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 28,
          "label": "资源 28：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        },
        {
          "value": 29,
          "label": "资源 29：长标题与动态高度验证",
          "description": "跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。跨行的说明文字，保持真实行高与滑动高亮同步。"
        }
      ]
    },
    "keyboard-navigation": {},
    "pointer-drag": {},
    "reduced-motion": {},
    "light-dark": {},
    "mobile": {
      "menuWidth": 260
    }
  },
  "propTypes": {
    "modelValue": "string | number",
    "defaultValue": "string | number",
    "options": "KfGlideSelectOption[]",
    "placeholder": "string",
    "size": "'sm' | 'md' | 'lg'",
    "disabled": "boolean",
    "readonly": "boolean",
    "placement": "'auto' | 'top' | 'bottom'",
    "align": "'start' | 'end'",
    "menuWidth": "number | string",
    "maxHeight": "number",
    "rememberHighlight": "boolean",
    "ariaLabel": "string",
    "iconName": "string"
  }
} as const
