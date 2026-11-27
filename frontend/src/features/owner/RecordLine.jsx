/** One record in a list. Give it onClick and the whole line opens the record. */
export default function RecordLine({ title, lines = [], amount, amountSub, struck = false, badge, onClick }) {
    const body = (
        <>
      <span className="min-w-0 text-left">
        <span className="block truncate font-medium">{title}</span>
          {lines.filter(Boolean).map((line, i) => (
              <span key={i} className="block text-sm text-ink-soft">{line}</span>
          ))}
      </span>
            <span className="shrink-0 text-right">
        <span className={`block font-medium tabular-nums ${struck ? 'text-ink-faint line-through' : ''}`}>{amount}</span>
                {badge && <span className="block text-xs font-medium text-ledger">{badge}</span>}
                {amountSub && <span className="block text-sm text-ink-soft">{amountSub}</span>}
      </span>
        </>
    )
    const cls = 'flex w-full items-start justify-between gap-4 py-4'
    return (
        <li>
            {onClick ? (
                <button type="button" onClick={onClick}
                        className={`${cls} transition-colors hover:bg-ink/3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-forest-600`}>
                    {body}
                </button>
            ) : (
                <div className={cls}>{body}</div>
            )}
        </li>
    )
}