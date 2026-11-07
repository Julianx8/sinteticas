import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    // App instalable: manifest + service worker que guarda la "cáscara" de la app (HTML, JS, CSS, íconos)
    // para que abra al instante y funcione sin conexión. Las respuestas de la API NO se guardan aquí:
    // "Mis reservas" sin conexión lo maneja api/client.ts por usuario, y la disponibilidad siempre es en vivo.
    // Solo actúa en el build de producción (en `npm run dev` no hay service worker).
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Sintéticas · Reserva de canchas en Girardot',
        short_name: 'Sintéticas',
        description: 'Reserva canchas sintéticas de fútbol en Girardot.',
        lang: 'es-CO',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#f6f1ea',
        theme_color: '#2f7d5c',
        icons: [
          { src: 'pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png}'],
        // Cualquier ruta de la app (p. ej. /mis-reservas) abre index.html aunque no haya internet
        navigateFallback: '/index.html',
        cleanupOutdatedCaches: true,
      },
    }),
  ],
  server: {
    host: true,
    port: 5173,
  },
})
