import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 백엔드 API는 Spring Boot(8080)로 넘긴다
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
