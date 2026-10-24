import { CircleAlert } from 'lucide-react'

export default function Alert({ children }) {
    return (
        <div
            role="alert"
            className="flex gap-2.5 rounded-xl border border-ledger/20 bg-ledger-50 px-3.5 py-3 text-sm text-ledger-700"
        >
            <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{children}</span>
        </div>
    )
}