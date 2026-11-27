import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import Spinner from '../ui/Spinner'

export function RequireAuth() {
    const { me, loading } = useAuth()
    const location = useLocation()

    if (loading) {
        return <Spinner className="min-h-dvh items-center" size="lg" />
    }
    if (!me) return <Navigate to="/login" replace state={{ from: location }} />
    return <Outlet />
}

// Convenience only: the server enforces owner-only endpoints regardless of what the UI shows.
export function RequireOwner() {
    const { isOwner } = useAuth()
    return isOwner ? <Outlet /> : <Navigate to="/" replace />
}