// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const promptContract = {
  "version": 1,
  "id": "promptbar",
  "publicName": "KfPromptBar",
  "package": "@beiluol/knowflow-ai",
  "category": "AI / Input",
  "description": "Prompt Bar",
  "status": "implemented",
  "source": "packages/ai/src/prompt-bar/KfPromptBar.vue",
  "props": {
    "modelValue": null,
    "defaultValue": "",
    "modelId": "demo",
    "effort": "medium",
    "models": [
      {
        "id": "demo",
        "label": "KnowFlow Demo",
        "description": "Fictional model; no network",
        "supportedEfforts": [
          "low",
          "medium",
          "high"
        ]
      },
      {
        "id": "simple",
        "label": "Simple Demo",
        "supportedEfforts": []
      }
    ],
    "efforts": [
      {
        "value": "low",
        "label": "快速"
      },
      {
        "value": "medium",
        "label": "标准"
      },
      {
        "value": "high",
        "label": "深度"
      }
    ],
    "attachments": [],
    "sources": [
      {
        "id": "local",
        "label": "本地文件",
        "description": "Host chooses a file"
      },
      {
        "id": "notes",
        "label": "示例笔记",
        "insertText": "Demo note "
      }
    ],
    "commands": [
      {
        "id": "summarize",
        "label": "总结",
        "insertText": "Please summarize: "
      },
      {
        "id": "explain",
        "label": "解释",
        "insertText": "Please explain: "
      }
    ],
    "placeholder": "向 AI 提问，输入 @ 引用资料...",
    "ariaLabel": "智能提示词输入",
    "maxRows": 6,
    "busy": false,
    "disabled": false,
    "readonly": false,
    "error": "",
    "stoppable": true,
    "showVoice": false,
    "showAttachments": true,
    "allowAttachmentsOnly": false,
    "submitMode": "enter"
  },
  "propTypes": {
    "modelValue": "string",
    "defaultValue": "string",
    "modelId": "string",
    "effort": "string",
    "models": "KfPromptBarModel[]",
    "efforts": "KfPromptEffort[]",
    "attachments": "KfAttachmentItem[]",
    "sources": "KfPromptSource[]",
    "commands": "KfPromptSource[]",
    "placeholder": "string",
    "ariaLabel": "string",
    "maxRows": "number",
    "busy": "boolean",
    "disabled": "boolean",
    "readonly": "boolean",
    "error": "string",
    "stoppable": "boolean",
    "showVoice": "boolean",
    "showAttachments": "boolean",
    "allowAttachmentsOnly": "boolean",
    "submitMode": "'enter' | 'mod-enter'"
  },
  "emits": [
    "update:modelValue",
    "update:modelId",
    "update:effort",
    "send",
    "stop",
    "attach-click",
    "attachment-remove",
    "source-select",
    "command-select",
    "dictate-click",
    "focus",
    "blur"
  ],
  "slots": [
    "toolbar",
    "hint",
    "model",
    "prefix"
  ],
  "stories": [
    "basic",
    "light",
    "dark",
    "effort-picker",
    "model-picker",
    "with-attachments",
    "sources-menu",
    "slash-commands",
    "busy-and-stop",
    "error",
    "long-prompt",
    "disabled",
    "no-models",
    "dictation-available",
    "reduced-motion",
    "mobile",
    "controlled",
    "combined-interactions"
  ],
  "storyProps": {
    "basic": {
      "modelValue": ""
    },
    "light": {},
    "dark": {},
    "effort-picker": {
      "efforts": [
        {
          "value": "e0",
          "label": "1"
        },
        {
          "value": "e1",
          "label": "2"
        },
        {
          "value": "e2",
          "label": "3"
        },
        {
          "value": "e3",
          "label": "4"
        },
        {
          "value": "e4",
          "label": "5"
        }
      ],
      "models": [
        {
          "id": "five",
          "label": "Five-level Demo",
          "supportedEfforts": [
            "e0",
            "e1",
            "e2",
            "e3",
            "e4"
          ]
        }
      ],
      "modelId": "five",
      "effort": "e2"
    },
    "model-picker": {},
    "with-attachments": {
      "attachments": [
        {
          "id": "demo-file",
          "name": "demo-notes.md",
          "status": "ready",
          "size": 2048
        }
      ]
    },
    "sources-menu": {},
    "slash-commands": {
      "modelValue": "/"
    },
    "busy-and-stop": {
      "busy": true,
      "modelValue": "Retained draft"
    },
    "error": {
      "error": "Demo failure; draft retained",
      "modelValue": "Please review this content"
    },
    "long-prompt": {
      "modelValue": "A long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\nA long prompt to verify height limits and internal scrolling.\n",
      "maxRows": 4
    },
    "disabled": {
      "disabled": true
    },
    "no-models": {
      "models": []
    },
    "dictation-available": {
      "showVoice": true
    },
    "reduced-motion": {},
    "mobile": {},
    "controlled": {
      "modelValue": "Controlled draft"
    },
    "combined-interactions": {
      "modelValue": "Please refer to the attachment",
      "attachments": [
        {
          "id": "demo-combined",
          "name": "sample.md",
          "status": "ready"
        }
      ]
    }
  }
} as const
