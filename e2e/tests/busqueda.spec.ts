import { expect, test, type Page } from '@playwright/test'
import { api, loginToken, tomorrowInBogota } from '../api'
import { data } from '../data'
import { failOnPageErrors, slot } from './helpers'

/** Busca la sede de las pruebas para mañana a esa hora con "¿Cuándo quieres jugar?". */
async function searchTomorrowAt(page: Page, hour: number) {
  await page.goto('/')
  await page.getByLabel('Buscar sedes').fill(data.venueName)
  await page.getByRole('group', { name: '¿Cuándo quieres jugar?' }).getByRole('button', { name: 'Mañana' }).click()
  await page.getByLabel('Hora').selectOption(String(hour))
}

test('buscar por día y hora muestra el resumen de la sede y abre la reserva con esa hora elegida', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await searchTomorrowAt(page, 13)

  const card = page.locator('.venue-card', { hasText: data.venueName })
  await expect(card).toBeVisible()
  await expect(card).toContainText('Fútbol 5 · Fútbol 7')
  await expect(card.locator('.venue-price')).toContainText(/80\.000/) // el más barato de sus horarios
  await card.getByRole('link', { name: 'Reservar a las 13:00' }).click()

  await expect(page).toHaveURL(new RegExp(`fecha=${tomorrowInBogota()}&hora=13`))
  await expect(page.locator(`button[data-date="${tomorrowInBogota()}"]`)).toHaveAttribute('aria-pressed', 'true')
  await expect(slot(page, 13)).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('region', { name: 'Resumen de la reserva' })).toContainText('13:00–14:00')

  // Pestañas con las horas libres de cada cancha (Cancha 2 solo abre de 18 a 22)
  const tabs = page.getByRole('tablist', { name: 'Canchas' })
  await expect(tabs.getByRole('tab', { name: /Cancha 1/ })).toContainText('h libres')
  await expect(tabs.getByRole('tab', { name: /Cancha 2/ })).toContainText('4 h libres')
  checkErrors()
})

test('si a esa hora no hay cancha libre, se ofrecen las horas vecinas', async ({ page }) => {
  const customerToken = await loginToken(data.customerEmail)
  const reservation = await api('POST', '/api/v1/reservations',
    { courtId: data.courtId, startAt: `${tomorrowInBogota()}T13:00:00-05:00`, durationHours: 1 }, customerToken)

  try {
    await searchTomorrowAt(page, 13) // Cancha 1 ocupada y Cancha 2 cerrada a esa hora
    await expect(page.getByText(/No hay canchas libres el .* a las 13:00/)).toBeVisible()
    await expect(page.locator('.venue-card', { hasText: data.venueName })).toHaveCount(0)

    await page.getByRole('button', { name: 'Probar a las 14:00' }).click()
    await expect(page.getByLabel('Hora')).toHaveValue('14')
    await expect(page.locator('.venue-card', { hasText: data.venueName })).toBeVisible()
  } finally {
    await api('POST', `/api/v1/reservations/${reservation.id}/cancel`, {}, customerToken)
  }
})
