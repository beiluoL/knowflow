import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  define: {
    // 开发环境 WebSocket 直连后端端口（绕过 Vite 代理 WS 转发不稳定的问题）
    __WS_BACKEND_PORT__: JSON.stringify(process.env.SERVER__PORT || '8080'),
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.SERVER__PORT || '8080'}`,
        changeOrigin: true,
      },
      '/uploads': {
        target: `http://localhost:${process.env.SERVER__PORT || '8080'}`,
        changeOrigin: true,
      },
      '/ws': {
        target: `http://localhost:${process.env.SERVER__PORT || '8080'}`,
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
