import { useCallback, useEffect, useRef, useState } from 'react'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../../api/client'
import { formatBusinessDate, formatDateTime } from '../../lib/dates'
import { formatKsh, minorToInput, parseKshToMinor } from '../../lib/money'
import { METHODS, methodLabel } from '../../lib/sales'
import { UNITS, formatQty, parseQty } from '../../lib/units'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'
import Notice from '../../ui/Notice'
import Segmented from '../../ui/Segmented'

const DISPOSITIONS = [
    { value: 'RESTOCK', label: 'Back on the shelf' },
    { value: 'WRITE_OFF', label: 'Damaged, thrown away' },
]

function RefundHistory({ refunds }) {
    return (
        <ul className="divide-y divide-line border-y border-line">
            {refunds.map((r) => (
                <li key={r.id} className="py-4">
                    <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                            <p className="font-medium">Refund of {formatKsh(r.totalMinor)}</p>
                            <p className="text-sm text-ink-soft">
                                {formatBusinessDate(r.businessDate)}, by {r.refundedByName}. {r.reason}
                            </p>
                        </div>
                        <p className="shrink-0 text-sm text-ink-soft">{formatDateTime(r.createdAt)}</p>
                    </div>
                    <ul className="mt-2 text-sm text-ink-soft">
                        {r.lines.map((l) => (
                            <li key={l.saleLineId}>
                                {formatQty(l.quantity, l.unit)} {l.name}, {formatKsh(l.lineRefundMinor)}.{' '}
                                {l.disposition === 'RESTOCK' ? 'Back on the shelf.' : 'Thrown away.'}
                            </li>
                        ))}
                    </ul>
                    <p className="mt-1 text-sm text-ink-soft">
                        {r.debtCreditMinor > 0 && `${formatKsh(r.debtCreditMinor)} taken off what the customer owed. `}
                        {r.payouts.length > 0 &&
                            `Paid back: ${r.payouts.map((p) => `${methodLabel(p.method)} ${formatKsh(p.amountMinor)}`).join(', ')}.`}
                    </p>
                </li>
            ))}
        </ul>
    )
}

function RefundForm({ shop, options, openDay, onDone }) {
    const [qty, setQty] = useState({})
    const [disp, setDisp] = useState({})
    const [reason, setReason] = useState('')
    const [pay, setPay] = useState({ CASH: '', POCHI: '', TILL: '' })
    const [payTouched, setPayTouched] = useState(false)
    const [quote, setQuote] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [done, setDone] = useState('')
    const attempt = useRef({ key: null, sig: '' })

    // Which lines are being refunded, and is each quantity usable
    const rows = options.lines.map((l) => {
        const text = (qty[l.saleLineId] ?? '').trim()
        if (!text) return { line: l, selected: false }
        const decimals = UNITS[l.unit].decimals
        const parsed = parseQty(text, decimals)
        let problem = null
        if (parsed === null) problem = decimals === 0 ? 'Enter a whole number.' : 'Enter a quantity, like 0.5.'
        else if (parsed > Number(l.refundableQuantity)) problem = `Only ${formatQty(l.refundableQuantity, l.unit)} can still be refunded.`
        return { line: l, selected: true, parsed, problem }
    })
    const selected = rows.filter((r) => r.selected)
    const hasProblem = selected.some((r) => r.problem)
    const lines = selected.filter((r) => !r.problem).map((r) => ({
        saleLineId: r.line.saleLineId,
        quantity: r.parsed,
        disposition: disp[r.line.saleLineId] ?? 'RESTOCK',
    }))
    const linesKey = JSON.stringify(lines)

    // The server does the refund arithmetic. This only shows its answer.
    useEffect(() => {
        if (hasProblem || lines.length === 0) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setQuote(null)
            return undefined
        }
        let cancelled = false
        const timer = setTimeout(async () => {
            try {
                const { data } = await api.post(`/shops/${shop.id}/sales/${options.saleId}/refunds/quote`, { lines })
                if (!cancelled) {
                    setQuote(data)
                    setError('')
                }
            } catch (err) {
                if (!cancelled) {
                    setQuote(null)
                    setError(errorMessage(err, 'Could not work out the refund.'))
                }
            }
        }, 300)
        return () => {
            cancelled = true
            clearTimeout(timer)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [linesKey, hasProblem, shop.id, options.saleId])

    // Until the owner edits the boxes, everything to pay back is cash
    const payout = quote?.payoutMinor ?? 0
    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        if (!payTouched) setPay({ CASH: payout > 0 ? minorToInput(payout) : '', POCHI: '', TILL: '' })
    }, [payout, payTouched])

    const amounts = {}
    let amountsInvalid = false
    for (const m of METHODS) {
        const text = pay[m.value].trim()
        const value = text === '' ? 0 : parseKshToMinor(text)
        if (value === null) amountsInvalid = true
        amounts[m.value] = value ?? 0
    }
    const paidBack = Object.values(amounts).reduce((a, b) => a + b, 0)
    const matches = quote && !amountsInvalid && paidBack === payout
    const canSubmit = Boolean(openDay) && quote && !hasProblem && reason.trim().length >= 3 && matches && !submitting

    function handlePay(method, text) {
        setPayTouched(true)
        setPay((p) => ({ ...p, [method]: text }))
    }

    function fillRest(method) {
        const remaining = payout - (paidBack - amounts[method])
        if (remaining > 0) {
            setPayTouched(true)
            setPay((p) => ({ ...p, [method]: minorToInput(remaining) }))
        }
    }

    async function handleSubmit(event) {
        event.preventDefault()
        if (!canSubmit) return
        setError('')
        setDone('')
        const payouts = METHODS.filter((m) => amounts[m.value] > 0).map((m) => ({ method: m.value, amountMinor: amounts[m.value] }))
        const body = { lines, payouts, reason: reason.trim() }
        const sig = JSON.stringify(body)
        if (!attempt.current.key || attempt.current.sig !== sig) attempt.current = { key: crypto.randomUUID(), sig }

        setSubmitting(true)
        try {
            const { data } = await api.post(`/shops/${shop.id}/sales/${options.saleId}/refunds`, body, {
                headers: { 'Idempotency-Key': attempt.current.key },
                timeout: 20000,
            })
            attempt.current = { key: null, sig: '' }
            setDone(`Refund of ${formatKsh(data.totalMinor)} recorded.`)
            setQty({})
            setDisp({})
            setReason('')
            setPayTouched(false)
            await onDone()
        } catch (err) {
            const code = err.response?.data?.code
            if (!err.response || err.response.status >= 500) {
                setError('We could not confirm that the refund was saved. Tap Record refund again. It will not be recorded twice.')
            } else {
                attempt.current = { key: null, sig: '' }
                setError(errorMessage(err, 'The refund was not recorded.'))
                if (['PAYOUT_MISMATCH', 'REFUND_EXCEEDS_SOLD', 'SALE_REVERSED', 'SALE_DAY_OPEN'].includes(code)) await onDone()
            }
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="mt-8 max-w-md space-y-6">
            <h3 className="font-display text-xl font-medium">Refund items</h3>
            <p className="text-sm text-ink-soft">
                For goods the customer brought back after the day was closed. The refund is recorded on today’s book. The
                original sale stays as it was.
            </p>

            <ul className="divide-y divide-line border-y border-line">
                {rows.map(({ line, selected, problem }) => (
                    <li key={line.saleLineId} className="py-4">
                        <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                                <p className="font-medium">{line.name}</p>
                                <p className="text-sm text-ink-soft">
                                    Sold {formatQty(line.soldQuantity, line.unit)} at {formatKsh(line.unitPriceMinor)}.
                                    {Number(line.refundedQuantity) > 0 && ` ${formatQty(line.refundedQuantity, line.unit)} already refunded.`}
                                </p>
                            </div>
                        </div>
                        {Number(line.refundableQuantity) > 0 ? (
                            <div className="mt-3 space-y-3">
                                <Field id={`rq-${line.saleLineId}`} label={`How many came back (${UNITS[line.unit].short})`}
                                       inputMode="decimal" placeholder={`Up to ${formatQty(line.refundableQuantity, line.unit)}`}
                                       value={qty[line.saleLineId] ?? ''}
                                       onChange={(e) => setQty((q) => ({ ...q, [line.saleLineId]: e.target.value }))} />
                                {selected && !problem && (
                                    <Segmented label={`What happens to the ${line.name}`} value={disp[line.saleLineId] ?? 'RESTOCK'}
                                               onChange={(v) => setDisp((d) => ({ ...d, [line.saleLineId]: v }))} options={DISPOSITIONS} />
                                )}
                                {problem && <p role="alert" className="text-sm text-ledger">{problem}</p>}
                            </div>
                        ) : (
                            <p className="mt-2 text-sm text-ink-soft">All of this has been refunded.</p>
                        )}
                    </li>
                ))}
            </ul>

            {quote && (
                <div className="space-y-2">
                    <div className="flex items-baseline justify-between">
                        <span className="text-ink-soft">Refund total</span>
                        <span className="font-display text-3xl font-medium tabular-nums">{formatKsh(quote.totalMinor)}</span>
                    </div>
                    {quote.debtCreditMinor > 0 && (
                        <p className="text-sm text-ink-soft">
                            {formatKsh(quote.debtCreditMinor)} comes off what the customer still owes on this sale.
                        </p>
                    )}
                    <p className="text-sm font-medium">
                        {quote.payoutMinor > 0 ? `Pay back ${formatKsh(quote.payoutMinor)} to the customer.` : 'Nothing needs to be paid back.'}
                    </p>
                </div>
            )}

            {quote && quote.payoutMinor > 0 && (
                <div className="space-y-3">
                    {METHODS.map((m) => (
                        <div key={m.value} className="flex items-end gap-2">
                            <div className="min-w-0 flex-1">
                                <Field id={`rp-${m.value}`} label={`Paid back by ${m.label}`} inputMode="decimal" placeholder="0"
                                       value={pay[m.value]} onChange={(e) => handlePay(m.value, e.target.value)} />
                            </div>
                            <button type="button" onClick={() => fillRest(m.value)}
                                    className="h-12 shrink-0 rounded-xl px-3 text-sm font-medium text-forest-700 hover:bg-forest-50">
                                Fill rest
                            </button>
                        </div>
                    ))}
                    <p role="status" className={`text-sm font-medium ${matches ? 'text-forest-700' : 'text-ledger'}`}>
                        {amountsInvalid ? 'Check the amounts.' : matches ? 'Payments match what is to be paid back.'
                            : paidBack < payout ? `${formatKsh(payout - paidBack)} still to be paid back.` : `${formatKsh(paidBack - payout)} too much.`}
                    </p>
                    <p className="text-sm text-ink-soft">Only cash comes out of the drawer.</p>
                </div>
            )}

            <Field id="refund-reason" label="Reason" maxLength={200} placeholder="e.g. Customer returned damaged sugar"
                   value={reason} onChange={(e) => setReason(e.target.value)} />

            {!openDay && (
                <Alert>Open today’s book on the Home page first. A refund is recorded on today’s book.</Alert>
            )}
            {error && <Alert>{error}</Alert>}
            {done && <Notice>{done}</Notice>}

            <Button type="submit" variant="danger" loading={submitting} disabled={!canSubmit}>Record refund</Button>
        </form>
    )
}

export default function RefundPanel({ shop, sale, openDay }) {
    const [options, setOptions] = useState(null)
    const [error, setError] = useState('')

    const load = useCallback(async () => {
        try {
            const { data } = await api.get(`/shops/${shop.id}/sales/${sale.id}/refunds`)
            setOptions(data)
            setError('')
        } catch (err) {
            setError(errorMessage(err, 'Could not load refunds.'))
        }
    }, [shop.id, sale.id])

    useEffect(() => {
        load()
    }, [load])

    if (!options) {
        return error ? <div className="no-print mt-8"><Alert>{error}</Alert></div> : (
            <div className="no-print mt-8"><Loader2 className="h-5 w-5 animate-spin text-forest-600" aria-label="Loading" /></div>
        )
    }

    const isOwner = shop.role === 'OWNER'
    const hasRefunds = options.refunds.length > 0
    if (!options.refundable && !hasRefunds) return null
    const anythingLeft = options.lines.some((l) => Number(l.refundableQuantity) > 0)

    return (
        <section className="no-print mt-12">
            <h2 className="font-display text-2xl font-medium">Refunds</h2>
            {hasRefunds && <div className="mt-4"><RefundHistory refunds={options.refunds} /></div>}
            {options.refundable && anythingLeft && isOwner && (
                <RefundForm shop={shop} options={options} openDay={openDay} onDone={load} />
            )}
            {options.refundable && anythingLeft && !isOwner && (
                <p className="mt-4 max-w-md text-sm text-ink-soft">
                    If goods came back after the day was closed, ask the owner to record the refund.
                </p>
            )}
        </section>
    )
}