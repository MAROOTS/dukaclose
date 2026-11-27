import Field, { SelectField } from '../../ui/Field'

/** Dates and shop. An empty date means "the server's default" (today, or the last seven days for History). */
export default function Filters({ idPrefix, from, to, onFrom, onTo, max, shops, shopId, onShop }) {
    return (
        <div className="grid gap-4 sm:grid-cols-3">
            <Field id={`${idPrefix}-from`} label="From" type="date" max={max} value={from} onChange={(e) => onFrom(e.target.value)} />
            <Field id={`${idPrefix}-to`} label="To" type="date" max={max} value={to} onChange={(e) => onTo(e.target.value)} />
            {shops.length > 1 && (
                <SelectField id={`${idPrefix}-shop`} label="Shop" value={shopId} onChange={(e) => onShop(e.target.value)}>
                    <option value="">All shops</option>
                    {shops.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </SelectField>
            )}
        </div>
    )
}