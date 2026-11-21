import { formatDateTime } from '../../lib/dates'
import { formatKsh } from '../../lib/money'
import { categoryLabel } from '../../lib/moneyCategories'
import { differenceInfo, toneClass } from '../../lib/closing'
import { reasonLabel } from '../../lib/stock'
import { formatQty } from '../../lib/units'

export function Block({ title, note, children }) {
    return (
        <section className="mt-10">
            <h2 className="font-display text-2xl font-medium">{title}</h2>
            {note && <p className="mt-1 text-sm text-ink-soft">{note}</p>}
            <div className="mt-3">{children}</div>
        </section>
    )
}

/** rows: [label, value, { strong, tone }] */
export function Lines({ rows }) {
    return (
        <dl className="divide-y divide-line border-y border-line">
            {rows.filter(Boolean).map(([label, value, opts = {}]) => (
                <div key={label} className="flex items-baseline justify-between gap-4 py-3">
                    <dt className={opts.strong ? 'font-medium' : 'text-ink-soft'}>{label}</dt>
                    <dd className={`shrink-0 tabular-nums ${opts.strong ? 'font-medium' : ''} ${opts.tone ?? ''}`}>{value}</dd>
                </div>
            ))}
        </dl>
    )
}

function Empty({ children }) {
    return <p className="border-y border-line py-5 text-ink-soft">{children}</p>
}

export default function DayReport({ report }) {
    const s = report.sales
    const c = report.collections
    const m = report.money
    const d = report.deni
    const st = report.stock
    const p = report.profit

    return (
        <>
            <Block title="Sales">
                <Lines
                    rows={[
                        [`Total (${s.count} ${s.count === 1 ? 'sale' : 'sales'})`, formatKsh(s.totalMinor), { strong: true }],
                        ['Cash', formatKsh(s.cashMinor)],
                        ['Pochi', formatKsh(s.pochiMinor)],
                        ['Till', formatKsh(s.tillMinor)],
                        ['Deni (on credit)', formatKsh(s.deniMinor)],
                        s.reversedCount > 0 && [`Reversed (${s.reversedCount}), not counted`, formatKsh(s.reversedMinor), { tone: 'text-ink-faint' }],
                    ]}
                />
            </Block>

            <Block title="Deni collected" note="Repayments are money in. They are not sales.">
                {c.count === 0 && c.reversedCount === 0 ? (
                    <Empty>No deni was collected.</Empty>
                ) : (
                    <Lines
                        rows={[
                            [`Total (${c.count} ${c.count === 1 ? 'payment' : 'payments'})`, formatKsh(c.totalMinor), { strong: true }],
                            ['Cash', formatKsh(c.cashMinor)],
                            ['Pochi', formatKsh(c.pochiMinor)],
                            ['Till', formatKsh(c.tillMinor)],
                            c.reversedCount > 0 && [`Reversed (${c.reversedCount}), not counted`, '', { tone: 'text-ink-faint' }],
                        ]}
                    />
                )}
            </Block>

            <Block title="Money in and out" note="Stock purchases and cash taken out of the drawer are not expenses.">
                {m.byCategory.length === 0 ? (
                    <Empty>No money in or out.</Empty>
                ) : (
                    <Lines
                        rows={[
                            ...m.byCategory.map((x) => [
                                categoryLabel(x.category),
                                `${x.direction === 'IN' ? '+' : '−'}${formatKsh(x.totalMinor)}`,
                            ]),
                            ['Operating expenses', formatKsh(m.operatingExpensesMinor), { strong: true }],
                        ]}
                    />
                )}
            </Block>

            <Block title="Deni owed">
                <Lines
                    rows={[
                        [`New deni today (${d.newTodayCount})`, formatKsh(d.newTodayMinor)],
                        ['Collected today', formatKsh(d.collectedTodayMinor)],
                        ['Owed by all customers', formatKsh(d.outstandingMinor), { strong: true }],
                        ['Customers who owe', String(d.customerCount)],
                        ['Overdue', formatKsh(d.overdueMinor), { tone: d.overdueMinor > 0 ? 'text-ledger' : '' }],
                    ]}
                />
            </Block>

            <Block title="Stock">
                <h3 className="text-sm font-medium">Sold today</h3>
                {st.itemsSold.length === 0 ? (
                    <Empty>Nothing was sold.</Empty>
                ) : (
                    <Lines rows={st.itemsSold.map((i) => [i.name, `${formatQty(i.quantity, i.unit)}, ${formatKsh(i.totalMinor)}`])} />
                )}

                {st.lowStock.length > 0 && (
                    <>
                        <h3 className="mt-6 text-sm font-medium">Low stock</h3>
                        <Lines
                            rows={st.lowStock.map((i) => [
                                i.name,
                                `${formatQty(i.onHand, i.unit)} left`,
                                { tone: 'text-ledger' },
                            ])}
                        />
                    </>
                )}

                {st.adjustments.length > 0 && (
                    <>
                        <h3 className="mt-6 text-sm font-medium">Stock adjustments</h3>
                        <ul className="divide-y divide-line border-y border-line">
                            {st.adjustments.map((a, i) => (
                                <li key={i} className="flex items-start justify-between gap-4 py-3">
                                    <div className="min-w-0">
                                        <p className="font-medium">{a.itemName}</p>
                                        <p className="text-sm text-ink-soft">
                                            {reasonLabel(a.reason)}{a.note ? `: ${a.note}` : ''}
                                        </p>
                                        <p className="text-sm text-ink-soft">{a.byName}, {formatDateTime(a.createdAt)}</p>
                                    </div>
                                    <p className={`shrink-0 tabular-nums ${Number(a.quantityDelta) < 0 ? 'text-ledger' : 'text-forest-700'}`}>
                                        {Number(a.quantityDelta) > 0 ? '+' : '−'}{formatQty(Math.abs(Number(a.quantityDelta)), a.unit)}
                                    </p>
                                </li>
                            ))}
                        </ul>
                    </>
                )}
            </Block>

            {p && (
                <Block title="Estimated profit" note="A shop-management estimate. It is not a full accounting statement or a tax figure.">
                    <Lines
                        rows={[
                            ['Net sales', formatKsh(p.netSalesMinor)],
                            ['Cost of goods sold', `−${formatKsh(p.costOfGoodsSoldMinor)}`],
                            ['Gross profit', formatKsh(p.grossProfitMinor)],
                            ['Operating expenses', `−${formatKsh(p.operatingExpensesMinor)}`],
                            ['Estimated profit', formatKsh(p.estimatedProfitMinor), { strong: true }],
                        ]}
                    />
                    <h3 className="mt-6 text-sm font-medium">Shown separately, not subtracted above</h3>
                    <Lines
                        rows={[
                            ['Damaged, expired and lost stock, at cost', formatKsh(p.shrinkageMinor)],
                            ['Deni written off', formatKsh(p.writeOffsMinor)],
                        ]}
                    />
                </Block>
            )}

            {report.counts && report.counts.length > 0 && (
                <Block title="Cash counts" note="Every time the drawer was counted today.">
                    <ul className="divide-y divide-line border-y border-line">
                        {report.counts.map((k) => {
                            const diff = differenceInfo(k.differenceMinor)
                            return (
                                <li key={k.countNumber} className="flex items-start justify-between gap-4 py-3">
                                    <div className="min-w-0">
                                        <p className="font-medium">Count {k.countNumber}: {formatKsh(k.countedMinor)}</p>
                                        <p className="text-sm text-ink-soft">
                                            {k.countedByName}, {formatDateTime(k.createdAt)}
                                            {!k.current && ' (the drawer moved after this count)'}
                                        </p>
                                    </div>
                                    <p className={`shrink-0 text-sm font-medium ${toneClass(diff.tone)}`}>{diff.label}</p>
                                </li>
                            )
                        })}
                    </ul>
                </Block>
            )}
        </>
    )
}