// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const bellContract = {
  "version": 1,
  "id": "belltoggle",
  "publicName": "KfBellToggle",
  "package": "@beiluol/knowflow-ui",
  "description": "通知语义Toggle Button，减幅铃铛、稳定标签、宿主计数及受控订阅意图。",
  "source": "packages/ui/src/components/bell-toggle/KfBellToggle.vue",
  "status": "implemented",
  "props": {
    "modelValue": null,
    "defaultPressed": false,
    "offLabel": "开启通知",
    "onLabel": "已开启通知",
    "ariaLabel": "通知提醒",
    "size": "md",
    "disabled": false,
    "count": 0,
    "badge": true,
    "badgeMax": 9,
    "ringAmplitude": 17,
    "ringPasses": 5,
    "ringDecay": 1,
    "ringDuration": 820,
    "waves": true,
    "clapper": false,
    "crossfadeMs": null,
    "busy": false,
    "error": "",
    "animated": true,
    "ringPivot": 16,
    "revealBounce": 0,
    "radius": 22,
    "color": "",
    "background": "",
    "onColor": "",
    "onBackground": "",
    "badgeColor": "",
    "badgeTextColor": ""
  },
  "propTypes": {
    "modelValue": "boolean",
    "defaultPressed": "boolean",
    "offLabel": "string",
    "onLabel": "string",
    "ariaLabel": "string",
    "size": "string",
    "disabled": "boolean",
    "count": "number",
    "badge": "boolean",
    "badgeMax": "number",
    "ringAmplitude": "number",
    "ringPasses": "number",
    "ringDecay": "number",
    "ringDuration": "number",
    "waves": "boolean",
    "clapper": "boolean",
    "crossfadeMs": "number",
    "busy": "boolean",
    "error": "string",
    "animated": "boolean",
    "ringPivot": "number",
    "revealBounce": "number",
    "radius": "number",
    "color": "string",
    "background": "string",
    "onColor": "string",
    "onBackground": "string",
    "badgeColor": "string",
    "badgeTextColor": "string"
  },
  "emits": [
    "update:modelValue",
    "change",
    "focus",
    "blur"
  ],
  "slots": [
    "icon",
    "badge",
    "off-label",
    "on-label"
  ],
  "stories": [
    "basic",
    "off",
    "on",
    "controlled",
    "uncontrolled",
    "with-badge",
    "without-badge",
    "count-zero",
    "count-three",
    "count-over-nine",
    "count-increase",
    "bell-swing",
    "custom-ring-amplitude",
    "custom-ring-duration",
    "waves-enabled",
    "waves-disabled",
    "clapper-enabled",
    "long-on-label",
    "short-labels",
    "clip-reveal",
    "disabled",
    "busy",
    "async-success",
    "async-error",
    "light-mode",
    "dark-mode",
    "reduced-motion",
    "keyboard",
    "mobile",
    "multiple-instances"
  ],
  "storyProps": {
    "basic": {},
    "off": {},
    "on": {
      "defaultPressed": true
    },
    "controlled": {
      "modelValue": false
    },
    "uncontrolled": {},
    "with-badge": {
      "defaultPressed": true,
      "count": 3
    },
    "without-badge": {
      "defaultPressed": true,
      "count": 3,
      "badge": false
    },
    "count-zero": {},
    "count-three": {
      "defaultPressed": true,
      "count": 3
    },
    "count-over-nine": {
      "defaultPressed": true,
      "count": 120
    },
    "count-increase": {
      "defaultPressed": true,
      "count": 3
    },
    "bell-swing": {},
    "custom-ring-amplitude": {
      "ringAmplitude": 24
    },
    "custom-ring-duration": {
      "ringDuration": 1500
    },
    "waves-enabled": {
      "waves": true,
      "clapper": true
    },
    "waves-disabled": {
      "waves": false
    },
    "clapper-enabled": {
      "clapper": true
    },
    "long-on-label": {
      "onLabel": "已开启此课程的学习提醒与更新通知 · Course notifications"
    },
    "short-labels": {
      "offLabel": "提醒",
      "onLabel": "已开启"
    },
    "clip-reveal": {},
    "disabled": {
      "disabled": true
    },
    "busy": {
      "busy": true
    },
    "async-success": {
      "modelValue": false
    },
    "async-error": {
      "modelValue": false
    },
    "light-mode": {},
    "dark-mode": {},
    "reduced-motion": {
      "animated": false
    },
    "keyboard": {},
    "mobile": {},
    "multiple-instances": {}
  }
} as const
