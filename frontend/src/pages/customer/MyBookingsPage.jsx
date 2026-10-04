import { useState } from 'react';
import { Link } from 'react-router-dom';
import { bookingApi, packageApi, refundApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../api/client';
import StatusBadge from '../../components/StatusBadge';
import { Photo } from '../../components/Photo';
import { useCurrency } from '../../context/CurrencyContext';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Modal,
  Skeleton,
  Textarea,
  dateFmt,
  isoPlusDays,
  relativeDays,
} from '../../components/ui';

const TABS = [
  { key: 'ALL', label: 'All' },
  { key: 'PENDING', label: 'Pending' },
  { key: 'CONFIRMED', label: 'Confirmed' },
  { key: 'COMPLETED', label: 'Completed' },
  { key: 'CANCELLED', label: 'Cancelled' },
];

export default function MyBookingsPage() {
  const toast = useToast();
  const { format: money } = useCurrency();
  const bookings = useApi(() => bookingApi.mine(), []);
  const [tab, setTab] = useState('ALL');
  const [cancelling, setCancelling] = useState(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [refundQuote, setRefundQuote] = useState(null);
  const [editingBooking, setEditingBooking] = useState(null);
  const [editForm, setEditForm] = useState({ tripDate: '', participants: 1, specialRequests: '' });
  const [deletingBooking, setDeletingBooking] = useState(null);

  // Live price and seat check while editing; ignores this booking's own seats.
  const editQuote = useApi(
    () =>
      editingBooking && editForm.tripDate
        ? packageApi.quote(editingBooking.packageId, editForm.tripDate, editForm.participants, editingBooking.id)
        : Promise.resolve(null),
    [editingBooking?.id, editForm.tripDate, editForm.participants],
  );

  const rows = (bookings.data ?? []).filter((b) => tab === 'ALL' || b.status === tab);

  const openCancel = async (b) => {
    setCancelling(b);
    setReason('');
    setRefundQuote(null);
    if (Number(b.amountPaid) > 0) {
      try {
        setRefundQuote(await refundApi.quote(b.id));
      } catch {
        setRefundQuote({ unavailable: true });
      }
    }
  };

  const openEdit = (b) => {
    setEditForm({ tripDate: b.tripDate, participants: b.participants, specialRequests: b.specialRequests ?? '' });
    setEditingBooking(b);
  };

  const saveEdit = async () => {
    setBusy(true);
    try {
      const updated = await bookingApi.update(editingBooking.id, {
        tripDate: editForm.tripDate,
        participants: Number(editForm.participants),
        specialRequests: editForm.specialRequests || null,
      });
      toast.success(
        `${updated.bookingReference}: ${updated.participants} traveller(s) on ${dateFmt(updated.tripDate)}, total ${money(updated.totalPrice)}.`,
        'Booking updated',
      );
      setEditingBooking(null);
      bookings.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update booking');
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await bookingApi.remove(deletingBooking.id);
      toast.success(`Booking ${deletingBooking.bookingReference} deleted and its seats released.`, 'Deleted');
      setDeletingBooking(null);
      bookings.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const doCancel = async () => {
    setBusy(true);
    try {
      await bookingApi.cancel(cancelling.id, reason);
      toast.success(`Booking ${cancelling.bookingReference} has been cancelled.`, 'Cancelled');
      setCancelling(null);
      setReason('');
      bookings.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not cancel');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Your trips</div>
          <h1>
            My <em>trips</em>
          </h1>
          <p className="lede">Every safari you have reserved, with its payment and trip status.</p>
        </div>
        <Link className="btn btn-outline" to="/safaris">
          Browse safaris
        </Link>
      </div>

      <div className="chip-row mb-3">
        {TABS.map((t) => {
          const count =
            t.key === 'ALL'
              ? (bookings.data ?? []).length
              : (bookings.data ?? []).filter((b) => b.status === t.key).length;
          return (
            <button
              key={t.key}
              type="button"
              className={`chip${tab === t.key ? ' on' : ''}`}
              onClick={() => setTab(t.key)}
            >
              {t.label} ({count})
            </button>
          );
        })}
      </div>

      {bookings.loading ? (
        <div className="stack">
          {[0, 1, 2].map((i) => (
            <Card key={i} className="card-pad">
              <Skeleton width="30%" height={17} />
              <Skeleton width="55%" height={11} style={{ marginTop: 10 }} />
              <Skeleton width="40%" height={11} style={{ marginTop: 7 }} />
            </Card>
          ))}
        </div>
      ) : bookings.error ? (
        <ErrorState message={bookings.error} onRetry={bookings.reload} />
      ) : rows.length === 0 ? (
        <EmptyState
          icon="🧳"
          title={tab === 'ALL' ? 'No bookings yet' : `No ${tab.toLowerCase()} bookings`}
          message={
            tab === 'ALL'
              ? 'When you reserve a safari it will show up here with its status and balance.'
              : 'Try a different status filter.'
          }
          action={
            tab === 'ALL' ? (
              <Link className="btn" to="/safaris">
                Find a safari
              </Link>
            ) : (
              <Button variant="outline" onClick={() => setTab('ALL')}>
                Show all
              </Button>
            )
          }
        />
      ) : (
        <div className="stack">
          {rows.map((b) => (
            <Card key={b.id} className="booking-row">
              <div className={`booking-stripe ${b.status}`} />
              <Photo className="booking-thumb" src={b.packageImageUrl} caption={b.parkName} />
              <div className="booking-main">
                <div className="row row-gap-2 wrap mb-1">
                  <StatusBadge value={b.status} />
                  {b.paymentOverdue && <StatusBadge value="OVERDUE" tone="danger" label="Payment overdue" />}
                  <span className="tiny muted mono-num">{b.bookingReference}</span>
                </div>

                <h3>{b.packageName}</h3>
                <div className="small muted">{b.parkName}</div>

                <div className="summary-grid mt-2">
                  <div className="summary-item">
                    <div className="si-label">Departure</div>
                    <div className="si-value">
                      {dateFmt(b.tripDate)}
                      {b.status !== 'CANCELLED' && b.status !== 'COMPLETED' && (
                        <span className="tiny muted"> · {relativeDays(b.tripDate)}</span>
                      )}
                    </div>
                  </div>
                  <div className="summary-item">
                    <div className="si-label">Travellers</div>
                    <div className="si-value">{b.participants}</div>
                  </div>
                  <div className="summary-item">
                    <div className="si-label">Total</div>
                    <div className="si-value">{money(b.totalPrice)}</div>
                  </div>
                  <div className="summary-item">
                    <div className="si-label">Balance due</div>
                    <div className="si-value">
                      {b.fullyPaid ? (
                        <span style={{ color: 'var(--success)' }}>Paid in full</span>
                      ) : (
                        money(b.balanceDue)
                      )}
                    </div>
                  </div>
                </div>

                {b.status === 'CANCELLED' && b.cancellationReason && (
                  <div className="small muted mt-2">Reason: {b.cancellationReason}</div>
                )}
                {b.specialRequests && (
                  <div className="small muted mt-1">Requests: {b.specialRequests}</div>
                )}
              </div>

              <div className="booking-actions">
                <Link className="btn btn-outline btn-sm" to={`/my-trips/${b.id}`}>
                  View trip
                </Link>
                {!b.fullyPaid && b.status !== 'CANCELLED' && (
                  <Link className="btn btn-sm" to={`/my-trips/${b.id}/pay`}>
                    Pay {money(b.balanceDue)}
                  </Link>
                )}
                <Link className="btn btn-ghost btn-sm" to={`/invoices/${b.id}`}>
                  Invoice
                </Link>
                {b.status === 'PENDING' && (
                  <Button variant="ghost" size="sm" onClick={() => openEdit(b)}>
                    Edit
                  </Button>
                )}
                {(b.status === 'PENDING' || b.status === 'CONFIRMED') && (
                  <Button variant="ghost" size="sm" onClick={() => openCancel(b)}>
                    Cancel
                  </Button>
                )}
                {b.status === 'PENDING' && Number(b.amountPaid) === 0 && (
                  <Button variant="ghost" size="sm" onClick={() => setDeletingBooking(b)}>
                    Delete
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal
        open={!!cancelling}
        title="Cancel this booking?"
        onClose={() => !busy && setCancelling(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCancelling(null)} disabled={busy}>
              Keep booking
            </Button>
            <Button variant="danger" onClick={doCancel} loading={busy}>
              Cancel booking
            </Button>
          </>
        }
      >
        {cancelling && (
          <div className="stack">
            <p>
              You are about to cancel <strong>{cancelling.packageName}</strong> on{' '}
              <strong>{dateFmt(cancelling.tripDate)}</strong> (ref {cancelling.bookingReference}).
            </p>
            {Number(cancelling.amountPaid) > 0 &&
              (refundQuote && !refundQuote.unavailable ? (
                <div className={`inline-note ${refundQuote.refundPercentage > 0 ? 'ok' : 'warn'}`}>
                  <span aria-hidden="true">{refundQuote.refundPercentage > 0 ? '↩' : '⚠'}</span>
                  <div>
                    <strong>
                      Refund if you cancel today: {money(refundQuote.refundAmount)} (
                      {refundQuote.refundPercentage}% of the {money(refundQuote.amountPaid)} paid)
                    </strong>
                    <div className="tiny mt-1">{refundQuote.policyExplanation}</div>
                    <div className="tiny mt-1">
                      Cancelling raises the refund request automatically; our finance team confirms it.
                    </div>
                  </div>
                </div>
              ) : (
                <div className="inline-note warn">
                  <span aria-hidden="true">⚠</span>
                  <span>
                    You have paid {money(cancelling.amountPaid)}. Any refund is assessed against our
                    cancellation-window policy and reviewed by our finance team.
                  </span>
                </div>
              ))}
            <Field label="Reason (optional)">
              <Textarea
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Let us know why, so we can improve."
              />
            </Field>
          </div>
        )}
      </Modal>

      <Modal
        open={!!editingBooking}
        title={editingBooking ? `Change booking ${editingBooking.bookingReference}` : ''}
        onClose={() => !busy && setEditingBooking(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditingBooking(null)} disabled={busy}>
              Keep as is
            </Button>
            <Button
              onClick={saveEdit}
              loading={busy}
              disabled={editQuote.data ? !editQuote.data.available : false}
            >
              Save changes
            </Button>
          </>
        }
      >
        {editingBooking && (
          <div className="stack">
            <div className="small muted">
              {editingBooking.packageName} · {editingBooking.parkName}
            </div>
            <div className="form-grid">
              <Field label="Departure date">
                <Input
                  type="date"
                  min={isoPlusDays(1)}
                  value={editForm.tripDate}
                  onChange={(e) => setEditForm((f) => ({ ...f, tripDate: e.target.value }))}
                />
              </Field>
              <Field label="Travellers">
                <Input
                  type="number"
                  min={1}
                  value={editForm.participants}
                  onChange={(e) =>
                    setEditForm((f) => ({ ...f, participants: Math.max(1, Number(e.target.value) || 1) }))
                  }
                />
              </Field>
            </div>
            <Field label="Special requests">
              <Textarea
                rows={2}
                value={editForm.specialRequests}
                onChange={(e) => setEditForm((f) => ({ ...f, specialRequests: e.target.value }))}
              />
            </Field>

            {editQuote.loading ? (
              <Skeleton height={40} />
            ) : editQuote.data ? (
              <div className={`avail-strip ${editQuote.data.available ? 'avail-ok' : 'avail-none'}`}>
                <span aria-hidden="true">{editQuote.data.available ? '✓' : '✕'}</span>
                <span>{editQuote.data.message}</span>
              </div>
            ) : null}

            <div className="price-lines">
              <div className="price-line">
                <span className="muted">Current total</span>
                <span>{money(editingBooking.totalPrice)}</span>
              </div>
              <div className="price-line total">
                <span>New total</span>
                <span>{money(editQuote.data?.totalPrice ?? editingBooking.totalPrice)}</span>
              </div>
            </div>

            <div className="inline-note">
              <span aria-hidden="true">ℹ</span>
              <span>
                The date and group size lock once a guide or park permit is arranged for your trip;
                special requests can always be changed.
              </span>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={!!deletingBooking}
        title="Delete this booking?"
        onClose={() => !busy && setDeletingBooking(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeletingBooking(null)} disabled={busy}>
              Keep booking
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete booking
            </Button>
          </>
        }
      >
        {deletingBooking && (
          <div className="stack">
            <p>
              Delete <strong>{deletingBooking.packageName}</strong> on{' '}
              <strong>{dateFmt(deletingBooking.tripDate)}</strong> (ref {deletingBooking.bookingReference})?
              Nothing has been paid, so it will be removed completely and the seats released.
            </p>
            <div className="inline-note">
              <span aria-hidden="true">ℹ</span>
              <span>Want to keep a record of it? Use Cancel instead.</span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
