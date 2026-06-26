import { useState, useEffect } from 'react'
import api from '../api/client'

const CATEGORIES = [
  'Celulares y Teléfonos', 'Computación', 'Electrónica y Audio',
  'Electrodomésticos', 'Herramientas', 'Indumentaria y Calzado',
  'Deportes y Fitness', 'Hogar, Muebles y Jardín', 'Juegos y Juguetes',
  'Alimentos y Bebidas', 'Salud y Belleza', 'Otras categorías'
]

const INSTALLMENT_OPTIONS = [
  'Sin cuotas',
  'Cuotas con interés bajo',
  'Cuotas al mismo precio 3 cuotas',
  'Cuotas al mismo precio 6 cuotas',
  'Cuotas al mismo precio 9 cuotas',
  'Cuotas al mismo precio 12 cuotas',
]

export default function ProductConfigModal({ unknownSkus, uploadId, onDone, onClose }) {
  const products = unknownSkus.map(s => {
    const [sku, name] = s.split('|')
    return { sku, name }
  })

  const [forms, setForms] = useState(
    products.map(p => ({
      sku: p.sku,
      name: p.name,
      category: 'Otras categorías',
      saleModality: 'Sin cuotas',
      restockThreshold: 5,
      currentStock: 0,
      costPrice: 0,
      // SKU del producto al que se fusiona (puede ser de este batch o de la BD), o null
      mergedInto: null,
    }))
  )
  // Productos ya existentes en la BD (para ofrecer como destino de fusión)
  const [dbProducts, setDbProducts] = useState([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/products')
      .then(res => setDbProducts(res.data || []))
      .catch(() => {/* si falla, el select simplemente no tendrá opciones de BD */})
  }, [])

  function updateForm(idx, field, value) {
    setForms(prev => prev.map((f, i) => i === idx ? { ...f, [field]: value } : f))
  }

  // Al quitar una tarjeta desvinculamos las que apuntaban a ella
  function removeForm(idx) {
    const removedSku = forms[idx].sku
    setForms(prev =>
      prev
        .filter((_, i) => i !== idx)
        .map(f => f.mergedInto === removedSku ? { ...f, mergedInto: null } : f)
    )
  }

  function setMerge(idx, targetSku) {
    setForms(prev => prev.map((f, i) => i === idx ? { ...f, mergedInto: targetSku || null } : f))
  }

  // Resuelve el nombre visible de un SKU destino
  function resolveTargetName(targetSku) {
    const inBatch = forms.find(f => f.sku === targetSku)
    if (inBatch) return inBatch.name
    const inDb = dbProducts.find(p => p.sku === targetSku)
    if (inDb) return inDb.name
    return targetSku
  }

  async function handleSave() {
    // Solo validar nombres en tarjetas que crean producto nuevo
    const primaryForms = forms.filter(f => f.mergedInto === null)
    const sinNombre = primaryForms.findIndex(f => !f.name || f.name.trim() === '')
    if (sinNombre !== -1) {
      const globalIdx = forms.indexOf(primaryForms[sinNombre])
      setError(`El producto ${globalIdx + 1} no tiene nombre. Completalo o eliminá la tarjeta con la ✕.`)
      return
    }

    setSaving(true)
    setError('')
    try {
      // Mando todo junto en una sola petición: el backend crea los productos,
      // aplica las fusiones y reprocesa las operaciones de una. Así el dashboard
      // nunca queda con datos a medio cargar.

      // Productos nuevos a crear: armo el objeto con los campos que espera el backend
      // (sin mandar el mergedInto, que es solo de la pantalla).
      const products = primaryForms.map(f => ({
        sku: f.sku,
        name: f.name,
        category: f.category,
        saleModality: f.saleModality,
        restockThreshold: f.restockThreshold,
        currentStock: f.currentStock,
        costPrice: f.costPrice,
      }))

      // Fusiones: las tarjetas que el usuario marcó como "este es el mismo que aquel".
      const merges = []
      for (const f of forms) {
        if (f.mergedInto !== null) {
          merges.push({ fromSku: f.sku, toSku: f.mergedInto })
        }
      }

      await api.post(`/uploads/${uploadId}/apply-config`, { products, merges })

      onDone()
    } catch (e) {
      setError('Error guardando los productos. Verificá los datos.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[90vh] flex flex-col">
        <div className="p-6 border-b border-slate-200">
          <h2 className="text-base font-semibold text-slate-800">Productos nuevos detectados</h2>
          <p className="text-slate-500 text-sm mt-1">
            Se {products.length === 1 ? 'encontró' : 'encontraron'} {products.length}{' '}
            producto{products.length !== 1 ? 's' : ''} sin registrar. Completá su configuración
            o indicá si corresponde a un producto ya existente.
          </p>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {forms.map((form, idx) => {
            const isMerged = form.mergedInto !== null
            // Opciones dentro del mismo batch (excluye las que ya están fusionadas con algo)
            const batchTargets = forms.filter((f, i) => i !== idx && f.mergedInto === null)

            return (
              <div
                key={idx}
                className={`border rounded-lg p-4 relative ${
                  isMerged ? 'border-indigo-200 bg-indigo-50/50' : 'border-slate-200'
                }`}
              >
                <button
                  onClick={() => removeForm(idx)}
                  title="Quitar este producto"
                  className="absolute top-3 right-3 text-slate-300 hover:text-red-400 text-lg leading-none"
                >✕</button>

                {/* Nombre del producto */}
                <div className="mb-3">
                  <label className="text-xs font-medium text-slate-600 block mb-1">
                    Nombre del producto
                  </label>
                  <input
                    type="text"
                    value={form.name}
                    onChange={e => updateForm(idx, 'name', e.target.value)}
                    disabled={isMerged}
                    className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm font-medium text-slate-800 focus:outline-none focus:border-indigo-500 disabled:bg-slate-100 disabled:text-slate-400"
                  />
                  {!isMerged && (
                    <p className="text-xs text-slate-400 mt-0.5">
                      Podés acortarlo para agrupar variantes (ej: "Perfume Rasasi Daarej")
                    </p>
                  )}
                </div>

                {/* Selector de fusión */}
                <div className="mb-3">
                  <label className="text-xs font-medium text-slate-600 block mb-1">
                    ¿Corresponde a un producto ya existente?
                  </label>
                  <select
                    value={form.mergedInto || ''}
                    onChange={e => setMerge(idx, e.target.value)}
                    className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500"
                  >
                    <option value="">No, crear como producto nuevo</option>

                    {/* Productos de la BD */}
                    {dbProducts.length > 0 && (
                      <optgroup label="── Productos ya registrados ──">
                        {dbProducts.map(p => (
                          <option key={p.sku} value={p.sku}>{p.name}</option>
                        ))}
                      </optgroup>
                    )}

                    {/* Otros nuevos en este mismo batch */}
                    {batchTargets.length > 0 && (
                      <optgroup label="── Otros nuevos en esta carga ──">
                        {batchTargets.map(t => (
                          <option key={t.sku} value={t.sku}>{t.name}</option>
                        ))}
                      </optgroup>
                    )}
                  </select>
                </div>

                {/* Banner de fusión */}
                {isMerged ? (
                  <div className="mt-1 p-3 bg-indigo-100 border border-indigo-200 rounded-md text-sm text-indigo-800 flex items-start gap-2">
                    <span className="shrink-0 mt-0.5">🔗</span>
                    <div>
                      <p className="font-medium">Sus ventas se contabilizarán junto a:</p>
                      <p className="font-semibold text-indigo-700 mt-0.5">
                        {resolveTargetName(form.mergedInto)}
                      </p>
                      <p className="text-xs text-indigo-500 mt-1">
                        No necesitás configurar este SKU por separado.
                      </p>
                    </div>
                  </div>
                ) : (
                  /* Formulario de configuración */
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-medium text-slate-600 block mb-1">Categoría</label>
                      <select
                        value={form.category}
                        onChange={e => updateForm(idx, 'category', e.target.value)}
                        className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500"
                      >
                        {CATEGORIES.map(c => <option key={c}>{c}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="text-xs font-medium text-slate-600 block mb-1">Opción de cuotas</label>
                      <select
                        value={form.saleModality}
                        onChange={e => updateForm(idx, 'saleModality', e.target.value)}
                        className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500"
                      >
                        {INSTALLMENT_OPTIONS.map(m => <option key={m}>{m}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="text-xs font-medium text-slate-600 block mb-1">Costo de compra (unitario)</label>
                      <input
                        type="number"
                        value={form.costPrice}
                        onChange={e => updateForm(idx, 'costPrice', Number(e.target.value))}
                        className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500"
                        min="0"
                        step="0.01"
                      />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-slate-600 block mb-1">Stock actual</label>
                      <input
                        type="number"
                        value={form.currentStock}
                        onChange={e => updateForm(idx, 'currentStock', Number(e.target.value))}
                        className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500"
                        min="0"
                      />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-slate-600 block mb-1">Umbral de reabastecimiento</label>
                      <input
                        type="number"
                        value={form.restockThreshold}
                        onChange={e => updateForm(idx, 'restockThreshold', Number(e.target.value))}
                        className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500"
                        min="1"
                      />
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>

        {error && (
          <div className="px-6 pb-2">
            <p className="text-red-500 text-sm">{error}</p>
          </div>
        )}

        <div className="p-6 border-t border-slate-200 flex gap-2 justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded hover:bg-slate-50"
          >
            Cancelar
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="px-4 py-2 text-sm font-medium bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-50"
          >
            {saving ? 'Guardando…' : 'Guardar y continuar'}
          </button>
        </div>
      </div>
    </div>
  )
}
