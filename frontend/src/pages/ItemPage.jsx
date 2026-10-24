import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import AdjustForm from '../features/inventory/AdjustForm'
import History from '../features/inventory/History'
import PriceForm from '../features/inventory/PriceForm'
import ReceiveForm from '../features/inventory/ReceiveForm'
import { useInventory } from '../hooks/useInventory'
import { formatKsh } from '../lib/money'
import { UNITS, formatQty } from '../lib/units'
import Alert from '../ui/Alert'
import BackLink from '../ui/BackLink'
import Segmented from '../ui/Segmented'

function Stat({ label, children }) {
    return (
        <div>
            <dt className="text-sm text-ink-soft">{label}</dt>
            <dd className="mt-1 font-display text-2xl font-medium tabular-nums">{children}</dd>
        </div>
    )
}

export default function ItemPage() {
    const { itemId } = useParams()
    const { selectedShop } = useAuth()
    const shopId = selectedShop?.id
    const canManage = selectedShop?.role === 'OWNER'
    const { rows, loading, error, reload } = useInventory(shopId)
    const [movements, setMovements] = useState(null) // null = not loaded yet
    const [movementsError, setMovementsError] = useState('')
    const [tab, setTab] = useState('receive')

    const loadMovements = useCallback(async () => {
        if (!canManage || !shopId) return
        try {
            const { data } = await api.get(`/shops/${shopId}/inventory/${itemId}/movements`, { params: { limit: 50 } })
            setMovements(data)
            setMovementsError('')
        } catch (err) {
            setMovementsError(errorMessage(err, 'Could not load the history.'))
        }
    }, [canManage, shopId, itemId])

    useEffect(() => {
        loadMovements()
    }, [loadMovements])

    const refreshAll = () => Promise.all([reload(), loadMovements()])
    const item = rows.find((r) => r.itemId === itemId)

    if (!selectedShop) return <p className="text-ink-soft">Choose a shop first.</p>
    if (loading) {
        return (
            <div className="flex justify-center pt-16">
                <Loader2 className="h-6 w-6 animate-spin text-forest-600" aria-label="Loading" />
            </div>
        )
    }
    if (!item) {
        return (
            <div>
                <BackLink to="/stock">Stock</BackLink>
                <p className="mt-6 text-ink-soft">{error || 'This item was not found in the selected shop.'}</p>
            </div>
        )
    }

    const unit = UNITS[item.unit]
    const noCostYet = Number(item.onHand) === 0 && Number(item.averageCostMinor) === 0

    return (
        <div>
            <BackLink to="/stock">Stock</BackLink>
            <h1 className="mt-4 font-display text-4xl font-medium tracking-tight">{item.name}</h1>
            <p className="mt-2 text-ink-soft">
                Sold per {unit.short}
                {item.packName ? `. Bought by the ${item.packName.toLowerCase()} of ${formatQty(item.packSize, item.unit)}.` : '.'}
            </p>

            <dl className="mt-8 grid grid-cols-2 gap-x-6 gap-y-6 border-y border-line py-6">
                <Stat label="On hand">
                    <span className={item.lowStock ? 'text-ledger' : ''}>{formatQty(item.onHand, item.unit)}</span>
                    {item.lowStock && <span className="mt-1 block font-sans text-sm font-medium text-ledger">Low stock</span>}
                </Stat>
                <Stat label="Price">
                    {item.priceMinor === null ? 'Not set' : `${formatKsh(item.priceMinor)}`}
                </Stat>
                {canManage && (
                    <Stat label="Average cost">{noCostYet ? '—' : formatKsh(item.averageCostMinor)}</Stat>
                )}
                <Stat label="Reorder at">{formatQty(item.reorderLevel, item.unit)}</Stat>
            </dl>

            {canManage && (
                <section className="mt-8 max-w-md">
                    {movementsError && <div className="mb-4"><Alert>{movementsError}</Alert></div>}

                    {movements !== null && (
                        <>
                            <Segmented
                                label="Action"
                                value={tab}
                                onChange={setTab}
                                options={[
                                    { value: 'receive', label: 'Receive' },
                                    { value: 'adjust', label: 'Adjust' },
                                    { value: 'price', label: 'Price' },
                                    { value: 'history', label: 'History' },
                                ]}
                            />
                            <div className="mt-6">
                                {tab === 'receive' && (
                                    <ReceiveForm shopId={shopId} item={item} hasHistory={movements.length > 0} onDone={refreshAll} />
                                )}
                                {tab === 'adjust' && <AdjustForm shopId={shopId} item={item} onDone={refreshAll} />}
                                {tab === 'price' && <PriceForm shopId={shopId} item={item} onDone={refreshAll} />}
                                {tab === 'history' && <History movements={movements} unit={item.unit} />}
                            </div>
                        </>
                    )}
                </section>
            )}
        </div>
    )
}