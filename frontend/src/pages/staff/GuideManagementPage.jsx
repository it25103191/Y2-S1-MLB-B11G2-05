import { useState } from 'react';
import { guideApi } from '../../api/api';
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
  SkeletonTable,
} from '../../components/ui';

const STATUSES = ['AVAILABLE', 'ON_LEAVE', 'INACTIVE'];

const LABEL = {
  AVAILABLE: 'Available',
  ON_LEAVE: 'On leave',
  INACTIVE: 'Inactive',
};

const BLANK = {
  fullName: '',
  email: '',
  phone: '',
  licenseNumber: '',
  languages: '',
  specialization: '',
  yearsExperience: 0,
  status: 'AVAILABLE',
};

export default function GuideManagementPage() {
  const toast = useToast();
  const guides = useApi(() => guideApi.list(), []);

  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');

  const rows = (guides.data ?? []).filter((g) => !statusFilter || g.status === statusFilter);

  const openNew = () => {
    setForm(BLANK);
    setErrors({});
    setEditing({ isNew: true });
  };

  const openEdit = (g) => {
    setForm({
      fullName: g.fullName,
      email: g.email ?? '',
      phone: g.phone ?? '',
      licenseNumber: g.licenseNumber,
      languages: g.languages ?? '',
      specialization: g.specialization ?? '',
      yearsExperience: g.yearsExperience ?? 0,
      status: g.status,
    });
    setErrors({});
    setEditing(g);
  };

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    const payload = { ...form, yearsExperience: Number(form.yearsExperience || 0) };
    try {
      if (editing.isNew) {
        await guideApi.create(payload);
        toast.success(`${payload.fullName} added to the guide roster.`, 'Guide registered');
      } else {
        await guideApi.update(editing.id, payload);
        toast.success(`${payload.fullName} updated.`, 'Saved');
      }
      setEditing(null);
      guides.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save guide');
    } finally {
      setBusy(false);
    }
  };

  const quickStatus = async (g, status) => {
    try {
      await guideApi.setStatus(g.id, status);
      toast.success(`${g.fullName} is now ${LABEL[status].toLowerCase()}.`, 'Availability updated');
      guides.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update availability');
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await guideApi.remove(deleting.id);
      toast.success(`${deleting.fullName} removed from the roster.`, 'Guide deleted');
      setDeleting(null);
      guides.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const columns = [
    {
      key: 'fullName',
      header: 'Guide',
      render: (r) => (
        <div>
          <div className="strong">{r.fullName}</div>
          <div className="tiny muted">{r.email}</div>
        </div>
      ),
    },
    {
      key: 'licenseNumber',
      header: 'Licence',
      width: 128,
      render: (r) => <span className="mono-num small">{r.licenseNumber}</span>,
    },
    { key: 'specialization', header: 'Specialisation', width: 170 },
    { key: 'languages', header: 'Languages', width: 200 },
    {
      key: 'yearsExperience',
      header: 'Years',
      width: 76,
      align: 'center',
      sortValue: (r) => r.yearsExperience,
    },
    {
      key: 'upcomingAssignments',
      header: 'Upcoming',
      width: 96,
      align: 'center',
      sortValue: (r) => r.upcomingAssignments,
      render: (r) =>
        r.upcomingAssignments > 0 ? (
          <span className="strong">{r.upcomingAssignments}</span>
        ) : (
          <span className="muted">—</span>
        ),
    },
    {
      key: 'status',
      header: 'Availability',
      width: 128,
      render: (r) => <StatusBadge value={r.status} label={LABEL[r.status]} />,
    },
    {
      key: 'actions',
      header: '',
      width: 210,
      sortable: false,
      searchable: false,
      render: (r) => (
        <div className="row row-gap-1">
          <Button size="sm" variant="outline" onClick={() => openEdit(r)}>
            Edit
          </Button>
          {r.status === 'AVAILABLE' ? (
            <Button size="sm" variant="ghost" onClick={() => quickStatus(r, 'ON_LEAVE')}>
              Set on leave
            </Button>
          ) : (
            <Button size="sm" variant="ghost" onClick={() => quickStatus(r, 'AVAILABLE')}>
              Set available
            </Button>
          )}
          <Button size="sm" variant="ghost" onClick={() => setDeleting(r)}>
            Delete
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Roster</div>
          <h1>Guides</h1>
          <p className="lede">
            Your guiding team, their licences and specialisations. Guides who are on leave or
            inactive are excluded from assignment suggestions.
          </p>
        </div>
        <Button onClick={openNew}>+ Register guide</Button>
      </div>

      <div className="grid grid-4 mb-3">
        {STATUSES.map((s) => {
          const n = (guides.data ?? []).filter((g) => g.status === s).length;
          return (
            <Card key={s} className="kpi">
              <div className="kpi-label">{LABEL[s]}</div>
              <div className="kpi-value">{n}</div>
            </Card>
          );
        })}
        <Card className="kpi kpi-amber">
          <div className="kpi-label">Avg experience</div>
          <div className="kpi-value">
            {guides.data?.length
              ? `${Math.round(
                  guides.data.reduce((s, g) => s + (g.yearsExperience || 0), 0) / guides.data.length,
                )} yrs`
              : '—'}
          </div>
        </Card>
      </div>

      {guides.loading ? (
        <SkeletonTable rows={5} cols={8} />
      ) : guides.error ? (
        <ErrorState message={guides.error} onRetry={guides.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'fullName', dir: 'asc' }}
          searchPlaceholder="Search name, licence, language…"
          emptyIcon="🧑‍🌾"
          emptyTitle="No guides registered"
          emptyMessage="Add your guiding team so trips can be crewed."
          filters={
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 170 }}
              aria-label="Filter by availability"
            >
              <option value="">All availability</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {LABEL[s]}
                </option>
              ))}
            </Select>
          }
        />
      )}

      <Modal
        open={!!editing}
        title={editing?.isNew ? 'Register guide' : `Edit — ${editing?.fullName ?? ''}`}
        onClose={() => !busy && setEditing(null)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} loading={busy}>
              {editing?.isNew ? 'Register guide' : 'Save changes'}
            </Button>
          </>
        }
      >
        <form onSubmit={save} className="form-grid" noValidate>
          <Field label="Full name" error={errors.fullName}>
            <Input value={form.fullName} onChange={set('fullName')} error={errors.fullName} required />
          </Field>

          <Field label="Licence number" error={errors.licenseNumber}>
            <Input
              value={form.licenseNumber}
              onChange={set('licenseNumber')}
              placeholder="LK-G-4471"
              error={errors.licenseNumber}
              required
            />
          </Field>

          <Field label="Email" error={errors.email}>
            <Input type="email" value={form.email} onChange={set('email')} error={errors.email} />
          </Field>

          <Field label="Phone" error={errors.phone}>
            <Input value={form.phone} onChange={set('phone')} error={errors.phone} />
          </Field>

          <Field label="Specialisation" error={errors.specialization}>
            <Input
              value={form.specialization}
              onChange={set('specialization')}
              placeholder="Leopard tracking"
            />
          </Field>

          <Field label="Years of experience" error={errors.yearsExperience}>
            <Input
              type="number"
              min={0}
              value={form.yearsExperience}
              onChange={set('yearsExperience')}
              error={errors.yearsExperience}
            />
          </Field>

          <Field label="Languages" error={errors.languages}>
            <Input
              value={form.languages}
              onChange={set('languages')}
              placeholder="English, Sinhala, Tamil"
            />
          </Field>

          <Field label="Availability">
            <Select value={form.status} onChange={set('status')}>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {LABEL[s]}
                </option>
              ))}
            </Select>
          </Field>
        </form>
      </Modal>

      <Modal
        open={!!deleting}
        title="Delete guide"
        onClose={() => !busy && setDeleting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete guide
            </Button>
          </>
        }
      >
        {deleting && (
          <div className="stack">
            <p>
              Remove <strong>{deleting.fullName}</strong> from the roster?
            </p>
            {deleting.upcomingAssignments > 0 && (
              <div className="inline-note warn">
                <span aria-hidden="true">⚠</span>
                <span>
                  This guide has {deleting.upcomingAssignments} upcoming assignment(s), so the server
                  will refuse the delete. Set them inactive instead.
                </span>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
