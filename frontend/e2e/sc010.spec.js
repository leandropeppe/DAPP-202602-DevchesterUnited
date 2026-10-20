import { expect, test } from '@playwright/test'

test('SC-010 completa el flujo funcional del frontend sin hosts externos', async ({ page }) => {
  const requests = []

  page.on(
      'request',
      (request) => requests.push(request.url())
  )

  const username = `e2euser${Date.now()}`
  const email = `${username}@example.test`

  await page.goto('/registro')

  await page
      .getByLabel('Nombre de usuario', { exact: true })
      .fill(username)

  await page
      .getByLabel('Correo electrónico', { exact: true })
      .fill(email)

  await page
      .getByLabel('Contraseña', { exact: true })
      .fill('ClaveSegura1')

  await page
      .getByLabel('Confirmar contraseña', { exact: true })
      .fill('ClaveSegura1')

  await page
      .getByRole('button', { name: 'Registrarme' })
      .click()

  await expect(
      page.getByRole('status')
  ).toContainText('registrado')

  await page
      .getByRole('link', { name: 'login' })
      .click()

  await page
      .getByLabel('Nombre de usuario', { exact: true })
      .fill(username)

  await page
      .getByLabel('Contraseña', { exact: true })
      .fill('ClaveSegura1')

  await page
      .getByRole('button', { name: 'Iniciar sesión' })
      .click()

  await expect(page).toHaveURL(/\/catalogo$/)

  await expect(
      page.getByText('Jugador Real 01')
  ).toBeVisible()

  await expect(
      page.getByText('--', { exact: true }).first()
  ).toBeVisible()

  await page
      .getByLabel('Equipo')
      .fill('Madrid')

  await page
      .getByRole('button', { name: 'Aplicar filtros' })
      .click()

  await expect(
      page.getByText('Página 1 de 2')
  ).toBeVisible()

  await page
      .getByRole('button', { name: 'Siguiente' })
      .click()

  await expect(
      page.getByText('Página 2 de 2')
  ).toBeVisible()

  await page
      .getByRole('button', { name: 'Cerrar sesión' })
      .click()

  await expect(page).toHaveURL(/\/login$/)

  await expect(
      page.getByRole(
          'heading',
          { name: 'Iniciar sesión' }
      )
  ).toBeVisible()

  const apiRequests = requests.filter(
      (url) => url.includes('/api/v1/')
  )

  expect(
      apiRequests.length
  ).toBeGreaterThan(0)

  expect(
      apiRequests.every(
          (url) =>
              url.startsWith(
                  'http://127.0.0.1:4173/api/v1/'
              )
      )
  ).toBe(true)

  expect(
      requests.every(
          (url) =>
              url.startsWith(
                  'http://127.0.0.1:4173/'
              )
      )
  ).toBe(true)

  expect(
      requests.some(
          (url) =>
              /football-data|external|api\.football/i.test(url)
      )
  ).toBe(false)
})