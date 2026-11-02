// Generated from knowflow-ui/component-manifest.json; run node scripts/check-dual-catalog.mjs
export const lineContract = {
  "version": 1,
  "id": "linesidebar",
  "publicName": "KfLineSidebar",
  "package": "@beiluol/knowflow-ui",
  "description": "Proximity-aware single-level navigation with cached geometry and accessible native controls.",
  "source": "packages/ui/src/components/line-sidebar/KfLineSidebar.vue",
  "status": "implemented",
  "props": {
    "items": [
      {
        "key": "overview",
        "label": "Overview",
        "iconName": "home"
      },
      {
        "key": "notes",
        "label": "Notes",
        "description": "Your knowledge workspace"
      },
      {
        "key": "learning",
        "label": "Learning"
      },
      {
        "key": "practice",
        "label": "Practice"
      },
      {
        "key": "locked",
        "label": "Coming soon",
        "disabled": true
      }
    ],
    "modelValue": null,
    "defaultValue": "overview",
    "showIndex": true,
    "showMarker": true,
    "proximityRadius": 100,
    "maxShift": 30,
    "falloff": "smooth",
    "markerLength": 60,
    "markerGap": 0,
    "tickScale": 0.5,
    "scaleTick": true,
    "smoothing": 100,
    "animated": true,
    "size": "md",
    "ariaLabel": "Line navigation",
    "itemGap": 20,
    "fontSize": 0,
    "accentColor": "",
    "textColor": "",
    "markerColor": ""
  },
  "propTypes": {
    "items": "KfLineSidebarItem[]",
    "modelValue": "string",
    "defaultValue": "string",
    "showIndex": "boolean",
    "showMarker": "boolean",
    "proximityRadius": "number",
    "maxShift": "number",
    "falloff": "string",
    "markerLength": "number",
    "markerGap": "number",
    "tickScale": "number",
    "scaleTick": "boolean",
    "smoothing": "number",
    "animated": "boolean",
    "size": "string",
    "ariaLabel": "string",
    "accentColor": "string",
    "textColor": "string",
    "markerColor": "string",
    "itemGap": "number",
    "fontSize": "number"
  },
  "emits": [
    "update:modelValue",
    "select"
  ],
  "slots": [
    "item",
    "item-icon",
    "item-suffix",
    "empty"
  ],
  "stories": [
    "basic",
    "proximity-effect",
    "linear-falloff",
    "smooth-falloff",
    "sharp-falloff",
    "show-index",
    "hide-index",
    "show-marker",
    "hide-marker",
    "scale-ticks",
    "active-item",
    "controlled",
    "disabled-items",
    "with-icons",
    "long-labels",
    "compact",
    "documentation-sidebar",
    "learning-navigation",
    "dark-mode",
    "reduced-motion",
    "touch-fallback",
    "dynamic-items"
  ],
  "storyProps": {
    "basic": {},
    "proximity-effect": {
      "maxShift": 24,
      "proximityRadius": 140
    },
    "linear-falloff": {
      "falloff": "linear"
    },
    "smooth-falloff": {
      "falloff": "smooth"
    },
    "sharp-falloff": {
      "falloff": "sharp"
    },
    "show-index": {},
    "hide-index": {
      "showIndex": false
    },
    "show-marker": {},
    "hide-marker": {
      "showMarker": false
    },
    "scale-ticks": {
      "tickScale": 3
    },
    "active-item": {
      "defaultValue": "notes"
    },
    "controlled": {
      "modelValue": "learning"
    },
    "disabled-items": {},
    "with-icons": {},
    "long-labels": {
      "items": [
        {
          "key": "long",
          "label": "A long navigation title that wraps naturally without overlapping neighboring markers and indices. A long navigation title that wraps naturally without overlapping neighboring markers and indices. A long navigation title that wraps naturally without overlapping neighboring markers and indices. "
        },
        {
          "key": "next",
          "label": "Next"
        }
      ]
    },
    "compact": {
      "size": "sm"
    },
    "documentation-sidebar": {
      "items": [
        {
          "key": "0",
          "label": "Introduction",
          "href": "#introduction"
        },
        {
          "key": "1",
          "label": "Installation",
          "href": "#installation"
        },
        {
          "key": "2",
          "label": "Components",
          "href": "#components"
        },
        {
          "key": "3",
          "label": "Accessibility",
          "href": "#accessibility"
        }
      ]
    },
    "learning-navigation": {
      "items": [
        {
          "key": "0",
          "label": "Knowledge"
        },
        {
          "key": "1",
          "label": "Review"
        },
        {
          "key": "2",
          "label": "Practice"
        },
        {
          "key": "3",
          "label": "Progress"
        }
      ]
    },
    "dark-mode": {},
    "reduced-motion": {
      "animated": false
    },
    "touch-fallback": {},
    "dynamic-items": {}
  }
} as const
