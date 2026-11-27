import { formatBusinessDate, formatShortDate } from './dates'
import { differenceInfo } from './closing'
import { formatKsh } from './money'
import { categoryLabel } from './moneyCategories'
import { formatReceiptNo, methodLabel } from './sales'
import { reasonLabel } from './stock'

export const PERIODS = [
    { value: 'today', label: 'Today' },
    { value: 'week', label: 'This week' },
    { value: 'month', label: 'This month' },
]

export const COMPARE_LABEL = {
    today: 'yesterday',
    week: 'the same days last week',
    month: 'the same days last month',
}

/** "2026-11-18" or "15 Nov to 18 Nov" */
export function rangeText(from, to) {
    return from === to ? formatBusinessDate(from) : `${formatShortDate(from)} to ${formatShortDate(to)}`
}

/** A link to the list behind a number. The list's total equals the number. */
export function recordsPath(kind, { from, to, shopId } = {}) {
    const q = new URLSearchParams()
    if (from) q.set('from', from)
    if (to) q.set('to', to)
    if (shopId) q.set('shopId', shopId)
    const text = q.toString()
    return `/owner/records/${kind}${text ? `?${text}` : ''}`
}

/** Percent change from before to now, or null when there is nothing to compare with. */
export function percentChange(now, before) {
    if (!before) return null
    return Math.round(((now - before) / Math.abs(before)) * 100)
}

export const SHOP_STATE = {
    OPEN: { label: 'Open', tone: 'ok' },
    CLOSED: { label: 'Closed today', tone: 'ok' },
    NOT_OPEN: { label: 'Not opened today', tone: 'quiet' },
    NEEDS_CLOSE: { label: 'Never closed', tone: 'bad' },
}

export const AUDIT_CATEGORIES = [
    { value: '', label: 'Everything' },
    { value: 'sales', label: 'Sales' },
    { value: 'deni', label: 'Deni' },
    { value: 'money', label: 'Money' },
    { value: 'stock', label: 'Stock' },
    { value: 'close', label: 'Closing' },
    { value: 'setup', label: 'Prices and items' },
    { value: 'access', label: 'Staff' },
]

const num = (x) => String(Number(x))
const limit = (x) => (x === null || x === undefined ? 'none' : formatKsh(x))

/** One plain sentence for an audit event. */
export function auditTitle(e) {
    const d = e.details ?? {}
    const b = d.before ?? {}
    const a = d.after ?? {}
    const receipt = e.receiptNo ? formatReceiptNo(e.receiptNo) : ''
    switch (e.kind) {
        case 'SALE_RECORDED': return `Sale ${receipt} recorded`
        case 'SALE_REVERSED': return `Sale ${receipt} reversed`
        case 'REFUND_RECORDED': return `Refund on sale ${receipt}`
        case 'DEBT_PAYMENT_RECORDED': return `Deni payment collected (${methodLabel(d.method)})`
        case 'DEBT_PAYMENT_REVERSED': return 'Deni payment reversed'
        case 'DEBT_WRITTEN_OFF': return `Deni written off (sale ${receipt})`
        case 'MONEY_IN_RECORDED': return `Money in: ${categoryLabel(d.category)}`
        case 'MONEY_OUT_RECORDED': return `Money out: ${categoryLabel(d.category)}`
        case 'MONEY_REVERSED': return `Money entry reversed: ${categoryLabel(d.category)}`
        case 'STOCK_OPENING': return `Starting stock: ${d.item}`
        case 'STOCK_RECEIVED': return `Delivery received: ${d.item}`
        case 'STOCK_ADJUSTED': return `Stock adjusted: ${d.item}`
        case 'DAY_OPENED': return `Day opened for ${formatShortDate(d.businessDate)}`
        case 'CASH_COUNTED': return 'Drawer counted'
        case 'DAY_CLOSED': return `Day closed for ${formatShortDate(d.businessDate)}`
        case 'PRICE_CHANGED': return `Price of ${a.item ?? b.item}: ${limit(b.priceMinor)} to ${limit(a.priceMinor)}`
        case 'REORDER_LEVEL_CHANGED': return `Low-stock level of ${a.item ?? b.item}: ${num(b.reorderLevel)} to ${num(a.reorderLevel)}`
        case 'CREDIT_LIMIT_CHANGED': return `Credit limit of ${a.customer ?? b.customer}: ${limit(b.creditLimitMinor)} to ${limit(a.creditLimitMinor)}`
        case 'ITEM_CREATED': return `Item added: ${a.name}`
        case 'ITEM_UPDATED': return `Item changed: ${b.name === a.name ? a.name : `${b.name} to ${a.name}`}`
        case 'ITEM_ARCHIVED': return `Item archived: ${a.name}`
        case 'ITEM_RESTORED': return `Item restored: ${a.name}`
        case 'SHOP_CREATED': return `Shop added: ${a.name}`
        case 'CASHIER_CREATED': return `Cashier added: ${a.displayName}`
        case 'CASHIER_ASSIGNED': return `${a.cashier} assigned to ${a.shop}`
        case 'CASHIER_UNASSIGNED': return `${a.cashier} removed from ${a.shop}`
        default: return e.kind
    }
}

/** A second line of detail, when there is something worth saying. */
export function auditDetail(e) {
    const d = e.details ?? {}
    switch (e.kind) {
        case 'REFUND_RECORDED':
            return d.debtCreditMinor > 0 ? `${formatKsh(d.debtCreditMinor)} was taken off what the customer owed` : null
        case 'STOCK_OPENING':
        case 'STOCK_RECEIVED':
        case 'STOCK_ADJUSTED': {
            const q = Number(d.quantity)
            const sign = q > 0 ? '+' : '−'
            return `${sign}${Math.abs(q)}${d.reason ? `, ${reasonLabel(d.reason)}` : ''}`
        }
        case 'CASH_COUNTED': return `Expected ${formatKsh(d.expectedMinor)}`
        case 'DAY_CLOSED': {
            const diff = differenceInfo(d.differenceMinor)
            return `Counted ${formatKsh(d.countedMinor)}. ${diff.label}`
        }
        case 'DAY_OPENED':
            return d.carriedOverMinor === null || d.carriedOverMinor === undefined
                ? null : `The last close counted ${formatKsh(d.carriedOverMinor)}`
        case 'MONEY_IN_RECORDED':
        case 'MONEY_OUT_RECORDED':
        case 'MONEY_REVERSED': return methodLabel(d.method)
        default: return null
    }
}

const AMOUNT_LABEL = {
    DAY_OPENED: 'Opening float', CASH_COUNTED: 'Counted', DAY_CLOSED: 'Sales',
    STOCK_OPENING: 'Cost', STOCK_RECEIVED: 'Cost',
}
export const auditAmountLabel = (e) => AMOUNT_LABEL[e.kind] ?? null

/** Where to open the thing an event is about, or null. */
export function auditTarget(e, fallbackShopId) {
    const shopId = e.shopId ?? fallbackShopId
    switch (e.entityType) {
        case 'SALE': return { shopId, path: `/sales/${e.entityId}` }
        case 'CUSTOMER': return { shopId, path: `/deni/${e.entityId}` }
        case 'ITEM':
        case 'SHOP_ITEM': return { shopId, path: `/stock/${e.entityId}` }
        case 'DAY_CLOSE':
            return e.details?.businessDate ? { shopId, path: `/closes/${e.details.businessDate}` } : null
        default: return null
    }
}