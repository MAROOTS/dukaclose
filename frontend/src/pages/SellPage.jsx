import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import DeniSection from '../features/sales/DeniSection'
import ItemPicker from '../features/sales/ItemPicker'
import PayStage from '../features/sales/PayStage'
import Receipt, { ReceiptActions } from '../features/sales/Receipt'
import { useCart } from '../hooks/useCart'
import { useInventory } from '../hooks/useInventory'
import { useShopDay } from '../hooks/useShopDay'
import { addDays, formatBusinessDate } from '../lib/dates'
import { formatKsh, minorToInput, parseKshToMinor } from '../lib/money'
import { isValidPhone } from '../lib/phone'
import { describeLines, SALE_METHODS, sumLines } from '../lib/sales'
import Alert from '../ui/Alert'
import Button, { buttonClass } from '../ui/Button'
import Notice from '../ui/Notice'

const EMPTY_PAY = { CASH: '', POCHI: '', TILL: '', DENI: '' }

function Spinner() {
    return (
        <div className="flex justify-center pt-16">
            <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
        </div>
    )
}

function Sell({ shop }) {
    const { rows, loading: stockLoading, error: stockError, reload } = useInventory(shop.id)
    const { status, loading: dayLoading, error: dayError } = useShopDay(shop.id)
    const cart = useCart(shop.id)

    const [stage, setStage] = useState('items') // items | pay | done
    const [pay, setPay] = useState(EMPTY_PAY)
    const [touched, setTouched] = useState(false)
    const [received, setReceived] = useState('')
    const [customer, setCustomer] = useState(null) // null | { mode: 'existing', customer } | { mode: 'new', name, phone }
    const [dueDate, setDueDate] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [conflict, setConflict] = useState(false)
    const [notice, setNotice] = useState('')
    const [done, setDone] = useState(null)
    // Reused on retries so one sale can never be recorded twice. Cleared only on a definite outcome.
    const pendingKey = useRef(null)

    const byId = useMemo(() => new Map(rows.map((r) => [r.itemId, r])), [rows])

    // Items that vanished from the catalogue (archived) cannot stay in the sale
    const { lines: cartLines, removeMany } = cart
    useEffect(() => {
        if (stockLoading || stockError) return
        const missing = cartLines.filter((l) => !byId.has(l.itemId))
        if (missing.length > 0) {
            removeMany(missing.map((l) => l.itemId))
            setNotice(missing.length === 1
                ? 'An item is no longer available and was removed from the sale.'
                : `${missing.length} items are no longer available and were removed from the sale.`)
        }
    }, [stockLoading, stockError, byId, cartLines, removeMany])

    const described = useMemo(
        () => describeLines(cartLines.filter((l) => byId.has(l.itemId)), byId),
        [cartLines, byId],
    )
    const total = sumLines(described)
    const inCart = Object.fromEntries(cartLines.map((l) => [l.itemId, l.qty]))

    // Until the cashier edits the payment boxes, "all cash" follows the total
    useEffect(() => {
        if (!touched) setPay({ ...EMPTY_PAY, CASH: total ? minorToInput(total) : '' })
    }, [total, touched])

    useEffect(() => {
        if (stage === 'pay' && cartLines.length === 0) setStage('items')
    }, [stage, cartLines.length])

    const amounts = {}
    let amountsInvalid = false
    for (const m of SALE_METHODS) {
        const text = pay[m.value].trim()
        const value = text === '' ? 0 : parseKshToMinor(text)
        if (value === null) amountsInvalid = true
        amounts[m.value] = value ?? 0
    }
    const paid = Object.values(amounts).reduce((a, b) => a + b, 0)

    // Deni needs a customer and a due date
    const deniMinor = amounts.DENI
    const today = status?.today ?? ''
    const due = dueDate || (today ? addDays(today, 14) : '')
    const customerReady =
        customer?.mode === 'existing' ||
        (customer?.mode === 'new' && customer.name.trim().length > 0 && isValidPhone(customer.phone))
    const dueValid = Boolean(today) && due >= today && due <= addDays(today, 365)
    const deniOk = deniMinor === 0 || (customerReady && dueValid)

    const canConfirm = !submitting && !amountsInvalid && total !== null && total > 0 && paid === total && deniOk

    function handlePay(method, text) {
        setTouched(true)
        setPay((p) => ({ ...p, [method]: text }))
    }

    function handleRest(method) {
        if (total === null) return
        const remaining = total - (paid - amounts[method])
        if (remaining > 0) {
            setTouched(true)
            setPay((p) => ({ ...p, [method]: minorToInput(remaining) }))
        }
    }

    function startNewSale() {
        cart.clear()
        setStage('items')
        setDone(null)
        setTouched(false)
        setReceived('')
        setCustomer(null)
        setDueDate('')
        setError('')
        setConflict(false)
        pendingKey.current = null
    }

    async function confirm() {
        if (!canConfirm) return
        setError('')
        setSubmitting(true)
        if (!pendingKey.current) pendingKey.current = crypto.randomUUID()

        const body = {
            lines: described.map((d) => ({ itemId: d.itemId, quantity: d.qty })),
            allocations: SALE_METHODS.filter((m) => amounts[m.value] > 0)
                .map((m) => ({ method: m.value, amountMinor: amounts[m.value] })),
        }
        if (deniMinor > 0) {
            body.customer = customer.mode === 'existing'
                ? { name: customer.customer.name, phone: customer.customer.phone }
                : { name: customer.name.trim(), phone: customer.phone }
            body.dueDate = due
        }

        try {
            const { data } = await api.post(`/shops/${shop.id}/sales`, body, {
                headers: { 'Idempotency-Key': pendingKey.current },
                timeout: 20000,
            })
            pendingKey.current = null
            cart.clear()
            setDone(data)
            setStage('done')
            reload() // stock changed
        } catch (err) {
            const code = err.response?.data?.code
            const httpStatus = err.response?.status
            if (!err.response || httpStatus >= 500) {
                // We cannot know whether it was saved. Keep the key: pressing Confirm again is safe.
                setError('We could not confirm that the sale was saved. Tap Confirm again. It will not be recorded twice.')
            } else if (code === 'IDEMPOTENCY_CONFLICT') {
                setConflict(true)
            } else {
                pendingKey.current = null // a definite refusal: nothing was saved
                if (code === 'ALLOCATION_MISMATCH') {
                    setError('A price changed while you were selling. The total has been updated. Check the payments.')
                    setTouched(false)
                    reload()
                } else {
                    setError(errorMessage(err, 'The sale was not recorded.'))
                    if (['INSUFFICIENT_STOCK', 'NO_PRICE', 'ITEM_NOT_FOUND'].includes(code)) reload()
                }
            }
        } finally {
            setSubmitting(false)
        }
    }

    if (stockLoading || dayLoading) return <Spinner />

    if (stage === 'done' && done) {
        return (
            <div>
                <h1 className="font-display text-4xl font-medium tracking-tight">Sale recorded</h1>
                {done.deni?.overLimit && (
                    <div className="mt-6 max-w-md">
                        <Alert>
                            {done.deni.customerName} now owes {formatKsh(done.deni.customerOutstandingMinor)}, above the limit
                            of {formatKsh(done.deni.creditLimitMinor)}.
                        </Alert>
                    </div>
                )}
                <div className="mt-6 max-w-md"><Receipt sale={done} shopName={shop.name} /></div>
                <div className="no-print mt-6 flex flex-wrap gap-3">
                    <Button onClick={startNewSale}>New sale</Button>
                    <ReceiptActions sale={done} shopName={shop.name} />
                    <Link to={`/sales/${done.id}`} className={`${buttonClass('quiet')} border border-line-strong`}>View in sales</Link>
                </div>
            </div>
        )
    }

    if (!status) return <Alert>{dayError || 'Could not load today’s book.'}</Alert>

    if (!status.openDay) {
        return (
            <div className="max-w-md">
                <h1 className="font-display text-4xl font-medium tracking-tight">Open today’s book first</h1>
                <p className="mt-3 text-ink-soft">
                    Sales are recorded against an open day. Count the drawer and open the day on the Home page.
                </p>
                <Link to="/" className={`${buttonClass()} mt-6`}>Go to Home</Link>
            </div>
        )
    }

    const day = status.openDay

    return (
        <div>
            {stage === 'pay' ? (
                <button type="button" onClick={() => setStage('items')}
                        className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-soft hover:text-ink">
                    <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Add more items
                </button>
            ) : null}
            <h1 className={`font-display text-4xl font-medium tracking-tight ${stage === 'pay' ? 'mt-4' : ''}`}>
                {stage === 'pay' ? 'Review and pay' : 'New sale'}
            </h1>
            <p className="mb-6 mt-2 text-ink-soft">{shop.name}</p>

            {day.stale && (
                <div className="mb-6">
                    <Alert>
                        This book is from {formatBusinessDate(day.businessDate)} and was never closed.
                        Sales you record now go into that day.
                    </Alert>
                </div>
            )}
            {notice && <div className="mb-6"><Notice>{notice}</Notice></div>}
            {stockError && <div className="mb-6"><Alert>{stockError}</Alert></div>}

            {stage === 'items' && (
                <>
                    <ItemPicker rows={rows} inCart={inCart} onAdd={(row) => cart.add(row.itemId)} />
                    {described.length > 0 && (
                        <div className="sticky bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-10 mt-6 lg:bottom-6">
                            <button type="button" onClick={() => setStage('pay')}
                                    className="flex w-full items-center justify-between rounded-2xl bg-forest-700 px-5 py-4 text-white shadow-lg hover:bg-forest-800">
                                <span className="font-semibold">Review sale ({described.length})</span>
                                <span className="font-display text-xl tabular-nums">{total === null ? '' : formatKsh(total)}</span>
                            </button>
                        </div>
                    )}
                </>
            )}

            {stage === 'pay' && (
                <>
                    {conflict && (
                        <div className="mb-6 space-y-3">
                            <Alert>
                                An earlier attempt of this sale may already have been saved. Check today’s sales before selling again.
                            </Alert>
                            <div className="flex flex-wrap gap-3">
                                <Link to="/sales" className={`${buttonClass('quiet')} border border-line-strong`}>Check today’s sales</Link>
                                <Button variant="quiet" onClick={startNewSale}>Start over</Button>
                            </div>
                        </div>
                    )}
                    <PayStage
                        lines={described}
                        total={total}
                        pay={pay}
                        amounts={amounts}
                        amountsInvalid={amountsInvalid}
                        paid={paid}
                        received={received}
                        onReceived={setReceived}
                        onQty={cart.setQty}
                        onRemove={cart.remove}
                        onPay={handlePay}
                        onRest={handleRest}
                        canConfirm={canConfirm}
                        submitting={submitting}
                        onConfirm={confirm}
                        error={error}
                        blocked={conflict}
                        deniMissing={deniMinor > 0 && !deniOk}
                        deniSection={
                            <DeniSection
                                shopId={shop.id}
                                customer={customer}
                                onCustomer={setCustomer}
                                dueDate={due}
                                onDueDate={setDueDate}
                                today={today}
                                deniMinor={deniMinor}
                            />
                        }
                    />
                </>
            )}
        </div>
    )
}

export default function SellPage() {
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <Sell key={selectedShop.id} shop={selectedShop} />
}