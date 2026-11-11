import { useRef, useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { IN_CATEGORY, OUT_CATEGORIES } from '../../lib/moneyCategories'
import { formatKsh, parseKshToMinor } from '../../lib/money'
import { METHODS } from '../../lib/sales'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field, { SelectField } from '../../ui/Field'
import Notice from '../../ui/Notice'
import Segmented from '../../ui/Segmented'

export default function MoneyForm({ shopId, onDone }) {
    const [direction, setDirection] = useState('OUT')
    const [category, setCategory] = useState('TRANSPORT')
    const [method, setMethod] = useState('CASH')
    const [amount, setAmount] = useState('')
    const [note, setNote] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [done, setDone] = useState('')
    // One key per attempt, reused if the connection drops with an unknown outcome
    const key = useRef(null)

    const info = direction === 'IN' ? IN_CATEGORY : OUT_CATEGORIES.find((c) => c.value === category)
    const cashOnly = Boolean(info.cashOnly)
    const effectiveMethod = cashOnly ? 'CASH' : method

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
        if (note.trim().length < 3) {
            setError('Add a short note saying what this was for.')
            return
        }

        setSubmitting(true)
        if (!key.current) key.current = crypto.randomUUID()
        try {
            await api.post(
                `/shops/${shopId}/money-movements`,
                { category: info.value, method: effectiveMethod, amountMinor, note: note.trim() },
                { headers: { 'Idempotency-Key': key.current }, timeout: 20000 },
            )
            key.current = null
            setDone(`${direction === 'IN' ? 'Money in' : 'Money out'} recorded: ${formatKsh(amountMinor)}.`)
            setAmount('')
            setNote('')
            await onDone()
        } catch (err) {
            const code = err.response?.data?.code
            const httpStatus = err.response?.status
            if (!err.response || httpStatus >= 500) {
                setError('We could not confirm that this was saved. Tap Record again. It will not be recorded twice.')
            } else if (code === 'IDEMPOTENCY_CONFLICT') {
                key.current = null
                setError('An earlier attempt may already have been saved. Check the list below before recording again.')
                await onDone()
            } else {
                key.current = null // a definite refusal: nothing was saved
                setError(errorMessage(err, 'Nothing was recorded.'))
            }
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="max-w-md space-y-5">
            <Segmented label="Direction" value={direction} onChange={setDirection}
                       options={[{ value: 'OUT', label: 'Money out' }, { value: 'IN', label: 'Money in' }]} />

            {direction === 'OUT' && (
                <SelectField id="money-category" label="What for?" value={category} onChange={(e) => setCategory(e.target.value)}>
                    {OUT_CATEGORIES.map((c) => (
                        <option key={c.value} value={c.value}>{c.label}</option>
                    ))}
                </SelectField>
            )}
            {info.hint && <p className="-mt-2 text-sm text-ink-soft">{info.hint}</p>}

            {cashOnly ? (
                <p className="text-sm font-medium text-ink">Paid in cash</p>
            ) : (
                <div>
                    <Segmented label="Paid how?" value={method} onChange={setMethod} options={METHODS} />
                    <p className="mt-2 text-sm text-ink-soft">
                        {method === 'CASH' ? 'This cash leaves the drawer.' : 'This does not come out of the drawer.'}
                    </p>
                </div>
            )}

            <Field id="money-amount" label="Amount (KSh)" inputMode="decimal" placeholder="e.g. 500"
                   value={amount} onChange={(e) => setAmount(e.target.value)} />
            <Field id="money-note" label="Note" maxLength={200} placeholder="What was it for?"
                   value={note} onChange={(e) => setNote(e.target.value)} />

            {error && <Alert>{error}</Alert>}
            {done && <Notice>{done}</Notice>}

            <Button type="submit" loading={submitting}>Record</Button>
        </form>
    )
}