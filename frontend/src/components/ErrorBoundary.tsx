import { Component, type ErrorInfo, type ReactNode } from 'react'

/**
 * Si una página falla al dibujarse, muestra un aviso en vez de dejar la pantalla en blanco.
 * App.tsx la reinicia al cambiar de ruta (key = ruta actual).
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Error en la página', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <section className="content-panel empty-page" role="alert">
        <p className="eyebrow">Error</p>
        <h1>Algo salió mal en esta página</h1>
        <p className="muted">No se perdió nada de lo que guardaste. Recarga la página o vuelve al inicio.</p>
        <div className="venue-actions">
          <button type="button" className="primary" onClick={() => window.location.reload()}>Recargar</button>
          <a href="/" className="secondary link-button">Ir al inicio</a>
        </div>
      </section>
    )
  }
}
