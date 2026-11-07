import { useCallback, useEffect, useRef, useState } from 'react'
import api, { errorMessage } from '../api/client'

export function useShopDay(shopId) {
    const [status, setStatus] = useState(null) // { today, openDay }
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const latest = useRef(0)

    const load = useCallback(async () => {
        if (!shopId) return
        const id = ++latest.current
        try {
            const { data } = await api.get(`/shops/${shopId}/days/current`)
            if (id !== latest.current) return
            setStatus(data)
            setError('')
        } catch (err) {
            if (id !== latest.current) return
            setError(errorMessage(err, 'Could not load today’s book.'))
        } finally {
            if (id === latest.current) setLoading(false)
        }
    }, [shopId])

    useEffect(() => {
        setLoading(true)
        setStatus(null)
        load()
    }, [load])

    return { status, loading, error, reload: load }
}