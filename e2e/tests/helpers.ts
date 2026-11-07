import { expect, type Page } from '@playwright/test'
import { PASSWORD } from '../api'

/** Inicia sesión por el formulario, como lo haría una persona (sirve en escritorio y en celular). */
export async function loginByForm(page: Page, email: string, password = PASSWORD) {
  await page.goto('/login')
  await page.fill('input[type=email]', email)
  await page.fill('input[type=password]', password)
  await page.click('button[type=submit]')
  await page.waitForURL((url) => !url.pathname.startsWith('/login'))
  await expect
    .poll(() => page.evaluate(() => Boolean(JSON.parse(localStorage.getItem('sinteticas-auth') ?? '{}').token)))
    .toBe(true)
}

/** Falla la prueba si la página lanza un error de JavaScript. */
export function failOnPageErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  return () => expect(errors, 'errores de JavaScript en la página').toEqual([])
}

/** Elige un día en la tira de fechas de la sede. */
export async function pickDay(page: Page, isoDate: string) {
  await page.locator(`button[data-date="${isoDate}"]`).click()
  await expect(page.locator(`button[data-date="${isoDate}"]`)).toHaveAttribute('aria-pressed', 'true')
}

/** Fila de una hora en la línea de tiempo (8 = 08:00). */
export const slot = (page: Page, hour: number) => page.locator(`.slot-row[data-hour="${hour}"]`)

/** Primera hora libre que tenga la siguiente también libre (para probar reservas de 2 horas). */
export async function firstFreePair(page: Page): Promise<number> {
  await expect(page.locator('.slot-row').first()).toBeVisible()
  const hours = await page
    .locator('.slot-row:not([disabled])')
    .evaluateAll((rows) => rows.map((row) => Number(row.getAttribute('data-hour'))))
  const start = hours.find((hour) => hours.includes(hour + 1))
  if (start === undefined) throw new Error('No hay dos horas libres seguidas')
  return start
}

/** La página no se desborda a lo ancho (nada obliga a desplazarse de lado). */
export async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow, 'la página se sale del ancho de la pantalla').toBeLessThanOrEqual(0)
}
