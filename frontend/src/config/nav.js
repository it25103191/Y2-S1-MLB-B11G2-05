/**
 * Staff sidebar definition. `roles` lists who may see each entry; the matching
 * route guards enforce the same rule server-side and in the router.
 */

const ALL_STAFF = [
  'OPERATIONS_MANAGER',
  'CUSTOMER_RELATIONS_OFFICER',
  'SAFARI_VEHICLE_COORDINATOR',
  'FINANCE_RESERVATIONS_EXECUTIVE',
  'FINANCE_ACCOUNTS_OFFICER',
];

const CATALOGUE = ['OPERATIONS_MANAGER', 'FINANCE_RESERVATIONS_EXECUTIVE'];
const OPERATIONS = ['OPERATIONS_MANAGER', 'SAFARI_VEHICLE_COORDINATOR'];
const RELATIONS = ['CUSTOMER_RELATIONS_OFFICER', 'OPERATIONS_MANAGER'];
const FINANCE = ['FINANCE_ACCOUNTS_OFFICER', 'FINANCE_RESERVATIONS_EXECUTIVE'];
const MANAGEMENT = ['OPERATIONS_MANAGER'];

export const NAV_GROUPS = [
  {
    label: 'Overview',
    items: [
      { to: '/staff', label: 'Dashboard', icon: 'dashboard', roles: ALL_STAFF, end: true },
      { to: '/staff/targets', label: 'KPI Targets', icon: 'target', roles: ALL_STAFF },
    ],
  },
  {
    label: 'Reservations',
    items: [
      { to: '/staff/bookings', label: 'Bookings', icon: 'calendar', roles: ALL_STAFF },
      { to: '/staff/packages', label: 'Safari Packages', icon: 'package', roles: CATALOGUE },
      { to: '/staff/parks', label: 'Parks', icon: 'park', roles: CATALOGUE },
    ],
  },
  {
    label: 'Operations',
    items: [
      { to: '/staff/assignments', label: 'Assignments', icon: 'compass', roles: OPERATIONS },
      { to: '/staff/schedule', label: 'Schedule', icon: 'schedule', roles: OPERATIONS },
      { to: '/staff/vehicles', label: 'Vehicles', icon: 'car', roles: OPERATIONS },
      { to: '/staff/guides', label: 'Guides', icon: 'guide', roles: OPERATIONS },
      { to: '/staff/permits', label: 'Permits', icon: 'permit', roles: [...OPERATIONS, 'FINANCE_RESERVATIONS_EXECUTIVE'] },
    ],
  },
  {
    label: 'Customers',
    items: [
      { to: '/staff/complaints', label: 'Complaints', icon: 'chat', roles: RELATIONS },
      { to: '/staff/customers', label: 'Customer Profiles', icon: 'users', roles: [...RELATIONS, ...FINANCE] },
      { to: '/staff/reply-templates', label: 'Reply Templates', icon: 'template', roles: RELATIONS },
      { to: '/staff/notifications', label: 'Notification Log', icon: 'bell', roles: ALL_STAFF },
    ],
  },
  {
    label: 'Finance',
    items: [
      { to: '/staff/payments', label: 'Payments', icon: 'card', roles: FINANCE },
      { to: '/staff/refunds', label: 'Refunds', icon: 'refund', roles: FINANCE },
    ],
  },
  {
    label: 'Team',
    items: [{ to: '/staff/team', label: 'Staff Accounts', icon: 'badge', roles: MANAGEMENT }],
  },
];

/**
 * Routes that are wired end to end today. The sidebar only renders these, so the
 * navigation never offers a destination that does not exist yet.
 */
export const IMPLEMENTED = new Set([
  '/staff',
  '/staff/bookings',
  '/staff/packages',
  '/staff/parks',
  '/staff/assignments',
  '/staff/schedule',
  '/staff/vehicles',
  '/staff/guides',
  '/staff/complaints',
  '/staff/customers',
  '/staff/notifications',
  '/staff/permits',
  '/staff/payments',
  '/staff/refunds',
  '/staff/targets',
  '/staff/reply-templates',
  '/staff/team',
]);

export function navForRole(role) {
  return NAV_GROUPS.map((g) => ({
    ...g,
    items: g.items.filter((i) => i.roles.includes(role) && IMPLEMENTED.has(i.to)),
  })).filter((g) => g.items.length > 0);
}

export { ALL_STAFF, CATALOGUE, OPERATIONS, RELATIONS, FINANCE, MANAGEMENT };
