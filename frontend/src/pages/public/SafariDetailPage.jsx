import { useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { bookingApi, packageApi, parkApi } from '../../api/api';
import { errorMessage } from '../../api/client';
import { useApi } from '../../hooks/useApi';
import { useReveal } from '../../hooks/useReveal';
import { useAuth } from '../../context/AuthContext';
import { useCurrency } from '../../context/CurrencyContext';
import { useToast } from '../../context/ToastContext';
import { Photo } from '../../components/Photo';
import AuthGate from '../../components/auth/AuthGate';
import JourneyCard from '../../components/site/JourneyCard';
import { Button, ErrorState, Field, Input, Skeleton, SkeletonText, Textarea, dateFmt, isoPlusDays, relativeDays } from '../../components/ui';

const days = (n) => `${n} ${n === 1 ? 'day' : 'days'}`;

function BookCard({ pkg }) {
  const navigate = useNavigate();
  const { isAuthenticated, isCustomer, user } = useAuth();
  const { format, formatUsd, currency, rate } = useCurrency();
  const toast = useToast();

  const [tripDate, setTripDate] = useState(isoPlusDays(30));
  const [participants, setParticipants] = useState(Math.min(2, pkg.maxGroupSize));
  const [requests, setRequests] = useState('');
  const [noteOpen, setNoteOpen] = useState(false);
  const [gateOpen, setGateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState(null);

  const quote = useApi(
    () => (tripDate ? packageApi.quote(pkg.id, tripDate, participants) : Promise.resolve(null)),
    [pkg.id, tripDate, participants],
  );
  const q = quote.data;

  const total = q ? Number(q.totalPrice) : Number(pkg.pricePerPerson) * participants;
  const remaining = q?.seatsRemaining ?? pkg.maxGroupSize;
  const takenAfter = pkg.maxGroupSize - remaining + Math.min(participants, remaining);
  const fill = Math.min(100, Math.round((takenAfter / pkg.maxGroupSize) * 100));
  const tone = remaining === 0 || (q && !q.available) ? 'full' : remaining - participants <= 2 ? 'low' : '';

  const reserve = async () => {
    setSubmitting(true);
    try {
      const booking = await bookingApi.create({
        packageId: pkg.id,
        tripDate,
        participants,
        specialRequests: requests.trim() || null,
      });
      setCreated(booking);
      toast.success(`Reference ${booking.bookingReference}. Your seats are held.`, 'Reserved');
    } catch (err) {
      toast.error(errorMessage(err), 'Could not reserve');
      quote.reload();
    } finally {
      setSubmitting(false);
    }
  };

  const onReserve = () => {
    if (!isAuthenticated) setGateOpen(true);
    else if (isCustomer) reserve();
  };

  const onAuthenticated = (u) => {
    setGateOpen(false);
    if (u.role === 'CUSTOMER') reserve();
    else toast.warning('Staff accounts manage bookings from the console rather than making them.', 'Signed in as staff');
  };

  if (created) {
    return (
      <div className="book-card stack">
        <span className="kicker" style={{ color: 'var(--success)', marginBottom: 0 }}>Seats held</span>
        <h3 style={{ fontSize: '1.8rem', fontWeight: 400 }}>
          You&rsquo;re going to <em>{pkg.parkName.replace(/ National Park$/, '')}</em>.
        </h3>
        <dl className="dl">
          <dt>Reference</dt>
          <dd className="mono-num">{created.bookingReference}</dd>
          <dt>Departs</dt>
          <dd>{dateFmt(created.tripDate)}</dd>
          <dt>Travellers</dt>
          <dd>{created.participants}</dd>
          <dt>Total</dt>
          <dd>{format(created.totalPrice)}</dd>
          <dt>Pay by</dt>
          <dd>{dateFmt(created.paymentDueDate)}</dd>
        </dl>
        <div className="inline-note">
          <span>Your seats are held as pending. The trip is confirmed once it is paid in full.</span>
        </div>
        <Button className="btn-block btn-lg" onClick={() => navigate(`/my-trips/${created.id}/pay`)}>
          Pay now <span className="arrow">&rarr;</span>
        </Button>
        <Link className="btn btn-outline btn-block" to={`/my-trips/${created.id}`}>
          View my trip
        </Link>
      </div>
    );
  }

  return (
    <div className="book-card">
      <div className="from">
        <div>
          <span className="caps">From</span>
          <div className="j-price">{format(pkg.pricePerPerson, { decimals: 0 })}</div>
        </div>
        <span className="caps">per person</span>
      </div>

      <Field label="Departure date">
        <Input type="date" value={tripDate} min={isoPlusDays(1)} onChange={(e) => setTripDate(e.target.value)} />
      </Field>
      <Field label="Travellers" hint={`Up to ${pkg.maxGroupSize} on each departure.`}>
        <div className="stepper">
          <button type="button" aria-label="Fewer travellers" disabled={participants <= 1} onClick={() => setParticipants((n) => Math.max(1, n - 1))}>
            &minus;
          </button>
          <output aria-live="polite">{participants}</output>
          <button type="button" aria-label="More travellers" disabled={participants >= pkg.maxGroupSize} onClick={() => setParticipants((n) => Math.min(pkg.maxGroupSize, n + 1))}>
            +
          </button>
        </div>
      </Field>

      <div className="avail">
        {quote.loading && !q ? (
          <Skeleton height={3} />
        ) : (
          <div className="track">
            <span className={tone} style={{ width: `${fill}%` }} />
          </div>
        )}
        <p className={q && !q.available ? 'warn' : ''}>
          {q ? q.message : 'Checking seats…'}
          {q?.available && tripDate ? ` Departs ${relativeDays(tripDate)}.` : ''}
        </p>
      </div>

      {noteOpen ? (
        <Field label="Anything we should know?">
          <Textarea rows={2} value={requests} onChange={(e) => setRequests(e.target.value)} placeholder="Dietary needs, mobility, a birthday on the trip…" />
        </Field>
      ) : (
        <button type="button" className="link-btn small" onClick={() => setNoteOpen(true)}>
          Add a note for your specialist
        </button>
      )}

      <div className="price-lines">
        <div className="price-line">
          <span>
            {participants} {participants === 1 ? 'traveller' : 'travellers'} × {format(pkg.pricePerPerson, { decimals: 0 })}
          </span>
          <span>{format(total, { decimals: 0 })}</span>
        </div>
        <div className="price-line">
          <span>Park permits and tracker</span>
          <span>Included</span>
        </div>
        <div className="price-line total">
          <span>Total</span>
          <span>{format(total)}</span>
        </div>
        {currency !== 'USD' && (
          <div className="alt-currency">
            ≈ {formatUsd(total)} · 1 USD = {rate.toLocaleString('en-US')} {currency}
          </div>
        )}
      </div>

      {isAuthenticated && !isCustomer ? (
        <div className="inline-note warn mt-2">
          <span>
            You are signed in as {user.roleLabel}. Travellers make bookings; you can manage them from the{' '}
            <Link to="/staff/bookings">console</Link>.
          </span>
        </div>
      ) : (
        <Button className="btn-block btn-lg" loading={submitting} disabled={!q?.available || quote.loading} onClick={onReserve}>
          {q && !q.available ? 'Not available on this date' : isAuthenticated ? 'Reserve these seats' : 'Reserve — sign in to continue'}
        </Button>
      )}
      <p className="small-print">No payment is taken to reserve. Pay by card online, or in person in LKR or USD.</p>

      <AuthGate
        open={gateOpen}
        onClose={() => setGateOpen(false)}
        onDone={onAuthenticated}
        intro={
          <div className="gate-intro">
            <Photo src={pkg.imageUrl} caption="" />
            <div>
              <b>{pkg.name}</b>
              <span>
                {dateFmt(tripDate)} · {participants} {participants === 1 ? 'traveller' : 'travellers'} · {format(total)}
              </span>
            </div>
          </div>
        }
      />
    </div>
  );
}

export default function SafariDetailPage() {
  const { id } = useParams();
  const { format } = useCurrency();
  const pkg = useApi(() => packageApi.get(id), [id]);
  const parks = useApi(() => parkApi.list(false), []);
  const siblings = useApi(
    () => (pkg.data ? packageApi.list({ activeOnly: true, parkId: pkg.data.parkId }) : Promise.resolve([])),
    [pkg.data?.parkId],
  );
  const root = useRef(null);
  useReveal(root, [siblings.data]);

  const park = useMemo(() => (parks.data ?? []).find((p) => p.id === pkg.data?.parkId), [parks.data, pkg.data]);
  const others = (siblings.data ?? []).filter((p) => String(p.id) !== String(id)).slice(0, 3);

  if (pkg.loading) {
    return (
      <>
        <Skeleton height="60vh" style={{ borderRadius: 0 }} />
        <div className="container detail-body">
          <SkeletonText lines={8} />
          <Skeleton height={420} />
        </div>
      </>
    );
  }

  if (pkg.error) {
    return (
      <div className="container" style={{ padding: '64px 0' }}>
        <ErrorState message={pkg.error} onRetry={pkg.reload} />
      </div>
    );
  }

  const p = pkg.data;
  const highlights = (p.highlights ?? '').split('|').map((h) => h.trim()).filter(Boolean);

  return (
    <div ref={root}>
      <section className="cover">
        <Photo src={p.imageUrl} alt="" caption={p.parkName} eager />
        <div className="container">
          <div className="crumbs">
            <Link to="/safaris">Safaris</Link> / <Link to={`/safaris?parkId=${p.parkId}`}>{p.parkName}</Link>
          </div>
          <h1>{p.name}</h1>
          <div className="cover-facts">
            <div>
              <span className="caps">Length</span>
              <b>{days(p.durationDays)}</b>
            </div>
            <div>
              <span className="caps">From</span>
              <b>{format(p.pricePerPerson, { decimals: 0 })}</b>
            </div>
            <div>
              <span className="caps">Group</span>
              <b>Up to {p.maxGroupSize}</b>
            </div>
            <div>
              <span className="caps">Region</span>
              <b>{p.parkLocation}</b>
            </div>
          </div>
        </div>
      </section>

      <div className="container detail-body">
        <div>
          {!p.active && (
            <div className="inline-note warn mb-3">
              <span>This safari is not open for new bookings at the moment.</span>
            </div>
          )}
          <p className="detail-lede">{p.description}</p>

          {highlights.length > 0 && (
            <div className="detail-block">
              <span className="kicker">Highlights</span>
              <ul className="highlight-list">
                {highlights.map((h) => (
                  <li key={h}>{h}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="detail-block">
            <span className="kicker">Included</span>
            <h2>
              One price, <em>everything</em> arranged.
            </h2>
            <div className="included">
              <div>
                <b>Park permits</b>
                <span>Requested and approved before you land, for every traveller.</span>
              </div>
              <div>
                <b>A tracker</b>
                <span>A naturalist guide and a dedicated jeep on every drive.</span>
              </div>
              <div>
                <b>A specialist</b>
                <span>One named person looking after your trip from start to finish.</span>
              </div>
            </div>
          </div>

          {park && (
            <div className="detail-block">
              <span className="kicker">The park</span>
              <h2>{park.name}</h2>
              <p className="muted" style={{ maxWidth: '62ch' }}>{park.description}</p>
            </div>
          )}

          <div className="detail-block">
            <span className="kicker">How it works</span>
            <ol className="how-steps">
              <li>
                <div>
                  <b>Reserve your seats</b>
                  <span>Pick a date and your group. Nothing is charged; the seats are simply held for you.</span>
                </div>
              </li>
              <li>
                <div>
                  <b>Pay in rupees or dollars</b>
                  <span>By card online, or by cash or bank transfer with our team. Part payments are fine.</span>
                </div>
              </li>
              <li>
                <div>
                  <b>We arrange the rest</b>
                  <span>Your permits, tracker and jeep are confirmed, and you can follow it all under My trips.</span>
                </div>
              </li>
              <li>
                <div>
                  <b>Plans change</b>
                  <span>Cancel more than 7 days before departure for a full refund; 2 to 7 days, half.</span>
                </div>
              </li>
            </ol>
          </div>
        </div>

        <aside className="book-panel">
          {p.active ? (
            <BookCard pkg={p} />
          ) : (
            <div className="book-card">
              <p className="muted">This departure is closed. Browse other safaris in {p.parkName}.</p>
            </div>
          )}
        </aside>
      </div>

      {others.length > 0 && (
        <section className="section section-ivory">
          <div className="container">
            <div className="sec-head">
              <div>
                <span className="kicker">Also in {p.parkName}</span>
                <h2>
                  More from this <em>park</em>.
                </h2>
              </div>
              <Link className="link" to={`/safaris?parkId=${p.parkId}`}>
                See all <span className="arrow">&rarr;</span>
              </Link>
            </div>
            <div className="journeys three">
              {others.map((o, i) => (
                <JourneyCard key={o.id} pkg={o} index={i} variant="compact" />
              ))}
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
