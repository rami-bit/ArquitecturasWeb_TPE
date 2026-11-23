import { useEffect, useState } from 'react'
import { Api, Preferences, Recommendation, errorMessage, formatDate, formatMoney } from './types'

export function PreferencesView({ api, openEvent }: { api: Api; openEvent: (eventId: string) => void }) {
  const [city, setCity] = useState('')
  const [types, setTypes] = useState('')
  const [artists, setArtists] = useState('')
  const [saved, setSaved] = useState<Preferences | null>(null)
  const [recommendations, setRecommendations] = useState<{ items: Recommendation[]; warnings: string[] } | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    api<Preferences>('/api/customers/me/preferences', { signal: controller.signal }).then(data => {
      setSaved(data); setCity(data.city || ''); setTypes(data.eventTypes.join(', ')); setArtists(data.favoriteArtists.join(', '))
    }).catch(e => { if (!controller.signal.aborted) setError(errorMessage(e)) })
    return () => controller.abort()
  }, [api])

  async function save() {
    setError('')
    try {
      const payload = {
        city: city.trim() || null,
        eventTypes: types.split(',').map(value => value.trim()).filter(Boolean),
        favoriteArtists: artists.split(',').map(value => value.trim()).filter(Boolean),
      }
      setSaved(await api<Preferences>('/api/customers/me/preferences', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }))
    } catch (e) { setError(errorMessage(e)) }
  }

  async function loadRecommendations() {
    setError('')
    try { setRecommendations(await api<{ items: Recommendation[]; warnings: string[] }>('/api/customers/me/recommendations?limit=10')) }
    catch (e) { setError(errorMessage(e)) }
  }

  return <section aria-label="Preferencias y recomendaciones">
    <h2>Preferencias</h2>
    {error && <p role="alert">{error}</p>}
    <div className="filters">
      <label>Ciudad <input value={city} onChange={e => setCity(e.target.value)} /></label>
      <label>Tipos de evento (separados por coma) <input value={types} onChange={e => setTypes(e.target.value)} /></label>
      <label>Artistas favoritos (separados por coma) <input value={artists} onChange={e => setArtists(e.target.value)} /></label>
    </div>
    <button onClick={() => void save()}>Guardar preferencias</button>
    {saved && <p>Preferencias guardadas · versión {saved.version}. Ciudad: {saved.city || 'sin preferencia'}; tipos: {saved.eventTypes.join(', ') || 'ninguno'}; artistas: {saved.favoriteArtists.join(', ') || 'ninguno'}.</p>}
    <h3>Recomendaciones</h3><p>Las recomendaciones no garantizan disponibilidad. Consultaremos el inventario al abrir el evento.</p>
    <button onClick={() => void loadRecommendations()}>Consultar recomendaciones</button>
    {recommendations && <>{recommendations.warnings.length > 0 && <p role="status">Avisos: {recommendations.warnings.join(', ')}</p>}
      {recommendations.items.length === 0 && <p>No hay recomendaciones para mostrar.</p>}
      <ul>{recommendations.items.map(event => <li key={event.eventId}><button onClick={() => openEvent(event.eventId)}>{event.name}</button> · {event.city} · {formatDate(event.startsAt)} · {formatMoney(event.minPrice ?? 0)} · Afinidad {event.score} · {event.reasons.join(', ')}</li>)}</ul>
    </>}
  </section>
}
