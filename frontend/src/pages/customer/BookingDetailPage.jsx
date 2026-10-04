import { Link, useParams } from 'react-router-dom';
import { bookingApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useCurrency } from '../../context/CurrencyContext';
import StatusBadge from '../../components/StatusBadge';
import PermitPanel from '../../components/PermitPanel';
import BookingTimeline from '../../components/BookingTimeline';
import { Photo } from '../../components/Photo';
import { Card, CardHead, ErrorState, Skeleton, SkeletonText, dateFmt, dateTimeFmt, relativeDays } from '../../components/ui';

/** The trip as a sequence of milestones, derived from the booking's own fields. */
function milestones(b, format) {
  const cancelled = b.status === 'CANCELLED';
  const paidSome = Number(b.amountPaid) > 0;
  const upcoming = new Date(`${b.tripDate}T00:00:00`) >= new Date(new Date().toDateString());
  return [
    { key: 'booked', state: 'done', title: 'Seats reserved', note: dateFmt(b.createdAt) },
    {
      key: 'paid',
      state: b.fullyPaid ? 'done' : cancelled ? 'bad' : paidSome ? 'now' : b.paymentOverdue ? 'bad' : 'now',
      title: b.fullyPaid ? 'Paid in full' : paidSome ? 'Part paid' : 'Payment',
      note: b.fullyPaid
        ? format(b.totalPrice)
        : `${format(b.amountPaid)} of ${format(b.totalPrice)} · balance due ${dateFmt(b.paymentDueDate)}`,
    },
    {
      key: 'confirmed',
      state: ['CONFIRMED', 'COMPLETED'].includes(b.status) ? 'done' : cancelled ? 'bad' : '',
      title: cancelled ? 'Cancelled' : 'Confirmed',
      note: cancelled
        ? dateTimeFmt(b.cancelledAt)
        : ['CONFIRMED', 'COMPLETED'].includes(b.status)
          ? 'Permits, tracker and jeep are being arranged'
          : 'Confirmed automatically once the balance is paid',
    },
    {
      key: 'depart',
      state: b.status === 'COMPLETED' ? 'done' : !cancelled && b.status === 'CONFIRMED' && upcoming ? 'now' : '',
      title: 'Departure',
      note: `${dateFmt(b.tripDate)}${upcoming && !cancelled ? ` · ${relativeDays(b.tripDate)}` : ''}`,
    },
    {
      key: 'home',
      state: b.status === 'COMPLETED' ? 'done' : '',
      title: b.status === 'COMPLETED' ? 'Trip complete' : 'Home again',
      note: dateFmt(b.tripEndDate),
    },
  ];
}

export default function BookingDetailPage() {
  const { id } = useParams();
  const { format, formatUsd, currency } = useCurrency();
  const booking = useApi(() => bookingApi.get(id), [id]);

  if (booking.loading) {
    return (
      <div className="page">
        <Skeleton width="40%" height={40} className="mb-3" />
        <Card className="card-pad">
          <SkeletonText lines={6} />
        </Card>
      </div>
    );
  }

  if (booking.error) {
    return (
      <div className="page">
        <ErrorState message={booking.error} onRetry={booking.reload} />
      </div>
    );
  }

  const b = booking.data;
  const payable = !b.fullyPaid && b.status !== 'CANCELLED';

  return (
    <div className="page">
      <Link to="/my-trips" className="link">
        <span className="arrow" style={{ transform: 'scaleX(-1)' }}>&rarr;</span> All my trips
      </Link>

      <div className="page-head mt-2">
        <div>
          <div className="eyebrow mono-num">{b.bookingReference}</div>
          <h1>{b.packageName}</h1>
          <p className="lede">
            {b.parkName} · {b.durationDays} {b.durationDays === 1 ? 'day' : 'days'} · {b.participants}{' '}
            {b.participants === 1 ? 'traveller' : 'travellers'}
          </p>
        </div>
        <div className="row row-gap-2">
          <StatusBadge value={b.status} />
          {b.paymentOverdue && <StatusBadge value="OVERDUE" tone="danger" label="Payment overdue" />}
        </div>
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'minmax(0, 1.6fr) minmax(0, 1fr)', alignItems: 'start', gap: 28 }}>
        <div className="stack-lg">
          <Photo src={b.packageImageUrl} caption={b.parkName} style={{ aspectRatio: '21 / 9' }} />

          <Card className="card-pad">
            <div className="section-title">Your trip, step by step</div>
            <ol className="trip-steps">
              {milestones(b, format).map((m) => (
                <li key={m.key} className={m.state}>
                  <b>{m.title}</b>
                  <span>{m.note}</span>
                </li>
              ))}
            </ol>
          </Card>

          <Card className="card-pad">
            <div className="section-title">Booking history</div>
            <BookingTimeline bookingId={b.id} version={b.status} />
          </Card>

          <Card className="card-pad">
            <div className="section-title">Park permit</div>
            <PermitPanel bookingId={b.id} />

            {b.specialRequests && (
              <>
                <div className="section-title mt-3">Your note to us</div>
                <p className="small">{b.specialRequests}</p>
              </>
            )}

            {b.status === 'CANCELLED' && (
              <div className="inline-note danger mt-3">
                <div>
                  <strong>Cancelled {dateTimeFmt(b.cancelledAt)}</strong>
                  {b.cancellationReason && <div className="tiny mt-1">{b.cancellationReason}</div>}
                </div>
              </div>
            )}
          </Card>
        </div>

        <Card style={{ position: 'sticky', top: 90 }}>
          <CardHead title="Payment" subtitle={currency !== 'USD' ? `Shown in ${currency} at today's rate` : undefined} />
          <div className="card-body">
            <div className="price-lines" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
              <div className="price-line">
                <span>
                  {b.participants} × {format(b.pricePerPerson, { decimals: 0 })}
                </span>
                <span>{format(b.totalPrice)}</span>
              </div>
              <div className="price-line">
                <span>Paid to date</span>
                <span style={{ color: 'var(--success)' }}>{format(b.amountPaid)}</span>
              </div>
              <div className="price-line total">
                <span>{b.fullyPaid ? 'Total paid' : 'Balance due'}</span>
                <span>{b.fullyPaid ? format(b.totalPrice) : format(b.balanceDue)}</span>
              </div>
              {currency !== 'USD' && (
                <div className="fx-note right">≈ {formatUsd(b.fullyPaid ? b.totalPrice : b.balanceDue)}</div>
              )}
            </div>

            {payable && (
              <div className={`inline-note ${b.paymentOverdue ? 'danger' : 'warn'} mt-2`}>
                <span>
                  {b.paymentOverdue ? 'Payment overdue since ' : 'Balance due by '}
                  <strong>{dateFmt(b.paymentDueDate)}</strong>.
                </span>
              </div>
            )}

            {b.fullyPaid && (
              <div className="inline-note ok mt-2">
                <span>This trip is paid in full.</span>
              </div>
            )}

            <div className="stack-sm mt-2">
              {payable && (
                <Link className="btn btn-block btn-lg" to={`/my-trips/${b.id}/pay`}>
                  Pay {format(b.balanceDue)} <span className="arrow">&rarr;</span>
                </Link>
              )}
              <Link className="btn btn-outline btn-block" to={`/invoices/${b.id}`}>
                View invoice
              </Link>
              <Link className="btn btn-ghost btn-block" to="/my-support">
                Message our team
              </Link>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
}
