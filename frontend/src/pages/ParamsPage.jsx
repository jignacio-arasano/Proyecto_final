import { useState, useEffect } from 'react'
import api from '../api/client'

function RateTable({ title, subtitle, columns, rows, onUpdate, onSave, savingKey, saving }) {
  return (
    <div className="bg-white rounded-lg border border-slate-200 overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-200">
        <h3 className="font-medium text-slate-800 text-sm">{title}</h3>
        {subtitle && <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>}
      </div>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            {columns.map(col => (
              <th key={col.key} className={`px-5 py-2.5 font-medium ${col.align === 'right' ? 'text-right' : 'text-left'}`}>
                {col.label}
              </th>
            ))}
            <th className="px-5 py-2.5 font-medium text-right">Acción</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map(row => (
            <tr key={row.id}>
              {columns.map(col => (
                <td key={col.key} className={`px-5 py-2 ${col.align === 'right' ? 'text-right' : 'text-slate-700'}`}>
                  {col.editable ? (
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      max="100"
                      value={(Number(row[col.key]) * 100).toFixed(2)}
                      onChange={e => onUpdate(row.id, col.key, (parseFloat(e.target.value) / 100).toFixed(4))}
                      className="w-20 border border-slate-300 rounded px-2 py-1 text-right text-sm focus:outline-none focus:border-indigo-500"
                    />
                  ) : (
                    <span className="text-slate-600">{row[col.key]}</span>
                  )}
                </td>
              ))}
              <td className="px-5 py-2 text-right">
                <button
                  onClick={() => onSave(row)}
                  disabled={saving === `${savingKey}-${row.id}`}
                  className="px-3 py-1 text-xs bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-50"
                >
                  {saving === `${savingKey}-${row.id}` ? '...' : 'Guardar'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function ParamsPage() {
  const [commissions, setCommissions] = useState([])
  const [installments, setInstallments] = useState([])
  const [iibb, setIibb] = useState([])
  const [saving, setSaving] = useState(null)
  const [msg, setMsg] = useState('')

  useEffect(() => {
    api.get('/params/commissions').then(r => setCommissions(r.data))
    api.get('/params/installments').then(r => setInstallments(r.data))
    api.get('/params/iibb').then(r => setIibb(r.data))
  }, [])

  async function save(url, body, key, id) {
    setSaving(`${key}-${id}`)
    try {
      await api.put(url, body)
      setMsg('Guardado correctamente')
    } catch {
      setMsg('Error al guardar')
    } finally {
      setSaving(null)
      setTimeout(() => setMsg(''), 2500)
    }
  }

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-xl font-semibold text-slate-800">Parámetros del sistema</h2>
        <p className="text-slate-500 text-sm mt-0.5">
          Configurá las tasas de Mercado Libre y las alícuotas de IIBB por provincia.
          Los cambios se aplican al próximo procesamiento.
        </p>
      </div>

      {msg && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 rounded text-emerald-700 text-sm">
          {msg}
        </div>
      )}

      {/* Comisiones base por categoría */}
      <RateTable
        title="Comisiones base por categoría"
        subtitle="Cargo por vender según categoría del producto (sin recargo por cuotas)"
        columns={[
          { key: 'category', label: 'Categoría', align: 'left', editable: false },
          { key: 'rate', label: 'Tasa base (%)', align: 'right', editable: true },
        ]}
        rows={commissions}
        onUpdate={(id, key, val) =>
          setCommissions(prev => prev.map(c => c.id === id ? { ...c, [key]: val } : c))
        }
        onSave={item => save(`/params/commissions/${item.id}`, { rate: item.rate }, 'comm', item.id)}
        savingKey="comm"
        saving={saving}
      />

      {/* Recargos por opción de cuotas */}
      <RateTable
        title="Recargos por opción de cuotas"
        subtitle="Se suman a la comisión base cuando el vendedor ofrece cuotas a sus compradores"
        columns={[
          { key: 'optionName', label: 'Opción de cuotas', align: 'left', editable: false },
          { key: 'surchargeRate', label: 'Recargo (%)', align: 'right', editable: true },
        ]}
        rows={installments}
        onUpdate={(id, key, val) =>
          setInstallments(prev => prev.map(r => r.id === id ? { ...r, [key]: val } : r))
        }
        onSave={item => save(`/params/installments/${item.id}`, { surchargeRate: item.surchargeRate }, 'inst', item.id)}
        savingKey="inst"
        saving={saving}
      />

      {/* IIBB por provincia */}
      <RateTable
        title="Ingresos Brutos por provincia del comprador"
        subtitle="Alícuota sobre el ingreso bruto — se determina por la provincia donde está el comprador"
        columns={[
          { key: 'province', label: 'Provincia', align: 'left', editable: false },
          { key: 'rate', label: 'Alícuota (%)', align: 'right', editable: true },
        ]}
        rows={iibb}
        onUpdate={(id, key, val) =>
          setIibb(prev => prev.map(r => r.id === id ? { ...r, [key]: val } : r))
        }
        onSave={item => save(`/params/iibb/${item.id}`, { rate: item.rate }, 'iibb', item.id)}
        savingKey="iibb"
        saving={saving}
      />
    </div>
  )
}
