import { useState } from 'react';
import { vehicleApi } from '../../api/api';
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
  dateFmt,
} from '../../components/ui';

const STATUSES = ['AVAILABLE', 'MAINTENANCE', 'RETIRED'];

const BLANK = {
  registrationNumber: '',
  model: '',
  type: '4x4 Game Viewer',
  capacity: 7,
  status: 'AVAILABLE',
  lastServiceDate: '',
  notes: '',
};

export default function VehicleManagementPage() {
  const toast = useToast();
  const vehicles = useApi(() => vehicleApi.list(), []);

  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');

  const rows = (vehicles.data ?? []).filter((v) => !statusFilter || v.status === statusFilter);

  const openNew = () => {
    setForm(BLANK);
    setErrors({});
    setEditing({ isNew: true });
  };

  const openEdit = (v) => {
    setForm({
      registrationNumber: v.registrationNumber,
      model: v.model,
      type: v.type,
      capacity: v.capacity,
      status: v.status,
      lastServiceDate: v.lastServiceDate ?? '',
      notes: v.notes ?? '',
    });
    setErrors({});
    setEditing(v);
  };

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    const payload = {
      ...form,
      capacity: Number(form.capacity),
      lastServiceDate: form.lastServiceDate || null,
    };
    try {
      if (editing.isNew) {
        await vehicleApi.create(payload);
        toast.success(`${payload.registrationNumber} added to the fleet.`, 'Vehicle registered');
      } else {
        await vehicleApi.update(editing.id, payload);
        toast.success(`${payload.registrationNumber} updated.`, 'Saved');
      }
      setEditing(null);
      vehicles.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save vehicle');
    } finally {
      setBusy(false);
    }
  };

  const quickStatus = async (v, status) => {
    try {
      await vehicleApi.setStatus(v.id, status);
      toast.success(`${v.registrationNumber} is now ${status.toLowerCase()}.`, 'Status updated');
      vehicles.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update status');
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await vehicleApi.remove(deleting.id);
      toast.success(`${deleting.registrationNumber} removed from the fleet.`, 'Vehicle deleted');
      setDeleting(null);
      vehicles.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const columns = [
    {
      key: 'registrationNumber',
      header: 'Registration',
      width: 140,
      render: (r) => <span className="strong mono-num">{r.registrationNumber}</span>,
    },
    {
      key: 'model',
      header: 'Vehicle',
      render: (r) => (
        <div>
          <div>{r.model}</div>
          <div className="tiny muted">{r.type}</div>
        </div>
      ),
    },
    { key: 'capacity', header: 'Seats', width: 76, align: 'center', sortValue: (r) => r.capacity },
    {
      key: 'lastServiceDate',
      header: 'Last service',
      width: 128,
      render: (r) => dateFmt(r.lastServiceDate),
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
      header: 'Status',
      width: 130,
      render: (r) => <StatusBadge value={r.status} />,
    },
    {
      key: 'actions',
      header: '',
      width: 240,
      sortable: false,
      searchable: false,
      render: (r) => (
        <div className="row row-gap-1">
          <Button size="sm" variant="outline" onClick={() => openEdit(r)}>
            Edit
          </Button>
          {r.status === 'AVAILABLE' ? (
            <Button size="sm" variant="ghost" onClick={() => quickStatus(r, 'MAINTENANCE')}>
              Maintenance
            </Button>
          ) : (
            <Button size="sm" variant="ghost" onClick={() => quickStatus(r, 'AVAILABLE')}>
              Return to service
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
          <div className="eyebrow">Fleet</div>
          <h1>Vehicles</h1>
          <p className="lede">
            Register safari vehicles, track servicing and take them off the road for maintenance.
            Vehicles that are not available cannot be assigned to a trip.
          </p>
        </div>
        <Button onClick={openNew}>+ Register vehicle</Button>
      </div>

      <div className="grid grid-4 mb-3">
        {STATUSES.map((s) => {
          const n = (vehicles.data ?? []).filter((v) => v.status === s).length;
          return (
            <Card key={s} className="kpi">
              <div className="kpi-label">{s.charAt(0) + s.slice(1).toLowerCase()}</div>
              <div className="kpi-value">{n}</div>
            </Card>
          );
        })}
        <Card className="kpi kpi-amber">
          <div className="kpi-label">Total seats</div>
          <div className="kpi-value">
            {(vehicles.data ?? []).reduce((s, v) => s + (v.capacity || 0), 0)}
          </div>
        </Card>
      </div>

      {vehicles.loading ? (
        <SkeletonTable rows={5} cols={7} />
      ) : vehicles.error ? (
        <ErrorState message={vehicles.error} onRetry={vehicles.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'registrationNumber', dir: 'asc' }}
          searchPlaceholder="Search registration, model or type…"
          emptyIcon="🚙"
          emptyTitle="No vehicles registered"
          emptyMessage="Register your first safari vehicle to start crewing trips."
          filters={
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 170 }}
              aria-label="Filter by status"
            >
              <option value="">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.charAt(0) + s.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          }
        />
      )}

      <Modal
        open={!!editing}
        title={editing?.isNew ? 'Register vehicle' : `Edit — ${editing?.registrationNumber ?? ''}`}
        onClose={() => !busy && setEditing(null)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} loading={busy}>
              {editing?.isNew ? 'Register vehicle' : 'Save changes'}
            </Button>
          </>
        }
      >
        <form onSubmit={save} className="form-grid" noValidate>
          <Field label="Registration number" error={errors.registrationNumber}>
            <Input
              value={form.registrationNumber}
              onChange={set('registrationNumber')}
              placeholder="WP KA-4471"
              error={errors.registrationNumber}
              required
            />
          </Field>

          <Field label="Model" error={errors.model}>
            <Input
              value={form.model}
              onChange={set('model')}
              placeholder="Toyota Land Cruiser 79"
              error={errors.model}
              required
            />
          </Field>

          <Field label="Type" error={errors.type}>
            <Input value={form.type} onChange={set('type')} error={errors.type} required />
          </Field>

          <Field label="Seat capacity" error={errors.capacity}>
            <Input
              type="number"
              min={1}
              value={form.capacity}
              onChange={set('capacity')}
              error={errors.capacity}
              required
            />
          </Field>

          <Field label="Status">
            <Select value={form.status} onChange={set('status')}>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.charAt(0) + s.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Last service date">
            <Input type="date" value={form.lastServiceDate} onChange={set('lastServiceDate')} />
          </Field>

          <Field label="Notes" span2>
            <Textarea rows={2} value={form.notes} onChange={set('notes')} />
          </Field>
        </form>
      </Modal>

      <Modal
        open={!!deleting}
        title="Delete vehicle"
        onClose={() => !busy && setDeleting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete vehicle
            </Button>
          </>
        }
      >
        {deleting && (
          <div className="stack">
            <p>
              Remove <strong>{deleting.registrationNumber}</strong> ({deleting.model}) from the fleet?
            </p>
            {deleting.upcomingAssignments > 0 && (
              <div className="inline-note warn">
                <span aria-hidden="true">⚠</span>
                <span>
                  This vehicle has {deleting.upcomingAssignments} upcoming assignment(s), so the
                  server will refuse the delete. Mark it retired instead.
                </span>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
