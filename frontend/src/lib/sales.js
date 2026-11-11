import { formatDateTime, formatShortDate } from './dates'
import { formatKsh } from './money'
import { formatQty, parseQty, UNITS } from './units'

export const METHODS = [
    { value: 'CASH', label: 'Cash' },
    { value: 'POCHI', label: 'Pochi' },
    { value: 'TILL', label: 'Till' },
]

export const SALE_METHODS = [...METHODS, { value: 'DENI', label: 'Deni (on credit)' }]


const LABELS = { CASH: 'Cash', POCHI: 'Pochi', TILL: 'Till', DENI: 'Deni' }
export const methodLabel = (method) => LABELS[method] ?? method

export const formatReceiptNo = (n) => `#${String(n).padStart(4, '0')}`

/** Number -> tidy text, avoiding float noise like 2.3000000000000003 */
export const qtyToText = (n) => String(Number(Number(n).toFixed(3)))

/**
 * Line total in cents: quantity x price, rounded half up, like the server.
 * A preview only. The server calculates the real total.
 */
export function lineTotalMinor(quantity, priceMinor) {
    const thousandths = Math.round(quantity * 1000)
    return Math.round((thousandths * priceMinor) / 1000)
}

/** Cart lines + current stock rows -> lines with parsed quantity, a problem message, and a line total. */
export function describeLines(lines, byId) {
    return lines.map((line) => {
        const row = byId.get(line.itemId)
        const decimals = UNITS[row.unit].decimals
        const qty = parseQty(line.qty, decimals)
        let problem = null
        if (qty === null) problem = decimals === 0 ? 'Enter a whole number.' : 'Enter a quantity, like 0.5.'
        else if (row.priceMinor === null) problem = 'This item has no price yet. Ask the owner.'
        else if (qty > Number(row.onHand)) problem = `Only ${formatQty(row.onHand, row.unit)} on hand.`
        return {
            ...line,
            row,
            qty,
            problem,
            totalMinor: problem === null ? lineTotalMinor(qty, row.priceMinor) : null,
        }
    })
}

/** Total of the sale in cents, or null while any line has a problem. */
export function sumLines(described) {
    if (described.some((d) => d.problem)) return null
    return described.reduce((sum, d) => sum + d.totalMinor, 0)
}

export function receiptText(sale, shopName) {
    const lines = sale.lines.map(
        (l) => `${formatQty(l.quantity, l.unit)} ${l.name} @ ${formatKsh(l.unitPriceMinor)} = ${formatKsh(l.lineTotalMinor)}`,
    )
    const paid = sale.allocations
        .filter((a) => a.method !== 'DENI')
        .map((a) => `${methodLabel(a.method)} ${formatKsh(a.amountMinor)}`)
        .join(', ')
    const deni = sale.deni
        ? [`On deni: ${formatKsh(sale.deni.amountMinor)} for ${sale.deni.customerName}, due ${formatShortDate(sale.deni.dueDate)}`]
        : []
    return [
        shopName,
        `Receipt ${formatReceiptNo(sale.receiptNo)}, ${formatDateTime(sale.createdAt)}`,
        '',
        ...lines,
        '',
        `Total: ${formatKsh(sale.totalMinor)}`,
        ...(paid ? [`Paid: ${paid}`] : []),
        ...deni,
        '',
        'Asante!',
    ].join('\n')
}