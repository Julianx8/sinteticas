import { expect, test } from '@playwright/test'
import { tomorrowInBogota } from '../api'
import { data } from '../data'
import { failOnPageErrors, loginByForm } from './helpers'

test('un cliente reserva, la ve en "Mis reservas", la cancela y la hora vuelve a quedar libre', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await loginByForm(page, data.customerEmail)

  await page.goto(`/venues/${data.venueId}`)
  await page.fill('#select-date', tomorrowInBogota())
  const firstFree = page.locator('.time-chip:not([disabled])').first()
  const hourLabel = (await firstFree.textContent())!.split(' · ')[0]
  await firstFree.click()
  await page.fill('input[placeholder="Ej.: llevamos balón"]', 'Prueba automática')
  await page.click('button:has-text("Confirmar reserva")')
  await expect(page.getByText('Reserva confirmada')).toBeVisible()

  // La hora reservada ya no se puede elegir
  await expect(page.locator('.time-chip', { hasText: hourLabel }).first()).toBeDisabled()

  await page.goto('/mis-reservas')
  await page.locator('.reservation-card').first().click()
  await expect(page.getByText('Prueba automática')).toBeVisible()
  await page.click('button:has-text("Cancelar reserva")')
  await expect(page.locator('.status-tag', { hasText: 'Cancelada' })).toBeVisible()

  await page.goto(`/venues/${data.venueId}`)
  await page.fill('#select-date', tomorrowInBogota())
  await expect(page.locator('.time-chip', { hasText: hourLabel }).first()).toBeEnabled()
  checkErrors()
})

test('sin sesión se puede ver la sede pero se pide iniciar sesión para reservar', async ({ page }) => {
  await page.goto(`/venues/${data.venueId}`)
  await page.fill('#select-date', tomorrowInBogota())
  await page.locator('.time-chip:not([disabled])').first().click()
  await expect(page.getByRole('link', { name: 'Inicia sesión' })).toBeVisible()
})
