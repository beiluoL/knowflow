// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const holdContract = {
  "version": 1,
  "id": "holdbutton",
  "publicName": "KfHoldButton",
  "package": "@beiluol/knowflow-ui",
  "description": "持续按压确认意图；真实业务结果由宿主提供，支持键盘、触屏与取消。",
  "source": "packages/ui/src/components/hold-button/KfHoldButton.vue",
  "status": "implemented",
  "props": {
    "label": "长按确认",
    "doneLabel": "已完成",
    "holdTime": 2000,
    "releaseTime": 200,
    "fillDirection": "right",
    "size": "md",
    "disabled": false,
    "operationState": "idle",
    "operationId": "local-hold",
    "result": null,
    "pressScale": 0.99,
    "wave": false,
    "waveAmplitude": 3,
    "glow": false,
    "resetAfter": 0,
    "animated": true,
    "ariaLabel": "",
    "iconName": ""
  },
  "propTypes": {
    "label": "string",
    "doneLabel": "string",
    "holdTime": "number",
    "releaseTime": "number",
    "fillDirection": "string",
    "size": "string",
    "disabled": "boolean",
    "operationState": "string",
    "operationId": "string",
    "result": "KfHoldResult",
    "pressScale": "number",
    "wave": "boolean",
    "waveAmplitude": "number",
    "glow": "boolean",
    "resetAfter": "number",
    "animated": "boolean",
    "ariaLabel": "string",
    "iconName": "string"
  },
  "emits": [
    "hold-start",
    "hold-cancel",
    "confirm",
    "phase-change"
  ],
  "slots": [
    "default",
    "icon",
    "done-icon",
    "progress-label",
    "error"
  ],
  "stories": [
    "basic",
    "hold2-seconds",
    "hold1-second",
    "hold3-seconds",
    "fill-right",
    "fill-up",
    "early-release",
    "pointer-drag-cancel",
    "touch-hold",
    "keyboard-hold",
    "keyboard-cancel",
    "with-icon",
    "without-icon",
    "wave-enabled",
    "wave-disabled",
    "custom-wave-amplitude",
    "press-scale",
    "glow",
    "disabled",
    "async-success",
    "async-error",
    "loading-state",
    "reset-after",
    "multiple-instances",
    "dark-mode",
    "reduced-motion",
    "long-labels",
    "mobile",
    "accessibility-fallback",
    "real-hold-lifecycle"
  ],
  "storyProps": {
    "basic": {},
    "hold2-seconds": {},
    "hold1-second": {
      "holdTime": 1000
    },
    "hold3-seconds": {
      "holdTime": 3000
    },
    "fill-right": {},
    "fill-up": {
      "fillDirection": "up"
    },
    "early-release": {},
    "pointer-drag-cancel": {},
    "touch-hold": {},
    "keyboard-hold": {},
    "keyboard-cancel": {},
    "with-icon": {
      "iconName": "archive"
    },
    "without-icon": {},
    "wave-enabled": {
      "wave": true
    },
    "wave-disabled": {},
    "custom-wave-amplitude": {
      "wave": true,
      "waveAmplitude": 6
    },
    "press-scale": {
      "pressScale": 0.95
    },
    "glow": {
      "glow": true
    },
    "disabled": {
      "disabled": true
    },
    "async-success": {},
    "async-error": {},
    "loading-state": {
      "operationState": "pending"
    },
    "reset-after": {
      "resetAfter": 1500
    },
    "multiple-instances": {},
    "dark-mode": {},
    "reduced-motion": {
      "animated": false
    },
    "long-labels": {
      "label": "长按确认本地演示草稿 · A long document title for clipping validation"
    },
    "mobile": {},
    "accessibility-fallback": {},
    "real-hold-lifecycle": {}
  }
} as const
