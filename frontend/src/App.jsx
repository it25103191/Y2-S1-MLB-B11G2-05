import { Link, Navigate, Outlet, Route, Routes, useParams } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import ProtectedRoute, { BootGate, homeFor } from './routes/ProtectedRoute';
import { ALL_STAFF, CATALOGUE, OPERATIONS, RELATIONS, FINANCE, MANAGEMENT } from './config/nav';

import SiteLayout from './layouts/SiteLayout';
import StaffLayout from './layouts/StaffLayout';

import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';

import HomePage from './pages/public/HomePage';
import SafarisPage from './pages/public/SafarisPage';
import SafariDetailPage from './pages/public/SafariDetailPage';

import MyBookingsPage from './pages/customer/MyBookingsPage';
import BookingDetailPage from './pages/customer/BookingDetailPage';
import MySupportPage from './pages/customer/MySupportPage';
import PaymentPage from './pages/customer/PaymentPage';
import InvoicePage from './pages/shared/InvoicePage';
import AccountPage from './pages/shared/AccountPage';

import StaffDashboardPage from './pages/staff/StaffDashboardPage';
import StaffBookingsPage from './pages/staff/StaffBookingsPage';
import PackageManagementPage from './pages/staff/PackageManagementPage';
import ParkManagementPage from './pages/staff/ParkManagementPage';
import VehicleManagementPage from './pages/staff/VehicleManagementPage';
import GuideManagementPage from './pages/staff/GuideManagementPage';
import AssignmentsPage from './pages/staff/AssignmentsPage';
import SchedulePage from './pages/staff/SchedulePage';
import ComplaintsPage from './pages/staff/ComplaintsPage';
import ComplaintDetailPage from './pages/staff/ComplaintDetailPage';
import CustomerProfilesPage from './pages/staff/CustomerProfilesPage';
import CustomerProfilePage from './pages/staff/CustomerProfilePage';
import NotificationLogPage from './pages/staff/NotificationLogPage';
import PermitsPage from './pages/staff/PermitsPage';
import PaymentsPage from './pages/staff/PaymentsPage';
import RefundsPage from './pages/staff/RefundsPage';
import KpiTargetsPage from './pages/staff/KpiTargetsPage';
import ReplyTemplatesPage from './pages/staff/ReplyTemplatesPage';
import StaffAccountsPage from './pages/staff/StaffAccountsPage';

import { EmptyState } from './components/ui';

function NotFound() {
  const { user } = useAuth();
  return (
    <div className="page">
      <EmptyState
        icon="🧭"
        title="We could not find that page"
        message="The trail seems to end here. Head back and try another way."
        action={
          <Link className="btn" to={user ? homeFor(user) : '/'}>
            Back to safety
          </Link>
        }
      />
    </div>
  );
}

/** Keeps links from the previous version of the site working. */
function Legacy({ to }) {
  const params = useParams();
  const target = Object.entries(params).reduce((path, [k, v]) => path.replace(`:${k}`, v), to);
  return <Navigate to={target} replace />;
}

const staffOnly = (roles, element) => <ProtectedRoute roles={roles}>{element}</ProtectedRoute>;

export default function App() {
  return (
    <BootGate>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* ------------------------------------------ Public site and customer area */}
        <Route element={<SiteLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/safaris" element={<SafarisPage />} />
          <Route path="/safaris/:id" element={<SafariDetailPage />} />

          <Route
            element={
              <ProtectedRoute roles={['CUSTOMER']}>
                <Outlet />
              </ProtectedRoute>
            }
          >
            <Route path="/my-trips" element={<MyBookingsPage />} />
            <Route path="/my-trips/:id" element={<BookingDetailPage />} />
            <Route path="/my-trips/:id/pay" element={<PaymentPage />} />
            <Route path="/my-support" element={<MySupportPage />} />
            <Route path="/invoices/:bookingId" element={<InvoicePage />} />
            <Route path="/account" element={<AccountPage />} />
          </Route>

          <Route path="/browse" element={<Navigate to="/safaris" replace />} />
          <Route path="/packages/:id" element={<Legacy to="/safaris/:id" />} />
          <Route path="/my-bookings" element={<Navigate to="/my-trips" replace />} />
          <Route path="/my-bookings/:id" element={<Legacy to="/my-trips/:id" />} />
          <Route path="/my-bookings/:id/pay" element={<Legacy to="/my-trips/:id/pay" />} />
          <Route path="*" element={<NotFound />} />
        </Route>

        {/* --------------------------------------------------------- Staff console */}
        <Route
          element={
            <ProtectedRoute roles={ALL_STAFF}>
              <StaffLayout />
            </ProtectedRoute>
          }
        >
          <Route path="/staff" element={<StaffDashboardPage />} />
          <Route path="/staff/targets" element={<KpiTargetsPage />} />
          <Route path="/staff/reply-templates" element={staffOnly(RELATIONS, <ReplyTemplatesPage />)} />
          <Route path="/staff/bookings" element={<StaffBookingsPage />} />
          <Route path="/staff/packages" element={staffOnly(CATALOGUE, <PackageManagementPage />)} />
          <Route path="/staff/parks" element={staffOnly(CATALOGUE, <ParkManagementPage />)} />
          <Route path="/staff/assignments" element={staffOnly(OPERATIONS, <AssignmentsPage />)} />
          <Route path="/staff/schedule" element={staffOnly(OPERATIONS, <SchedulePage />)} />
          <Route path="/staff/vehicles" element={staffOnly(OPERATIONS, <VehicleManagementPage />)} />
          <Route path="/staff/guides" element={staffOnly(OPERATIONS, <GuideManagementPage />)} />
          <Route path="/staff/complaints" element={staffOnly(RELATIONS, <ComplaintsPage />)} />
          <Route path="/staff/complaints/:id" element={staffOnly(RELATIONS, <ComplaintDetailPage />)} />
          <Route path="/staff/customers" element={staffOnly([...RELATIONS, ...FINANCE], <CustomerProfilesPage />)} />
          <Route path="/staff/customers/:id" element={staffOnly([...RELATIONS, ...FINANCE], <CustomerProfilePage />)} />
          <Route path="/staff/notifications" element={<NotificationLogPage />} />
          <Route path="/staff/invoices/:bookingId" element={<InvoicePage />} />
          <Route path="/staff/payments" element={staffOnly(FINANCE, <PaymentsPage />)} />
          <Route path="/staff/refunds" element={staffOnly(FINANCE, <RefundsPage />)} />
          <Route path="/staff/team" element={staffOnly(MANAGEMENT, <StaffAccountsPage />)} />
          <Route path="/staff/account" element={<AccountPage />} />
          <Route
            path="/staff/permits"
            element={staffOnly([...OPERATIONS, 'FINANCE_RESERVATIONS_EXECUTIVE'], <PermitsPage />)}
          />
        </Route>
      </Routes>
    </BootGate>
  );
}
