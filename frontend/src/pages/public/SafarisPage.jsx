import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { packageApi, parkApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useReveal } from '../../hooks/useReveal';
import { useCurrency } from '../../context/CurrencyContext';
import { TRIP_STYLES } from '../../config/lanka';
import JourneyCard from '../../components/site/JourneyCard';
import { Button, EmptyState, ErrorState, Field, Input, Select, SkeletonCards } from '../../components/ui';

const DURATIONS = [
  { value: '', label: 'Any length' },
  { value: '1-3', label: 'Short break · up to 3 days' },
  { value: '4-5', label: '4 to 5 days' },
  { value: '6-30', label: '6 days or more' },
];

/** Budget bands are stored in USD; the labels follow the visitor's currency. */
const BUDGETS = [200, 300, 400, 500];

const SORTS = [
  { value: 'recommended', label: 'Recommended' },
  { value: 'price-asc', label: 'Price, low to high' },
  { value: 'price-desc', label: 'Price, high to low' },
  { value: 'days-asc', label: 'Shortest first' },
  { value: 'days-desc', label: 'Longest first' },
];

export default function SafarisPage() {
  const [params, setParams] = useSearchParams();
  const { format } = useCurrency();
  const root = useRef(null);

  const search = params.get('search') ?? '';
  const parkId = params.get('parkId') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const sort = params.get('sort') ?? 'recommended';
  const duration = params.get('maxDays') && !params.get('minDays') ? `1-${params.get('maxDays')}` : params.get('days') ?? '';
  const [minDays, maxDays] = duration ? duration.split('-') : ['', ''];

  const [draft, setDraft] = useState(search);
  useEffect(() => setDraft(search), [search]);

  const update = (patch) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v === '' || v == null ? next.delete(k) : next.set(k, v)));
    setParams(next, { replace: true });
  };

  // Search as the visitor types, once they pause.
  useEffect(() => {
    if (draft === search) return undefined;
    const t = setTimeout(() => update({ search: draft.trim() }), 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  const parks = useApi(() => parkApi.list(true), []);
  const packages = useApi(
    () => packageApi.list({ activeOnly: true, search, parkId, maxPrice, minDays, maxDays }),
    [search, parkId, maxPrice, minDays, maxDays],
  );

  const results = useMemo(() => {
    const list = [...(packages.data ?? [])];
    const by = {
      'price-asc': (a, b) => a.pricePerPerson - b.pricePerPerson,
      'price-desc': (a, b) => b.pricePerPerson - a.pricePerPerson,
      'days-asc': (a, b) => a.durationDays - b.durationDays,
      'days-desc': (a, b) => b.durationDays - a.durationDays,
      recommended: (a, b) =>
        Number(/^https?:/.test(b.imageUrl ?? '')) - Number(/^https?:/.test(a.imageUrl ?? '')) || a.name.localeCompare(b.name),
    }[sort];
    return by ? list.sort(by) : list;
  }, [packages.data, sort]);

  useReveal(root, [results]);

  const filtered = search || parkId || maxPrice || duration;
  const park = (parks.data ?? []).find((p) => String(p.id) === parkId);

  return (
    <div className="container" ref={root}>
      <header className="listing-head">
        <span className="kicker">Safaris</span>
        <h1>
          {park ? (
            <>
              Safaris in <em>{park.name.replace(/ National Park$/, '')}</em>.
            </>
          ) : (
            <>
              Every <em>departure</em>, in one place.
            </>
          )}
        </h1>
        <p className="sec-sub">
          {park
            ? park.description
            : 'Small groups, tracker-led drives and park permits included. Pick a safari to see live seat counts for your dates.'}
        </p>
      </header>

      <div className="listing-filters">
        <Field label="Search">
          <Input
            type="search"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Leopards, Yala, elephants…"
            aria-label="Search safaris"
          />
        </Field>
        <Field label="Park">
          <Select value={parkId} onChange={(e) => update({ parkId: e.target.value })}>
            <option value="">All parks</option>
            {(parks.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Length">
          <Select value={duration} onChange={(e) => update({ days: e.target.value, maxDays: '', minDays: '' })}>
            {DURATIONS.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Budget per person">
          <Select value={maxPrice} onChange={(e) => update({ maxPrice: e.target.value })}>
            <option value="">Any budget</option>
            {BUDGETS.map((b) => (
              <option key={b} value={b}>
                Up to {format(b, { decimals: 0 })}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      <div className="chip-row">
        {TRIP_STYLES.filter((t) => t.search).map((t) => (
          <button
            key={t.title}
            type="button"
            className={`chip${search.toLowerCase() === t.search ? ' on' : ''}`}
            onClick={() => update({ search: search.toLowerCase() === t.search ? '' : t.search })}
          >
            {t.title}
          </button>
        ))}
      </div>

      <div className="listing-bar mt-2">
        <span className="count">
          {packages.loading ? 'Finding departures…' : `${results.length} ${results.length === 1 ? 'safari' : 'safaris'}`}
        </span>
        <div className="row row-gap-2">
          {filtered && (
            <button type="button" className="link" onClick={() => setParams({}, { replace: true })}>
              Clear filters
            </button>
          )}
          <Select value={sort} onChange={(e) => update({ sort: e.target.value === 'recommended' ? '' : e.target.value })} aria-label="Sort" style={{ width: 'auto' }}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div style={{ paddingBottom: 96 }}>
        {packages.loading ? (
          <SkeletonCards count={6} height={240} />
        ) : packages.error ? (
          <ErrorState message={packages.error} onRetry={packages.reload} />
        ) : results.length === 0 ? (
          <EmptyState
            icon="🍃"
            title="Nothing matches those filters"
            message="Try a different park or a wider budget. New departures are added every season."
            action={<Button variant="outline" onClick={() => setParams({}, { replace: true })}>Show all safaris</Button>}
          />
        ) : (
          <div className="journeys three">
            {results.map((p, i) => (
              <JourneyCard key={p.id} pkg={p} index={i} variant="compact" />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
