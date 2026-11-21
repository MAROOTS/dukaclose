import { formatKsh } from '../../lib/money'
import { differenceInfo, toneClass } from '../../lib/closing'
import { Lines } from './DayReport'

export default function DrawerBreakdown({ drawer }) {
    const diff = drawer.countCurrent ? differenceInfo(drawer.differenceMinor) : null
    const hasCount = drawer.countedMinor !== null && drawer.countedMinor !== undefined
    const floatDiff = drawer.floatDifferenceMinor

    return (
        <div>
            <Lines
                rows={[
                    ['Opening float', formatKsh(drawer.openingFloatMinor)],
                    ['Cash sales', `+${formatKsh(drawer.cashSalesMinor)}`],
                    ['Cash collected from deni', `+${formatKsh(drawer.cashCollectionsMinor)}`],
                    ['Other cash received', `+${formatKsh(drawer.otherCashInMinor)}`],
                    ['Cash paid out', `−${formatKsh(drawer.cashOutMinor)}`],
                    ['Cash expected', formatKsh(drawer.expectedMinor), { strong: true }],
                    hasCount && ['Cash counted', formatKsh(drawer.countedMinor), { strong: true }],
                    diff && ['Difference', diff.label, { strong: true, tone: toneClass(diff.tone) }],
                ]}
            />
            {hasCount && !drawer.countCurrent && (
                <p className="mt-3 text-sm font-medium text-ledger">
                    That count is out of date. Something was recorded after it, so the drawer must be counted again.
                </p>
            )}
            {floatDiff !== null && floatDiff !== undefined && floatDiff !== 0 && (
                <p className="mt-3 text-sm text-ink-soft">
                    Today started with {formatKsh(Math.abs(floatDiff))} {floatDiff > 0 ? 'more' : 'less'} than the last close counted
                    ({formatKsh(drawer.carriedOverMinor)}).
                </p>
            )}
        </div>
    )
}