import { execFile, spawn } from 'node:child_process'
import { existsSync, mkdirSync, openSync, writeFileSync } from 'node:fs'
import { promisify } from 'node:util'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const execFileAsync = promisify(execFile)
const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repositoryDir = resolve(frontendDir, '..')
const composeFile = resolve(frontendDir, 'e2e', 'compose.yml')
const composeProject = 'devchester-e2e'
const jarPath = resolve(repositoryDir, 'backend', 'build', 'libs', 'DevchesterUnitedV2-0.0.1-SNAPSHOT.jar')
const runtimeDir = resolve(frontendDir, 'e2e', '.runtime')
const runtimeFile = resolve(runtimeDir, 'runtime.json')

async function compose (...args) {
  return execFileAsync('docker', ['compose', '-p', composeProject, '-f', composeFile, ...args], { cwd: frontendDir })
}

async function waitForDatabase (timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      await compose('exec', '-T', 'postgres', 'pg_isready', '-U', 'e2e_user', '-d', 'devchester_e2e')
      return
    } catch {
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 1000))
    }
  }
  throw new Error('PostgreSQL E2E no alcanzó readiness')
}

async function waitFor (url, timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs
  let lastError
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch (error) {
      lastError = error
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 1000))
  }
  throw new Error(`El backend E2E no alcanzó readiness en ${url}: ${lastError?.message || 'timeout'}`)
}

export default async function globalSetup () {
  if (!existsSync(jarPath)) {
    throw new Error(`No existe ${jarPath}. Ejecuta backend\\gradlew.bat bootJar antes de Playwright.`)
  }

  mkdirSync(runtimeDir, { recursive: true })
  await compose('up', '-d', 'postgres')
  await waitForDatabase()

  const logPath = resolve(runtimeDir, 'backend.log')
  const logHandle = openSync(logPath, 'a')

  const backend = spawn('java', ['-Duser.timezone=UTC', '-jar', jarPath], {
    cwd: repositoryDir,
    env: {
      ...process.env,

      DB_URL: 'jdbc:postgresql://127.0.0.1:15432/devchester_e2e',
      DB_USERNAME: 'e2e_user',
      DB_PASSWORD: 'e2e_password',

      JWT_SECRET: 'ZGV2Y2hlc3Rlci1lMmUtc3ludGhldGljLWp3dC1zZWNyZXQ=',

      SPRING_PROFILES_ACTIVE: 'local',
      SERVER_PORT: '18080',
      FOOTBALL_DATA_API_KEY: '',
      TZ: 'UTC',

      VALORIZACION_GENERAL_VALOR_BASE: '1',
      VALORIZACION_GENERAL_FACTOR_ESCALA: '99',

      VALORIZACION_POSITIONAL_VALOR_BASE: '1',
      VALORIZACION_POSITIONAL_FACTOR_ESCALA: '99',

      VALORIZACION_POSITIONAL_FACTOR_MERCADO_DL: '1.15',
      VALORIZACION_POSITIONAL_FACTOR_MERCADO_MC: '1.05',
      VALORIZACION_POSITIONAL_FACTOR_MERCADO_DF: '0.95',
      VALORIZACION_POSITIONAL_FACTOR_MERCADO_PO: '0.90',

      VALORIZACION_ELEGIBILIDAD_CONFIGURADA: 'true',
      VALORIZACION_ELEGIBILIDAD_MINIMO_PARTIDOS: '0',
      VALORIZACION_ELEGIBILIDAD_MINIMO_MINUTOS: '0'

    },
    stdio: ['ignore', logHandle, logHandle],
    windowsHide: true
  })

  writeFileSync(runtimeFile, JSON.stringify({ pid: backend.pid, logPath }))

  try {
    await waitFor('http://127.0.0.1:18080/v3/api-docs')
    await compose('exec', '-T', 'postgres', 'psql', '-U', 'e2e_user', '-d', 'devchester_e2e', '-f', '/e2e/fixture.sql')
  } catch (error) {
    backend.kill()
    await compose('down', '-v').catch(() => {})
    throw error
  }
}
