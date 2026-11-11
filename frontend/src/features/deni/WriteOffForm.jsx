import { useId, useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { formatKsh, parseKshToMinor } from '../../lib/money'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

const CHIPS = ['Customer cannot pay', 'Customer moved away', 'Settled for less']

export default function WriteOffForm({ businessId, debt, onDone }) {
    const id = useId()
    const [open, setOpen] = useState(false)
    const [amount, setAmount] = useState('')
    const [reason, setReason] = useState('')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')

    async function handleSubmit(event) {
        event.preventDefault()
        if (busy) return
        setError('')

        let amountMinor = null // blank means everything still owed
        if (amount.trim()) {
            amountMinor = parseKshToMinor(amount)
            if (amountMinor === null || amountMinor <= 0) {
                setError('Enter an amount like 500, or leave it empty to write off everything left.')
                return
            }
            if (amountMinor > debt.balanceMinor) {
                setError(`Only ${formatKsh(debt.balanceMinor)} is still owed on this deni.`)
                return
            }
        }
        if (reason.trim().length < 3) {
            setError('Say why, in a few words.')
            return
        }

        setBusy(true)
        try {
            await api.post(`/businesses/${businessId}/debts/${debt.debtId}/write-off`, {
                amountMinor,
                reason: reason.trim(),
            })
            await onDone()
        } catch (err) {
            if (!err.response || err.response.status >= 500) {
                // A write-off has no retry key, so never resend blindly: refresh and let the owner check the list
                setError('We could not confirm that this was saved. The account has been refreshed. Check it before trying again.')
                await onDone()
            } else {
                setError(errorMessage(err, 'Could not write it off. Nothing was changed.'))
            }
        } finally {
            setBusy(false)
        }
    }

    if (!open) {
        return (
            <button type="button" onClick={() => setOpen(true)}
                    className="mt-2 h-9 rounded-lg px-3 text-sm font-medium text-ledger hover:bg-ledger-50">
                Write off
            </button>
        )
    }

    return (
        <form onSubmit={handleSubmit} className="mt-3 max-w-md space-y-4 rounded-2xl border border-line bg-surface p-5">
            <h3 className="font-display text-xl font-medium">Write off this deni</h3>
            <p className="text-sm text-ink-soft">
                The customer no longer has to pay it. It stays in your records as a loss. {formatKsh(debt.balanceMinor)} is still owed.
            </p>
            <Field id={`${id}-amount`} label="Amount to write off (KSh)" inputMode="decimal"
                   placeholder="Empty means everything left" value={amount} onChange={(e) => setAmount(e.target.value)} />
            <div className="flex flex-wrap gap-1.5">
                {CHIPS.map((chip) => (
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
                <Button type="submit" variant="danger" loading={busy}>Write off</Button>
                <Button type="button" variant="quiet" onClick={() => setOpen(false)}>Cancel</Button>
            </div>
        </form>
    )
}