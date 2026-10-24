import { formatDateTime } from '../../lib/dates'
import { formatKsh } from '../../lib/money'
import { reasonLabel } from '../../lib/stock'
import { formatQty } from '../../lib/units'

function titleOf(m) {
    if (m.type === 'OPENING') return 'Starting stock'
    if (m.type === 'RECEIPT') return 'Delivery received'
    return reasonLabel(m.reason)
}

export default function History({ movements, unit }) {
    if (movements.length === 0) {
        return <p className="border-y border-line py-6 text-ink-soft">No stock movements yet.</p>
    }

    return (
        <ul className="divide-y divide-line border-y border-line">
            {movements.map((m) => {
                const added = Number(m.quantityDelta) > 0
                return (
                    <li key={m.id} className="flex items-start justify-between gap-4 py-4">
                        <div className="min-w-0">
                            <div className="font-medium">{titleOf(m)}</div>
                            <div className="text-sm text-ink-soft">
                                {formatDateTime(m.createdAt)} by {m.createdByName}
                            </div>
                            {m.totalCostMinor !== null && (
                                <div className="text-sm text-ink-soft">Paid {formatKsh(m.totalCostMinor)}</div>
                            )}
                            {m.note && <div className="mt-1 text-sm text-ink-soft">{m.note}</div>}
                        </div>
                        <div className="shrink-0 text-right">
                            <div className={`font-medium tabular-nums ${added ? 'text-forest-700' : 'text-ledger'}`}>
                                {added ? '+' : '−'}
                                {formatQty(Math.abs(m.quantityDelta), unit)}
                            </div>
                            <div className="text-sm text-ink-soft tabular-nums">Balance {formatQty(m.balanceAfter, unit)}</div>
                        </div>
                    </li>
                )
            })}
        </ul>
    )
}