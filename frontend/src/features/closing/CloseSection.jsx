import { useRef, useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { formatBusinessDate } from '../../lib/dates'
import { formatKsh } from '../../lib/money'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

export default function CloseSection({ shopId, drawer, businessDate, onClosed, onStale, onRefresh }) {
    const needsNote = drawer.differenceMinor !== 0
    const [note, setNote] = useState('')
    const [confirming, setConfirming] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    // One key per attempt, reused if the connection drops with an unknown outcome
    const attempt = useRef({ key: null, sig: '' })

    function review(event) {
        event.preventDefault()
        setError('')
        if (needsNote && note.trim().length < 3) {
            setError(`The drawer is ${drawer.differenceMinor < 0 ? 'short' : 'over'} by ${formatKsh(Math.abs(drawer.differenceMinor))}. Add a note explaining why.`)
            return
        }
        setConfirming(true)
    }

    async function confirm() {
        if (submitting) return
        setError('')
        setSubmitting(true)
        const sig = `${drawer.countId}|${note.trim()}`
        if (!attempt.current.key || attempt.current.sig !== sig) attempt.current = { key: crypto.randomUUID(), sig }

        try {
            const { data } = await api.post(
                `/shops/${shopId}/days/current/close`,
                { cashCountId: drawer.countId, note: note.trim() || null },
                { headers: { 'Idempotency-Key': attempt.current.key }, timeout: 20000 },
            )
            attempt.current = { key: null, sig: '' }
            await onClosed(data)
        } catch (err) {
            const code = err.response?.data?.code
            if (!err.response || err.response.status >= 500) {
                // We cannot know whether it closed. Keep the key: confirming again is safe.
                setError('We could not confirm that the day was closed. Tap Yes, close the day again. It will not be closed twice.')
            } else {
                attempt.current = { key: null, sig: '' }
                if (code === 'COUNT_STALE' || code === 'COUNT_SUPERSEDED') {
                    setConfirming(false)
                    onStale(errorMessage(err, 'The drawer must be counted again.'))
                } else if (code === 'NO_OPEN_DAY') {
                    onRefresh()
                } else if (code === 'IDEMPOTENCY_CONFLICT') {
                    setError('An earlier attempt may already have closed the day. Refresh to check.')
                } else {
                    setConfirming(false)
                    setError(errorMessage(err, 'The day was not closed.'))
                }
            }
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={review} className="mt-6 max-w-md space-y-5">
            {needsNote && (
                <Field id="close-note" label="Why is the drawer different?" maxLength={200}
                       placeholder="e.g. Gave change twice" value={note} onChange={(e) => setNote(e.target.value)} />
            )}
            {!needsNote && (
                <Field id="close-note" label="Note, optional" maxLength={200} value={note}
                       onChange={(e) => setNote(e.target.value)} />
            )}

            {error && <Alert>{error}</Alert>}

            {confirming ? (
                <div className="space-y-4 rounded-2xl border border-line-strong bg-surface p-5">
                    <h3 className="font-display text-xl font-medium">Close {formatBusinessDate(businessDate)}?</h3>
                    <p className="text-sm text-ink-soft">
                        No sales, deni payments or money entries can be added to this day after this.
                        The day is kept exactly as it is now. This cannot be undone.
                    </p>
                    <div className="flex gap-3">
                        <Button type="button" onClick={confirm} loading={submitting}>Yes, close the day</Button>
                        <Button type="button" variant="quiet" onClick={() => setConfirming(false)}>Not yet</Button>
                    </div>
                </div>
            ) : (
                <Button type="submit">Close day</Button>
            )}
        </form>
    )
}