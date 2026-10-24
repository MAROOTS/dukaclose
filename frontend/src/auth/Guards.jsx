import { Loader2 } from 'lucide-react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RequireAuth() {
    const { me, loading } = useAuth()
    const location = useLocation()

    if (loading) {
        return (
            <div className="grid min-h-dvh place-items-center">
                <Loader2 className="h-7 w-7 animate-spin text-forest-600" aria-label="Loading" />
            </div>
        )
    }
    if (!me) return <Navigate to="/login" replace state={{ from: location }} />
    return <Outlet />
}

// Convenience only: the server enforces owner-only endpoints regardless of what the UI shows.
export function RequireOwner() {
    const { isOwner } = useAuth()
    return isOwner ? <Outlet /> : <Navigate to="/" replace />
}