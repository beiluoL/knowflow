// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const textContract = {
  "version": 1,
  "id": "texttype",
  "publicName": "KfTextType",
  "package": "@beiluol/knowflow-ui",
  "description": "逐字打字动画 · Text Type",
  "source": "packages/ui/src/components/text-type/KfTextType.vue",
  "status": "implemented",
  "props": {
    "text": "让学习更高效 👨‍👩‍👧‍👦",
    "typingSpeed": 50,
    "deletingSpeed": 30,
    "initialDelay": 0,
    "pauseDuration": 1000,
    "loop": false,
    "playing": true,
    "showCursor": true,
    "cursorCharacter": "|",
    "cursorBlinkDuration": 700,
    "hideCursorWhileTyping": false,
    "variableSpeed": null,
    "startOnVisible": false,
    "reverseMode": false,
    "resetKey": null
  },
  "emits": [
    "start",
    "sentence-complete",
    "cycle-complete",
    "complete",
    "pause",
    "resume"
  ],
  "slots": [
    "cursor"
  ],
  "propTypes": {
    "text": "string | string[]",
    "typingSpeed": "number",
    "deletingSpeed": "number",
    "initialDelay": "number",
    "pauseDuration": "number",
    "loop": "boolean",
    "playing": "boolean",
    "showCursor": "boolean",
    "cursorCharacter": "string",
    "cursorBlinkDuration": "number",
    "hideCursorWhileTyping": "boolean",
    "variableSpeed": "KfTextTypeVariableSpeed",
    "startOnVisible": "boolean",
    "reverseMode": "boolean",
    "resetKey": "string | number"
  },
  "stories": [
    "basic",
    "chinese-typing",
    "english-typing",
    "emoji-typing",
    "multiple-texts",
    "loop",
    "no-loop",
    "delete-animation",
    "cursor-blink",
    "custom-cursor",
    "hide-cursor-while-typing",
    "variable-speed",
    "initial-delay",
    "start-on-visible",
    "pause-resume",
    "reset",
    "reduced-motion",
    "dark-mode",
    "long-text",
    "dynamic-text"
  ],
  "storyProps": {
    "basic": {},
    "chinese-typing": {},
    "english-typing": {
      "text": "Java + AI"
    },
    "emoji-typing": {
      "text": "🚀 👨‍👩‍👧‍👦 é"
    },
    "multiple-texts": {
      "text": [
        "让知识更清晰",
        "让学习更高效"
      ]
    },
    "loop": {
      "loop": true,
      "text": [
        "你好",
        "KnowFlow"
      ]
    },
    "no-loop": {},
    "delete-animation": {
      "text": [
        "中文 👨‍👩‍👧‍👦",
        "English 🚀"
      ],
      "pauseDuration": 300
    },
    "cursor-blink": {},
    "custom-cursor": {
      "cursorCharacter": "▌"
    },
    "hide-cursor-while-typing": {
      "hideCursorWhileTyping": true
    },
    "variable-speed": {
      "variableSpeed": {
        "min": 40,
        "max": 110
      }
    },
    "initial-delay": {
      "initialDelay": 1500
    },
    "start-on-visible": {
      "startOnVisible": true
    },
    "pause-resume": {},
    "reset": {},
    "reduced-motion": {},
    "dark-mode": {},
    "long-text": {
      "text": "长文本与换行\nKnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。KnowFlow — 中文与 Emoji 🚀 混合排版。"
    },
    "dynamic-text": {}
  }
} as const
