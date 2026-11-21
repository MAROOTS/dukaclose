import { useRef, useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { parseKshToMinor } from '../../lib/money'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

export default function CountSection({ shopId, onCounted, onCancel }) {
    const [amount, setAmount] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    // One key per attempt, reused if the connection drops. A different amount is a different attempt.
    const attempt = useRef({ key: null, minor: null })

    async function handleSubmit(event) {
        event.preventDefault()
        if (submitting) return
        setError('')

        const minor = parseKshToMinor(amount, 10_000_000_000)
        if (minor === null) {
            setError('Enter the cash you counted, like 12500. Use 0 if the drawer is empty.')
            return
        }
        if (!attempt.current.key || attempt.current.minor !== minor) {
            attempt.current = { key: crypto.randomUUID(), minor }
        }

        setSubmitting(true)
        try {
            const { data } = await api.post(
                `/shops/${shopId}/days/current/cash-counts`,
                { countedMinor: minor },
                { headers: { 'Idempotency-Key': attempt.current.key }, timeout: 20000 },
            )
            attempt.current = { key: null, minor: null }
            await onCounted(data)
        } catch (err) {
            if (!err.response || err.response.status >= 500) {
                setError('We could not confirm that the count was saved. Tap Count again. It will not be recorded twice.')
            } else {
                attempt.current = { key: null, minor: null }
                setError(errorMessage(err, 'The count was not recorded.'))
            }
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="max-w-md space-y-5">
            <p className="text-ink-soft">
                Count all the cash in the drawer, notes and coins. Leave out Pochi and till money.
                What was expected is shown after you count.
            </p>
            <Field id="counted-cash" label="Cash counted (KSh)" inputMode="decimal" placeholder="e.g. 12500"
                   value={amount} onChange={(e) => setAmount(e.target.value)} />
            {error && <Alert>{error}</Alert>}
            <div className="flex gap-3">
                <Button type="submit" loading={submitting}>Count</Button>
                {onCancel && <Button type="button" variant="quiet" onClick={onCancel}>Cancel</Button>}
            </div>
        </form>
    )
}