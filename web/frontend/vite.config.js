import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./test/setup.js']
  },
  server: {
    port: 5174,
    proxy: {
      '/api': 'http://127.0.0.1:8088'
    }
  }
})
