import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import CollectForm from '../features/deni/CollectForm'
import CreditLimitForm from '../features/deni/CreditLimitForm'
import DebtList from '../features/deni/DebtList'
import PaymentList from '../features/deni/PaymentList'
import { formatReceiptNo } from '../lib/sales'
import { useShopDay } from '../hooks/useShopDay'
import { formatDateTime } from '../lib/dates'
import { formatKsh } from '../lib/money'
import { formatPhone } from '../lib/phone'
import Alert from '../ui/Alert'
import BackLink from '../ui/BackLink'

function Stat({ label, children, danger }) {
    return (
        <div>
            <dt className="text-sm text-ink-soft">{label}</dt>
            <dd className={`mt-1 font-display text-2xl font-medium tabular-nums ${danger ? 'text-ledger' : ''}`}>{children}</dd>
        </div>
    )
}

function Account({ shop, customerId }) {
    const { me } = useAuth()
    const { status } = useShopDay(shop.id)
    const [ledger, setLedger] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const isOwner = shop.role === 'OWNER'
    const openDate = status?.openDay?.businessDate ?? null

    const load = useCallback(async () => {
        try {
            const { data } = await api.get(`/shops/${shop.id}/customers/${customerId}/ledger`)
            setLedger(data)
            setError('')
        } catch (err) {
            setError(errorMessage(err, 'Could not load this account.'))
        } finally {
            setLoading(false)
        }
    }, [shop.id, customerId])

    useEffect(() => {
        load()
    }, [load])

    if (loading) {
        return (
            <div className="flex justify-center pt-16">
                <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
            </div>
        )
    }
    if (!ledger) {
        return (
            <div>
                <BackLink to="/deni">Deni</BackLink>
                <div className="mt-6"><Alert>{error || 'This customer was not found.'}</Alert></div>
            </div>
        )
    }

    const c = ledger.customer

    return (
        <div>
            <BackLink to="/deni">Deni</BackLink>
            <h1 className="mt-4 font-display text-4xl font-medium tracking-tight">{c.name}</h1>
            <p className="mt-2 text-ink-soft">{formatPhone(c.phone)}</p>

            {error && <div className="mt-6"><Alert>{error}</Alert></div>}

            <dl className="mt-8 grid grid-cols-2 gap-6 border-y border-line py-6">
                <Stat label="Owes">{formatKsh(c.outstandingMinor)}</Stat>
                <Stat label="Overdue" danger={ledger.overdueMinor > 0}>{formatKsh(ledger.overdueMinor)}</Stat>
            </dl>
            {c.overLimit && (
                <p className="mt-4 text-sm font-medium text-ledger">
                    Above the credit limit of {formatKsh(c.creditLimitMinor)}.
                </p>
            )}
            {isOwner && <CreditLimitForm key={String(c.creditLimitMinor)} businessId={shop.businessId} customer={c} onDone={load} />}

            <section className="mt-12">
                <h2 className="font-display text-2xl font-medium">Collect a payment</h2>
                <div className="mt-4">
                    {c.outstandingMinor > 0 ? (
                        <CollectForm
                            shopId={shop.id}
                            customerId={c.id}
                            customerName={c.name}
                            debts={ledger.debts}
                            outstandingMinor={c.outstandingMinor}
                            dayOpen={Boolean(openDate)}
                            onDone={load}
                        />
                    ) : (
                        <p className="text-ink-soft">{c.name} does not owe anything.</p>
                    )}
                </div>
            </section>

            <section className="mt-12">
                <h2 className="font-display text-2xl font-medium">Deni</h2>
                <div className="mt-4">
                    <DebtList debts={ledger.debts} businessId={shop.businessId} canWriteOff={isOwner} onDone={load} />
                </div>
            </section>

            <section className="mt-12">
                <h2 className="font-display text-2xl font-medium">Payments</h2>
                <div className="mt-4">
                    <PaymentList payments={ledger.payments} shop={shop} userId={me.user.id} openDate={openDate} onDone={load} />
                </div>
            </section>

            {ledger.writeOffs.length > 0 && (
                <section className="mt-12">
                    <h2 className="font-display text-2xl font-medium">Written off</h2>
                    <ul className="mt-4 divide-y divide-line border-y border-line">
                        {ledger.writeOffs.map((w, i) => (
                            <li key={`${w.debtId}-${i}`} className="flex items-start justify-between gap-4 py-4">
                                <div className="min-w-0">
                                    <p className="font-medium">{formatReceiptNo(w.receiptNo)}</p>
                                    <p className="text-sm text-ink-soft">{w.reason}</p>
                                    <p className="text-sm text-ink-soft">{w.writtenOffByName}, {formatDateTime(w.createdAt)}</p>
                                </div>
                                <p className="shrink-0 font-medium tabular-nums text-ledger">{formatKsh(w.amountMinor)}</p>
                            </li>
                        ))}
                    </ul>
                </section>
            )}
        </div>
    )
}

export default function CustomerPage() {
    const { customerId } = useParams()
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <Account key={`${selectedShop.id}:${customerId}`} shop={selectedShop} customerId={customerId} />
}