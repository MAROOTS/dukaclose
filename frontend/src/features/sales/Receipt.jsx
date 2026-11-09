import { Printer, Share2 } from 'lucide-react'
import { formatDateTime } from '../../lib/dates'
import { formatKsh } from '../../lib/money'
import { formatReceiptNo, methodLabel, receiptText } from '../../lib/sales'
import { formatQty } from '../../lib/units'
import { buttonClass } from '../../ui/Button'

export default function Receipt({ sale, shopName }) {
    const reversed = sale.status === 'REVERSED'

    return (
        <article aria-label={`Receipt ${formatReceiptNo(sale.receiptNo)}`}
                 className="rounded-2xl border border-line bg-surface p-6 sm:p-8">
            <header className="text-center">
                <p className="font-display text-2xl font-medium">{shopName}</p>
                <p className="mt-1 text-sm text-ink-soft">Receipt {formatReceiptNo(sale.receiptNo)}</p>
                <p className="text-sm text-ink-soft">{formatDateTime(sale.createdAt)}</p>
                <p className="text-sm text-ink-soft">Served by {sale.cashierName}</p>
            </header>

            {reversed && sale.reversal && (
                <div className="mt-5 rounded-xl border border-ledger/20 bg-ledger-50 px-4 py-3 text-sm text-ledger-700">
                    <p className="font-medium">This sale was reversed</p>
                    <p>{sale.reversal.reason}</p>
                    <p>By {sale.reversal.reversedByName}, {formatDateTime(sale.reversal.reversedAt)}</p>
                </div>
            )}

            <ul className="mt-6 divide-y divide-line border-y border-dashed border-line-strong">
                {sale.lines.map((l) => (
                    <li key={l.itemId} className="flex items-start justify-between gap-4 py-3">
                        <div className="min-w-0">
                            <p className="font-medium">{l.name}</p>
                            <p className="text-sm text-ink-soft">{formatQty(l.quantity, l.unit)} × {formatKsh(l.unitPriceMinor)}</p>
                        </div>
                        <p className="shrink-0 tabular-nums">{formatKsh(l.lineTotalMinor)}</p>
                    </li>
                ))}
            </ul>

            <div className="mt-5 flex items-baseline justify-between">
                <span className="text-ink-soft">Total</span>
                <span className={`font-display text-3xl font-medium tabular-nums ${reversed ? 'text-ink-faint line-through' : ''}`}>
          {formatKsh(sale.totalMinor)}
        </span>
            </div>

            <ul className="mt-4 space-y-1 text-sm">
                {sale.allocations.map((a) => (
                    <li key={a.method} className="flex justify-between">
                        <span className="text-ink-soft">Paid by {methodLabel(a.method)}</span>
                        <span className="tabular-nums">{formatKsh(a.amountMinor)}</span>
                    </li>
                ))}
            </ul>

            <p className="mt-8 text-center text-sm text-ink-soft">Asante, karibu tena.</p>
        </article>
    )
}

export function ReceiptActions({ sale, shopName }) {
    async function share() {
        const text = receiptText(sale, shopName)
        if (navigator.share) {
            try {
                await navigator.share({ text })
            } catch {
                /* the person closed the share sheet */
            }
        } else {
            window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank', 'noopener')
        }
    }

    const quiet = `${buttonClass('quiet')} border border-line-strong no-print`
    return (
        <>
            <button type="button" onClick={share} className={quiet}>
                <Share2 className="h-4 w-4" aria-hidden="true" /> Share
            </button>
            <button type="button" onClick={() => window.print()} className={quiet}>
                <Printer className="h-4 w-4" aria-hidden="true" /> Print
            </button>
        </>
    )
}