import { Check } from 'lucide-react'

export default function Notice({ children }) {
    return (
        <div role="status" className="flex gap-2.5 rounded-xl border border-forest-600/20 bg-forest-50 px-3.5 py-3 text-sm text-forest-800">
            <Check className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{children}</span>
        </div>
    )
}