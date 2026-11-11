const MOBILE = /^(?:\+?254|0)([17]\d{8})$/

/** "0712 345 678" -> "+254712345678", or null when it is not a Kenyan mobile number. Mirrors the server. */
export function normalisePhone(text) {
    const cleaned = String(text ?? '').replace(/[\s\-()]/g, '')
    const match = MOBILE.exec(cleaned)
    return match ? `+254${match[1]}` : null
}

export const isValidPhone = (text) => normalisePhone(text) !== null

/** "+254712345678" -> "0712 345 678" */
export function formatPhone(phone) {
    const match = /^\+254(\d{3})(\d{3})(\d{3})$/.exec(phone ?? '')
    return match ? `0${match[1]} ${match[2]} ${match[3]}` : phone ?? ''
}