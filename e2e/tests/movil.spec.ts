import { expect, test } from '@playwright/test'
import { api, loginToken, tomorrowInBogota } from '../api'
import { data } from '../data'
import { expectNoHorizontalScroll, failOnPageErrors, loginByForm, pickDay } from './helpers'

// Pantalla de un celular común (390 × 844, táctil)
test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true })

test('en celular la navegación va abajo y ninguna pantalla se sale del ancho', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await loginByForm(page, data.customerEmail)

  const bottomNav = page.getByRole('navigation', { name: 'Navegación inferior' })
  await expect(bottomNav).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Navegación principal' })).toBeHidden()

  for (const path of ['/', `/venues/${data.venueId}`, '/mis-reservas', '/mi-perfil', '/no-existe']) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    await expectNoHorizontalScroll(page)
  }

  await bottomNav.getByRole('link', { name: 'Reservas' }).click()
  await expect(page).toHaveURL(/\/mis-reservas$/)
  await expect(page).toHaveTitle('Mis reservas · Sintéticas')
  await bottomNav.getByRole('link', { name: 'Perfil' }).click()
  await expect(page.getByRole('button', { name: 'Cerrar sesión', exact: true })).toBeVisible()
  checkErrors()
})

test('la barra de resumen queda visible encima de la navegación inferior', async ({ page }) => {
  await loginByForm(page, data.customerEmail)
  await page.goto(`/venues/${data.venueId}`)
  await pickDay(page, tomorrowInBogota())
  await page.locator('.slot-row:not([disabled])').first().click()

  const bar = page.getByRole('region', { name: 'Resumen de la reserva' })
  await expect(bar).toBeInViewport()
  const barBox = (await bar.boundingBox())!
  const navBox = (await page.getByRole('navigation', { name: 'Navegación inferior' }).boundingBox())!
  expect(barBox.y + barBox.height).toBeLessThanOrEqual(navBox.y + 1)
})

test('una dirección que no existe muestra la página 404 con salida', async ({ page }) => {
  await page.goto('/esta/ruta/no/existe')
  await expect(page.getByRole('heading', { name: 'Esta página no existe' })).toBeVisible()
  await expect(page).toHaveTitle('Página no encontrada · Sintéticas')
  await page.getByRole('link', { name: 'Buscar canchas' }).click()
  await expect(page).toHaveURL(/\/$/)
})

test('sin internet se ven las reservas guardadas, con aviso', async ({ page, context }) => {
  const day = tomorrowInBogota()
  const customerToken = await loginToken(data.customerEmail)
  const reservation = await api('POST', '/api/v1/reservations',
    { courtId: data.courtId, startAt: `${day}T20:00:00-05:00`, durationHours: 1 }, customerToken)

  await loginByForm(page, data.customerEmail)
  await page.goto('/mis-reservas')
  await expect(page.locator(`a[href="/reservas/${reservation.id}"]`)).toBeVisible() // queda guardada una copia

  await page.goto('/')
  await context.setOffline(true)
  await page.getByRole('navigation', { name: 'Navegación inferior' }).getByRole('link', { name: 'Reservas' }).click()
  await expect(page.getByText('Sin conexión. Puedes ver tus reservas guardadas')).toBeVisible()
  await expect(page.getByText(/mostrando lo que guardamos/)).toBeVisible()
  await expect(page.locator(`a[href="/reservas/${reservation.id}"]`)).toBeVisible()

  await context.setOffline(false)
  await api('POST', `/api/v1/reservations/${reservation.id}/cancel`, {}, customerToken)
})

test('al cerrar sesión se borran las copias guardadas', async ({ page }) => {
  await loginByForm(page, data.customerEmail)
  await page.goto('/mis-reservas')
  await expect(page.getByRole('tab', { name: 'Próximas' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => Object.keys(localStorage).some((key) => key.startsWith('sinteticas-offline:')))).toBe(true)

  await page.goto('/mi-perfil')
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  expect(await page.evaluate(() => Object.keys(localStorage).filter((key) => key.startsWith('sinteticas-')))).toEqual([])
})
