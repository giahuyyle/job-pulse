/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { host: '127.0.0.1', proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' } },
  test: { environment: 'jsdom', setupFiles: './src/test/setup.ts' },
})
