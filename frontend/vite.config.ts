import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      // 开发环境把 /api 代理到网关，避免浏览器跨域
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
