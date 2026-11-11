import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { formatShortDate } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { formatPhone } from '../lib/phone'
import Alert from '../ui/Alert'
import Button from '../ui/Button'

function Stat({ label, children, danger }) {
    return (
        <div>
            <dt className="text-sm text-ink-soft">{label}</dt>
            <dd className={`mt-1 font-display text-2xl font-medium tabular-nums ${danger ? 'text-ledger' : ''}`}>{children}</dd>
        </div>
    )
}

function DeniLedger({ shop }) {
    const [items, setItems] = useState([])
    const [meta, setMeta] = useState(null)
    const [loading, setLoading] = useState(true)
    const [loadingMore, setLoadingMore] = useState(false)
    const [error, setError] = useState('')
    const latest = useRef(0)

    const load = useCallback(async (page) => {
        const id = ++latest.current
        if (page === 0) setLoading(true)
        else setLoadingMore(true)
        try {
            const { data } = await api.get(`/shops/${shop.id}/debts`, { params: { page, size: 20 } })
            if (id !== latest.current) return
            setItems((prev) => (page === 0 ? data.items : [...prev, ...data.items]))
            setMeta(data)
            setError('')
        } catch (err) {
            if (id !== latest.current) return
            setError(errorMessage(err, 'Could not load deni.'))
        } finally {
            if (id === latest.current) {
                setLoading(false)
                setLoadingMore(false)
            }
        }
    }, [shop.id])

    useEffect(() => {
        load(0)
    }, [load])

    const hasMore = meta && meta.page + 1 < meta.totalPages

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">Deni</h1>
            <p className="mt-2 text-ink-soft">Everyone who owes {shop.name}’s business, earliest due date first.</p>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}

            {loading ? (
                <div className="mt-12 flex justify-center">
                    <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
                </div>
            ) : meta && meta.customerCount === 0 ? (
                <p className="mt-8 border-y border-line py-8 text-ink-soft">
                    Nobody owes anything. Deni sales will appear here.
                </p>
            ) : meta ? (
                <>
                    <dl className="mt-8 grid grid-cols-3 gap-6 border-y border-line py-6">
                        <Stat label="Owed">{formatKsh(meta.totalOutstandingMinor)}</Stat>
                        <Stat label="Overdue" danger={meta.totalOverdueMinor > 0}>{formatKsh(meta.totalOverdueMinor)}</Stat>
                        <Stat label="Customers">{meta.customerCount}</Stat>
                    </dl>

                    <ul className="mt-4 divide-y divide-line border-b border-line">
                        {items.map((c) => (
                            <li key={c.customerId}>
                                <Link to={`/deni/${c.customerId}`}
                                      className="flex items-center justify-between gap-4 py-4 transition-colors hover:bg-ink/[0.03] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-forest-600">
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{c.name}</span>
                    <span className="block text-sm text-ink-soft">{formatPhone(c.phone)}</span>
                    <span className={`block text-sm ${c.overdueMinor > 0 ? 'font-medium text-ledger' : 'text-ink-soft'}`}>
                      {c.overdueMinor > 0 ? `${formatKsh(c.overdueMinor)} overdue` : `Next due ${formatShortDate(c.nextDueDate)}`}
                    </span>
                  </span>
                                    <span className="shrink-0 text-right">
                    <span className="block font-medium tabular-nums">{formatKsh(c.outstandingMinor)}</span>
                                        {c.overLimit && <span className="block text-xs font-medium text-ledger">Over limit</span>}
                  </span>
                                </Link>
                            </li>
                        ))}
                    </ul>

                    {hasMore && (
                        <div className="mt-6">
                            <Button variant="quiet" loading={loadingMore} onClick={() => load(meta.page + 1)}
                                    className="border border-line-strong">
                                Show more
                            </Button>
                        </div>
                    )}
                </>
            ) : null}
        </div>
    )
}

export default function DeniPage() {
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <DeniLedger key={selectedShop.id} shop={selectedShop} />
}