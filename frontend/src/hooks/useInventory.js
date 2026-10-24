import { useCallback, useEffect, useRef, useState } from 'react'
import api, { errorMessage } from '../api/client'

export function useInventory(shopId) {
    const [rows, setRows] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const latest = useRef(0)

    const load = useCallback(async () => {
        if (!shopId) return
        const id = ++latest.current
        try {
            const { data } = await api.get(`/shops/${shopId}/inventory`)
            if (id !== latest.current) return // a newer request (e.g. another shop) took over
            setRows(data)
            setError('')
        } catch (err) {
            if (id !== latest.current) return
            setError(errorMessage(err, 'Could not load stock.'))
        } finally {
            if (id === latest.current) setLoading(false)
        }
    }, [shopId])

    useEffect(() => {
        setLoading(true)
        setRows([])
        load()
    }, [load])

    return { rows, loading, error, reload: load }
}