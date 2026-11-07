import { expect, test } from '@playwright/test'
import { api, loginToken, register, tomorrowInBogota, uniqueEmail } from '../api'
import { data } from '../data'
import { failOnPageErrors, firstFreePair, loginByForm, pickDay, slot } from './helpers'

test('reservar 2 horas con dos toques, revisar, compartir, dividir la cuenta y cancelar', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await loginByForm(page, data.customerEmail)

  await page.goto(`/venues/${data.venueId}`)
  await pickDay(page, tomorrowInBogota())
  const start = await firstFreePair(page)

  // Primer toque: hora de inicio. Segundo toque en la hora siguiente: el partido dura 2 horas
  await slot(page, start).click()
  await slot(page, start + 1).click()
  await expect(slot(page, start)).toHaveAttribute('aria-pressed', 'true')
  await expect(slot(page, start + 1)).toHaveAttribute('aria-pressed', 'true')
  const bar = page.getByRole('region', { name: 'Resumen de la reserva' })
  await expect(bar).toContainText('2 h')
  await expect(bar).toContainText(/160\.000/) // 2 horas a $80.000 (horario de prueba)

  await bar.getByRole('button', { name: 'Continuar' }).click()
  const review = page.getByRole('dialog', { name: 'Revisa tu reserva' })
  await expect(review).toContainText('Puedes cancelar desde la app hasta las')
  await review.getByPlaceholder('Ej.: llevamos balón').fill('Prueba automática')
  await review.getByRole('button', { name: 'Confirmar reserva' }).click()

  const success = page.getByRole('dialog', { name: '¡Cancha reservada!' })
  await expect(success).toBeVisible()
  await expect(success.getByRole('link', { name: /WhatsApp/ })).toHaveAttribute('href', /^https:\/\/wa\.me\/\?text=/)
  await expect(success.getByRole('link', { name: /Cómo llegar/ })).toHaveAttribute('href', /google\.com\/maps/)
  const download = page.waitForEvent('download')
  await success.getByRole('button', { name: /Agregar al calendario/ }).click()
  expect((await download).suggestedFilename()).toBe('partido-sinteticas.ics')

  await success.getByRole('link', { name: 'Ver mi reserva' }).click()
  await expect(page.getByText('Prueba automática')).toBeVisible()

  // Dividir la cuenta: Fútbol 5 son 10 jugadores por defecto, $160.000 / 10 = $16.000
  await expect(page.getByTestId('per-player')).toHaveText(/16\.000/)
  await page.getByRole('button', { name: 'Un jugador más' }).click()
  await expect(page.getByTestId('per-player')).toHaveText(/14\.600/) // 160.000 / 11 = 14.545, redondeado a la centena

  await page.getByRole('button', { name: 'Cancelar reserva' }).click()
  await page.getByRole('dialog', { name: '¿Cancelar esta reserva?' }).getByRole('button', { name: 'Cancelar reserva' }).click()
  await expect(page.locator('.status-tag', { hasText: 'Cancelada' })).toBeVisible()

  await page.goto(`/venues/${data.venueId}`)
  await pickDay(page, tomorrowInBogota())
  await expect(slot(page, start)).toBeEnabled()
  checkErrors()
})

test('si alguien toma la hora mientras se revisa, se avisa y se sugiere la más cercana', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await loginByForm(page, data.customerEmail)
  await page.goto(`/venues/${data.venueId}`)
  const day = tomorrowInBogota()
  await pickDay(page, day)
  const start = await firstFreePair(page)
  await slot(page, start).click()
  await page.getByRole('button', { name: 'Continuar' }).click()

  // Otra persona reserva esa misma hora en el último segundo
  const rivalEmail = uniqueEmail('rival')
  await register('Rival E2E', rivalEmail)
  const rivalToken = await loginToken(rivalEmail)
  const startAt = `${day}T${String(start).padStart(2, '0')}:00:00-05:00`
  const rival = await api('POST', '/api/v1/reservations', { courtId: data.courtId, startAt, durationHours: 1 }, rivalToken)

  const review = page.getByRole('dialog', { name: 'Revisa tu reserva' })
  await review.getByRole('button', { name: 'Confirmar reserva' }).click()
  await expect(review.getByRole('alert')).toContainText('Alguien acaba de tomar esa hora')
  const suggestion = review.getByRole('button', { name: /^Cambiar a/ })
  await expect(suggestion).toBeVisible()
  await suggestion.click()
  await review.getByRole('button', { name: 'Confirmar reserva' }).click()
  await expect(page.getByRole('dialog', { name: '¡Cancha reservada!' })).toBeVisible()

  // Limpieza: se cancelan ambas para no llegar al máximo de reservas activas
  await api('POST', `/api/v1/reservations/${rival.id}/cancel`, {}, rivalToken)
  await page.getByRole('link', { name: 'Ver mi reserva' }).click()
  await page.getByRole('button', { name: 'Cancelar reserva' }).click()
  await page.getByRole('dialog', { name: '¿Cancelar esta reserva?' }).getByRole('button', { name: 'Cancelar reserva' }).click()
  await expect(page.locator('.status-tag', { hasText: 'Cancelada' })).toBeVisible()
  checkErrors()
})

test('sin sesión se puede ver la sede pero se pide iniciar sesión para reservar', async ({ page }) => {
  await page.goto(`/venues/${data.venueId}`)
  await pickDay(page, tomorrowInBogota())
  await page.locator('.slot-row:not([disabled])').first().click()
  await expect(page.getByRole('link', { name: 'Inicia sesión para reservar' })).toBeVisible()
})
