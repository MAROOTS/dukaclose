import { useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../../api/client'
import { formatShortDate } from '../../lib/dates'
import { formatKsh, minorToInput, parseKshToMinor } from '../../lib/money'
import { formatReceiptNo, METHODS } from '../../lib/sales'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field, { SelectField } from '../../ui/Field'
import Notice from '../../ui/Notice'
import Segmented from '../../ui/Segmented'

export default function CollectForm({ shopId, customerId, customerName, debts, outstandingMinor, dayOpen, onDone }) {
    const open = useMemo(
        () => debts
            .filter((d) => !d.voided && d.balanceMinor > 0)
            .sort((a, b) => a.dueDate.localeCompare(b.dueDate) || a.createdAt.localeCompare(b.createdAt)),
        [debts],
    )
    const [method, setMethod] = useState('CASH')
    const [amount, setAmount] = useState('')
    const [debtId, setDebtId] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [done, setDone] = useState('')
    // One key per attempt, reused if the connection drops with an unknown outcome
    const key = useRef(null)

    const chosen = open.find((d) => d.debtId === debtId)
    const target = chosen ? chosen.balanceMinor : outstandingMinor

    if (!dayOpen) {
        return (
            <p className="max-w-md text-ink-soft">
                Open today’s book to collect payments. <Link to="/" className="font-medium text-forest-700 underline">Go to Home</Link>
            </p>
        )
    }

    async function handleSubmit(event) {
        event.preventDefault()
        if (submitting) return
        setError('')
        setDone('')

        const amountMinor = parseKshToMinor(amount)
        if (amountMinor === null || amountMinor <= 0) {
            setError('Enter the amount, like 500 or 500.50.')
            return
        }
        if (amountMinor > target) {
            setError(`${chosen ? 'That deni' : customerName} owes only ${formatKsh(target)}.`)
            return
        }

        setSubmitting(true)
        if (!key.current) key.current = crypto.randomUUID()
        try {
            const { data } = await api.post(
                `/shops/${shopId}/debt-payments`,
                { customerId, method, amountMinor, debtId: debtId || null },
                { headers: { 'Idempotency-Key': key.current }, timeout: 20000 },
            )
            key.current = null
            setDone(`Collected ${formatKsh(data.amountMinor)}. ${customerName} now owes ${formatKsh(data.customerOutstandingMinor)}.`)
            setAmount('')
            setDebtId('')
            await onDone()
        } catch (err) {
            const code = err.response?.data?.code
            const httpStatus = err.response?.status
            if (!err.response || httpStatus >= 500) {
                setError('We could not confirm that the payment was saved. Tap Collect again. It will not be recorded twice.')
            } else if (code === 'IDEMPOTENCY_CONFLICT') {
                key.current = null
                setError('An earlier attempt may already have been saved. Check the payments below before collecting again.')
                await onDone()
            } else {
                key.current = null // a definite refusal: nothing was saved
                setError(errorMessage(err, 'The payment was not recorded.'))
                if (code === 'OVERPAYMENT' || code === 'DEBT_NOT_FOUND') await onDone()
            }
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="max-w-md space-y-5">
            <div>
                <Segmented label="How was it paid?" value={method} onChange={setMethod} options={METHODS} />
                <p className="mt-2 text-sm text-ink-soft">
                    {method === 'CASH' ? 'This cash goes into the drawer.' : 'This does not go into the drawer.'}
                </p>
            </div>

            <div className="flex items-end gap-2">
                <div className="min-w-0 flex-1">
                    <Field id="collect-amount" label="Amount (KSh)" inputMode="decimal" placeholder="e.g. 500"
                           value={amount} onChange={(e) => setAmount(e.target.value)} />
                </div>
                <button type="button" onClick={() => setAmount(minorToInput(target))}
                        className="h-12 shrink-0 rounded-xl px-3 text-sm font-medium text-forest-700 hover:bg-forest-50">
                    Pay in full
                </button>
            </div>

            {open.length > 1 && (
                <SelectField id="collect-debt" label="Apply to" value={debtId} onChange={(e) => setDebtId(e.target.value)}>
                    <option value="">Oldest due first (recommended)</option>
                    {open.map((d) => (
                        <option key={d.debtId} value={d.debtId}>
                            {formatReceiptNo(d.receiptNo)}, due {formatShortDate(d.dueDate)}, owes {formatKsh(d.balanceMinor)}
                        </option>
                    ))}
                </SelectField>
            )}

            {error && <Alert>{error}</Alert>}
            {done && <Notice>{done}</Notice>}

            <Button type="submit" loading={submitting}>Collect</Button>
        </form>
    )
}