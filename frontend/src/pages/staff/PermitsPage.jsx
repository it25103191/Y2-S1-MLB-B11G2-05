import { useState } from 'react';
import { permitApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../api/client';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import {
  Button,
  Card,
  CardHead,
  ErrorState,
  Field,
  Input,
  Modal,
  Select,
  Skeleton,
  SkeletonTable,
  Textarea,
  dateFmt,
  money,
  relativeDays,
} from '../../components/ui';

const STATUSES = ['PENDING', 'APPROVED', 'EXPIRED', 'REJECTED'];

function addDaysISO(iso, n) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + n);
  return d.toISOString().slice(0, 10);
}

export default function PermitsPage() {
  const toast = useToast();
  const dash = useApi(() => permitApi.dashboard(), []);

  const [statusFilter, setStatusFilter] = useState('');
  const [riskOnly, setRiskOnly] = useState(false);
  const [busy, setBusy] = useState(false);

  const [requesting, setRequesting] = useState(null); // unpermitted booking
  const [reqExpiry, setReqExpiry] = useState('');
  const [reqNotes, setReqNotes] = useState('');

  const [renewing, setRenewing] = useState(null);
  const [renewExpiry, setRenewExpiry] = useState('');
  const [renewNotes, setRenewNotes] = useState('');

  const [deciding, setDeciding] = useState(null);
  const [decideExpiry, setDecideExpiry] = useState('');
  const [decideNotes, setDecideNotes] = useState('');

  const [editingPermit, setEditingPermit] = useState(null);
  const [editExpiry, setEditExpiry] = useState('');
  const [editCovered, setEditCovered] = useState(1);
  const [editNotes, setEditNotes] = useState('');
  const [deletingPermit, setDeletingPermit] = useState(null);

  const data = dash.data;
  const permits = data?.permits ?? [];
  const rows = permits.filter(
    (p) => (!statusFilter || p.status === statusFilter) && (!riskOnly || p.atRisk || p.expiringSoon),
  );

  const openRequest = (booking) => {
    setRequesting(booking);
    setReqExpiry(addDaysISO(booking.tripEndDate, 7));
    setReqNotes('');
  };

  const submitRequest = async () => {
    setBusy(true);
    try {
      const created = await permitApi.request({
        bookingId: requesting.bookingId,
        expiryDate: reqExpiry || null,
        notes: reqNotes || null,
      });
      toast.success(
        `Permit ${created.permitNumber} raised with ${created.permitAuthority ?? 'the park authority'}.`,
        'Permit requested',
      );
      setRequesting(null);
      dash.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not request permit');
    } finally {
      setBusy(false);
    }
  };

  const openDecide = (permit) => {
    setDeciding(permit);
    setDecideExpiry(permit.expiryDate);
    setDecideNotes(permit.notes ?? '');
  };

  const approve = async () => {
    setBusy(true);
    try {
      await permitApi.approve(deciding.id, { expiryDate: decideExpiry, notes: decideNotes || null });
      toast.success(`Permit ${deciding.permitNumber} approved.`, 'Approved');
      setDeciding(null);
      dash.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not approve');
    } finally {
      setBusy(false);
    }
  };

  const reject = async () => {
    setBusy(true);
    try {
      await permitApi.reject(deciding.id, { notes: decideNotes || null });
      toast.warning(`Permit ${deciding.permitNumber} rejected.`, 'Rejected');
      setDeciding(null);
      dash.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not reject');
    } finally {
      setBusy(false);
    }
  };

  const openRenew = (permit) => {
    setRenewing(permit);
    setRenewExpiry(addDaysISO(permit.tripEndDate, 14));
    setRenewNotes('');
  };

  const submitRenew = async () => {
    setBusy(true);
    try {
      const updated = await permitApi.renew(renewing.id, {
        expiryDate: renewExpiry,
        notes: renewNotes || null,
      });
      toast.success(`Permit now valid until ${dateFmt(updated.expiryDate)}.`, 'Permit renewed');
      setRenewing(null);
      dash.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not renew');
    } finally {
      setBusy(false);
    }
  };

  const openEditPermit = (permit) => {
    setEditingPermit(permit);
    setEditExpiry(permit.expiryDate);
    setEditCovered(permit.coveredParticipants);
    setEditNotes(permit.notes ?? '');
  };

  const submitEditPermit = async () => {
    setBusy(true);
    try {
      const updated = await permitApi.update(editingPermit.id, {
        expiryDate: editExpiry,
        coveredParticipants: Number(editCovered),
        notes: editNotes || null,
      });
      toast.success(
        `${updated.permitNumber} now covers ${updated.coveredParticipants} traveller(s), fee ${money(updated.feeAmount)}.`,
        'Permit updated',
      );
      setEditingPermit(null);
      dash.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update permit');
    } finally {
      setBusy(false);
    }
  };

  const doDeletePermit = async () => {
    setBusy(true);
    try {
      await permitApi.remove(deletingPermit.id);
      toast.success(`Permit ${deletingPermit.permitNumber} deleted.`, 'Permit deleted');
      setDeletingPermit(null);
      dash.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete permit');
    } finally {
      setBusy(false);
    }
  };

  const columns = [
    {
      key: 'permitNumber',
      header: 'Permit',
      width: 132,
      render: (r) => (
        <div>
          <div className="mono-num small strong">{r.permitNumber}</div>
          {r.renewalCount > 0 && <div className="tiny muted">renewed ×{r.renewalCount}</div>}
        </div>
      ),
    },
    {
      key: 'parkName',
      header: 'Park / trip',
      render: (r) => (
        <div>
          <div className="strong">{r.parkName}</div>
          <div className="tiny muted truncate" style={{ maxWidth: 300 }}>
            {r.packageName} · <span className="mono-num">{r.bookingReference}</span>
          </div>
        </div>
      ),
    },
    { key: 'customerName', header: 'Customer', width: 150 },
    {
      key: 'tripDate',
      header: 'Trip',
      width: 150,
      render: (r) => (
        <div>
          <div className="small">
            {dateFmt(r.tripDate)} → {dateFmt(r.tripEndDate)}
          </div>
          <div className="tiny muted">{relativeDays(r.tripDate)}</div>
        </div>
      ),
    },
    {
      key: 'expiryDate',
      header: 'Valid until',
      width: 140,
      sortValue: (r) => r.expiryDate,
      render: (r) => (
        <div>
          <div className="small">{dateFmt(r.expiryDate)}</div>
          <div
            className="tiny"
            style={{
              color: r.atRisk ? 'var(--danger)' : r.expiringSoon ? 'var(--warning)' : 'var(--ink-400)',
            }}
          >
            {r.daysToExpiry < 0 ? `${Math.abs(r.daysToExpiry)}d ago` : `in ${r.daysToExpiry}d`}
          </div>
        </div>
      ),
    },
    {
      key: 'feeAmount',
      header: 'Fee',
      width: 100,
      align: 'right',
      sortValue: (r) => Number(r.feeAmount),
      render: (r) => (
        <div>
          <div className="mono-num">{money(r.feeAmount)}</div>
          <div
            className="tiny"
            style={{ color: r.coveredParticipants < r.bookingParticipants ? 'var(--danger)' : 'var(--ink-400)' }}
          >
            covers {r.coveredParticipants} of {r.bookingParticipants}
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 150,
      render: (r) => (
        <div className="col" style={{ gap: 4 }}>
          <StatusBadge value={r.status} />
          {r.atRisk && <StatusBadge value="RISK" tone="danger" label="At risk" />}
          {!r.atRisk && r.expiringSoon && (
            <StatusBadge value="SOON" tone="warning" label="Expiring soon" />
          )}
        </div>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: 250,
      sortable: false,
      searchable: false,
      render: (r) => (
        <div className="row row-gap-1 wrap">
          {r.status === 'PENDING' && (
            <>
              <Button size="sm" variant="outline" onClick={() => openDecide(r)}>
                Decide
              </Button>
              <Button size="sm" variant="ghost" onClick={() => openEditPermit(r)}>
                Edit
              </Button>
            </>
          )}
          {r.status !== 'PENDING' && r.status !== 'REJECTED' && (
            <Button size="sm" variant="outline" onClick={() => openRenew(r)}>
              Renew
            </Button>
          )}
          {['PENDING', 'REJECTED', 'EXPIRED'].includes(r.status) && (
            <Button size="sm" variant="ghost" onClick={() => setDeletingPermit(r)}>
              Delete
            </Button>
          )}
        </div>
      ),
    },
  ];

  if (dash.loading) {
    return (
      <div className="page">
        <div className="grid grid-4 mb-3">
          {[0, 1, 2, 3].map((i) => (
            <Card key={i} className="kpi">
              <Skeleton width="60%" height={10} />
              <Skeleton width="35%" height={24} style={{ marginTop: 8 }} />
            </Card>
          ))}
        </div>
        <SkeletonTable rows={6} cols={8} />
      </div>
    );
  }

  if (dash.error) {
    return (
      <div className="page">
        <ErrorState message={dash.error} onRetry={dash.reload} />
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Compliance</div>
          <h1>Park permits</h1>
          <p className="lede">
            Every trip needs a valid park permit covering its dates. Permits lapsing within{' '}
            {data.warningDays} days are flagged, and any trip whose permit will not last the whole
            journey is marked at risk.
          </p>
        </div>
      </div>

      <div className="grid grid-4 mb-3">
        <Card className="kpi">
          <span className="kpi-icon" aria-hidden="true">
            📜
          </span>
          <div className="kpi-label">Total permits</div>
          <div className="kpi-value">{permits.length}</div>
          <div className="kpi-sub">
            {permits.filter((p) => p.status === 'APPROVED').length} approved
          </div>
        </Card>
        <Card className="kpi kpi-amber">
          <span className="kpi-icon" aria-hidden="true">
            ⏳
          </span>
          <div className="kpi-label">Awaiting approval</div>
          <div className="kpi-value">{permits.filter((p) => p.status === 'PENDING').length}</div>
          <div className="kpi-sub">With the park authority</div>
        </Card>
        <Card className={`kpi ${data.expiringSoon.length ? 'kpi-terracotta' : ''}`}>
          <span className="kpi-icon" aria-hidden="true">
            ⚠
          </span>
          <div className="kpi-label">Expiring soon</div>
          <div className="kpi-value">{data.expiringSoon.length}</div>
          <div className="kpi-sub">Within {data.warningDays} days</div>
        </Card>
        <Card className={`kpi ${data.atRisk.length ? 'kpi-danger' : ''}`}>
          <span className="kpi-icon" aria-hidden="true">
            🚨
          </span>
          <div className="kpi-label">Trips at risk</div>
          <div className="kpi-value">{data.atRisk.length}</div>
          <div className="kpi-sub">Cover does not span the trip</div>
        </Card>
      </div>

      {data.atRisk.length > 0 && (
        <Card className="mb-3" style={{ borderColor: '#eec8c0' }}>
          <CardHead title="⚠ Trips at risk" subtitle="Fix these before the departure date" />
          <div className="card-body stack-sm">
            {data.atRisk.map((p) => (
              <div
                key={p.id}
                className="row row-gap-2 wrap"
                style={{ padding: '10px 0', borderBottom: '1px solid var(--cream-200)' }}
              >
                <div className="grow" style={{ minWidth: 220 }}>
                  <div className="strong">
                    {p.parkName} · <span className="mono-num small">{p.permitNumber}</span>
                  </div>
                  <div className="tiny" style={{ color: 'var(--danger)' }}>
                    {p.riskReason}
                  </div>
                </div>
                <div className="right nowrap">
                  <div className="small">{dateFmt(p.tripDate)}</div>
                  <div className="tiny muted">{p.bookingReference}</div>
                </div>
                {p.status === 'PENDING' ? (
                  <Button size="sm" onClick={() => openDecide(p)}>
                    Decide
                  </Button>
                ) : (
                  <Button size="sm" onClick={() => openRenew(p)}>
                    Renew
                  </Button>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card className="mb-3">
        <CardHead
          title="Trips needing a permit"
          subtitle="Active future bookings with no permit on file"
          actions={
            <span className={`badge ${data.awaitingRequest.length ? 'badge-warning' : 'badge-success'}`}>
              {data.awaitingRequest.length} to raise
            </span>
          }
        />
        <div className="card-body">
          {data.awaitingRequest.length === 0 ? (
            <div className="inline-note ok">
              <span aria-hidden="true">✓</span>
              <span>Every upcoming trip has a permit on file.</span>
            </div>
          ) : (
            <div className="stack-sm">
              {data.awaitingRequest.map((b) => (
                <div
                  key={b.bookingId}
                  className="row row-gap-2 wrap"
                  style={{ padding: '10px 0', borderBottom: '1px solid var(--cream-200)' }}
                >
                  <div className="grow" style={{ minWidth: 220 }}>
                    <div className="strong">{b.packageName}</div>
                    <div className="tiny muted">
                      <span className="mono-num">{b.bookingReference}</span> · {b.customerName} ·{' '}
                      {b.parkName} · {b.participants} pax
                    </div>
                  </div>
                  <div className="right nowrap">
                    <div className="small">{dateFmt(b.tripDate)}</div>
                    <div
                      className="tiny"
                      style={{ color: b.urgent ? 'var(--danger)' : 'var(--ink-400)' }}
                    >
                      {b.daysToDeparture} days out
                    </div>
                  </div>
                  <div className="right nowrap">
                    <div className="tiny muted">est. fee</div>
                    <div className="small mono-num">{money(b.estimatedFee)}</div>
                  </div>
                  {b.urgent && <StatusBadge value="URGENT" tone="danger" label="Urgent" />}
                  <Button size="sm" onClick={() => openRequest(b)}>
                    Request permit
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      </Card>

      <div className="section-title">All permits</div>
      <DataTable
        columns={columns}
        rows={rows}
        initialSort={{ key: 'expiryDate', dir: 'asc' }}
        searchPlaceholder="Search permit, park, booking or customer…"
        emptyIcon="📜"
        emptyTitle="No permits yet"
        emptyMessage="Raise a permit request for an upcoming trip to get started."
        rowClassName={(r) => (r.atRisk ? 'row-alert' : '')}
        filters={
          <>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 160 }}
              aria-label="Filter by status"
            >
              <option value="">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.charAt(0) + s.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
            <button
              type="button"
              className={`chip${riskOnly ? ' on' : ''}`}
              onClick={() => setRiskOnly((v) => !v)}
            >
              Needs attention only
            </button>
          </>
        }
      />

      {/* ------------------------------------------------ Request a permit */}
      <Modal
        open={!!requesting}
        title="Request a park permit"
        onClose={() => !busy && setRequesting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRequesting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={submitRequest} loading={busy}>
              Raise request
            </Button>
          </>
        }
      >
        {requesting && (
          <div className="stack">
            <dl className="dl">
              <dt>Booking</dt>
              <dd className="mono-num">{requesting.bookingReference}</dd>
              <dt>Trip</dt>
              <dd>{requesting.packageName}</dd>
              <dt>Park</dt>
              <dd>{requesting.parkName}</dd>
              <dt>Authority</dt>
              <dd>{requesting.permitAuthority ?? '—'}</dd>
              <dt>Dates</dt>
              <dd>
                {dateFmt(requesting.tripDate)} → {dateFmt(requesting.tripEndDate)}
              </dd>
              <dt>Travellers</dt>
              <dd>{requesting.participants}</dd>
              <dt>Estimated fee</dt>
              <dd className="mono-num">{money(requesting.estimatedFee)}</dd>
            </dl>

            <Field
              label="Valid until"
              hint="Defaults to a week after the trip ends. Must not be before departure."
            >
              <Input type="date" value={reqExpiry} onChange={(e) => setReqExpiry(e.target.value)} />
            </Field>

            <Field label="Notes">
              <Textarea
                rows={3}
                value={reqNotes}
                onChange={(e) => setReqNotes(e.target.value)}
                placeholder="Reference numbers, quota notes…"
              />
            </Field>
          </div>
        )}
      </Modal>

      {/* -------------------------------------------------------- Decide */}
      <Modal
        open={!!deciding}
        title={deciding ? `Permit ${deciding.permitNumber}` : ''}
        onClose={() => !busy && setDeciding(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeciding(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={reject} loading={busy}>
              Reject
            </Button>
            <Button onClick={approve} loading={busy}>
              Approve
            </Button>
          </>
        }
      >
        {deciding && (
          <div className="stack">
            <dl className="dl">
              <dt>Park</dt>
              <dd>{deciding.parkName}</dd>
              <dt>Booking</dt>
              <dd className="mono-num">{deciding.bookingReference}</dd>
              <dt>Trip</dt>
              <dd>
                {dateFmt(deciding.tripDate)} → {dateFmt(deciding.tripEndDate)}
              </dd>
              <dt>Fee</dt>
              <dd className="mono-num">{money(deciding.feeAmount)}</dd>
            </dl>
            <Field label="Valid until">
              <Input
                type="date"
                value={decideExpiry}
                onChange={(e) => setDecideExpiry(e.target.value)}
              />
            </Field>
            <Field label="Notes">
              <Textarea rows={3} value={decideNotes} onChange={(e) => setDecideNotes(e.target.value)} />
            </Field>
          </div>
        )}
      </Modal>

      {/* --------------------------------------------------------- Renew */}
      <Modal
        open={!!renewing}
        title={renewing ? `Renew ${renewing.permitNumber}` : ''}
        onClose={() => !busy && setRenewing(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRenewing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={submitRenew} loading={busy}>
              Renew permit
            </Button>
          </>
        }
      >
        {renewing && (
          <div className="stack">
            {renewing.riskReason && (
              <div className="inline-note danger">
                <span aria-hidden="true">⚠</span>
                <span>{renewing.riskReason}</span>
              </div>
            )}
            <dl className="dl">
              <dt>Currently valid to</dt>
              <dd>{dateFmt(renewing.expiryDate)}</dd>
              <dt>Trip ends</dt>
              <dd>{dateFmt(renewing.tripEndDate)}</dd>
              <dt>Renewals so far</dt>
              <dd>{renewing.renewalCount}</dd>
            </dl>
            <Field label="New expiry date" hint="Must be later than the current expiry.">
              <Input
                type="date"
                value={renewExpiry}
                onChange={(e) => setRenewExpiry(e.target.value)}
              />
            </Field>
            <Field label="Notes">
              <Textarea rows={3} value={renewNotes} onChange={(e) => setRenewNotes(e.target.value)} />
            </Field>
          </div>
        )}
      </Modal>

      {/* ---------------------------------------------------------- Edit */}
      <Modal
        open={!!editingPermit}
        title={editingPermit ? `Edit ${editingPermit.permitNumber}` : ''}
        onClose={() => !busy && setEditingPermit(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditingPermit(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={submitEditPermit} loading={busy}>
              Save changes
            </Button>
          </>
        }
      >
        {editingPermit && (
          <div className="stack">
            <dl className="dl">
              <dt>Park</dt>
              <dd>{editingPermit.parkName}</dd>
              <dt>Booking</dt>
              <dd className="mono-num">{editingPermit.bookingReference}</dd>
              <dt>Trip</dt>
              <dd>
                {dateFmt(editingPermit.tripDate)} → {dateFmt(editingPermit.tripEndDate)}
              </dd>
            </dl>
            <div className="form-grid">
              <Field label="Valid until" hint="Must not be before the trip starts.">
                <Input type="date" value={editExpiry} onChange={(e) => setEditExpiry(e.target.value)} />
              </Field>
              <Field
                label="Travellers covered"
                hint={`${editingPermit.bookingParticipants} on the booking. Fewer puts the trip at risk.`}
              >
                <Input
                  type="number"
                  min={1}
                  max={editingPermit.bookingParticipants}
                  value={editCovered}
                  onChange={(e) => setEditCovered(e.target.value)}
                />
              </Field>
            </div>
            <div className="price-lines" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
              <div className="price-line total">
                <span>New fee</span>
                <span className="mono-num">
                  {money(
                    (Number(editingPermit.feeAmount) / Math.max(1, editingPermit.coveredParticipants)) *
                      Math.max(0, Number(editCovered) || 0),
                  )}
                </span>
              </div>
            </div>
            <Field label="Notes">
              <Textarea rows={3} value={editNotes} onChange={(e) => setEditNotes(e.target.value)} />
            </Field>
          </div>
        )}
      </Modal>

      {/* -------------------------------------------------------- Delete */}
      <Modal
        open={!!deletingPermit}
        title="Delete permit"
        onClose={() => !busy && setDeletingPermit(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeletingPermit(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDeletePermit} loading={busy}>
              Delete permit
            </Button>
          </>
        }
      >
        {deletingPermit && (
          <div className="stack">
            <p>
              Delete <strong>{deletingPermit.permitNumber}</strong> for {deletingPermit.parkName} (
              {deletingPermit.bookingReference})?
            </p>
            {deletingPermit.status !== 'EXPIRED' && (
              <div className="inline-note warn">
                <span aria-hidden="true">⚠</span>
                <span>The trip will show under "Trips needing a permit" again until a new one is raised.</span>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
