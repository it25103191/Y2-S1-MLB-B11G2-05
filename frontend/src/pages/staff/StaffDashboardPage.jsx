import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from '../../components/Icon';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { reportApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../../context/AuthContext';
import { TargetProgress, formatMetric } from '../../components/KpiProgress';
import {
  Button,
  Card,
  CardHead,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Skeleton,
  money,
} from '../../components/ui';

/* Palette drawn from the product theme so charts sit inside the brand. */
/** Ceylon Trails categorical palette: canopy, cinnamon, brass, sage, monsoon, laterite, stone. */
const SERIES = ['#203A2F', '#B3703C', '#C99A5B', '#6F8A6A', '#3E6372', '#A64A33', '#9A9180'];

const PRESETS = [
  { label: 'Last 3 months', months: -3, forward: 1 },
  { label: 'Last 6 months', months: -6, forward: 3 },
  { label: 'Last 12 months', months: -12, forward: 6 },
  { label: 'This year', year: true },
];

const iso = (d) => d.toISOString().slice(0, 10);

function presetRange(p) {
  const now = new Date();
  if (p.year) {
    return { from: `${now.getFullYear()}-01-01`, to: `${now.getFullYear()}-12-31` };
  }
  const from = new Date(now.getFullYear(), now.getMonth() + p.months, 1);
  const to = new Date(now.getFullYear(), now.getMonth() + p.forward + 1, 0);
  return { from: iso(from), to: iso(to) };
}

function ChartTip({ active, payload, label, formatter }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tip">
      <div className="ct-label">{label ?? payload[0]?.name}</div>
      {payload.map((p) => (
        <div className="ct-value" key={p.dataKey ?? p.name}>
          {p.name}: {formatter ? formatter(p.value) : p.value}
        </div>
      ))}
    </div>
  );
}

function Kpi({ label, value, sub, icon, tone, progress }) {
  return (
    <Card className={`kpi ${tone ? `kpi-${tone}` : ''}`}>
      <span className="kpi-icon" aria-hidden="true">
        {icon}
      </span>
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      {sub && <div className="kpi-sub">{sub}</div>}
      {progress && (
        <div style={{ marginTop: 8 }}>
          <div className="tiny muted">
            Target {formatMetric(progress.unit, progress.targetValue)} · actual{' '}
            {formatMetric(progress.unit, progress.actualValue)}
          </div>
          <TargetProgress
            percent={progress.percentOfTarget}
            met={progress.met}
            higherIsBetter={progress.higherIsBetter}
          />
        </div>
      )}
    </Card>
  );
}

function ChartSkeleton({ height = 260 }) {
  return (
    <Card className="chart-card">
      <Skeleton width="35%" height={14} />
      <Skeleton width="55%" height={10} style={{ marginTop: 6 }} />
      <Skeleton height={height} style={{ marginTop: 14, borderRadius: 'var(--r-md)' }} />
    </Card>
  );
}

export default function StaffDashboardPage() {
  const { user } = useAuth();
  const [presetIdx, setPresetIdx] = useState(1);
  const [range, setRange] = useState(() => presetRange(PRESETS[1]));

  // Changing the range re-queries and re-renders the charts without a page reload.
  const report = useApi(() => reportApi.dashboard(range.from, range.to), [range.from, range.to]);

  const applyPreset = (i) => {
    setPresetIdx(i);
    setRange(presetRange(PRESETS[i]));
  };

  const setCustom = (key) => (e) => {
    setPresetIdx(-1);
    setRange((r) => ({ ...r, [key]: e.target.value }));
  };

  const d = report.data;

  // Monthly targets keyed by yyyy-MM, merged into the chart series as a dashed line.
  const targetsFor = (metric) =>
    new Map(
      (d?.monthlyTargets ?? [])
        .filter((t) => t.metric === metric)
        .map((t) => [t.key, Number(t.targetValue)]),
    );
  const progressFor = (metric) => (d?.targetProgress ?? []).find((p) => p.metric === metric);

  const bookingSeries = useMemo(() => {
    const targets = targetsFor('BOOKINGS');
    return (d?.bookingsOverTime ?? []).map((p) => ({
      label: p.label,
      Bookings: Number(p.value),
      Cancelled: Number(p.secondary ?? 0),
      Target: targets.get(p.key) ?? null,
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [d]);

  const revenueSeries = useMemo(() => {
    const targets = targetsFor('REVENUE');
    return (d?.revenueTrend ?? []).map((p) => ({
      label: p.label,
      Revenue: Number(p.value),
      Target: targets.get(p.key) ?? null,
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [d]);

  const packageSeries = useMemo(
    () => (d?.popularPackages ?? []).map((p) => ({ name: p.label, value: Number(p.value) })),
    [d],
  );

  const parkSeries = useMemo(
    () =>
      (d?.popularParks ?? []).map((p) => ({
        label: p.label,
        Bookings: Number(p.value),
        Travellers: Number(p.secondary ?? 0),
      })),
    [d],
  );

  const hasBookingData = bookingSeries.some((p) => p.Bookings > 0 || p.Cancelled > 0 || p.Target);
  const hasRevenue = revenueSeries.some((p) => p.Revenue > 0 || p.Target);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Analytics</div>
          <h1>Good to see you, {user.fullName.split(' ')[0]}</h1>
          <p className="lede">
            Performance across the reservation book. Change the date range and every figure and
            chart below re-queries live.
          </p>
        </div>
      </div>

      {/* -------------------------------------------------- Range filter */}
      <Card className="card-pad mb-3">
        <div className="row row-gap-3 wrap" style={{ alignItems: 'flex-end' }}>
          <div className="field">
            <label>Quick range</label>
            <div className="chip-row">
              {PRESETS.map((p, i) => (
                <button
                  key={p.label}
                  type="button"
                  className={`chip${presetIdx === i ? ' on' : ''}`}
                  onClick={() => applyPreset(i)}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>

          <Field label="From">
            <Input type="date" value={range.from} onChange={setCustom('from')} />
          </Field>
          <Field label="To">
            <Input type="date" value={range.to} onChange={setCustom('to')} />
          </Field>

          <span className="spacer" />

          <Button variant="outline" onClick={report.reload} loading={report.loading}>
            ↻ Refresh
          </Button>
        </div>
      </Card>

      {report.error ? (
        <ErrorState message={report.error} onRetry={report.reload} />
      ) : (
        <>
          {/* ------------------------------------------------------ KPIs */}
          <div className="grid grid-4 mb-3">
            {report.loading || !d ? (
              [0, 1, 2, 3].map((i) => (
                <Card key={i} className="kpi">
                  <Skeleton width="60%" height={10} />
                  <Skeleton width="45%" height={26} style={{ marginTop: 8 }} />
                  <Skeleton width="70%" height={9} style={{ marginTop: 8 }} />
                </Card>
              ))
            ) : (
              <>
                <Kpi
                  label="Total bookings"
                  value={d.kpis.totalBookings}
                  sub={`${d.kpis.confirmedBookings} confirmed · ${d.kpis.totalTravellers} travellers`}
                  icon={<Icon name="calendar" />}
                  progress={progressFor('BOOKINGS')}
                />
                <Kpi
                  label="Total revenue"
                  value={money(d.kpis.totalRevenue)}
                  sub={`Avg booking ${money(d.kpis.averageBookingValue)}`}
                  icon={<Icon name="card" />}
                  tone="amber"
                  progress={progressFor('REVENUE')}
                />
                <Kpi
                  label="Active complaints"
                  value={d.kpis.activeComplaints}
                  sub="Open or in progress right now"
                  icon={<Icon name="chat" />}
                  tone={d.kpis.activeComplaints > 0 ? 'danger' : undefined}
                />
                <Kpi
                  label="Upcoming trips"
                  value={d.kpis.upcomingTrips}
                  sub={`Cancellation rate ${d.kpis.cancellationRate}%`}
                  icon={<Icon name="compass" />}
                  tone="terracotta"
                />
              </>
            )}
          </div>

          {/* ------------------------------------------- Against target */}
          {!report.loading && d && (
            <Card className="mb-3">
              <CardHead
                title="Performance against target"
                subtitle="Only months in this range that have a target are counted"
                actions={
                  <Link to="/staff/targets">
                    <Button size="sm" variant="outline">
                      Manage targets
                    </Button>
                  </Link>
                }
              />
              <div className="card-body">
                {d.targetProgress.length === 0 ? (
                  <div className="inline-note">
                    <span aria-hidden="true">🎯</span>
                    <span>No KPI targets fall in this date range. Set some on the KPI Targets screen.</span>
                  </div>
                ) : (
                  <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))' }}>
                    {d.targetProgress.map((p) => (
                      <div key={p.metric}>
                        <div className="row row-gap-2">
                          <div className="strong small grow">{p.metricLabel}</div>
                          <span className={`badge ${p.met ? 'badge-success' : 'badge-warning'}`}>
                            {p.met ? 'On target' : 'Below target'}
                          </span>
                        </div>
                        <div className="kpi-value" style={{ fontSize: '1.35rem', marginTop: 4 }}>
                          {formatMetric(p.unit, p.actualValue)}
                        </div>
                        <div className="tiny muted mb-1">
                          target {p.higherIsBetter ? '' : '≤ '}
                          {formatMetric(p.unit, p.targetValue)} · {p.monthsWithTarget} of {p.monthsInRange} month
                          {p.monthsInRange === 1 ? '' : 's'}
                        </div>
                        <TargetProgress percent={p.percentOfTarget} met={p.met} higherIsBetter={p.higherIsBetter} />
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </Card>
          )}

          {/* --------------------------------------------------- Charts */}
          <div className="grid" style={{ gridTemplateColumns: '1.5fr 1fr', marginBottom: 18 }}>
            {report.loading || !d ? (
              <>
                <ChartSkeleton />
                <ChartSkeleton />
              </>
            ) : (
              <>
                <Card className="chart-card">
                  <h3>Bookings over time</h3>
                  <div className="chart-sub">Departures per month, cancellations shown separately</div>
                  {hasBookingData ? (
                    <ResponsiveContainer width="100%" height={280}>
                      <ComposedChart data={bookingSeries} margin={{ top: 6, right: 8, left: -18, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e8e0d2" vertical={false} />
                        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: '#ddd3c2' }} />
                        <YAxis allowDecimals={false} tickLine={false} axisLine={false} />
                        <Tooltip content={<ChartTip />} cursor={{ fill: 'rgba(31,61,43,0.05)' }} />
                        <Legend iconType="circle" wrapperStyle={{ fontSize: 12, paddingTop: 8 }} />
                        <Bar dataKey="Bookings" fill={SERIES[0]} radius={[1, 1, 0, 0]} maxBarSize={38} />
                        <Bar dataKey="Cancelled" fill={SERIES[1]} radius={[1, 1, 0, 0]} maxBarSize={38} />
                        <Line
                          type="monotone"
                          dataKey="Target"
                          stroke={SERIES[2]}
                          strokeWidth={2}
                          strokeDasharray="6 4"
                          dot={{ r: 3, fill: SERIES[2] }}
                          connectNulls={false}
                        />
                      </ComposedChart>
                    </ResponsiveContainer>
                  ) : (
                    <EmptyState
                      icon="📊"
                      title="No departures in this range"
                      message="Widen the date range to see booking volumes."
                    />
                  )}
                </Card>

                <Card className="chart-card">
                  <h3>Most popular packages</h3>
                  <div className="chart-sub">Share of non-cancelled bookings</div>
                  {packageSeries.length > 0 ? (
                    <>
                      <ResponsiveContainer width="100%" height={230}>
                        <PieChart>
                          <Pie
                            data={packageSeries}
                            dataKey="value"
                            nameKey="name"
                            innerRadius={58}
                            outerRadius={92}
                            paddingAngle={2}
                            stroke="#fff"
                            strokeWidth={2}
                          >
                            {packageSeries.map((entry, i) => (
                              <Cell key={entry.name} fill={SERIES[i % SERIES.length]} />
                            ))}
                          </Pie>
                          <Tooltip content={<ChartTip formatter={(v) => `${v} booking(s)`} />} />
                        </PieChart>
                      </ResponsiveContainer>
                      <div className="chart-legend">
                        {packageSeries.map((entry, i) => (
                          <span className="cl-item" key={entry.name}>
                            <span
                              className="cl-swatch"
                              style={{ background: SERIES[i % SERIES.length] }}
                            />
                            {entry.name} ({entry.value})
                          </span>
                        ))}
                      </div>
                    </>
                  ) : (
                    <EmptyState icon="🥧" title="No bookings" message="Nothing to break down yet." />
                  )}
                </Card>
              </>
            )}
          </div>

          <div className="grid" style={{ gridTemplateColumns: '1.5fr 1fr', marginBottom: 18 }}>
            {report.loading || !d ? (
              <>
                <ChartSkeleton />
                <ChartSkeleton />
              </>
            ) : (
              <>
                <Card className="chart-card">
                  <h3>Revenue trend</h3>
                  <div className="chart-sub">Settled payments per month</div>
                  {hasRevenue ? (
                    <ResponsiveContainer width="100%" height={260}>
                      <LineChart data={revenueSeries} margin={{ top: 6, right: 12, left: 2, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e8e0d2" vertical={false} />
                        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: '#ddd3c2' }} />
                        <YAxis
                          tickLine={false}
                          axisLine={false}
                          width={72}
                          tickFormatter={(v) => `$${Math.round(v / 1000)}k`}
                        />
                        <Tooltip content={<ChartTip formatter={(v) => money(v)} />} />
                        <Line
                          type="monotone"
                          dataKey="Revenue"
                          stroke={SERIES[1]}
                          strokeWidth={2.5}
                          dot={{ r: 3, fill: SERIES[1] }}
                          activeDot={{ r: 6 }}
                        />
                        <Line
                          type="monotone"
                          dataKey="Target"
                          stroke={SERIES[0]}
                          strokeWidth={2}
                          strokeDasharray="6 4"
                          dot={{ r: 3, fill: SERIES[0] }}
                          connectNulls={false}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <EmptyState
                      icon="📈"
                      title="No revenue in this range"
                      message="Settled payments will plot here."
                    />
                  )}
                </Card>

                <Card className="chart-card">
                  <h3>Bookings by park</h3>
                  <div className="chart-sub">Where our travellers went</div>
                  {parkSeries.length > 0 ? (
                    <ResponsiveContainer width="100%" height={260}>
                      <BarChart
                        data={parkSeries}
                        layout="vertical"
                        margin={{ top: 6, right: 16, left: 8, bottom: 0 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke="#e8e0d2" horizontal={false} />
                        <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
                        <YAxis
                          type="category"
                          dataKey="label"
                          width={120}
                          tickLine={false}
                          axisLine={false}
                          tick={{ fontSize: 11 }}
                        />
                        <Tooltip content={<ChartTip />} cursor={{ fill: 'rgba(31,61,43,0.05)' }} />
                        <Bar dataKey="Bookings" radius={[0, 5, 5, 0]} maxBarSize={22}>
                          {parkSeries.map((entry, i) => (
                            <Cell key={entry.label} fill={SERIES[i % SERIES.length]} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  ) : (
                    <EmptyState icon="🌍" title="No park data" message="Nothing booked in this range." />
                  )}
                </Card>
              </>
            )}
          </div>

          {/* --------------------------------------------- Utilisation */}
          <div className="grid grid-2">
            {report.loading || !d ? (
              <>
                <ChartSkeleton height={180} />
                <ChartSkeleton height={180} />
              </>
            ) : (
              <>
                <Card>
                  <CardHead
                    title="Guide utilisation"
                    subtitle="Days deployed inside the selected range"
                    actions={
                      <Link to="/staff/guides">
                        <Button size="sm" variant="outline">
                          Roster
                        </Button>
                      </Link>
                    }
                  />
                  <div className="card-body">
                    {d.guideUtilisation.length === 0 ? (
                      <EmptyState icon="🧑‍🌾" title="No guides" message="Register guides to track utilisation." />
                    ) : (
                      <div className="stack-sm">
                        {d.guideUtilisation.map((g) => (
                          <div key={g.label}>
                            <div className="row row-gap-2">
                              <div className="grow" style={{ minWidth: 0 }}>
                                <div className="small strong truncate">{g.label}</div>
                                <div className="tiny muted truncate">{g.detail}</div>
                              </div>
                              <div className="right nowrap">
                                <div className="small mono-num">{g.daysDeployed}d</div>
                                <div className="tiny muted">{g.assignments} trip(s)</div>
                              </div>
                            </div>
                            <div className="avail-meter" style={{ marginTop: 5 }}>
                              <span
                                style={{
                                  width: `${Math.min(100, Number(g.utilisationPercent))}%`,
                                  background: SERIES[0],
                                }}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </Card>

                <Card>
                  <CardHead
                    title="Vehicle utilisation"
                    subtitle="Days on the road inside the selected range"
                    actions={
                      <Link to="/staff/vehicles">
                        <Button size="sm" variant="outline">
                          Fleet
                        </Button>
                      </Link>
                    }
                  />
                  <div className="card-body">
                    {d.vehicleUtilisation.length === 0 ? (
                      <EmptyState icon="🚙" title="No vehicles" message="Register vehicles to track utilisation." />
                    ) : (
                      <div className="stack-sm">
                        {d.vehicleUtilisation.map((v) => (
                          <div key={v.label}>
                            <div className="row row-gap-2">
                              <div className="grow" style={{ minWidth: 0 }}>
                                <div className="small strong truncate mono-num">{v.label}</div>
                                <div className="tiny muted truncate">{v.detail}</div>
                              </div>
                              <div className="right nowrap">
                                <div className="small mono-num">{v.daysDeployed}d</div>
                                <div className="tiny muted">{v.assignments} trip(s)</div>
                              </div>
                            </div>
                            <div className="avail-meter" style={{ marginTop: 5 }}>
                              <span
                                style={{
                                  width: `${Math.min(100, Number(v.utilisationPercent))}%`,
                                  background: SERIES[1],
                                }}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </Card>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
