import { useState } from 'react'
import api, { errorMessage } from '../../api/client'
import { formatKsh, parseKshToMinor } from '../../lib/money'
import { UNITS, fitsUnit, formatQty, parseQty } from '../../lib/units'
import Alert from '../../ui/Alert'
import Button from '../../ui/Button'
import Field from '../../ui/Field'
import Notice from '../../ui/Notice'
import Segmented from '../../ui/Segmented'

const MAX_COST_MINOR = 10_000_000_000

export default function ReceiveForm({ shopId, item, hasHistory, onDone }) {
    const unit = UNITS[item.unit]
    const hasPack = Boolean(item.packName)

    const [kind, setKind] = useState('opening')
    const [by, setBy] = useState('unit')
    const [amount, setAmount] = useState('')
    const [total, setTotal] = useState('')
    const [note, setNote] = useState('')
    // One id per save attempt. It is reused on retries (so a double-tap or a lost response cannot count twice)
    // and replaced only after the save succeeds.
    const [requestId, setRequestId] = useState(() => crypto.randomUUID())
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const [done, setDone] = useState('')

    const effectiveKind = hasHistory ? 'receipts' : kind
    const byPack = hasPack && by === 'pack'

    // Live preview. For reassurance only: the server does the real arithmetic.
    const entered = parseQty(amount, byPack ? 3 : unit.decimals)
    const totalMinor = parseKshToMinor(total, MAX_COST_MINOR)
    const totalQty = entered === null ? null : byPack ? entered * Number(item.packSize) : entered
    const preview =
        totalQty && totalMinor !== null && fitsUnit(totalQty, unit.decimals)
            ? `${formatQty(totalQty, item.unit)} at ${formatKsh(totalMinor / totalQty)} per ${unit.short}`
            : null

    async function handleSubmit(event) {
        event.preventDefault()
        if (submitting) return
        setError('')
        setDone('')

        if (entered === null) {
            setError(`Enter how much you ${effectiveKind === 'opening' ? 'have' : 'received'}${byPack ? ' (in packs)' : ''}.`)
            return
        }
        if (!fitsUnit(totalQty, unit.decimals)) {
            setError(`That works out to ${totalQty} ${unit.plural}, which does not fit this item.`)
            return
        }
        if (totalMinor === null) {
            setError('Enter what you paid in total. Use 0 if it was free.')
            return
        }

        setSubmitting(true)
        try {
            const { data } = await api.post(`/shops/${shopId}/inventory/${item.itemId}/${effectiveKind}`, {
                requestId,
                quantity: byPack ? null : entered,
                packs: byPack ? entered : null,
                totalCostMinor: totalMinor,
                note: note.trim() || null,
            })
            setDone(`${formatQty(data.quantityDelta, item.unit)} added. On hand: ${formatQty(data.balanceAfter, item.unit)}.`)
            setRequestId(crypto.randomUUID())
            setAmount('')
            setTotal('')
            setNote('')
            await onDone()
        } catch (err) {
            setError(errorMessage(err, 'Could not save. Nothing was recorded.'))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="space-y-5">
            {!hasHistory && (
                <div className="space-y-2">
                    <Segmented
                        label="Type"
                        value={kind}
                        onChange={setKind}
                        options={[
                            { value: 'opening', label: 'Starting stock' },
                            { value: 'receipts', label: 'New delivery' },
                        ]}
                    />
                    {kind === 'opening' && (
                        <p className="text-sm text-ink-soft">
                            What is already on your shelf today. You can record this only once for each item.
                        </p>
                    )}
                </div>
            )}

            {hasPack && (
                <Segmented
                    label="Count in"
                    value={by}
                    onChange={(v) => { setBy(v); setAmount('') }}
                    options={[
                        { value: 'unit', label: `By ${unit.short}` },
                        { value: 'pack', label: `By ${item.packName.toLowerCase()}` },
                    ]}
                />
            )}

            <Field
                id="receive-amount"
                label={byPack ? `How many ${item.packName.toLowerCase()}s?` : `Quantity (${unit.short})`}
                inputMode="decimal"
                placeholder={byPack ? 'e.g. 2' : 'e.g. 50'}
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
            />

            <Field
                id="receive-total"
                label="Total paid (KSh)"
                inputMode="decimal"
                placeholder="e.g. 6000"
                hint={preview ? `That is ${preview}.` : 'The whole amount you paid, not the price of one.'}
                value={total}
                onChange={(e) => setTotal(e.target.value)}
            />

            <Field
                id="receive-note"
                label="Note, optional"
                maxLength={200}
                placeholder="e.g. Supplier name"
                value={note}
                onChange={(e) => setNote(e.target.value)}
            />

            {error && <Alert>{error}</Alert>}
            {done && <Notice>{done}</Notice>}

            <Button type="submit" loading={submitting}>
                {effectiveKind === 'opening' ? 'Save starting stock' : 'Save delivery'}
            </Button>
        </form>
    )
}