import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDropzone } from 'react-dropzone'
import api from '../api/client'
import ProductConfigModal from '../components/ProductConfigModal'
import UploadHistory from '../components/UploadHistory'

export default function UploadPage() {
  const navigate = useNavigate()
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [loadingMsg, setLoadingMsg] = useState('Procesando documentos…')
  const [error, setError] = useState('')
  const [showConfig, setShowConfig] = useState(false)
  // Cambia cada vez que se procesa un ZIP para refrescar el historial.
  const [historyRefresh, setHistoryRefresh] = useState(0)

  // Se ejecuta cuando el usuario arrastra o elige un archivo.
  async function onDrop(files) {
    const file = files[0]
    if (!file) return
    // Solo acepto archivos .zip.
    if (!file.name.toLowerCase().endsWith('.zip')) {
      setError('El archivo seleccionado no es un ZIP. Solo se aceptan archivos .zip con facturas en PDF.')
      return
    }
    setError('')
    setLoading(true)
    setLoadingMsg('Leyendo facturas del ZIP…')
    setResult(null)
    try {
      // Mando el ZIP al backend para que lo procese.
      const formData = new FormData()
      formData.append('file', file)
      const res = await api.post('/uploads/process', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      setResult(res.data)
      setHistoryRefresh(k => k + 1)
      // Si hay productos nuevos sin configurar, abro el modal de configuración.
      if (res.data.status === 'needs_config') {
        setShowConfig(true)
      }
    } catch (e) {
      if (e.response) {
        // El backend respondió pero con un error: el problema es el archivo, no la conexión.
        setError(e.response.data || 'El archivo no se pudo procesar. Verificá que sea un ZIP válido con facturas en PDF.')
      } else {
        // No hubo respuesta del servidor: el backend no está corriendo o hay un problema de red.
        setError('No se pudo conectar con el servidor. Verificá que el backend esté corriendo.')
      }
    } finally {
      setLoading(false)
    }
  }

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'application/zip': ['.zip'] },
    maxFiles: 1,
    disabled: loading,
  })

  function handleConfigDone() {
    // El endpoint atómico /apply-config (llamado desde el modal) ya creó los
    // productos, aplicó las fusiones y reprocesó las operaciones en una sola
    // transacción. Acá solo reflejamos el resultado final en la UI.
    setShowConfig(false)
    setResult(prev => prev ? ({
      ...prev,
      status: 'completed',
      // tras apply-config todos los docs pasaron a ser válidos
      validDocs: prev.totalDocs,
      invalidDocs: 0,
      errors: [],
    }) : prev)
    setHistoryRefresh(k => k + 1)
  }

  function handleReset() {
    setResult(null)
    setError('')
    setShowConfig(false)
  }

  const isCompleted = result?.status === 'completed'

  return (
    <div className="max-w-3xl mx-auto">
      <h2 className="text-xl font-semibold text-slate-800">Importar documentos de ventas</h2>
      <p className="text-slate-500 text-sm mt-1 mb-6">
        Subí el ZIP con las facturas del período. El sistema las lee, calcula el margen neto
        de cada operación y deja los resultados disponibles en el dashboard.
      </p>

      {/* Dropzone — solo visible cuando no hay resultado completado */}
      {!isCompleted && (
        <div
          {...getRootProps()}
          className={`border-2 border-dashed rounded-lg p-10 text-center cursor-pointer transition-colors ${
            isDragActive
              ? 'border-indigo-400 bg-indigo-50'
              : 'border-slate-300 hover:border-slate-400 hover:bg-slate-50'
          } ${loading ? 'opacity-60 cursor-not-allowed' : ''}`}
        >
          <input {...getInputProps()} />
          {loading ? (
            <div className="flex flex-col items-center gap-3">
              <svg className="animate-spin h-7 w-7 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              <p className="text-slate-600 text-sm">{loadingMsg}</p>
            </div>
          ) : isDragActive ? (
            <p className="text-indigo-600 font-medium">Soltá el archivo para empezar</p>
          ) : (
            <>
              <p className="text-slate-700 font-medium">Arrastrá el ZIP acá o hacé clic para elegirlo</p>
              <p className="text-slate-400 text-sm mt-1">Solo archivos .zip que contengan las facturas en PDF</p>
            </>
          )}
        </div>
      )}

      {/* Error de red / validación */}
      {error && (
        <div className="mt-4 p-3 bg-rose-50 border border-rose-200 rounded text-rose-700 text-sm">
          {error}
        </div>
      )}

      {/* ZIP sin PDFs */}
      {result?.status === 'empty' && (
        <div className="mt-4 p-3 bg-amber-50 border border-amber-200 rounded text-amber-800 text-sm">
          El archivo no contiene facturas en PDF. Asegurate de que el ZIP incluya los archivos de facturación.
        </div>
      )}

      {/* ZIP con PDFs que no son facturas de ML */}
      {result?.status === 'no_invoices' && (
        <div className="mt-4 p-3 bg-amber-50 border border-amber-200 rounded text-amber-800 text-sm">
          <p className="font-medium">Ningún archivo tiene formato de factura de Mercado Libre.</p>
          <p className="mt-1">
            Se encontraron {result.totalDocs} PDF{result.totalDocs !== 1 ? 's' : ''}, pero ninguno pudo leerse
            como factura. Verificá que el ZIP contenga las facturas de ventas de ML (no otros documentos).
          </p>
        </div>
      )}

      {/* ZIP corrupto o inválido */}
      {result?.status === 'invalid' && (
        <div className="mt-4 p-3 bg-amber-50 border border-amber-200 rounded text-amber-800 text-sm">
          El archivo no es un ZIP válido o está corrupto. Verificá que el archivo no esté dañado y volvé a intentarlo.
        </div>
      )}

      {/* ── ÉXITO: solo se muestra cuando status === 'completed' ── */}
      {isCompleted && (
        <div className="mt-6 space-y-4">

          {/* Card principal de éxito */}
          <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-5 flex items-start gap-4">
            <div className="shrink-0 mt-0.5">
              <svg className="w-6 h-6 text-emerald-600" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-emerald-800 font-semibold text-sm">Importación completada</p>
              <p className="text-emerald-700 text-sm mt-0.5">
                {result.validDocs} {result.validDocs === 1 ? 'factura procesada' : 'facturas procesadas'} correctamente.
                Los márgenes y predicciones están actualizados en el dashboard.
              </p>
            </div>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-3 gap-3">
            <Stat label="Documentos" value={result.totalDocs} />
            <Stat label="Procesados" value={result.validDocs} tone="ok" />
            <Stat label="Con errores" value={result.invalidDocs ?? 0}
              tone={(result.invalidDocs ?? 0) > 0 ? 'bad' : 'muted'} />
          </div>

          {/* Errores de parsing reales (no productos sin configurar) */}
          {result.errors?.length > 0 && (
            <div className="p-3 bg-amber-50 border border-amber-200 rounded">
              <p className="text-amber-800 font-medium text-sm mb-1.5">
                {result.errors.length} {result.errors.length === 1 ? 'documento' : 'documentos'} que no se {result.errors.length === 1 ? 'pudo' : 'pudieron'} leer
              </p>
              <ul className="text-sm text-amber-700 space-y-0.5 list-disc list-inside">
                {result.errors.map((e, i) => <li key={i}>{e}</li>)}
              </ul>
            </div>
          )}

          {/* Botones de acción */}
          <div className="flex gap-3 pt-1">
            <button
              onClick={() => navigate('/dashboard')}
              className="flex items-center gap-2 px-5 py-2.5 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z" />
              </svg>
              Ir al Dashboard
            </button>
            <button
              onClick={handleReset}
              className="px-5 py-2.5 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50 transition-colors"
            >
              Importar otro ZIP
            </button>
          </div>
        </div>
      )}

      {/* Modal de configuración de productos nuevos */}
      {showConfig && result?.unknownSkus && (
        <ProductConfigModal
          unknownSkus={result.unknownSkus}
          uploadId={result.uploadId}
          onDone={handleConfigDone}
          onClose={() => setShowConfig(false)}
        />
      )}

      {/* Historial de ZIPs cargados, con opción de eliminar */}
      <UploadHistory refreshKey={historyRefresh} />
    </div>
  )
}

function Stat({ label, value, tone }) {
  const tones = {
    ok: 'text-emerald-700',
    bad: 'text-rose-600',
    muted: 'text-slate-400',
  }
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className={`text-2xl font-semibold ${tones[tone] || 'text-slate-800'}`}>{value}</div>
      <div className="text-xs text-slate-500 mt-0.5">{label}</div>
    </div>
  )
}
