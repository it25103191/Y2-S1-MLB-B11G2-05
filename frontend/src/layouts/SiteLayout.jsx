import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { BRAND } from '../brand';
import { BrandLink } from '../components/Brand';
import CurrencySwitch from '../components/CurrencySwitch';
import { Icon } from '../components/Icon';
import { useAuth } from '../context/AuthContext';
import { useCurrency } from '../context/CurrencyContext';
import { useToast } from '../context/ToastContext';
import { useScrolled } from '../hooks/useReveal';

const NAV = [
  { to: '/safaris', label: 'Safaris' },
  { to: '/#destinations', label: 'Destinations' },
  { to: '/#seasons', label: 'When to go' },
  { to: '/#why', label: 'About' },
];

/** What is worth announcing depends on the month: whales, leopards or the Gathering. */
function seasonalNote() {
  const m = new Date().getMonth();
  if (m >= 6 && m <= 9) {
    return { lead: 'The Gathering', where: 'Minneriya & Kaudulla', when: 'Jul – Oct', what: 'up to 300 elephants on one lakeshore', search: 'gathering' };
  }
  if (m >= 10 || m <= 3) {
    return { lead: 'Whale season', where: 'Mirissa', when: 'Nov – Apr', what: 'blue whales a short boat ride offshore', search: 'whales' };
  }
  return { lead: 'Leopard season', where: 'Yala & Wilpattu', when: 'May – Aug', what: 'the dry months bring every cat to the water', search: 'leopards' };
}

function initials(name = '') {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
}

function AccountMenu() {
  const { user, isStaff, logout } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const signOut = () => {
    setOpen(false);
    logout();
    toast.info('You have been signed out.', 'See you soon');
    navigate('/');
  };

  return (
    <div className="account" ref={ref}>
      <button type="button" className="account-btn" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="avatar-sm">{initials(user.fullName)}</span>
        <span className="hide-m">{isStaff ? 'Staff' : 'My account'}</span>
      </button>
      {open && (
        <div className="account-drop" role="menu" onClick={() => setOpen(false)}>
          <div className="who-line">
            <b>{user.fullName}</b>
            <span>{user.email}</span>
          </div>
          {isStaff ? (
            <>
              <Link to="/staff" role="menuitem">Staff console</Link>
              <Link to="/staff/account" role="menuitem">My account</Link>
            </>
          ) : (
            <>
              <Link to="/my-trips" role="menuitem">My trips</Link>
              <Link to="/my-support" role="menuitem">Support</Link>
              <Link to="/account" role="menuitem">My account</Link>
            </>
          )}
          <div className="sep" />
          <button type="button" role="menuitem" onClick={signOut}>
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

/** Scrolls to the top on a new page, or to the element named in the hash. */
function useScrollManager() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (!hash) {
      window.scrollTo({ top: 0 });
      return undefined;
    }
    let tries = 0;
    const id = setInterval(() => {
      const el = document.getElementById(hash.slice(1));
      if (el || tries > 30) {
        clearInterval(id);
        if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 70, behavior: 'smooth' });
      }
      tries += 1;
    }, 60);
    return () => clearInterval(id);
  }, [pathname, hash]);
}

export default function SiteLayout() {
  const { isAuthenticated, isCustomer } = useAuth();
  const { currency, rates } = useCurrency();
  const scrolled = useScrolled(40);
  const location = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const note = seasonalNote();

  useScrollManager();
  useEffect(() => setNavOpen(false), [location.pathname, location.hash]);

  const supportTo = isCustomer ? '/my-support' : '/login';

  return (
    <div className={`site${scrolled ? ' scrolled' : ''}`}>
      <div className="announce">
        <div className="container">
          <span className="announce-msg">
            <span className="pulse" />
            {note.lead} · <span className="hide-m">{note.where} · </span>
            <b>{note.when}</b>
            <span className="hide-m"> — {note.what}</span> ·{' '}
            <Link to={`/safaris?search=${note.search}`}>See departures &rarr;</Link>
          </span>
          <span className="announce-right">
            <Link to={supportTo} state={isCustomer ? undefined : { from: '/my-support' }}>
              Speak to a specialist
            </Link>
          </span>
        </div>
      </div>

      <header className="site-header">
        <div className="container">
          <BrandLink />
          <nav className={`site-nav${navOpen ? ' open' : ''}`} aria-label="Main">
            {NAV.map((item) =>
              item.to.includes('#') ? (
                <Link key={item.to} to={item.to} className="site-nav-link">
                  {item.label}
                </Link>
              ) : (
                <NavLink key={item.to} to={item.to} className={({ isActive }) => `site-nav-link${isActive ? ' active' : ''}`}>
                  {item.label}
                </NavLink>
              ),
            )}
            {isCustomer && (
              <NavLink to="/my-trips" className={({ isActive }) => `site-nav-link${isActive ? ' active' : ''}`}>
                My trips
              </NavLink>
            )}
            <div className="nav-currency">
              <span className="caps">Show prices in</span>
              <CurrencySwitch />
            </div>
          </nav>
          <div className="site-actions">
            <CurrencySwitch />
            {isAuthenticated ? (
              <AccountMenu />
            ) : (
              <Link className="link" to="/login" state={{ from: location.pathname + location.search }}>
                Sign in
              </Link>
            )}
            <Link className="btn btn-sm start-btn" to="/safaris">
              Start planning
            </Link>
            <button
              type="button"
              className="site-burger"
              aria-label="Menu"
              aria-expanded={navOpen}
              onClick={() => setNavOpen((v) => !v)}
            >
              <Icon name={navOpen ? 'close' : 'menu'} />
            </button>
          </div>
        </div>
      </header>

      <main className="site-main">
        <Outlet />
      </main>

      <footer className="site-footer on-dark">
        <div className="container">
          <div className="footer-grid">
            <div>
              <BrandLink />
              <p>{BRAND.blurb}</p>
            </div>
            <div>
              <h5>Safaris</h5>
              <ul>
                <li><Link to="/safaris">All safaris</Link></li>
                <li><Link to="/safaris?search=leopard">Leopard trails</Link></li>
                <li><Link to="/safaris?search=gathering">The Gathering</Link></li>
                <li><Link to="/safaris?maxDays=3">Short breaks</Link></li>
              </ul>
            </div>
            <div>
              <h5>Plan</h5>
              <ul>
                <li><Link to="/#destinations">Destinations</Link></li>
                <li><Link to="/#seasons">When to go</Link></li>
                <li><Link to={isCustomer ? '/my-trips' : '/login'}>My trips</Link></li>
                <li><Link to={supportTo}>Support</Link></li>
              </ul>
            </div>
            <div>
              <h5>Company</h5>
              <ul>
                <li><Link to="/#why">How we work</Link></li>
                <li><Link to="/register">Create an account</Link></li>
                <li><Link to="/login">Staff sign-in</Link></li>
              </ul>
            </div>
          </div>
          <div className="footer-base">
            <span>
              © {new Date().getFullYear()} {BRAND.name}. Prices in {currency}
              {currency === 'LKR' && rates.LKR ? ` at 1 USD = ${Number(rates.LKR).toLocaleString('en-US')} LKR` : ''}.
            </span>
            <span>Photography: Unsplash contributors, free licence</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
