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

export default function ProductsPage() {
  const [products, setProducts] = useState([])
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(null) // id del producto a eliminar
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    api.get('/products').then(r => setProducts(r.data))
  }, [])

  function startEdit(p) {
    setEditing({ ...p })
    setConfirmDelete(null)
  }

  async function saveEdit() {
    setSaving(true)
    try {
      const res = await api.put(`/products/${editing.id}`, editing)
      setProducts(prev => prev.map(p => p.id === editing.id ? res.data : p))
      setEditing(null)
    } catch {
      alert('No se pudo guardar el producto. Revisá la conexión con el servidor.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id) {
    setDeleting(true)
    try {
      await api.delete(`/products/${id}`)
      setProducts(prev => prev.filter(p => p.id !== id))
      setConfirmDelete(null)
    } catch {
      alert('No se pudo eliminar el producto.')
    } finally {
      setDeleting(false)
    }
  }

  if (products.length === 0) {
    return (
      <div className="mx-auto max-w-md mt-16 text-center">
        <h2 className="text-xl font-semibold text-slate-800">Todavía no hay productos</h2>
        <p className="text-sm text-slate-500 mt-2">
          Los productos se registran solos cuando procesás un ZIP de facturas
          y completás sus parámetros.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-semibold text-slate-800">Productos registrados</h2>
        <p className="text-sm text-slate-500 mt-0.5">
          {products.length} producto{products.length !== 1 ? 's' : ''} en el catálogo.
          La categoría y la opción de cuotas definen la comisión aplicada al calcular el margen.
        </p>
      </div>

      <div className="bg-white rounded-lg border border-slate-200">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
              <th className="px-4 py-3 font-medium">Producto</th>
              <th className="px-4 py-3 font-medium">Categoría</th>
              <th className="px-4 py-3 font-medium">Opción de cuotas</th>
              <th className="px-4 py-3 font-medium text-right">Costo compra</th>
              <th className="px-4 py-3 font-medium text-right">Stock</th>
              <th className="px-4 py-3 font-medium text-right">Umbral</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {products.map((p, i) => (
              <tr key={p.id} className={`border-b border-slate-100 last:border-0 ${i % 2 ? 'bg-slate-50/60' : ''}`}>
                <td className="px-4 py-3 font-medium text-slate-800">{p.name}</td>
                <td className="px-4 py-3 text-slate-600">{p.category || '—'}</td>
                <td className="px-4 py-3 text-slate-600">{p.saleModality || '—'}</td>
                <td className="px-4 py-3 text-right text-slate-700">
                  {p.costPrice != null
                    ? '$' + Number(p.costPrice).toLocaleString('es-AR', { minimumFractionDigits: 2 })
                    : '—'}
                </td>
                <td className="px-4 py-3 text-right text-slate-700">{p.currentStock}</td>
                <td className="px-4 py-3 text-right text-slate-700">{p.restockThreshold}</td>
                <td className="px-4 py-3">
                  {confirmDelete === p.id ? (
                    <span className="flex items-center justify-end gap-2">
                      <span className="text-xs text-slate-500">¿Seguro?</span>
                      <button
                        onClick={() => handleDelete(p.id)}
                        disabled={deleting}
                        className="text-xs text-rose-600 hover:text-rose-800 font-medium disabled:opacity-50"
                      >
                        {deleting ? 'Eliminando…' : 'Sí, eliminar'}
                      </button>
                      <button
                        onClick={() => setConfirmDelete(null)}
                        className="text-xs text-slate-500 hover:text-slate-700"
                      >
                        Cancelar
                      </button>
                    </span>
                  ) : (
                    <span className="flex items-center justify-end gap-3">
                      <button
                        onClick={() => startEdit(p)}
                        className="text-indigo-600 hover:text-indigo-800 hover:underline text-sm"
                      >
                        Editar
                      </button>
                      <button
                        onClick={() => setConfirmDelete(p.id)}
                        className="text-slate-400 hover:text-rose-600 text-sm transition-colors"
                      >
                        Eliminar
                      </button>
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl max-w-lg w-full p-6">
            <h3 className="text-base font-semibold text-slate-800">Editar producto</h3>
            <p className="text-sm text-slate-500 mt-1 mb-4">{editing.name}</p>
            <div className="grid grid-cols-2 gap-4">
              <div className="col-span-2">
                <label className="text-xs font-medium text-slate-600 block mb-1">Categoría</label>
                <select value={editing.category || ''} onChange={e => setEditing({ ...editing, category: e.target.value })}
                  className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500">
                  {CATEGORIES.map(c => <option key={c}>{c}</option>)}
                </select>
              </div>
              <div className="col-span-2">
                <label className="text-xs font-medium text-slate-600 block mb-1">Opción de cuotas</label>
                <select value={editing.saleModality || ''} onChange={e => setEditing({ ...editing, saleModality: e.target.value })}
                  className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500">
                  {INSTALLMENT_OPTIONS.map(m => <option key={m}>{m}</option>)}
                </select>
              </div>
              <div className="col-span-2">
                <label className="text-xs font-medium text-slate-600 block mb-1">Costo de compra (unitario)</label>
                <input type="number" min="0" step="0.01" value={editing.costPrice ?? 0}
                  onChange={e => setEditing({ ...editing, costPrice: Number(e.target.value) })}
                  className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500" />
              </div>
              <div>
                <label className="text-xs font-medium text-slate-600 block mb-1">Stock actual</label>
                <input type="number" min="0" value={editing.currentStock ?? 0}
                  onChange={e => setEditing({ ...editing, currentStock: Number(e.target.value) })}
                  className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500" />
              </div>
              <div>
                <label className="text-xs font-medium text-slate-600 block mb-1">Umbral de reabastecimiento</label>
                <input type="number" min="1" value={editing.restockThreshold ?? 5}
                  onChange={e => setEditing({ ...editing, restockThreshold: Number(e.target.value) })}
                  className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-indigo-500" />
              </div>
            </div>
            <div className="flex gap-2 justify-end mt-6">
              <button onClick={() => setEditing(null)}
                className="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded hover:bg-slate-50">
                Cancelar
              </button>
              <button onClick={saveEdit} disabled={saving}
                className="px-4 py-2 text-sm font-medium bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-50">
                {saving ? 'Guardando…' : 'Guardar cambios'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
