import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.DEV_API_TARGET || process.env.DEV_API_TARGET || 'http://localhost:8080'
  const apiProxy = {
    '/api/v1': {
      target: apiTarget,
      changeOrigin: false
    }
  }

  return {
    appType: 'spa',
    plugins: [react()],
    server: {
      proxy: apiProxy
    },
    preview: {
      proxy: apiProxy
    },
    test: {
      environment: 'jsdom',
      setupFiles: './tests/setup.js',
      clearMocks: true,
      exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        reportsDirectory: './coverage',
        include: ['src/**/*.{js,jsx}'],
        exclude: [
          '**/tests/**',
          '**/e2e/**',
          '**/coverage/**',
          '**/dist/**',
          '**/node_modules/**',
          '**/*.config.{js,mjs,cjs}'
        ]
      }
    }
  }
})
