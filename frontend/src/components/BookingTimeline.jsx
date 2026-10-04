import { bookingApi } from '../api/api';
import { useApi } from '../hooks/useApi';
import StatusBadge from './StatusBadge';
import { ErrorState, SkeletonText, dateTimeFmt } from './ui';

const ICONS = {
  CREATED: '＋',
  UPDATED: '✎',
  CANCELLED: '✕',
  STATUS_CHANGED: '⇄',
};

/**
 * A booking's history: every change, who made it and when. The entries come from the
 * BookingHistoryRecorder observer on the server, so anything that changes a booking shows up
 * here without this component knowing about it.
 *
 * Pass `version` (for example the booking's status) to reload after a change on the same screen.
 */
export default function BookingTimeline({ bookingId, version }) {
  const history = useApi(() => bookingApi.history(bookingId), [bookingId, version]);

  if (history.loading && !history.data) return <SkeletonText lines={3} />;
  if (history.error) return <ErrorState message={history.error} onRetry={history.reload} />;
  if (!history.data?.length) return <p className="muted small">No changes recorded yet.</p>;

  return (
    <div className="timeline">
      {history.data.map((e, i) => (
        <div className="tl-item" key={e.id ?? `derived-${i}`}>
          <span className={`tl-dot${e.status === 'CANCELLED' ? ' escalation' : e.event === 'STATUS_CHANGED' ? ' status' : ''}`} aria-hidden="true">
            {ICONS[e.event] ?? '•'}
          </span>
          <div className="tl-head">
            <span className="tl-who">{e.title}</span>
            <StatusBadge value={e.status} />
            <span className="tl-when">
              {dateTimeFmt(e.at)} · {e.actorName}
              {e.actorRole && <span className="muted"> ({e.actorRole})</span>}
            </span>
          </div>
          {e.detail && <div className="tl-msg">{e.detail}</div>}
        </div>
      ))}
    </div>
  );
}
