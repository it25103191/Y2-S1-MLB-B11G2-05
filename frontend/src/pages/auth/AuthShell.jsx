import { Link } from 'react-router-dom';
import { BrandLink } from '../../components/Brand';
import CurrencySwitch from '../../components/CurrencySwitch';
import { AUTH_PHOTO } from '../../config/lanka';

const POINTS = [
  'Live seat counts on every departure',
  'Prices in rupees or dollars, your choice',
  'Park permits and trackers arranged for you',
  'One named specialist from first message to last drive',
];

/** Split layout shared by sign-in and registration: photograph left, form right. */
export default function AuthShell({ title, artTitle, artCopy, children }) {
  return (
    <div className="auth-wrap">
      <section className="auth-art">
        <img className="auth-photo" src={AUTH_PHOTO} alt="" />
        <BrandLink />
        <div>
          <h1>{artTitle}</h1>
          <p>{artCopy}</p>
          <div className="auth-points">
            {POINTS.map((p) => (
              <div className="auth-point" key={p}>
                <span className="tick" aria-hidden="true" />
                <span className="auth-point-text">{p}</span>
              </div>
            ))}
          </div>
          <div className="auth-credit">Minneriya · 17:40</div>
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="brandline">
            <Link className="link back-link" to="/">
              <span className="arrow" style={{ transform: 'scaleX(-1)' }}>&rarr;</span> Back to the site
            </Link>
            <CurrencySwitch />
          </div>
          <h1>{title}</h1>
          <div className="mt-3">{children}</div>
        </div>
      </section>
    </div>
  );
}
