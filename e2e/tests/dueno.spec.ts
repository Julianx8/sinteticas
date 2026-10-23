import { expect, test } from '@playwright/test'
import { tomorrowInBogota } from '../api'
import { data } from '../data'
import { failOnPageErrors, loginByForm } from './helpers'

test.beforeEach(async ({ page }) => {
  await loginByForm(page, data.ownerEmail)
})

test('el dueño ve su sede en el panel y entra a gestionarla', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await page.click('a:has-text("Mis sedes")')
  await expect(page).toHaveURL(/\/admin$/)
  await page.locator('a:has-text("Gestionar")').first().click()
  await expect(page.getByText('Cancha 1')).toBeVisible()
  checkErrors()
})

test('horario: una franja que se cruza muestra el error con el nombre del día, y al quitarla se guarda', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await page.goto(`/admin/canchas/${data.courtId}/horario`)
  const rows = page.locator('.data-table tbody tr')
  await expect(rows).toHaveCount(7)

  await page.click('text=Agregar franja') // copia la última franja (domingo 8-22) → se cruza
  await page.click('text=Guardar horario')
  await expect(page.locator('.status-box.error')).toContainText('se cruzan el domingo')

  await rows.last().locator('button:has-text("Quitar")').click()
  await page.click('text=Guardar horario')
  await expect(page.getByText('Horario guardado')).toBeVisible()
  await expect(rows).toHaveCount(7)
  checkErrors()
})

test('bloquear un horario lo quita de la disponibilidad, y al borrar el bloqueo vuelve', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  const day = tomorrowInBogota()
  await page.goto(`/admin/canchas/${data.courtId}/horario`)
  await page.locator('input[type=datetime-local]').nth(0).fill(`${day}T08:00`)
  await page.locator('input[type=datetime-local]').nth(1).fill(`${day}T10:00`)
  await page.fill('input[placeholder="Mantenimiento de la grama"]', 'Bloqueo de prueba')
  await page.click('button:has-text("Crear bloqueo")')
  await expect(page.locator('.mini-item', { hasText: 'Bloqueo de prueba' })).toBeVisible()

  await page.goto(`/venues/${data.venueId}`)
  await page.fill('#select-date', day)
  await expect(page.locator('.time-chip', { hasText: '08:00' }).first()).toBeDisabled()

  await page.goto(`/admin/canchas/${data.courtId}/horario`)
  await page.locator('.mini-item', { hasText: 'Bloqueo de prueba' }).locator('button:has-text("Quitar")').click()
  await expect(page.locator('.mini-item', { hasText: 'Bloqueo de prueba' })).toHaveCount(0)

  await page.goto(`/venues/${data.venueId}`)
  await page.fill('#select-date', day)
  await expect(page.locator('.time-chip', { hasText: '08:00' }).first()).toBeEnabled()
  checkErrors()
})

test('la agenda y los reportes de la sede cargan', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await page.goto(`/admin/sedes/${data.venueId}/agenda`)
  await expect(page.getByRole('heading').first()).toBeVisible()
  await page.goto(`/admin/sedes/${data.venueId}/reportes`)
  await expect(page.locator('.bar-plot')).toBeVisible()
  checkErrors()
})
