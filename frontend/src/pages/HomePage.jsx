import { Check } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { firstName } from '../lib/names'
import { buttonClass } from '../ui/Button'

// Display only. The server decides which business day a transaction belongs to.
const dateFormat = new Intl.DateTimeFormat('en-KE', {
    timeZone: 'Africa/Nairobi',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
})

export default function HomePage() {
    const { me, isOwner, shops, selectedShop, selectShop } = useAuth()

    if (!selectedShop) {
        return (
            <div className="max-w-md">
                <h1 className="font-display text-4xl font-medium tracking-tight">No shop yet</h1>
                <p className="mt-3 text-ink-soft">
                    {isOwner
                        ? 'Add your first shop to start your evening book.'
                        : 'You are not assigned to a shop yet. Ask the owner to add you.'}
                </p>
                {isOwner && (
                    <Link to="/shops" className={`${buttonClass()} mt-6`}>
                        Add a shop
                    </Link>
                )}
            </div>
        )
    }

    const businessName = (id) => me.businesses.find((b) => b.id === id)?.name

    return (
        <div>
            <p className="text-ink-soft">{dateFormat.format(new Date())}</p>
            <h1 className="mt-1 font-display text-4xl font-medium tracking-tight sm:text-5xl">
                Habari, {firstName(me.user.displayName)}
            </h1>
            <p className="mt-3 text-ink-soft">
                You are working in {selectedShop.name} as {selectedShop.role === 'OWNER' ? 'the owner' : 'a cashier'}.
            </p>

            <section className="ledger-paper relative mt-10 overflow-hidden rounded-2xl border border-line bg-surface py-9 pl-14 pr-6 sm:pl-20 sm:pr-10">
                <span aria-hidden="true" className="absolute inset-y-0 left-8 w-px bg-ledger/30 sm:left-12" />
                <h2 className="font-display text-2xl font-medium">Today’s book is empty</h2>
                <p className="mt-2 max-w-sm text-ink-soft">
                    Sales, deni and money out will appear here as soon as you start recording.
                </p>
            </section>

            {isOwner && shops.length > 0 && (
                <section className="mt-12">
                    <h2 className="font-display text-2xl font-medium">Your shops</h2>
                    <ul className="mt-4 divide-y divide-line border-y border-line">
                        {shops.map((shop) => {
                            const active = shop.id === selectedShop.id
                            return (
                                <li key={shop.id}>
                                    <button
                                        type="button"
                                        onClick={() => selectShop(shop.id)}
                                        aria-pressed={active}
                                        className="flex w-full items-center justify-between gap-4 py-4 text-left transition-colors hover:bg-ink/[0.03] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-forest-600"
                                    >
                    <span className="min-w-0">
                      <span className="block truncate font-medium">{shop.name}</span>
                      <span className="block truncate text-sm text-ink-soft">{businessName(shop.businessId)}</span>
                    </span>
                                        {active ? (
                                            <span className="flex shrink-0 items-center gap-1.5 text-sm font-medium text-forest-700">
                        <Check className="h-4 w-4" aria-hidden="true" /> Working here
                      </span>
                                        ) : (
                                            <span className="shrink-0 text-sm text-ink-faint">Switch to this shop</span>
                                        )}
                                    </button>
                                </li>
                            )
                        })}
                    </ul>
                </section>
            )}
        </div>
    )
}