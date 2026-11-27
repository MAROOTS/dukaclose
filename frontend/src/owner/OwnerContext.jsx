import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { useAuth } from '../auth/AuthContext'

const OwnerContext = createContext(null)
const KEY = 'duka.ownerBusinessId'

function read() {
    try { return localStorage.getItem(KEY) } catch { return null }
}

export function OwnerProvider({ children }) {
    const { me, shops } = useAuth()
    const businesses = me.businesses
    const [savedId, setSavedId] = useState(read)

    const choose = useCallback((id) => {
        setSavedId(id)
        try { localStorage.setItem(KEY, id) } catch { /* the choice just will not be remembered */ }
    }, [])

    const value = useMemo(() => {
        const business = businesses.find((b) => b.id === savedId) ?? businesses[0] ?? null
        return { businesses, business, shops: shops.filter((s) => s.businessId === business?.id), choose }
    }, [businesses, shops, savedId, choose])

    return <OwnerContext.Provider value={value}>{children}</OwnerContext.Provider>
}

export function useOwner() {
    const ctx = useContext(OwnerContext)
    if (!ctx) throw new Error('useOwner must be used inside <OwnerProvider>')
    return ctx
}