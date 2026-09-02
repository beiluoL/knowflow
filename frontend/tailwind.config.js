/** @type {import('tailwindcss').Config} */
// F-07 说明：颜色令牌的单一来源是 src/style.css :root 的 --kb-* 变量。
//
// Phase UI-1 变更（重要）：
//   原先 primary / success / warning / danger / highlight 是**硬编码 hex 色板**
//   （如 primary-500: '#3B6FE0'），而语义色（card/background/foreground…）走 --kb-* 变量。
//   结果是深色模式下 --kb-primary 变为 #4F86F9，而 primary-500 仍是 #3B6FE0 —— 主色漂移。
//
//   现在：色板每一档都改为引用 --kb-* 令牌。
//   ✅ class 名完全不变（bg-primary-500 / text-danger-500 … 照常使用），无需改动任何页面；
//   ✅ 浅色模式下每一档数值与原色板逐档一致，视觉效果不变；
//   ✅ 深色模式下自动跟随 --kb-* 的深色取值，根治漂移。
//
// 修改主题请直接改 src/style.css（:root 与 [data-theme="dark"] 两处），此处无需再同步。
// U1：darkMode 配置为 ['class', '[data-theme="dark"]']，与 useTheme.ts 的 data-theme 切换一致，
// 使 dark: 变体可正常工作；gray/background/surface 全部改为引用 --kb-* token，深色模式自动跟随。
export default {
  darkMode: ['class', '[data-theme="dark"]'],
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // ===== 语义色（唯一来源：--kb-* 令牌）=====
        card: 'var(--kb-card)',
        'card-foreground': 'var(--kb-card-foreground)',
        popover: 'var(--kb-popover)',
        'popover-foreground': 'var(--kb-popover-foreground)',
        background: 'var(--kb-background)',
        foreground: 'var(--kb-foreground)',
        muted: 'var(--kb-muted)',
        'muted-foreground': 'var(--kb-muted-foreground)',
        border: 'var(--kb-border)',
        input: 'var(--kb-input)',
        ring: 'var(--kb-ring)',
        sidebar: 'var(--kb-sidebar)',
        'sidebar-foreground': 'var(--kb-sidebar-foreground)',
        accent: 'var(--kb-accent)',
        'accent-foreground': 'var(--kb-accent-foreground)',
        destructive: 'var(--kb-destructive)',
        'destructive-foreground': 'var(--kb-destructive-foreground)',
        // 旧别名保留（向后兼容）
        surface: 'var(--kb-card)',
        'text-primary': 'var(--kb-foreground)',
        'text-secondary': 'var(--kb-muted-foreground)',

        // ===== 语义色阶：全部引用 --kb-* 令牌（Phase UI-1）=====
        // 500 = 基准色；600 = hover；700 = active / 深文字；50/100 = 软背景。
        // 浅色档位与原硬编码色板逐档一致，深色档位由 style.css 的深色块提供。
        primary: {
          50: 'var(--kb-primary-subtle)',
          100: 'var(--kb-primary-soft-strong)',
          200: 'var(--kb-primary-tint)',
          300: 'var(--kb-primary-tint-strong)',
          400: 'var(--kb-primary-muted)',
          500: 'var(--kb-primary)',
          600: 'var(--kb-primary-hover)',
          700: 'var(--kb-primary-active)',
          800: 'var(--kb-primary-active)',
          900: 'var(--kb-primary-active)',
        },
        success: {
          50: 'var(--kb-success-subtle)',
          100: 'var(--kb-success-soft-strong)',
          500: 'var(--kb-success)',        // = --kb-accent
          600: 'var(--kb-success-hover)',
          700: 'var(--kb-state-success)',  // 深绿，软绿底上的文字
        },
        warning: {
          50: 'var(--kb-warning-subtle)',
          100: 'var(--kb-warning-soft-strong)',
          500: 'var(--kb-warning)',
          600: 'var(--kb-warning-hover)',
          700: 'var(--kb-state-warning)',  // 深琥珀
        },
        danger: {
          50: 'var(--kb-danger-subtle)',
          100: 'var(--kb-danger-soft-strong)',
          300: 'var(--kb-danger-tint)',
          400: 'var(--kb-danger-muted)',
          500: 'var(--kb-danger)',         // = --kb-destructive
          600: 'var(--kb-danger-hover)',
          700: 'var(--kb-state-error)',   // 深红
        },
        // Signature highlight：高光时刻标识色（成就/打卡火焰/AI 推荐）
        // 与 style.css --kb-highlight* 保持一致，禁止色值漂移
        highlight: {
          50: 'var(--kb-highlight-subtle)',
          100: 'var(--kb-highlight-soft-strong)',
          200: 'var(--kb-highlight-soft-strong)',
          300: 'var(--kb-highlight-soft-strong)',
          400: 'var(--kb-highlight)',
          500: 'var(--kb-highlight)',
          600: 'var(--kb-highlight-hover)',
          700: 'var(--kb-highlight-active)',
          soft: 'var(--kb-highlight-soft)',
        },
        gray: {
          // U1：gray 全梯度改为引用 --kb-* token，深色模式自动跟随；
          // opacity 修饰符（如 bg-gray-100/50）由 Tailwind 用 color-mix 处理，现代浏览器均支持。
          50: 'var(--kb-muted)',
          100: 'var(--kb-muted)',
          200: 'var(--kb-border)',
          300: 'var(--kb-border)',
          400: 'var(--kb-muted-foreground)',
          500: 'var(--kb-muted-foreground)',
          600: 'var(--kb-sidebar-foreground)',
          700: 'var(--kb-foreground)',
          800: 'var(--kb-foreground)',
          900: 'var(--kb-foreground)',
        },
      },
      // Phase UI-1：圆角改为引用 --kb-radius-* 令牌（数值与原来一致：6/10/16）
      borderRadius: {
        'sm': 'var(--kb-radius-sm)',
        'md': 'var(--kb-radius-md)',
        'lg': 'var(--kb-radius-lg)',
      },
      // Phase UI-1：阴影改为引用 --shadow-* 令牌，深色模式自动使用更强阴影
      boxShadow: {
        'sm': 'var(--shadow-sm)',
        'md': 'var(--shadow-md)',
        'lg': 'var(--shadow-lg)',
        'xl': '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
        'card': 'var(--shadow-card)',
        'card-hover': 'var(--shadow-card-hover)',
      },
      fontFamily: {
        sans: [
          '"Noto Sans SC"',
          'system-ui',
          '-apple-system',
          'BlinkMacSystemFont',
          '"Segoe UI"',
          'Roboto',
          '"Helvetica Neue"',
          'Arial',
          'sans-serif',
        ],
        // 展示标题：杂志感衬线，用于 H1/H2/H3（配合 font-serif class 或 kb-h* 工具类）
        serif: [
          '"Noto Serif SC"',
          'Georgia',
          '"Times New Roman"',
          'serif',
        ],
        // Hero 大标题专用：比 serif 更重更紧（900 字重），用于首页 Hero、登录页标题
        display: [
          '"Noto Serif SC"',
          'Georgia',
          '"Times New Roman"',
          'serif',
        ],
        // 代码与数字列：等宽带连字
        mono: [
          '"JetBrains Mono"',
          '"Fira Code"',
          '"SF Mono"',
          'Menlo',
          'Consolas',
          'monospace',
        ],
      },
    },
  },
  plugins: [],
}
