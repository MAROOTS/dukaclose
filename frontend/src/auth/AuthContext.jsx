import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import api, { setUnauthorizedHandler } from '../api/client'

const AuthContext = createContext(null)
const SHOP_KEY = 'duka.selectedShopId'

// The selected shop is only a UI preference, not a secret. The server still checks access on every request.
function readSavedShop() {
    try { return localStorage.getItem(SHOP_KEY) } catch { return null }
}
function saveShop(id) {
    try {
        if (id) localStorage.setItem(SHOP_KEY, id)
        else localStorage.removeItem(SHOP_KEY)
    } catch { /* storage unavailable: ignore */ }
}

export function AuthProvider({ children }) {
    const [me, setMe] = useState(null)
    const [loading, setLoading] = useState(true)
    const [savedShopId, setSavedShopId] = useState(readSavedShop)

    const refresh = useCallback(async () => {
        try {
            const { data } = await api.get('/me')
            setMe(data)
        } catch (error) {
            // Only a 401 means "not logged in". A network blip must not log the user out.
            if (error.response?.status === 401) setMe(null)
        }
    }, [])

    // On first load, ask the server whether we already have a session
    useEffect(() => {
        refresh().finally(() => setLoading(false))
    }, [refresh])

    // Let the axios interceptor clear the user when any call comes back 401
    useEffect(() => {
        setUnauthorizedHandler(() => setMe(null))
        return () => setUnauthorizedHandler(null)
    }, [])

    const login = useCallback(async (identifier, password) => {
        await api.get('/ping') // makes sure we hold a fresh CSRF cookie (logout deletes it)
        const { data } = await api.post('/auth/login', { identifier, password })
        setMe(data)
    }, [])

    const logout = useCallback(async () => {
        await api.post('/auth/logout') // if this fails, we do NOT pretend the user is logged out
        setMe(null)
    }, [])

    const selectShop = useCallback((id) => {
        setSavedShopId(id)
        saveShop(id)
    }, [])

    const value = useMemo(() => {
        const shops = me?.shops ?? []
        return {
            me,
            loading,
            login,
            logout,
            refresh,
            shops,
            isOwner: (me?.businesses?.length ?? 0) > 0,
            // fall back to the first shop if the saved one is gone or no longer allowed
            selectedShop: shops.find((s) => s.id === savedShopId) ?? shops[0] ?? null,
            selectShop,
        }
    }, [me, loading, login, logout, refresh, savedShopId, selectShop])

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
    const ctx = useContext(AuthContext)
    if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
    return ctx
}