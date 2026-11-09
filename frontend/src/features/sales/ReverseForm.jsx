import { useState } from 'react'
import api, { errorMessage } from '../../api/client'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

const CHIPS = ['Wrong quantity', 'Wrong item', 'Customer changed mind']

export default function ReverseForm({ shopId, saleId, onDone }) {
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
            await api.post(`/shops/${shopId}/sales/${saleId}/reverse`, { reason: text })
            await onDone()
        } catch (err) {
            if (err.response?.data?.code === 'ALREADY_REVERSED') {
                await onDone() // someone already did it: just show the current state
            } else {
                setError(errorMessage(err, 'Could not reverse the sale. Nothing was changed.'))
            }
        } finally {
            setBusy(false)
        }
    }

    if (!open) {
        return (
            <div className="no-print">
                <Button variant="quiet" onClick={() => setOpen(true)} className="border border-line-strong">
                    Reverse this sale
                </Button>
            </div>
        )
    }

    return (
        <form onSubmit={handleSubmit} className="no-print max-w-md space-y-4 rounded-2xl border border-line bg-surface p-5">
            <h2 className="font-display text-2xl font-medium">Reverse this sale</h2>
            <p className="text-sm text-ink-soft">
                The items go back into stock and the sale is marked as reversed. It stays in your records.
            </p>
            <div className="flex flex-wrap gap-1.5">
                {CHIPS.map((chip) => (
                    <button key={chip} type="button" onClick={() => setReason(chip)}
                            className="h-9 rounded-lg border border-line-strong px-3 text-sm font-medium text-ink-soft hover:bg-ink/5">
                        {chip}
                    </button>
                ))}
            </div>
            <Field id="reverse-reason" label="Reason" maxLength={200} value={reason}
                   onChange={(e) => setReason(e.target.value)} />
            {error && <Alert>{error}</Alert>}
            <div className="flex gap-3">
                <Button type="submit" variant="danger" loading={busy}>Reverse sale</Button>
                <Button type="button" variant="quiet" onClick={() => setOpen(false)}>Cancel</Button>
            </div>
        </form>
    )
}