import { expect, test } from '@playwright/test'

test('SC-010 completa el flujo funcional del frontend sin hosts externos', async ({ page, context }) => {
  const requests = []
  page.on('request', (request) => requests.push(request.url()))

  const username = `e2euser${Date.now()}`
  const email = `${username}@example.test`

  await page.goto('/registro')
  await page.getByLabel('Nombre de usuario').fill(username)
  await page.getByLabel('Correo electrónico').fill(email)
  await page.getByLabel('Contraseña').fill('ClaveSegura1')
  await page.getByRole('button', { name: 'Registrarme' }).click()
  await expect(page.getByRole('status')).toContainText('registrado')

  await page.getByRole('link', { name: 'login' }).click()
  await page.getByLabel('Nombre de usuario').fill(username)
  await page.getByLabel('Contraseña').fill('ClaveSegura1')
  await page.getByRole('button', { name: 'Iniciar sesión' }).click()
  await expect(page).toHaveURL(/\/catalogo$/)

  await page.getByRole('link', { name: 'Credenciales' }).click()
  await expect(page).toHaveURL(/\/credenciales$/)

  await page.getByRole('button', { name: 'Generar o rotar API Key' }).click()
  const generatedKey = await page.locator('code').textContent()
  expect(generatedKey).toMatch(/^dev_key_[A-Za-z0-9_-]{43}$/)
  await expect(page.getByText('no volverá a mostrarse completa')).toBeVisible()

  await page.reload()
  await expect(page.locator('code')).toHaveCount(0)
  await expect(page.getByText(/API Key conservada:/)).toBeVisible()

  await page.getByRole('link', { name: 'Catálogo' }).click()
  await expect(page).toHaveURL(/\/catalogo$/)
  await expect(page.getByText('Jugador Real 01')).toBeVisible()
  await expect(page.getByText('Sin información', { exact: true }).first()).toBeVisible()

  await page.getByLabel('Equipo').fill('Madrid')
  await page.getByRole('button', { name: 'Aplicar filtros' }).click()
  await expect(page.getByText('Página 1 de 2')).toBeVisible()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await expect(page.getByText('Página 2 de 2')).toBeVisible()

  const secondPage = await context.newPage()
  await secondPage.goto('/credenciales')
  await expect(secondPage.getByText('No hay una API Key guardada en esta pestaña.')).toBeVisible()
  await secondPage.close()

  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()

  const apiRequests = requests.filter((url) => url.includes('/api/v1/'))
  expect(apiRequests.length).toBeGreaterThan(0)
  expect(apiRequests.every((url) => url.startsWith('http://127.0.0.1:4173/api/v1/'))).toBe(true)
  expect(requests.every((url) => url.startsWith('http://127.0.0.1:4173/'))).toBe(true)
  expect(requests.some((url) => /football-data|external|api\.football/i.test(url))).toBe(false)
})
