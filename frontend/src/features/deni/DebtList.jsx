import { formatShortDate } from '../../lib/dates'
import { formatKsh } from '../../lib/money'
import { formatReceiptNo } from '../../lib/sales'
import WriteOffForm from './WriteOffForm'

export default function DebtList({ debts, businessId, canWriteOff, onDone }) {
    if (debts.length === 0) {
        return <p className="border-y border-line py-6 text-ink-soft">No deni yet.</p>
    }

    return (
        <ul className="divide-y divide-line border-y border-line">
            {debts.map((d) => {
                const settled = !d.voided && d.balanceMinor === 0
                return (
                    <li key={d.debtId} className="py-4">
                        <div className="flex items-start justify-between gap-4">
                            <div className="min-w-0">
                                <p className="font-medium">
                                    {formatReceiptNo(d.receiptNo)} <span className="font-normal text-ink-soft">at {d.shopName}</span>
                                </p>
                                <p className="text-sm text-ink-soft">Due {formatShortDate(d.dueDate)}</p>
                                {d.overdue && <p className="text-sm font-medium text-ledger">Overdue</p>}
                                {d.voided && <p className="text-sm text-ink-soft">Cancelled together with its sale</p>}
                            </div>
                            <div className="shrink-0 text-right">
                                {d.voided ? (
                                    <p className="font-medium tabular-nums text-ink-faint line-through">{formatKsh(d.originalMinor)}</p>
                                ) : settled ? (
                                    <p className="font-medium text-forest-700">Settled</p>
                                ) : (
                                    <p className="font-medium tabular-nums">{formatKsh(d.balanceMinor)}</p>
                                )}
                            </div>
                        </div>
                        <p className="mt-1 text-sm text-ink-soft">
                            {formatKsh(d.originalMinor)} on deni
                            {d.paidMinor > 0 && `, ${formatKsh(d.paidMinor)} paid`}
                            {d.writtenOffMinor > 0 && `, ${formatKsh(d.writtenOffMinor)} written off`}
                        </p>
                        {canWriteOff && !d.voided && d.balanceMinor > 0 && (
                            <WriteOffForm businessId={businessId} debt={d} onDone={onDone} />
                        )}
                    </li>
                )
            })}
        </ul>
    )
}