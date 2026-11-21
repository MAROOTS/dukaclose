import { useCallback, useEffect, useRef, useState } from 'react'
import api, { errorMessage } from '../api/client'

export function useClosePreview(shopId) {
    const [preview, setPreview] = useState(null)
    const [noOpenDay, setNoOpenDay] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const latest = useRef(0)

    const load = useCallback(async () => {
        const id = ++latest.current
        try {
            const { data } = await api.get(`/shops/${shopId}/days/current/close-preview`)
            if (id !== latest.current) return null
            setPreview(data)
            setNoOpenDay(false)
            setError('')
            return data
        } catch (err) {
            if (id !== latest.current) return null
            if (err.response?.data?.code === 'NO_OPEN_DAY') {
                setPreview(null)
                setNoOpenDay(true)
                setError('')
            } else {
                setError(errorMessage(err, 'Could not load the close preview.')) // keep any earlier preview on screen
            }
            return null
        } finally {
            if (id === latest.current) setLoading(false)
        }
    }, [shopId])

    useEffect(() => {
        setLoading(true)
        setPreview(null)
        setNoOpenDay(false)
        load()
    }, [load])

    return { preview, noOpenDay, loading, error, reload: load }
}