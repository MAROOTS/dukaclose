import { Navigate, useParams, useSearchParams } from 'react-router-dom'
import Filters from '../features/owner/Filters'
import RecordLine from '../features/owner/RecordLine'
import { usePaged } from '../hooks/usePaged'
import { useOpenInShop } from '../hooks/useOpenInShop'
import { formatBusinessDate, formatDateTime } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { categoryLabel } from '../lib/moneyCategories'
import { rangeText } from '../lib/owner'
import { formatReceiptNo, methodLabel } from '../lib/sales'
import { reasonLabel } from '../lib/stock'
import { formatQty } from '../lib/units'
import { useOwner } from '../owner/OwnerContext'
import Alert from '../ui/Alert'
import BackLink from '../ui/BackLink'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner.jsx'

const KINDS = {
    sales: {
        title: 'Sales', path: 'sales', summaryLabel: 'Sales total',
        hint: 'Sales that stand. Reversed sales are struck through and not counted.',
    },
    expenses: {
        title: 'Operating expenses', path: 'money-movements', extra: { expensesOnly: true }, summaryLabel: 'Operating expenses',
        hint: 'Transport, lunch and other expenses. Stock purchases and cash taken out of the drawer are not expenses.',
    },
    collections: {
        title: 'Deni collected', path: 'debt-payments', summaryLabel: 'Collected',
        hint: 'Repayments, on the day they were collected. They are not sales.',
    },
    refunds: {
        title: 'Refunds', path: 'refunds', summaryLabel: 'Refunded',
        hint: 'On the day the refund was recorded, not the day of the original sale.',
    },
    'write-offs': {
        title: 'Deni written off', path: 'write-offs', summaryLabel: 'Written off',
        hint: 'Shown separately. Not subtracted from the profit estimate.',
    },
    adjustments: {
        title: 'Stock adjustments', path: 'stock-adjustments', summaryLabel: 'Damaged, expired and lost, at cost',
        hint: 'Every adjustment is listed. Only damaged, expired and lost stock is in the total.',
    },
}

function Row({ kind, item: x, open, firstShop }) {
    switch (kind) {
        case 'sales':
            return (
                <RecordLine title={`Receipt ${formatReceiptNo(x.receiptNo)}`}
                            lines={[`${x.shopName}, ${formatDateTime(x.createdAt)}`, `${x.cashierName}. ${x.payments.map((p) => methodLabel(p.method)).join(', ')}`]}
                            amount={formatKsh(x.totalMinor)} struck={x.status === 'REVERSED'} badge={x.status === 'REVERSED' ? 'Reversed' : null}
                            onClick={() => open(x.shopId, `/sales/${x.id}`)} />
            )
        case 'expenses':
            return (
                <RecordLine title={categoryLabel(x.category)}
                            lines={[x.note, `${x.shopName}, ${formatDateTime(x.createdAt)}, ${x.createdByName}`]}
                            amount={`${x.direction === 'IN' ? '+' : '−'}${formatKsh(x.amountMinor)}`} amountSub={methodLabel(x.method)}
                            struck={x.reversed} badge={x.reversed ? 'Reversed' : null} />
            )
        case 'collections':
            return (
                <RecordLine title={x.customerName}
                            lines={[`${x.shopName}, ${formatDateTime(x.createdAt)}`, `Received by ${x.receivedByName}`]}
                            amount={formatKsh(x.amountMinor)} amountSub={methodLabel(x.method)}
                            struck={x.reversed} badge={x.reversed ? 'Reversed' : null}
                            onClick={() => open(x.shopId ?? firstShop, `/deni/${x.customerId}`)} />
            )
        case 'refunds':
            return (
                <RecordLine title={`Refund on sale ${formatReceiptNo(x.saleReceiptNo)}`}
                            lines={[`${x.shopName}, recorded ${formatDateTime(x.createdAt)} by ${x.refundedByName}`,
                                `Sold ${formatBusinessDate(x.saleDate)}. ${x.reason}`,
                                x.debtCreditMinor > 0 ? `${formatKsh(x.debtCreditMinor)} taken off what the customer owed` : null]}
                            amount={formatKsh(x.totalMinor)} onClick={() => open(x.shopId, `/sales/${x.saleId}`)} />
            )
        case 'write-offs':
            return (
                <RecordLine title={x.customerName}
                            lines={[`Sale ${formatReceiptNo(x.saleReceiptNo)} at ${x.shopName}`, x.reason, `${x.writtenOffByName}, ${formatDateTime(x.createdAt)}`]}
                            amount={formatKsh(x.amountMinor)} onClick={() => open(x.shopId, `/deni/${x.customerId}`)} />
            )
        case 'adjustments': {
            const delta = Number(x.quantityDelta)
            return (
                <RecordLine title={x.itemName}
                            lines={[`${x.shopName}, ${formatDateTime(x.createdAt)}, ${x.byName}`, `${reasonLabel(x.reason)}${x.note ? `: ${x.note}` : ''}`]}
                            amount={`${delta > 0 ? '+' : '−'}${formatQty(Math.abs(delta), x.unit)}`}
                            amountSub={x.shrinkage ? `${formatKsh(x.valueMinor)} at cost` : null}
                            onClick={() => open(x.shopId, `/stock/${x.itemId}`)} />
            )
        }
        default:
            return null
    }
}

export default function RecordsPage() {
    const { kind } = useParams()
    const config = KINDS[kind]
    const { business, shops } = useOwner()
    const open = useOpenInShop()
    const [params, setParams] = useSearchParams()

    const from = params.get('from') ?? ''
    const to = params.get('to') ?? ''
    const shopId = params.get('shopId') ?? ''

    const list = usePaged(config ? `/businesses/${business.id}/${config.path}` : null, {
        ...(config?.extra ?? {}),
        from: from || undefined,
        to: to || undefined,
        shopId: shopId || undefined,
    })

    if (!config) return <Navigate to="/owner" replace />

    function change(name, value) {
        const next = new URLSearchParams(params)
        if (value) next.set(name, value)
        else next.delete(name)
        setParams(next, { replace: true })
    }

    const { items, meta, loading, loadingMore, error, hasMore, more } = list
    const shopName = shopId ? shops.find((s) => s.id === shopId)?.name : null

    return (
        <div>
            <BackLink to="/owner">Overview</BackLink>
            <h1 className="mt-4 font-display text-4xl font-medium tracking-tight">{config.title}</h1>
            <p className="mt-2 text-ink-soft">
                {meta ? rangeText(meta.from, meta.to) : ''}{shopName ? `, ${shopName}` : ''}
            </p>

            <div className="mt-8">
                <Filters idPrefix="rec" from={from || meta?.from || ''} to={to || meta?.to || ''}
                         onFrom={(v) => change('from', v)} onTo={(v) => change('to', v)} shops={shops}
                         shopId={shopId} onShop={(v) => change('shopId', v)} />
            </div>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}
            {loading && <Spinner />}

            {meta && (
                <>
                    <div className="mt-8 flex items-baseline justify-between gap-4 border-y border-line py-5">
                        <div>
                            <p className="text-sm text-ink-soft">{config.summaryLabel}</p>
                            <p className="mt-1 font-display text-3xl font-medium tabular-nums">{formatKsh(meta.summaryMinor)}</p>
                        </div>
                        <p className="text-sm text-ink-soft">{meta.totalElements} {meta.totalElements === 1 ? 'record' : 'records'}</p>
                    </div>
                    <p className="mt-3 text-sm text-ink-soft">{config.hint}</p>

                    {items.length === 0 ? (
                        <p className="mt-6 border-y border-line py-8 text-ink-soft">Nothing was recorded in this time.</p>
                    ) : (
                        <ul className="mt-4 divide-y divide-line border-y border-line">
                            {items.map((item) => (
                                <Row key={item.id} kind={kind} item={item} open={open} firstShop={shops[0]?.id} />
                            ))}
                        </ul>
                    )}
                    {hasMore && (
                        <div className="mt-6">
                            <Button variant="quiet" loading={loadingMore} onClick={more} className="border border-line-strong">Show more</Button>
                        </div>
                    )}
                </>
            )}
        </div>
    )
}