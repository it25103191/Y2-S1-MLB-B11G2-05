import { useState } from 'react';
import { parkApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage, fieldErrors } from '../../api/client';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import {
  Button,
  ErrorState,
  Field,
  Input,
  Modal,
  SkeletonTable,
  Textarea,
  money,
} from '../../components/ui';

const BLANK = {
  name: '',
  location: '',
  description: '',
  entryFeePerPerson: '',
  permitAuthority: '',
  active: true,
};

export default function ParkManagementPage() {
  const toast = useToast();
  const parks = useApi(() => parkApi.list(false), []);

  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);

  const openNew = () => {
    setForm(BLANK);
    setErrors({});
    setEditing({ isNew: true });
  };

  const openEdit = (p) => {
    setForm({
      name: p.name,
      location: p.location,
      description: p.description ?? '',
      entryFeePerPerson: p.entryFeePerPerson,
      permitAuthority: p.permitAuthority ?? '',
      active: p.active,
    });
    setErrors({});
    setEditing(p);
  };

  const set = (k) => (e) => {
    const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((f) => ({ ...f, [k]: v }));
  };

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    const payload = { ...form, entryFeePerPerson: Number(form.entryFeePerPerson || 0) };
    try {
      if (editing.isNew) {
        await parkApi.create(payload);
        toast.success(`${payload.name} added to the directory.`, 'Park created');
      } else {
        await parkApi.update(editing.id, payload);
        toast.success(`${payload.name} updated.`, 'Saved');
      }
      setEditing(null);
      parks.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save park');
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await parkApi.remove(deleting.id);
      toast.success(`${deleting.name} removed.`, 'Park deleted');
      setDeleting(null);
      parks.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const columns = [
    {
      key: 'name',
      header: 'Park',
      render: (r) => (
        <div>
          <div className="strong">{r.name}</div>
          <div className="tiny muted truncate" style={{ maxWidth: 380 }}>
            {r.description}
          </div>
        </div>
      ),
    },
    { key: 'location', header: 'Location', width: 220 },
    { key: 'permitAuthority', header: 'Permit authority', width: 220 },
    {
      key: 'entryFeePerPerson',
      header: 'Entry fee',
      width: 110,
      align: 'right',
      sortValue: (r) => Number(r.entryFeePerPerson),
      render: (r) => <span className="mono-num">{money(r.entryFeePerPerson)}</span>,
    },
    {
      key: 'packageCount',
      header: 'Packages',
      width: 96,
      align: 'center',
      sortValue: (r) => r.packageCount,
    },
    {
      key: 'active',
      header: 'Status',
      width: 106,
      sortValue: (r) => (r.active ? 1 : 0),
      render: (r) => (
        <StatusBadge value={r.active ? 'AVAILABLE' : 'INACTIVE'} label={r.active ? 'Active' : 'Inactive'} />
      ),
    },
    {
      key: 'actions',
      header: '',
      width: 150,
      sortable: false,
      searchable: false,
      render: (r) => (
        <div className="row row-gap-1">
          <Button size="sm" variant="outline" onClick={() => openEdit(r)}>
            Edit
          </Button>
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
          <div className="eyebrow">Directory</div>
          <h1>Parks &amp; reserves</h1>
          <p className="lede">
            The reserves you operate in. Entry fees here feed permit costing, and each package must
            belong to a park.
          </p>
        </div>
        <Button onClick={openNew}>+ New park</Button>
      </div>

      {parks.loading ? (
        <SkeletonTable rows={5} cols={7} />
      ) : parks.error ? (
        <ErrorState message={parks.error} onRetry={parks.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={parks.data}
          initialSort={{ key: 'name', dir: 'asc' }}
          searchPlaceholder="Search parks or locations…"
          emptyIcon="🌍"
          emptyTitle="No parks yet"
          emptyMessage="Add a park before creating safari packages."
        />
      )}

      <Modal
        open={!!editing}
        title={editing?.isNew ? 'New park' : `Edit — ${editing?.name ?? ''}`}
        onClose={() => !busy && setEditing(null)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} loading={busy}>
              {editing?.isNew ? 'Create park' : 'Save changes'}
            </Button>
          </>
        }
      >
        <form onSubmit={save} className="form-grid" noValidate>
          <Field label="Park name" error={errors.name}>
            <Input value={form.name} onChange={set('name')} error={errors.name} required />
          </Field>

          <Field label="Location" error={errors.location}>
            <Input
              value={form.location}
              onChange={set('location')}
              placeholder="Southern Province"
              error={errors.location}
              required
            />
          </Field>

          <Field label="Description" error={errors.description} span2>
            <Textarea rows={3} value={form.description} onChange={set('description')} />
          </Field>

          <Field
            label="Entry fee per person (USD)"
            error={errors.entryFeePerPerson}
            hint="Used to cost permits."
          >
            <Input
              type="number"
              min="0"
              step="0.01"
              value={form.entryFeePerPerson}
              onChange={set('entryFeePerPerson')}
              error={errors.entryFeePerPerson}
              required
            />
          </Field>

          <Field label="Permit authority" error={errors.permitAuthority}>
            <Input
              value={form.permitAuthority}
              onChange={set('permitAuthority')}
              placeholder="Department of Wildlife Conservation"
            />
          </Field>

          <Field span2>
            <label className="checkline">
              <input type="checkbox" checked={!!form.active} onChange={set('active')} />
              <span>Active</span>
            </label>
          </Field>
        </form>
      </Modal>

      <Modal
        open={!!deleting}
        title="Delete park"
        onClose={() => !busy && setDeleting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete park
            </Button>
          </>
        }
      >
        {deleting && (
          <div className="stack">
            <p>
              Delete <strong>{deleting.name}</strong>? This cannot be undone.
            </p>
            {deleting.packageCount > 0 && (
              <div className="inline-note warn">
                <span aria-hidden="true">⚠</span>
                <span>
                  {deleting.packageCount} package(s) reference this park, so the server will refuse
                  the delete. Deactivate it instead.
                </span>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
