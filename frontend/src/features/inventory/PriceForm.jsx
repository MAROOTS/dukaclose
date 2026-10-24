import { useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { minorToInput, parseKshToMinor } from '../../lib/money'
import { UNITS, parseQty } from '../../lib/units'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'
import Notice from '../../ui/Notice'

export default function PriceForm({ shopId, item, onDone }) {
    const unit = UNITS[item.unit]
    const [price, setPrice] = useState(minorToInput(item.priceMinor))
    const [reorder, setReorder] = useState(String(item.reorderLevel))
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [done, setDone] = useState('')

    async function handleSubmit(event) {
        event.preventDefault()
        if (submitting) return
        setError('')
        setDone('')

        const priceMinor = parseKshToMinor(price)
        if (priceMinor === null) {
            setError('Enter the price as a number, like 190 or 190.50.')
            return
        }
        const reorderLevel = parseQty(reorder, unit.decimals, { allowZero: true })
        if (reorderLevel === null) {
            setError(`Enter the reorder level in ${unit.plural}. Use 0 for no warning.`)
            return
        }

        setSubmitting(true)
        try {
            await api.put(`/shops/${shopId}/inventory/${item.itemId}`, { priceMinor, reorderLevel })
            setDone('Saved.')
            await onDone()
        } catch (err) {
            setError(errorMessage(err, 'Could not save. Nothing was changed.'))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="space-y-5">
            <Field
                id="price"
                label={`Selling price per ${unit.short} (KSh)`}
                inputMode="decimal"
                hint="This price is for this shop only."
                value={price}
                onChange={(e) => setPrice(e.target.value)}
            />
            <Field
                id="reorder"
                label={`Warn me when stock falls to (${unit.short})`}
                inputMode="decimal"
                hint="Items at or below this level show as low stock."
                value={reorder}
                onChange={(e) => setReorder(e.target.value)}
            />

            {error && <Alert>{error}</Alert>}
            {done && <Notice>{done}</Notice>}

            <Button type="submit" loading={submitting}>Save</Button>
        </form>
    )
}