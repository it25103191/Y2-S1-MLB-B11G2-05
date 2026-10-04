import { useEffect, useState } from 'react';
import { assignmentApi, bookingApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorDetails, errorMessage } from '../../api/client';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import {
  Button,
  Card,
  CardHead,
  EmptyState,
  ErrorState,
  Field,
  Modal,
  Select,
  Skeleton,
  SkeletonTable,
  Textarea,
  dateFmt,
  relativeDays,
  titleCase,
} from '../../components/ui';

const CREW_STATUSES = ['SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

/** Rules the backend can use to recommend a crew (Strategy pattern: one class per rule). */
const CREW_RULES = [
  { value: 'least-busy', label: 'Least busy guide' },
  { value: 'most-experienced', label: 'Most experienced guide' },
];

function initials(name = '') {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
}

/** Guide / vehicle picker list with availability state baked in. */
function PickList({ items, selectedId, onSelect, recommendedId, emptyLabel }) {
  if (!items?.length) return <div className="muted small">{emptyLabel}</div>;
  return (
    <div className="pick-list">
      {items.map((c) => (
        <button
          key={c.id}
          type="button"
          className={`pick${selectedId === c.id ? ' selected' : ''}${c.available ? '' : ' pick-busy'}`}
          onClick={() => onSelect(c.id)}
          title={c.available ? 'Free for this window' : c.unavailableReason}
        >
          <span className="pick-avatar">{initials(c.name)}</span>
          <span className="pick-main">
            <span className="pick-name">
              {c.name}
              {recommendedId === c.id && (
                <span className="badge badge-forest" style={{ marginLeft: 8 }}>
                  Suggested
                </span>
              )}
            </span>
            <span className="pick-sub">{c.detail}</span>
            {!c.available && (
              <span className="pick-sub" style={{ color: 'var(--danger)' }}>
                {c.unavailableReason}
              </span>
            )}
          </span>
          {c.available ? (
            <StatusBadge value="AVAILABLE" label="Free" />
          ) : (
            <StatusBadge value="CANCELLED" tone="danger" label="Busy" />
          )}
        </button>
      ))}
    </div>
  );
}

export default function AssignmentsPage() {
  const toast = useToast();
  const awaiting = useApi(() => bookingApi.awaitingAssignment(), []);
  const assignments = useApi(() => assignmentApi.list(), []);

  const [target, setTarget] = useState(null); // booking being crewed
  const [editing, setEditing] = useState(null); // existing assignment being changed
  const [suggestion, setSuggestion] = useState(null);
  const [loadingSuggestion, setLoadingSuggestion] = useState(false);
  const [crewRule, setCrewRule] = useState(CREW_RULES[0].value);
  const [guideId, setGuideId] = useState(null);
  const [vehicleId, setVehicleId] = useState(null);
  const [notes, setNotes] = useState('');
  const [conflict, setConflict] = useState(null);
  const [override, setOverride] = useState(false);
  const [overrideReason, setOverrideReason] = useState('');
  const [busy, setBusy] = useState(false);

  const openFor = async (booking, existing = null) => {
    setTarget(booking);
    setEditing(existing);
    setSuggestion(null);
    setConflict(null);
    setOverride(false);
    setOverrideReason('');
    setNotes(existing?.notes ?? '');
    setGuideId(existing?.guideId ?? null);
    setVehicleId(existing?.vehicleId ?? null);
    await loadSuggestion(booking, crewRule);
  };

  /** Asks the backend for availability, with the recommendation made by the chosen rule. */
  const loadSuggestion = async (booking, rule) => {
    setLoadingSuggestion(true);
    try {
      const s = await assignmentApi.suggest(booking.id, rule);
      setSuggestion(s);
    } catch (err) {
      toast.error(errorMessage(err), 'Could not load availability');
    } finally {
      setLoadingSuggestion(false);
    }
  };

  const changeCrewRule = (rule) => {
    setCrewRule(rule);
    if (target) loadSuggestion(target, rule);
  };

  const close = () => {
    setTarget(null);
    setEditing(null);
    setSuggestion(null);
    setConflict(null);
  };

  const applySuggestion = () => {
    if (!suggestion) return;
    setGuideId(suggestion.recommendedGuideId);
    setVehicleId(suggestion.recommendedVehicleId);
    setConflict(null);
    if (suggestion.recommendedGuideId && suggestion.recommendedVehicleId) {
      toast.info(suggestion.rationale, 'Auto-suggestion applied');
    } else {
      toast.warning(suggestion.rationale, 'No complete suggestion');
    }
  };

  const submit = async () => {
    if (!guideId || !vehicleId) {
      toast.warning('Pick both a guide and a vehicle.', 'Incomplete');
      return;
    }
    setBusy(true);
    setConflict(null);
    const payload = {
      bookingId: target.id,
      guideId,
      vehicleId,
      notes: notes || null,
      override,
      overrideReason: override ? overrideReason : null,
    };
    try {
      if (editing) {
        await assignmentApi.update(editing.id, payload);
        toast.success(`Crew updated for ${target.bookingReference}.`, 'Assignment saved');
      } else {
        await assignmentApi.create(payload);
        toast.success(`${target.bookingReference} is crewed and scheduled.`, 'Assignment created');
      }
      close();
      awaiting.reload();
      assignments.reload();
    } catch (err) {
      const details = errorDetails(err);
      if (details?.canOverride) {
        // Surface exactly what is in the way and offer the manual override path.
        setConflict({ message: errorMessage(err), ...details });
        toast.error(errorMessage(err), 'Scheduling conflict');
      } else {
        toast.error(errorMessage(err), 'Could not save assignment');
      }
    } finally {
      setBusy(false);
    }
  };

  const changeStatus = async (a, status) => {
    try {
      await assignmentApi.changeStatus(a.id, status);
      toast.success(
        `${a.bookingReference} is now ${titleCase(status).toLowerCase()}.` +
          (status === 'CANCELLED' ? ' The guide and vehicle are free again.' : ''),
        'Crew status updated',
      );
      assignments.reload();
      awaiting.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update status');
    }
  };

  const removeAssignment = async (a) => {
    try {
      await assignmentApi.remove(a.id);
      toast.success(`Crew released for ${a.bookingReference}.`, 'Assignment removed');
      awaiting.reload();
      assignments.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not remove');
    }
  };

  // Clear a stale conflict as soon as the operator changes the selection.
  useEffect(() => {
    setConflict(null);
  }, [guideId, vehicleId]);

  const assignmentColumns = [
    {
      key: 'bookingReference',
      header: 'Booking',
      width: 132,
      render: (r) => <span className="mono-num small strong">{r.bookingReference}</span>,
    },
    {
      key: 'customerName',
      header: 'Customer',
      render: (r) => (
        <div>
          <div>{r.customerName}</div>
          <div className="tiny muted">
            {r.packageName} · {r.participants} pax
          </div>
        </div>
      ),
    },
    {
      key: 'assignmentDate',
      header: 'Dates',
      width: 172,
      render: (r) => (
        <div>
          <div>
            {dateFmt(r.assignmentDate)} → {dateFmt(r.endDate)}
          </div>
          <div className="tiny muted">{relativeDays(r.assignmentDate)}</div>
        </div>
      ),
    },
    { key: 'guideName', header: 'Guide', width: 160 },
    {
      key: 'vehicleRegistration',
      header: 'Vehicle',
      width: 168,
      render: (r) => (
        <div>
          <div className="mono-num small">{r.vehicleRegistration}</div>
          <div className="tiny muted">{r.vehicleModel}</div>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 160,
      render: (r) => (
        <div className="col" style={{ gap: 4 }}>
          <Select
            value={r.status}
            onChange={(e) => changeStatus(r, e.target.value)}
            aria-label={`Status for ${r.bookingReference}`}
            style={{ padding: '5px 28px 5px 9px', fontSize: '0.8rem' }}
          >
            {CREW_STATUSES.map((s) => (
              <option key={s} value={s}>
                {titleCase(s)}
              </option>
            ))}
          </Select>
          {r.manualOverride && <StatusBadge value="OVERRIDE" tone="warning" label="Override" />}
        </div>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: 160,
      sortable: false,
      searchable: false,
      render: (r) => (
        <div className="row row-gap-1">
          <Button
            size="sm"
            variant="outline"
            onClick={() =>
              openFor(
                {
                  id: r.bookingId,
                  bookingReference: r.bookingReference,
                  packageName: r.packageName,
                  customerName: r.customerName,
                  tripDate: r.assignmentDate,
                  participants: r.participants,
                  parkName: r.parkName,
                },
                r,
              )
            }
          >
            Change
          </Button>
          <Button size="sm" variant="ghost" onClick={() => removeAssignment(r)}>
            Release
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Operations</div>
          <h1>Crew assignments</h1>
          <p className="lede">
            Confirmed departures need a guide and a vehicle. Clashing commitments are blocked
            automatically — use a manual override when you deliberately need to double-book.
          </p>
        </div>
      </div>

      {/* -------------------------------------------- Awaiting assignment */}
      <Card className="mb-3">
        <CardHead
          title="Awaiting assignment"
          subtitle="Confirmed bookings with no crew yet"
          actions={
            <span className="badge badge-warning">
              {awaiting.loading ? '…' : (awaiting.data?.length ?? 0)} waiting
            </span>
          }
        />
        <div className="card-body">
          {awaiting.loading ? (
            <div className="stack-sm">
              <Skeleton height={54} />
              <Skeleton height={54} />
            </div>
          ) : awaiting.error ? (
            <ErrorState message={awaiting.error} onRetry={awaiting.reload} />
          ) : awaiting.data.length === 0 ? (
            <div className="inline-note ok">
              <span aria-hidden="true">✓</span>
              <span>Every confirmed departure has a guide and vehicle assigned.</span>
            </div>
          ) : (
            <div className="stack-sm">
              {awaiting.data.map((b) => (
                <div
                  key={b.id}
                  className="row row-gap-2 wrap"
                  style={{ padding: '12px 0', borderBottom: '1px solid var(--cream-200)' }}
                >
                  <div className="grow" style={{ minWidth: 200 }}>
                    <div className="strong">{b.packageName}</div>
                    <div className="tiny muted">
                      <span className="mono-num">{b.bookingReference}</span> · {b.customerName} ·{' '}
                      {b.participants} pax · {b.parkName}
                    </div>
                  </div>
                  <div className="right nowrap">
                    <div className="small">
                      {dateFmt(b.tripDate)} → {dateFmt(b.tripEndDate)}
                    </div>
                    <div className="tiny muted">{relativeDays(b.tripDate)}</div>
                  </div>
                  <Button size="sm" onClick={() => openFor(b)}>
                    Assign crew
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      </Card>

      {/* -------------------------------------------- Scheduled crews */}
      <div className="section-title">Scheduled crews</div>
      {assignments.loading ? (
        <SkeletonTable rows={5} cols={7} />
      ) : assignments.error ? (
        <ErrorState message={assignments.error} onRetry={assignments.reload} />
      ) : (
        <DataTable
          columns={assignmentColumns}
          rows={assignments.data}
          initialSort={{ key: 'assignmentDate', dir: 'asc' }}
          searchPlaceholder="Search booking, customer, guide, vehicle…"
          emptyIcon="🧭"
          emptyTitle="No crews scheduled"
          emptyMessage="Assign a guide and vehicle to a confirmed booking to see it here."
        />
      )}

      {/* -------------------------------------------- Assignment dialog */}
      <Modal
        open={!!target}
        title={
          target
            ? `${editing ? 'Change crew' : 'Assign crew'} — ${target.bookingReference}`
            : ''
        }
        onClose={() => !busy && close()}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={close} disabled={busy}>
              Cancel
            </Button>
            <Button variant="outline" onClick={applySuggestion} disabled={busy || !suggestion}>
              ✨ Auto-suggest
            </Button>
            <Button onClick={submit} loading={busy} disabled={!guideId || !vehicleId}>
              {editing ? 'Save crew' : 'Confirm assignment'}
            </Button>
          </>
        }
      >
        {target && (
          <div className="stack">
            <div className="suggest-box">
              <div className="row row-gap-3 wrap">
                <div>
                  <div className="si-label">Trip</div>
                  <div className="strong">{target.packageName}</div>
                </div>
                <div>
                  <div className="si-label">Window</div>
                  <div className="strong">
                    {suggestion
                      ? `${dateFmt(suggestion.from)} → ${dateFmt(suggestion.to)}`
                      : dateFmt(target.tripDate)}
                  </div>
                </div>
                <div>
                  <div className="si-label">Travellers</div>
                  <div className="strong">{target.participants}</div>
                </div>
                <div>
                  <div className="si-label">Customer</div>
                  <div className="strong">{target.customerName}</div>
                </div>
              </div>
              <div className="row row-gap-3 wrap mt-2" style={{ alignItems: 'flex-end' }}>
                <Field label="Suggest by" hint="Changes who Auto-suggest recommends.">
                  <Select
                    value={crewRule}
                    onChange={(e) => changeCrewRule(e.target.value)}
                    disabled={loadingSuggestion || busy}
                  >
                    {CREW_RULES.map((r) => (
                      <option key={r.value} value={r.value}>
                        {r.label}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              {suggestion && <div className="tiny mt-1">{suggestion.rationale}</div>}
            </div>

            {conflict && (
              <div className="conflict-box">
                <div className="cb-title">
                  <span aria-hidden="true">⚠</span> Scheduling conflict
                </div>
                <div>{conflict.message}</div>
                {conflict.clashes?.length > 0 && (
                  <ul>
                    {conflict.clashes.map((c) => (
                      <li key={`${c.resourceType}-${c.assignmentId}`}>
                        <strong>
                          {c.resourceType === 'GUIDE' ? 'Guide' : 'Vehicle'} {c.resourceName}
                        </strong>{' '}
                        is on {c.bookingReference} from {dateFmt(c.from)} to {dateFmt(c.to)}
                      </li>
                    ))}
                  </ul>
                )}
                {conflict.blockers?.length > 0 && (
                  <ul>
                    {conflict.blockers.map((b) => (
                      <li key={b}>{b}</li>
                    ))}
                  </ul>
                )}
                <label className="checkline mt-2">
                  <input
                    type="checkbox"
                    checked={override}
                    onChange={(e) => setOverride(e.target.checked)}
                  />
                  <span>Override this conflict anyway</span>
                </label>
                {override && (
                  <div className="mt-1">
                    <Field label="Override reason (recorded on the assignment)">
                      <Textarea
                        rows={2}
                        value={overrideReason}
                        onChange={(e) => setOverrideReason(e.target.value)}
                        placeholder="Why is this double-booking acceptable?"
                      />
                    </Field>
                  </div>
                )}
              </div>
            )}

            {loadingSuggestion ? (
              <div className="grid grid-2">
                <Skeleton height={210} />
                <Skeleton height={210} />
              </div>
            ) : suggestion ? (
              <div className="grid grid-2">
                <div>
                  <div className="section-title">
                    Guides{' '}
                    <span className="tiny muted">
                      ({suggestion.guides.filter((g) => g.available).length} free)
                    </span>
                  </div>
                  <PickList
                    items={suggestion.guides}
                    selectedId={guideId}
                    onSelect={setGuideId}
                    recommendedId={suggestion.recommendedGuideId}
                    emptyLabel="No guides registered."
                  />
                </div>
                <div>
                  <div className="section-title">
                    Vehicles{' '}
                    <span className="tiny muted">
                      ({suggestion.vehicles.filter((v) => v.available).length} free)
                    </span>
                  </div>
                  <PickList
                    items={suggestion.vehicles}
                    selectedId={vehicleId}
                    onSelect={setVehicleId}
                    recommendedId={suggestion.recommendedVehicleId}
                    emptyLabel="No vehicles registered."
                  />
                </div>
              </div>
            ) : (
              <EmptyState icon="⚠" title="Availability unavailable" message="Could not load candidates." />
            )}

            <Field label="Notes for the crew (optional)">
              <Textarea
                rows={2}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Pick-up details, client preferences…"
              />
            </Field>
          </div>
        )}
      </Modal>
    </div>
  );
}
