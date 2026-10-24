import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { parseKshToMinor } from '../lib/money'
import { UNITS, parseQty } from '../lib/units'
import Alert from '../ui/Alert'
import BackLink from '../ui/BackLink'
import Button from '../ui/Button'
import Field from '../ui/Field'
import Segmented from '../ui/Segmented'

export default function NewItemPage() {
    const { me, selectedShop } = useAuth()
    const navigate = useNavigate()
    const [name, setName] = useState('')
    const [unit, setUnit] = useState('PIECE')
    const [hasPack, setHasPack] = useState(false)
    const [packName, setPackName] = useState('')
    const [packSize, setPackSize] = useState('')
    const [price, setPrice] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')

    if (!selectedShop || selectedShop.role !== 'OWNER') return <Navigate to="/stock" replace />

    const businessId = selectedShop.businessId
    const businessName = me.businesses.find((b) => b.id === businessId)?.name
    const u = UNITS[unit]

    async function handleSubmit(event) {
        event.preventDefault()
        if (submitting) return
        setError('')

        let priceMinor = null
        if (price.trim()) {
            priceMinor = parseKshToMinor(price)
            if (priceMinor === null) {
                setError('Enter the price as a number, like 190 or 190.50.')
                return
            }
        }
        let size = null
        if (hasPack) {
            size = parseQty(packSize, u.decimals)
            if (!packName.trim() || size === null) {
                setError('Give the pack a name and a size, like Sack and 50.')
                return
            }
        }

        setSubmitting(true)
        try {
            await api.post(`/businesses/${businessId}/items`, {
                name,
                unit,
                packName: hasPack ? packName : null,
                packSize: size,
                initialPriceMinor: priceMinor,
            })
            navigate('/stock')
        } catch (err) {
            setError(errorMessage(err, 'Could not add the item.'))
            setSubmitting(false)
        }
    }

    return (
        <div>
            <BackLink to="/stock">Stock</BackLink>
            <h1 className="mt-4 font-display text-4xl font-medium tracking-tight">Add an item</h1>
            <p className="mt-2 text-ink-soft">
                Added to {businessName}, so it is available in all your shops.
            </p>

            <form onSubmit={handleSubmit} className="mt-8 max-w-md space-y-6">
                <Field
                    id="item-name"
                    label="Item name"
                    required
                    maxLength={120}
                    placeholder="e.g. Sugar"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                />

                <div>
                    <span className="mb-1.5 block text-sm font-medium">How do you sell it?</span>
                    <Segmented
                        label="Unit"
                        value={unit}
                        onChange={setUnit}
                        options={Object.entries(UNITS).map(([value, { label }]) => ({ value, label }))}
                    />
                </div>

                <div>
                    <label className="flex items-start gap-3 text-sm">
                        <input
                            type="checkbox"
                            checked={hasPack}
                            onChange={(e) => setHasPack(e.target.checked)}
                            className="mt-0.5 h-5 w-5 rounded border-line-strong accent-forest-600"
                        />
                        <span>
              <span className="font-medium">I buy this in packs</span>
              <span className="block text-ink-soft">Sacks, cartons, crates. Makes receiving stock faster.</span>
            </span>
                    </label>

                    {hasPack && (
                        <div className="mt-4 space-y-4">
                            <Field
                                id="pack-name"
                                label="Pack name"
                                maxLength={40}
                                placeholder="e.g. Sack"
                                value={packName}
                                onChange={(e) => setPackName(e.target.value)}
                            />
                            <Field
                                id="pack-size"
                                label={`How many ${u.plural} in one ${packName.trim().toLowerCase() || 'pack'}?`}
                                inputMode="decimal"
                                placeholder="e.g. 50"
                                value={packSize}
                                onChange={(e) => setPackSize(e.target.value)}
                            />
                        </div>
                    )}
                </div>

                <Field
                    id="item-price"
                    label={`Selling price per ${u.short} (KSh), optional`}
                    inputMode="decimal"
                    placeholder="e.g. 190"
                    hint="Applies to every shop for now. You can change it per shop later."
                    value={price}
                    onChange={(e) => setPrice(e.target.value)}
                />

                {error && <Alert>{error}</Alert>}

                <Button type="submit" loading={submitting}>Add item</Button>
            </form>
        </div>
    )
}