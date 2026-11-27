import { useCallback, useEffect, useRef, useState } from 'react'
import api, { errorMessage } from '../api/client'

/** A paginated list that grows with "Show more". meta is the whole last response (totals, summaryMinor, ...). */
export function usePaged(path, params, pageSize = 20) {
    const [items, setItems] = useState([])
    const [meta, setMeta] = useState(null)
    const [loading, setLoading] = useState(Boolean(path))
    const [loadingMore, setLoadingMore] = useState(false)
    const [error, setError] = useState('')
    const latest = useRef(0)
    const key = JSON.stringify(params ?? {})

    const load = useCallback(async (page) => {
        if (!path) return
        const id = ++latest.current
        if (page === 0) setLoading(true)
        else setLoadingMore(true)
        try {
            const { data } = await api.get(path, { params: { ...JSON.parse(key), page, size: pageSize } })
            if (id !== latest.current) return
            setItems((prev) => (page === 0 ? data.items : [...prev, ...data.items]))
            setMeta(data)
            setError('')
        } catch (err) {
            if (id !== latest.current) return
            setError(errorMessage(err, 'Could not load the list.'))
            if (page === 0) {
                setItems([])
                setMeta(null)
            }
        } finally {
            if (id === latest.current) {
                setLoading(false)
                setLoadingMore(false)
            }
        }
    }, [path, key, pageSize])

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        load(0)
    }, [load])

    const hasMore = Boolean(meta) && meta.page + 1 < meta.totalPages
    return { items, meta, loading, loadingMore, error, hasMore, more: () => load(meta.page + 1) }
}