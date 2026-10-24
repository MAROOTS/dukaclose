/** Cents -> "KSh 1,200" or "KSh 1,200.50". Works for fractional cents too (average costs). */
export function formatKsh(minor) {
    if (minor === null || minor === undefined) return '—'
    const shillings = Math.round(Number(minor)) / 100
    const rounded = Math.round(shillings * 100) / 100
    const hasCents = !Number.isInteger(rounded)
    return (
        'KSh ' +
        rounded.toLocaleString('en-KE', {
            minimumFractionDigits: hasCents ? 2 : 0,
            maximumFractionDigits: 2,
        })
    )
}

/**
 * "190", "190.5", "1,200.50" -> cents (19000, 19050, 120050). Returns null when invalid.
 * Done with string handling, never floating point.
 */
export function parseKshToMinor(text, maxMinor = 1_000_000_000) {
    const cleaned = String(text).replace(/[,\s]/g, '')
    if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null
    const [whole, fraction = ''] = cleaned.split('.')
    const minor = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
    return minor <= maxMinor ? minor : null
}

/** Cents -> text for an input box: 19000 -> "190", 19050 -> "190.50" */
export function minorToInput(minor) {
    if (minor === null || minor === undefined) return ''
    const shillings = Number(minor) / 100
    return Number.isInteger(shillings) ? String(shillings) : shillings.toFixed(2)
}