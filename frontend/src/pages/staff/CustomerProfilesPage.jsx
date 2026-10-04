import { useNavigate } from 'react-router-dom';
import { customerApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import DataTable from '../../components/DataTable';
import { Button, ErrorState, SkeletonTable, dateFmt, money } from '../../components/ui';

export default function CustomerProfilesPage() {
  const navigate = useNavigate();
  const customers = useApi(() => customerApi.list(), []);

  const columns = [
    {
      key: 'fullName',
      header: 'Customer',
      render: (r) => (
        <div>
          <div className="strong">{r.fullName}</div>
          <div className="tiny muted">{r.email}</div>
        </div>
      ),
    },
    { key: 'phone', header: 'Phone', width: 160 },
    {
      key: 'joinedAt',
      header: 'Joined',
      width: 120,
      render: (r) => dateFmt(r.joinedAt),
    },
    {
      key: 'totalBookings',
      header: 'Bookings',
      width: 100,
      align: 'center',
      sortValue: (r) => r.totalBookings,
      render: (r) => (
        <span>
          {r.totalBookings}
          {r.cancelledBookings > 0 && (
            <span className="tiny muted"> ({r.cancelledBookings} cancelled)</span>
          )}
        </span>
      ),
    },
    {
      key: 'lifetimeValue',
      header: 'Lifetime value',
      width: 138,
      align: 'right',
      sortValue: (r) => Number(r.lifetimeValue),
      render: (r) => <span className="mono-num">{money(r.lifetimeValue)}</span>,
    },
    {
      key: 'outstandingBalance',
      header: 'Outstanding',
      width: 128,
      align: 'right',
      sortValue: (r) => Number(r.outstandingBalance),
      render: (r) =>
        Number(r.outstandingBalance) > 0 ? (
          <span className="mono-num" style={{ color: 'var(--terracotta-600)' }}>
            {money(r.outstandingBalance)}
          </span>
        ) : (
          <span className="muted">—</span>
        ),
    },
    {
      key: 'openComplaints',
      header: 'Open cases',
      width: 110,
      align: 'center',
      sortValue: (r) => r.openComplaints,
      render: (r) =>
        r.openComplaints > 0 ? (
          <span className="badge badge-danger">{r.openComplaints}</span>
        ) : (
          <span className="muted">—</span>
        ),
    },
    {
      key: 'actions',
      header: '',
      width: 92,
      sortable: false,
      searchable: false,
      render: (r) => (
        <Button size="sm" variant="outline" onClick={() => navigate(`/staff/customers/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Customer relations</div>
          <h1>Customer profiles</h1>
          <p className="lede">
            Every traveller on file with their booking history, spend and open cases in one place.
          </p>
        </div>
      </div>

      {customers.loading ? (
        <SkeletonTable rows={5} cols={8} />
      ) : customers.error ? (
        <ErrorState message={customers.error} onRetry={customers.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={customers.data}
          initialSort={{ key: 'fullName', dir: 'asc' }}
          searchPlaceholder="Search name, email or phone…"
          emptyIcon="👥"
          emptyTitle="No customers yet"
          emptyMessage="Registered travellers will appear here."
          onRowClick={(r) => navigate(`/staff/customers/${r.id}`)}
          rowClassName={(r) => (r.openComplaints > 0 ? 'row-alert' : '')}
        />
      )}
    </div>
  );
}
