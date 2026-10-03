import { Badge, titleCase } from './ui';

/** Single source of truth for how every domain status is coloured across the app. */
const TONES = {
  // Bookings
  PENDING: 'warning',
  CONFIRMED: 'success',
  COMPLETED: 'forest',
  CANCELLED: 'danger',

  // Vehicles / guides
  AVAILABLE: 'success',
  MAINTENANCE: 'warning',
  RETIRED: 'neutral',
  ON_LEAVE: 'warning',
  INACTIVE: 'neutral',

  // Assignments
  SCHEDULED: 'info',
  IN_PROGRESS: 'warning',

  // Complaints
  OPEN: 'danger',
  RESOLVED: 'success',
  UNRESOLVED: 'neutral',

  // Priorities
  LOW: 'neutral',
  MEDIUM: 'info',
  HIGH: 'warning',
  CRITICAL: 'danger',

  // Permits
  APPROVED: 'success',
  EXPIRED: 'danger',
  REJECTED: 'danger',

  // Payments / refunds
  SUCCESS: 'success',
  DECLINED: 'danger',
  TIMEOUT: 'warning',
  REFUNDED: 'info',
  REQUESTED: 'warning',
  PROCESSED: 'success',
  VOIDED: 'neutral',

  // KPI targets
  ACHIEVED: 'success',
  MISSED: 'danger',
  UPCOMING: 'info',
};

export default function StatusBadge({ value, tone, label }) {
  if (!value && !label) return <span className="muted">—</span>;
  return <Badge tone={tone ?? TONES[value] ?? 'neutral'} dot>{label ?? titleCase(value)}</Badge>;
}
