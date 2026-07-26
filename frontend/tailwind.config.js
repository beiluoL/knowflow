/** @type {import('tailwindcss').Config} */
// F-07 说明：颜色令牌的单一来源是 src/style.css :root 的 --kb-* 变量。
// 本文件的 primary(#3B6FE0)/success/warning/danger 色板必须与其保持一致；
// 修改主题请先改 style.css 再同步此处，防止两套令牌漂移。
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#EFF4FE',
          100: '#DBE6FC',
          200: '#B7CDF9',
          300: '#93B3F5',
          400: '#6F9AF2',
          500: '#3B6FE0',
          600: '#2F59B3',
          700: '#234386',
          800: '#182D5A',
          900: '#0C182D',
        },
        success: {
          50: '#ECFDF5',
          100: '#D1FAE5',
          500: '#10B981',
          600: '#059669',
          700: '#047857',
        },
        warning: {
          50: '#FFFBEB',
          100: '#FEF3C7',
          500: '#F59E0B',
          600: '#D97706',
          700: '#B45309',
        },
        danger: {
          50: '#FEF2F2',
          100: '#FEE2E2',
          500: '#EF4444',
          600: '#DC2626',
          700: '#B91C1C',
        },
        gray: {
          50: '#F9FAFB',
          100: '#F3F4F6',
          200: '#E5E7EB',
          300: '#D1D5DB',
          400: '#9CA3AF',
          500: '#6B7280',
          600: '#4B5563',
          700: '#374151',
          800: '#1F2937',
          900: '#111827',
        },
        background: '#F7F8FA',
        surface: '#FFFFFF',
        'text-primary': '#1A1D23',
        'text-secondary': '#6B7280',
      },
      borderRadius: {
        'sm': '6px',
        'md': '10px',
        'lg': '16px',
      },
      boxShadow: {
        'sm': '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
        'md': '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
        'lg': '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
        'xl': '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
        'card': '0 2px 8px 0 rgba(0, 0, 0, 0.04)',
        'card-hover': '0 8px 24px 0 rgba(0, 0, 0, 0.08)',
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
      },
    },
  },
  plugins: [],
}
