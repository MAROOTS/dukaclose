import { useCallback, useEffect, useRef, useState } from 'react'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import MoneyForm from '../features/money/MoneyForm'
import ReverseForm from '../features/sales/ReverseForm'
import { useShopDay } from '../hooks/useShopDay'
import { formatBusinessDate, formatTime } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { categoryLabel } from '../lib/moneyCategories'
import { methodLabel } from '../lib/sales'
import Alert from '../ui/Alert'
import Button from '../ui/Button'
import Field from '../ui/Field'

function MoneyBook({ shop }) {
    const { me } = useAuth()
    const { status } = useShopDay(shop.id)
    const [date, setDate] = useState('') // '' means the shop's current day
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
            const { data } = await api.get(`/shops/${shop.id}/money-movements`, {
                params: { ...(date ? { date } : {}), page, size: 20 },
            })
            if (id !== latest.current) return
            setItems((prev) => (page === 0 ? data.items : [...prev, ...data.items]))
            setMeta(data)
            setError('')
        } catch (err) {
            if (id !== latest.current) return
            setError(errorMessage(err, 'Could not load the money book.'))
        } finally {
            if (id === latest.current) {
                setLoading(false)
                setLoadingMore(false)
            }
        }
    }, [shop.id, date])

    useEffect(() => {
        load(0)
    }, [load])

    const openDate = status?.openDay?.businessDate ?? null
    const canAdd = Boolean(openDate) && (date === '' || date === openDate)
    const hasMore = meta && meta.page + 1 < meta.totalPages

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">Money in and out</h1>
            <p className="mt-2 text-ink-soft">{shop.name}</p>

            <section className="mt-10">
                <h2 className="font-display text-2xl font-medium">Record money</h2>
                <div className="mt-4">
                    {status && !openDate ? (
                        <p className="max-w-md text-ink-soft">Open today’s book on the Home page before recording money.</p>
                    ) : canAdd ? (
                        <MoneyForm shopId={shop.id} onDone={() => load(0)} />
                    ) : (
                        <p className="max-w-md text-ink-soft">You are looking at another day. Switch to today to record money.</p>
                    )}
                </div>
            </section>

            <section className="mt-12">
                <h2 className="font-display text-2xl font-medium">The book</h2>
                <div className="mt-4 max-w-xs">
                    <Field id="money-date" label="Day" type="date" value={date || meta?.date || ''}
                           onChange={(e) => setDate(e.target.value)} />
                </div>

                {error && <div className="mt-6"><Alert>{error}</Alert></div>}

                {loading ? (
                    <div className="mt-10 flex justify-center">
                        <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
                    </div>
                ) : items.length === 0 ? (
                    <p className="mt-6 border-y border-line py-8 text-ink-soft">
                        No money recorded on {meta ? formatBusinessDate(meta.date) : 'this day'}.
                    </p>
                ) : (
                    <>
                        <ul className="mt-6 divide-y divide-line border-y border-line">
                            {items.map((m) => {
                                const reversed = Boolean(m.reversal)
                                const incoming = m.direction === 'IN'
                                const mayReverse =
                                    !reversed &&
                                    Boolean(openDate) &&
                                    m.businessDate === openDate &&
                                    (shop.role === 'OWNER' || m.createdById === me.user.id)
                                return (
                                    <li key={m.id} className="py-4">
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="min-w-0">
                                                <p className="font-medium">{categoryLabel(m.category)}</p>
                                                <p className="text-sm text-ink-soft">{m.note}</p>
                                                <p className="text-sm text-ink-soft">
                                                    {formatTime(m.createdAt)}, {m.createdByName}, {methodLabel(m.method)}
                                                </p>
                                            </div>
                                            <div className="shrink-0 text-right">
                                                <p className={`font-medium tabular-nums ${
                                                    reversed ? 'text-ink-faint line-through' : incoming ? 'text-forest-700' : ''
                                                }`}>
                                                    {incoming ? '+' : '−'}{formatKsh(m.amountMinor)}
                                                </p>
                                                {reversed && <p className="text-xs font-medium text-ledger">Reversed</p>}
                                            </div>
                                        </div>
                                        {reversed && (
                                            <p className="mt-2 text-sm text-ink-soft">
                                                Reversed: {m.reversal.reason}. {m.reversal.reversedByName}.
                                            </p>
                                        )}
                                        {mayReverse && (
                                            <ReverseForm
                                                compact
                                                noun="entry"
                                                url={`/shops/${shop.id}/money-movements/${m.id}/reverse`}
                                                onDone={() => load(0)}
                                            />
                                        )}
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
            </section>
        </div>
    )
}

export default function MoneyPage() {
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <MoneyBook key={selectedShop.id} shop={selectedShop} />
}