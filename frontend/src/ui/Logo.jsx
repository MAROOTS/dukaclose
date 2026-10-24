export default function Logo({ size = 36, onDark = false }) {
    const tile = onDark ? '#ffffff' : '#0f5a47'
    const rule = onDark ? '#0f5a47' : '#ffffff'
    return (
        <svg width={size} height={size} viewBox="0 0 36 36" role="img" aria-label="Duka Close">
            <rect width="36" height="36" rx="10" fill={tile} />
            <path d="M11 9.5v17" stroke="#c9972f" strokeWidth="2" strokeLinecap="round" />
            <path d="M16 13.5h10M16 18h10M16 22.5h6.5" stroke={rule} strokeWidth="2" strokeLinecap="round" />
        </svg>
    )
}