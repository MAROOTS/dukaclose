const format = new Intl.DateTimeFormat('en-KE', {
    timeZone: 'Africa/Nairobi',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
})

export const formatDateTime = (iso) => format.format(new Date(iso))