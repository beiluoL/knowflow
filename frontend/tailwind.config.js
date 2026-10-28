/** @type {import('tailwindcss').Config} */
// F-07 说明：颜色令牌的单一来源是 src/style.css :root 的 --kf-* 变量。
//
// Phase UI-1 变更（重要）：
//   原先 primary / success / warning / danger / highlight 是**硬编码 hex 色板**
//   （如 primary-500: '#3B6FE0'），而语义色（card/background/foreground…）走 --kf-* 变量。
//   结果是深色模式下 --kf-primary 变为 #4F86F9，而 primary-500 仍是 #3B6FE0 —— 主色漂移。
//
//   现在：色板每一档都改为引用 --kf-* 令牌。
//   ✅ class 名完全不变（bg-primary-500 / text-danger-500 … 照常使用），无需改动任何页面；
//   ✅ 浅色模式下每一档数值与原色板逐档一致，视觉效果不变；
//   ✅ 深色模式下自动跟随 --kf-* 的深色取值，根治漂移。
//
// 修改主题请直接改 src/style.css（:root 与 [data-theme="dark"] 两处），此处无需再同步。
// U1：darkMode 配置为 ['class', '[data-theme="dark"]']，与 useTheme.ts 的 data-theme 切换一致，
// 使 dark: 变体可正常工作；gray/background/surface 全部改为引用 --kf-* token，深色模式自动跟随。
export default {
  darkMode: ['class', '[data-theme="dark"]'],
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // ===== 语义色（唯一来源：--kf-* 令牌）=====
        card: 'var(--kf-card)',
        'card-foreground': 'var(--kf-card-foreground)',
        popover: 'var(--kf-popover)',
        'popover-foreground': 'var(--kf-popover-foreground)',
        background: 'var(--kf-background)',
        foreground: 'var(--kf-foreground)',
        muted: 'var(--kf-muted)',
        'muted-foreground': 'var(--kf-muted-foreground)',
        border: 'var(--kf-border)',
        input: 'var(--kf-input)',
        ring: 'var(--kf-ring)',
        sidebar: 'var(--kf-sidebar)',
        'sidebar-foreground': 'var(--kf-sidebar-foreground)',
        accent: 'var(--kf-accent)',
        'accent-foreground': 'var(--kf-accent-foreground)',
        destructive: 'var(--kf-destructive)',
        'destructive-foreground': 'var(--kf-destructive-foreground)',
        // 旧别名保留（向后兼容）
        surface: 'var(--kf-card)',
        'text-primary': 'var(--kf-foreground)',
        'text-secondary': 'var(--kf-muted-foreground)',

        // ===== 语义色阶：全部引用 --kf-* 令牌（Phase UI-1）=====
        // 500 = 基准色；600 = hover；700 = active / 深文字；50/100 = 软背景。
        // 浅色档位与原硬编码色板逐档一致，深色档位由 style.css 的深色块提供。
        primary: {
          50: 'var(--kf-primary-subtle)',
          100: 'var(--kf-primary-soft-strong)',
          200: 'var(--kf-primary-tint)',
          300: 'var(--kf-primary-tint-strong)',
          400: 'var(--kf-primary-muted)',
          500: 'var(--kf-primary)',
          600: 'var(--kf-primary-hover)',
          700: 'var(--kf-primary-active)',
          800: 'var(--kf-primary-active)',
          900: 'var(--kf-primary-active)',
        },
        success: {
          50: 'var(--kf-success-subtle)',
          100: 'var(--kf-success-soft-strong)',
          500: 'var(--kf-success)',        // = --kf-accent
          600: 'var(--kf-success-hover)',
          700: 'var(--kf-state-success)',  // 深绿，软绿底上的文字
        },
        warning: {
          50: 'var(--kf-warning-subtle)',
          100: 'var(--kf-warning-soft-strong)',
          500: 'var(--kf-warning)',
          600: 'var(--kf-warning-hover)',
          700: 'var(--kf-state-warning)',  // 深琥珀
        },
        danger: {
          50: 'var(--kf-danger-subtle)',
          100: 'var(--kf-danger-soft-strong)',
          300: 'var(--kf-danger-tint)',
          400: 'var(--kf-danger-muted)',
          500: 'var(--kf-danger)',         // = --kf-destructive
          600: 'var(--kf-danger-hover)',
          700: 'var(--kf-state-error)',   // 深红
        },
        // Signature highlight：高光时刻标识色（成就/打卡火焰/AI 推荐）
        // 与 style.css --kf-highlight* 保持一致，禁止色值漂移
        highlight: {
          50: 'var(--kf-highlight-subtle)',
          100: 'var(--kf-highlight-soft-strong)',
          200: 'var(--kf-highlight-soft-strong)',
          300: 'var(--kf-highlight-soft-strong)',
          400: 'var(--kf-highlight)',
          500: 'var(--kf-highlight)',
          600: 'var(--kf-highlight-hover)',
          700: 'var(--kf-highlight-active)',
          soft: 'var(--kf-highlight-soft)',
        },
        gray: {
          // U1：gray 全梯度改为引用 --kf-* token，深色模式自动跟随；
          // opacity 修饰符（如 bg-gray-100/50）由 Tailwind 用 color-mix 处理，现代浏览器均支持。
          50: 'var(--kf-muted)',
          100: 'var(--kf-muted)',
          200: 'var(--kf-border)',
          300: 'var(--kf-border)',
          400: 'var(--kf-muted-foreground)',
          500: 'var(--kf-muted-foreground)',
          600: 'var(--kf-sidebar-foreground)',
          700: 'var(--kf-foreground)',
          800: 'var(--kf-foreground)',
          900: 'var(--kf-foreground)',
        },
      },
      // Phase UI-1：圆角改为引用 --kf-radius-* 令牌（数值与原来一致：6/10/16）
      borderRadius: {
        'sm': 'var(--kf-radius-sm)',
        'md': 'var(--kf-radius-md)',
        'lg': 'var(--kf-radius-lg)',
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
      fontFamily: { sans:'var(--font-sans)',serif:'var(--font-serif)',display:'var(--font-display)',mono:'var(--font-mono)' },
      fontSize: {
        xs:['var(--kf-fs-caption)',{lineHeight:'var(--kf-lh-caption)'}],
        sm:['var(--kf-fs-body-sm)',{lineHeight:'var(--kf-lh-body-sm)'}],
        base:['var(--kf-fs-body-md)',{lineHeight:'var(--kf-lh-body-md)'}],
        lg:['var(--kf-fs-h4)',{lineHeight:'var(--kf-lh-h4)'}],
        xl:['var(--kf-fs-h3)',{lineHeight:'var(--kf-lh-h3)'}],
        '2xl':['var(--kf-fs-h2)',{lineHeight:'var(--kf-lh-h2)'}],
        '3xl':['var(--kf-fs-h1)',{lineHeight:'var(--kf-lh-h1)'}],
        '4xl':['var(--kf-fs-display)',{lineHeight:'var(--kf-lh-display)'}],
      },
    },
  },
  plugins: [],
}
