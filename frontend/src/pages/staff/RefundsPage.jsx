import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { bookingApi, refundApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../api/client';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import {
  Button,
  Card,
  ErrorState,
  Field,
  Input,
  Modal,
  Select,
  SkeletonTable,
  Textarea,
  dateFmt,
  dateTimeFmt,
  money,
  titleCase,
} from '../../components/ui';

const STATUSES = ['REQUESTED', 'APPROVED', 'PROCESSED', 'REJECTED'];

export default function RefundsPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const refunds = useApi(() => refundApi.list(), []);

  const [status, setStatus] = useState('');
  const [working, setWorking] = useState(null);
  const [approvedAmount, setApprovedAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);

  const bookings = useApi(() => bookingApi.list(), []);
  const [raiseOpen, setRaiseOpen] = useState(false);
  const [raiseBookingId, setRaiseBookingId] = useState('');
  const [raiseQuote, setRaiseQuote] = useState(null);
  const [raiseReason, setRaiseReason] = useState('');
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);

  const all = refunds.data ?? [];
  const rows = all.filter((r) => !status || r.status === status);

  const open = (r) => {
    setWorking(r);
    setApprovedAmount(String(r.approvedAmount ?? r.adjustedAmount ?? r.calculatedAmount ?? 0));
    setNotes(r.decisionNotes ?? '');
    setConfirmWithdraw(false);
  };

  // Cancelled bookings with money held and no refund in flight or already paid out.
  const refundable = (bookings.data ?? []).filter(
    (b) =>
      b.status === 'CANCELLED' &&
      Number(b.amountPaid) > 0 &&
      !all.some((r) => r.bookingId === b.id && ['REQUESTED', 'APPROVED', 'PROCESSED'].includes(r.status)),
  );

  const openRaise = () => {
    setRaiseBookingId('');
    setRaiseQuote(null);
    setRaiseReason('');
    setRaiseOpen(true);
  };

  const chooseRaiseBooking = async (id) => {
    setRaiseBookingId(id);
    setRaiseQuote(null);
    if (!id) return;
    try {
      setRaiseQuote(await refundApi.quote(Number(id)));
    } catch (err) {
      toast.error(errorMessage(err), 'Could not calculate the refund');
    }
  };

  const submitRaise = async () => {
    setBusy(true);
    try {
      const created = await refundApi.request({ bookingId: Number(raiseBookingId), reason: raiseReason || null });
      toast.success(
        `${created.refundReference} raised for ${money(created.calculatedAmount)} (${created.policyApplied}).`,
        'Refund raised',
      );
      setRaiseOpen(false);
      refunds.reload();
      bookings.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not raise refund');
    } finally {
      setBusy(false);
    }
  };

  const saveChanges = () =>
    act(
      () =>
        refundApi.update(working.id, {
          amount: Number(approvedAmount),
          decisionNotes: notes || null,
        }),
      `Refund ${working.refundReference} now set to ${money(Number(approvedAmount))}.`,
      'Refund adjusted',
    );

  const withdraw = () =>
    act(
      () => refundApi.remove(working.id),
      `Refund request ${working.refundReference} withdrawn.`,
      'Refund withdrawn',
    );

  const act = async (fn, successMsg, title) => {
    setBusy(true);
    try {
      await fn();
      toast.success(successMsg, title);
      setWorking(null);
      refunds.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update refund');
    } finally {
      setBusy(false);
    }
  };

  const approve = () =>
    act(
      () => refundApi.approve(working.id, { approvedAmount: Number(approvedAmount), decisionNotes: notes || null }),
      `${money(Number(approvedAmount))} approved for ${working.customerName}.`,
      'Refund approved',
    );

  const reject = () =>
    act(
      () => refundApi.reject(working.id, { decisionNotes: notes || null }),
      `Refund ${working.refundReference} declined.`,
      'Refund rejected',
    );

  const process = () =>
    act(
      () => refundApi.process(working.id),
      `Refund released to ${working.customerName}.`,
      'Refund processed',
    );

  const totals = {
    pending: all.filter((r) => r.status === 'REQUESTED').length,
    approved: all.filter((r) => r.status === 'APPROVED').length,
    processedValue: all
      .filter((r) => r.status === 'PROCESSED')
      .reduce((sum, r) => sum + Number(r.approvedAmount ?? 0), 0),
    awaitingValue: all
      .filter((r) => r.status === 'REQUESTED' || r.status === 'APPROVED')
      .reduce((sum, r) => sum + Number(r.approvedAmount ?? r.adjustedAmount ?? r.calculatedAmount ?? 0), 0),
  };

  const columns = [
    {
      key: 'refundReference',
      header: 'Refund',
      width: 138,
      render: (r) => (
        <div>
          <div className="mono-num small strong">{r.refundReference}</div>
          <div className="tiny muted">{dateFmt(r.requestedAt)}</div>
        </div>
      ),
    },
    {
      key: 'customerName',
      header: 'Customer / trip',
      render: (r) => (
        <div>
          <div className="strong">{r.customerName}</div>
          <div className="tiny muted truncate" style={{ maxWidth: 280 }}>
            {r.packageName} · <span className="mono-num">{r.bookingReference}</span>
          </div>
        </div>
      ),
    },
    {
      key: 'policyApplied',
      header: 'Policy applied',
      width: 250,
      render: (r) => (
        <div>
          <div className="small">{r.policyApplied}</div>
          <div className="tiny muted">
            cancelled {r.daysBeforeTrip} day(s) before departure
          </div>
        </div>
      ),
    },
    {
      key: 'amountPaidAtRequest',
      header: 'Paid',
      width: 106,
      align: 'right',
      sortValue: (r) => Number(r.amountPaidAtRequest),
      render: (r) => <span className="mono-num">{money(r.amountPaidAtRequest)}</span>,
    },
    {
      key: 'calculatedAmount',
      header: 'Refund due',
      width: 128,
      align: 'right',
      sortValue: (r) => Number(r.calculatedAmount),
      render: (r) => (
        <div>
          <div className="mono-num strong">{money(r.approvedAmount ?? r.adjustedAmount ?? r.calculatedAmount)}</div>
          <div className="tiny muted">
            {r.adjustedAmount != null && r.approvedAmount == null
              ? `adjusted from ${money(r.calculatedAmount)}`
              : `${r.refundPercentage}% of paid`}
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 122,
      render: (r) => <StatusBadge value={r.status} />,
    },
    {
      key: 'actions',
      header: '',
      width: 110,
      sortable: false,
      searchable: false,
      render: (r) => (
        <Button size="sm" variant="outline" onClick={() => open(r)}>
          {r.status === 'REQUESTED' ? 'Review' : r.status === 'APPROVED' ? 'Process' : 'View'}
        </Button>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Finance</div>
          <h1>Refunds</h1>
          <p className="lede">
            Refund amounts are calculated from our cancellation-window policy: full refund more than
            7 days before departure, 50% within 7 days, none inside 48 hours. You can override the
            figure before approving.
          </p>
        </div>
        <Button onClick={openRaise}>+ Raise refund</Button>
      </div>

      <div className="grid grid-4 mb-3">
        <Card className={`kpi ${totals.pending ? 'kpi-amber' : ''}`}>
          <span className="kpi-icon" aria-hidden="true">
            📥
          </span>
          <div className="kpi-label">Awaiting review</div>
          <div className="kpi-value">{totals.pending}</div>
        </Card>
        <Card className="kpi">
          <span className="kpi-icon" aria-hidden="true">
            ✓
          </span>
          <div className="kpi-label">Approved, not paid</div>
          <div className="kpi-value">{totals.approved}</div>
        </Card>
        <Card className="kpi kpi-terracotta">
          <span className="kpi-icon" aria-hidden="true">
            💷
          </span>
          <div className="kpi-label">Committed</div>
          <div className="kpi-value">{money(totals.awaitingValue)}</div>
          <div className="kpi-sub">Requested + approved</div>
        </Card>
        <Card className="kpi">
          <span className="kpi-icon" aria-hidden="true">
            ↩️
          </span>
          <div className="kpi-label">Refunded to date</div>
          <div className="kpi-value">{money(totals.processedValue)}</div>
        </Card>
      </div>

      {refunds.loading ? (
        <SkeletonTable rows={5} cols={7} />
      ) : refunds.error ? (
        <ErrorState message={refunds.error} onRetry={refunds.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'requestedAt', dir: 'desc' }}
          searchPlaceholder="Search refund, customer or booking…"
          emptyIcon="↩️"
          emptyTitle="No refunds requested"
          emptyMessage="Cancelling a paid booking raises a refund request automatically."
          rowClassName={(r) => (r.status === 'REQUESTED' ? 'row-alert' : '')}
          filters={
            <Select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              style={{ width: 170 }}
              aria-label="Filter by status"
            >
              <option value="">All statuses</option>
              {STATUSES.map((x) => (
                <option key={x} value={x}>
                  {titleCase(x)}
                </option>
              ))}
            </Select>
          }
        />
      )}

      <Modal
        open={!!working}
        title={working ? `Refund ${working.refundReference}` : ''}
        onClose={() => !busy && setWorking(null)}
        wide
        footer={
          working && (
            <>
              <Button variant="ghost" onClick={() => setWorking(null)} disabled={busy}>
                Close
              </Button>
              {working.status === 'REQUESTED' && confirmWithdraw && (
                <>
                  <Button variant="ghost" onClick={() => setConfirmWithdraw(false)} disabled={busy}>
                    Keep request
                  </Button>
                  <Button variant="danger" onClick={withdraw} loading={busy}>
                    Yes, withdraw
                  </Button>
                </>
              )}
              {working.status === 'REQUESTED' && !confirmWithdraw && (
                <>
                  <Button variant="ghost" onClick={() => setConfirmWithdraw(true)} disabled={busy}>
                    Withdraw
                  </Button>
                  <Button variant="outline" onClick={saveChanges} loading={busy}>
                    Save changes
                  </Button>
                  <Button variant="danger" onClick={reject} loading={busy}>
                    Reject
                  </Button>
                  <Button onClick={approve} loading={busy}>
                    Approve {money(Number(approvedAmount || 0))}
                  </Button>
                </>
              )}
              {working.status === 'APPROVED' && (
                <Button onClick={process} loading={busy}>
                  Process payout
                </Button>
              )}
            </>
          )
        }
      >
        {working && (
          <div className="stack">
            <div className="row row-gap-2 wrap">
              <StatusBadge value={working.status} />
              <span className="tiny muted mono-num">{working.bookingReference}</span>
              <span className="spacer" />
              <Button
                size="sm"
                variant="ghost"
                onClick={() => navigate(`/staff/invoices/${working.bookingId}`)}
              >
                View invoice
              </Button>
            </div>

            <Card className="card-pad-sm">
              <div className="section-title">Policy calculation</div>
              <div className="price-lines" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
                <div className="price-line">
                  <span className="muted">Amount paid at request</span>
                  <span className="mono-num">{money(working.amountPaidAtRequest)}</span>
                </div>
                <div className="price-line">
                  <span className="muted">
                    Cancelled {working.daysBeforeTrip} day(s) before departure
                  </span>
                  <span className="mono-num">× {working.refundPercentage}%</span>
                </div>
                <div className="price-line total">
                  <span>Policy amount</span>
                  <span className="mono-num">{money(working.calculatedAmount)}</span>
                </div>
              </div>
              <div className="inline-note mt-2">
                <span aria-hidden="true">ℹ</span>
                <span>{working.policyApplied}</span>
              </div>
            </Card>

            <dl className="dl">
              <dt>Customer</dt>
              <dd>
                {working.customerName}
                <div className="tiny muted">{working.customerEmail}</div>
              </dd>
              <dt>Trip</dt>
              <dd>
                {working.packageName} · {dateFmt(working.tripDate)}
              </dd>
              <dt>Requested</dt>
              <dd>{dateTimeFmt(working.requestedAt)}</dd>
              {working.reason && (
                <>
                  <dt>Reason</dt>
                  <dd>{working.reason}</dd>
                </>
              )}
              {working.processedAt && (
                <>
                  <dt>Processed</dt>
                  <dd>
                    {dateTimeFmt(working.processedAt)}
                    {working.processedByName ? ` by ${working.processedByName}` : ''}
                  </dd>
                </>
              )}
            </dl>

            {working.status === 'REQUESTED' && (
              <>
                <Field
                  label="Amount to approve"
                  hint="Defaults to the policy figure. Cannot exceed what the customer paid."
                >
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    max={working.amountPaidAtRequest}
                    value={approvedAmount}
                    onChange={(e) => setApprovedAmount(e.target.value)}
                  />
                </Field>
                <Field label="Decision notes">
                  <Textarea
                    rows={3}
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    placeholder="Why you approved or adjusted this amount"
                  />
                </Field>
              </>
            )}

            {working.status === 'APPROVED' && (
              <div className="inline-note ok">
                <span aria-hidden="true">✓</span>
                <span>
                  Approved for <strong>{money(working.approvedAmount)}</strong>. Processing the
                  payout reduces the amount held against the booking and closes the record.
                </span>
              </div>
            )}

            {working.decisionNotes && (
              <div>
                <div className="section-title">Decision notes</div>
                <p className="small">{working.decisionNotes}</p>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* ------------------------------------------------------ Raise refund */}
      <Modal
        open={raiseOpen}
        title="Raise a refund"
        onClose={() => !busy && setRaiseOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRaiseOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={submitRaise} loading={busy} disabled={!raiseQuote?.eligible}>
              Raise refund
            </Button>
          </>
        }
      >
        <div className="stack">
          <div className="inline-note">
            <span aria-hidden="true">ℹ</span>
            <span>
              Cancelling a paid booking raises its refund automatically. Use this when a request was
              withdrawn or rejected and needs raising again.
            </span>
          </div>

          <Field label="Cancelled booking">
            <Select value={raiseBookingId} onChange={(e) => chooseRaiseBooking(e.target.value)} disabled={bookings.loading}>
              <option value="">
                {bookings.loading
                  ? 'Loading bookings…'
                  : refundable.length
                    ? 'Choose a cancelled booking with money held'
                    : 'No cancelled bookings are waiting for a refund'}
              </option>
              {refundable.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.bookingReference} — {b.customerName} — paid {money(b.amountPaid)}
                </option>
              ))}
            </Select>
          </Field>

          {raiseQuote && (
            <Card className="card-pad-sm">
              <div className="section-title">Policy calculation</div>
              <div className="price-lines" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
                <div className="price-line">
                  <span className="muted">Amount held</span>
                  <span className="mono-num">{money(raiseQuote.amountPaid)}</span>
                </div>
                <div className="price-line">
                  <span className="muted">{raiseQuote.daysBeforeTrip} day(s) before departure</span>
                  <span className="mono-num">× {raiseQuote.refundPercentage}%</span>
                </div>
                <div className="price-line total">
                  <span>Refund due</span>
                  <span className="mono-num">{money(raiseQuote.refundAmount)}</span>
                </div>
              </div>
              <div className="tiny muted mt-1">{raiseQuote.policyExplanation}</div>
            </Card>
          )}

          <Field label="Reason (optional)">
            <Textarea rows={2} value={raiseReason} onChange={(e) => setRaiseReason(e.target.value)} />
          </Field>
        </div>
      </Modal>
    </div>
  );
}
