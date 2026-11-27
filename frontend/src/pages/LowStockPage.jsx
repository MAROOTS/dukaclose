import { useState } from 'react'
import RecordLine from '../features/owner/RecordLine'
import { usePaged } from '../hooks/usePaged'
import { useOpenInShop } from '../hooks/useOpenInShop'
import { formatDateTime } from '../lib/dates'
import { formatQty } from '../lib/units'
import { useOwner } from '../owner/OwnerContext'
import Alert from '../ui/Alert'
import Button from '../ui/Button'
import { SelectField } from '../ui/Field'
import Spinner from '../ui/Spinner.jsx'

export default function LowStockPage() {
    const { business, shops } = useOwner()
    const openIn = useOpenInShop()
    const [shopId, setShopId] = useState('')
    const { items, meta, loading, loadingMore, error, hasMore, more } =
        usePaged(`/businesses/${business.id}/low-stock`, { shopId: shopId || undefined })

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">Low stock</h1>
            <p className="mt-2 text-ink-soft">Items at or below the level you set for each shop.</p>

            {shops.length > 1 && (
                <div className="mt-8 max-w-xs">
                    <SelectField id="low-shop" label="Shop" value={shopId} onChange={(e) => setShopId(e.target.value)}>
                        <option value="">All shops</option>
                        {shops.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </SelectField>
                </div>
            )}

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}
            {loading && <Spinner />}

            {meta && (
                meta.totalElements === 0 ? (
                    <p className="mt-8 border-y border-line py-8 text-ink-soft">Nothing is running low.</p>
                ) : (
                    <>
                        <p className="mt-8 text-sm text-ink-soft">{meta.totalElements} {meta.totalElements === 1 ? 'item' : 'items'}</p>
                        <ul className="mt-3 divide-y divide-line border-y border-line">
                            {items.map((i) => (
                                <RecordLine key={`${i.shopId}-${i.itemId}`} title={i.itemName}
                                            lines={[i.shopName, i.lastMovementAt ? `Last moved ${formatDateTime(i.lastMovementAt)}` : 'No movement yet']}
                                            amount={<span className="text-ledger">{formatQty(i.onHand, i.unit)}</span>}
                                            amountSub={`Reorder at ${formatQty(i.reorderLevel, i.unit)}`}
                                            onClick={() => openIn(i.shopId, `/stock/${i.itemId}`)} />
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