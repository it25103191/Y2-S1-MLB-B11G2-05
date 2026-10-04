import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../api/api';
import { errorMessage, fieldErrors } from '../../api/client';
import { ROLE_LABELS, useAuth } from '../../context/AuthContext';
import { CURRENCIES, CURRENCY_LABELS } from '../../context/CurrencyContext';
import { useToast } from '../../context/ToastContext';
import { Button, Card, CardHead, Field, Input, Modal, Select, dateFmt } from '../../components/ui';

const PHONE = /^[0-9+()\-\s]{7,40}$/;
const NO_PASSWORDS = { currentPassword: '', newPassword: '', confirm: '' };

/** Name, phone, currency (customers) and password for whoever is signed in. */
export default function AccountPage() {
  const { user, isCustomer, updateUser, logout } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  /* ------------------------------------------------------------- Details */
  const [details, setDetails] = useState({
    fullName: user.fullName,
    phone: user.phone ?? '',
    preferredCurrency: user.preferredCurrency ?? 'USD',
  });
  const [detailErrors, setDetailErrors] = useState({});
  const [savingDetails, setSavingDetails] = useState(false);

  const setDetail = (k) => (e) => setDetails((d) => ({ ...d, [k]: e.target.value }));

  const saveDetails = async (e) => {
    e.preventDefault();
    const errors = {};
    if (details.fullName.trim().length < 2) errors.fullName = 'Full name must be 2-120 characters';
    if (details.phone.trim() && !PHONE.test(details.phone.trim())) errors.phone = 'Enter a valid phone number';
    setDetailErrors(errors);
    if (Object.keys(errors).length) return;

    setSavingDetails(true);
    try {
      const payload = { fullName: details.fullName, phone: details.phone };
      if (isCustomer) payload.preferredCurrency = details.preferredCurrency;
      const saved = await authApi.updateMe(payload);
      updateUser(saved);
      toast.success('Your details have been saved.', 'Saved');
    } catch (err) {
      setDetailErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save');
    } finally {
      setSavingDetails(false);
    }
  };

  /* ------------------------------------------------------------ Password */
  const [passwords, setPasswords] = useState(NO_PASSWORDS);
  const [passwordErrors, setPasswordErrors] = useState({});
  const [savingPassword, setSavingPassword] = useState(false);

  const setPassword = (k) => (e) => setPasswords((p) => ({ ...p, [k]: e.target.value }));

  const savePassword = async (e) => {
    e.preventDefault();
    const errors = {};
    if (!passwords.currentPassword) errors.currentPassword = 'Enter your current password';
    if (passwords.newPassword.length < 8) errors.newPassword = 'Password must be at least 8 characters';
    else if (passwords.newPassword === passwords.currentPassword) {
      errors.newPassword = 'Choose a password different from your current one';
    }
    if (passwords.confirm !== passwords.newPassword) errors.confirm = 'The two passwords do not match';
    setPasswordErrors(errors);
    if (Object.keys(errors).length) return;

    setSavingPassword(true);
    try {
      await authApi.changePassword({
        currentPassword: passwords.currentPassword,
        newPassword: passwords.newPassword,
      });
      setPasswords(NO_PASSWORDS);
      toast.success('Use your new password next time you sign in.', 'Password changed');
    } catch (err) {
      setPasswordErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not change password');
    } finally {
      setSavingPassword(false);
    }
  };

  /* -------------------------------------------------------- Close account */
  const [closing, setClosing] = useState(false);
  const [closePassword, setClosePassword] = useState('');
  const [closeError, setCloseError] = useState('');
  const [busyClosing, setBusyClosing] = useState(false);

  const closeAccount = async () => {
    if (!closePassword) {
      setCloseError('Enter your password to confirm');
      return;
    }
    setBusyClosing(true);
    try {
      await authApi.closeAccount(closePassword);
      logout();
      navigate('/');
      toast.info('Your account has been closed. Thank you for travelling with us.', 'Account closed');
    } catch (err) {
      setCloseError(fieldErrors(err).password ?? '');
      toast.error(errorMessage(err), 'Could not close account');
    } finally {
      setBusyClosing(false);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">{isCustomer ? 'Your account' : ROLE_LABELS[user.role]}</div>
          <h1>My account</h1>
          <p className="lede">Keep your details up to date and choose your own password.</p>
        </div>
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', alignItems: 'start', gap: 24 }}>
        <Card>
          <CardHead title="Your details" subtitle={`Member since ${dateFmt(user.createdAt)}`} />
          <form className="card-body stack" onSubmit={saveDetails} noValidate>
            <Field label="Full name" error={detailErrors.fullName}>
              <Input value={details.fullName} onChange={setDetail('fullName')} error={detailErrors.fullName} />
            </Field>
            <Field label="Email" hint="This is how you sign in, so it can't be changed here.">
              <Input value={user.email} disabled />
            </Field>
            <Field label="Phone" error={detailErrors.phone} hint="Optional">
              <Input
                value={details.phone}
                onChange={setDetail('phone')}
                placeholder="+94 77 123 4567"
                error={detailErrors.phone}
              />
            </Field>
            {isCustomer && (
              <Field label="Show prices in">
                <Select value={details.preferredCurrency} onChange={setDetail('preferredCurrency')}>
                  {CURRENCIES.map((c) => (
                    <option key={c} value={c}>
                      {CURRENCY_LABELS[c]} ({c})
                    </option>
                  ))}
                </Select>
              </Field>
            )}
            <div>
              <Button type="submit" loading={savingDetails}>
                Save details
              </Button>
            </div>
          </form>
        </Card>

        <Card>
          <CardHead title="Change password" subtitle="At least 8 characters" />
          <form className="card-body stack" onSubmit={savePassword} noValidate>
            <Field label="Current password" error={passwordErrors.currentPassword}>
              <Input
                type="password"
                autoComplete="current-password"
                value={passwords.currentPassword}
                onChange={setPassword('currentPassword')}
                error={passwordErrors.currentPassword}
              />
            </Field>
            <Field label="New password" error={passwordErrors.newPassword}>
              <Input
                type="password"
                autoComplete="new-password"
                value={passwords.newPassword}
                onChange={setPassword('newPassword')}
                error={passwordErrors.newPassword}
              />
            </Field>
            <Field label="Confirm new password" error={passwordErrors.confirm}>
              <Input
                type="password"
                autoComplete="new-password"
                value={passwords.confirm}
                onChange={setPassword('confirm')}
                error={passwordErrors.confirm}
              />
            </Field>
            <div>
              <Button type="submit" loading={savingPassword}>
                Change password
              </Button>
            </div>
          </form>
        </Card>

        {isCustomer ? (
          <Card>
            <CardHead title="Close account" />
            <div className="card-body stack">
              <p className="small">
                Closing your account removes your name, email and phone number and deletes the e-mails we sent you.
                We keep your past bookings, payments and support cases for our accounts, but they will no longer
                say who you are.
              </p>
              <p className="small muted">
                Cancel any upcoming trips first. If a refund is on its way, wait until it has been paid.
              </p>
              <div>
                <Button variant="outline" onClick={() => { setClosePassword(''); setCloseError(''); setClosing(true); }}>
                  Close my account
                </Button>
              </div>
            </div>
          </Card>
        ) : (
          <Card>
            <CardHead title="Your role" />
            <div className="card-body stack">
              <p className="strong">{ROLE_LABELS[user.role]}</p>
              <p className="small muted">
                {user.role === 'OPERATIONS_MANAGER'
                  ? 'You manage the team on the Staff Accounts page. Your own role can only be changed by another Operations Manager.'
                  : 'Your Operations Manager manages staff accounts. Ask them if your role or access needs to change.'}
              </p>
            </div>
          </Card>
        )}
      </div>

      <Modal
        open={closing}
        title="Close your account"
        onClose={() => !busyClosing && setClosing(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setClosing(false)} disabled={busyClosing}>
              Keep my account
            </Button>
            <Button variant="danger" onClick={closeAccount} loading={busyClosing}>
              Close account
            </Button>
          </>
        }
      >
        <div className="stack">
          <p>This can't be undone. You can always create a new account later with the same email.</p>
          <Field label="Password" error={closeError}>
            <Input
              type="password"
              autoComplete="current-password"
              value={closePassword}
              onChange={(e) => setClosePassword(e.target.value)}
              error={closeError}
            />
          </Field>
        </div>
      </Modal>
    </div>
  );
}
