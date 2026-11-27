import { formatShortDate } from '../../lib/dates'
import { formatKsh } from '../../lib/money'

export default function DayBars({ days, pick, label }) {
    const values = days.map((d) => pick(d.totals))
    const max = Math.max(...values, 1)

    return (
        <div>
            <div className="flex h-24 items-end gap-0.75" role="img" aria-label={label}>
                {days.map((d, i) => (
                    <div
                        key={d.date}
                        title={`${formatShortDate(d.date)}: ${formatKsh(values[i])}`}
                        className={`flex-1 rounded-t ${values[i] > 0 ? 'bg-forest-600/80' : 'bg-line-strong'}`}
                        style={{ height: `${values[i] > 0 ? Math.max((values[i] / max) * 100, 4) : 1}%` }}
                    />
                ))}
            </div>
            <div className="mt-1 flex justify-between text-xs text-ink-faint">
                <span>{formatShortDate(days[0].date)}</span>
                <span>{formatShortDate(days[days.length - 1].date)}</span>
            </div>
            <ul className="sr-only">
                {days.map((d, i) => <li key={d.date}>{formatShortDate(d.date)}: {formatKsh(values[i])}</li>)}
            </ul>
        </div>
    )
}