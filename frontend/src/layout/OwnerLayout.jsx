import { NavLink, Outlet } from 'react-router-dom'
import { OwnerProvider, useOwner } from '../owner/OwnerContext'
import { SelectField } from '../ui/Field'

const TABS = [
    { to: '/owner', label: 'Overview', end: true },
    { to: '/owner/closes', label: 'Closes' },
    { to: '/owner/deni', label: 'Deni' },
    { to: '/owner/stock', label: 'Low stock' },
    { to: '/owner/audit', label: 'History' },
]

function Frame() {
    const { businesses, business, choose } = useOwner()

    return (
        <div>
            {businesses.length > 1 && business && (
                <div className="no-print mb-6 max-w-xs">
                    <SelectField id="owner-business" label="Business" value={business.id} onChange={(e) => choose(e.target.value)}>
                        {businesses.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                    </SelectField>
                </div>
            )}

            <nav aria-label="Owner" className="no-print -mx-5 mb-8 overflow-x-auto border-b border-line px-5 lg:mx-0 lg:px-0">
                <ul className="-mb-px flex w-max gap-1">
                    {TABS.map((tab) => (
                        <li key={tab.to}>
                            <NavLink to={tab.to} end={tab.end}
                                     className={({ isActive }) =>
                                         `block whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium ${
                                             isActive ? 'border-forest-600 text-forest-700' : 'border-transparent text-ink-soft hover:text-ink'
                                         }`}>
                                {tab.label}
                            </NavLink>
                        </li>
                    ))}
                </ul>
            </nav>

            {business ? <Outlet key={business.id} /> : <p className="text-ink-soft">You do not own a business yet.</p>}
        </div>
    )
}

export default function OwnerLayout() {
    return (
        <OwnerProvider>
            <Frame />
        </OwnerProvider>
    )
}