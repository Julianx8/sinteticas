import fs from 'node:fs'
import path from 'node:path'
import { api, loginToken, register, uniqueEmail } from './api'

/**
 * Crea por la API un dueño con una sede, una cancha con horario y un cliente.
 * El super admin sale de las variables SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD (las mismas del .env).
 */
export default async function globalSetup() {
  const superEmail = process.env.SUPER_ADMIN_EMAIL
  const superPassword = process.env.SUPER_ADMIN_PASSWORD
  if (!superEmail || !superPassword) {
    throw new Error('Definir SUPER_ADMIN_EMAIL y SUPER_ADMIN_PASSWORD (los del .env) para correr las pruebas e2e')
  }

  const superToken = await loginToken(superEmail, superPassword)
  const ownerEmail = uniqueEmail('dueno')
  const owner = await register('Dueño E2E', ownerEmail)
  await api('PUT', `/api/v1/admin/users/${owner.user.id}/roles`, { roles: ['CUSTOMER', 'ADMIN'] }, superToken)
  const ownerToken = await loginToken(ownerEmail) // token nuevo con el rol ADMIN

  const venue = await api('POST', '/api/v1/venues', {
    name: `Sede E2E ${Date.now()}`, address: 'Calle 1 # 2-3', neighborhood: 'Centro', phone: '3001234567',
    description: 'Creada por las pruebas automáticas', active: true,
  }, ownerToken)
  const court = await api('POST', `/api/v1/venues/${venue.id}/courts`, {
    name: 'Cancha 1', courtType: 'FUTBOL_5', description: '', active: true,
  }, ownerToken)
  const slots = [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, startHour: 8, endHour: 22, pricePerHour: 80000 }))
  await api('PUT', `/api/v1/courts/${court.id}/schedules`, { slots }, ownerToken)
  // Segunda cancha, más cara y solo de noche: sirve para probar las pestañas y el precio mínimo
  const court2 = await api('POST', `/api/v1/venues/${venue.id}/courts`, {
    name: 'Cancha 2', courtType: 'FUTBOL_7', description: '', active: true,
  }, ownerToken)
  const slots2 = [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, startHour: 18, endHour: 22, pricePerHour: 120000 }))
  await api('PUT', `/api/v1/courts/${court2.id}/schedules`, { slots: slots2 }, ownerToken)

  const customerEmail = uniqueEmail('cliente')
  await register('Cliente E2E', customerEmail)

  const data = { ownerEmail, customerEmail, venueId: venue.id, venueName: venue.name, courtId: court.id, court2Id: court2.id }
  fs.writeFileSync(path.join(__dirname, '.e2e-data.json'), JSON.stringify(data, null, 2))
}
