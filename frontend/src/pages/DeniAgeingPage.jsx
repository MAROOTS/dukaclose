import BucketBar from '../features/owner/BucketBar'
import RecordLine from '../features/owner/RecordLine'
import { usePaged } from '../hooks/usePaged'
import { useOpenInShop } from '../hooks/useOpenInShop'
import { formatDateTime, formatShortDate } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { formatPhone } from '../lib/phone'
import { useOwner } from '../owner/OwnerContext'
import Alert from '../ui/Alert'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner.jsx'

function Stat({ label, children, danger }) {
    return (
        <div>
            <dt className="text-sm text-ink-soft">{label}</dt>
            <dd className={`mt-1 font-display text-2xl font-medium tabular-nums ${danger ? 'text-ledger' : ''}`}>{children}</dd>
        </div>
    )
}

export default function DeniAgeingPage() {
    const { business, shops } = useOwner()
    const openIn = useOpenInShop()
    const { items, meta, loading, loadingMore, error, hasMore, more } =
        usePaged(`/businesses/${business.id}/deni-ageing`, {})

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">Deni owed</h1>
            <p className="mt-2 text-ink-soft">Everyone who owes {business.name}, the longest overdue first.</p>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}
            {loading && <Spinner />}

            {meta && (
                meta.customerCount === 0 ? (
                    <p className="mt-8 border-y border-line py-8 text-ink-soft">Nobody owes anything.</p>
                ) : (
                    <>
                        <dl className="mt-8 grid grid-cols-3 gap-6 border-y border-line py-6">
                            <Stat label="Owed">{formatKsh(meta.outstandingMinor)}</Stat>
                            <Stat label="Overdue" danger={meta.overdueMinor > 0}>{formatKsh(meta.overdueMinor)}</Stat>
                            <Stat label="Customers">{meta.customerCount}</Stat>
                        </dl>
                        <div className="mt-6"><BucketBar buckets={meta.buckets} /></div>

                        <ul className="mt-8 divide-y divide-line border-y border-line">
                            {items.map((c) => (
                                <RecordLine key={c.customerId} title={c.name}
                                            lines={[
                                                formatPhone(c.phone),
                                                c.overdueMinor > 0
                                                    ? `${formatKsh(c.overdueMinor)} overdue, ${c.oldestOverdueDays} days late`
                                                    : `Next due ${formatShortDate(c.earliestDueDate)}`,
                                                c.lastPaymentAt ? `Last paid ${formatKsh(c.lastPaymentMinor)}, ${formatDateTime(c.lastPaymentAt)}` : 'Has not paid anything yet',
                                            ]}
                                            amount={formatKsh(c.outstandingMinor)}
                                            badge={c.overLimit ? 'Over limit' : null}
                                            onClick={() => openIn(shops[0]?.id, `/deni/${c.customerId}`)} />
                            ))}
                        </ul>
                        {hasMore && (
                            <div className="mt-6">
                                <Button variant="quiet" loading={loadingMore} onClick={more} className="border border-line-strong">Show more</Button>
                            </div>
                        )}
                    </>
                )
            )}
        </div>
    )
}