import { money } from './ui';

/** Formats a metric value according to its unit. */
export function formatMetric(unit, value) {
  if (value === null || value === undefined) return '—';
  const n = Number(value);
  if (unit === 'CURRENCY') return money(n);
  if (unit === 'PERCENT') return `${n.toFixed(1)}%`;
  return n.toLocaleString('en-US', { maximumFractionDigits: 0 });
}

/** Progress bar that reads correctly for both higher- and lower-is-better metrics. */
export function TargetProgress({ percent, met, higherIsBetter, status }) {
  if (percent === null || percent === undefined) {
    return <span className="tiny muted">{met ? 'Met (zero target)' : 'Over a zero target'}</span>;
  }
  const colour = met
    ? 'var(--success)'
    : status === 'MISSED' || !higherIsBetter
      ? 'var(--danger)'
      : 'var(--amber-600)';
  return (
    <div style={{ minWidth: 120 }}>
      <div className="tiny mono-num" style={{ color: colour, fontWeight: 600 }}>
        {Number(percent).toFixed(1)}% of target
      </div>
      <div className="avail-meter" style={{ marginTop: 4 }}>
        <span style={{ width: `${Math.min(100, Number(percent))}%`, background: colour }} />
      </div>
    </div>
  );
}
