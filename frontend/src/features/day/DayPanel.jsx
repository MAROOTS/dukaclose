import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../../api/client'
import { useShopDay } from '../../hooks/useShopDay'
import { formatBusinessDate, formatDateTime } from '../../lib/dates'
import { formatKsh, parseKshToMinor } from '../../lib/money'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

function Page({ children }) {
    return (
        <section className="ledger-paper relative mt-10 overflow-hidden rounded-2xl border border-line bg-surface py-9 pl-14 pr-6 sm:pl-20 sm:pr-10">
            <span aria-hidden="true" className="absolute inset-y-0 left-8 w-px bg-ledger/30 sm:left-12" />
            {children}
        </section>
    )
}

export default function DayPanel({ shop }) {
    const { status, loading, error: loadError, reload } = useShopDay(shop.id)
    const [float, setFloat] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')

    async function handleOpen(event) {
        event.preventDefault()
        if (submitting) return
        setError('')

        const openingFloatMinor = parseKshToMinor(float)
        if (openingFloatMinor === null) {
            setError('Enter the cash in the drawer, like 2000. Use 0 if it is empty.')
            return
        }

        setSubmitting(true)
        try {
            await api.post(`/shops/${shop.id}/days`, { openingFloatMinor })
            await reload()
        } catch (err) {
            if (err.response?.data?.code === 'DAY_ALREADY_OPEN') {
                await reload() // someone else just opened it: show it instead of an error
            } else {
                setError(errorMessage(err, 'Could not open the day. Nothing was saved.'))
            }
        } finally {
            setSubmitting(false)
        }
    }

    if (loading) {
        return (
            <Page>
                <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
            </Page>
        )
    }

    if (!status) {
        return (
            <Page>
                <Alert>{loadError || 'Could not load today’s book.'}</Alert>
            </Page>
        )
    }

    const day = status.openDay

    if (day) {
        return (
            <Page>
                <h2 className="font-display text-2xl font-medium">Today’s book is open</h2>
                <dl className="mt-5 grid max-w-sm grid-cols-2 gap-6">
                    <div>
                        <dt className="text-sm text-ink-soft">Opening float</dt>
                        <dd className="mt-1 font-display text-2xl font-medium tabular-nums">{formatKsh(day.openingFloatMinor)}</dd>
                    </div>
                    <div>
                        <dt className="text-sm text-ink-soft">Opened</dt>
                        <dd className="mt-1">
                            {formatDateTime(day.openedAt)}
                            <span className="block text-sm text-ink-soft">by {day.openedByName}</span>
                        </dd>
                    </div>
                </dl>

                {day.stale && (
                    <div className="mt-6 max-w-md">
                        <Alert>
                            This book is from {formatBusinessDate(day.businessDate)} and has not been closed.
                            A new day cannot start until it is closed.
                        </Alert>
                    </div>
                )}

                <p className="mt-6 max-w-sm text-ink-soft">
                    Sales, deni and money out will appear here as you record them.
                </p>
            </Page>
        )
    }

    return (
        <Page>
            <h2 className="font-display text-2xl font-medium">Open today’s book</h2>
            <p className="mt-2 max-w-sm text-ink-soft">
                Count the cash in the drawer before the first sale. This is your opening float.
            </p>
            <form onSubmit={handleOpen} className="mt-6 max-w-sm space-y-5">
                <Field
                    id="opening-float"
                    label="Cash in the drawer (KSh)"
                    inputMode="decimal"
                    placeholder="e.g. 2000"
                    value={float}
                    onChange={(e) => setFloat(e.target.value)}
                />
                {error && <Alert>{error}</Alert>}
                <Button type="submit" loading={submitting}>Open day</Button>
            </form>
        </Page>
    )
}