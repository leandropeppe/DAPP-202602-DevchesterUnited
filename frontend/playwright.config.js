import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'

const systemChrome = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const browserLaunchOptions = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
  : (process.platform === 'win32' && existsSync(systemChrome) ? { executablePath: systemChrome } : {})

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.mjs',
  globalTeardown: './e2e/global-teardown.mjs',
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['json', { outputFile: 'e2e/results/sc010.json' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    launchOptions: browserLaunchOptions,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off'
  },
  webServer: {
    command: process.platform === 'win32'
      ? 'npm.cmd run preview -- --host 127.0.0.1 --port 4173'
      : 'npm run preview -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173/login',
    reuseExistingServer: false,
    timeout: 60_000,
    env: {
      DEV_API_TARGET: 'http://127.0.0.1:18080'
    }
  }
})
