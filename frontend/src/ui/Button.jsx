import { Loader2 } from 'lucide-react'

const base =
    'inline-flex h-12 items-center justify-center gap-2 rounded-xl px-5 text-[15px] font-semibold transition-colors ' +
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600 ' +
    'disabled:cursor-not-allowed disabled:opacity-55'

const variants = {
    primary: 'bg-forest-600 text-white hover:bg-forest-700 active:bg-forest-800',
    quiet: 'text-ink hover:bg-ink/5',
}

/** Use on <Link> elements that should look like buttons. */
export const buttonClass = (variant = 'primary') => `${base} ${variants[variant]}`

export default function Button({ variant = 'primary', loading = false, className = '', disabled, children, ...props }) {
    return (
        <button className={`${buttonClass(variant)} ${className}`} disabled={disabled || loading} {...props}>
            {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {children}
        </button>
    )
}