export default function Spinner({ className = 'pt-16', size = 'md', label = 'Loading' }) {
    return (
        <div role="status" aria-live="polite" className={`flex justify-center text-forest-600 ${className}`}>
            <span className={`app-spinner app-spinner--${size}`} aria-hidden="true" />
            <span className="sr-only">{label}</span>
        </div>
    )
}