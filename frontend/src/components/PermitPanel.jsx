import { permitApi } from '../api/api';
import { useApi } from '../hooks/useApi';
import StatusBadge from './StatusBadge';
import { Skeleton, dateFmt, money } from './ui';

/**
 * Permit status for one booking. Used on booking and assignment detail views so permit
 * risk is visible where the trip is being worked on, not only on the permits screen.
 */
export default function PermitPanel({ bookingId, compact = false }) {
  const permits = useApi(
    () => (bookingId ? permitApi.forBooking(bookingId) : Promise.resolve([])),
    [bookingId],
  );

  if (permits.loading) return <Skeleton height={compact ? 40 : 62} />;

  if (permits.error) {
    return <div className="tiny muted">Permit status unavailable ({permits.error})</div>;
  }

  const list = permits.data ?? [];

  if (list.length === 0) {
    return (
      <div className="inline-note warn">
        <span aria-hidden="true">⚠</span>
        <span>No park permit on file for this trip yet.</span>
      </div>
    );
  }

  return (
    <div className="stack-sm">
      {list.map((p) => (
        <div
          key={p.id}
          className={`inline-note ${p.atRisk ? 'danger' : p.expiringSoon ? 'warn' : p.status === 'APPROVED' ? 'ok' : ''}`}
        >
          <span aria-hidden="true">📜</span>
          <div style={{ flex: 1 }}>
            <div className="row row-gap-2 wrap">
              <span className="mono-num strong">{p.permitNumber}</span>
              <StatusBadge value={p.status} />
              {p.atRisk && <StatusBadge value="RISK" tone="danger" label="At risk" />}
              {!p.atRisk && p.expiringSoon && (
                <StatusBadge value="SOON" tone="warning" label="Expiring soon" />
              )}
            </div>
            {!compact && (
              <div className="tiny mt-1">
                {p.parkName} · valid until {dateFmt(p.expiryDate)} · fee {money(p.feeAmount)}
                {p.renewalCount > 0 ? ` · renewed ×${p.renewalCount}` : ''}
              </div>
            )}
            {p.riskReason && <div className="tiny mt-1 strong">{p.riskReason}</div>}
          </div>
        </div>
      ))}
    </div>
  );
}
