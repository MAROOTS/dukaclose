import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Eye, EyeOff, Lock, Phone } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../api/client'
import Alert from '../ui/Alert'
import Button from '../ui/Button'
import Field from '../ui/Field'
import Logo from '../ui/Logo'

export default function LoginPage() {
    const { me, login } = useAuth()
    const location = useLocation()
    const [identifier, setIdentifier] = useState('')
    const [password, setPassword] = useState('')
    const [showPassword, setShowPassword] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')

    if (me) return <Navigate to={location.state?.from?.pathname ?? '/'} replace />

    async function handleSubmit(event) {
        event.preventDefault()
        if (submitting) return
        setError('')
        setSubmitting(true)
        try {
            await login(identifier, password)
        } catch (err) {
            setError(errorMessage(err, 'Could not sign in. Please try again.'))
            setSubmitting(false)
        }
    }

    return (
        <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
            <aside className="ledger-lines relative overflow-hidden bg-forest-800 py-6 pl-16 pr-6 text-white lg:flex lg:flex-col lg:justify-between lg:py-14 lg:pl-32 lg:pr-16">
                <span aria-hidden="true" className="absolute inset-y-0 left-10 w-px bg-brass-500/60 lg:left-24" />
                <div className="relative flex items-center gap-3">
                    <Logo size={36} onDark />
                    <span className="font-display text-2xl font-medium">Duka Close</span>
                </div>

                <div className="relative hidden max-w-md lg:block">
                    <p className="font-display text-5xl font-medium leading-[1.08] tracking-tight">
                        Close the day with numbers you can trust.
                    </p>
                    <p className="mt-6 text-lg leading-relaxed text-white/70">
                        Sales, deni, stock and drawer cash in one evening book, for every shop you run.
                    </p>
                </div>

                <p className="relative hidden text-sm text-white/50 lg:block">Made for the duka counter.</p>
            </aside>

            <main className="flex items-center justify-center px-5 py-12 lg:px-16">
                <div className="w-full max-w-sm">
                    <h1 className="font-display text-4xl font-medium tracking-tight text-ink">Welcome back</h1>
                    <p className="mt-2 text-ink-soft">Sign in to record sales and close the day.</p>

                    <form onSubmit={handleSubmit} className="mt-8 space-y-5">
                        <Field
                            id="phone"
                            label="Phone number"
                            icon={Phone}
                            type="tel"
                            inputMode="tel"
                            autoComplete="username"
                            required
                            placeholder="0712 345 678"
                            value={identifier}
                            onChange={(e) => setIdentifier(e.target.value)}
                        />
                        <Field
                            id="password"
                            label="Password"
                            icon={Lock}
                            type={showPassword ? 'text' : 'password'}
                            autoComplete="current-password"
                            required
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            trailing={
                                <button
                                    type="button"
                                    onClick={() => setShowPassword((v) => !v)}
                                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                                    className="flex h-9 w-9 items-center justify-center rounded-lg text-ink-faint hover:bg-ink/5 hover:text-ink"
                                >
                                    {showPassword ? <EyeOff className="h-[18px] w-[18px]" /> : <Eye className="h-[18px] w-[18px]" />}
                                </button>
                            }
                        />

                        {error && <Alert>{error}</Alert>}

                        <Button type="submit" loading={submitting} className="w-full">
                            {submitting ? 'Signing in…' : 'Sign in'}
                        </Button>
                    </form>
                </div>
            </main>
        </div>
    )
}