import { expect, test } from '@playwright/test'
import { api, loginToken, tomorrowInBogota } from '../api'
import { data } from '../data'
import { failOnPageErrors, loginByForm, pickDay, slot } from './helpers'

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
  await pickDay(page, day)
  await expect(slot(page, 8)).toBeDisabled()
  await expect(slot(page, 8)).toContainText('Cerrada por la sede')

  await page.goto(`/admin/canchas/${data.courtId}/horario`)
  await page.locator('.mini-item', { hasText: 'Bloqueo de prueba' }).locator('button:has-text("Quitar")').click()
  await expect(page.locator('.mini-item', { hasText: 'Bloqueo de prueba' })).toHaveCount(0)

  await page.goto(`/venues/${data.venueId}`)
  await pickDay(page, day)
  await expect(slot(page, 8)).toBeEnabled()
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

test('el dueño cancela una reserva desde la agenda con el diálogo de confirmación', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  const day = tomorrowInBogota()
  const customerToken = await loginToken(data.customerEmail)
  const reservation = await api('POST', '/api/v1/reservations',
    { courtId: data.courtId, startAt: `${day}T21:00:00-05:00`, durationHours: 1 }, customerToken)

  await page.goto(`/admin/sedes/${data.venueId}/agenda`)
  const item = page.locator('.mini-item', { has: page.locator(`a[href="/reservas/${reservation.id}"]`) })
  await item.getByRole('button', { name: 'Cancelar' }).click()

  const dialog = page.getByRole('dialog', { name: /Cancelar la reserva de/ })
  await expect(dialog).toContainText('Le llegará un correo')
  await dialog.getByRole('button', { name: 'Volver' }).click() // arrepentirse no cancela nada
  await expect(dialog).toBeHidden()
  await expect(item.locator('.status-tag')).toHaveText(/Confirmada/i)

  await item.getByRole('button', { name: 'Cancelar' }).click()
  await dialog.getByRole('button', { name: 'Cancelar reserva' }).click()
  await expect(item.locator('.status-tag')).toHaveText(/Cancelada/i)
  checkErrors()
})
