import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { complaintApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { Card, ErrorState, Select, SkeletonTable, dateFmt, titleCase } from '../../components/ui';

const STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'UNRESOLVED'];
const PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export default function ComplaintsPage() {
  const navigate = useNavigate();
  const complaints = useApi(() => complaintApi.list(), []);
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');

  const all = complaints.data ?? [];
  const rows = all.filter(
    (c) => (!status || c.status === status) && (!priority || c.priority === priority),
  );

  const counts = STATUSES.reduce((acc, s) => {
    acc[s] = all.filter((c) => c.status === s).length;
    return acc;
  }, {});

  const columns = [
    {
      key: 'reference',
      header: 'Case',
      width: 130,
      render: (r) => <span className="mono-num small strong">{r.reference}</span>,
    },
    {
      key: 'subject',
      header: 'Subject',
      render: (r) => (
        <div>
          <div className="strong truncate" style={{ maxWidth: 340 }}>
            {r.subject}
          </div>
          <div className="tiny muted">
            {titleCase(r.category)}
            {r.bookingReference ? ` · ${r.bookingReference}` : ''}
          </div>
        </div>
      ),
    },
    {
      key: 'customerName',
      header: 'Customer',
      width: 170,
      render: (r) => (
        <div>
          <div>{r.customerName}</div>
          <div className="tiny muted truncate">{r.customerEmail}</div>
        </div>
      ),
    },
    {
      key: 'priority',
      header: 'Priority',
      width: 110,
      sortValue: (r) => PRIORITIES.indexOf(r.priority),
      render: (r) => <StatusBadge value={r.priority} />,
    },
    {
      key: 'status',
      header: 'Status',
      width: 128,
      render: (r) => (
        <div className="col" style={{ gap: 4 }}>
          <StatusBadge value={r.status} />
          {r.escalatedToDepartment && (
            <StatusBadge value="ESC" tone="warning" label={`↑ ${r.escalatedToDepartment}`} />
          )}
        </div>
      ),
    },
    {
      key: 'assignedToName',
      header: 'Owner',
      width: 150,
      render: (r) => r.assignedToName ?? <span className="muted">Unassigned</span>,
    },
    {
      key: 'ageDays',
      header: 'Age',
      width: 90,
      align: 'right',
      sortValue: (r) => r.ageDays,
      render: (r) => (
        <span style={{ color: r.ageDays > 14 && !['RESOLVED', 'UNRESOLVED'].includes(r.status) ? 'var(--danger)' : undefined }}>
          {r.ageDays}d
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: 'Raised',
      width: 120,
      render: (r) => dateFmt(r.createdAt),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Customer relations</div>
          <h1>Complaints &amp; inquiries</h1>
          <p className="lede">
            Every case raised by a customer, with its owner, age and escalation state. Click a row
            to open the full history.
          </p>
        </div>
      </div>

      <div className="grid grid-4 mb-3">
        {STATUSES.map((s) => (
          <Card
            key={s}
            className={`kpi${s === 'OPEN' ? ' kpi-danger' : s === 'IN_PROGRESS' ? ' kpi-amber' : ''}`}
            style={{ cursor: 'pointer' }}
            onClick={() => setStatus(status === s ? '' : s)}
          >
            <div className="kpi-label">{titleCase(s)}</div>
            <div className="kpi-value">{counts[s] ?? 0}</div>
            <div className="kpi-sub">{status === s ? 'Filtering — click to clear' : 'Click to filter'}</div>
          </Card>
        ))}
      </div>

      {complaints.loading ? (
        <SkeletonTable rows={6} cols={8} />
      ) : complaints.error ? (
        <ErrorState message={complaints.error} onRetry={complaints.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'createdAt', dir: 'desc' }}
          searchPlaceholder="Search case, subject, customer…"
          emptyIcon="💬"
          emptyTitle="No complaints on file"
          emptyMessage="Cases raised by customers will appear here."
          onRowClick={(r) => navigate(`/staff/complaints/${r.id}`)}
          rowClassName={(r) => (r.priority === 'CRITICAL' && r.status !== 'RESOLVED' ? 'row-alert' : '')}
          filters={
            <>
              <Select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                style={{ width: 160 }}
                aria-label="Filter by status"
              >
                <option value="">All statuses</option>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {titleCase(s)}
                  </option>
                ))}
              </Select>
              <Select
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                style={{ width: 150 }}
                aria-label="Filter by priority"
              >
                <option value="">All priorities</option>
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {titleCase(p)}
                  </option>
                ))}
              </Select>
            </>
          }
        />
      )}
    </div>
  );
}
