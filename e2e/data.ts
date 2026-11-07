import fs from 'node:fs'
import path from 'node:path'

export type E2EData = {
  ownerEmail: string
  customerEmail: string
  venueId: string
  venueName: string
  courtId: string
  court2Id: string
}

/** Datos creados por global-setup.ts. */
export const data: E2EData = JSON.parse(fs.readFileSync(path.join(__dirname, '.e2e-data.json'), 'utf8'))
