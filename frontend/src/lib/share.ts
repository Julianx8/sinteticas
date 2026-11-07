// Acciones para compartir una reserva: calendario, WhatsApp y mapa. Todo ocurre en el navegador.

type MatchInfo = {
  id: string
  courtName: string
  venueName: string
  address: string
  startAt: string
  endAt: string
}

/** Enlace de Google Maps a la dirección (abre la app de mapas en el celular). Sin clave ni costo. */
export function mapsUrl(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${address}, Girardot, Cundinamarca`)}`
}

/** Enlace para enviar un mensaje por WhatsApp (la persona elige el chat o grupo). */
export function whatsappUrl(text: string): string {
  return `https://wa.me/?text=${encodeURIComponent(text)}`
}

/** "20261107T000000Z": formato de fecha de los archivos de calendario (en UTC). */
function icsDate(iso: string): string {
  return new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
}

function icsText(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\n/g, '\\n')
}

/** Contenido de un archivo .ics con el partido y un aviso 2 horas antes. */
export function buildIcs(match: MatchInfo): string {
  return [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Sinteticas//Reservas//ES',
    'CALSCALE:GREGORIAN',
    'BEGIN:VEVENT',
    `UID:${match.id}@sinteticas`,
    `DTSTAMP:${icsDate(new Date().toISOString())}`,
    `DTSTART:${icsDate(match.startAt)}`,
    `DTEND:${icsDate(match.endAt)}`,
    `SUMMARY:${icsText(`Partido · ${match.courtName} (${match.venueName})`)}`,
    `LOCATION:${icsText(`${match.venueName}, ${match.address}, Girardot`)}`,
    `DESCRIPTION:${icsText(`Reserva en Sintéticas. Cómo llegar: ${mapsUrl(match.address)}`)}`,
    'BEGIN:VALARM',
    'TRIGGER:-PT2H',
    'ACTION:DISPLAY',
    'DESCRIPTION:Partido en 2 horas',
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR',
  ].join('\r\n')
}

/** Descarga el .ics; el celular lo abre con su app de calendario. */
export function downloadIcs(match: MatchInfo) {
  const blob = new Blob([buildIcs(match)], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'partido-sinteticas.ics'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Jugadores por defecto según el tipo de cancha ("Fútbol 5" = 5 contra 5). */
export function defaultPlayers(courtType: string | undefined): number {
  const perSide = Number(courtType?.replace('FUTBOL_', ''))
  return Number.isFinite(perSide) && perSide > 0 ? perSide * 2 : 10
}

/** Cuota por jugador redondeada a la centena hacia arriba, y cuánto sobra con ese redondeo. */
export function splitBill(total: number, players: number) {
  const perPlayer = Math.ceil(total / players / 100) * 100
  return { perPlayer, leftover: perPlayer * players - total }
}
