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