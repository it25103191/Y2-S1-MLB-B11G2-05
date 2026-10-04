import { Link } from 'react-router-dom';
import { Photo } from '../Photo';
import { useCurrency } from '../../context/CurrencyContext';

const days = (n) => `${n} ${n === 1 ? 'day' : 'days'}`;

/**
 * A safari package as an editorial card. `full` is the two-column itinerary style used on the
 * home page; `compact` is the three-column listing card.
 */
export default function JourneyCard({ pkg, variant = 'full', index = 0 }) {
  const { format } = useCurrency();
  const highlights = (pkg.highlights ?? '').split('|').map((h) => h.trim()).filter(Boolean);
  const to = `/safaris/${pkg.id}`;

  return (
    <article className="journey zoom-host" data-reveal style={{ '--i': index % (variant === 'full' ? 2 : 3) }}>
      <Link to={to} aria-label={pkg.name}>
        <Photo
          src={pkg.imageUrl}
          alt=""
          caption={pkg.parkName}
          badge={!pkg.active ? 'Not bookable' : pkg.maxGroupSize <= 8 ? 'Small group' : null}
        />
      </Link>
      <div className="body">
        <span className="caps meta">
          {days(pkg.durationDays)} · {pkg.parkLocation}
        </span>
        <h3>
          <Link to={to}>{pkg.name}</Link>
        </h3>
        <span className="caps tags">
          {pkg.parkName} · Up to {pkg.maxGroupSize} travellers
        </span>
        {variant === 'full' ? (
          highlights.length > 0 ? (
            <ul>
              {highlights.map((h) => (
                <li key={h}>{h}</li>
              ))}
            </ul>
          ) : (
            <p className="j-desc">{pkg.description}</p>
          )
        ) : (
          <p className="j-desc">{highlights.slice(0, 3).join(' · ') || pkg.description}</p>
        )}
        <div className="journey-foot">
          <div>
            <span className="j-price">{format(pkg.pricePerPerson, { decimals: 0 })}</span>
            <span className="j-price-note">per person · park permits and tracker included</span>
          </div>
          <Link className={variant === 'full' ? 'btn btn-sm' : 'link'} to={to}>
            {variant === 'full' ? 'View itinerary' : 'View'} <span className="arrow">&rarr;</span>
          </Link>
        </div>
      </div>
    </article>
  );
}
