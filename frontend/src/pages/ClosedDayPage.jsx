import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Loader2, Printer } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import DayReport, { Block } from '../features/closing/DayReport'
import DrawerBreakdown from '../features/closing/DrawerBreakdown'
import { formatBusinessDate, formatDateTime } from '../lib/dates'
import Alert from '../ui/Alert'
import BackLink from '../ui/BackLink'
import Button from '../ui/Button'

function ClosedDay({ shop, date }) {
    const [view, setView] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        let cancelled = false
        setLoading(true)
        api.get(`/shops/${shop.id}/closes/${date}`)
            .then(({ data }) => {
                if (!cancelled) {
                    setView(data)
                    setError('')
                }
            })
            .catch((err) => {
                if (cancelled) return
                setView(null)
                setError(err.response?.data?.code === 'CLOSE_NOT_FOUND'
                    ? 'That day has not been closed.'
                    : errorMessage(err, 'Could not load this day.'))
            })
            .finally(() => {
                if (!cancelled) setLoading(false)
            })
        return () => {
            cancelled = true
        }
    }, [shop.id, date])

    if (loading) {
        return (
            <div className="flex justify-center pt-16">
                <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
            </div>
        )
    }
    if (!view) {
        return (
            <div>
                <BackLink to="/closes">Past days</BackLink>
                <div className="mt-6"><Alert>{error || 'This day was not found.'}</Alert></div>
            </div>
        )
    }

    // The close is the record of what was counted. The snapshot supplies the rest of the drawer arithmetic.
    const drawer = {
        ...view.snapshot.drawer,
        countedMinor: view.countedMinor,
        differenceMinor: view.differenceMinor,
        countCurrent: true,
    }

    return (
        <div>
            <div className="no-print"><BackLink to="/closes">Past days</BackLink></div>
            <div className="mt-4 flex flex-wrap items-end justify-between gap-4">
                <div>
                    <h1 className="font-display text-4xl font-medium tracking-tight">{formatBusinessDate(view.businessDate)}</h1>
                    <p className="mt-2 text-ink-soft">
                        {view.shopName}. Closed by {view.closedByName}, {formatDateTime(view.closedAt)}.
                    </p>
                </div>
                <Button variant="quiet" onClick={() => window.print()} className="no-print border border-line-strong">
                    <Printer className="h-4 w-4" aria-hidden="true" /> Print
                </Button>
            </div>

            <Block title="Cash in the drawer">
                <DrawerBreakdown drawer={drawer} />
                {view.note && <p className="mt-3 text-sm text-ink-soft">Note: {view.note}</p>}
            </Block>

            <DayReport report={view.snapshot} />
        </div>
    )
}

export default function ClosedDayPage() {
    const { date } = useParams()
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <ClosedDay key={`${selectedShop.id}:${date}`} shop={selectedShop} date={date} />
}