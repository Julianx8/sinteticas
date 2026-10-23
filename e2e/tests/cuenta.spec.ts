import { expect, test } from '@playwright/test'
import { PASSWORD, resetLinkFromMailpit, uniqueEmail } from '../api'
import { data } from '../data'
import { failOnPageErrors, loginByForm } from './helpers'

test('registrarse, olvidar la contraseña, restablecerla por correo y entrar con la nueva', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  const email = uniqueEmail('olvido')

  await page.goto('/register')
  await page.fill('input[type=text]', 'Persona Olvidadiza')
  await page.fill('input[type=email]', email)
  await page.fill('input[type=password]', PASSWORD)
  await page.click('button[type=submit]')
  await expect(page.locator('a.user-pill')).toHaveText('Persona Olvidadiza')
  await page.click('text=Cerrar sesión')

  await page.goto('/login')
  await page.click('text=¿Olvidaste tu contraseña?')
  await page.fill('input[type=email]', email)
  await page.click('button[type=submit]')
  await expect(page.getByText('te enviamos un enlace')).toBeVisible()

  await page.goto(await resetLinkFromMailpit(email))
  const nueva = 'Nueva12345'
  await page.locator('input[autocomplete=new-password]').nth(0).fill(nueva)
  await page.locator('input[autocomplete=new-password]').nth(1).fill(nueva)
  await page.click('button[type=submit]')
  await expect(page.getByText('Tu contraseña cambió')).toBeVisible()

  await loginByForm(page, email, nueva)
  checkErrors()
})

test('cambiar el nombre en el perfil se ve en la barra', async ({ page }) => {
  const checkErrors = failOnPageErrors(page)
  await loginByForm(page, data.customerEmail)
  await page.goto('/mi-perfil')
  const newName = `Cliente ${Date.now() % 100000}` // distinto en cada ejecución: con el mismo nombre no hay nada que guardar
  await page.fill('input[maxlength="150"]', newName)
  await page.click('button:has-text("Guardar")')
  await expect(page.getByText('Nombre actualizado')).toBeVisible()
  await expect(page.locator('a.user-pill')).toHaveText(newName)
  checkErrors()
})

test('si el token de acceso vence, se renueva solo sin sacar a la persona', async ({ page }) => {
  await loginByForm(page, data.customerEmail)
  await page.evaluate(() => {
    const session = JSON.parse(localStorage.getItem('sinteticas-auth')!)
    session.token = 'token-vencido'
    localStorage.setItem('sinteticas-auth', JSON.stringify(session))
  })
  // La primera petición recibe 401: la app renueva el token con el refresh token y la repite
  await page.goto('/mis-reservas')
  await expect(page.getByText('Cargando reservas...')).toHaveCount(0)
  await expect(page.getByRole('tab', { name: 'Próximas' })).toBeVisible()
  await expect(page.locator('a.user-pill')).toBeVisible()
  await expect
    .poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('sinteticas-auth') ?? '{}').token))
    .not.toBe('token-vencido')
})

test('las páginas protegidas piden iniciar sesión', async ({ page }) => {
  await page.goto('/mis-reservas')
  await expect(page).toHaveURL(/\/login/)
})
