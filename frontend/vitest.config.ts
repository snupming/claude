import { defineConfig } from 'vitest/config'
import { resolve } from 'path'

export default defineConfig({
  test: {
    environment: 'happy-dom',
    globals: true,
    setupFiles: ['./app/composables/__tests__/setup.ts'],
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './app'),
      '~': resolve(__dirname, './app'),
    },
  },
})
