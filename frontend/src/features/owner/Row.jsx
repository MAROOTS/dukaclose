import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

const linkClass =
    'flex w-full items-center justify-between gap-4 py-3 text-left transition-colors hover:bg-ink/[0.03] ' +
    'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-forest-600'

export function Rows({ children }) {
    return <ul className="divide-y divide-line border-y border-line">{children}</ul>
}

/** A label and a value. Give it `to` or `onClick` and it opens the records behind the number. */
export default function Row({ label, sub, value, to, onClick, strong = false, tone = '' }) {
    const inner = (
        <>
      <span className="min-w-0">
        <span className={strong ? 'font-medium' : 'text-ink-soft'}>{label}</span>
          {sub && <span className="block text-sm text-ink-faint">{sub}</span>}
      </span>
            <span className={`flex shrink-0 items-center gap-1 tabular-nums ${strong ? 'font-medium' : ''} ${tone}`}>
        {value}
                {(to || onClick) && <ChevronRight className="h-4 w-4 text-ink-faint" aria-hidden="true" />}
      </span>
        </>
    )
    if (to) return <li><Link to={to} className={linkClass}>{inner}</Link></li>
    if (onClick) return <li><button type="button" onClick={onClick} className={linkClass}>{inner}</button></li>
    return <li className="flex items-center justify-between gap-4 py-3">{inner}</li>
}