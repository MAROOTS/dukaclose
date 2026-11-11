import { useCallback, useEffect, useState } from 'react'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Receipt, { ReceiptActions } from '../features/sales/Receipt'
import ReverseForm from '../features/sales/ReverseForm'
import { useShopDay } from '../hooks/useShopDay'
import Alert from '../ui/Alert'
import BackLink from '../ui/BackLink'
import { Link, useParams } from 'react-router-dom'
import { buttonClass } from '../ui/Button'

function SaleDetail({ shop, saleId }) {
    const { me } = useAuth()
    const { status } = useShopDay(shop.id)
    const [sale, setSale] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    const load = useCallback(async () => {
        try {
            const { data } = await api.get(`/shops/${shop.id}/sales/${saleId}`)
            setSale(data)
            setError('')
        } catch (err) {
            setError(errorMessage(err, 'Could not load this sale.'))
        } finally {
            setLoading(false)
        }
    }, [shop.id, saleId])

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
    if (!sale) {
        return (
            <div>
                <BackLink to="/sales">Sales</BackLink>
                <div className="mt-6"><Alert>{error || 'This sale was not found in the selected shop.'}</Alert></div>
            </div>
        )
    }

    const dayOpen = Boolean(status?.openDay) && status.openDay.businessDate === sale.businessDate
    const mayReverse = shop.role === 'OWNER' || sale.cashierId === me.user.id
    const canReverse = sale.status === 'COMPLETED' && dayOpen && mayReverse

    let why = ''
    if (sale.status === 'COMPLETED' && status && !canReverse) {
        why = !dayOpen
            ? 'This sale is from a day that is no longer open, so it cannot be reversed here.'
            : 'Only the cashier who recorded this sale, or the owner, can reverse it.'
    }

    return (
        <div>
            <div className="no-print"><BackLink to="/sales">Sales</BackLink></div>
            <div className="mt-4 max-w-md"><Receipt sale={sale} shopName={shop.name} /></div>
            <div className="no-print mt-6 flex flex-wrap gap-3">
                <ReceiptActions sale={sale} shopName={shop.name} />
            </div>
            {sale.deni && (
                <div className="no-print mt-4">
                    <Link to={`/deni/${sale.deni.customerId}`} className={`${buttonClass('quiet')} border border-line-strong`}>
                        View {sale.deni.customerName}’s account
                    </Link>
                </div>
            )}
            <div className="mt-8">
                <ReverseForm url={`/shops/${shop.id}/sales/${sale.id}/reverse`} noun="sale" onDone={load} />
                {why && <p className="no-print max-w-md text-sm text-ink-soft">{why}</p>}
            </div>
        </div>
    )
}

export default function SalePage() {
    const { saleId } = useParams()
    const { selectedShop } = useAuth()
    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    return <SaleDetail key={`${selectedShop.id}:${saleId}`} shop={selectedShop} saleId={saleId} />
}