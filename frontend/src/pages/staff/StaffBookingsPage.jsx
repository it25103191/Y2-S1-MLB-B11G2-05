import { useState } from 'react';
import { bookingApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../api/client';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import PermitPanel from '../../components/PermitPanel';
import BookingTimeline from '../../components/BookingTimeline';
import {
  Button,
  Card,
  ErrorState,
  Field,
  Modal,
  Select,
  SkeletonTable,
  Textarea,
  dateFmt,
  money,
  relativeDays,
} from '../../components/ui';

const STATUSES = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'];

export default function StaffBookingsPage() {
  const toast = useToast();
  const bookings = useApi(() => bookingApi.list(), []);
  const [statusFilter, setStatusFilter] = useState('');
  const [detail, setDetail] = useState(null);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const rows = (bookings.data ?? []).filter((b) => !statusFilter || b.status === statusFilter);

  const changeStatus = async (booking, status) => {
    setBusy(true);
    try {
      await bookingApi.changeStatus(booking.id, status);
      toast.success(`${booking.bookingReference} is now ${status.toLowerCase()}.`, 'Status updated');
      setDetail(null);
      bookings.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update status');
    } finally {
      setBusy(false);
    }
  };

  const doCancel = async () => {
    setBusy(true);
    try {
      await bookingApi.cancel(cancelTarget.id, reason);
      toast.success(`${cancelTarget.bookingReference} cancelled.`, 'Booking cancelled');
      setCancelTarget(null);
      setDetail(null);
      setReason('');
      bookings.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not cancel');
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await bookingApi.remove(deleteTarget.id);
      toast.success(`${deleteTarget.bookingReference} deleted and its seats released.`, 'Booking deleted');
      setDeleteTarget(null);
      setDetail(null);
      bookings.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const columns = [
    {
      key: 'bookingReference',
      header: 'Reference',
      width: 132,
      render: (r) => <span className="mono-num small strong">{r.bookingReference}</span>,
    },
    {
      key: 'customerName',
      header: 'Customer',
      render: (r) => (
        <div>
          <div className="strong">{r.customerName}</div>
          <div className="tiny muted">{r.customerEmail}</div>
        </div>
      ),
    },
    {
      key: 'packageName',
      header: 'Safari',
      render: (r) => (
        <div>
          <div>{r.packageName}</div>
          <div className="tiny muted">{r.parkName}</div>
        </div>
      ),
    },
    {
      key: 'tripDate',
      header: 'Departure',
      width: 140,
      render: (r) => (
        <div>
          <div>{dateFmt(r.tripDate)}</div>
          <div className="tiny muted">{relativeDays(r.tripDate)}</div>
        </div>
      ),
    },
    { key: 'participants', header: 'Pax', width: 62, align: 'center' },
    {
      key: 'totalPrice',
      header: 'Total',
      width: 108,
      align: 'right',
      sortValue: (r) => Number(r.totalPrice),
      render: (r) => <span className="mono-num">{money(r.totalPrice)}</span>,
    },
    {
      key: 'balanceDue',
      header: 'Balance',
      width: 112,
      align: 'right',
      sortValue: (r) => Number(r.balanceDue),
      render: (r) =>
        r.fullyPaid ? (
          <span className="tiny" style={{ color: 'var(--success)' }}>
            Paid
          </span>
        ) : (
          <span className="mono-num" style={{ color: r.paymentOverdue ? 'var(--danger)' : undefined }}>
            {money(r.balanceDue)}
          </span>
        ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 126,
      render: (r) => <StatusBadge value={r.status} />,
    },
    {
      key: 'actions',
      header: '',
      width: 78,
      sortable: false,
      searchable: false,
      render: (r) => (
        <Button size="sm" variant="outline" onClick={() => setDetail(r)}>
          Open
        </Button>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Reservations</div>
          <h1>All bookings</h1>
          <p className="lede">
            Every reservation in the system. Search by customer, reference or safari, and change
            status as trips progress.
          </p>
        </div>
      </div>

      {bookings.loading ? (
        <SkeletonTable rows={8} cols={9} />
      ) : bookings.error ? (
        <ErrorState message={bookings.error} onRetry={bookings.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'tripDate', dir: 'desc' }}
          searchPlaceholder="Search reference, customer, safari…"
          emptyIcon="🗓️"
          emptyTitle="No bookings yet"
          emptyMessage="Bookings created by customers will appear here."
          onRowClick={setDetail}
          rowClassName={(r) => (r.paymentOverdue ? 'row-alert' : '')}
          filters={
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 170 }}
              aria-label="Filter by status"
            >
              <option value="">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.charAt(0) + s.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          }
        />
      )}

      {/* -------------------------------------------------- Booking detail */}
      <Modal
        open={!!detail}
        title={detail ? `Booking ${detail.bookingReference}` : ''}
        onClose={() => !busy && setDetail(null)}
        wide
        footer={
          detail && (
            <>
              <Button variant="ghost" onClick={() => setDetail(null)} disabled={busy}>
                Close
              </Button>
              {detail.status === 'PENDING' && Number(detail.amountPaid) === 0 && (
                <Button variant="ghost" onClick={() => setDeleteTarget(detail)} disabled={busy}>
                  Delete
                </Button>
              )}
              {!['CANCELLED', 'COMPLETED'].includes(detail.status) && (
                <Button variant="danger" onClick={() => setCancelTarget(detail)} disabled={busy}>
                  Cancel booking
                </Button>
              )}
              {detail.status === 'PENDING' && (
                <Button onClick={() => changeStatus(detail, 'CONFIRMED')} loading={busy}>
                  Mark confirmed
                </Button>
              )}
              {detail.status === 'CONFIRMED' && (
                <Button onClick={() => changeStatus(detail, 'COMPLETED')} loading={busy}>
                  Mark completed
                </Button>
              )}
            </>
          )
        }
      >
        {detail && (
          <div className="stack">
            <div className="row row-gap-2 wrap">
              <StatusBadge value={detail.status} />
              {detail.paymentOverdue && (
                <StatusBadge value="OVERDUE" tone="danger" label="Payment overdue" />
              )}
              {detail.fullyPaid && <StatusBadge value="PAID" tone="success" label="Paid in full" />}
            </div>

            <div className="grid grid-2">
              <Card className="card-pad-sm">
                <div className="section-title">Customer</div>
                <dl className="dl">
                  <dt>Name</dt>
                  <dd>{detail.customerName}</dd>
                  <dt>Email</dt>
                  <dd>{detail.customerEmail}</dd>
                  <dt>Booked</dt>
                  <dd>{dateFmt(detail.createdAt)}</dd>
                </dl>
              </Card>

              <Card className="card-pad-sm">
                <div className="section-title">Trip</div>
                <dl className="dl">
                  <dt>Safari</dt>
                  <dd>{detail.packageName}</dd>
                  <dt>Park</dt>
                  <dd>{detail.parkName}</dd>
                  <dt>Departs</dt>
                  <dd>
                    {dateFmt(detail.tripDate)} → {dateFmt(detail.tripEndDate)}
                  </dd>
                  <dt>Travellers</dt>
                  <dd>{detail.participants}</dd>
                </dl>
              </Card>
            </div>

            <Card className="card-pad-sm">
              <div className="section-title">Money</div>
              <div className="price-lines" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
                <div className="price-line">
                  <span className="muted">
                    {money(detail.pricePerPerson)} × {detail.participants}
                  </span>
                  <span>{money(detail.totalPrice)}</span>
                </div>
                <div className="price-line">
                  <span className="muted">Paid</span>
                  <span>{money(detail.amountPaid)}</span>
                </div>
                <div className="price-line total">
                  <span>Balance</span>
                  <span>{money(detail.balanceDue)}</span>
                </div>
              </div>
              <div className="tiny muted mt-1">Payment due {dateFmt(detail.paymentDueDate)}</div>
            </Card>

            <Card className="card-pad-sm">
              <div className="section-title">Park permit</div>
              <PermitPanel bookingId={detail.id} />
            </Card>

            {detail.specialRequests && (
              <Card className="card-pad-sm">
                <div className="section-title">Special requests</div>
                <p className="small">{detail.specialRequests}</p>
              </Card>
            )}

            {detail.status === 'CANCELLED' && detail.cancellationReason && (
              <div className="inline-note danger">
                <span aria-hidden="true">✕</span>
                <span>{detail.cancellationReason}</span>
              </div>
            )}

            <Card className="card-pad-sm">
              <div className="section-title">History</div>
              <BookingTimeline bookingId={detail.id} />
            </Card>
          </div>
        )}
      </Modal>

      {/* -------------------------------------------------- Cancel dialog */}
      <Modal
        open={!!cancelTarget}
        title="Cancel booking"
        onClose={() => !busy && setCancelTarget(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCancelTarget(null)} disabled={busy}>
              Keep it
            </Button>
            <Button variant="danger" onClick={doCancel} loading={busy}>
              Confirm cancellation
            </Button>
          </>
        }
      >
        {cancelTarget && (
          <div className="stack">
            <p>
              Cancel <strong>{cancelTarget.bookingReference}</strong> for {cancelTarget.customerName}?
              The seats are released back to the departure immediately.
            </p>
            <Field label="Reason">
              <Textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
            </Field>
          </div>
        )}
      </Modal>

      <Modal
        open={!!deleteTarget}
        title="Delete booking"
        onClose={() => !busy && setDeleteTarget(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleteTarget(null)} disabled={busy}>
              Keep it
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete permanently
            </Button>
          </>
        }
      >
        {deleteTarget && (
          <div className="stack">
            <p>
              Permanently delete <strong>{deleteTarget.bookingReference}</strong> for{' '}
              {deleteTarget.customerName}? Use this for duplicates and abandoned bookings.
            </p>
            <div className="inline-note warn">
              <span aria-hidden="true">⚠</span>
              <span>
                The server refuses if anything is attached (payments, crew, permits, cases or
                refunds). Cancel the booking instead to keep its history.
              </span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
