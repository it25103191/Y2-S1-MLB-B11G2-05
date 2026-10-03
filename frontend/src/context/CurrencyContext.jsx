import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi, currencyApi } from '../api/api';
import { useAuth } from './AuthContext';

/**
 * Display and payment currency. Prices are stored in USD; LKR is converted at the rate finance
 * maintains. Guests' choice lives on the device, signed-in customers' choice on their account.
 */
const CurrencyContext = createContext(null);

const STORAGE_KEY = 'ceylontrails.currency';
export const CURRENCIES = ['USD', 'LKR'];
export const CURRENCY_LABELS = { USD: 'US dollars', LKR: 'Sri Lankan rupees' };

function readStored() {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return CURRENCIES.includes(v) ? v : null;
  } catch {
    return null;
  }
}

function store(code) {
  try {
    localStorage.setItem(STORAGE_KEY, code);
  } catch {
    /* private mode: the choice just won't survive a reload */
  }
}

/** Visitors on Sri Lankan time start in rupees; everyone else in dollars. */
function guess() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone === 'Asia/Colombo' ? 'LKR' : 'USD';
  } catch {
    return 'USD';
  }
}

/** "$1,840.00" or "LKR 552,000". Rupees are shown without cents unless asked. */
export function formatMoney(value, currency, { decimals } = {}) {
  const n = Number(value ?? 0);
  const d = decimals ?? (currency === 'LKR' ? 0 : 2);
  const body = Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  const sign = n < 0 ? '−' : '';
  return currency === 'LKR' ? `${sign}LKR ${body}` : `${sign}$${body}`;
}

export function CurrencyProvider({ children }) {
  const { user, isCustomer, updateUser } = useAuth();
  const [rates, setRates] = useState({ USD: 1 });
  const [rateMeta, setRateMeta] = useState({});
  const [ratesReady, setRatesReady] = useState(false);
  const [local, setLocal] = useState(() => readStored() ?? guess());

  const loadRates = useCallback(async () => {
    try {
      const data = await currencyApi.rates();
      const map = { USD: 1 };
      const meta = {};
      data.rates.forEach((r) => {
        map[r.currency] = Number(r.unitsPerUsd);
        meta[r.currency] = r;
      });
      setRates(map);
      setRateMeta(meta);
    } catch {
      /* no rates: everything shows in USD and the switch offers USD only */
    } finally {
      setRatesReady(true);
    }
  }, []);

  useEffect(() => {
    loadRates();
  }, [loadRates]);

  const wanted = isCustomer ? user.preferredCurrency ?? 'USD' : local;
  const active = rates[wanted] ? wanted : 'USD';

  // Keep a customer's saved choice on this device too, so it carries over after sign-out.
  const saved = isCustomer ? user.preferredCurrency : null;
  useEffect(() => {
    if (saved) {
      store(saved);
      setLocal(saved);
    }
  }, [saved]);

  const setCurrency = useCallback(
    async (code) => {
      if (!CURRENCIES.includes(code)) return;
      store(code);
      setLocal(code);
      if (isCustomer) {
        const previous = user.preferredCurrency;
        updateUser({ preferredCurrency: code });
        try {
          await authApi.updateMe({ preferredCurrency: code });
        } catch (err) {
          updateUser({ preferredCurrency: previous });
          throw err;
        }
      }
    },
    [isCustomer, user, updateUser],
  );

  const value = useMemo(() => {
    const rate = rates[active] ?? 1;
    const convert = (usd) => Number(usd ?? 0) * rate;
    return {
      currency: active,
      rate,
      rates,
      rateMeta,
      ratesReady,
      available: CURRENCIES.filter((c) => rates[c]),
      setCurrency,
      reloadRates: loadRates,
      convert,
      /** A USD ledger amount in the visitor's currency. */
      format: (usd, opts) => formatMoney(convert(usd), active, opts),
      /** The same amount in USD, for "≈ $1,840" reference lines. */
      formatUsd: (usd, opts) => formatMoney(usd, 'USD', opts),
      /** Converts an amount typed in the visitor's currency back to USD, to the cent. */
      toUsd: (amount) => Math.round((Number(amount ?? 0) / rate) * 100) / 100,
    };
  }, [active, rates, rateMeta, ratesReady, setCurrency, loadRates]);

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency() {
  const ctx = useContext(CurrencyContext);
  if (!ctx) throw new Error('useCurrency must be used inside <CurrencyProvider>');
  return ctx;
}
