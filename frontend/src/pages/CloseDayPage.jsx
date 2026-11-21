import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Loader2, RefreshCw } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import CloseSection from '../features/closing/CloseSection'
import CountSection from '../features/closing/CountSection'
import DayReport, { Block, Lines } from '../features/closing/DayReport'
import DrawerBreakdown from '../features/closing/DrawerBreakdown'
import { useClosePreview } from '../hooks/useClosePreview'
import { useShopDay } from '../hooks/useShopDay'
import { differenceInfo, toneClass } from '../lib/closing'
import { formatBusinessDate } from '../lib/dates'
import { formatKsh } from '../lib/money'
import Alert from '../ui/Alert'
import Button, { buttonClass } from '../ui/Button'

function Spinner() {
    return (
        <div className="flex justify-center pt-16">
            <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
        </div>
    )
}

function CloseDay({ shop }) {
    const { status, loading: dayLoading } = useShopDay(shop.id)
    const { preview, noOpenDay, loading, error, reload } = useClosePreview(shop.id)
    const isOwner = shop.role === 'OWNER'
    const [recounting, setRecounting] = useState(false)
    const [notice, setNotice] = useState('')
    const [closed, setClosed] = useState(null)

    if (loading || dayLoading) return <Spinner />

    if (closed) {
        const diff = differenceInfo(closed.differenceMinor)
        return (
            <div>
                <h1 className="font-display text-4xl font-medium tracking-tight">Day closed</h1>
                <p className="mt-2 text-ink-soft">
                    {formatBusinessDate(closed.businessDate)} is closed. Nothing more can be added to it.
                </p>
                <div className="mt-8 max-w-md">
                    <Lines
                        rows={[
                            ['Cash counted', formatKsh(closed.countedMinor)],
                            ['Cash expected', formatKsh(closed.expectedMinor)],
                            ['Difference', diff.label, { strong: true, tone: toneClass(diff.tone) }],
                            ['Sales', formatKsh(closed.salesTotalMinor)],
                        ]}
                    />
                    {closed.note && <p className="mt-4 text-sm text-ink-soft">Note: {closed.note}</p>}
                </div>
                <div className="mt-8 flex flex-wrap gap-3">
                    <Link to={`/closes/${closed.businessDate}`} className={buttonClass()}>See the close</Link>
                    <Link to="/" className={`${buttonClass('quiet')} border border-line-strong`}>Home</Link>
                </div>
            </div>
        )
    }

    if (noOpenDay) {
        const closedToday = status && status.lastCloseDate === status.today
        return (
            <div className="max-w-md">
                <h1 className="font-display text-4xl font-medium tracking-tight">
                    {closedToday ? 'Today is already closed' : 'There is no open day'}
                </h1>
                <p className="mt-3 text-ink-soft">
                    {closedToday
                        ? 'You can open a new day tomorrow.'
                        : 'Open today’s book on the Home page first.'}
                </p>
                <Link to={closedToday ? `/closes/${status.today}` : '/'} className={`${buttonClass()} mt-6`}>
                    {closedToday ? 'See today’s close' : 'Go to Home'}
                </Link>
            </div>
        )
    }

    if (!preview) {
        return (
            <div className="max-w-md space-y-4">
                <Alert>{error || 'Could not load the close preview.'}</Alert>
                <Button variant="quiet" onClick={reload} className="border border-line-strong">Try again</Button>
            </div>
        )
    }

    const drawer = preview.drawer
    const counted = Boolean(drawer?.countCurrent)
    const countMovedAway = isOwner && drawer && drawer.countNumber > 0 && !drawer.countCurrent

    async function handleCounted() {
        setNotice('')
        setRecounting(false)
        await reload()
    }

    async function handleStale(message) {
        setNotice(message)
        setRecounting(false)
        await reload()
    }

    return (
        <div>
            <div className="flex flex-wrap items-end justify-between gap-4">
                <div>
                    <h1 className="font-display text-4xl font-medium tracking-tight">Close day</h1>
                    <p className="mt-2 text-ink-soft">{shop.name}, {formatBusinessDate(preview.businessDate)}</p>
                </div>
                <Button variant="quiet" onClick={reload} className="border border-line-strong">
                    <RefreshCw className="h-4 w-4" aria-hidden="true" /> Refresh numbers
                </Button>
            </div>

            {preview.stale && (
                <div className="mt-6">
                    <Alert>
                        This book is from {formatBusinessDate(preview.businessDate)} and was never closed.
                        Closing it now closes that day.
                    </Alert>
                </div>
            )}
            {error && <div className="mt-6"><Alert>{error}</Alert></div>}

            <DayReport report={preview} />

            <Block title="Cash in the drawer">
                {notice && <div className="mb-4"><Alert>{notice}</Alert></div>}
                {countMovedAway && !notice && (
                    <div className="mb-4">
                        <Alert>The drawer was counted earlier, but sales or money were recorded since. It must be counted again.</Alert>
                    </div>
                )}

                {isOwner && drawer && <DrawerBreakdown drawer={drawer} />}

                {counted && !recounting ? (
                    <>
                        {!isOwner && <DrawerBreakdown drawer={drawer} />}
                        <CloseSection
                            key={`${drawer.countId}`}
                            shopId={shop.id}
                            drawer={drawer}
                            businessDate={preview.businessDate}
                            onClosed={setClosed}
                            onStale={handleStale}
                            onRefresh={reload}
                        />
                        <div className="mt-4">
                            <button type="button" onClick={() => setRecounting(true)}
                                    className="rounded-lg px-3 py-2 text-sm font-medium text-forest-700 hover:bg-forest-50">
                                Count again
                            </button>
                        </div>
                    </>
                ) : (
                    <div className={isOwner && drawer ? 'mt-8' : ''}>
                        <CountSection
                            shopId={shop.id}
                            onCounted={handleCounted}
                            onCancel={recounting ? () => setRecounting(false) : null}
                        />
                    </div>
                )}
            </Block>
        </div>
    )
}

export default function CloseDayPage() {
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <CloseDay key={selectedShop.id} shop={selectedShop} />
}