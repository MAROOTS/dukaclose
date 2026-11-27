import { useState } from 'react'
import { Link } from 'react-router-dom'
import BucketBar from '../features/owner/BucketBar'
import DayBars from '../features/owner/DayBars'
import Delta from '../features/owner/Delta'
import Row, { Rows } from '../features/owner/Row'
import { Block } from '../features/closing/DayReport'
import { useGet } from '../hooks/useGet'
import { useOpenInShop } from '../hooks/useOpenInShop'
import { differenceInfo, toneClass } from '../lib/closing'
import { formatBusinessDate, formatShortDate } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { COMPARE_LABEL, PERIODS, SHOP_STATE, rangeText, recordsPath } from '../lib/owner'
import { formatQty } from '../lib/units'
import { useOwner } from '../owner/OwnerContext'
import Alert from '../ui/Alert'
import Field, { SelectField } from '../ui/Field'
import Segmented from '../ui/Segmented'
import Spinner from '../ui/Spinner.jsx'

function Overview({ business, shops }) {
    const openIn = useOpenInShop()
    const [period, setPeriod] = useState('today')
    const [shopId, setShopId] = useState('')
    const [asOf, setAsOf] = useState('')
    const base = `/businesses/${business.id}`
    const shop = shopId || undefined

    const dash = useGet(`${base}/dashboard`, { period, shopId: shop, asOf: asOf || undefined })
    const ageing = useGet(`${base}/deni-ageing`, { size: 3 })
    const low = useGet(`${base}/low-stock`, { shopId: shop, size: 5 })
    const board = useGet(`${base}/close-board`, { days: 7 })
    const d = dash.data
    const firstShop = shops[0]?.id

    const range = d ? { from: d.from, to: d.to, shopId: shop } : {}
    const t = d?.totals

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">Overview</h1>
            <p className="mt-2 text-ink-soft">{business.name}{d ? `, ${rangeText(d.from, d.to)}` : ''}</p>

            <div className="mt-8 space-y-4">
                <Segmented label="Period" value={period} onChange={setPeriod} options={PERIODS} />
                <div className="grid gap-4 sm:grid-cols-2">
                    {shops.length > 1 && (
                        <SelectField id="ov-shop" label="Shop" value={shopId} onChange={(e) => setShopId(e.target.value)}>
                            <option value="">All shops</option>
                            {shops.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                        </SelectField>
                    )}
                    <Field id="ov-asof" label="Look back at" type="date" max={board.data?.today}
                           value={asOf || d?.asOf || ''} onChange={(e) => setAsOf(e.target.value)}
                           hint={asOf ? undefined : 'Today. Pick an earlier date to see that day, week or month.'} />
                </div>
            </div>

            {dash.error && !d && <div className="mt-8"><Alert>{dash.error}</Alert></div>}
            {!d && !dash.error && <Spinner />}

            {d && (
                <>
                    {/* 1. Sales per shop */}
                    <Block title="Sales">
                        <p className="font-display text-5xl font-medium tabular-nums">{formatKsh(t.salesTotalMinor)}</p>
                        <Delta now={t.salesTotalMinor} before={d.previous.salesTotalMinor} label={COMPARE_LABEL[period]} />

                        {d.byDay.length > 1 && (
                            <div className="mt-6"><DayBars days={d.byDay} pick={(x) => x.salesTotalMinor} label="Sales by day" /></div>
                        )}

                        {d.byShop.length > 1 && (
                            <div className="mt-6">
                                <h3 className="mb-2 text-sm font-medium">By shop</h3>
                                <Rows>
                                    {d.byShop.map((s) => (
                                        <Row key={s.shopId} label={s.name}
                                             sub={`${s.totals.salesCount} ${s.totals.salesCount === 1 ? 'sale' : 'sales'}`}
                                             value={formatKsh(s.totals.salesTotalMinor)} strong
                                             to={recordsPath('sales', { ...range, shopId: s.shopId })} />
                                    ))}
                                </Rows>
                            </div>
                        )}

                        <div className="mt-6">
                            <Rows>
                                <Row label="Sales rung up" value={`${t.salesCount}`} to={recordsPath('sales', range)} />
                                <Row label="Paid in cash" value={formatKsh(t.cashSalesMinor)} />
                                <Row label="Paid by Pochi" value={formatKsh(t.pochiSalesMinor)} />
                                <Row label="Paid by till" value={formatKsh(t.tillSalesMinor)} />
                                <Row label="On deni (credit)" value={formatKsh(t.deniSalesMinor)} />
                                <Row label="Refunded" value={`−${formatKsh(t.refundsTotalMinor)}`} to={recordsPath('refunds', range)} />
                                <Row label="Net sales" value={formatKsh(t.netSalesMinor)} strong />
                                <Row label="Deni collected" sub="Not sales: money coming in" value={formatKsh(t.collectionsTotalMinor)}
                                     to={recordsPath('collections', range)} />
                            </Rows>
                        </div>
                    </Block>

                    {/* 2. Overdue deni */}
                    <Block title="Overdue deni" note="What customers owe right now, across all shops.">
                        {ageing.data ? (
                            <>
                                <p className={`font-display text-5xl font-medium tabular-nums ${ageing.data.overdueMinor > 0 ? 'text-ledger' : ''}`}>
                                    {formatKsh(ageing.data.overdueMinor)}
                                </p>
                                <p className="mt-1 text-sm text-ink-soft">
                                    {formatKsh(ageing.data.outstandingMinor)} owed by {ageing.data.customerCount}{' '}
                                    {ageing.data.customerCount === 1 ? 'customer' : 'customers'}
                                </p>
                                {ageing.data.customerCount > 0 && (
                                    <>
                                        <div className="mt-5"><BucketBar buckets={ageing.data.buckets} /></div>
                                        <div className="mt-5">
                                            <Rows>
                                                {ageing.data.items.map((c) => (
                                                    <Row key={c.customerId} label={c.name}
                                                         sub={c.overdueMinor > 0 ? `${c.oldestOverdueDays} days late` : `Next due ${formatShortDate(c.earliestDueDate)}`}
                                                         value={formatKsh(c.outstandingMinor)} strong
                                                         tone={c.overdueMinor > 0 ? 'text-ledger' : ''}
                                                         onClick={() => openIn(firstShop, `/deni/${c.customerId}`)} />
                                                ))}
                                            </Rows>
                                        </div>
                                        <Link to="/owner/deni" className="mt-3 inline-block rounded-lg px-3 py-2 text-sm font-medium text-forest-700 hover:bg-forest-50">
                                            See everyone who owes
                                        </Link>
                                    </>
                                )}
                            </>
                        ) : ageing.error ? <Alert>{ageing.error}</Alert> : <Spinner className="py-6" />}
                    </Block>

                    {/* 3. Estimated profit */}
                    <Block title="Estimated profit" note="A shop-management estimate. It is not a full accounting statement or a tax figure.">
                        <p className={`font-display text-5xl font-medium tabular-nums ${t.estimatedProfitMinor < 0 ? 'text-ledger' : ''}`}>
                            {formatKsh(t.estimatedProfitMinor)}
                        </p>
                        <Delta now={t.estimatedProfitMinor} before={d.previous.estimatedProfitMinor} label={COMPARE_LABEL[period]} />
                        <div className="mt-6">
                            <Rows>
                                <Row label="Net sales (after refunds)" value={formatKsh(t.netSalesMinor)} />
                                <Row label="Cost of goods sold" value={`−${formatKsh(t.costOfGoodsSoldMinor)}`} />
                                <Row label="Gross profit" value={formatKsh(t.grossProfitMinor)} />
                                <Row label="Operating expenses" value={`−${formatKsh(t.operatingExpensesMinor)}`} strong
                                     to={recordsPath('expenses', range)} />
                            </Rows>
                        </div>
                        <h3 className="mb-2 mt-6 text-sm font-medium">Shown separately, not subtracted above</h3>
                        <Rows>
                            <Row label="Damaged, expired and lost stock, at cost" value={formatKsh(t.shrinkageMinor)}
                                 to={recordsPath('adjustments', range)} />
                            <Row label="Deni written off" value={formatKsh(t.writeOffsMinor)} to={recordsPath('write-offs', range)} />
                        </Rows>
                    </Block>
                </>
            )}

            {/* 4. Low stock */}
            <Block title="Low stock" note="At or below the level you set for each shop.">
                {low.data ? (
                    low.data.totalElements === 0 ? (
                        <p className="border-y border-line py-5 text-ink-soft">Nothing is running low.</p>
                    ) : (
                        <>
                            <Rows>
                                {low.data.items.map((i) => (
                                    <Row key={`${i.shopId}-${i.itemId}`} label={i.itemName}
                                         sub={shops.length > 1 ? i.shopName : undefined}
                                         value={`${formatQty(i.onHand, i.unit)} left`} tone="text-ledger"
                                         onClick={() => openIn(i.shopId, `/stock/${i.itemId}`)} />
                                ))}
                            </Rows>
                            <Link to="/owner/stock" className="mt-3 inline-block rounded-lg px-3 py-2 text-sm font-medium text-forest-700 hover:bg-forest-50">
                                See all {low.data.totalElements}
                            </Link>
                        </>
                    )
                ) : low.error ? <Alert>{low.error}</Alert> : <Spinner className="py-6" />}
            </Block>

            {/* 5. Closing differences */}
            <Block title="Closing differences" note="Every difference is flagged, however small.">
                {board.data ? (
                    <>
                        {board.data.needsCloseCount > 0 && (
                            <div className="mb-4">
                                <Alert>
                                    {board.data.needsCloseCount === 1 ? 'A shop has' : `${board.data.needsCloseCount} shops have`} a day
                                    that was never closed.
                                </Alert>
                            </div>
                        )}
                        <Rows>
                            {board.data.shops.map((s) => {
                                const info = SHOP_STATE[s.state]
                                return (
                                    <Row key={s.shopId} label={s.shopName}
                                         sub={s.state === 'NEEDS_CLOSE' ? `Open since ${formatShortDate(s.openDate)}` : undefined}
                                         value={info.label} strong tone={info.tone === 'bad' ? 'text-ledger' : info.tone === 'ok' ? 'text-forest-700' : 'text-ink-soft'}
                                         onClick={s.state === 'OPEN' || s.state === 'NEEDS_CLOSE' ? () => openIn(s.shopId, '/close')
                                             : s.lastClose ? () => openIn(s.shopId, `/closes/${s.lastClose.businessDate}`) : undefined} />
                                )
                            })}
                        </Rows>
                        <h3 className="mb-2 mt-6 text-sm font-medium">Differences in the last 7 days</h3>
                        {board.data.flaggedCount === 0 ? (
                            <p className="border-y border-line py-5 text-ink-soft">Every drawer matched.</p>
                        ) : (
                            <Rows>
                                {board.data.recentCloses.filter((c) => c.flagged).slice(0, 5).map((c) => {
                                    const diff = differenceInfo(c.differenceMinor)
                                    return (
                                        <Row key={`${c.shopId}-${c.businessDate}`} label={c.shopName}
                                             sub={formatBusinessDate(c.businessDate)} value={diff.label} strong tone={toneClass(diff.tone)}
                                             onClick={() => openIn(c.shopId, `/closes/${c.businessDate}`)} />
                                    )
                                })}
                            </Rows>
                        )}
                        <Link to="/owner/closes" className="mt-3 inline-block rounded-lg px-3 py-2 text-sm font-medium text-forest-700 hover:bg-forest-50">
                            See the full close board
                        </Link>
                    </>
                ) : board.error ? <Alert>{board.error}</Alert> : <Spinner className="py-6" />}
            </Block>
        </div>
    )
}

export default function OverviewPage() {
    const { business, shops } = useOwner()
    return <Overview business={business} shops={shops} />
}