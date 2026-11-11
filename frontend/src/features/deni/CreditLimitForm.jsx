import { useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { formatKsh, minorToInput, parseKshToMinor } from '../../lib/money'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

export default function CreditLimitForm({ businessId, customer, onDone }) {
    const [open, setOpen] = useState(false)
    const [value, setValue] = useState(minorToInput(customer.creditLimitMinor))
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')

    async function handleSubmit(event) {
        event.preventDefault()
        if (busy) return
        setError('')

        let limit = null // empty means no limit
        if (value.trim()) {
            limit = parseKshToMinor(value)
            if (limit === null) {
                setError('Enter the limit as a number, like 5000. Leave it empty for no limit.')
                return
            }
        }
        setBusy(true)
        try {
            await api.put(`/businesses/${businessId}/customers/${customer.id}/credit-limit`, { creditLimitMinor: limit })
            setOpen(false)
            await onDone()
        } catch (err) {
            setError(errorMessage(err, 'Could not save the limit.'))
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="mt-6">
            <p className="text-sm text-ink-soft">
                Credit limit:{' '}
                <span className="font-medium text-ink">
          {customer.creditLimitMinor === null ? 'None' : formatKsh(customer.creditLimitMinor)}
        </span>
                {!open && (
                    <button type="button" onClick={() => setOpen(true)}
                            className="ml-2 rounded-lg px-2 py-1 font-medium text-forest-700 hover:bg-forest-50">
                        Change
                    </button>
                )}
            </p>
            {open && (
                <form onSubmit={handleSubmit} className="mt-3 max-w-xs space-y-3">
                    <Field id="credit-limit" label="Limit (KSh)" inputMode="decimal" placeholder="Empty means no limit"
                           hint="A warning only. It never blocks a sale." value={value} onChange={(e) => setValue(e.target.value)} />
                    {error && <Alert>{error}</Alert>}
                    <div className="flex gap-3">
                        <Button type="submit" loading={busy}>Save</Button>
                        <Button type="button" variant="quiet" onClick={() => setOpen(false)}>Cancel</Button>
                    </div>
                </form>
            )}
        </div>
    )
}