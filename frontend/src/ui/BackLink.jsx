import { ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function BackLink({ to, children }) {
    return (
        <Link to={to} className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-soft hover:text-ink">
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            {children}
        </Link>
    )
}