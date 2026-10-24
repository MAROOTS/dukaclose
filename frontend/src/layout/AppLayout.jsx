import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import {Building2, ChevronDown, Home, LogOut, Package, Store} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../api/client'
import { initials } from '../lib/names'
import Alert from '../ui/Alert'
import Logo from '../ui/Logo'

const NAV = [
    { to: '/', label: 'Home', icon: Home, end: true },
    { to: '/stock', label: 'Stock', icon: Package },
    { to: '/shops', label: 'Shops', icon: Building2, ownerOnly: true },
]

function ShopSwitcher() {
    const { shops, selectedShop, selectShop } = useAuth()

    if (!selectedShop) return <span className="text-sm text-ink-soft">No shop assigned</span>

    if (shops.length === 1) {
        return (
            <div className="flex min-w-0 items-center gap-2 text-sm font-medium text-ink">
                <Store className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
                <span className="truncate">{selectedShop.name}</span>
            </div>
        )
    }

    return (
        <label className="relative block">
            <span className="sr-only">Select shop</span>
            <select
                value={selectedShop.id}
                onChange={(e) => selectShop(e.target.value)}
                className="h-10 w-full appearance-none rounded-lg border border-line-strong bg-surface pl-3 pr-9 text-sm font-medium text-ink focus:border-forest-600 focus:outline-none focus:ring-4 focus:ring-forest-600/15"
            >
                {shops.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                ))}
            </select>
            <ChevronDown
                aria-hidden="true"
                className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint"
            />
        </label>
    )
}

export default function AppLayout() {
    const { me, isOwner, logout } = useAuth()
    const [logoutError, setLogoutError] = useState('')

    const items = NAV.filter((item) => !item.ownerOnly || isOwner)
    const showBottomNav = items.length > 1

    async function handleLogout() {
        setLogoutError('')
        try {
            await logout()
        } catch (err) {
            setLogoutError(errorMessage(err, 'Could not log out. Please try again.'))
        }
    }

    const sideLink = ({ isActive }) =>
        `flex items-center gap-3 rounded-lg px-3 py-2.5 text-[15px] font-medium transition-colors ${
            isActive ? 'bg-forest-50 text-forest-700' : 'text-ink-soft hover:bg-ink/5 hover:text-ink'
        }`

    return (
        <div className="min-h-dvh lg:grid lg:grid-cols-[17rem_minmax(0,1fr)]">
            {/* Sidebar: laptops */}
            <aside className="hidden border-r border-line bg-surface px-5 py-7 lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col">
                <div className="flex items-center gap-3">
                    <Logo size={34} />
                    <span className="font-display text-xl font-medium">Duka Close</span>
                </div>

                <div className="mt-8">
                    <ShopSwitcher />
                </div>

                <nav aria-label="Main" className="mt-6 space-y-1">
                    {items.map(({ to, label, icon: Icon, end }) => (
                        <NavLink key={to} to={to} end={end} className={sideLink}>
                            <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
                            {label}
                        </NavLink>
                    ))}
                </nav>

                <div className="mt-auto border-t border-line pt-5">
                    <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-forest-100 text-sm font-semibold text-forest-800">
                            {initials(me.user.displayName)}
                        </div>
                        <div className="min-w-0 leading-tight">
                            <div className="truncate text-sm font-medium">{me.user.displayName}</div>
                            <div className="text-sm text-ink-soft">{isOwner ? 'Owner' : 'Cashier'}</div>
                        </div>
                    </div>
                    <button
                        onClick={handleLogout}
                        className="mt-4 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-[15px] font-medium text-ink-soft transition-colors hover:bg-ink/5 hover:text-ink"
                    >
                        <LogOut className="h-[18px] w-[18px]" aria-hidden="true" />
                        Log out
                    </button>
                </div>
            </aside>

            <div className="flex min-w-0 flex-col">
                {/* Top bar: phones */}
                <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur lg:hidden">
                    <Logo size={32} />
                    <div className="min-w-0 flex-1">
                        <ShopSwitcher />
                    </div>
                    <button
                        onClick={handleLogout}
                        aria-label="Log out"
                        className="flex h-10 w-10 items-center justify-center rounded-lg text-ink-soft hover:bg-ink/5"
                    >
                        <LogOut className="h-5 w-5" />
                    </button>
                </header>

                <main
                    className={`mx-auto w-full max-w-3xl flex-1 px-5 pt-8 lg:px-10 lg:pt-14 lg:pb-14 ${
                        showBottomNav ? 'pb-28' : 'pb-10'
                    }`}
                >
                    {logoutError && (
                        <div className="mb-6">
                            <Alert>{logoutError}</Alert>
                        </div>
                    )}
                    <Outlet />
                </main>

                {/* Tabs: phones, only when there is more than one destination */}
                {showBottomNav && (
                    <nav
                        aria-label="Main"
                        className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden"
                    >
                        <ul className="mx-auto flex max-w-md">
                            {items.map(({ to, label, icon: Icon, end }) => (
                                <li key={to} className="flex-1">
                                    <NavLink
                                        to={to}
                                        end={end}
                                        className={({ isActive }) =>
                                            `flex h-16 flex-col items-center justify-center gap-1 text-xs font-medium ${
                                                isActive ? 'text-forest-700' : 'text-ink-faint'
                                            }`
                                        }
                                    >
                                        {({ isActive }) => (
                                            <>
                                                <Icon className="h-5 w-5" strokeWidth={isActive ? 2.25 : 1.75} aria-hidden="true" />
                                                {label}
                                            </>
                                        )}
                                    </NavLink>
                                </li>
                            ))}
                        </ul>
                    </nav>
                )}
            </div>
        </div>
    )
}