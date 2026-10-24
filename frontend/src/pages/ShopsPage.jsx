import { useState } from 'react'
import { Plus } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../ui/Alert'
import Button from '../ui/Button'
import Field, { SelectField } from '../ui/Field'

export default function ShopsPage() {
    const { me, shops, refresh } = useAuth()
    const businesses = me.businesses
    const [businessId, setBusinessId] = useState(businesses[0]?.id ?? '')
    const [name, setName] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')

    async function handleCreate(event) {
        event.preventDefault()
        if (submitting) return
        setError('')
        setSubmitting(true)
        try {
            await api.post('/shops', { businessId, name })
            setName('')
            await refresh() // reload /me so the new shop appears everywhere
        } catch (err) {
            setError(errorMessage(err, 'Could not add the shop.'))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div>
            <h1 className="font-display text-4xl font-medium tracking-tight">Shops</h1>
            <p className="mt-2 text-ink-soft">Every branch you own, each with its own stock and daily close.</p>

            {shops.length === 0 ? (
                <p className="mt-8 border-y border-line py-6 text-ink-soft">No shops yet. Add your first one below.</p>
            ) : (
                <ul className="mt-8 divide-y divide-line border-y border-line">
                    {shops.map((shop) => (
                        <li key={shop.id} className="py-4">
                            <div className="font-medium">{shop.name}</div>
                            <div className="text-sm text-ink-soft">
                                {businesses.find((b) => b.id === shop.businessId)?.name}
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            <form onSubmit={handleCreate} className="mt-12 max-w-sm space-y-5">
                <h2 className="font-display text-2xl font-medium">Add a shop</h2>

                {businesses.length > 1 && (
                    <SelectField id="business" label="Business" value={businessId} onChange={(e) => setBusinessId(e.target.value)}>
                        {businesses.map((b) => (
                            <option key={b.id} value={b.id}>{b.name}</option>
                        ))}
                    </SelectField>
                )}

                <Field
                    id="shop-name"
                    label="Shop name"
                    required
                    maxLength={120}
                    placeholder="e.g. Ruiru Branch"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                />

                {error && <Alert>{error}</Alert>}

                <Button type="submit" loading={submitting}>
                    {!submitting && <Plus className="h-4 w-4" aria-hidden="true" />}
                    Add shop
                </Button>
            </form>
        </div>
    )
}