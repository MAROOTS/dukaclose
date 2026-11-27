import { useCallback, useEffect, useRef, useState } from 'react'
import api, { errorMessage } from '../api/client'

/** GET with a "latest request wins" guard. params is compared by value. */
export function useGet(path, params) {
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(Boolean(path))
    const [error, setError] = useState('')
    const latest = useRef(0)
    const key = JSON.stringify(params ?? {})

    const load = useCallback(async () => {
        if (!path) return
        const id = ++latest.current
        setLoading(true)
        try {
            const { data: body } = await api.get(path, { params: JSON.parse(key) })
            if (id !== latest.current) return
            setData(body)
            setError('')
        } catch (err) {
            if (id !== latest.current) return
            setError(errorMessage(err, 'Could not load this.'))
        } finally {
            if (id === latest.current) setLoading(false)
        }
    }, [path, key])

    // Another business or shop must never show the previous one's numbers while loading
    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setData(null)
    }, [path])

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        load()
    }, [load])

    return { data, loading, error, reload: load }
}