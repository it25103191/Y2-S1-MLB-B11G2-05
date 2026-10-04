import { useCurrency, CURRENCY_LABELS } from '../context/CurrencyContext';
import { useToast } from '../context/ToastContext';
import { errorMessage } from '../api/client';

/** USD / LKR segmented control. Hidden entirely until an LKR rate is available. */
export default function CurrencySwitch({ className = '' }) {
  const { currency, available, setCurrency, rates } = useCurrency();
  const toast = useToast();

  if (available.length < 2) return null;

  const choose = async (code) => {
    if (code === currency) return;
    try {
      await setCurrency(code);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not save your currency.'), 'Currency not changed');
    }
  };

  const rateLine = `1 USD = ${Number(rates.LKR).toLocaleString('en-US', { maximumFractionDigits: 2 })} LKR`;

  return (
    <div className={`currency-switch ${className}`.trim()} role="group" aria-label={`Show prices in (${rateLine})`} title={rateLine}>
      {available.map((code) => (
        <button
          key={code}
          type="button"
          className={code === currency ? 'on' : ''}
          aria-pressed={code === currency}
          aria-label={CURRENCY_LABELS[code]}
          onClick={() => choose(code)}
        >
          {code}
        </button>
      ))}
    </div>
  );
}
