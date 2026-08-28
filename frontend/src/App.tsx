import { useEffect, useState } from 'react'

type HealthResponse = {
  status: string
  application: string
}

function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('http://localhost:8080/api/v1/health')
      .then((res) => res.json())
      .then((data) => setHealth(data))
      .catch(() => setError('No se pudo conectar con el backend'))
  }, [])

  return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>Sintéticas — Punto cero</h1>
      {health && (
        <p>
          Backend responde: <strong>{health.status}</strong> ({health.application})
        </p>
      )}
      {error && <p style={{ color: 'red' }}>{error}</p>}
      {!health && !error && <p>Consultando backend...</p>}
    </div>
  )
}

export default App
