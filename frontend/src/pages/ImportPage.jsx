import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { Download, Loader2 } from 'lucide-react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { formatKsh } from '../lib/money'
import Alert from '../ui/Alert'
import BackLink from '../ui/BackLink'
import Button, { buttonClass } from '../ui/Button'
import Notice from '../ui/Notice'

const TEMPLATE = [
    'name,unit,price,quantity,cost_per_unit,reorder_level,pack_name,pack_size',
    'Sugar,kg,190,75,120,20,Sack,50',
    'Soda 500ml,piece,50,48,20,24,Carton,24',
    'Cooking oil,litre,350,10,280,5,,',
    '',
].join('\n')

const COLUMNS = [
    ['name', 'Required. The item name, like Sugar.'],
    ['unit', 'Required. kg, litre or piece.'],
    ['price', 'Selling price per unit in KSh, like 190 or 190.50.'],
    ['quantity', 'What is on your shelf now. Starting stock can be recorded only once per item.'],
    ['cost_per_unit', 'What one unit cost you. Needed with quantity. Use 0 if it was free.'],
    ['reorder_level', 'Warn me when stock falls to this level.'],
    ['pack_name, pack_size', 'Like Sack and 50. Used for new items only.'],
]

function downloadTemplate() {
    const blob = new Blob([TEMPLATE], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'duka-close-items-template.csv'
    link.click()
    URL.revokeObjectURL(url)
}

function Messages({ items }) {
    return (
        <ul className="divide-y divide-line border-y border-line">
            {items.map((m, i) => (
                <li key={i} className="flex gap-4 py-3 text-sm">
                    <span className="w-16 shrink-0 font-medium tabular-nums">Row {m.line}</span>
                    <span className="text-ink-soft">{m.message}</span>
                </li>
            ))}
        </ul>
    )
}

function Stat({ label, children }) {
    return (
        <div>
            <dt className="text-sm text-ink-soft">{label}</dt>
            <dd className="mt-1 font-display text-2xl font-medium tabular-nums">{children}</dd>
        </div>
    )
}

export default function ImportPage() {
    const { selectedShop } = useAuth()
    const [fileName, setFileName] = useState('')
    const [csv, setCsv] = useState('')
    const [importId, setImportId] = useState(null)
    const [report, setReport] = useState(null)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')

    if (!selectedShop || selectedShop.role !== 'OWNER') return <Navigate to="/stock" replace />

    async function run(commit, text, id) {
        setBusy(true)
        setError('')
        try {
            const { data } = await api.post(
                `/shops/${selectedShop.id}/inventory/import`,
                { importId: id, csv: text },
                { params: { commit } },
            )
            setReport(data)
        } catch (err) {
            setError(errorMessage(err, 'Could not read the file.'))
            if (!commit) setReport(null)
        } finally {
            setBusy(false)
        }
    }

    async function handleFile(event) {
        const input = event.target
        const file = input.files?.[0]
        if (!file) return
        setReport(null)
        setError('')
        if (file.size > 1_000_000) {
            setError('That file is too big. Keep it under 1 MB, or split it into smaller files.')
            input.value = ''
            return
        }
        const text = await file.text()
        // A fresh id per chosen file. The same id is reused when you confirm, so a retry cannot count twice.
        const id = crypto.randomUUID()
        setCsv(text)
        setFileName(file.name)
        setImportId(id)
        input.value = '' // lets you pick the same file again after fixing it
        await run(false, text, id)
    }

    const ready = report && !report.committed && report.errorCount === 0
    const itemCount = report ? report.newItems + report.existingItems : 0

    return (
        <div>
            <BackLink to="/stock">Stock</BackLink>
            <h1 className="mt-4 font-display text-4xl font-medium tracking-tight">Import items</h1>
            <p className="mt-2 text-ink-soft">
                Add many items at once from a spreadsheet. Prices and stock go into {selectedShop.name}.
            </p>

            <section className="mt-8 max-w-xl">
                <h2 className="font-display text-2xl font-medium">1. Prepare your file</h2>
                <p className="mt-2 text-ink-soft">
                    In Excel or Google Sheets, save as <strong className="font-medium text-ink">CSV UTF-8</strong>.
                    The first row holds the column names.
                </p>
                <div className="mt-4">
                    <button type="button" onClick={downloadTemplate} className={`${buttonClass('quiet')} border border-line-strong`}>
                        <Download className="h-4 w-4" aria-hidden="true" /> Download template
                    </button>
                </div>
                <dl className="mt-6 divide-y divide-line border-y border-line">
                    {COLUMNS.map(([name, text]) => (
                        <div key={name} className="flex flex-col gap-1 py-3 sm:flex-row sm:gap-4">
                            <dt className="shrink-0 sm:w-48">
                                <code className="rounded bg-ink/[0.06] px-1.5 py-0.5 text-[13px]">{name}</code>
                            </dt>
                            <dd className="text-sm text-ink-soft">{text}</dd>
                        </div>
                    ))}
                </dl>
            </section>

            <section className="mt-12 max-w-xl">
                <h2 className="font-display text-2xl font-medium">2. Choose your file</h2>
                <p className="mt-2 text-ink-soft">We check it first. Nothing is saved until you confirm.</p>
                <div className="mt-4">
                    <label htmlFor="csv-file" className="sr-only">CSV file</label>
                    <input
                        id="csv-file"
                        type="file"
                        accept=".csv,text/csv"
                        onChange={handleFile}
                        disabled={busy}
                        className="block w-full text-sm text-ink-soft file:mr-4 file:cursor-pointer file:rounded-xl file:border-0 file:bg-forest-50 file:px-5 file:py-3 file:text-sm file:font-semibold file:text-forest-700 hover:file:bg-forest-100"
                    />
                </div>

                {busy && (
                    <p className="mt-6 flex items-center gap-2 text-sm text-ink-soft">
                        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Working…
                    </p>
                )}
                {error && <div className="mt-6"><Alert>{error}</Alert></div>}
            </section>

            {report && !busy && (
                <section className="mt-12 max-w-xl">
                    <h2 className="font-display text-2xl font-medium">3. Result</h2>
                    <p className="mt-1 text-sm text-ink-soft">{fileName}</p>

                    {report.committed ? (
                        <div className="mt-4 space-y-5">
                            <Notice>
                                Imported {itemCount} {itemCount === 1 ? 'item' : 'items'}
                                {report.openingStockRows > 0 && `, with starting stock for ${report.openingStockRows}`}.
                            </Notice>
                            <Link to="/stock" className={buttonClass()}>Go to stock</Link>
                        </div>
                    ) : report.errorCount > 0 ? (
                        <div className="mt-4 space-y-5">
                            <Alert>
                                {report.errorCount} {report.errorCount === 1 ? 'problem' : 'problems'} found. Nothing was saved.
                                Fix the rows below in your file, then choose it again.
                            </Alert>
                            <Messages items={report.errors} />
                            {report.errorCount > report.errors.length && (
                                <p className="text-sm text-ink-soft">Showing the first {report.errors.length}.</p>
                            )}
                        </div>
                    ) : (
                        <div className="mt-4 space-y-6">
                            <dl className="grid grid-cols-2 gap-6 border-y border-line py-6">
                                <Stat label="New items">{report.newItems}</Stat>
                                <Stat label="Existing items">{report.existingItems}</Stat>
                                <Stat label="Starting stock lines">{report.openingStockRows}</Stat>
                                <Stat label="Stock value at cost">{formatKsh(report.openingValueMinor)}</Stat>
                            </dl>
                            {report.warnings.length > 0 && (
                                <div>
                                    <p className="mb-2 text-sm font-medium">Notes</p>
                                    <Messages items={report.warnings} />
                                </div>
                            )}
                            <Button onClick={() => run(true, csv, importId)} loading={busy}>
                                Import {itemCount} {itemCount === 1 ? 'item' : 'items'}
                            </Button>
                        </div>
                    )}
                </section>
            )}
        </div>
    )
}