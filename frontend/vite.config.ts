/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // alpha.blog.test:5173 처럼 블로그 주소로도 연다 (/etc/hosts, quickstart)
    allowedHosts: ['.blog.test'],
    // API와 업로드 이미지는 Spring Boot(8080)로 넘긴다. Host는 그대로 넘겨 서버가 블로그를 찾게 한다
    proxy: {
      '/api': 'http://localhost:8080',
      '/uploads': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'node',
  },
})
