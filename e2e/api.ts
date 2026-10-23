// Ayudas para preparar datos por la API (más rápido que hacerlo con clics).
export const API_URL = process.env.API_URL ?? 'http://localhost:8080'
export const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://localhost:8025'
export const PASSWORD = 'Prueba12345'

export async function api<T = any>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
  const response = await fetch(API_URL + path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  const text = await response.text()
  if (!response.ok) throw new Error(`${method} ${path} → ${response.status}: ${text}`)
  return (text ? JSON.parse(text) : undefined) as T
}

export async function register(fullName: string, email: string) {
  return api('POST', '/api/v1/auth/register', { fullName, email, password: PASSWORD })
}

export async function loginToken(email: string, password = PASSWORD): Promise<string> {
  return (await api('POST', '/api/v1/auth/login', { email, password })).accessToken
}

/** Correo único por ejecución, para que las pruebas no choquen con datos de ejecuciones anteriores. */
export const uniqueEmail = (prefix: string) => `${prefix}.${Date.now()}${Math.floor(Math.random() * 1000)}@e2e.test`

/** Fecha de mañana en Colombia (AAAA-MM-DD): siempre tiene horas libres, sin importar la hora a la que corra la prueba. */
export function tomorrowInBogota() {
  const now = new Date(Date.now() - 5 * 3600_000)
  now.setUTCDate(now.getUTCDate() + 1)
  return now.toISOString().slice(0, 10)
}

/** Espera el correo de restablecer contraseña en Mailpit y devuelve el enlace. */
export async function resetLinkFromMailpit(email: string): Promise<string> {
  for (let attempt = 0; attempt < 30; attempt++) {
    const list = await (await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)).json()
    if (list.messages?.length) {
      const message = await (await fetch(`${MAILPIT_URL}/api/v1/message/${list.messages[0].ID}`)).json()
      const link = message.Text.match(/https?:\/\/\S+restablecer-contrasena\?token=[\w-]+/)?.[0]
      if (link) return link
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`No llegó a Mailpit el correo para ${email}`)
}
