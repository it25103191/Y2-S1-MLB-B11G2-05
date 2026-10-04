import { useRef, useState } from 'react';
import { replyTemplateApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage, fieldErrors } from '../../api/client';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import {
  COMPLAINT_CATEGORIES,
  TEMPLATE_PLACEHOLDERS,
  categoryLabel,
  fillPlaceholders,
} from '../../config/complaints';
import {
  Button,
  Card,
  ErrorState,
  Field,
  Input,
  Modal,
  Select,
  SkeletonTable,
  Textarea,
  dateFmt,
} from '../../components/ui';

const BLANK = { title: '', category: '', subject: '', body: '', active: true };

/** Sample values used only for the live preview in the editor. */
const SAMPLE = {
  customerName: 'Sarah Smith',
  caseReference: 'CM-2610-K7QPX',
  bookingReference: 'BK-2610-4ME8T',
};

export default function ReplyTemplatesPage() {
  const toast = useToast();
  const templates = useApi(() => replyTemplateApi.list(false), []);
  const bodyRef = useRef(null);

  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const [activeFilter, setActiveFilter] = useState('');

  const all = templates.data ?? [];
  const rows = all.filter(
    (t) => !activeFilter || (activeFilter === 'active' ? t.active : !t.active),
  );

  const openNew = () => {
    setForm(BLANK);
    setErrors({});
    setEditing({ isNew: true });
  };

  const openEdit = (t) => {
    setForm({
      title: t.title,
      category: t.category ?? '',
      subject: t.subject ?? '',
      body: t.body,
      active: t.active,
    });
    setErrors({});
    setEditing(t);
  };

  const set = (k) => (e) => {
    const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((f) => ({ ...f, [k]: v }));
  };

  /** Inserts a placeholder at the cursor in the body. */
  const insertToken = (token) => {
    const el = bodyRef.current;
    const text = `{{${token}}}`;
    setForm((f) => {
      if (!el) return { ...f, body: f.body + text };
      const start = el.selectionStart ?? f.body.length;
      const end = el.selectionEnd ?? f.body.length;
      const body = f.body.slice(0, start) + text + f.body.slice(end);
      requestAnimationFrame(() => {
        el.focus();
        el.setSelectionRange(start + text.length, start + text.length);
      });
      return { ...f, body };
    });
  };

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    const payload = {
      title: form.title,
      category: form.category || null,
      subject: form.subject || null,
      body: form.body,
      active: form.active,
    };
    try {
      if (editing.isNew) {
        await replyTemplateApi.create(payload);
        toast.success(`"${payload.title}" is ready to use in case replies.`, 'Template created');
      } else {
        await replyTemplateApi.update(editing.id, payload);
        toast.success(`"${payload.title}" updated.`, 'Template saved');
      }
      setEditing(null);
      templates.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not save template');
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (t) => {
    try {
      await replyTemplateApi.update(t.id, {
        title: t.title,
        category: t.category,
        subject: t.subject,
        body: t.body,
        active: !t.active,
      });
      toast.success(`"${t.title}" ${t.active ? 'hidden from the reply picker' : 'available again'}.`, 'Updated');
      templates.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update');
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await replyTemplateApi.remove(deleting.id);
      toast.success(`"${deleting.title}" deleted. Replies already sent keep their text.`, 'Template deleted');
      setDeleting(null);
      templates.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete');
    } finally {
      setBusy(false);
    }
  };

  const columns = [
    {
      key: 'title',
      header: 'Template',
      render: (r) => (
        <div>
          <div className="strong">{r.title}</div>
          <div className="tiny muted truncate" style={{ maxWidth: 320 }}>
            {r.subject ?? r.body}
          </div>
        </div>
      ),
    },
    {
      key: 'category',
      header: 'Best for',
      width: 170,
      sortValue: (r) => categoryLabel(r.category),
      render: (r) =>
        r.category ? (
          <span className="badge badge-forest">{categoryLabel(r.category)}</span>
        ) : (
          <span className="badge badge-neutral">Any category</span>
        ),
    },
    {
      key: 'placeholders',
      header: 'Placeholders',
      width: 220,
      sortable: false,
      searchable: false,
      render: (r) =>
        r.placeholders.length ? (
          <div className="chip-row">
            {r.placeholders.map((p) => (
              <span key={p} className="pkg-tag mono-num">{`{{${p}}}`}</span>
            ))}
          </div>
        ) : (
          <span className="muted tiny">None</span>
        ),
    },
    {
      key: 'usageCount',
      header: 'Uses',
      width: 80,
      align: 'center',
      sortValue: (r) => r.usageCount,
      render: (r) => <span className="strong">{r.usageCount}</span>,
    },
    {
      key: 'active',
      header: 'Status',
      width: 110,
      sortValue: (r) => (r.active ? 1 : 0),
      render: (r) => (
        <StatusBadge value={r.active ? 'AVAILABLE' : 'INACTIVE'} label={r.active ? 'Active' : 'Hidden'} />
      ),
    },
    {
      key: 'updatedAt',
      header: 'Last changed',
      width: 120,
      sortValue: (r) => r.updatedAt ?? r.createdAt,
      render: (r) => <span className="small">{dateFmt(r.updatedAt ?? r.createdAt)}</span>,
    },
    {
      key: 'actions',
      header: '',
      width: 220,
      sortable: false,
      searchable: false,
      render: (r) => (
        <div className="row row-gap-1">
          <Button size="sm" variant="outline" onClick={() => openEdit(r)}>
            Edit
          </Button>
          <Button size="sm" variant="ghost" onClick={() => toggleActive(r)}>
            {r.active ? 'Hide' : 'Show'}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setDeleting(r)}>
            Delete
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Customer relations</div>
          <h1>Reply templates</h1>
          <p className="lede">
            Canned responses for common cases. Insert one from a case's reply box and the customer
            name, case and booking references are filled in for you.
          </p>
        </div>
        <Button onClick={openNew}>+ New template</Button>
      </div>

      <div className="grid grid-3 mb-3">
        <Card className="kpi">
          <div className="kpi-label">Active</div>
          <div className="kpi-value">{all.filter((t) => t.active).length}</div>
          <div className="kpi-sub">Shown in the reply picker</div>
        </Card>
        <Card className="kpi kpi-terracotta">
          <div className="kpi-label">Hidden</div>
          <div className="kpi-value">{all.filter((t) => !t.active).length}</div>
          <div className="kpi-sub">Kept but not offered</div>
        </Card>
        <Card className="kpi kpi-amber">
          <div className="kpi-label">Replies sent from templates</div>
          <div className="kpi-value">{all.reduce((sum, t) => sum + t.usageCount, 0)}</div>
        </Card>
      </div>

      {templates.loading ? (
        <SkeletonTable rows={6} cols={7} />
      ) : templates.error ? (
        <ErrorState message={templates.error} onRetry={templates.reload} />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          initialSort={{ key: 'title', dir: 'asc' }}
          searchPlaceholder="Search title, subject or body…"
          emptyIcon="📋"
          emptyTitle="No templates yet"
          emptyMessage="Create a template for the replies your team writes most often."
          filters={
            <Select
              value={activeFilter}
              onChange={(e) => setActiveFilter(e.target.value)}
              style={{ width: 150 }}
              aria-label="Filter by status"
            >
              <option value="">All templates</option>
              <option value="active">Active</option>
              <option value="hidden">Hidden</option>
            </Select>
          }
        />
      )}

      <Modal
        open={!!editing}
        title={editing?.isNew ? 'New reply template' : `Edit — ${editing?.title ?? ''}`}
        onClose={() => !busy && setEditing(null)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} loading={busy}>
              {editing?.isNew ? 'Create template' : 'Save changes'}
            </Button>
          </>
        }
      >
        <form onSubmit={save} className="form-grid" noValidate>
          <Field label="Title" error={errors.title}>
            <Input value={form.title} onChange={set('title')} placeholder="Vehicle fault apology" error={errors.title} />
          </Field>

          <Field label="Best for" hint="Templates for the case's category are listed first.">
            <Select value={form.category} onChange={set('category')}>
              <option value="">Any category</option>
              {COMPLAINT_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Subject (optional)" error={errors.subject} span2>
            <Input value={form.subject} onChange={set('subject')} placeholder="Update on {{caseReference}}" />
          </Field>

          <Field label="Message" error={errors.body} span2>
            <div className="chip-row mb-1">
              {TEMPLATE_PLACEHOLDERS.map((p) => (
                <button key={p.token} type="button" className="chip" onClick={() => insertToken(p.token)}>
                  + {p.label}
                </button>
              ))}
            </div>
            <Textarea
              ref={bodyRef}
              rows={6}
              value={form.body}
              onChange={set('body')}
              placeholder="Hi {{customerName}}, …"
              error={errors.body}
            />
          </Field>

          <Field span2>
            <label className="checkline">
              <input type="checkbox" checked={!!form.active} onChange={set('active')} />
              <span>Active — offer this template in the reply picker</span>
            </label>
          </Field>

          <div className="span-2">
            <div className="section-title">Preview</div>
            <Card className="card-pad-sm" style={{ background: 'var(--cream-50)' }}>
              {form.subject && <div className="strong small mb-1">{fillPlaceholders(form.subject, SAMPLE)}</div>}
              <div className="small" style={{ whiteSpace: 'pre-wrap' }}>
                {form.body ? fillPlaceholders(form.body, SAMPLE) : <span className="muted">Nothing to preview yet.</span>}
              </div>
            </Card>
            <div className="tiny muted mt-1">Preview uses sample values. Real values come from the case.</div>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!deleting}
        title="Delete template"
        onClose={() => !busy && setDeleting(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="danger" onClick={doDelete} loading={busy}>
              Delete template
            </Button>
          </>
        }
      >
        {deleting && (
          <div className="stack">
            <p>
              Delete <strong>{deleting.title}</strong>? It has been used {deleting.usageCount} time
              {deleting.usageCount === 1 ? '' : 's'}.
            </p>
            <div className="inline-note">
              <span aria-hidden="true">ℹ</span>
              <span>Replies already sent keep their text. To stop offering it but keep it, use Hide instead.</span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
