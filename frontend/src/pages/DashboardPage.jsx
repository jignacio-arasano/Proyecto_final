import { useState, useEffect } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell
} from 'recharts'
import api from '../api/client'

export default function DashboardPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [expanded, setExpanded] = useState(null)

  // Abre o cierra la fila del producto. Si toco la que ya estaba abierta, la cierro.
  function toggleRow(sku) {
    if (expanded === sku) {
      setExpanded(null)
    } else {
      setExpanded(sku)
    }
  }

  useEffect(() => {
    api.get('/dashboard')
      .then(res => setData(res.data))
      .catch(() => setError('No se pudieron cargar los datos del dashboard.'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <div className="text-center py-20 text-slate-500">Cargando dashboard…</div>

  if (error) return (
    <div className="max-w-md mx-auto mt-16 text-center">
      <h2 className="text-lg font-semibold text-slate-800">No hay datos para mostrar</h2>
      <p className="text-sm text-slate-500 mt-2">{error}</p>
      <p className="text-sm text-slate-500 mt-1">Importá un ZIP de facturas para ver los indicadores.</p>
    </div>
  )

  if (!data || data.totalOperations === 0) return (
    <div className="max-w-md mx-auto mt-16 text-center">
      <h2 className="text-lg font-semibold text-slate-800">Todavía no hay operaciones</h2>
      <p className="text-sm text-slate-500 mt-2">
        Importá un archivo ZIP con facturas y los indicadores de rentabilidad van a aparecer acá.
      </p>
    </div>
  )

  // Formatea un número como plata en pesos (ej: $1.234,56). Si no hay valor, muestro un guion.
  const fmt = (n) => n != null
    ? `$${Number(n).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '—'

  // Junto los productos que están dando pérdida para avisar arriba de todo.
  const productosNegativos = (data.productKpis || []).filter(p => p.totalNetMargin < 0)

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold text-slate-800">Dashboard de rentabilidad</h2>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Kpi label="Ingreso bruto total" value={fmt(data.totalRevenue)} />
        <Kpi
          label="Margen teórico"
          value={fmt(data.totalTheoreticalMargin)}
          sub={`${data.avgTheoreticalMarginPercent}% sobre ingreso`}
          negative={data.totalTheoreticalMargin < 0}
          tooltip="Lo que ML deposita: ingreso menos comisión, IIBB y logística. No descuenta el costo de compra."
        />
        <Kpi
          label="Margen real"
          value={fmt(data.totalNetMargin)}
          sub={`${data.avgMarginPercent}% sobre ingreso`}
          negative={data.totalNetMargin < 0}
          tooltip="Ganancia efectiva: margen teórico menos el costo de adquisición de la mercadería."
        />
        <Kpi label="Operaciones" value={data.totalOperations} />
      </div>

      {productosNegativos.length > 0 && (
        <div className="p-3 bg-rose-50 border border-rose-200 rounded text-rose-700 text-sm">
          {productosNegativos.length} producto{productosNegativos.length !== 1 ? 's' : ''} con margen neto negativo:{' '}
          {productosNegativos.map(p => p.name).join(', ')}.
          Revisá precios de costo, costos de envío o la categoría asignada.
        </div>
      )}

      {data.marginByPeriod?.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200 p-5">
          <h3 className="font-medium text-slate-800 text-sm mb-4">Margen neto por mes</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={data.marginByPeriod}>
              <CartesianGrid strokeDasharray="3 3" stroke="#eef2f7" vertical={false} />
              <XAxis dataKey="period" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false}
                tickFormatter={v => `$${(v / 1000).toFixed(0)}k`} />
              <Tooltip formatter={(v) => fmt(v)} cursor={{ fill: '#f8fafc' }} />
              <Bar dataKey="margin" radius={[3, 3, 0, 0]} maxBarSize={48}>
                {data.marginByPeriod.map((entry, i) => (
                  <Cell key={i} fill={entry.margin >= 0 ? '#059669' : '#e11d48'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {data.productKpis?.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200">
          <div className="px-5 py-3 border-b border-slate-200">
            <h3 className="font-medium text-slate-800 text-sm">Rentabilidad por producto</h3>
            <p className="text-xs text-slate-400 mt-0.5">Hacé clic en una fila para ver el desglose de costos</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="px-5 py-2.5 font-medium w-6"></th>
                  <th className="px-5 py-2.5 font-medium">Producto</th>
                  <th className="px-5 py-2.5 font-medium text-right">Unidades</th>
                  <th className="px-5 py-2.5 font-medium text-right">Ingreso bruto</th>
                  <th className="px-5 py-2.5 font-medium text-right">Margen neto</th>
                  <th className="px-5 py-2.5 font-medium text-right">% Margen</th>
                </tr>
              </thead>
              <tbody>
                {data.productKpis.map((p, i) => {
                  const isOpen = expanded === p.sku
                  return (
                    <>
                      <tr
                        key={p.sku}
                        onClick={() => toggleRow(p.sku)}
                        className={`border-b border-slate-100 cursor-pointer transition-colors
                          ${isOpen ? 'bg-slate-50' : 'hover:bg-slate-50'}
                          ${p.totalNetMargin < 0 ? 'bg-rose-50 hover:bg-rose-100' : ''}`}
                      >
                        <td className="px-5 py-2.5 text-slate-400 text-xs select-none">
                          {isOpen ? '▲' : '▼'}
                        </td>
                        <td className="px-5 py-2.5 text-slate-800 font-medium">{p.name}</td>
                        <td className="px-5 py-2.5 text-right text-slate-600">{p.totalQuantity}</td>
                        <td className="px-5 py-2.5 text-right text-slate-700">{fmt(p.totalRevenue)}</td>
                        <td className={`px-5 py-2.5 text-right font-medium ${p.totalNetMargin < 0 ? 'text-rose-600' : 'text-emerald-700'}`}>
                          {fmt(p.totalNetMargin)}
                        </td>
                        <td className={`px-5 py-2.5 text-right ${p.marginPercent < 0 ? 'text-rose-600' : 'text-slate-700'}`}>
                          {p.marginPercent != null ? `${p.marginPercent}%` : '—'}
                        </td>
                      </tr>

                      {isOpen && (
                        <tr key={`${p.sku}-detail`} className="border-b border-slate-100 bg-slate-50">
                          <td colSpan={6} className="px-8 py-4">
                            <CostBreakdown p={p} fmt={fmt} />
                          </td>
                        </tr>
                      )}
                    </>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {data.predictions?.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200">
          <div className="px-5 py-3 border-b border-slate-200">
            <h3 className="font-medium text-slate-800 text-sm">Predicción de demanda — próximo mes</h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Estimación basada en el historial de ventas mensual. Actualizá el stock en Productos para activar alertas.
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="px-5 py-2.5 font-medium">Producto</th>
                  <th className="px-5 py-2.5 font-medium text-right">Meses de historial</th>
                  <th className="px-5 py-2.5 font-medium text-right">Demanda estimada</th>
                  <th className="px-5 py-2.5 font-medium text-right">Stock actual</th>
                  <th className="px-5 py-2.5 font-medium">Estado</th>
                </tr>
              </thead>
              <tbody>
                {data.predictions.map((p, i) => {
                  const hasAlert = p.alert
                  const noPrediction = p.predictedDemand == null
                  return (
                    <tr key={i} className={`border-b border-slate-100 last:border-0 ${hasAlert ? 'bg-rose-50' : ''}`}>
                      <td className="px-5 py-2.5 font-medium text-slate-800">
                        {p.productName}
                        {p.salesHistory?.length > 0 && (
                          <div className="mt-1 flex flex-wrap gap-1.5">
                            {p.salesHistory.map((m, j) => (
                              <span key={j} className="inline-flex items-center text-xs font-normal text-slate-500 bg-slate-100 rounded px-1.5 py-0.5">
                                Mes {m.period}: {m.quantity} u.
                              </span>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-2.5 text-right text-slate-500">
                        {p.monthsOfData != null ? p.monthsOfData : '—'}
                        {p.monthsOfData === 1 && (
                          <span className="ml-1 text-xs text-amber-600">(tendencia plana)</span>
                        )}
                      </td>
                      <td className="px-5 py-2.5 text-right font-semibold text-slate-800">
                        {noPrediction ? '—' : `${p.predictedDemand} u.`}
                      </td>
                      <td className="px-5 py-2.5 text-right text-slate-700">
                        {p.currentStock != null ? p.currentStock : 0}
                      </td>
                      <td className="px-5 py-2.5">
                        {noPrediction
                          ? <span className="text-xs text-slate-400">{p.message}</span>
                          : hasAlert
                            ? <span className="inline-flex items-center gap-1 text-xs font-medium text-rose-700 bg-rose-100 px-2 py-0.5 rounded-full">Reponer stock</span>
                            : <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full">Stock suficiente</span>
                        }
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

function Kpi({ label, value, negative, sub, tooltip }) {
  return (
    <div className="bg-white rounded-lg border border-slate-200 p-4" title={tooltip}>
      <div className="text-xs text-slate-500">{label}</div>
      <div className={`text-lg font-semibold mt-1 ${negative ? 'text-rose-600' : 'text-slate-800'}`}>{value}</div>
      {sub && <div className="text-xs text-slate-400 mt-0.5">{sub}</div>}
    </div>
  )
}

function CostBreakdown({ p, fmt }) {
  const pct = (v) => v != null ? `${Number(v).toFixed(2)}%` : '—'

  const rows = [
    {
      label: 'Ingreso bruto',
      value: fmt(p.totalRevenue),
      note: null,
      positive: true,
    },
    {
      label: 'Envío (costo logístico)',
      value: p.totalShipping > 0 ? `–${fmt(p.totalShipping)}` : '$0,00',
      note: p.totalShipping > 0
        ? `$6.080 fijo por venta (precio ≥ $33.000)`
        : 'el comprador paga el envío (precio < $33.000)',
      positive: p.totalShipping === 0,
    },
    {
      label: 'Comisión Mercado Libre',
      value: `–${fmt(p.totalCommission)}`,
      note: `${pct(p.avgCommissionRate)} promedio sobre ingreso`,
      positive: false,
    },
    {
      label: 'IIBB (Ing. Brutos)',
      value: `–${fmt(p.totalIibb)}`,
      note: `${pct(p.avgIibbRate)} promedio según provincia`,
      positive: false,
    },
    {
      label: 'Costo fijo ML por unidad',
      value: p.totalUnitCost > 0 ? `–${fmt(p.totalUnitCost)}` : '$0,00',
      note: p.totalUnitCost > 0
        ? `tramos $1.255/$2.500/$3.030 según precio unitario`
        : 'no aplica (precio ≥ $33.000, el costo es el envío)',
      positive: p.totalUnitCost === 0,
    },
    {
      label: 'Costo de compra',
      value: p.totalProductCost > 0 ? `–${fmt(p.totalProductCost)}` : '$0,00',
      note: p.unitCostPrice > 0
        ? `${fmt(p.unitCostPrice)} unitario × ${p.totalQuantity} u.`
        : 'sin costo de compra cargado — configurá el producto',
      positive: !(p.totalProductCost > 0),
    },
  ]

  const theoreticalMargin = p.totalTheoreticalMargin

  return (
    <div className="max-w-lg">
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">
        Desglose de costos — {p.totalQuantity} {p.totalQuantity === 1 ? 'venta' : 'ventas'}
      </p>
      <div className="space-y-1.5">
        {rows.map((r, i) => (
          <div key={i} className="flex items-baseline justify-between gap-4">
            <div className="flex items-baseline gap-2 min-w-0">
              <span className="text-sm text-slate-700 shrink-0">{r.label}</span>
              {r.note && <span className="text-xs text-slate-400 truncate">{r.note}</span>}
            </div>
            <span className={`text-sm font-medium tabular-nums shrink-0 ${r.positive ? 'text-slate-800' : 'text-slate-600'}`}>
              {r.value}
            </span>
          </div>
        ))}

        {/* Subtotal: margen teórico (lo que deposita ML, sin costo de compra) */}
        <div className="border-t border-slate-200 pt-2 mt-2 flex items-baseline justify-between">
          <div>
            <span className="text-sm font-medium text-slate-600">Margen teórico</span>
            <span className="text-xs text-slate-400 ml-2">lo que deposita ML</span>
          </div>
          <span className={`text-sm font-semibold tabular-nums ${theoreticalMargin < 0 ? 'text-rose-500' : 'text-slate-700'}`}>
            {fmt(theoreticalMargin)}
            {p.theoreticalMarginPercent != null &&
              <span className="text-xs font-normal ml-1.5 text-slate-400">({p.theoreticalMarginPercent}%)</span>
            }
          </span>
        </div>

        {/* Total: margen real (descuenta costo de compra) */}
        <div className="border-t border-slate-300 pt-2 mt-1 flex items-baseline justify-between">
          <div>
            <span className="text-sm font-semibold text-slate-800">Margen real</span>
            <span className="text-xs text-slate-400 ml-2">ganancia efectiva</span>
          </div>
          <span className={`text-sm font-bold tabular-nums ${p.totalNetMargin < 0 ? 'text-rose-600' : 'text-emerald-700'}`}>
            {fmt(p.totalNetMargin)}
            <span className="text-xs font-normal ml-1.5 text-slate-500">({p.marginPercent}%)</span>
          </span>
        </div>
      </div>
    </div>
  )
}
