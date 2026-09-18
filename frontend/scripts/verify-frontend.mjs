import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { resolve, relative, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const src = resolve(frontend, 'src')
const packageJsonPath = resolve(frontend, 'package.json')
const forbiddenNames = new Set(['tsconfig.json', 'tsconfig.base.json', 'jsconfig.ts'])
const sourceFiles = []

function walk (directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) walk(path)
    else sourceFiles.push(path)
  }
}

walk(src)

const typeFiles = sourceFiles.filter((path) => /\.tsx?$/.test(path))
const forbiddenConfig = sourceFiles.filter((path) => forbiddenNames.has(path.split(/[\\/]/).pop()))
const sourceText = sourceFiles.map((path) => readFileSync(path, 'utf8')).join('\n')
const packageText = readFileSync(packageJsonPath, 'utf8')
const violations = []

if (typeFiles.length) violations.push(`Archivos TypeScript: ${typeFiles.map((path) => relative(frontend, path)).join(', ')}`)
if (forbiddenConfig.length) violations.push(`Configuración TypeScript: ${forbiddenConfig.map((path) => relative(frontend, path)).join(', ')}`)
if (/football-data|Football-Data/i.test(sourceText)) violations.push('Referencia productiva a Football-Data en frontend/src')
if (/localStorage/.test(sourceText)) violations.push('Uso de localStorage en frontend/src')
if (/JWT_SECRET|DB_PASSWORD|FOOTBALL_DATA_API_KEY|devchester-e2e|e2e_password/i.test(packageText)) violations.push('Posible secreto o credencial en package.json')
if (!/sessionStorage/.test(sourceText)) violations.push('No se encontró sessionStorage en frontend/src')

if (violations.length) {
  console.error(violations.join('\n'))
  process.exit(1)
}

console.log(`Frontend verification passed: ${sourceFiles.length} JavaScript/JSX source files; no TypeScript, secrets, Football-Data or localStorage references.`)
