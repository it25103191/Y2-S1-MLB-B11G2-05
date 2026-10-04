import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { SignInForm } from '../../components/auth/AuthForms';
import AuthShell from './AuthShell';
import { homeFor } from '../../routes/ProtectedRoute';

export default function LoginPage() {
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from;

  if (isAuthenticated) {
    return <Navigate to={from ?? homeFor(user)} replace />;
  }

  return (
    <AuthShell
      title={
        <>
          Welcome <em>back</em>.
        </>
      }
      artTitle="Where the leopard waits."
      artCopy="Sign in to see your trips, pay a balance or message your specialist. Staff sign in here for the operations console."
    >
      <SignInForm onDone={(u) => navigate(from ?? homeFor(u), { replace: true })} />
      <p className="small muted mt-3 center">
        New to Ceylon Trails?{' '}
        <Link to="/register" state={location.state}>
          Create an account
        </Link>
      </p>
    </AuthShell>
  );
}
