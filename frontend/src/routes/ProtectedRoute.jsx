import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { EmptyState } from '../components/ui';
import { BrandMark } from '../components/Brand';

/** Where each role lands after signing in when there is nowhere better to return to. */
export function homeFor(user) {
  if (!user) return '/';
  return user.role === 'CUSTOMER' ? '/my-trips' : '/staff';
}

export function BootGate({ children }) {
  const { booting } = useAuth();
  if (booting) {
    return (
      <div className="boot">
        <div className="boot-inner">
          <BrandMark />
          <div className="caps">Restoring your session</div>
        </div>
      </div>
    );
  }
  return children;
}

/**
 * Guards a branch of the router.
 * `roles` (optional) restricts the branch to specific roles.
 */
export default function ProtectedRoute({ roles, children }) {
  const { user, isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  if (roles && !roles.includes(user.role)) {
    // Signed in but on the wrong side of the product: send them to their own home.
    if (roles.includes('CUSTOMER') !== (user.role === 'CUSTOMER')) {
      return <Navigate to={homeFor(user)} replace />;
    }
    return (
      <div className="page">
        <EmptyState
          icon="🔒"
          title="Not available for your role"
          message={`Your role (${user.roleLabel ?? user.role}) does not have access to this screen. Use the navigation to reach the tools assigned to you.`}
        />
      </div>
    );
  }

  return children;
}
