import { useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { REASONS } from '../../lib/stock'
import { UNITS, formatQty, parseQty } from '../../lib/units'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field, { SelectField } from '../../ui/Field'
import Notice from '../../ui/Notice'
import Segmented from '../../ui/Segmented'

export default function AdjustForm({ shopId, item, onDone }) {
    const unit = UNITS[item.unit]
    const [reason, setReason] = useState('DAMAGED')
    const [chosenDirection, setChosenDirection] = useState('remove')
    const [amount, setAmount] = useState('')
    const [note, setNote] = useState('')
    const [requestId, setRequestId] = useState(() => crypto.randomUUID())
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [done, setDone] = useState('')

    const reasonInfo = REASONS.find((r) => r.value === reason)
    const direction = reasonInfo.direction === 'either' ? chosenDirection : reasonInfo.direction

    async function handleSubmit(event) {
        event.preventDefault()
        if (submitting) return
        setError('')
        setDone('')

        const quantity = parseQty(amount, unit.decimals)
        if (quantity === null) {
            setError(`Enter a quantity in ${unit.plural}${unit.decimals === 0 ? ' (whole numbers)' : ''}.`)
            return
        }
        if (direction === 'remove' && quantity > Number(item.onHand)) {
            setError(`Only ${formatQty(item.onHand, item.unit)} on hand.`)
            return
        }

        setSubmitting(true)
        try {
            const { data } = await api.post(`/shops/${shopId}/inventory/${item.itemId}/adjustments`, {
                requestId,
                quantityDelta: direction === 'remove' ? -quantity : quantity,
                reason,
                note: note.trim() || null,
            })
            setDone(`Saved. On hand is now ${formatQty(data.balanceAfter, item.unit)}.`)
            setRequestId(crypto.randomUUID())
            setAmount('')
            setNote('')
            await onDone()
        } catch (err) {
            setError(errorMessage(err, 'Could not save. Nothing was recorded.'))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="space-y-5">
            <p className="text-sm text-ink-soft">
                For stock that left or appeared outside a normal sale or delivery. On hand now: {formatQty(item.onHand, item.unit)}.
            </p>

            <SelectField id="adjust-reason" label="Reason" value={reason} onChange={(e) => setReason(e.target.value)}>
                {REASONS.map((r) => (
                    <option key={r.value} value={r.value}>{r.label}</option>
                ))}
            </SelectField>

            {reasonInfo.direction === 'either' && (
                <Segmented
                    label="Direction"
                    value={chosenDirection}
                    onChange={setChosenDirection}
                    options={[
                        { value: 'remove', label: 'Take away' },
                        { value: 'add', label: 'Add' },
                    ]}
                />
            )}

            <Field
                id="adjust-amount"
                label={`${direction === 'remove' ? 'How much to take away' : 'How much to add'} (${unit.short})`}
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
            />

            <Field
                id="adjust-note"
                label="Note, optional"
                maxLength={200}
                placeholder="e.g. Sack tore open"
                value={note}
                onChange={(e) => setNote(e.target.value)}
            />

            {error && <Alert>{error}</Alert>}
            {done && <Notice>{done}</Notice>}

            <Button type="submit" loading={submitting}>Save adjustment</Button>
        </form>
    )
}