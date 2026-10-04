import { useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { packageApi, parkApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useReady, useReveal } from '../../hooks/useReveal';
import { useAuth } from '../../context/AuthContext';
import { BRAND } from '../../brand';
import { MAP_PARKS, TRIP_STYLES, mapIdForPark, parkPhoto } from '../../config/lanka';
import { Photo } from '../../components/Photo';
import HeroFilm from '../../components/site/HeroFilm';
import SriLankaMap from '../../components/site/SriLankaMap';
import SeasonGuide from '../../components/site/SeasonGuide';
import JourneyCard from '../../components/site/JourneyCard';
import { SkeletonCards } from '../../components/ui';

const PROMISES = [
  ['One specialist', 'A named person plans your trip and stays with it, from first message to last game drive.'],
  ['Tracker-led', 'Every jeep carries a naturalist tracker, not just a driver. In Yala, that is the difference.'],
  ['Both monsoons', 'Two rainy seasons, never at the same time. We plan each park around the one that matters.'],
  ['Permits handled', 'Park entry, jeep slots and fees are confirmed before you land. Nothing to queue for.'],
];

/** Well-known parks lead, in the order the map lists them (Yala first). */
const rank = (pin) => {
  const i = MAP_PARKS.findIndex((p) => p.id === pin);
  return i === -1 ? 99 : i;
};

export default function HomePage() {
  const ready = useReady();
  const { isCustomer } = useAuth();
  const packages = useApi(() => packageApi.list({ activeOnly: true }), []);
  const parks = useApi(() => parkApi.list(true), []);
  const [activePin, setActivePin] = useState('yala');
  const root = useRef(null);

  useReveal(root, [packages.data, parks.data]);

  // Parks with bookable safaris, best-known first. Cards link to the listing filtered by park.
  const destinations = useMemo(() => {
    const list = (parks.data ?? []).filter((p) => p.active && p.packageCount > 0);
    return list
      .map((p) => ({ ...p, pin: mapIdForPark(p.name), photo: parkPhoto(p.name) }))
      .sort((a, b) => Number(!!b.photo) - Number(!!a.photo) || rank(a.pin) - rank(b.pin) || b.packageCount - a.packageCount)
      .slice(0, 4);
  }, [parks.data]);

  const onMap = new Set(destinations.map((d) => d.pin).filter(Boolean));
  const otherPlaces = MAP_PARKS.filter((p) => !onMap.has(p.id));

  // Photographed journeys lead; four is enough for the home page.
  const featured = useMemo(
    () =>
      [...(packages.data ?? [])]
        .sort((a, b) => Number(/^https?:/.test(b.imageUrl ?? '')) - Number(/^https?:/.test(a.imageUrl ?? '')) || b.pricePerPerson - a.pricePerPerson)
        .slice(0, 4),
    [packages.data],
  );

  return (
    <div className={`home${ready ? ' is-ready' : ''}`} ref={root}>
      {/* ------------------------------------------------------------ Hero */}
      <section className="home-hero">
        <div className="home-hero-copy">
          <span className="kicker fade-up">Tailor-made wildlife safaris across Sri Lanka</span>
          <h1 className="hero-title">
            <span className="line">
              <span>Where the</span>
            </span>
            <span className="line">
              <span>
                leopard <em>waits</em>.
              </span>
            </span>
          </h1>
          <p className="hero-lede fade-up d1">
            From the dry-zone scrub of Yala to the lakes of Wilpattu, we plan small-group safaris around the
            island&rsquo;s wild calendar, with one named specialist from your first message to the last game drive.
          </p>
          <div className="hero-ctas fade-up d2">
            <Link className="btn btn-lg" to="/safaris">
              Start planning <span className="arrow">&rarr;</span>
            </Link>
            <Link className="link" to="/#why">
              How we work <span className="arrow">&rarr;</span>
            </Link>
          </div>
          <p className="hero-note fade-up d3">
            <b>Included:</b> park permits and jeep slots, a naturalist tracker on every drive, and on-island support
            day and night. Browse freely; you only need an account when you reserve.
          </p>
        </div>
        <HeroFilm />
      </section>

      <div className="container">
        <div className="promises">
          {PROMISES.map(([title, copy], i) => (
            <div className="promise" key={title} data-reveal style={{ '--i': i }}>
              <h3>{title}</h3>
              <p>{copy}</p>
            </div>
          ))}
        </div>
      </div>

      {/* ------------------------------------------------------------ Destinations */}
      <section className="section" id="destinations">
        <div className="container">
          <div className="sec-head" data-reveal>
            <div>
              <span className="kicker">Destinations</span>
              <h2>
                Where to <em>go</em>.
              </h2>
              <p className="sec-sub">
                National parks on an island you can cross in a day. Point at a park to find it on the map.
              </p>
            </div>
            <Link className="link" to="/safaris">
              All safaris <span className="arrow">&rarr;</span>
            </Link>
          </div>

          <div className="where">
            <div className="map-card" data-reveal>
              <SriLankaMap active={activePin} onPick={setActivePin} />
              <div className="map-legend">
                <span><i />National park</span>
                <span><i className="a" />Selected</span>
              </div>
            </div>

            <div>
              {parks.loading ? (
                <SkeletonCards count={4} />
              ) : (
                <div className="dest-grid">
                  {destinations.map((d, i) => (
                    <Link
                      key={d.id}
                      className="dest zoom-host"
                      to={`/safaris?parkId=${d.id}`}
                      data-reveal
                      style={{ '--i': i }}
                      onMouseEnter={() => d.pin && setActivePin(d.pin)}
                      onFocus={() => d.pin && setActivePin(d.pin)}
                    >
                      <Photo src={d.photo} caption={d.name} alt="" />
                      <span className="caps">{d.location}</span>
                      <h3>{d.name}</h3>
                      <p>{d.description}</p>
                      <span className="count">
                        {d.packageCount} {d.packageCount === 1 ? 'safari' : 'safaris'} <span className="arrow">&rarr;</span>
                      </span>
                    </Link>
                  ))}
                </div>
              )}
              <div className="chip-row mt-3" style={{ paddingTop: 22, borderTop: '1px solid var(--line)' }}>
                {otherPlaces.map((p) => (
                  <Link
                    key={p.id}
                    className={`chip${activePin === p.id ? ' on' : ''}`}
                    to={`/safaris?search=${encodeURIComponent(p.label)}`}
                    onMouseEnter={() => setActivePin(p.id)}
                    onFocus={() => setActivePin(p.id)}
                  >
                    {p.label}
                  </Link>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ Trip styles */}
      <section className="section section-tight">
        <div className="container">
          <div className="sec-head" data-reveal>
            <div>
              <span className="kicker">Experiences</span>
              <h2>
                By the kind of <em>trip</em>.
              </h2>
            </div>
          </div>
          <div className="types">
            {TRIP_STYLES.map((t, i) => {
              const q = t.maxDays ? `maxDays=${t.maxDays}` : `search=${encodeURIComponent(t.search)}`;
              return (
                <Link className="type" key={t.title} to={`/safaris?${q}`} data-reveal style={{ '--i': i % 3 }}>
                  <h3>{t.title}</h3>
                  <span className="caps">{t.parks}</span>
                  <span className="count">
                    Find departures <span className="arrow">&rarr;</span>
                  </span>
                </Link>
              );
            })}
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ Seasons */}
      <section className="section section-ivory" id="seasons">
        <div className="container">
          <div className="sec-head" data-reveal>
            <div>
              <span className="kicker">Seasons</span>
              <h2>
                When to <em>go</em>.
              </h2>
              <p className="sec-sub">
                Two monsoons, never at the same time. Somewhere on the island is always in season — pick a month.
              </p>
            </div>
            <span className="caps hide-m">Indicative · we confirm conditions when you book</span>
          </div>
          <SeasonGuide />
        </div>
      </section>

      {/* ------------------------------------------------------------ Journeys */}
      <section className="section">
        <div className="container">
          <div className="sec-head" data-reveal>
            <div>
              <span className="kicker">Itineraries</span>
              <h2>
                Our most considered <em>journeys</em>.
              </h2>
              <p className="sec-sub">
                Planned by specialists who drive them every season. Choose a date and see live seat counts on the
                next page.
              </p>
            </div>
            <Link className="link" to="/safaris">
              All itineraries <span className="arrow">&rarr;</span>
            </Link>
          </div>
          {packages.loading ? (
            <SkeletonCards count={4} height={260} />
          ) : (
            <div className="journeys">
              {featured.map((p, i) => (
                <JourneyCard key={p.id} pkg={p} index={i} />
              ))}
            </div>
          )}
        </div>
      </section>

      {/* ------------------------------------------------------------ Why */}
      <section className="section section-ivory" id="why">
        <div className="container">
          <div className="editorial" data-reveal>
            <span className="kicker">Why {BRAND.name}</span>
            <h2>
              A quietly excellent way to see <em>Sri Lanka</em>.
            </h2>
            <p>
              We are a small team of naturalists, trackers and planners who live on the island. Every lodge we
              recommend is one we have stayed in; every route is one we have driven, in both monsoons.
            </p>
            <p>
              You pay one price that covers permits, jeeps, trackers and transfers, in rupees or dollars. When a park
              closes or the rain comes early, we move your drives — not your dates.
            </p>
            <div className="quote">
              <div className="stars">Our promise</div>
              <blockquote>
                &ldquo;If the leopard does not show, we go back at dawn. The island keeps its own time, and so do
                we.&rdquo;
              </blockquote>
              <cite>The {BRAND.name} team</cite>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ CTA */}
      <section className="section on-dark cta">
        <div className="container" data-reveal>
          <span className="kicker">Speak to a safari specialist</span>
          <h2>
            Plan a safari, the <em>unhurried</em> way.
          </h2>
          <p>Tell us when, who is coming and what you hope to see. One named specialist takes care of the rest.</p>
          <div className="row">
            <Link className="btn btn-accent btn-lg" to="/safaris">
              Browse safaris <span className="arrow">&rarr;</span>
            </Link>
            {isCustomer ? (
              <Link className="btn btn-outline btn-lg" to="/my-trips">
                My trips
              </Link>
            ) : (
              <Link className="btn btn-outline btn-lg" to="/register">
                Create an account
              </Link>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
