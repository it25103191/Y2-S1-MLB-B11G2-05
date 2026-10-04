import { useMemo, useState } from 'react';
import { assignmentApi, guideApi, vehicleApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import StatusBadge from '../../components/StatusBadge';
import PermitPanel from '../../components/PermitPanel';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Modal,
  Select,
  Skeleton,
  dateFmt,
  todayISO,
} from '../../components/ui';

const SPANS = [
  { label: '2 weeks', days: 14 },
  { label: '4 weeks', days: 28 },
  { label: '8 weeks', days: 56 },
];

function addDays(iso, n) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + n);
  return d.toISOString().slice(0, 10);
}

function diffDays(fromIso, toIso) {
  return Math.round(
    (new Date(`${toIso}T00:00:00`) - new Date(`${fromIso}T00:00:00`)) / 86400000,
  );
}

export default function SchedulePage() {
  const [start, setStart] = useState(todayISO());
  const [spanIdx, setSpanIdx] = useState(1);
  const [mode, setMode] = useState('guides');
  const [detail, setDetail] = useState(null);

  const days = SPANS[spanIdx].days;
  const end = addDays(start, days - 1);

  const schedule = useApi(() => assignmentApi.schedule(start, end), [start, end]);
  const guides = useApi(() => guideApi.list(), []);
  const vehicles = useApi(() => vehicleApi.list(), []);

  const dayCells = useMemo(
    () =>
      Array.from({ length: days }, (_, i) => {
        const iso = addDays(start, i);
        const d = new Date(`${iso}T00:00:00`);
        const dow = d.getDay();
        return {
          iso,
          label: d.getDate(),
          month: d.toLocaleDateString('en-GB', { month: 'short' }),
          dow: d.toLocaleDateString('en-GB', { weekday: 'narrow' }),
          weekend: dow === 0 || dow === 6,
          first: d.getDate() === 1 || i === 0,
        };
      }),
    [start, days],
  );

  const resources =
    mode === 'guides'
      ? (guides.data ?? []).map((g) => ({
          id: g.id,
          name: g.fullName,
          sub: `${g.specialization ?? 'Guide'} · ${g.status.replace('_', ' ').toLowerCase()}`,
          status: g.status,
        }))
      : (vehicles.data ?? []).map((v) => ({
          id: v.id,
          name: v.registrationNumber,
          sub: `${v.model} · ${v.capacity} seats`,
          status: v.status,
        }));

  const barsFor = (resourceId) =>
    (schedule.data ?? [])
      .filter((a) => (mode === 'guides' ? a.guideId === resourceId : a.vehicleId === resourceId))
      .map((a) => {
        const startOffset = Math.max(0, diffDays(start, a.assignmentDate));
        const endOffset = Math.min(days - 1, diffDays(start, a.endDate));
        if (endOffset < 0 || startOffset > days - 1) return null;
        return { a, from: startOffset + 1, to: endOffset + 2 };
      })
      .filter(Boolean);

  const loading = schedule.loading || guides.loading || vehicles.loading;
  const error = schedule.error || guides.error || vehicles.error;
  const totalBars = (schedule.data ?? []).length;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Operations</div>
          <h1>Schedule</h1>
          <p className="lede">
            Who is out, where and when. Each bar is one assignment; click it for the booking behind
            it.
          </p>
        </div>
      </div>

      <Card className="card-pad mb-3">
        <div className="row row-gap-3 wrap" style={{ alignItems: 'flex-end' }}>
          <Field label="Starting from">
            <Input type="date" value={start} onChange={(e) => setStart(e.target.value)} />
          </Field>

          <Field label="Span">
            <Select value={spanIdx} onChange={(e) => setSpanIdx(Number(e.target.value))}>
              {SPANS.map((s, i) => (
                <option key={s.label} value={i}>
                  {s.label}
                </option>
              ))}
            </Select>
          </Field>

          <div className="field">
            <label>View</label>
            <div className="chip-row">
              <button
                type="button"
                className={`chip${mode === 'guides' ? ' on' : ''}`}
                onClick={() => setMode('guides')}
              >
                Guides
              </button>
              <button
                type="button"
                className={`chip${mode === 'vehicles' ? ' on' : ''}`}
                onClick={() => setMode('vehicles')}
              >
                Vehicles
              </button>
            </div>
          </div>

          <span className="spacer" />

          <Button variant="outline" onClick={() => setStart(todayISO())}>
            Jump to today
          </Button>
        </div>
        <div className="tiny muted mt-2">
          Showing {dateFmt(start)} → {dateFmt(end)} · {totalBars} assignment
          {totalBars === 1 ? '' : 's'} in range
        </div>
      </Card>

      {loading ? (
        <Skeleton height={340} style={{ borderRadius: 'var(--r-lg)' }} />
      ) : error ? (
        <ErrorState message={error} onRetry={schedule.reload} />
      ) : resources.length === 0 ? (
        <EmptyState
          icon={mode === 'guides' ? '🧑‍🌾' : '🚙'}
          title={`No ${mode} registered`}
          message={`Register ${mode} before they can appear on the schedule.`}
        />
      ) : (
        <div className="cal-scroll">
          <div className="cal">
            <div className="cal-head-row">
              <div className="cal-res">
                <div className="cr-name">{mode === 'guides' ? 'Guide' : 'Vehicle'}</div>
                <div className="cr-sub">{resources.length} total</div>
              </div>
              <div
                className="cal-days"
                style={{ gridTemplateColumns: `repeat(${days}, minmax(0, 1fr))` }}
              >
                {dayCells.map((d, i) => (
                  <div
                    key={d.iso}
                    className={`cal-day-head${d.weekend ? ' weekend' : ''}`}
                    style={{ gridColumn: i + 1 }}
                  >
                    {d.first ? `${d.month} ${d.label}` : d.label}
                    <span className="cdh-dow">{d.dow}</span>
                  </div>
                ))}
              </div>
            </div>

            {resources.map((r) => {
              const bars = barsFor(r.id);
              return (
                <div className="cal-row" key={r.id}>
                  <div className="cal-res">
                    <div className="cr-name">{r.name}</div>
                    <div className="cr-sub">{r.sub}</div>
                  </div>
                  <div
                    className="cal-days"
                    style={{ gridTemplateColumns: `repeat(${days}, minmax(0, 1fr))` }}
                  >
                    {dayCells.map((d, i) => (
                      <div
                        key={d.iso}
                        className={`cal-cell${d.weekend ? ' weekend' : ''}`}
                        style={{ gridColumn: i + 1, gridRow: 1 }}
                      />
                    ))}
                    {bars.map(({ a, from, to }) => (
                      <div
                        key={a.id}
                        className={`cal-bar ${a.status}`}
                        style={{ gridColumn: `${from} / ${to}`, gridRow: 1 }}
                        onClick={() => setDetail(a)}
                        title={`${a.bookingReference} — ${a.packageName}`}
                      >
                        {a.packageName}
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      <div className="chart-legend mt-2">
        <span className="cl-item">
          <span className="cl-swatch" style={{ background: 'var(--forest-600)' }} /> Scheduled
        </span>
        <span className="cl-item">
          <span className="cl-swatch" style={{ background: 'var(--amber-500)' }} /> In progress
        </span>
        <span className="cl-item">
          <span className="cl-swatch" style={{ background: 'var(--ink-400)' }} /> Completed
        </span>
        <span className="cl-item">
          <span className="cl-swatch" style={{ background: 'var(--cream-300)' }} /> Cancelled
        </span>
      </div>

      <Modal
        open={!!detail}
        title={detail ? `Assignment — ${detail.bookingReference}` : ''}
        onClose={() => setDetail(null)}
        footer={
          <Button variant="ghost" onClick={() => setDetail(null)}>
            Close
          </Button>
        }
      >
        {detail && (
          <div className="stack">
            <div className="row row-gap-2">
              <StatusBadge value={detail.status} />
              {detail.manualOverride && (
                <StatusBadge value="OVERRIDE" tone="warning" label="Manual override" />
              )}
            </div>
            <dl className="dl">
              <dt>Safari</dt>
              <dd>{detail.packageName}</dd>
              <dt>Park</dt>
              <dd>{detail.parkName}</dd>
              <dt>Customer</dt>
              <dd>{detail.customerName}</dd>
              <dt>Travellers</dt>
              <dd>{detail.participants}</dd>
              <dt>Dates</dt>
              <dd>
                {dateFmt(detail.assignmentDate)} → {dateFmt(detail.endDate)} ({detail.durationDays}{' '}
                days)
              </dd>
              <dt>Guide</dt>
              <dd>{detail.guideName}</dd>
              <dt>Vehicle</dt>
              <dd>
                {detail.vehicleRegistration} · {detail.vehicleModel} ({detail.vehicleCapacity} seats)
              </dd>
              {detail.assignedByName && (
                <>
                  <dt>Assigned by</dt>
                  <dd>{detail.assignedByName}</dd>
                </>
              )}
            </dl>
            <div>
              <div className="section-title">Park permit</div>
              <PermitPanel bookingId={detail.bookingId} compact />
            </div>

            {detail.notes && (
              <div>
                <div className="section-title">Notes</div>
                <p className="small">{detail.notes}</p>
              </div>
            )}
            {detail.manualOverride && detail.overrideReason && (
              <div className="inline-note warn">
                <span aria-hidden="true">⚠</span>
                <span>
                  <strong>Override reason:</strong> {detail.overrideReason}
                </span>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
