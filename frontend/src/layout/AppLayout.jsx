import { useId, useState } from 'react'
import { Link , NavLink, Outlet } from 'react-router-dom'
import {
    Building2,
    CalendarCheck,
    Home, LayoutDashboard,
    LogOut, Moon,
    Package,
    Receipt,
    ShoppingCart,
    Store,
    Users,
    Wallet
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../api/client'
import { initials } from '../lib/names'
import Alert from '../ui/Alert'
import { SelectField } from '../ui/Field'
import Logo from '../ui/Logo'

const NAV = [
    { to: '/', label: 'Home', icon: Home, end: true, sidebarOnly: true },
    { to: '/owner', label: 'Overview', icon: LayoutDashboard, ownerOnly: true },
    { to: '/sell', label: 'Sell', icon: ShoppingCart },
    { to: '/sales', label: 'Sales', icon: Receipt },
    { to: '/deni', label: 'Deni', icon: Users },
    { to: '/money', label: 'Money', icon: Wallet },
    { to: '/stock', label: 'Stock', icon: Package },
    { to: '/shops', label: 'Shops', icon: Building2, ownerOnly: true, sidebarOnly: true },
    { to: '/close', label: 'Close day', icon: Moon, sidebarOnly: true },
    { to: '/closes', label: 'Past days', icon: CalendarCheck, sidebarOnly: true },
]

function ShopSwitcher() {
    const { shops, selectedShop, selectShop } = useAuth()
    const id = useId()

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
        <SelectField
            id={id}
            label="Select shop"
            labelClassName="sr-only"
            value={selectedShop.id}
            onChange={(e) => selectShop(e.target.value)}
            size="sm"
        >
            {shops.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
            ))}
        </SelectField>
    )
}

export default function AppLayout() {
    const { me, isOwner, logout } = useAuth()
    const [logoutError, setLogoutError] = useState('')

    const items = NAV.filter((item) => !item.ownerOnly || isOwner)
    const tabs = items.filter((item) => !item.sidebarOnly && !(isOwner && item.to === '/money'))
    const showBottomNav = tabs.length > 1

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
        <div className="print-flat min-h-dvh lg:grid lg:grid-cols-[17rem_minmax(0,1fr)]">
            {/* Sidebar: laptops */}
            <aside className="no-print hidden border-r border-line bg-surface lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col">
                <div className="shrink-0 px-5 pt-7">
                    <div className="flex items-center gap-3">
                        <Logo size={34} />
                        <span className="font-display text-xl font-medium">Duka Close</span>
                    </div>
                    <div className="mt-6">
                        <ShopSwitcher />
                    </div>
                </div>

                <nav aria-label="Main" className="mt-5 min-h-0 flex-1 space-y-1 overflow-y-auto px-5 pb-4">
                    {items.map(({ to, label, icon: Icon, end }) => (
                        <NavLink key={to} to={to} end={end} className={sideLink}>
                            <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
                            {label}
                        </NavLink>
                    ))}
                </nav>

                <div className="shrink-0 border-t border-line px-5 py-4">
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
                        className="mt-3 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-[15px] font-medium text-ink-soft transition-colors hover:bg-ink/5 hover:text-ink"
                    >
                        <LogOut className="h-[18px] w-[18px]" aria-hidden="true" />
                        Log out
                    </button>
                </div>
            </aside>

            <div className="flex min-w-0 flex-col">
                {/* Top bar: phones */}
                <header className="no-print sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur lg:hidden">
                    <Link to="/" aria-label="Home" className="shrink-0"><Logo size={32} /></Link>
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
                        className="mobile-bottom-nav no-print fixed inset-x-2 z-30 mx-auto max-w-xl rounded-2xl border border-white/80 bg-surface/90 p-1 shadow-[0_12px_35px_-12px_rgba(23,48,43,0.32)] backdrop-blur-xl lg:hidden"
                    >
                        <ul className="mx-auto flex max-w-md gap-0.5">
                            {tabs.map(({ to, label, icon: Icon, end }) => (
                                <li key={to} className="min-w-0 flex-1">
                                    <NavLink
                                        to={to}
                                        end={end}
                                        className={({ isActive }) =>
                                            `flex h-14 min-w-0 flex-col items-center justify-center gap-1 rounded-xl text-[11px] font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600 ${
                                                isActive ? 'bg-forest-50 text-forest-700' : 'text-ink-faint hover:bg-ink/5'
                                            }`
                                        }
                                    >
                                        {({ isActive }) => (
                                            <>
                                                <Icon className="h-5 w-5 shrink-0" strokeWidth={isActive ? 2.25 : 1.75} aria-hidden="true" />
                                                <span className="max-w-full truncate">{label}</span>
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