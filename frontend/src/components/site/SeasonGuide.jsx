import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { MONTHS, SEASONS } from '../../config/lanka';

function Rating({ value }) {
  return (
    <span className="rating" aria-label={`${value} out of 5`}>
      {[0, 1, 2, 3, 4].map((i) => (
        <i key={i} className={i < value ? 'f' : ''} />
      ))}
    </span>
  );
}

/** Month picker over the island's two-monsoon calendar. Opens on the current month. */
export default function SeasonGuide() {
  const now = new Date().getMonth();
  const [month, setMonth] = useState(now);
  const [shown, setShown] = useState(now);
  const [swapping, setSwapping] = useState(false);
  const buttons = useRef([]);
  const [ind, setInd] = useState({ left: 0, width: 0 });

  const measure = () => {
    const b = buttons.current[month];
    if (b) setInd({ left: b.offsetLeft, width: b.offsetWidth });
  };

  useLayoutEffect(measure, [month]);
  useEffect(() => {
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  });

  // Fade the old month out before the new one is painted in.
  useEffect(() => {
    if (month === shown) return undefined;
    setSwapping(true);
    const t = setTimeout(() => {
      setShown(month);
      setSwapping(false);
    }, 280);
    return () => clearTimeout(t);
  }, [month, shown]);

  const s = SEASONS[shown];

  return (
    <>
      <div className="months" role="tablist" aria-label="Month">
        {MONTHS.map((m, i) => (
          <button
            key={m}
            type="button"
            role="tab"
            aria-selected={i === month}
            ref={(el) => {
              buttons.current[i] = el;
            }}
            className={i === month ? 'on' : ''}
            onClick={() => setMonth(i)}
          >
            {m}
            <span className="now">{i === now ? 'now' : ''}</span>
          </button>
        ))}
        <span className="month-ind" style={{ width: ind.width, transform: `translateX(${ind.left}px)` }} />
      </div>
      <div className={`season${swapping ? ' out' : ''}`} role="tabpanel">
        <div className="season-copy">
          {/* Titles are our own static copy with a single <em>. */}
          <h3 dangerouslySetInnerHTML={{ __html: s.title }} />
          <p>{s.copy}</p>
          <div className="monsoon">
            <span className="bar">
              <span style={{ width: `${s.rain}%` }} />
            </span>
            <span>{s.monsoon}</span>
          </div>
          <Link className="link mt-3" to="/safaris" style={{ marginTop: 26 }}>
            Safaris for {MONTHS[shown]} <span className="arrow">&rarr;</span>
          </Link>
        </div>
        <ul className="picks">
          {s.picks.map(([name, why, rating]) => (
            <li key={name}>
              <div>
                <span className="name">{name}</span>
                <span className="why">{why}</span>
              </div>
              {rating === 0 ? <span className="closed">Closed</span> : <Rating value={rating} />}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
