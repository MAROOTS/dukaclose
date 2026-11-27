import { useState } from 'react'
import Filters from '../features/owner/Filters'
import RecordLine from '../features/owner/RecordLine'
import { usePaged } from '../hooks/usePaged'
import { useOpenInShop } from '../hooks/useOpenInShop'
import { formatDateTime } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { AUDIT_CATEGORIES, auditAmountLabel, auditDetail, auditTarget, auditTitle, rangeText } from '../lib/owner'
import { useOwner } from '../owner/OwnerContext'
import Alert from '../ui/Alert'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner.jsx'

export default function AuditPage() {
    const { business, shops } = useOwner()
    const open = useOpenInShop()
    const [category, setCategory] = useState('')
    const [shopId, setShopId] = useState('')
    const [from, setFrom] = useState('')
    const [to, setTo] = useState('')

    const { items, meta, loading, loadingMore, error, hasMore, more } = usePaged(
        `/businesses/${business.id}/audit-events`,
        { category: category || undefined, shopId: shopId || undefined, from: from || undefined, to: to || undefined },
        25,
    )

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">History</h1>
            <p className="mt-2 text-ink-soft">
                Who did what, and when. Nothing here can be edited or deleted.
                {meta ? ` Showing ${rangeText(meta.from, meta.to)}.` : ''}
            </p>

            <div role="radiogroup" aria-label="What happened" className="mt-8 flex flex-wrap gap-1.5">
                {AUDIT_CATEGORIES.map((c) => (
                    <button key={c.value} type="button" role="radio" aria-checked={category === c.value}
                            onClick={() => setCategory(c.value)}
                            className={`h-10 rounded-full border px-4 text-sm font-medium transition-colors ${
                                category === c.value ? 'border-forest-600 bg-forest-50 text-forest-800' : 'border-line-strong text-ink-soft hover:bg-ink/5'
                            }`}>
                        {c.label}
                    </button>
                ))}
            </div>

            <div className="mt-6">
                <Filters idPrefix="aud" from={from || meta?.from || ''} to={to || meta?.to || ''}
                         onFrom={setFrom} onTo={setTo} shops={shops} shopId={shopId} onShop={setShopId} />
            </div>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}
            {loading && <Spinner />}

            {meta && (
                items.length === 0 ? (
                    <p className="mt-8 border-y border-line py-8 text-ink-soft">Nothing happened in this time.</p>
                ) : (
                    <>
                        <p className="mt-8 text-sm text-ink-soft">{meta.totalElements} {meta.totalElements === 1 ? 'event' : 'events'}, newest first</p>
                        <ul className="mt-3 divide-y divide-line border-y border-line">
                            {items.map((e, i) => {
                                const target = auditTarget(e, shops[0]?.id)
                                const label = auditAmountLabel(e)
                                return (
                                    <RecordLine key={`${e.kind}-${e.entityId}-${e.occurredAt}-${i}`}
                                                title={auditTitle(e)}
                                                lines={[
                                                    `${formatDateTime(e.occurredAt)}, ${e.actorName ?? 'Unknown'}${e.shopName ? `, ${e.shopName}` : ''}`,
                                                    e.reason,
                                                    auditDetail(e),
                                                ]}
                                                amount={e.amountMinor === null || e.amountMinor === undefined ? '' : formatKsh(e.amountMinor)}
                                                amountSub={e.amountMinor === null || e.amountMinor === undefined ? null : label}
                                                onClick={target ? () => open(target.shopId, target.path) : undefined} />
                                )
                            })}
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