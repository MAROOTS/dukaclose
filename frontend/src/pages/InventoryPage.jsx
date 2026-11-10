import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
    AlertTriangle,
    Boxes,
    ChevronRight,
    Loader2,
    Package,
    Plus,
    Search,
    Upload,
} from 'lucide-react'

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
            (r) =>
                (!needle || r.name.toLowerCase().includes(needle)) &&
                (!lowOnly || r.lowStock),
        )
    }, [rows, query, lowOnly])

    if (!selectedShop) {
        return (
            <div className="flex min-h-[300px] items-center justify-center">
                <div className="text-center">
                    <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-surface">
                        <Package className="h-6 w-6 text-ink-soft" />
                    </div>
                    <p className="mt-4 font-medium text-ink">
                        No shop selected
                    </p>
                    <p className="mt-1 text-sm text-ink-soft">
                        Choose a shop to view its inventory.
                    </p>
                </div>
            </div>
        )
    }

    return (
        <div className="mx-auto max-w-6xl space-y-7 pb-10">

            {/* Page header */}
            <header className="relative overflow-hidden rounded-3xl border border-line bg-surface p-6 sm:p-8">
                <div className="pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-forest-600/[0.06] blur-3xl" />

                <div className="relative flex flex-col justify-between gap-6 sm:flex-row sm:items-center">
                    <div className="min-w-0">
                        <div className="mb-4 inline-flex items-center gap-2   bg-white/60 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.16em] text-forest-600">

                            Inventory management
                        </div>

                        <h1 className="font-display text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
                            Stock
                        </h1>

                        <p className="mt-2 truncate text-sm text-ink-soft sm:text-base">
                            {selectedShop.name}
                        </p>

                        <p className="mt-1 max-w-md text-sm text-ink-faint">
                            Keep track of your products, quantities and stock levels.
                        </p>
                    </div>

                    {canManage && (
                        <div className="flex shrink-0 flex-wrap gap-3">
                            <Link
                                to="/stock/import"
                                className={`${buttonClass('quiet')} h-11 gap-2 rounded-xl border border-line-strong px-4 transition-all hover:border-forest-600/40 hover:bg-surface`}
                            >
                                <Upload className="h-4 w-4" aria-hidden="true" />
                                Import
                            </Link>

                            <Link
                                to="/stock/new"
                                className={`${buttonClass()} h-11 gap-2 rounded-xl px-5 shadow-sm transition-all hover:shadow-md`}
                            >
                                <Plus className="h-4 w-4" aria-hidden="true" />
                                Add item
                            </Link>
                        </div>
                    )}
                </div>
            </header>

            {/* Inventory summary */}
            <section className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="flex items-center justify-between rounded-2xl border border-line bg-surface p-5 shadow-sm shadow-ink/[0.02]">
                    <div>
                        <p className="text-sm font-medium text-ink-soft">
                            Total items
                        </p>
                        <p className="mt-2 font-display text-3xl font-semibold tracking-tight tabular-nums text-ink">
                            {rows.length}
                        </p>
                        <p className="mt-1 text-xs text-ink-faint">
                            Products in your inventory
                        </p>
                    </div>

                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-forest-600/[0.09]">
                        <Boxes className="h-6 w-6 text-forest-600" />
                    </div>
                </div>

                <div className="flex items-center justify-between rounded-2xl border border-line bg-surface p-5 shadow-sm shadow-ink/[0.02]">
                    <div>
                        <p className="text-sm font-medium text-ink-soft">
                            Low stock
                        </p>
                        <p className="mt-2 font-display text-3xl font-semibold tracking-tight tabular-nums text-ink">
                            {lowCount}
                        </p>
                        <p className="mt-1 text-xs text-ink-faint">
                            Items that need attention
                        </p>
                    </div>

                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-ledger/[0.09]">
                        <AlertTriangle className="h-6 w-6 text-ledger" />
                    </div>
                </div>
            </section>

            {/* Search and filters */}
            <section className="space-y-4 rounded-2xl border border-line bg-surface p-4 sm:p-5">
                <div>
                    <h2 className="font-semibold text-ink">
                        Your inventory
                    </h2>
                    <p className="mt-1 text-sm text-ink-soft">
                        Search and manage products in your shop.
                    </p>
                </div>

                <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                    <div className="relative min-w-0 flex-1">
                        <label htmlFor="stock-search" className="sr-only">
                            Search items
                        </label>

                        <Search
                            aria-hidden="true"
                            className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-faint"
                        />

                        <input
                            id="stock-search"
                            type="search"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder="Search by product name..."
                            className="h-12 w-full rounded-xl border border-line-strong bg-surface pl-11 pr-4 text-sm text-ink outline-none transition-all placeholder:text-ink-faint hover:border-ink-faint focus:border-forest-600 focus:ring-4 focus:ring-forest-600/10"
                        />
                    </div>

                    <div className="shrink-0">
                        <Segmented
                            label="Filter"
                            value={lowOnly ? 'low' : 'all'}
                            onChange={(v) =>
                                setParams(v === 'low' ? { low: '1' } : {})
                            }
                            options={[
                                { value: 'all', label: 'All items' },
                                { value: 'low', label: `Low stock (${lowCount})` },
                            ]}
                        />
                    </div>
                </div>
            </section>

            {/* Error message */}
            {error && (
                <div>
                    <Alert>{error}</Alert>
                </div>
            )}

            {/* Inventory list */}
            <section>
                {!loading && rows.length > 0 && (
                    <div className="mb-4 flex items-center justify-between gap-3">
                        <p className="text-sm text-ink-soft">
                            Showing{' '}
                            <span className="font-semibold text-ink">
                                {visible.length}
                            </span>{' '}
                            of {rows.length} items
                        </p>

                        {lowOnly && (
                            <span className="inline-flex items-center gap-1.5 rounded-full bg-ledger/[0.09] px-3 py-1.5 text-xs font-semibold text-ledger">
                                <AlertTriangle className="h-3.5 w-3.5" />
                                Low stock only
                            </span>
                        )}
                    </div>
                )}

                {loading ? (
                    <div className="flex min-h-[240px] flex-col items-center justify-center rounded-2xl border border-line bg-surface">
                        <Loader2
                            className="h-7 w-7 animate-spin text-forest-600"
                            aria-label="Loading"
                        />
                        <p className="mt-3 text-sm text-ink-soft">
                            Loading your inventory...
                        </p>
                    </div>
                ) : rows.length === 0 ? (
                    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-line-strong bg-surface px-5 py-16 text-center">
                        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-forest-600/[0.08]">
                            <Package className="h-8 w-8 text-forest-600" />
                        </div>

                        <h3 className="mt-5 font-semibold text-ink">
                            Your inventory is empty
                        </h3>

                        <p className="mt-2 max-w-sm text-sm leading-6 text-ink-soft">
                            {canManage
                                ? 'Start by adding your first product to keep track of your shop stock.'
                                : 'The shop owner has not added any inventory items yet.'}
                        </p>

                        {canManage && (
                            <Link
                                to="/stock/new"
                                className={`${buttonClass()} mt-6 gap-2 rounded-xl px-5`}
                            >
                                <Plus className="h-4 w-4" />
                                Add your first item
                            </Link>
                        )}
                    </div>
                ) : visible.length === 0 ? (
                    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-line-strong bg-surface px-5 py-14 text-center">
                        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-ink/[0.04]">
                            <Search className="h-6 w-6 text-ink-soft" />
                        </div>

                        <h3 className="mt-4 font-semibold text-ink">
                            No matching items
                        </h3>

                        <p className="mt-1 text-sm text-ink-soft">
                            Try another search term or change your filter.
                        </p>

                        <button
                            type="button"
                            onClick={() => {
                                setQuery('')
                                setParams({})
                            }}
                            className="mt-4 text-sm font-semibold text-forest-600 transition-colors hover:text-forest-600/75"
                        >
                            Clear search and filters
                        </button>
                    </div>
                ) : (
                    <ul className="space-y-3">
                        {visible.map((r) => (
                            <li key={r.itemId}>
                                <Link
                                    to={`/stock/${r.itemId}`}
                                    className="group flex items-center justify-between gap-3 rounded-2xl border border-line bg-surface p-4 shadow-sm shadow-ink/[0.02] transition-all duration-200 hover:-translate-y-0.5 hover:border-forest-600/30 hover:shadow-md hover:shadow-ink/[0.05] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600 sm:gap-5 sm:p-5"
                                >
                                    {/* Product details */}
                                    <div className="flex min-w-0 items-center gap-3 sm:gap-4">
                                        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-forest-600/[0.07] transition-colors group-hover:bg-forest-600/[0.12] sm:h-12 sm:w-12">
                                            <Package className="h-5 w-5 text-forest-600" />
                                        </div>

                                        <div className="min-w-0">
                                            <p className="truncate font-semibold text-ink transition-colors group-hover:text-forest-600">
                                                {r.name}
                                            </p>

                                            <p className="mt-1 truncate text-xs text-ink-soft sm:text-sm">
                                                {r.priceMinor === null
                                                    ? 'No price set'
                                                    : `${formatKsh(r.priceMinor)} per ${UNITS[r.unit].short}`}
                                            </p>

                                            {r.lowStock && (
                                                <span className="mt-2 inline-flex items-center gap-1 rounded-full bg-ledger/[0.09] px-2.5 py-1 text-[11px] font-semibold text-ledger">
                                                    <AlertTriangle className="h-3 w-3" />
                                                    Low stock
                                                </span>
                                            )}
                                        </div>
                                    </div>

                                    {/* Stock quantity */}
                                    <div className="flex shrink-0 items-center gap-2 sm:gap-4">
                                        <div className="text-right">
                                            <p className="text-[10px] font-semibold uppercase tracking-wider text-ink-faint">
                                                On hand
                                            </p>

                                            <p
                                                className={`mt-1 font-display text-lg font-semibold tabular-nums sm:text-xl ${
                                                    r.lowStock
                                                        ? 'text-ledger'
                                                        : 'text-ink'
                                                }`}
                                            >
                                                {formatQty(r.onHand, r.unit)}
                                            </p>
                                        </div>

                                        <ChevronRight className="h-5 w-5 shrink-0 text-ink-faint transition-all group-hover:translate-x-0.5 group-hover:text-forest-600" />
                                    </div>
                                </Link>
                            </li>
                        ))}
                    </ul>
                )}
            </section>
        </div>
    )
}