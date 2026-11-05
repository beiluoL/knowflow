// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const springContract = {
  "version": 1,
  "id": "springcheck",
  "publicName": "KfSpringCheck",
  "package": "@beiluol/knowflow-ui",
  "description": "弹簧填充、SVG绘制勾号与多行删除线复选框",
  "source": "packages/ui/src/components/spring-check/KfSpringCheck.vue",
  "status": "implemented",
  "props": {
    "modelValue": null,
    "defaultChecked": false,
    "label": "完成今天的学习",
    "description": "",
    "disabled": false,
    "readonly": false,
    "animation": true,
    "duration": 360,
    "color": "",
    "fillColor": "",
    "checkColor": "",
    "boxSize": 20,
    "boxRadius": 3,
    "fontSize": 0,
    "bounce": 0.2,
    "strikeLag": 0.12,
    "doneOpacity": 0.42,
    "strike": "left",
    "ariaLabel": ""
  },
  "propTypes": {
    "modelValue": "boolean",
    "defaultChecked": "boolean",
    "label": "string",
    "description": "string",
    "disabled": "boolean",
    "readonly": "boolean",
    "animation": "boolean",
    "duration": "number",
    "color": "string",
    "fillColor": "string",
    "checkColor": "string",
    "boxSize": "number",
    "boxRadius": "number",
    "fontSize": "number",
    "bounce": "number",
    "strikeLag": "number",
    "doneOpacity": "number",
    "strike": "'left' | 'center' | 'right' | 'none'",
    "ariaLabel": "string"
  },
  "emits": [
    "update:modelValue",
    "change",
    "native-change"
  ],
  "slots": [
    "default"
  ],
  "stories": [
    "basic",
    "checked",
    "unchecked",
    "animation",
    "disabled",
    "long-label",
    "description",
    "multiple-items",
    "form",
    "controlled",
    "rapid-toggle",
    "reduced-motion",
    "dark-mode",
    "mobile",
    "keyboard"
  ],
  "storyProps": {
    "checked": {
      "defaultChecked": true
    },
    "disabled": {
      "disabled": true
    },
    "long-label": {
      "label": "完成今天的学习计划并整理中文与 English 长文本笔记，检查多行文字删除线和自然换行。完成今天的学习计划并整理中文与 English 长文本笔记，检查多行文字删除线和自然换行。完成今天的学习计划并整理中文与 English 长文本笔记，检查多行文字删除线和自然换行。"
    },
    "description": {
      "description": "保存学习进度前请确认内容"
    },
    "reduced-motion": {
      "animation": false
    },
    "controlled": {
      "modelValue": false
    },
    "basic": {},
    "unchecked": {},
    "animation": {},
    "multiple-items": {},
    "form": {},
    "rapid-toggle": {},
    "dark-mode": {},
    "mobile": {},
    "keyboard": {}
  }
} as const
