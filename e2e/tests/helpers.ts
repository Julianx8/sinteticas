import { expect, type Page } from '@playwright/test'
import { PASSWORD } from '../api'

/** Inicia sesión por el formulario, como lo haría una persona. */
export async function loginByForm(page: Page, email: string, password = PASSWORD) {
  await page.goto('/login')
  await page.fill('input[type=email]', email)
  await page.fill('input[type=password]', password)
  await page.click('button[type=submit]')
  await expect(page.locator('a.user-pill')).toBeVisible()
}

/** Falla la prueba si la página lanza un error de JavaScript. */
export function failOnPageErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  return () => expect(errors, 'errores de JavaScript en la página').toEqual([])
}
