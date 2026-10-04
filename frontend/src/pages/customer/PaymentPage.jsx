import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { bookingApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useCurrency } from '../../context/CurrencyContext';
import PaymentForm from '../../components/PaymentForm';
import StatusBadge from '../../components/StatusBadge';
import { Photo } from '../../components/Photo';
import { Card, CardHead, EmptyState, ErrorState, Skeleton, SkeletonText, dateFmt } from '../../components/ui';

function Steps({ step }) {
  const labels = ['Review', 'Pay', 'Done'];
  return (
    <div className="steps">
      {labels.map((l, i) => (
        <span key={l} style={{ display: 'contents' }}>
          {i > 0 && <span className="step-sep" />}
          <span className={`step${step === i ? ' active' : ''}${step > i ? ' done' : ''}`}>
            <span className="step-num">{step > i ? '✓' : i + 1}</span>
            {l}
          </span>
        </span>
      ))}
    </div>
  );
}

export default function PaymentPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { format, formatUsd, currency } = useCurrency();
  const booking = useApi(() => bookingApi.get(id), [id]);
  const [result, setResult] = useState(null);

  if (booking.loading) {
    return (
      <div className="page">
        <Skeleton width="40%" height={40} className="mb-3" />
        <Card className="card-pad">
          <SkeletonText lines={7} />
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

  const b = result?.booking ?? booking.data;
  const settled = Number(b.balanceDue) <= 0;
  const step = result?.accepted ? 2 : 1;

  return (
    <div className="page">
      <Link to={`/my-trips/${id}`} className="link">
        <span className="arrow" style={{ transform: 'scaleX(-1)' }}>&rarr;</span> Back to the trip
      </Link>

      <div className="page-head mt-2">
        <div>
          <div className="eyebrow mono-num">{b.bookingReference}</div>
          <h1>
            Pay for your <em>trip</em>
          </h1>
          <p className="lede">
            {b.packageName} · {dateFmt(b.tripDate)} · {b.participants} {b.participants === 1 ? 'traveller' : 'travellers'}
          </p>
        </div>
        <StatusBadge value={b.status} />
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'minmax(0, 1.4fr) minmax(0, 1fr)', alignItems: 'start', gap: 28 }}>
        <Card>
          <div className="card-body">
            <Steps step={step} />

            {settled ? (
              <EmptyState
                icon="✓"
                title="Paid in full"
                message={
                  result?.accepted
                    ? 'Thank you. The payment went through and your trip is confirmed.'
                    : 'There is nothing left to pay on this trip.'
                }
                action={
                  <div className="row row-gap-2" style={{ justifyContent: 'center' }}>
                    <Link className="btn" to={`/invoices/${b.id}`}>
                      View receipt
                    </Link>
                    <Link className="btn btn-outline" to="/my-trips">
                      My trips
                    </Link>
                  </div>
                }
              />
            ) : (
              <>
                {result && !result.accepted && (
                  <div className={`inline-note ${result.status === 'DECLINED' ? 'danger' : 'warn'} mb-2`}>
                    <div>
                      <strong>{result.status === 'DECLINED' ? 'Payment declined' : 'The bank did not answer in time'}</strong>
                      <div className="tiny mt-1">{result.message}</div>
                    </div>
                  </div>
                )}
                {result?.accepted && result.partial && (
                  <div className="inline-note ok mb-2">
                    <div>
                      <strong>Part payment received</strong>
                      <div className="tiny mt-1">{result.message}</div>
                    </div>
                  </div>
                )}
                <PaymentForm key={b.balanceDue} booking={b} onResult={setResult} onCancel={() => navigate(`/my-trips/${id}`)} />
              </>
            )}
          </div>
        </Card>

        <Card>
          <Photo src={b.packageImageUrl} caption={b.parkName} style={{ aspectRatio: '16 / 9' }} />
          <CardHead title="Trip summary" />
          <div className="card-body">
            <dl className="dl">
              <dt>Safari</dt>
              <dd>{b.packageName}</dd>
              <dt>Park</dt>
              <dd>{b.parkName}</dd>
              <dt>Departs</dt>
              <dd>{dateFmt(b.tripDate)}</dd>
              <dt>Returns</dt>
              <dd>{dateFmt(b.tripEndDate)}</dd>
              <dt>Travellers</dt>
              <dd>{b.participants}</dd>
            </dl>

            <div className="price-lines">
              <div className="price-line">
                <span>
                  {b.participants} × {format(b.pricePerPerson, { decimals: 0 })}
                </span>
                <span>{format(b.totalPrice)}</span>
              </div>
              <div className="price-line">
                <span>Paid</span>
                <span style={{ color: 'var(--success)' }}>{format(b.amountPaid)}</span>
              </div>
              <div className="price-line total">
                <span>Balance</span>
                <span>{format(b.balanceDue)}</span>
              </div>
              {currency !== 'USD' && <div className="fx-note right">≈ {formatUsd(b.balanceDue)}</div>}
            </div>

            {b.paymentDueDate && !settled && (
              <div className={`inline-note ${b.paymentOverdue ? 'danger' : 'warn'} mt-2`}>
                <span>
                  {b.paymentOverdue ? 'Overdue since ' : 'Due by '}
                  <strong>{dateFmt(b.paymentDueDate)}</strong>
                </span>
              </div>
            )}

            <Link className="btn btn-outline btn-sm btn-block mt-2" to={`/invoices/${b.id}`}>
              View invoice
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
}
