import { ChevronDown } from 'lucide-react'

const control =
    'h-12 w-full rounded-xl border border-line-strong bg-surface text-base text-ink placeholder:text-ink-faint ' +
    'transition-shadow focus:border-forest-600 focus:outline-none focus:ring-4 focus:ring-forest-600/15'

export default function Field({ label, id, icon: Icon, trailing, hint, ...input }) {
    return (
        <div>
            <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink">
                {label}
            </label>
            <div className="relative">
                {Icon && (
                    <Icon
                        aria-hidden="true"
                        className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-faint"
                    />
                )}
                <input
                    id={id}
                    {...input}
                    className={`${control} ${Icon ? 'pl-11' : 'pl-4'} ${trailing ? 'pr-12' : 'pr-4'}`}
                />
                {trailing && <div className="absolute right-1.5 top-1/2 -translate-y-1/2">{trailing}</div>}
            </div>
            {hint && <p className="mt-1.5 text-sm text-ink-soft">{hint}</p>}
        </div>
    )
}

export function SelectField({ label, id, children, ...select }) {
    return (
        <div>
            <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink">
                {label}
            </label>
            <div className="relative">
                <select id={id} {...select} className={`${control} appearance-none pl-4 pr-11`}>
                    {children}
                </select>
                <ChevronDown
                    aria-hidden="true"
                    className="pointer-events-none absolute right-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-faint"
                />
            </div>
        </div>
    )
}