import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { bookingApi, currencyApi, paymentApi } from '../../api/api';
import { formatMoney, useCurrency } from '../../context/CurrencyContext';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage, fieldErrors } from '../../api/client';
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
  Skeleton,
  SkeletonTable,
  Textarea,
  dateFmt,
  dateTimeFmt,
  money,
  titleCase,
} from '../../components/ui';

const STATUSES = ['SUCCESS', 'DECLINED', 'TIMEOUT', 'REFUNDED', 'VOIDED'];

const OFFLINE_METHODS = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'MOBILE_MONEY', label: 'Mobile money' },
];

const BLANK_OFFLINE = { bookingId: '', amount: '', currency: 'USD', method: 'BANK_TRANSFER', reference: '', notes: '' };

/** USD ledger amount, plus what was actually charged when that was another currency. */
function AmountCell({ p }) {
  return (
    <>
      <span className="mono-num strong">{money(p.amount)}</span>
      {p.currency && p.currency !== 'USD' && (
        <div className="tiny muted mono-num">{formatMoney(p.chargedAmount, p.currency)}</div>
      )}
    </>
  );
}

export default function PaymentsPage() {
  const navigate = useNavigate();
  const payments = useApi(() => paymentApi.list(), []);
  const summary = useApi(() => paymentApi.summary(), []);

  const [status, setStatus] = useState('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [detail, setDetail] = useState(null);
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const bookings = useApi(() => bookingApi.list(), []);
  const [offlineOpen, setOfflineOpen] = useState(false);
  const [offline, setOffline] = useState(BLANK_OFFLINE);
  const [offlineErrors, setOfflineErrors] = useState({});
  const [voiding, setVoiding] = useState(null);
  const [voidReason, setVoidReason] = useState('');
  const [deleting, setDeleting] = useState(null);
  const { rates, rateMeta, reloadRates } = useCurrency();
  const [rateOpen, setRateOpen] = useState(false);
  const [rateDraft, setRateDraft] = useState('');
  const [rateError, setRateError] = useState('');
  const lkr = rates.LKR;

  // Bookings that can still take money.
  const chargeable = (bookings.data ?? []).filter((b) => b.status !== 'CANCELLED' && Number(b.balanceDue) > 0);
  const chosenBooking = chargeable.find((b) => String(b.id) === String(offline.bookingId));

  const refreshAll = () => {
    payments.reload();
    summary.reload();
    bookings.reload();
  };

  const openOffline = () => {
    setOffline(BLANK_OFFLINE);
    setOfflineErrors({});
    setOfflineOpen(true);
  };

  /** The outstanding balance in the currency the money arrived in. */
  const balanceIn = (b, cur) =>
    cur === 'LKR' ? String(Math.round(Number(b.balanceDue) * lkr)) : Number(b.balanceDue).toFixed(2);

  const chooseBooking = (id) => {
    const b = chargeable.find((x) => String(x.id) === String(id));
    setOffline((o) => ({ ...o, bookingId: id, amount: b ? balanceIn(b, o.currency) : '' }));
  };

  const chooseCurrency = (cur) => {
    setOffline((o) => ({ ...o, currency: cur, amount: chosenBooking ? balanceIn(chosenBooking, cur) : o.amount }));
  };

  // LKR received is converted to the USD ledger, capped at the balance to absorb rounding.
  const offlineUsd = () => {
    if (offline.amount === '') return null;
    const entered = Number(offline.amount);
    if (offline.currency !== 'LKR') return entered;
    const usd = Math.round((entered / lkr) * 100) / 100;
    return chosenBooking ? Math.min(usd, Number(chosenBooking.balanceDue)) : usd;
  };

  const openRate = () => {
    setRateDraft(lkr ? String(lkr) : '');
    setRateError('');
    setRateOpen(true);
  };

  const saveRate = async () => {
    setBusy(true);
    setRateError('');
    try {
      const saved = await currencyApi.update('LKR', Number(rateDraft));
      toast.success(`1 USD = ${Number(saved.unitsPerUsd).toLocaleString('en-US')} LKR from now on.`, 'Exchange rate updated');
      setRateOpen(false);
      reloadRates();
    } catch (err) {
      setRateError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const submitOffline = async () => {
    setBusy(true);
    setOfflineErrors({});
    try {
      const result = await paymentApi.recordOffline({
        bookingId: offline.bookingId ? Number(offline.bookingId) : null,
        amount: offlineUsd(),
        currency: offline.currency,
        chargedAmount: offline.amount === '' || offline.currency === 'USD' ? null : Number(offline.amount),
        method: offline.method,
        reference: offline.reference || null,
        notes: offline.notes || null,
      });
      toast.success(result.message, result.bookingNowConfirmed ? 'Booking confirmed' : 'Payment recorded');
      setOfflineOpen(false);
      refreshAll();
    } catch (err) {
      setOfflineErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not record payment');
    } finally {
      setBusy(false);
    }
  };

  const doVoid = async () => {
    setBusy(true);
    try {
      await paymentApi.voidPayment(voiding.id, voidReason.trim());
      toast.success(
        `${voiding.paymentReference} voided and ${money(voiding.amount)} taken off ${voiding.bookingReference}.`,
        'Payment voided',
      );
      setVoiding(null);
      setDetail(null);
      refreshAll();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not void payment');
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await paymentApi.remove(deleting.id);
      toast.success(`Failed attempt ${deleting.paymentReference} deleted.`, 'Deleted');
      setDeleting(null);
      setDetail(null);
      refreshAll();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const all = payments.data ?? [];
  const rows = all.filter((p) => (!status || p.status === status) && (!overdueOnly || p.overdue));

  const columns = [
    {
      key: 'paymentReference',
      header: 'Payment',
      width: 140,
      render: (r) => (
        <div>
          <div className="mono-num small strong">{r.paymentReference}</div>
          <div className="tiny muted">{dateFmt(r.paidAt ?? r.createdAt)}</div>
        </div>
      ),
    },
    {
      key: 'customerName',
      header: 'Customer',
      render: (r) => (
        <div>
          <div className="strong">{r.customerName}</div>
          <div className="tiny muted truncate" style={{ maxWidth: 260 }}>
            {r.packageName} · <span className="mono-num">{r.bookingReference}</span>
          </div>
        </div>
      ),
    },
    {
      key: 'amount',
      header: 'Amount',
      width: 116,
      align: 'right',
      sortValue: (r) => Number(r.amount),
      render: (r) => <AmountCell p={r} />,
    },
    {
      key: 'method',
      header: 'Method',
      width: 138,
      render: (r) => (
        <span className="small">
          {titleCase(r.method)}
          {r.cardLast4 ? ` ••${r.cardLast4}` : ''}
        </span>
      ),
    },
    {
      key: 'bookingBalance',
      header: 'Booking balance',
      width: 140,
      align: 'right',
      sortValue: (r) => Number(r.bookingBalance),
      render: (r) =>
        Number(r.bookingBalance) <= 0 ? (
          <span className="tiny" style={{ color: 'var(--success)' }}>
            Settled
          </span>
        ) : (
          <div>
            <div className="mono-num" style={{ color: r.overdue ? 'var(--danger)' : undefined }}>
              {money(r.bookingBalance)}
            </div>
            {r.paymentDueDate && (
              <div className="tiny muted">due {dateFmt(r.paymentDueDate)}</div>
            )}
          </div>
        ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 140,
      render: (r) => (
        <div className="col" style={{ gap: 4 }}>
          <StatusBadge value={r.status} />
          {r.overdue && <StatusBadge value="OVERDUE" tone="danger" label="Overdue" />}
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
        <div className="row row-gap-1">
          <Button size="sm" variant="outline" onClick={() => setDetail(r)}>
            Details
          </Button>
          <Button size="sm" variant="ghost" onClick={() => navigate(`/staff/invoices/${r.bookingId}`)}>
            Invoice
          </Button>
          {r.status === 'SUCCESS' && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                setVoidReason('');
                setVoiding(r);
              }}
            >
              Void
            </Button>
          )}
          {(r.status === 'DECLINED' || r.status === 'TIMEOUT') && (
            <Button size="sm" variant="ghost" onClick={() => setDeleting(r)}>
              Delete
            </Button>
          )}
        </div>
      ),
    },
  ];

  const s = summary.data;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Finance</div>
          <h1>Payments</h1>
          <p className="lede">
            Every transaction against the reservation book, including declined and timed-out
            attempts. Overdue balances are highlighted.
          </p>
        </div>
        <div className="row row-gap-2">
          {lkr && (
            <Button variant="outline" onClick={openRate}>
              1 USD = {lkr.toLocaleString('en-US')} LKR · Edit
            </Button>
          )}
          <Button onClick={openOffline}>+ Record offline payment</Button>
        </div>
      </div>

      <div className="grid grid-4 mb-3">
        {summary.loading || !s ? (
          [0, 1, 2, 3].map((i) => (
            <Card key={i} className="kpi">
              <Skeleton width="60%" height={10} />
              <Skeleton width="45%" height={24} style={{ marginTop: 8 }} />
            </Card>
          ))
        ) : (
          <>
            <Card className="kpi">
              <span className="kpi-icon" aria-hidden="true">
                💰
              </span>
              <div className="kpi-label">Collected</div>
              <div className="kpi-value">{money(s.collectedAllTime)}</div>
              <div className="kpi-sub">{money(s.collectedThisMonth)} this month</div>
            </Card>
            <Card className="kpi kpi-terracotta">
              <span className="kpi-icon" aria-hidden="true">
                ⏳
              </span>
              <div className="kpi-label">Outstanding</div>
              <div className="kpi-value">{money(s.outstanding)}</div>
              <div className="kpi-sub">Across active bookings</div>
            </Card>
            <Card className={`kpi ${s.overdueBookings > 0 ? 'kpi-danger' : ''}`}>
              <span className="kpi-icon" aria-hidden="true">
                🚨
              </span>
              <div className="kpi-label">Overdue</div>
              <div className="kpi-value">{money(s.overdueAmount)}</div>
              <div className="kpi-sub">{s.overdueBookings} booking(s) past due</div>
            </Card>
            <Card className="kpi kpi-amber">
              <span className="kpi-icon" aria-hidden="true">
                📊
              </span>
              <div className="kpi-label">Gateway outcomes</div>
              <div className="kpi-value">
                {s.successfulPayments}
                <span style={{ fontSize: '1rem', color: 'var(--ink-400)' }}> / {s.failedPayments}</span>
              </div>
              <div className="kpi-sub">successful / failed</div>
            </Card>
          </>
        )}
      </div>

      {payments.loading ? (
        <SkeletonTable rows={8} cols={7} />
      ) : payments.error ? (
        <ErrorState message={payments.error} onRetry={payments.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'paymentReference', dir: 'desc' }}
          searchPlaceholder="Search reference, customer, booking…"
          emptyIcon="💳"
          emptyTitle="No payments recorded"
          emptyMessage="Payments taken through the booking flow will appear here."
          rowClassName={(r) => (r.overdue ? 'row-alert' : '')}
          filters={
            <>
              <Select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                style={{ width: 160 }}
                aria-label="Filter by status"
              >
                <option value="">All statuses</option>
                {STATUSES.map((x) => (
                  <option key={x} value={x}>
                    {titleCase(x)}
                  </option>
                ))}
              </Select>
              <button
                type="button"
                className={`chip${overdueOnly ? ' on' : ''}`}
                onClick={() => setOverdueOnly((v) => !v)}
              >
                Overdue only
              </button>
            </>
          }
        />
      )}

      <Modal
        open={!!detail}
        title={detail ? `Payment ${detail.paymentReference}` : ''}
        onClose={() => setDetail(null)}
        footer={
          detail && (
            <>
              <Button variant="ghost" onClick={() => setDetail(null)}>
                Close
              </Button>
              <Button onClick={() => navigate(`/staff/invoices/${detail.bookingId}`)}>Open invoice</Button>
            </>
          )
        }
      >
        {detail && (
          <div className="stack">
            <div className="row row-gap-2 wrap">
              <StatusBadge value={detail.status} />
              {detail.overdue && <StatusBadge value="OVERDUE" tone="danger" label="Booking overdue" />}
            </div>

            <dl className="dl">
              <dt>Amount</dt>
              <dd className="mono-num strong">{money(detail.amount)}</dd>
              {detail.currency && detail.currency !== 'USD' && (
                <>
                  <dt>Charged</dt>
                  <dd className="mono-num">
                    {formatMoney(detail.chargedAmount, detail.currency)} at 1 USD ={' '}
                    {Number(detail.fxRate).toLocaleString('en-US')} {detail.currency}
                  </dd>
                </>
              )}
              <dt>Method</dt>
              <dd>
                {titleCase(detail.method)}
                {detail.cardLast4 ? ` ending ${detail.cardLast4}` : ''}
              </dd>
              {detail.cardHolderName && (
                <>
                  <dt>Card holder</dt>
                  <dd>{detail.cardHolderName}</dd>
                </>
              )}
              <dt>Gateway ref</dt>
              <dd className="mono-num small">{detail.gatewayReference ?? '—'}</dd>
              <dt>Taken</dt>
              <dd>{detail.paidAt ? dateTimeFmt(detail.paidAt) : '—'}</dd>
              <dt>Recorded</dt>
              <dd>{dateTimeFmt(detail.createdAt)}</dd>
              {detail.processedByName && (
                <>
                  <dt>Processed by</dt>
                  <dd>{detail.processedByName}</dd>
                </>
              )}
            </dl>

            {detail.failureReason && (
              <div className="inline-note danger">
                <span aria-hidden="true">✕</span>
                <span>{detail.failureReason}</span>
              </div>
            )}

            {detail.status === 'VOIDED' && (
              <div className="inline-note warn">
                <span aria-hidden="true">⊘</span>
                <div>
                  <strong>Voided {dateTimeFmt(detail.voidedAt)}</strong>
                  {detail.voidedByName && <span> by {detail.voidedByName}</span>}
                  <div className="tiny mt-1">{detail.voidReason}</div>
                </div>
              </div>
            )}

            {detail.notes && (
              <div>
                <div className="section-title">Notes</div>
                <p className="small">{detail.notes}</p>
              </div>
            )}

            <Card className="card-pad-sm">
              <div className="section-title">Booking</div>
              <dl className="dl">
                <dt>Reference</dt>
                <dd className="mono-num">{detail.bookingReference}</dd>
                <dt>Customer</dt>
                <dd>
                  {detail.customerName}
                  <div className="tiny muted">{detail.customerEmail}</div>
                </dd>
                <dt>Trip</dt>
                <dd>
                  {detail.packageName} · {dateFmt(detail.tripDate)}
                </dd>
                <dt>Total / paid</dt>
                <dd className="mono-num">
                  {money(detail.bookingTotal)} / {money(detail.bookingPaid)}
                </dd>
                <dt>Balance</dt>
                <dd className="mono-num">{money(detail.bookingBalance)}</dd>
              </dl>
            </Card>
          </div>
        )}
      </Modal>

      {/* ------------------------------------------------ Offline payment */}
      <Modal
        open={offlineOpen}
        title="Record an offline payment"
        onClose={() => !busy && setOfflineOpen(false)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setOfflineOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={submitOffline} loading={busy} disabled={!offline.bookingId}>
              Record {offline.amount ? formatMoney(Number(offline.amount), offline.currency) : 'payment'}
            </Button>
          </>
        }
      >
        <div className="stack">
          <div className="inline-note">
            <span aria-hidden="true">ℹ</span>
            <span>
              For money received outside the card gateway: cash at the lodge, a bank transfer or a
              mobile money payment. It is recorded as settled straight away.
            </span>
          </div>

          <Field label="Booking" error={offlineErrors.bookingId}>
            <Select value={offline.bookingId} onChange={(e) => chooseBooking(e.target.value)} disabled={bookings.loading}>
              <option value="">{bookings.loading ? 'Loading bookings…' : 'Choose a booking with a balance'}</option>
              {chargeable.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.bookingReference} — {b.customerName} — {b.packageName} (owes {money(b.balanceDue)})
                </option>
              ))}
            </Select>
          </Field>

          {chosenBooking && (
            <div className="price-lines" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
              <div className="price-line">
                <span className="muted">Booking total</span>
                <span>{money(chosenBooking.totalPrice)}</span>
              </div>
              <div className="price-line">
                <span className="muted">Already paid</span>
                <span>{money(chosenBooking.amountPaid)}</span>
              </div>
              <div className="price-line total">
                <span>Outstanding</span>
                <span>{money(chosenBooking.balanceDue)}</span>
              </div>
            </div>
          )}

          <div className="form-grid">
            <Field
              label={`Amount received (${offline.currency})`}
              error={offlineErrors.amount}
              hint={offline.currency === 'LKR' && offline.amount ? `Recorded as ${money(offlineUsd())} at 1 USD = ${lkr} LKR` : undefined}
            >
              <Input
                type="number"
                step={offline.currency === 'LKR' ? '1' : '0.01'}
                min="0"
                value={offline.amount}
                onChange={(e) => setOffline((o) => ({ ...o, amount: e.target.value }))}
                error={offlineErrors.amount}
              />
            </Field>
            {lkr && (
              <Field label="Currency">
                <Select value={offline.currency} onChange={(e) => chooseCurrency(e.target.value)}>
                  <option value="USD">US dollars</option>
                  <option value="LKR">Sri Lankan rupees</option>
                </Select>
              </Field>
            )}
            <Field label="Received by" error={offlineErrors.method}>
              <Select value={offline.method} onChange={(e) => setOffline((o) => ({ ...o, method: e.target.value }))}>
                {OFFLINE_METHODS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </Select>
            </Field>
          </div>

          <Field label="Reference (optional)" hint="Bank slip, receipt or mobile money transaction number." error={offlineErrors.reference}>
            <Input
              value={offline.reference}
              onChange={(e) => setOffline((o) => ({ ...o, reference: e.target.value }))}
              placeholder="BANK-7781"
            />
          </Field>

          <Field label="Notes (optional)" error={offlineErrors.notes}>
            <Textarea
              rows={2}
              value={offline.notes}
              onChange={(e) => setOffline((o) => ({ ...o, notes: e.target.value }))}
              placeholder="Where and how it was received"
            />
          </Field>
        </div>
      </Modal>

      {/* ------------------------------------------------------------ Void */}
      <Modal
        open={!!voiding}
        title={voiding ? `Void ${voiding.paymentReference}` : ''}
        onClose={() => !busy && setVoiding(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setVoiding(null)} disabled={busy}>
              Keep payment
            </Button>
            <Button variant="danger" onClick={doVoid} loading={busy} disabled={!voidReason.trim()}>
              Void payment
            </Button>
          </>
        }
      >
        {voiding && (
          <div className="stack">
            <p>
              Void the <strong>{money(voiding.amount)}</strong> payment on {voiding.bookingReference} for{' '}
              {voiding.customerName}?
            </p>
            <div className="inline-note warn">
              <span aria-hidden="true">⚠</span>
              <span>
                The record is kept and marked Voided. The money comes off the booking, its balance
                reopens, and a confirmed booking goes back to pending until it is paid again.
              </span>
            </div>
            <Field label="Reason (required)">
              <Textarea
                rows={3}
                value={voidReason}
                onChange={(e) => setVoidReason(e.target.value)}
                placeholder="e.g. duplicate charge, recorded against the wrong booking"
              />
            </Field>
          </div>
        )}
      </Modal>

      {/* ---------------------------------------------------------- Delete */}
      <Modal
        open={!!deleting}
        title="Delete failed attempt"
        onClose={() => !busy && setDeleting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete attempt
            </Button>
          </>
        }
      >
        {deleting && (
          <p>
            Delete the {titleCase(deleting.status).toLowerCase()} attempt <strong>{deleting.paymentReference}</strong>{' '}
            ({money(deleting.amount)})? It moved no money, so removing it does not change any balance.
          </p>
        )}
      </Modal>

      <Modal
        open={rateOpen}
        title="Exchange rate"
        onClose={() => !busy && setRateOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRateOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={saveRate} loading={busy} disabled={!rateDraft}>
              Save rate
            </Button>
          </>
        }
      >
        <div className="stack">
          <p className="small muted">
            Customers who choose rupees see prices and pay at this rate. Bookings stay in US dollars, and every
            payment keeps the rate it was taken at.
          </p>
          <Field label="Rupees per US dollar" error={rateError}>
            <Input type="number" step="0.01" min="0" value={rateDraft} onChange={(e) => setRateDraft(e.target.value)} error={rateError} />
          </Field>
          {rateMeta.LKR?.updatedAt && (
            <div className="tiny muted">
              Last changed {dateTimeFmt(rateMeta.LKR.updatedAt)}
              {rateMeta.LKR.updatedByName ? ` by ${rateMeta.LKR.updatedByName}` : ' (starting rate)'}.
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}
