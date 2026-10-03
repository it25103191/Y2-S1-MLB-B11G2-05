import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { BRAND } from '../brand';
import { BrandLink } from '../components/Brand';
import { Icon } from '../components/Icon';
import { ROLE_LABELS, useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { navForRole } from '../config/nav';

function initials(name = '') {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
}

export default function StaffLayout() {
  const { user, logout } = useAuth();
  const toast = useToast();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);

  const groups = navForRole(user.role);

  // Close the drawer whenever the route changes on small screens.
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  const current = groups
    .flatMap((g) => g.items)
    .filter((i) => (i.end ? location.pathname === i.to : location.pathname.startsWith(i.to)))
    .sort((a, b) => b.to.length - a.to.length)[0];

  const handleLogout = () => {
    logout();
    toast.info('You have been signed out.', 'Session ended');
  };

  return (
    <div className="shell">
      {mobileOpen && <div className="scrim-mobile" onClick={() => setMobileOpen(false)} />}

      <aside className={`sidebar${mobileOpen ? ' open' : ''}`}>
        <div className="sidebar-brand">
          <BrandLink to="/staff" sub={BRAND.staffTagline} />
        </div>

        <nav className="nav">
          {groups.map((group) => (
            <div className="nav-group" key={group.label}>
              <div className="nav-group-label">{group.label}</div>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
                >
                  <span className="nav-icon">
                    <Icon name={item.icon} size={17} />
                  </span>
                  <span>{item.label}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-foot">
          <NavLink
            to="/staff/account"
            className={({ isActive }) => `who who-link${isActive ? ' active' : ''}`}
            title="My account"
          >
            <div className="avatar">{initials(user.fullName)}</div>
            <div className="who-text">
              <div className="who-name truncate">{user.fullName}</div>
              <div className="who-role truncate">{ROLE_LABELS[user.role] ?? user.role}</div>
            </div>
            <Icon name="user" size={15} />
          </NavLink>
          <button type="button" className="logout-btn" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </aside>

      <div className="shell-main">
        <header className="topbar">
          <button
            type="button"
            className="burger"
            onClick={() => setMobileOpen((v) => !v)}
            aria-label="Toggle navigation"
          >
            <Icon name="menu" />
          </button>
          <div>
            <div className="crumb">{BRAND.name}</div>
            <h2>{location.pathname === '/staff/account' ? 'My account' : current?.label ?? 'Staff console'}</h2>
          </div>
          <div className="topbar-actions">
            <Link className="link" to="/" target="_blank" rel="noreferrer">
              View site <Icon name="external" size={13} />
            </Link>
          </div>
        </header>

        <main>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
