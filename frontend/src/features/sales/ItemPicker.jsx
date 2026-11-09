import { useMemo, useState } from 'react'
import { Plus, Search } from 'lucide-react'
import { formatKsh } from '../../lib/money'
import { UNITS, formatQty } from '../../lib/units'

const LIMIT = 30

function reasonNotSellable(row) {
    if (row.priceMinor === null) return 'No price yet. Ask the owner.'
    if (Number(row.onHand) <= 0) return 'Out of stock.'
    return null
}

export default function ItemPicker({ rows, inCart, onAdd }) {
    const [query, setQuery] = useState('')
    const needle = query.trim().toLowerCase()

    const matches = useMemo(
        () =>
            needle
                ? rows.filter((r) => r.name.toLowerCase().includes(needle))
                : rows.filter((r) => !reasonNotSellable(r)), // with no search, show only what can be sold
        [rows, needle],
    )
    const shown = matches.slice(0, LIMIT)

    function handleAdd(row) {
        onAdd(row)
        setQuery('')
    }

    return (
        <div>
            <div className="relative">
                <label htmlFor="item-search" className="sr-only">Search items</label>
                <Search
                    aria-hidden="true"
                    className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-faint"
                />
                <input
                    id="item-search"
                    type="search"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Search items"
                    autoComplete="off"
                    className="h-12 w-full rounded-xl border border-line-strong bg-surface pl-11 pr-4 text-base text-ink placeholder:text-ink-faint focus:border-forest-600 focus:outline-none focus:ring-4 focus:ring-forest-600/15"
                />
            </div>

            {rows.length === 0 ? (
                <p className="mt-6 border-y border-line py-8 text-ink-soft">This shop has no items yet. The owner adds them in Stock.</p>
            ) : shown.length === 0 ? (
                <p className="mt-6 border-y border-line py-8 text-ink-soft">
                    {needle ? 'Nothing matches.' : 'Nothing is ready to sell. Items need a price and stock.'}
                </p>
            ) : (
                <ul className="mt-4 divide-y divide-line border-y border-line">
                    {shown.map((r) => {
                        const blocked = reasonNotSellable(r)
                        const qty = inCart[r.itemId]
                        return (
                            <li key={r.itemId}>
                                <button
                                    type="button"
                                    disabled={Boolean(blocked)}
                                    onClick={() => handleAdd(r)}
                                    className="flex w-full items-center justify-between gap-4 py-4 text-left transition-colors hover:bg-ink/[0.03] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-forest-600 disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:bg-transparent"
                                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{r.name}</span>
                    <span className="block text-sm text-ink-soft">
                      {blocked ?? `${formatKsh(r.priceMinor)} per ${UNITS[r.unit].short}`}
                    </span>
                  </span>
                                    <span className="flex shrink-0 items-center gap-3">
                    {!blocked && (
                        <span className={`text-sm tabular-nums ${r.lowStock ? 'text-ledger' : 'text-ink-soft'}`}>
                        {formatQty(r.onHand, r.unit)} left
                      </span>
                    )}
                                        {qty ? (
                                            <span className="rounded-full bg-forest-100 px-2.5 py-1 text-sm font-medium tabular-nums text-forest-800">
                        {qty} in sale
                      </span>
                                        ) : (
                                            !blocked && (
                                                <span className="flex h-9 w-9 items-center justify-center rounded-full border border-line-strong text-forest-700">
                          <Plus className="h-4 w-4" aria-hidden="true" />
                        </span>
                                            )
                                        )}
                  </span>
                                </button>
                            </li>
                        )
                    })}
                </ul>
            )}
            {matches.length > LIMIT && (
                <p className="mt-3 text-sm text-ink-soft">Showing {LIMIT} of {matches.length}. Type more to narrow it down.</p>
            )}
        </div>
    )
}