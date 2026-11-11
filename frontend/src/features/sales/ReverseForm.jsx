import { useId, useState } from 'react'
import api, { errorMessage } from '../../api/client'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

const COPY = {
    sale: {
        chips: ['Wrong quantity', 'Wrong item', 'Customer changed mind'],
        text: 'The items go back into stock and the sale is marked as reversed. It stays in your records.',
    },
    repayment: {
        chips: ['Wrong amount', 'Wrong customer', 'Wrong payment method'],
        text: 'The payment is cancelled and the customer owes that amount again. It stays in your records.',
    },
    entry: {
        chips: ['Wrong amount', 'Entered twice', 'Wrong category'],
        text: 'The entry is cancelled and no longer counts. It stays in your records.',
    },
}

export default function ReverseForm({ url, noun = 'sale', onDone, compact = false }) {
    const id = useId()
    const copy = COPY[noun]
    const [open, setOpen] = useState(false)
    const [reason, setReason] = useState('')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')

    async function handleSubmit(event) {
        event.preventDefault()
        if (busy) return
        const text = reason.trim()
        if (text.length < 3) {
            setError('Say why, in a few words.')
            return
        }
        setError('')
        setBusy(true)
        try {
            await api.post(url, { reason: text })
            await onDone()
        } catch (err) {
            if (err.response?.data?.code === 'ALREADY_REVERSED') {
                await onDone() // someone already did it: just show the current state
            } else {
                setError(errorMessage(err, 'Could not reverse it. Nothing was changed.'))
            }
        } finally {
            setBusy(false)
        }
    }

    if (!open) {
        return compact ? (
            <button type="button" onClick={() => setOpen(true)}
                    className="no-print h-9 rounded-lg px-3 text-sm font-medium text-ledger hover:bg-ledger-50">
                Reverse
            </button>
        ) : (
            <div className="no-print">
                <Button variant="quiet" onClick={() => setOpen(true)} className="border border-line-strong">
                    Reverse this {noun}
                </Button>
            </div>
        )
    }

    return (
        <form onSubmit={handleSubmit}
              className="no-print mt-3 max-w-md space-y-4 rounded-2xl border border-line bg-surface p-5">
            <h2 className="font-display text-2xl font-medium">Reverse this {noun}</h2>
            <p className="text-sm text-ink-soft">{copy.text}</p>
            <div className="flex flex-wrap gap-1.5">
                {copy.chips.map((chip) => (
                    <button key={chip} type="button" onClick={() => setReason(chip)}
                            className="h-9 rounded-lg border border-line-strong px-3 text-sm font-medium text-ink-soft hover:bg-ink/5">
                        {chip}
                    </button>
                ))}
            </div>
            <Field id={`${id}-reason`} label="Reason" maxLength={200} value={reason}
                   onChange={(e) => setReason(e.target.value)} />
            {error && <Alert>{error}</Alert>}
            <div className="flex gap-3">
                <Button type="submit" variant="danger" loading={busy}>Reverse {noun}</Button>
                <Button type="button" variant="quiet" onClick={() => setOpen(false)}>Cancel</Button>
            </div>
        </form>
    )
}