export default function Segmented({ options, value, onChange, label }) {
    return (
        <div role="radiogroup" aria-label={label} className="flex w-full rounded-xl bg-ink/[0.06] p-1">
            {options.map((option) => {
                const selected = option.value === value
                return (
                    <button
                        key={option.value}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        onClick={() => onChange(option.value)}
                        className={`h-10 flex-1 rounded-lg px-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-forest-600 ${
                            selected ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
                        }`}
                    >
                        {option.label}
                    </button>
                )
            })}
        </div>
    )
}