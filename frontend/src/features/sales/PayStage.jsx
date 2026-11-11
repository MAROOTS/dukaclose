import { Minus, Plus, Trash2 } from 'lucide-react'
import { formatKsh, parseKshToMinor } from '../../lib/money'
import { SALE_METHODS, qtyToText } from '../../lib/sales'
import { UNITS } from '../../lib/units'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'

const CHIPS = [['0.25', '¼'], ['0.5', '½'], ['1', '1'], ['2', '2']]
const iconButton =
    'flex h-11 w-11 items-center justify-center rounded-lg border border-line-strong text-ink-soft hover:bg-ink/5'

function QuantityControl({ line, onChange }) {
    const piece = line.row.unit === 'PIECE'
    const current = Number(line.qty)
    const base = Number.isFinite(current) ? current : 0

    return (
        <div>
            <div className="flex items-center gap-2">
                {piece && (
                    <button type="button" aria-label="One less" className={iconButton}
                            onClick={() => onChange(qtyToText(Math.max(1, base - 1)))}>
                        <Minus className="h-4 w-4" />
                    </button>
                )}
                <input
                    aria-label={`Quantity of ${line.row.name}`}
                    inputMode={piece ? 'numeric' : 'decimal'}
                    value={line.qty}
                    onChange={(e) => onChange(e.target.value)}
                    className="h-11 w-20 rounded-lg border border-line-strong bg-surface text-center text-base tabular-nums focus:border-forest-600 focus:outline-none focus:ring-4 focus:ring-forest-600/15"
                />
                {piece ? (
                    <button type="button" aria-label="One more" className={iconButton}
                            onClick={() => onChange(qtyToText(base + 1))}>
                        <Plus className="h-4 w-4" />
                    </button>
                ) : (
                    <span className="text-sm text-ink-soft">{UNITS[line.row.unit].short}</span>
                )}
            </div>
            {!piece && (
                <div className="mt-2 flex gap-1.5">
                    {CHIPS.map(([value, label]) => (
                        <button key={value} type="button" onClick={() => onChange(value)}
                                className="h-9 min-w-9 rounded-lg border border-line-strong px-3 text-sm font-medium text-ink-soft hover:bg-ink/5">
                            {label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    )
}

export default function PayStage({
                                     lines, total, pay, amounts, amountsInvalid, paid, received, onReceived,
                                     onQty, onRemove, onPay, onRest, canConfirm, submitting, onConfirm, error, blocked,
                                     deniSection, deniMissing,
                                 }) {
    const diff = total === null ? null : total - paid
    const cash = amounts.CASH
    const receivedMinor = received.trim() === '' ? null : parseKshToMinor(received)
    const change = receivedMinor !== null && cash > 0 ? receivedMinor - cash : null

    let status
    if (total === null) status = 'Fix the quantities above first.'
    else if (amountsInvalid) status = 'Check the amounts. Use numbers like 500 or 500.50.'
    else if (diff === 0) status = 'Payments match the total.'
    else if (diff > 0) status = `${formatKsh(diff)} still to be paid.`
    else status = `${formatKsh(-diff)} too much.`
    const matches = total !== null && !amountsInvalid && diff === 0

    return (
        <div>
            <ul className="divide-y divide-line border-y border-line">
                {lines.map((line) => (
                    <li key={line.itemId} className="py-4">
                        <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                                <p className="font-medium">{line.row.name}</p>
                                <p className="text-sm text-ink-soft">
                                    {line.row.priceMinor === null ? 'No price' : `${formatKsh(line.row.priceMinor)} per ${UNITS[line.row.unit].short}`}
                                </p>
                            </div>
                            <button type="button" aria-label={`Remove ${line.row.name}`} onClick={() => onRemove(line.itemId)}
                                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-ink-faint hover:bg-ledger-50 hover:text-ledger">
                                <Trash2 className="h-[18px] w-[18px]" />
                            </button>
                        </div>
                        <div className="mt-3 flex items-start justify-between gap-3">
                            <QuantityControl line={line} onChange={(text) => onQty(line.itemId, text)} />
                            <p className="pt-2 font-medium tabular-nums">
                                {line.totalMinor === null ? '—' : formatKsh(line.totalMinor)}
                            </p>
                        </div>
                        {line.problem && <p role="alert" className="mt-2 text-sm text-ledger">{line.problem}</p>}
                    </li>
                ))}
            </ul>

            <div className="mt-5 flex items-baseline justify-between">
                <span className="text-ink-soft">Total</span>
                <span className="font-display text-4xl font-medium tabular-nums">{total === null ? '—' : formatKsh(total)}</span>
            </div>

            <section className="mt-10">
                <h2 className="font-display text-2xl font-medium">How was it paid?</h2>
                <div className="mt-4 space-y-3">
                    {SALE_METHODS.map((m) => (
                        <div key={m.value} className="flex items-end gap-2">
                            <div className="min-w-0 flex-1">
                                <Field id={`pay-${m.value}`} label={m.label} inputMode="decimal" placeholder="0"
                                       value={pay[m.value]} onChange={(e) => onPay(m.value, e.target.value)} />
                            </div>
                            <button type="button" onClick={() => onRest(m.value)}
                                    className="h-12 shrink-0 rounded-xl px-3 text-sm font-medium text-forest-700 hover:bg-forest-50">
                                Fill rest
                            </button>
                        </div>
                    ))}
                </div>
                <p role="status" className={`mt-4 text-sm font-medium ${matches ? 'text-forest-700' : 'text-ledger'}`}>{status}</p>

                {amounts.DENI > 0 && deniSection}
                {deniMissing && (
                    <p role="status" className="mt-3 text-sm font-medium text-ledger">
                        Add the customer and a due date for the deni.
                    </p>
                )}

                {cash > 0 && (
                    <div className="mt-6 max-w-xs">
                        <Field id="cash-received" label="Cash received (optional)" inputMode="decimal" placeholder="e.g. 1000"
                               value={received} onChange={(e) => onReceived(e.target.value)} />
                        {change !== null && (
                            <p className={`mt-2 font-display text-2xl font-medium tabular-nums ${change < 0 ? 'text-ledger' : ''}`}>
                                {change >= 0 ? `Change due ${formatKsh(change)}` : `Short by ${formatKsh(-change)}`}
                            </p>
                        )}
                    </div>
                )}
            </section>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}

            <div className="mt-8">
                <Button onClick={onConfirm} loading={submitting} disabled={!canConfirm || blocked} className="w-full sm:w-auto">
                    {total ? `Confirm sale, ${formatKsh(total)}` : 'Confirm sale'}
                </Button>
            </div>
        </div>
    )
}