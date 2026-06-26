import { useState, useEffect, useCallback } from 'react'
import api from '../api/client'

// Formatea una fecha ISO (LocalDateTime) a algo legible en es-AR.
function fmtDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d)) return iso
  return d.toLocaleString('es-AR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

const STATUS_LABELS = {
  completed:   { label: 'Completado',       cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  needs_config:{ label: 'Completado',       cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  processing:  { label: 'Procesando',       cls: 'bg-slate-50 text-slate-600 border-slate-200' },
  empty:       { label: 'Sin facturas',     cls: 'bg-amber-50 text-amber-700 border-amber-200' },
  no_invoices: { label: 'Sin facturas ML',  cls: 'bg-amber-50 text-amber-700 border-amber-200' },
  invalid:     { label: 'Archivo inválido', cls: 'bg-rose-50 text-rose-600 border-rose-200' },
}

export default function UploadHistory({ refreshKey }) {
  const [uploads, setUploads] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deletingId, setDeletingId] = useState(null)
  const [confirmId, setConfirmId] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/uploads')
      setUploads(res.data || [])
    } catch {
      setError('No se pudo cargar el historial de cargas.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load, refreshKey])

  async function handleDelete(id) {
    setDeletingId(id)
    setError('')
    try {
      await api.delete(`/uploads/${id}`)
      setUploads(prev => prev.filter(u => u.id !== id))
      setConfirmId(null)
    } catch {
      setError('No se pudo eliminar el ZIP. Intentá de nuevo.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="mt-10">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-base font-semibold text-slate-800">Historial de cargas</h3>
        <button
          onClick={load}
          className="text-xs text-slate-500 hover:text-slate-700"
          title="Actualizar lista"
        >
          Actualizar
        </button>
      </div>

      {error && (
        <div className="mb-3 p-2.5 bg-rose-50 border border-rose-200 rounded text-rose-700 text-sm">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-slate-400 text-sm">Cargando…</p>
      ) : uploads.length === 0 ? (
        <p className="text-slate-400 text-sm">Todavía no cargaste ningún ZIP.</p>
      ) : (
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100 overflow-hidden">
          {uploads.map(u => {
            const st = STATUS_LABELS[u.status] || { label: u.status, cls: 'bg-slate-50 text-slate-600 border-slate-200' }
            return (
              <div key={u.id} className="flex items-center gap-3 px-4 py-3 hover:bg-slate-50">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium text-slate-800 truncate">{u.filename || 'ZIP sin nombre'}</p>
                    <span className={`shrink-0 text-[11px] px-1.5 py-0.5 rounded border ${st.cls}`}>{st.label}</span>
                  </div>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {fmtDate(u.uploadDate)} · {u.validDocs ?? 0}/{u.totalDocs ?? 0} facturas válidas
                  </p>
                </div>

                {confirmId === u.id ? (
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="text-xs text-slate-500">¿Eliminar?</span>
                    <button
                      onClick={() => handleDelete(u.id)}
                      disabled={deletingId === u.id}
                      className="text-xs font-medium px-2.5 py-1 bg-rose-600 text-white rounded hover:bg-rose-700 disabled:opacity-50"
                    >
                      {deletingId === u.id ? 'Eliminando…' : 'Sí, eliminar'}
                    </button>
                    <button
                      onClick={() => setConfirmId(null)}
                      disabled={deletingId === u.id}
                      className="text-xs px-2.5 py-1 text-slate-600 border border-slate-300 rounded hover:bg-slate-100"
                    >
                      Cancelar
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => setConfirmId(u.id)}
                    title="Eliminar este ZIP y sus operaciones"
                    className="shrink-0 text-slate-300 hover:text-rose-500 p-1"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
