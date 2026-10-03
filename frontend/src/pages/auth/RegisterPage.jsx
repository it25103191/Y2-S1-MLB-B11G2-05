import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { RegisterForm } from '../../components/auth/AuthForms';
import AuthShell from './AuthShell';
import { homeFor } from '../../routes/ProtectedRoute';

export default function RegisterPage() {
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from;

  if (isAuthenticated) return <Navigate to={from ?? homeFor(user)} replace />;

  return (
    <AuthShell
      title={
        <>
          Start your <em>first</em> safari.
        </>
      }
      artTitle="The island, on its own clock."
      artCopy="An account lets you hold seats on a departure, pay in rupees or dollars, and follow every trip from booking to the last game drive."
    >
      <RegisterForm onDone={(u) => navigate(from ?? homeFor(u), { replace: true })} />
      <p className="small muted mt-3 center">
        Already travelling with us?{' '}
        <Link to="/login" state={location.state}>
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
