import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3001,
    host: '0.0.0.0',
    allowedHosts: ['k36a37d4.natappfree.cc', '.natappfree.cc'],
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true
      }
    }
  }
})
