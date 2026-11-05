// 由 knowflow-ui/component-manifest.json 生成；运行 node scripts/check-dual-catalog.mjs 校验。
export const branchContract = {
  "version": 1,
  "id": "branchedmenu",
  "publicName": "KfBranchedMenu",
  "package": "@beiluol/knowflow-ui",
  "description": "Branched Menu",
  "source": "packages/ui/src/components/branched-menu/KfBranchedMenu.vue",
  "status": "implemented",
  "props": {
    "items": [
      {
        "key": "workspace",
        "label": "Workspace",
        "iconName": "folder",
        "children": [
          {
            "key": "overview",
            "label": "Overview",
            "iconName": "home"
          },
          {
            "key": "notes",
            "label": "Knowledge and notes",
            "description": "Organize and connect resources"
          },
          {
            "key": "disabled",
            "label": "Unavailable",
            "disabled": true
          }
        ]
      },
      {
        "key": "learning",
        "label": "Learning",
        "children": [
          {
            "key": "tasks",
            "label": "Tasks"
          },
          {
            "key": "reports",
            "label": "Reports",
            "href": "#demo-report"
          }
        ]
      }
    ],
    "modelValue": null,
    "defaultValue": "overview",
    "openKeys": null,
    "defaultOpenKeys": [
      "workspace",
      "learning"
    ],
    "multiple": true,
    "animated": true,
    "drawDuration": 280,
    "foldDuration": 220,
    "size": "md",
    "ariaLabel": "Branched navigation"
  },
  "emits": [
    "update:modelValue",
    "update:openKeys",
    "select",
    "toggle"
  ],
  "slots": [
    "section-label",
    "item-label",
    "item-icon",
    "item-suffix",
    "empty"
  ],
  "propTypes": {
    "items": "KfBranchedMenuSection[]",
    "modelValue": "string",
    "defaultValue": "string",
    "openKeys": "string[]",
    "defaultOpenKeys": "string[]",
    "multiple": "boolean",
    "animated": "boolean",
    "drawDuration": "number",
    "foldDuration": "number",
    "size": "string",
    "ariaLabel": "string"
  },
  "stories": [
    "basic",
    "single-open",
    "multiple-open",
    "default-active",
    "controlled-active",
    "controlled-open",
    "with-icons",
    "long-labels",
    "disabled-items",
    "empty-sections",
    "dynamic-items",
    "active-path-animation",
    "active-marker",
    "custom-slots",
    "compact",
    "sidebar-navigation",
    "learning-path",
    "dark",
    "reduced-motion",
    "keyboard-navigation"
  ],
  "storyProps": {
    "basic": {},
    "single-open": {
      "multiple": false
    },
    "multiple-open": {},
    "default-active": {},
    "controlled-active": {
      "modelValue": "notes"
    },
    "controlled-open": {
      "openKeys": [
        "learning"
      ]
    },
    "with-icons": {},
    "long-labels": {
      "items": [
        {
          "key": "long",
          "label": "Long title",
          "children": [
            {
              "key": "l1",
              "label": "Long navigation label with multiple lines and real geometry. Long navigation label with multiple lines and real geometry. Long navigation label with multiple lines and real geometry. Long navigation label with multiple lines and real geometry. Long navigation label with multiple lines and real geometry. "
            },
            {
              "key": "l2",
              "label": "Short"
            }
          ]
        }
      ],
      "defaultOpenKeys": [
        "long"
      ],
      "defaultValue": "l1"
    },
    "disabled-items": {},
    "empty-sections": {
      "items": [
        {
          "key": "empty",
          "label": "Empty group",
          "children": []
        }
      ]
    },
    "dynamic-items": {},
    "active-path-animation": {},
    "active-marker": {},
    "custom-slots": {},
    "compact": {
      "size": "sm"
    },
    "sidebar-navigation": {},
    "learning-path": {},
    "dark": {},
    "reduced-motion": {},
    "keyboard-navigation": {}
  }
} as const
