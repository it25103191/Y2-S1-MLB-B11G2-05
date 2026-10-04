import { useState } from 'react';
import { packageApi, parkApi } from '../../api/api';
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
  Textarea,
  money,
} from '../../components/ui';

const BLANK = {
  name: '',
  description: '',
  parkId: '',
  durationDays: 3,
  pricePerPerson: '',
  maxGroupSize: 10,
  imageUrl: '',
  highlights: '',
  active: true,
};

export default function PackageManagementPage() {
  const toast = useToast();
  const packages = useApi(() => packageApi.list({ activeOnly: false }), []);
  const parks = useApi(() => parkApi.list(false), []);

  const [editing, setEditing] = useState(null); // null | {} (new) | package (edit)
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);

  const openNew = () => {
    setForm({ ...BLANK, parkId: parks.data?.[0]?.id ?? '' });
    setErrors({});
    setEditing({ isNew: true });
  };

  const openEdit = (p) => {
    setForm({
      name: p.name,
      description: p.description ?? '',
      parkId: p.parkId,
      durationDays: p.durationDays,
      pricePerPerson: p.pricePerPerson,
      maxGroupSize: p.maxGroupSize,
      imageUrl: p.imageUrl ?? '',
      highlights: p.highlights ?? '',
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
    const payload = {
      ...form,
      parkId: Number(form.parkId),
      durationDays: Number(form.durationDays),
      pricePerPerson: Number(form.pricePerPerson),
      maxGroupSize: Number(form.maxGroupSize),
    };
    try {
      if (editing.isNew) {
        await packageApi.create(payload);
        toast.success(`"${payload.name}" is now in the catalogue.`, 'Package created');
      } else {
        await packageApi.update(editing.id, payload);
        toast.success(`"${payload.name}" updated.`, 'Saved');
      }
      setEditing(null);
      packages.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save package');
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await packageApi.remove(deleting.id);
      toast.success(`"${deleting.name}" removed from the catalogue.`, 'Package deleted');
      setDeleting(null);
      packages.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (p) => {
    try {
      await packageApi.setActive(p.id, !p.active);
      toast.success(
        `"${p.name}" ${p.active ? 'deactivated — it will no longer accept bookings' : 'reactivated'}.`,
        'Updated',
      );
      packages.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update');
    }
  };

  const columns = [
    {
      key: 'name',
      header: 'Package',
      render: (r) => (
        <div>
          <div className="strong">{r.name}</div>
          <div className="tiny muted truncate" style={{ maxWidth: 320 }}>
            {r.description}
          </div>
        </div>
      ),
    },
    { key: 'parkName', header: 'Park', width: 190 },
    {
      key: 'durationDays',
      header: 'Days',
      width: 70,
      align: 'center',
      sortValue: (r) => r.durationDays,
    },
    {
      key: 'pricePerPerson',
      header: 'Price / person',
      width: 130,
      align: 'right',
      sortValue: (r) => Number(r.pricePerPerson),
      render: (r) => <span className="mono-num">{money(r.pricePerPerson)}</span>,
    },
    { key: 'maxGroupSize', header: 'Max group', width: 100, align: 'center' },
    {
      key: 'active',
      header: 'Status',
      width: 110,
      sortValue: (r) => (r.active ? 1 : 0),
      render: (r) => (
        <StatusBadge
          value={r.active ? 'AVAILABLE' : 'INACTIVE'}
          label={r.active ? 'Active' : 'Inactive'}
        />
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
          <Button size="sm" variant="ghost" onClick={() => toggleActive(r)}>
            {r.active ? 'Deactivate' : 'Activate'}
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
          <div className="eyebrow">Catalogue</div>
          <h1>Safari packages</h1>
          <p className="lede">
            Create and maintain the departures customers can book. Deactivating a package hides it
            from browsing without affecting existing bookings.
          </p>
        </div>
        <Button onClick={openNew} disabled={!parks.data?.length}>
          + New package
        </Button>
      </div>

      {packages.loading ? (
        <SkeletonTable rows={7} cols={7} />
      ) : packages.error ? (
        <ErrorState message={packages.error} onRetry={packages.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={packages.data}
          initialSort={{ key: 'name', dir: 'asc' }}
          searchPlaceholder="Search packages or parks…"
          emptyIcon="🎒"
          emptyTitle="No packages yet"
          emptyMessage="Create your first safari package to open the catalogue."
        />
      )}

      <Modal
        open={!!editing}
        title={editing?.isNew ? 'New safari package' : `Edit — ${editing?.name ?? ''}`}
        onClose={() => !busy && setEditing(null)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} loading={busy}>
              {editing?.isNew ? 'Create package' : 'Save changes'}
            </Button>
          </>
        }
      >
        <form onSubmit={save} className="form-grid" noValidate>
          <Field label="Package name" error={errors.name} span2>
            <Input value={form.name} onChange={set('name')} error={errors.name} required />
          </Field>

          <Field label="Description" error={errors.description} span2>
            <Textarea rows={3} value={form.description} onChange={set('description')} />
          </Field>

          <Field label="Park" error={errors.parkId}>
            <Select value={form.parkId} onChange={set('parkId')} error={errors.parkId} required>
              <option value="">Choose a park…</option>
              {(parks.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Duration (days)" error={errors.durationDays}>
            <Input
              type="number"
              min={1}
              value={form.durationDays}
              onChange={set('durationDays')}
              error={errors.durationDays}
              required
            />
          </Field>

          <Field label="Price per person (USD)" error={errors.pricePerPerson}>
            <Input
              type="number"
              min="0.01"
              step="0.01"
              value={form.pricePerPerson}
              onChange={set('pricePerPerson')}
              error={errors.pricePerPerson}
              required
            />
          </Field>

          <Field
            label="Max group size"
            error={errors.maxGroupSize}
            hint="Hard capacity per departure date."
          >
            <Input
              type="number"
              min={1}
              value={form.maxGroupSize}
              onChange={set('maxGroupSize')}
              error={errors.maxGroupSize}
              required
            />
          </Field>

          <Field
            label="Photo URL"
            hint="An https:// image link, shown on the website. Leave blank to use the branded placeholder."
            error={errors.imageUrl}
          >
            <Input value={form.imageUrl} onChange={set('imageUrl')} placeholder="https://images.unsplash.com/…" />
          </Field>

          <Field label="Highlights" hint="Separate with | (pipe)." error={errors.highlights}>
            <Input
              value={form.highlights}
              onChange={set('highlights')}
              placeholder="Tracker-led jeep|Bundala flamingos"
            />
          </Field>

          <Field span2>
            <label className="checkline">
              <input type="checkbox" checked={!!form.active} onChange={set('active')} />
              <span>Active — visible to customers and open for booking</span>
            </label>
          </Field>
        </form>
      </Modal>

      <Modal
        open={!!deleting}
        title="Delete package"
        onClose={() => !busy && setDeleting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete package
            </Button>
          </>
        }
      >
        {deleting && (
          <div className="stack">
            <p>
              Permanently delete <strong>{deleting.name}</strong>? This cannot be undone.
            </p>
            <div className="inline-note warn">
              <span aria-hidden="true">⚠</span>
              <span>
                Only packages that have never been booked can be deleted. If anyone has booked it,
                the server will refuse and you should deactivate it instead, which stops new
                bookings but keeps the history.
              </span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
