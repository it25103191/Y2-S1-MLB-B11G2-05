import { Link } from 'react-router-dom';
import { BRAND } from '../brand';

const SPOTS = [0, 1, 2, 3, 4].map((k) => {
  const a = ((k * 72 - 90) * Math.PI) / 180;
  return { cx: 20 + 8.6 * Math.cos(a), cy: 20 + 8.6 * Math.sin(a), rot: k * 72 };
});

/** The leopard-rosette mark. Inherits its colour from `color`. */
export function BrandMark({ className = '' }) {
  return (
    <svg className={`brand-mark ${className}`.trim()} viewBox="0 0 40 40" aria-hidden="true">
      <circle cx="20" cy="20" r="18.3" fill="none" stroke="currentColor" strokeWidth="1.4" />
      <g className="spots">
        {SPOTS.map((s) => (
          <ellipse
            key={s.rot}
            cx={s.cx}
            cy={s.cy}
            rx="3.9"
            ry="2.3"
            transform={`rotate(${s.rot} ${s.cx} ${s.cy})`}
            fill="currentColor"
          />
        ))}
      </g>
    </svg>
  );
}

/** Mark plus wordmark, linking home. */
export function BrandLink({ to = '/', sub = BRAND.tagline, className = '' }) {
  return (
    <Link to={to} className={`brand-link ${className}`.trim()} aria-label={`${BRAND.name} home`}>
      <BrandMark />
      <span className="brand-text">
        <span className="brand-name">{BRAND.name}</span>
        {sub && <span className="brand-sub">{sub}</span>}
      </span>
    </Link>
  );
}
