import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** Opens a page that works on the selected shop, after switching to the shop the record belongs to. */
export function useOpenInShop() {
    const { selectShop } = useAuth()
    const navigate = useNavigate()
    return useCallback((shopId, path) => {
        if (shopId) selectShop(shopId)
        navigate(path)
    }, [selectShop, navigate])
}