import { formatKsh } from '../../lib/money'

const SEGMENTS = [
    { key: 'notDueMinor', label: 'Not due yet', cls: 'bg-forest-600/35' },
    { key: 'days1to7Minor', label: '1 to 7 days late', cls: 'bg-brass-500/70' },
    { key: 'days8to30Minor', label: '8 to 30 days late', cls: 'bg-ledger/60' },
    { key: 'days31PlusMinor', label: 'More than 30 days late', cls: 'bg-ledger' },
]

export default function BucketBar({ buckets }) {
    const total = SEGMENTS.reduce((sum, s) => sum + buckets[s.key], 0)
    if (total === 0) return null

    return (
        <div>
            <div className="flex h-3 w-full overflow-hidden rounded-full bg-ink/6" role="img" aria-label="Deni by how late it is">
                {SEGMENTS.map((s) => buckets[s.key] > 0 && (
                    <div key={s.key} className={s.cls} style={{ width: `${(buckets[s.key] / total) * 100}%` }} />
                ))}
            </div>
            <ul className="mt-3 space-y-1.5 text-sm">
                {SEGMENTS.map((s) => (
                    <li key={s.key} className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-2 text-ink-soft">
              <span className={`h-2.5 w-2.5 rounded-full ${s.cls}`} aria-hidden="true" />
                {s.label}
            </span>
                        <span className="tabular-nums">{formatKsh(buckets[s.key])}</span>
                    </li>
                ))}
            </ul>
        </div>
    )
}