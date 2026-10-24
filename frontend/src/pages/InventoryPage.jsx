import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loader2, Plus, Search } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { useInventory } from '../hooks/useInventory'
import { formatKsh } from '../lib/money'
import { UNITS, formatQty } from '../lib/units'
import Alert from '../ui/Alert'
import { buttonClass } from '../ui/Button'
import Segmented from '../ui/Segmented'

export default function InventoryPage() {
    const { selectedShop } = useAuth()
    const { rows, loading, error } = useInventory(selectedShop?.id)
    const [params, setParams] = useSearchParams()
    const [query, setQuery] = useState('')

    const lowOnly = params.get('low') === '1'
    const canManage = selectedShop?.role === 'OWNER'
    const lowCount = rows.filter((r) => r.lowStock).length

    const visible = useMemo(() => {
        const needle = query.trim().toLowerCase()
        return rows.filter(
            (r) => (!needle || r.name.toLowerCase().includes(needle)) && (!lowOnly || r.lowStock),
        )
    }, [rows, query, lowOnly])

    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>

    return (
        <div>
            <div className="flex flex-wrap items-end justify-between gap-4">
                <div>
                    <h1 className="font-display text-4xl font-medium tracking-tight">Stock</h1>
                    <p className="mt-2 text-ink-soft">{selectedShop.name}</p>
                </div>
                {canManage && (
                    <Link to="/stock/new" className={buttonClass()}>
                        <Plus className="h-4 w-4" aria-hidden="true" /> Add item
                    </Link>
                )}
            </div>

            <div className="mt-8 space-y-3">
                <div className="relative">
                    <label htmlFor="stock-search" className="sr-only">Search items</label>
                    <Search
                        aria-hidden="true"
                        className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-faint"
                    />
                    <input
                        id="stock-search"
                        type="search"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="Search items"
                        className="h-12 w-full rounded-xl border border-line-strong bg-surface pl-11 pr-4 text-base text-ink placeholder:text-ink-faint focus:border-forest-600 focus:outline-none focus:ring-4 focus:ring-forest-600/15"
                    />
                </div>
                <Segmented
                    label="Filter"
                    value={lowOnly ? 'low' : 'all'}
                    onChange={(v) => setParams(v === 'low' ? { low: '1' } : {})}
                    options={[
                        { value: 'all', label: 'All items' },
                        { value: 'low', label: `Low stock (${lowCount})` },
                    ]}
                />
            </div>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}

            {loading ? (
                <div className="mt-12 flex justify-center">
                    <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
                </div>
            ) : rows.length === 0 ? (
                <p className="mt-8 border-y border-line py-8 text-ink-soft">
                    {canManage ? 'No items yet. Add your first item to start counting stock.' : 'No items yet. The owner has not added any.'}
                </p>
            ) : visible.length === 0 ? (
                <p className="mt-8 border-y border-line py-8 text-ink-soft">Nothing matches.</p>
            ) : (
                <ul className="mt-6 divide-y divide-line border-y border-line">
                    {visible.map((r) => (
                        <li key={r.itemId}>
                            <Link
                                to={`/stock/${r.itemId}`}
                                className="flex items-center justify-between gap-4 py-4 transition-colors hover:bg-ink/[0.03] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-forest-600"
                            >
                <span className="min-w-0">
                  <span className="block truncate font-medium">{r.name}</span>
                  <span className="block text-sm text-ink-soft">
                    {r.priceMinor === null ? 'No price yet' : `${formatKsh(r.priceMinor)} per ${UNITS[r.unit].short}`}
                  </span>
                </span>
                                <span className="shrink-0 text-right">
                  <span className={`block font-medium tabular-nums ${r.lowStock ? 'text-ledger' : ''}`}>
                    {formatQty(r.onHand, r.unit)}
                  </span>
                                    {r.lowStock && <span className="block text-xs font-medium text-ledger">Low stock</span>}
                </span>
                            </Link>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}