export const UNITS = {
    PIECE: { label: 'Pieces', short: 'pc', plural: 'pieces', decimals: 0 },
    KG: { label: 'Kilograms', short: 'kg', plural: 'kg', decimals: 3 },
    LITRE: { label: 'Litres', short: 'L', plural: 'litres', decimals: 3 },
}

export function formatQty(value, unit) {
    const text = Number(value).toLocaleString('en-KE', { maximumFractionDigits: 3 })
    return `${text} ${UNITS[unit]?.short ?? ''}`.trim()
}

const MAX_QTY = 99_999_999

/** Returns a positive number, or null when the text is not valid for this many decimals. */
export function parseQty(text, decimals, { allowZero = false } = {}) {
    const cleaned = String(text).trim()
    const pattern = decimals > 0 ? new RegExp(`^\\d+(\\.\\d{1,${decimals}})?$`) : /^\d+$/
    if (!pattern.test(cleaned)) return null
    const n = Number(cleaned)
    if (n > MAX_QTY) return null
    return n > 0 || (allowZero && n === 0) ? n : null
}

/** True when a computed quantity has no more decimals than the unit allows. */
export function fitsUnit(quantity, decimals) {
    const scaled = quantity * 10 ** decimals
    return Math.abs(scaled - Math.round(scaled)) < 1e-9
}