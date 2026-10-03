import { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useCurrency } from '../../context/CurrencyContext';
import { useToast } from '../../context/ToastContext';
import { errorMessage, fieldErrors } from '../../api/client';
import { Button, Field, Input } from '../ui';

export const DEMO_PASSWORD = 'Password123!';

/**
 * The demo-account shortcuts are for local rehearsals only and stay hidden unless
 * VITE_SHOW_DEMO_ACCOUNTS=true is set (e.g. in frontend/.env.local).
 */
export const SHOW_DEMO_ACCOUNTS = import.meta.env.VITE_SHOW_DEMO_ACCOUNTS === 'true';

export const DEMO_ACCOUNTS = [
  { role: 'Customer', email: 'sarah@example.com' },
  { role: 'Operations Manager', email: 'kamal@ceylontrails.lk' },
  { role: 'Customer Relations', email: 'sachini@ceylontrails.lk' },
  { role: 'Vehicle Coordinator', email: 'saman@ceylontrails.lk' },
  { role: 'Reservations Exec', email: 'nuwan@ceylontrails.lk' },
  { role: 'Accounts Officer', email: 'ishara@ceylontrails.lk' },
];

/** Email and password sign-in. Calls `onDone(user)` once the session is established. */
export function SignInForm({ onDone, showDemo = SHOW_DEMO_ACCOUNTS, submitLabel = 'Sign in' }) {
  const { login } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setErrors({});
    try {
      const signedIn = await login(form.email, form.password);
      toast.success(`Signed in as ${signedIn.fullName}.`, 'Welcome back');
      onDone?.(signedIn);
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err, 'Could not sign you in.'), 'Sign-in failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <form onSubmit={submit} className="stack" noValidate>
        <Field label="Email address" error={errors.email}>
          <Input
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={set('email')}
            placeholder="you@example.com"
            error={errors.email}
            required
          />
        </Field>
        <Field label="Password" error={errors.password}>
          <Input
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={set('password')}
            placeholder="••••••••"
            error={errors.password}
            required
          />
        </Field>
        <Button type="submit" className="btn-block btn-lg" loading={submitting}>
          {submitting ? 'Signing in…' : submitLabel}
        </Button>
      </form>

      {showDemo && (
        <div className="demo-accounts">
          <div className="demo-title">Demo accounts · password {DEMO_PASSWORD}</div>
          <div className="demo-grid">
            {DEMO_ACCOUNTS.map((d) => (
              <button
                key={d.email}
                type="button"
                className="demo-chip"
                onClick={() => {
                  setForm({ email: d.email, password: DEMO_PASSWORD });
                  setErrors({});
                }}
              >
                <span className="demo-role">{d.role}</span>
                <span className="demo-mail">{d.email}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </>
  );
}

/** Where the traveller lives decides the currency they see and pay in. */
export function ResidenceChoice({ value, onChange }) {
  return (
    <div className="segmented" role="radiogroup" aria-label="Where do you live?">
      <button type="button" role="radio" aria-checked={value === 'LKR'} className={value === 'LKR' ? 'on' : ''} onClick={() => onChange('LKR')}>
        <b>Sri Lanka</b>
        <span>Prices in rupees (LKR)</span>
      </button>
      <button type="button" role="radio" aria-checked={value === 'USD'} className={value === 'USD' ? 'on' : ''} onClick={() => onChange('USD')}>
        <b>Elsewhere</b>
        <span>Prices in US dollars</span>
      </button>
    </div>
  );
}

/** New customer account. Calls `onDone(user)` once registered and signed in. */
export function RegisterForm({ onDone, submitLabel = 'Create account' }) {
  const { register } = useAuth();
  const { currency, available } = useCurrency();
  const toast = useToast();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '', phone: '' });
  const [residence, setResidence] = useState(currency);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    if (form.password !== form.confirm) {
      setErrors({ confirm: 'Passwords do not match' });
      return;
    }
    setSubmitting(true);
    setErrors({});
    try {
      const created = await register({
        fullName: form.fullName,
        email: form.email,
        password: form.password,
        phone: form.phone,
        preferredCurrency: residence,
      });
      toast.success(`Account created for ${created.email}.`, 'Welcome to the island');
      onDone?.(created);
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err, 'Could not create your account.'), 'Registration failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} className="stack" noValidate>
      <Field label="Full name" error={errors.fullName}>
        <Input value={form.fullName} onChange={set('fullName')} placeholder="Your name" autoComplete="name" error={errors.fullName} required />
      </Field>
      <Field label="Email address" error={errors.email}>
        <Input type="email" value={form.email} onChange={set('email')} placeholder="you@example.com" autoComplete="email" error={errors.email} required />
      </Field>
      {available.includes('LKR') && (
        <Field label="Where do you live?" hint="You can switch currency any time from the top of the page.">
          <ResidenceChoice value={residence} onChange={setResidence} />
        </Field>
      )}
      <Field label="Phone (optional)" error={errors.phone} hint="For trip-day updates from your tracker.">
        <Input value={form.phone} onChange={set('phone')} placeholder="+94 77 123 4567" autoComplete="tel" error={errors.phone} />
      </Field>
      <div className="form-grid">
        <Field label="Password" error={errors.password} hint="At least 8 characters.">
          <Input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" error={errors.password} required />
        </Field>
        <Field label="Confirm password" error={errors.confirm}>
          <Input type="password" value={form.confirm} onChange={set('confirm')} autoComplete="new-password" error={errors.confirm} required />
        </Field>
      </div>
      <Button type="submit" className="btn-block btn-lg" loading={submitting}>
        {submitting ? 'Creating account…' : submitLabel}
      </Button>
    </form>
  );
}
