import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/demo-idp': { target: 'http://127.0.0.1:19998', changeOrigin: true, rewrite: (path) => path.replace(/^\/demo-idp/, '') },
    },
  },
})
