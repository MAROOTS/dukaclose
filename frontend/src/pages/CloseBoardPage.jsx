import { useState } from 'react'
import RecordLine from '../features/owner/RecordLine'
import { useGet } from '../hooks/useGet'
import { useOpenInShop } from '../hooks/useOpenInShop'
import { differenceInfo, toneClass } from '../lib/closing'
import { formatBusinessDate, formatDateTime, formatShortDate } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { SHOP_STATE } from '../lib/owner'
import { useOwner } from '../owner/OwnerContext'
import Alert from '../ui/Alert'
import Segmented from '../ui/Segmented'
import Spinner from '../ui/Spinner.jsx'

const WINDOWS = [
    { value: '7', label: '7 days' },
    { value: '30', label: '30 days' },
    { value: '90', label: '90 days' },
]

export default function CloseBoardPage() {
    const { business } = useOwner()
    const openIn = useOpenInShop()
    const [days, setDays] = useState('30')
    const { data, loading, error } = useGet(`/businesses/${business.id}/close-board`, { days: Number(days) })

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">Closes</h1>
            <p className="mt-2 text-ink-soft">Which shops have closed, and every drawer that did not match.</p>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}
            {!data && loading && <Spinner />}

            {data && (
                <>
                    {data.needsCloseCount > 0 && (
                        <div className="mt-6">
                            <Alert>
                                {data.needsCloseCount === 1 ? 'A day was' : `${data.needsCloseCount} days were`} never closed.
                                Closing them keeps the books complete.
                            </Alert>
                        </div>
                    )}

                    <h2 className="mt-10 font-display text-2xl font-medium">Today</h2>
                    <ul className="mt-3 divide-y divide-line border-y border-line">
                        {data.shops.map((s) => {
                            const info = SHOP_STATE[s.state]
                            const diff = s.lastClose ? differenceInfo(s.lastClose.differenceMinor) : null
                            const floatDiff = s.floatDifferenceMinor
                            const target = s.state === 'OPEN' || s.state === 'NEEDS_CLOSE' ? '/close'
                                : s.lastClose ? `/closes/${s.lastClose.businessDate}` : null
                            return (
                                <RecordLine key={s.shopId} title={s.shopName}
                                            lines={[
                                                s.state === 'NEEDS_CLOSE' ? `Open since ${formatShortDate(s.openDate)}, opened by ${s.openedByName}` : null,
                                                s.state === 'OPEN' ? `Opened by ${s.openedByName} with ${formatKsh(s.openingFloatMinor)}` : null,
                                                floatDiff ? `Started with ${formatKsh(Math.abs(floatDiff))} ${floatDiff > 0 ? 'more' : 'less'} than the last close counted` : null,
                                                s.lastClose ? `Last close ${formatShortDate(s.lastClose.businessDate)}${diff ? `: ${diff.label}` : ''}` : 'Never closed yet',
                                            ]}
                                            amount={<span className={info.tone === 'bad' ? 'text-ledger' : info.tone === 'ok' ? 'text-forest-700' : 'text-ink-soft'}>{info.label}</span>}
                                            onClick={target ? () => openIn(s.shopId, target) : undefined} />
                            )
                        })}
                    </ul>

                    <div className="mt-12 flex flex-wrap items-end justify-between gap-4">
                        <h2 className="font-display text-2xl font-medium">Recent closes</h2>
                        <div className="w-full sm:w-72"><Segmented label="Look back" value={days} onChange={setDays} options={WINDOWS} /></div>
                    </div>
                    <p className="mt-2 text-sm text-ink-soft">
                        {data.flaggedCount === 0 ? 'Every drawer matched.' : `${data.flaggedCount} did not match.`}
                    </p>

                    {data.recentCloses.length === 0 ? (
                        <p className="mt-4 border-y border-line py-6 text-ink-soft">No day was closed in this time.</p>
                    ) : (
                        <ul className="mt-3 divide-y divide-line border-y border-line">
                            {data.recentCloses.map((c) => {
                                const diff = differenceInfo(c.differenceMinor)
                                return (
                                    <RecordLine key={`${c.shopId}-${c.businessDate}`}
                                                title={`${c.shopName}, ${formatBusinessDate(c.businessDate)}`}
                                                lines={[`Closed by ${c.closedByName}, ${formatDateTime(c.closedAt)}`, c.note]}
                                                amount={<span className={toneClass(diff.tone)}>{diff.label}</span>}
                                                amountSub={`Sales ${formatKsh(c.salesTotalMinor)}`}
                                                badge={c.flagged ? 'Flagged' : null}
                                                onClick={() => openIn(c.shopId, `/closes/${c.businessDate}`)} />
                                )
                            })}
                        </ul>
                    )}
                </>
            )}
        </div>
    )
}