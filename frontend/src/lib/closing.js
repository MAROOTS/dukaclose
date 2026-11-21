import { formatKsh } from './money'

/** The drawer difference in plain words. Negative means short, positive means over. */
export function differenceInfo(differenceMinor) {
    if (differenceMinor === null || differenceMinor === undefined) return null
    if (differenceMinor === 0) return { tone: 'ok', label: 'Matches', short: 'Matches' }
    const amount = formatKsh(Math.abs(differenceMinor))
    return differenceMinor < 0
        ? { tone: 'bad', label: `Short by ${amount}`, short: `−${amount}` }
        : { tone: 'bad', label: `Over by ${amount}`, short: `+${amount}` }
}

export const toneClass = (tone) => (tone === 'ok' ? 'text-forest-700' : 'text-ledger')