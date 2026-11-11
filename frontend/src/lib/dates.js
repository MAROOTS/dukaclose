const format = new Intl.DateTimeFormat('en-KE', {
    timeZone: 'Africa/Nairobi',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
})

export const formatDateTime = (iso) => format.format(new Date(iso))

const dayFormat = new Intl.DateTimeFormat('en-KE', {
    timeZone: 'Africa/Nairobi',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
})

/** "2026-10-23" -> "Friday 23 October". Noon in Nairobi avoids any off-by-one-day surprise. */
export const formatBusinessDate = (isoDate) => dayFormat.format(new Date(`${isoDate}T12:00:00+03:00`))

const timeFormat = new Intl.DateTimeFormat('en-KE', {
    timeZone: 'Africa/Nairobi',
    hour: '2-digit',
    minute: '2-digit',
})

export const formatTime = (iso) => timeFormat.format(new Date(iso))

const shortDay = new Intl.DateTimeFormat('en-KE', {
    timeZone: 'Africa/Nairobi',
    day: 'numeric',
    month: 'short',
})

/** "2026-11-06" -> "6 Nov" */
export const formatShortDate = (isoDate) => shortDay.format(new Date(`${isoDate}T12:00:00+03:00`))

/** "2026-10-23" + 14 -> "2026-11-06". Pure calendar arithmetic, so no time zone can shift it. */
export function addDays(isoDate, days) {
    const date = new Date(`${isoDate}T00:00:00Z`)
    date.setUTCDate(date.getUTCDate() + days)
    return date.toISOString().slice(0, 10)
}