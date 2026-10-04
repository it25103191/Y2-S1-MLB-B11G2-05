import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { customerApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import StatusBadge from '../../components/StatusBadge';
import Timeline from '../../components/Timeline';
import {
  Button,
  Card,
  CardHead,
  EmptyState,
  ErrorState,
  Skeleton,
  SkeletonText,
  dateFmt,
  dateTimeFmt,
  money,
  titleCase,
} from '../../components/ui';

const TABS = [
  { key: 'bookings', label: 'Bookings' },
  { key: 'complaints', label: 'Cases' },
  { key: 'communications', label: 'Communication' },
  { key: 'notifications', label: 'Notifications' },
];

export default function CustomerProfilePage() {
  const { id } = useParams();
  const profile = useApi(() => customerApi.profile(id), [id]);
  const [tab, setTab] = useState('bookings');

  if (profile.loading) {
    return (
      <div className="page">
        <Skeleton width="30%" height={26} className="mb-3" />
        <div className="grid grid-4 mb-3">
          {[0, 1, 2, 3].map((i) => (
            <Card key={i} className="kpi">
              <Skeleton width="60%" height={10} />
              <Skeleton width="40%" height={24} style={{ marginTop: 8 }} />
            </Card>
          ))}
        </div>
        <Card className="card-pad">
          <SkeletonText lines={6} />
        </Card>
      </div>
    );
  }

  if (profile.error) {
    return (
      <div className="page">
        <ErrorState message={profile.error} onRetry={profile.reload} />
      </div>
    );
  }

  const { summary, bookings, complaints, communications, notifications } = profile.data;
  const counts = {
    bookings: bookings.length,
    complaints: complaints.length,
    communications: communications.length,
    notifications: notifications.length,
  };

  return (
    <div className="page">
      <Link to="/staff/customers" className="small">
        ← Back to customer profiles
      </Link>

      <div className="page-head mt-2">
        <div>
          <div className="eyebrow">Customer profile</div>
          <h1>{summary.fullName}</h1>
          <p className="lede">
            {summary.email}
            {summary.phone ? ` · ${summary.phone}` : ''} · joined {dateFmt(summary.joinedAt)}
          </p>
        </div>
      </div>

      <div className="grid grid-4 mb-3">
        <Card className="kpi">
          <span className="kpi-icon" aria-hidden="true">
            🗓️
          </span>
          <div className="kpi-label">Bookings</div>
          <div className="kpi-value">{summary.totalBookings}</div>
          <div className="kpi-sub">
            {summary.activeBookings} active · {summary.cancelledBookings} cancelled
          </div>
        </Card>
        <Card className="kpi kpi-amber">
          <span className="kpi-icon" aria-hidden="true">
            💰
          </span>
          <div className="kpi-label">Lifetime value</div>
          <div className="kpi-value">{money(summary.lifetimeValue)}</div>
          <div className="kpi-sub">Paid to date</div>
        </Card>
        <Card className={`kpi ${Number(summary.outstandingBalance) > 0 ? 'kpi-terracotta' : ''}`}>
          <span className="kpi-icon" aria-hidden="true">
            ⏳
          </span>
          <div className="kpi-label">Outstanding</div>
          <div className="kpi-value">{money(summary.outstandingBalance)}</div>
          <div className="kpi-sub">Across active bookings</div>
        </Card>
        <Card className={`kpi ${summary.openComplaints > 0 ? 'kpi-danger' : ''}`}>
          <span className="kpi-icon" aria-hidden="true">
            💬
          </span>
          <div className="kpi-label">Open cases</div>
          <div className="kpi-value">{summary.openComplaints}</div>
          <div className="kpi-sub">{complaints.length} total raised</div>
        </Card>
      </div>

      <div className="chip-row mb-3">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={`chip${tab === t.key ? ' on' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label} ({counts[t.key]})
          </button>
        ))}
      </div>

      {tab === 'bookings' && (
        <Card>
          <CardHead title="Booking history" subtitle="Newest first" />
          <div className="card-body">
            {bookings.length === 0 ? (
              <EmptyState icon="🧳" title="No bookings" message="This customer has not booked yet." />
            ) : (
              <div className="stack-sm">
                {bookings.map((b) => (
                  <div
                    key={b.id}
                    className="row row-gap-2 wrap"
                    style={{ padding: '11px 0', borderBottom: '1px solid var(--cream-200)' }}
                  >
                    <div className="grow" style={{ minWidth: 200 }}>
                      <div className="strong">{b.packageName}</div>
                      <div className="tiny muted">
                        <span className="mono-num">{b.bookingReference}</span> · {b.parkName} ·{' '}
                        {b.participants} pax
                      </div>
                    </div>
                    <div className="right nowrap">
                      <div className="small">{dateFmt(b.tripDate)}</div>
                      <div className="tiny muted mono-num">{money(b.totalPrice)}</div>
                    </div>
                    <div className="col" style={{ gap: 4, alignItems: 'flex-end' }}>
                      <StatusBadge value={b.status} />
                      {b.paymentOverdue && (
                        <StatusBadge value="OVERDUE" tone="danger" label="Overdue" />
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Card>
      )}

      {tab === 'complaints' && (
        <Card>
          <CardHead title="Case history" subtitle="Complaints and inquiries raised" />
          <div className="card-body">
            {complaints.length === 0 ? (
              <EmptyState icon="💬" title="No cases" message="This customer has never raised a case." />
            ) : (
              <div className="stack-sm">
                {complaints.map((c) => (
                  <div
                    key={c.id}
                    className="row row-gap-2 wrap"
                    style={{ padding: '11px 0', borderBottom: '1px solid var(--cream-200)' }}
                  >
                    <div className="grow" style={{ minWidth: 220 }}>
                      <div className="strong">{c.subject}</div>
                      <div className="tiny muted">
                        <span className="mono-num">{c.reference}</span> · {titleCase(c.category)} ·{' '}
                        {dateFmt(c.createdAt)}
                      </div>
                    </div>
                    <StatusBadge value={c.priority} />
                    <StatusBadge value={c.status} />
                    <Link to={`/staff/complaints/${c.id}`}>
                      <Button size="sm" variant="outline">
                        Open
                      </Button>
                    </Link>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Card>
      )}

      {tab === 'communications' && (
        <Card>
          <CardHead title="Communication log" subtitle="Every recorded interaction, newest first" />
          <div className="card-body">
            {communications.length === 0 ? (
              <EmptyState
                icon="📝"
                title="No communication recorded"
                message="Notes, emails and calls will appear here."
              />
            ) : (
              <Timeline entries={communications} />
            )}
          </div>
        </Card>
      )}

      {tab === 'notifications' && (
        <Card>
          <CardHead title="Notifications sent" subtitle="Simulated email and SMS delivery records" />
          <div className="card-body">
            {notifications.length === 0 ? (
              <EmptyState
                icon="🔔"
                title="Nothing sent yet"
                message="Automated messages to this customer will be listed here."
              />
            ) : (
              <div className="stack-sm">
                {notifications.map((n) => (
                  <div
                    key={n.id}
                    style={{ padding: '11px 0', borderBottom: '1px solid var(--cream-200)' }}
                  >
                    <div className="row row-gap-2 wrap">
                      <span className="badge badge-neutral">{n.channel}</span>
                      <span className="strong small">{n.subject ?? '(SMS)'}</span>
                      <span className="spacer" />
                      <StatusBadge value={n.status === 'SENT' ? 'SUCCESS' : n.status} label={titleCase(n.status)} />
                      <span className="tiny muted">{dateTimeFmt(n.sentAt ?? n.createdAt)}</span>
                    </div>
                    <div className="small muted mt-1">{n.body}</div>
                    <div className="tiny muted mt-1">
                      to {n.recipientAddress}
                      {n.relatedEntity ? ` · ${n.relatedEntity} #${n.relatedId}` : ''}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
