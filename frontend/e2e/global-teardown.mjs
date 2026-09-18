import { execFile } from 'node:child_process'
import { existsSync, readFileSync, rmSync } from 'node:fs'
import { promisify } from 'node:util'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const execFileAsync = promisify(execFile)
const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const composeFile = resolve(frontendDir, 'e2e', 'compose.yml')
const runtimeFile = resolve(frontendDir, 'e2e', '.runtime', 'runtime.json')

export default async function globalTeardown () {
  if (existsSync(runtimeFile)) {
    const { pid } = JSON.parse(readFileSync(runtimeFile, 'utf8'))
    if (pid) {
      if (process.platform === 'win32') {
        await execFileAsync('taskkill', ['/PID', String(pid), '/T', '/F']).catch(() => {})
      } else {
        process.kill(pid)
      }
    }
  }

  await execFileAsync('docker', ['compose', '-p', 'devchester-e2e', '-f', composeFile, 'down', '-v'], { cwd: frontendDir }).catch(() => {})
  try {
    rmSync(resolve(frontendDir, 'e2e', '.runtime'), { recursive: true, force: true, maxRetries: 5, retryDelay: 200 })
  } catch {
    // Windows can briefly retain redirected log handles after taskkill.
  }
}
