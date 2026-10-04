import { useState } from 'react';
import { kpiTargetApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { errorMessage, fieldErrors } from '../../api/client';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { TargetProgress, formatMetric } from '../../components/KpiProgress';
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
  titleCase,
} from '../../components/ui';

const METRICS = [
  { value: 'REVENUE', label: 'Revenue', unit: 'CURRENCY', hint: 'Settled payments received in the month.' },
  { value: 'BOOKINGS', label: 'Bookings', unit: 'COUNT', hint: 'Non-cancelled bookings departing in the month.' },
  { value: 'TRAVELLERS', label: 'Travellers', unit: 'COUNT', hint: 'Travellers on non-cancelled departures.' },
  {
    value: 'CANCELLATION_RATE',
    label: 'Cancellation rate',
    unit: 'PERCENT',
    hint: 'Lower is better. Share of the month’s departures that were cancelled.',
  },
  {
    value: 'AVERAGE_BOOKING_VALUE',
    label: 'Average booking value',
    unit: 'CURRENCY',
    hint: 'Average price of non-cancelled departures in the month.',
  },
];

const STATUSES = ['ACHIEVED', 'MISSED', 'IN_PROGRESS', 'UPCOMING'];
const SETTERS = ['OPERATIONS_MANAGER', 'FINANCE_RESERVATIONS_EXECUTIVE', 'FINANCE_ACCOUNTS_OFFICER'];

function thisMonth() {
  return new Date().toISOString().slice(0, 7);
}

const BLANK = { metric: 'REVENUE', month: thisMonth(), targetValue: '', notes: '' };

export default function KpiTargetsPage() {
  const { user } = useAuth();
  const toast = useToast();
  const targets = useApi(() => kpiTargetApi.list(), []);
  const canEdit = SETTERS.includes(user.role);

  const [statusFilter, setStatusFilter] = useState('');
  const [metricFilter, setMetricFilter] = useState('');
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);

  const all = targets.data ?? [];
  const rows = all.filter(
    (t) => (!statusFilter || t.status === statusFilter) && (!metricFilter || t.metric === metricFilter),
  );
  const selectedMetric = METRICS.find((m) => m.value === form.metric) ?? METRICS[0];

  const openNew = () => {
    setForm(BLANK);
    setErrors({});
    setEditing({ isNew: true });
  };

  const openEdit = (t) => {
    setForm({ metric: t.metric, month: t.month, targetValue: t.targetValue, notes: t.notes ?? '' });
    setErrors({});
    setEditing(t);
  };

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    const payload = {
      metric: form.metric,
      month: form.month,
      targetValue: form.targetValue === '' ? null : Number(form.targetValue),
      notes: form.notes || null,
    };
    try {
      if (editing.isNew) {
        const created = await kpiTargetApi.create(payload);
        toast.success(`${created.metricLabel} target set for ${created.monthLabel}.`, 'Target created');
      } else {
        const updated = await kpiTargetApi.update(editing.id, payload);
        toast.success(`${updated.metricLabel} target for ${updated.monthLabel} updated.`, 'Target saved');
      }
      setEditing(null);
      targets.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save target');
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await kpiTargetApi.remove(deleting.id);
      toast.success(`${deleting.metricLabel} target for ${deleting.monthLabel} removed.`, 'Target deleted');
      setDeleting(null);
      targets.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const counts = STATUSES.reduce((acc, s) => ({ ...acc, [s]: all.filter((t) => t.status === s).length }), {});

  const columns = [
    {
      key: 'month',
      header: 'Month',
      width: 110,
      render: (r) => <span className="strong">{r.monthLabel}</span>,
    },
    {
      key: 'metricLabel',
      header: 'Metric',
      width: 180,
      render: (r) => (
        <div>
          <div>{r.metricLabel}</div>
          {!r.higherIsBetter && <div className="tiny muted">Lower is better</div>}
        </div>
      ),
    },
    {
      key: 'targetValue',
      header: 'Target',
      width: 120,
      align: 'right',
      sortValue: (r) => Number(r.targetValue),
      render: (r) => (
        <span className="mono-num">
          {r.higherIsBetter ? '' : '≤ '}
          {formatMetric(r.unit, r.targetValue)}
        </span>
      ),
    },
    {
      key: 'actualValue',
      header: 'Actual',
      width: 120,
      align: 'right',
      sortValue: (r) => Number(r.actualValue),
      render: (r) => <span className="mono-num strong">{formatMetric(r.unit, r.actualValue)}</span>,
    },
    {
      key: 'percentOfTarget',
      header: 'Progress',
      width: 150,
      sortValue: (r) => Number(r.percentOfTarget ?? 0),
      render: (r) => (
        <TargetProgress
          percent={r.percentOfTarget}
          met={r.met}
          higherIsBetter={r.higherIsBetter}
          status={r.status}
        />
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 120,
      sortValue: (r) => STATUSES.indexOf(r.status),
      render: (r) => <StatusBadge value={r.status} />,
    },
    {
      key: 'notes',
      header: 'Notes',
      render: (r) => (
        <div>
          <div className="small truncate" style={{ maxWidth: 240 }}>
            {r.notes ?? <span className="muted">—</span>}
          </div>
          <div className="tiny muted">
            {r.updatedByName ? `Updated by ${r.updatedByName}` : r.createdByName ? `Set by ${r.createdByName}` : ''}
          </div>
        </div>
      ),
    },
    ...(canEdit
      ? [
          {
            key: 'actions',
            header: '',
            width: 140,
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
        ]
      : []),
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Analytics</div>
          <h1>KPI targets</h1>
          <p className="lede">
            Monthly goals the business is measured against. Actuals are live, so progress updates
            as bookings and payments come in, and the dashboard charts show each target as a dashed
            line.
          </p>
        </div>
        {canEdit && <Button onClick={openNew}>+ Set target</Button>}
      </div>

      <div className="grid grid-4 mb-3">
        {STATUSES.map((s) => (
          <Card
            key={s}
            className={`kpi${s === 'MISSED' ? ' kpi-danger' : s === 'IN_PROGRESS' ? ' kpi-amber' : s === 'UPCOMING' ? ' kpi-terracotta' : ''}`}
            style={{ cursor: 'pointer' }}
            onClick={() => setStatusFilter(statusFilter === s ? '' : s)}
          >
            <div className="kpi-label">{titleCase(s)}</div>
            <div className="kpi-value">{counts[s] ?? 0}</div>
            <div className="kpi-sub">{statusFilter === s ? 'Filtering — click to clear' : 'Click to filter'}</div>
          </Card>
        ))}
      </div>

      {!canEdit && (
        <div className="inline-note mb-2">
          <span aria-hidden="true">ℹ</span>
          <span>Targets are set by the Operations Manager and the finance team. You can view them here.</span>
        </div>
      )}

      {targets.loading ? (
        <SkeletonTable rows={7} cols={8} />
      ) : targets.error ? (
        <ErrorState message={targets.error} onRetry={targets.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'month', dir: 'desc' }}
          searchPlaceholder="Search metric, month or notes…"
          emptyIcon="🎯"
          emptyTitle="No targets yet"
          emptyMessage={canEdit ? 'Set your first monthly target to start tracking performance.' : 'No targets have been set.'}
          rowClassName={(r) => (r.status === 'MISSED' ? 'row-alert' : '')}
          filters={
            <>
              <Select
                value={metricFilter}
                onChange={(e) => setMetricFilter(e.target.value)}
                style={{ width: 190 }}
                aria-label="Filter by metric"
              >
                <option value="">All metrics</option>
                {METRICS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </Select>
              <Select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                style={{ width: 150 }}
                aria-label="Filter by status"
              >
                <option value="">All statuses</option>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {titleCase(s)}
                  </option>
                ))}
              </Select>
            </>
          }
        />
      )}

      <Modal
        open={!!editing}
        title={editing?.isNew ? 'Set a KPI target' : `Edit — ${editing?.metricLabel ?? ''}, ${editing?.monthLabel ?? ''}`}
        onClose={() => !busy && setEditing(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} loading={busy}>
              {editing?.isNew ? 'Set target' : 'Save changes'}
            </Button>
          </>
        }
      >
        <form onSubmit={save} className="stack" noValidate>
          <Field label="Metric" error={errors.metric} hint={selectedMetric.hint}>
            <Select value={form.metric} onChange={set('metric')}>
              {METRICS.map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Month" error={errors.month} hint="One target per metric per month.">
            <Input type="month" value={form.month} onChange={set('month')} error={errors.month} required />
          </Field>

          <Field
            label={
              selectedMetric.unit === 'CURRENCY'
                ? 'Target (USD)'
                : selectedMetric.unit === 'PERCENT'
                  ? 'Target (%) — maximum acceptable'
                  : 'Target (whole number)'
            }
            error={errors.targetValue}
          >
            <Input
              type="number"
              min="0"
              step={selectedMetric.unit === 'COUNT' ? '1' : '0.01'}
              max={selectedMetric.unit === 'PERCENT' ? '100' : undefined}
              value={form.targetValue}
              onChange={set('targetValue')}
              error={errors.targetValue}
              required
            />
          </Field>

          <Field label="Notes (optional)" error={errors.notes}>
            <Textarea rows={2} value={form.notes} onChange={set('notes')} placeholder="Why this number?" />
          </Field>
        </form>
      </Modal>

      <Modal
        open={!!deleting}
        title="Delete target"
        onClose={() => !busy && setDeleting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete target
            </Button>
          </>
        }
      >
        {deleting && (
          <p>
            Remove the <strong>{deleting.metricLabel}</strong> target for <strong>{deleting.monthLabel}</strong> (
            {formatMetric(deleting.unit, deleting.targetValue)})? The dashboard will stop showing it.
          </p>
        )}
      </Modal>
    </div>
  );
}
