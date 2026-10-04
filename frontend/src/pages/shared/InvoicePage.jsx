import { Link, useParams } from 'react-router-dom';
import { paymentApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../../context/AuthContext';
import StatusBadge from '../../components/StatusBadge';
import { BrandMark } from '../../components/Brand';
import { formatMoney, useCurrency } from '../../context/CurrencyContext';
import { BRAND } from '../../brand';
import {
  Button,
  Card,
  ErrorState,
  Skeleton,
  SkeletonText,
  dateFmt,
  dateTimeFmt,
  money,
  titleCase,
} from '../../components/ui';

export default function InvoicePage() {
  const { bookingId } = useParams();
  const { isStaff } = useAuth();
  const { currency, format } = useCurrency();
  const showLocal = !isStaff && currency !== 'USD';
  const invoice = useApi(() => paymentApi.invoice(bookingId), [bookingId]);

  if (invoice.loading) {
    return (
      <div className="page">
        <Skeleton width="30%" height={26} className="mb-3" />
        <Card className="card-pad">
          <SkeletonText lines={9} />
        </Card>
      </div>
    );
  }

  if (invoice.error) {
    return (
      <div className="page">
        <ErrorState message={invoice.error} onRetry={invoice.reload} />
      </div>
    );
  }

  const inv = invoice.data;
  const stamp = inv.fullyPaid
    ? { cls: 'stamp-paid', text: 'Paid in full' }
    : Number(inv.totalPaid) > 0
      ? { cls: 'stamp-partial', text: 'Part paid' }
      : { cls: 'stamp-due', text: inv.overdue ? 'Overdue' : 'Payment due' };

  return (
    <div className="page">
      <div className="row row-gap-2 mb-2 no-print">
        <Link to={isStaff ? '/staff/payments' : `/my-trips/${bookingId}`} className="link">
          <span className="arrow" style={{ transform: 'scaleX(-1)' }}>&rarr;</span> Back
        </Link>
        <span className="spacer" />
        <Button variant="outline" size="sm" onClick={() => window.print()}>
          Print or save as PDF
        </Button>
      </div>

      <div className="invoice">
        <div className="invoice-head">
          <div>
            <div className="row row-gap-2 mb-1" style={{ color: 'var(--canopy)' }}>
              <BrandMark />
              <div className="brand-text" style={{ color: 'var(--ink)' }}>
                <span className="brand-name">{BRAND.name}</span>
                <span className="brand-sub">Wildlife journeys across Sri Lanka</span>
              </div>
            </div>
            <div className="tiny muted" style={{ marginTop: 12 }}>
              Colombo, Sri Lanka
              <br />
              Amounts in US dollars, our booking currency
            </div>
          </div>

          <div className="right">
            <div className="invoice-title">Invoice</div>
            <div className="small mono-num">{inv.invoiceNumber}</div>
            <div className="tiny muted">Issued {dateFmt(inv.issuedAt)}</div>
            <div style={{ marginTop: 12 }}>
              <span className={`stamp ${stamp.cls}`}>{stamp.text}</span>
            </div>
          </div>
        </div>

        <div className="grid grid-2 mb-3">
          <div>
            <div className="si-label">Billed to</div>
            <div className="strong">{inv.customerName}</div>
            <div className="small muted">{inv.customerEmail}</div>
            {inv.customerPhone && <div className="small muted">{inv.customerPhone}</div>}
          </div>
          <div>
            <div className="si-label">Trip</div>
            <div className="strong">{inv.packageName}</div>
            <div className="small muted">{inv.parkName}</div>
            <div className="small muted">
              {dateFmt(inv.tripDate)} → {dateFmt(inv.tripEndDate)} · {inv.participants} traveller
              {inv.participants === 1 ? '' : 's'}
            </div>
            <div className="tiny muted mono-num mt-1">Booking {inv.bookingReference}</div>
          </div>
        </div>

        <table>
          <thead>
            <tr>
              <th>Description</th>
              <th style={{ textAlign: 'center', width: 80 }}>Qty</th>
              <th style={{ textAlign: 'right', width: 120 }}>Unit price</th>
              <th style={{ textAlign: 'right', width: 120 }}>Amount</th>
            </tr>
          </thead>
          <tbody>
            {inv.lines.map((l) => (
              <tr key={l.description}>
                <td>{l.description}</td>
                <td style={{ textAlign: 'center' }}>{l.quantity}</td>
                <td style={{ textAlign: 'right' }} className="mono-num">
                  {money(l.unitPrice)}
                </td>
                <td style={{ textAlign: 'right' }} className="mono-num">
                  {money(l.amount)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="invoice-totals">
          <div className="it-row">
            <span className="muted">Subtotal</span>
            <span className="mono-num">{money(inv.subtotal)}</span>
          </div>
          <div className="it-row">
            <span className="muted">Paid to date</span>
            <span className="mono-num" style={{ color: 'var(--success)' }}>
              −{money(inv.totalPaid)}
            </span>
          </div>
          <div className="it-row grand">
            <span>{inv.fullyPaid ? 'Balance' : 'Amount due'}</span>
            <span className="mono-num">{money(inv.balanceDue)}</span>
          </div>
          {showLocal && !inv.fullyPaid && (
            <div className="fx-note right mt-1">≈ {format(inv.balanceDue)} at today&rsquo;s rate</div>
          )}
          {inv.paymentDueDate && !inv.fullyPaid && (
            <div className="tiny mt-1" style={{ color: inv.overdue ? 'var(--danger)' : 'var(--ink-400)' }}>
              {inv.overdue ? 'Overdue since ' : 'Due by '}
              {dateFmt(inv.paymentDueDate)}
            </div>
          )}
        </div>

        {inv.payments.length > 0 && (
          <>
            <div className="section-title mt-3">Payment history</div>
            <table>
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Date</th>
                  <th>Method</th>
                  <th style={{ textAlign: 'right' }}>Amount</th>
                  <th style={{ textAlign: 'right' }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {inv.payments.map((p) => (
                  <tr key={p.id}>
                    <td className="mono-num small">{p.paymentReference}</td>
                    <td className="small">{p.paidAt ? dateTimeFmt(p.paidAt) : dateTimeFmt(p.createdAt)}</td>
                    <td className="small">
                      {titleCase(p.method)}
                      {p.cardLast4 ? ` ••${p.cardLast4}` : ''}
                    </td>
                    <td style={{ textAlign: 'right' }} className="mono-num">
                      {money(p.amount)}
                      {p.currency && p.currency !== 'USD' && (
                        <div className="tiny muted">
                          paid {formatMoney(p.chargedAmount, p.currency)}
                        </div>
                      )}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <StatusBadge value={p.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}

        {inv.refunds.length > 0 && (
          <>
            <div className="section-title mt-3">Refunds</div>
            <table>
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Policy</th>
                  <th style={{ textAlign: 'right' }}>Amount</th>
                  <th style={{ textAlign: 'right' }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {inv.refunds.map((r) => (
                  <tr key={r.id}>
                    <td className="mono-num small">{r.refundReference}</td>
                    <td className="small">{r.policyApplied}</td>
                    <td style={{ textAlign: 'right' }} className="mono-num">
                      {money(r.approvedAmount ?? r.calculatedAmount)}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <StatusBadge value={r.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}

        <div className="tiny muted mt-3" style={{ borderTop: '1px solid var(--line)', paddingTop: 14 }}>
          Cancellation policy: full refund more than 7 days before departure, 50% within 7 days, and
          no refund inside 48 hours of departure. Park permit fees are included in the price shown.
        </div>
      </div>
    </div>
  );
}
