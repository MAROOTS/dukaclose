import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { differenceInfo, toneClass } from '../lib/closing'
import { formatBusinessDate, formatTime } from '../lib/dates'
import { formatKsh } from '../lib/money'
import Alert from '../ui/Alert'
import Button from '../ui/Button'

function Closes({ shop }) {
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
            const { data } = await api.get(`/shops/${shop.id}/closes`, { params: { page, size: 20 } })
            if (id !== latest.current) return
            setItems((prev) => (page === 0 ? data.items : [...prev, ...data.items]))
            setMeta(data)
            setError('')
        } catch (err) {
            if (id !== latest.current) return
            setError(errorMessage(err, 'Could not load past days.'))
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
            <h1 className="font-display text-4xl font-medium tracking-tight">Past days</h1>
            <p className="mt-2 text-ink-soft">{shop.name}, newest first.</p>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}

            {loading ? (
                <div className="mt-12 flex justify-center">
                    <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
                </div>
            ) : items.length === 0 ? (
                <p className="mt-8 border-y border-line py-8 text-ink-soft">No day has been closed yet.</p>
            ) : (
                <>
                    <ul className="mt-8 divide-y divide-line border-y border-line">
                        {items.map((c) => {
                            const diff = differenceInfo(c.differenceMinor)
                            return (
                                <li key={c.id}>
                                    <Link to={`/closes/${c.businessDate}`}
                                          className="flex items-center justify-between gap-4 py-4 transition-colors hover:bg-ink/[0.03] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-forest-600">
                    <span className="min-w-0">
                      <span className="block font-medium">{formatBusinessDate(c.businessDate)}</span>
                      <span className="block text-sm text-ink-soft">Closed {formatTime(c.closedAt)} by {c.closedByName}</span>
                        {c.note && <span className="block truncate text-sm text-ink-soft">{c.note}</span>}
                    </span>
                                        <span className="shrink-0 text-right">
                      <span className="block font-medium tabular-nums">{formatKsh(c.salesTotalMinor)}</span>
                      <span className={`block text-sm font-medium ${toneClass(diff.tone)}`}>{diff.short}</span>
                    </span>
                                    </Link>
                                </li>
                            )
                        })}
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
            )}
        </div>
    )
}

export default function ClosesPage() {
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <Closes key={selectedShop.id} shop={selectedShop} />
}