import { useEffect, useState } from 'react'
import { Store, CheckCircle2, XCircle } from 'lucide-react'
import api from './api/client'

export default function App() {
  const [status, setStatus] = useState('checking')

  useEffect(() => {
    api.get('/ping')
        .then(() => setStatus('ok'))
        .catch(() => setStatus('down'))
  }, [])

  return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="bg-white rounded-2xl shadow p-8 text-center space-y-3">
          <Store className="mx-auto h-10 w-10 text-emerald-600" />
          <h1 className="text-2xl font-bold text-slate-800">Duka Close</h1>
          {status === 'checking' && <p className="text-slate-500">Checking API…</p>}
          {status === 'ok' && (
              <p className="flex items-center justify-center gap-2 text-emerald-600">
                <CheckCircle2 className="h-5 w-5" /> API connected
              </p>
          )}
          {status === 'down' && (
              <p className="flex items-center justify-center gap-2 text-red-600">
                <XCircle className="h-5 w-5" /> Cannot reach API
              </p>
          )}
        </div>
      </div>
  )
}