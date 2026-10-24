export const REASONS = [
    { value: 'DAMAGED', label: 'Damaged', direction: 'remove' },
    { value: 'EXPIRED', label: 'Expired', direction: 'remove' },
    { value: 'LOST', label: 'Lost or stolen', direction: 'remove' },
    { value: 'FOUND', label: 'Found extra', direction: 'add' },
    { value: 'COUNT_CORRECTION', label: 'Stock count correction', direction: 'either' },
    { value: 'OTHER', label: 'Other', direction: 'either' },
]

export const reasonLabel = (value) => REASONS.find((r) => r.value === value)?.label ?? value