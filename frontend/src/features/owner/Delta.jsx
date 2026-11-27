import { TrendingDown, TrendingUp } from 'lucide-react'
import { formatKsh } from '../../lib/money'
import { percentChange } from '../../lib/owner'

/** How this number compares with the same stretch of the period before. */
export default function Delta({ now, before, label }) {
    const pct = percentChange(now, before)
    if (pct === null) {
        return <p className="mt-1 text-sm text-ink-soft">Nothing to compare with from {label}.</p>
    }
    if (pct === 0) {
        return <p className="mt-1 text-sm text-ink-soft">The same as {label} ({formatKsh(before)}).</p>
    }
    const up = pct > 0
    const Icon = up ? TrendingUp : TrendingDown
    return (
        <p className={`mt-1 flex items-center gap-1.5 text-sm font-medium ${up ? 'text-forest-700' : 'text-ledger'}`}>
            <Icon className="h-4 w-4" aria-hidden="true" />
            {up ? 'Up' : 'Down'} {Math.abs(pct)}% on {label} ({formatKsh(before)})
        </p>
    )
}