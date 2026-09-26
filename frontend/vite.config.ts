/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { host: 'localhost', proxy: { '/api': 'http://localhost:8080', '/oauth2': 'http://localhost:8080', '/login': 'http://localhost:8080', '/logout': 'http://localhost:8080' } },
  test: { environment: 'jsdom', setupFiles: './src/test/setup.ts' },
})
