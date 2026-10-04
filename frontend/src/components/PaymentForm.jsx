import { useState } from 'react';
import { paymentApi } from '../api/api';
import { useToast } from '../context/ToastContext';
import { formatMoney, useCurrency } from '../context/CurrencyContext';
import { errorMessage, fieldErrors } from '../api/client';
import { Button, Field, Input, Select } from './ui';

/** Online payments are card only; cash, transfers and mobile money are recorded by finance staff. */
const METHODS = [
  { value: 'CREDIT_CARD', label: 'Credit card' },
  { value: 'DEBIT_CARD', label: 'Debit card' },
];

const SHARES = [
  { key: 'full', label: 'Pay in full', share: 1 },
  { key: 'half', label: 'Pay half', share: 0.5 },
  { key: 'deposit', label: '25% deposit', share: 0.25 },
];

const groupCard = (value) =>
  value
    .replace(/\D/g, '')
    .slice(0, 16)
    .replace(/(.{4})/g, '$1 ')
    .trim();

/**
 * Card-style mock payment form, in the customer's currency. The ledger is USD, so the form
 * sends the USD amount plus the amount the customer saw; the server checks the two agree at
 * today's rate. The simulated gateway always accepts cards ending 4242, declines 0002 and
 * times out on 0003, so every path can be demonstrated.
 */
export default function PaymentForm({ booking, onResult, onCancel }) {
  const toast = useToast();
  const { currency, convert, toUsd, format, formatUsd, rate } = useCurrency();
  const balance = Number(booking.balanceDue ?? 0);
  const decimals = currency === 'LKR' ? 0 : 2;
  const round = (v) => Number(Number(v).toFixed(decimals));
  const shown = (usd) => round(convert(usd));

  const [share, setShare] = useState('full');
  const [amount, setAmount] = useState(String(shown(balance)));
  const [method, setMethod] = useState('CREDIT_CARD');
  const [cardNumber, setCardNumber] = useState('4242 4242 4242 4242');
  const [holder, setHolder] = useState(booking.customerName ?? '');
  const [expiry, setExpiry] = useState('11/29');
  const [cvc, setCvc] = useState('123');
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);

  const typed = Number(amount || 0);
  // A preset share is exact in USD; a typed figure is converted back and capped at the balance.
  const preset = SHARES.find((s) => s.key === share);
  const usd = preset ? Math.round(balance * preset.share * 100) / 100 : Math.min(balance, toUsd(typed));
  const charged = preset ? shown(usd) : typed;
  const isPartial = usd > 0 && usd < balance - 0.004;

  const pick = (s) => {
    setShare(s.key);
    setAmount(String(shown(Math.round(balance * s.share * 100) / 100)));
    setErrors({});
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!(typed > 0)) {
      setErrors({ amount: 'Enter an amount greater than zero' });
      return;
    }
    if (!preset && toUsd(typed) > balance + 0.005) {
      setErrors({ amount: `Cannot exceed the outstanding balance of ${format(balance)}` });
      return;
    }

    setBusy(true);
    setErrors({});
    try {
      const result = await paymentApi.pay({
        bookingId: booking.id,
        amount: usd,
        currency,
        chargedAmount: charged,
        method,
        cardNumber,
        cardHolderName: holder,
        cardExpiry: expiry,
        cardCvc: cvc,
      });

      if (result.accepted) {
        toast.success(result.message, result.bookingNowConfirmed ? 'Trip confirmed' : 'Payment received');
      } else if (result.status === 'DECLINED') {
        toast.error(result.message, 'Payment declined');
      } else {
        toast.warning(result.message, 'Payment timed out');
      }
      onResult?.(result);
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Payment could not be processed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="stack" noValidate>
      <div className="pay-card">
        <div className="pc-chip" />
        <div className="pc-number">{cardNumber || '•••• •••• •••• ••••'}</div>
        <div className="pc-row">
          <div>
            <div className="pc-label">Card holder</div>
            <div className="pc-value">{holder || 'YOUR NAME'}</div>
          </div>
          <div>
            <div className="pc-label">Expires</div>
            <div className="pc-value">{expiry || 'MM/YY'}</div>
          </div>
          <div>
            <div className="pc-label">Charged in</div>
            <div className="pc-value">{currency}</div>
          </div>
        </div>
      </div>

      <div className="chip-row">
        {SHARES.map((s) => (
          <button key={s.key} type="button" className={`chip${share === s.key ? ' on' : ''}`} onClick={() => pick(s)}>
            {s.label}
          </button>
        ))}
      </div>

      <div className="form-grid">
        <Field
          label={`Amount (${currency})`}
          error={errors.amount}
          hint={currency === 'USD' ? 'Pay in full or leave a deposit.' : `≈ ${formatUsd(usd)} at 1 USD = ${rate.toLocaleString('en-US')} LKR`}
        >
          <Input
            type="number"
            step={currency === 'LKR' ? '1' : '0.01'}
            min="0"
            value={amount}
            onChange={(e) => {
              setShare(null);
              setAmount(e.target.value);
            }}
            error={errors.amount}
            required
          />
        </Field>
        <Field label="Card type">
          <Select value={method} onChange={(e) => setMethod(e.target.value)}>
            {METHODS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {isPartial && (
        <div className="inline-note">
          <span>
            A part payment. {format(balance - usd)} will remain, and the trip stays pending until it is settled.
          </span>
        </div>
      )}

      <Field label="Card number" error={errors.cardNumber}>
        <Input
          value={cardNumber}
          onChange={(e) => setCardNumber(groupCard(e.target.value))}
          placeholder="4242 4242 4242 4242"
          inputMode="numeric"
          error={errors.cardNumber}
        />
      </Field>
      <Field label="Name on card" error={errors.cardHolderName}>
        <Input value={holder} onChange={(e) => setHolder(e.target.value)} />
      </Field>
      <div className="form-grid">
        <Field label="Expiry (MM/YY)" error={errors.cardExpiry}>
          <Input value={expiry} onChange={(e) => setExpiry(e.target.value)} placeholder="11/29" error={errors.cardExpiry} />
        </Field>
        <Field label="CVC" error={errors.cardCvc}>
          <Input value={cvc} onChange={(e) => setCvc(e.target.value.replace(/\D/g, '').slice(0, 4))} placeholder="123" error={errors.cardCvc} />
        </Field>
      </div>

      <div className="inline-note">
        <span>
          Demonstration gateway: cards ending <strong>4242</strong> succeed, <strong>0002</strong> decline and{' '}
          <strong>0003</strong> time out. Paying cash or by bank transfer, in rupees or dollars? Our team records it
          for you.
        </span>
      </div>

      <div className="row row-gap-2">
        {onCancel && (
          <Button variant="ghost" onClick={onCancel} disabled={busy} type="button">
            Not now
          </Button>
        )}
        <span className="spacer" />
        <Button type="submit" loading={busy} className="btn-lg">
          {busy ? 'Contacting the bank…' : `Pay ${formatMoney(charged, currency, { decimals })}`}
        </Button>
      </div>
    </form>
  );
}
