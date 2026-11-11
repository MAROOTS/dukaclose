export const OUT_CATEGORIES = [
    { value: 'TRANSPORT', label: 'Transport' },
    { value: 'LUNCH', label: 'Lunch' },
    { value: 'STOCK_PURCHASE', label: 'Stock purchase',
        hint: 'Goods bought for the shop. Record the delivery under Stock too. This is not counted as an expense.' },
    { value: 'OTHER_EXPENSE', label: 'Other expense' },
    { value: 'FLOAT_MOVEMENT', label: 'Cash taken out of the drawer', cashOnly: true,
        hint: 'For example money taken to the owner or the bank. Always cash. Not an expense.' },
]

export const IN_CATEGORY = {
    value: 'OTHER_CASH_RECEIVED',
    label: 'Cash received',
    cashOnly: true,
    hint: 'Cash put into the drawer that is not from a sale, like the owner adding float. Always cash.',
}

export const categoryLabel = (value) =>
    [...OUT_CATEGORIES, IN_CATEGORY].find((c) => c.value === value)?.label ?? value