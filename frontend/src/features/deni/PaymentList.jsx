import { formatDateTime } from '../../lib/dates'
import { formatKsh } from '../../lib/money'
import { formatReceiptNo, methodLabel } from '../../lib/sales'
import ReverseForm from '../sales/ReverseForm'

export default function PaymentList({ payments, shop, userId, openDate, onDone }) {
    if (payments.length === 0) {
        return <p className="border-y border-line py-6 text-ink-soft">No payments yet.</p>
    }

    return (
        <ul className="divide-y divide-line border-y border-line">
            {payments.map((p) => {
                const reversed = Boolean(p.reversal)
                const mayReverse =
                    !reversed &&
                    Boolean(openDate) &&
                    p.shopId === shop.id &&
                    p.businessDate === openDate &&
                    (shop.role === 'OWNER' || p.receivedById === userId)
                return (
                    <li key={p.paymentId} className="py-4">
                        <div className="flex items-start justify-between gap-4">
                            <div className="min-w-0">
                                <p className="font-medium">{methodLabel(p.method)}</p>
                                <p className="text-sm text-ink-soft">
                                    {formatDateTime(p.createdAt)}, {p.receivedByName} at {p.shopName}
                                </p>
                                <p className="text-sm text-ink-soft">
                                    {p.allocations.map((a) => `${formatReceiptNo(a.receiptNo)} ${formatKsh(a.amountMinor)}`).join(', ')}
                                </p>
                            </div>
                            <div className="shrink-0 text-right">
                                <p className={`font-medium tabular-nums ${reversed ? 'text-ink-faint line-through' : 'text-forest-700'}`}>
                                    {formatKsh(p.amountMinor)}
                                </p>
                                {reversed && <p className="text-xs font-medium text-ledger">Reversed</p>}
                            </div>
                        </div>
                        {reversed && (
                            <p className="mt-2 text-sm text-ink-soft">
                                Reversed: {p.reversal.reason}. {p.reversal.reversedByName}, {formatDateTime(p.reversal.reversedAt)}.
                            </p>
                        )}
                        {mayReverse && (
                            <ReverseForm
                                compact
                                noun="repayment"
                                url={`/shops/${shop.id}/debt-payments/${p.paymentId}/reverse`}
                                onDone={onDone}
                            />
                        )}
                    </li>
                )
            })}
        </ul>
    )
}