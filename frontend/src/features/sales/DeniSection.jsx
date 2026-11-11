import { useEffect, useRef, useState } from 'react'
import { Search, UserRound } from 'lucide-react'
import api from '../../api/client'
import { addDays, formatShortDate } from '../../lib/dates'
import { formatKsh } from '../../lib/money'
import { formatPhone } from '../../lib/phone'
import Alert from '../../ui/Alert'
import Field from '../../ui/Field'

const DUE_CHIPS = [[7, '1 week'], [14, '2 weeks'], [30, '1 month']]

function CustomerPicker({ shopId, customer, onCustomer, deniMinor }) {
    const [query, setQuery] = useState('')
    const [results, setResults] = useState([])
    const [searching, setSearching] = useState(false)
    const [searchError, setSearchError] = useState('')
    const latest = useRef(0)

    useEffect(() => {
        const q = query.trim()
        if (q.length < 2) {
            setResults([])
            setSearching(false)
            return undefined
        }
        const id = ++latest.current
        setSearching(true)
        const timer = setTimeout(async () => {
            try {
                const { data } = await api.get(`/shops/${shopId}/customers`, { params: { q } })
                if (id === latest.current) {
                    setResults(data)
                    setSearchError('')
                }
            } catch {
                if (id === latest.current) setSearchError('Could not search customers. You can still add a new one.')
            } finally {
                if (id === latest.current) setSearching(false)
            }
        }, 300)
        return () => clearTimeout(timer)
    }, [query, shopId])

    if (customer?.mode === 'existing') {
        const c = customer.customer
        const after = c.outstandingMinor + deniMinor
        const overLimit = c.creditLimitMinor !== null && c.creditLimitMinor !== undefined && after > c.creditLimitMinor
        return (
            <div className="space-y-3">
                <div className="flex items-start justify-between gap-3 rounded-xl border border-line-strong bg-surface p-4">
                    <div className="min-w-0">
                        <p className="font-medium">{c.name}</p>
                        <p className="text-sm text-ink-soft">{formatPhone(c.phone)}</p>
                        <p className="mt-1 text-sm text-ink-soft">
                            {c.outstandingMinor > 0 ? `Already owes ${formatKsh(c.outstandingMinor)}` : 'Owes nothing at the moment'}
                        </p>
                    </div>
                    <button type="button" onClick={() => onCustomer(null)}
                            className="shrink-0 rounded-lg px-3 py-2 text-sm font-medium text-forest-700 hover:bg-forest-50">
                        Change
                    </button>
                </div>
                {overLimit && (
                    <Alert>
                        This takes {c.name} to {formatKsh(after)}, above the limit of {formatKsh(c.creditLimitMinor)}.
                        You can still record the sale.
                    </Alert>
                )}
            </div>
        )
    }

    if (customer?.mode === 'new') {
        return (
            <div className="space-y-4">
                <Field id="new-customer-name" label="Customer name" maxLength={120} value={customer.name}
                       onChange={(e) => onCustomer({ ...customer, name: e.target.value })} />
                <Field id="new-customer-phone" label="Phone number" type="tel" inputMode="tel" placeholder="0712 345 678"
                       hint="If this number is already a customer, their saved name is kept."
                       value={customer.phone} onChange={(e) => onCustomer({ ...customer, phone: e.target.value })} />
                <button type="button" onClick={() => onCustomer(null)}
                        className="rounded-lg px-3 py-2 text-sm font-medium text-forest-700 hover:bg-forest-50">
                    Search for an existing customer instead
                </button>
            </div>
        )
    }

    const q = query.trim()
    const looksLikePhone = /^[\d\s+\-()]+$/.test(q)
    return (
        <div>
            <Field id="customer-search" label="Customer" icon={Search} type="search" autoComplete="off"
                   placeholder="Search by name or phone" value={query} onChange={(e) => setQuery(e.target.value)} />
            {searchError && <p className="mt-2 text-sm text-ledger">{searchError}</p>}
            {results.length > 0 && (
                <ul className="mt-3 divide-y divide-line border-y border-line">
                    {results.map((c) => (
                        <li key={c.id}>
                            <button type="button" onClick={() => onCustomer({ mode: 'existing', customer: c })}
                                    className="flex w-full items-center justify-between gap-3 py-3 text-left hover:bg-ink/[0.03]">
                <span className="min-w-0">
                  <span className="block truncate font-medium">{c.name}</span>
                  <span className="block text-sm text-ink-soft">{formatPhone(c.phone)}</span>
                </span>
                                <span className="shrink-0 text-right text-sm">
                  {c.outstandingMinor > 0 ? (
                      <span className={c.overLimit ? 'font-medium text-ledger' : 'text-ink-soft'}>
                      Owes {formatKsh(c.outstandingMinor)}
                    </span>
                  ) : (
                      <span className="text-ink-faint">Owes nothing</span>
                  )}
                                    {c.overLimit && <span className="block text-xs font-medium text-ledger">Over limit</span>}
                </span>
                            </button>
                        </li>
                    ))}
                </ul>
            )}
            {q.length >= 2 && !searching && results.length === 0 && !searchError && (
                <p className="mt-3 text-sm text-ink-soft">No customer found.</p>
            )}
            <button type="button"
                    onClick={() => onCustomer({ mode: 'new', name: looksLikePhone ? '' : q, phone: looksLikePhone ? q : '' })}
                    className="mt-3 inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-forest-700 hover:bg-forest-50">
                <UserRound className="h-4 w-4" aria-hidden="true" /> Add a new customer
            </button>
        </div>
    )
}

export default function DeniSection({ shopId, customer, onCustomer, dueDate, onDueDate, today, deniMinor }) {
    const max = today ? addDays(today, 365) : undefined
    return (
        <div className="mt-8 space-y-6 rounded-2xl border border-line bg-surface p-5">
            <div>
                <h3 className="font-display text-xl font-medium">Who owes {formatKsh(deniMinor)}?</h3>
                <p className="mt-1 text-sm text-ink-soft">Deni needs the customer and a due date.</p>
            </div>

            <CustomerPicker shopId={shopId} customer={customer} onCustomer={onCustomer} deniMinor={deniMinor} />

            <div>
                <Field id="deni-due" label="Due date" type="date" min={today} max={max} value={dueDate}
                       onChange={(e) => onDueDate(e.target.value)}
                       hint={dueDate ? `Due ${formatShortDate(dueDate)}` : undefined} />
                <div className="mt-2 flex flex-wrap gap-1.5">
                    {DUE_CHIPS.map(([days, label]) => (
                        <button key={days} type="button" onClick={() => onDueDate(addDays(today, days))}
                                className={`h-9 rounded-lg border px-3 text-sm font-medium ${
                                    dueDate === addDays(today, days)
                                        ? 'border-forest-600 bg-forest-50 text-forest-800'
                                        : 'border-line-strong text-ink-soft hover:bg-ink/5'
                                }`}>
                            {label}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    )
}