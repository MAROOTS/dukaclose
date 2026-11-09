import { useCallback, useEffect, useMemo, useState } from 'react'
import { qtyToText } from '../lib/sales'

const storageKey = (shopId) => `duka.cart.${shopId}`

function read(shopId) {
    try {
        const parsed = JSON.parse(sessionStorage.getItem(storageKey(shopId)) ?? '[]')
        return Array.isArray(parsed)
            ? parsed.filter((l) => l && typeof l.itemId === 'string' && typeof l.qty === 'string')
            : []
    } catch {
        return []
    }
}

/** The sale being built. Quantities stay as text while typing ("0." is a valid half-typed number). */
export function useCart(shopId) {
    const [lines, setLines] = useState(() => read(shopId))

    useEffect(() => {
        try {
            sessionStorage.setItem(storageKey(shopId), JSON.stringify(lines))
        } catch {
            /* storage unavailable: the cart simply will not survive a refresh */
        }
    }, [shopId, lines])

    const add = useCallback((itemId) => {
        setLines((prev) => {
            const existing = prev.find((l) => l.itemId === itemId)
            if (!existing) return [...prev, { itemId, qty: '1' }]
            const current = Number(existing.qty)
            const next = qtyToText((Number.isFinite(current) ? current : 0) + 1)
            return prev.map((l) => (l.itemId === itemId ? { ...l, qty: next } : l))
        })
    }, [])

    const setQty = useCallback(
        (itemId, qty) => setLines((prev) => prev.map((l) => (l.itemId === itemId ? { ...l, qty } : l))),
        [],
    )
    const remove = useCallback((itemId) => setLines((prev) => prev.filter((l) => l.itemId !== itemId)), [])
    const removeMany = useCallback((ids) => setLines((prev) => prev.filter((l) => !ids.includes(l.itemId))), [])
    const clear = useCallback(() => setLines([]), [])

    return useMemo(
        () => ({ lines, add, setQty, remove, removeMany, clear }),
        [lines, add, setQty, remove, removeMany, clear],
    )
}