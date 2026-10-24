// "Njeri (Owner)" -> "Njeri"
const clean = (name) => name.replace(/\(.*?\)/g, '').trim()

export const firstName = (name) => clean(name).split(/\s+/)[0] || name

export const initials = (name) =>
    clean(name)
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0].toUpperCase())
        .join('')