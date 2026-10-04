import { useState } from 'react';
import { staffAccountApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { ROLE_LABELS } from '../../context/AuthContext';
import { errorMessage, fieldErrors } from '../../api/client';
import { ALL_STAFF } from '../../config/nav';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import {
  Badge,
  Button,
  Card,
  ErrorState,
  Field,
  Input,
  Modal,
  Select,
  SkeletonTable,
  dateFmt,
} from '../../components/ui';

const BLANK = {
  fullName: '',
  email: '',
  phone: '',
  role: 'CUSTOMER_RELATIONS_OFFICER',
  temporaryPassword: '',
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[0-9+()\-\s]{7,40}$/;

/** The same rules the server applies, checked before the form is sent. */
function validate(form, isNew) {
  const errors = {};
  if (form.fullName.trim().length < 2) errors.fullName = 'Full name must be 2-120 characters';
  if (isNew && !EMAIL.test(form.email.trim())) errors.email = 'Enter a valid email address';
  if (form.phone.trim() && !PHONE.test(form.phone.trim())) errors.phone = 'Enter a valid phone number';
  if (isNew && form.temporaryPassword.length < 8) {
    errors.temporaryPassword = 'Temporary password must be at least 8 characters';
  }
  return errors;
}

export default function StaffAccountsPage() {
  const toast = useToast();
  const accounts = useApi(() => staffAccountApi.list(), []);

  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [deactivating, setDeactivating] = useState(null);
  const [removing, setRemoving] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');

  const all = accounts.data ?? [];
  const rows = all.filter(
    (a) => !statusFilter || (statusFilter === 'ACTIVE' ? a.active : !a.active),
  );

  const openNew = () => {
    setForm(BLANK);
    setErrors({});
    setEditing({ isNew: true });
  };

  const openEdit = (a) => {
    setForm({ fullName: a.fullName, email: a.email, phone: a.phone ?? '', role: a.role, temporaryPassword: '' });
    setErrors({});
    setEditing(a);
  };

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const save = async (e) => {
    e.preventDefault();
    const found = validate(form, editing.isNew);
    setErrors(found);
    if (Object.keys(found).length) return;

    setBusy(true);
    try {
      if (editing.isNew) {
        await staffAccountApi.create(form);
        toast.success(`${form.fullName} can now sign in as ${ROLE_LABELS[form.role]}.`, 'Staff account created');
      } else {
        await staffAccountApi.update(editing.id, { fullName: form.fullName, phone: form.phone, role: form.role });
        toast.success(`${form.fullName} updated.`, 'Saved');
      }
      setEditing(null);
      accounts.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save account');
    } finally {
      setBusy(false);
    }
  };

  const setActive = async (a, active) => {
    setBusy(true);
    try {
      await staffAccountApi.setActive(a.id, active);
      toast.success(
        active ? `${a.fullName} can sign in again.` : `${a.fullName} can no longer sign in.`,
        active ? 'Account reactivated' : 'Account deactivated',
      );
      setDeactivating(null);
      accounts.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not change the account');
    } finally {
      setBusy(false);
    }
  };

  const doRemove = async () => {
    setBusy(true);
    try {
      await staffAccountApi.remove(removing.id);
      toast.success(`${removing.fullName}'s account has been removed.`, 'Account removed');
      setRemoving(null);
      accounts.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not remove');
    } finally {
      setBusy(false);
    }
  };

  const columns = [
    {
      key: 'fullName',
      header: 'Name',
      render: (r) => (
        <div>
          <div className="strong">
            {r.fullName} {r.you && <Badge tone="info">You</Badge>}
          </div>
          <div className="tiny muted">{r.email}</div>
        </div>
      ),
    },
    { key: 'roleLabel', header: 'Role', width: 230 },
    { key: 'phone', header: 'Phone', width: 150, render: (r) => r.phone ?? <span className="muted">—</span> },
    {
      key: 'openCases',
      header: 'Open cases',
      width: 104,
      align: 'center',
      sortValue: (r) => r.openCases,
      render: (r) => (r.openCases > 0 ? <span className="strong">{r.openCases}</span> : <span className="muted">—</span>),
    },
    { key: 'createdAt', header: 'Since', width: 120, render: (r) => dateFmt(r.createdAt) },
    {
      key: 'active',
      header: 'Status',
      width: 130,
      sortValue: (r) => (r.active ? 0 : 1),
      render: (r) => (
        <StatusBadge value="account" tone={r.active ? 'success' : 'neutral'} label={r.active ? 'Active' : 'Deactivated'} />
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
          <Button size="sm" variant="outline" onClick={() => openEdit(r)}>
            Edit
          </Button>
          {r.active ? (
            <Button size="sm" variant="ghost" onClick={() => setDeactivating(r)} disabled={r.you}>
              Deactivate
            </Button>
          ) : (
            <Button size="sm" variant="ghost" onClick={() => setActive(r, true)} disabled={busy}>
              Reactivate
            </Button>
          )}
          <Button size="sm" variant="ghost" onClick={() => setRemoving(r)} disabled={r.you}>
            Remove
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Team</div>
          <h1>Staff accounts</h1>
          <p className="lede">
            Add new team members, change their role, and switch off accounts for people who have left.
            A deactivated account keeps its name on everything that person handled.
          </p>
        </div>
        <Button onClick={openNew}>+ Add staff member</Button>
      </div>

      <div className="grid grid-3 mb-3">
        <Card className="kpi">
          <div className="kpi-label">Active</div>
          <div className="kpi-value">{all.filter((a) => a.active).length}</div>
        </Card>
        <Card className="kpi">
          <div className="kpi-label">Deactivated</div>
          <div className="kpi-value">{all.filter((a) => !a.active).length}</div>
        </Card>
        <Card className="kpi kpi-amber">
          <div className="kpi-label">Open cases owned</div>
          <div className="kpi-value">{all.reduce((s, a) => s + a.openCases, 0)}</div>
        </Card>
      </div>

      {accounts.loading ? (
        <SkeletonTable rows={5} cols={6} />
      ) : accounts.error ? (
        <ErrorState message={accounts.error} onRetry={accounts.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'fullName', dir: 'asc' }}
          searchPlaceholder="Search name, email or role…"
          emptyIcon="🪪"
          emptyTitle="No staff accounts"
          emptyMessage="Add a team member to give them access to the console."
          filters={
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 170 }}
              aria-label="Filter by status"
            >
              <option value="">All accounts</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Deactivated</option>
            </Select>
          }
        />
      )}

      <Modal
        open={!!editing}
        title={editing?.isNew ? 'Add staff member' : `Edit — ${editing?.fullName ?? ''}`}
        onClose={() => !busy && setEditing(null)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} loading={busy}>
              {editing?.isNew ? 'Create account' : 'Save changes'}
            </Button>
          </>
        }
      >
        <form onSubmit={save} className="form-grid" noValidate>
          <Field label="Full name" error={errors.fullName}>
            <Input value={form.fullName} onChange={set('fullName')} placeholder="Ravi Fernando" error={errors.fullName} />
          </Field>

          <Field
            label="Email"
            error={errors.email}
            hint={editing?.isNew ? 'This is the sign-in name.' : 'The sign-in name cannot be changed.'}
          >
            <Input
              type="email"
              value={form.email}
              onChange={set('email')}
              placeholder="ravi@ceylontrails.lk"
              error={errors.email}
              disabled={!editing?.isNew}
            />
          </Field>

          <Field label="Role" error={errors.role}>
            <Select value={form.role} onChange={set('role')} disabled={editing?.you}>
              {ALL_STAFF.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABELS[r]}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Phone" error={errors.phone} hint="Optional">
            <Input value={form.phone} onChange={set('phone')} placeholder="+94 77 123 4567" error={errors.phone} />
          </Field>

          {editing?.isNew && (
            <Field
              label="Temporary password"
              error={errors.temporaryPassword}
              hint="Give it to them in person. They choose their own under My account."
              span2
            >
              <Input
                type="text"
                autoComplete="off"
                value={form.temporaryPassword}
                onChange={set('temporaryPassword')}
                error={errors.temporaryPassword}
              />
            </Field>
          )}
        </form>
      </Modal>

      <Modal
        open={!!deactivating}
        title="Deactivate account"
        onClose={() => !busy && setDeactivating(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeactivating(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={() => setActive(deactivating, false)} loading={busy}>
              Deactivate
            </Button>
          </>
        }
      >
        {deactivating && (
          <div className="stack">
            <p>
              <strong>{deactivating.fullName}</strong> will be signed out and won't be able to sign in. Their name
              stays on the bookings, payments and cases they handled, and you can reactivate the account at any time.
            </p>
            {deactivating.openCases > 0 && (
              <div className="inline-note warn">
                <span aria-hidden="true">⚠</span>
                <span>
                  They still own {deactivating.openCases} open customer case(s), so the server will refuse.
                  Reassign those cases first.
                </span>
              </div>
            )}
          </div>
        )}
      </Modal>

      <Modal
        open={!!removing}
        title="Remove account"
        onClose={() => !busy && setRemoving(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRemoving(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doRemove} loading={busy}>
              Remove permanently
            </Button>
          </>
        }
      >
        {removing && (
          <div className="stack">
            <p>
              Permanently remove <strong>{removing.fullName}</strong> ({removing.email})?
            </p>
            <div className="inline-note warn">
              <span aria-hidden="true">⚠</span>
              <span>
                Only accounts that were never used can be removed, for example one added by mistake. If they have
                worked on any booking, payment, permit or case, deactivate the account instead.
              </span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
