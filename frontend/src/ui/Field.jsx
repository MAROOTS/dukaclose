import { Children, isValidElement, useEffect, useRef, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'

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

export function SelectField({
    label,
    id,
    children,
    value,
    defaultValue,
    onChange,
    disabled = false,
    labelClassName = 'mb-1.5 block text-sm font-medium text-ink',
    className = '',
    buttonClassName = '',
    size = 'md',
    ...buttonProps
}) {
    const rootRef = useRef(null)
    const triggerRef = useRef(null)
    const [open, setOpen] = useState(false)
    const [openUp, setOpenUp] = useState(false)
    const [activeIndex, setActiveIndex] = useState(0)
    const [uncontrolledValue, setUncontrolledValue] = useState(
        defaultValue === undefined ? undefined : String(defaultValue),
    )
    const options = Children.toArray(children)
        .filter((option) => isValidElement(option) && option.type === 'option')
        .map((option, index) => ({
            key: option.key ?? `${id}-${index}`,
            value: String(option.props.value ?? option.props.children ?? ''),
            label: option.props.children,
            disabled: Boolean(option.props.disabled),
        }))
    const selectedValue = value !== undefined
        ? String(value ?? '')
        : uncontrolledValue ?? String(defaultValue ?? options[0]?.value ?? '')
    const selectedIndex = options.findIndex((option) => option.value === selectedValue)
    const selectedOption = selectedIndex >= 0 ? options[selectedIndex] : null
    const listboxId = `${id}-listbox`
    const optionId = (index) => `${id}-option-${index}`
    const enabledIndexes = options.reduce((indexes, option, index) => {
        if (!option.disabled) indexes.push(index)
        return indexes
    }, [])
    const compact = size === 'sm'

    useEffect(() => {
        if (!open) return undefined

        function closeOnOutsidePointer(event) {
            if (!rootRef.current?.contains(event.target)) setOpen(false)
        }

        document.addEventListener('pointerdown', closeOnOutsidePointer)
        return () => document.removeEventListener('pointerdown', closeOnOutsidePointer)
    }, [open])

    function openMenu(index = selectedIndex) {
        const nextIndex = options[index] && !options[index].disabled ? index : enabledIndexes[0]
        const triggerRect = triggerRef.current?.getBoundingClientRect()
        if (triggerRect) {
            const spaceBelow = window.innerHeight - triggerRect.bottom
            const spaceAbove = triggerRect.top
            const estimatedMenuHeight = Math.min(options.length * 52 + 16, window.innerHeight * 0.45)
            setOpenUp(spaceBelow < estimatedMenuHeight + 8 && spaceAbove > spaceBelow)
        }
        setActiveIndex(nextIndex ?? 0)
        setOpen(true)
    }

    function moveActive(direction) {
        if (!enabledIndexes.length) return
        const currentPosition = enabledIndexes.indexOf(activeIndex)
        const start = currentPosition < 0 ? (direction > 0 ? -1 : 0) : currentPosition
        const nextPosition = (start + direction + enabledIndexes.length) % enabledIndexes.length
        setActiveIndex(enabledIndexes[nextPosition])
    }

    function choose(option) {
        if (!option || option.disabled) return
        if (value === undefined) setUncontrolledValue(option.value)
        onChange?.({ target: { value: option.value } })
        setOpen(false)
    }

    function handleKeyDown(event) {
        if (event.key === 'Tab') {
            setOpen(false)
            return
        }
        if (event.key === 'Escape' && open) {
            event.preventDefault()
            setOpen(false)
            return
        }
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            if (!open) openMenu()
            else moveActive(event.key === 'ArrowDown' ? 1 : -1)
            return
        }
        if ((event.key === 'Enter' || event.key === ' ') && open) {
            event.preventDefault()
            choose(options[activeIndex])
            return
        }
        if ((event.key === 'Enter' || event.key === ' ') && !open) {
            event.preventDefault()
            openMenu()
            return
        }
        if (open && (event.key === 'Home' || event.key === 'End') && enabledIndexes.length) {
            event.preventDefault()
            setActiveIndex(event.key === 'Home' ? enabledIndexes[0] : enabledIndexes.at(-1))
        }
    }

    return (
        <div ref={rootRef} className={`relative min-w-0 ${className}`}>
            <label id={`${id}-label`} htmlFor={id} className={labelClassName}>
                {label}
            </label>
            <button
                ref={triggerRef}
                id={id}
                type="button"
                role="combobox"
                aria-haspopup="listbox"
                aria-expanded={open}
                aria-controls={listboxId}
                aria-activedescendant={open ? optionId(activeIndex) : undefined}
                aria-labelledby={`${id}-label`}
                disabled={disabled}
                onClick={() => (open ? setOpen(false) : openMenu())}
                onKeyDown={handleKeyDown}
                className={`${compact
                    ? 'h-10 rounded-lg px-3 text-sm'
                    : 'h-12 rounded-xl px-4 text-base'} flex w-full min-w-0 items-center justify-between gap-3 border border-line-strong bg-surface text-left text-ink transition-[border-color,box-shadow,background-color] focus:border-forest-600 focus:outline-none focus:ring-4 focus:ring-forest-600/15 disabled:cursor-not-allowed disabled:opacity-60 ${buttonClassName}`}
                {...buttonProps}
            >
                <span className="min-w-0 flex-1 truncate">
                    {selectedOption?.label ?? <span className="text-ink-faint">Select an option</span>}
                </span>
                <ChevronDown
                    aria-hidden="true"
                    className={`h-[18px] w-[18px] shrink-0 text-ink-faint transition-transform ${open ? 'rotate-180' : ''}`}
                />
            </button>
            {open && (
                <div
                    id={listboxId}
                    role="listbox"
                    aria-labelledby={`${id}-label`}
                    className={`absolute left-0 right-0 z-50 max-h-[min(18rem,45dvh)] min-w-0 overflow-y-auto overscroll-contain rounded-2xl border border-line bg-surface p-1.5 shadow-[0_16px_40px_-16px_rgba(23,48,43,0.35)] ${
                        openUp ? 'bottom-full mb-2' : 'top-full mt-2'
                    }`}
                >
                    {options.map((option, index) => (
                        <div
                            key={option.key}
                            id={optionId(index)}
                            role="option"
                            aria-selected={index === selectedIndex}
                            aria-disabled={option.disabled || undefined}
                            onMouseDown={(event) => event.preventDefault()}
                            onMouseMove={() => !option.disabled && setActiveIndex(index)}
                            onClick={() => choose(option)}
                            className={`flex min-h-11 min-w-0 cursor-pointer items-start gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
                                option.disabled
                                    ? 'cursor-not-allowed opacity-50'
                                    : index === activeIndex
                                        ? 'bg-forest-50 text-forest-800'
                                        : 'text-ink hover:bg-forest-50/70'
                            } ${index === selectedIndex ? 'font-semibold' : ''}`}
                        >
                            <span className="min-w-0 flex-1 whitespace-normal break-words">{option.label}</span>
                            {index === selectedIndex && <Check className="mt-0.5 h-4 w-4 shrink-0 text-forest-700" aria-hidden="true" />}
                        </div>
                    ))}
                </div>
            )}
        </div>
    )
}