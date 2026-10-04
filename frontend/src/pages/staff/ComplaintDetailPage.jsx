import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { complaintApi, customerApi, replyTemplateApi } from '../../api/api';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { categoryLabel, fillPlaceholders } from '../../config/complaints';
import { errorMessage } from '../../api/client';
import StatusBadge from '../../components/StatusBadge';
import Timeline from '../../components/Timeline';
import {
  Button,
  Card,
  CardHead,
  Checkbox,
  ErrorState,
  Field,
  Modal,
  Select,
  Skeleton,
  SkeletonText,
  Textarea,
  dateTimeFmt,
  titleCase,
} from '../../components/ui';

const STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'UNRESOLVED'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const DEPARTMENTS = ['Operations', 'Finance', 'Fleet & Logistics', 'Park Liaison', 'Management'];
const NOTE_TYPES = ['IN_APP_NOTE', 'EMAIL', 'PHONE_CALL', 'SMS'];

export default function ComplaintDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const navigate = useNavigate();
  const { user } = useAuth();

  const complaint = useApi(() => complaintApi.get(id), [id]);
  const timeline = useApi(() => complaintApi.timeline(id), [id]);
  const staff = useApi(() => customerApi.staffUsers(), []);
  const templates = useApi(() => replyTemplateApi.list(true), []);

  const [note, setNote] = useState('');
  const [noteType, setNoteType] = useState('IN_APP_NOTE');
  const [noteSubject, setNoteSubject] = useState('');
  const [notify, setNotify] = useState(false);
  const [templateId, setTemplateId] = useState('');
  const [busy, setBusy] = useState(false);

  const [editingNote, setEditingNote] = useState(null);
  const [editText, setEditText] = useState('');
  const [editSubject, setEditSubject] = useState('');
  const [deletingNote, setDeletingNote] = useState(null);
  const [deleteCaseOpen, setDeleteCaseOpen] = useState(false);

  const [escalateOpen, setEscalateOpen] = useState(false);
  const [department, setDepartment] = useState(DEPARTMENTS[0]);
  const [escalateReason, setEscalateReason] = useState('');

  const [resolveOpen, setResolveOpen] = useState(false);
  const [resolveStatus, setResolveStatus] = useState('RESOLVED');
  const [resolutionNotes, setResolutionNotes] = useState('');

  const refresh = () => {
    complaint.reload();
    timeline.reload();
  };

  const postNote = async (e) => {
    e.preventDefault();
    if (!note.trim()) return;
    setBusy(true);
    try {
      await complaintApi.addNote(id, {
        message: note.trim(),
        type: noteType,
        direction: 'OUTBOUND',
        subject: noteSubject || null,
        notifyCustomer: notify,
        templateId: templateId ? Number(templateId) : null,
      });
      toast.success(
        notify ? 'Note added and the customer was notified.' : 'Note added to the case history.',
        'Update posted',
      );
      setNote('');
      setNoteSubject('');
      setNotify(false);
      setTemplateId('');
      refresh();
      templates.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not post update');
    } finally {
      setBusy(false);
    }
  };

  /** Fills the reply box from a template, with the case's own details substituted in. */
  const applyTemplate = (value) => {
    setTemplateId(value);
    if (!value) return;
    const t = (templates.data ?? []).find((x) => String(x.id) === String(value));
    if (!t) return;
    const c = complaint.data;
    const values = {
      customerName: c.customerName,
      caseReference: c.reference,
      bookingReference: c.bookingReference ?? 'your booking',
    };
    setNoteSubject(fillPlaceholders(t.subject ?? '', values));
    setNote(fillPlaceholders(t.body, values));
    setNoteType('EMAIL');
  };

  const openNoteEdit = (entry) => {
    setEditingNote(entry);
    setEditText(entry.message);
    setEditSubject(entry.subject ?? '');
  };

  const saveNoteEdit = async () => {
    if (!editText.trim()) return;
    setBusy(true);
    try {
      await complaintApi.updateNote(id, editingNote.id, { message: editText.trim(), subject: editSubject });
      toast.success('Your note has been updated and marked as edited.', 'Note saved');
      setEditingNote(null);
      timeline.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update note');
    } finally {
      setBusy(false);
    }
  };

  const doDeleteNote = async () => {
    setBusy(true);
    try {
      await complaintApi.deleteNote(id, deletingNote.id);
      toast.success('The note has been removed from the case history.', 'Note deleted');
      setDeletingNote(null);
      timeline.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete note');
    } finally {
      setBusy(false);
    }
  };

  const doDeleteCase = async () => {
    setBusy(true);
    try {
      await complaintApi.remove(id);
      toast.success(`Case ${complaint.data.reference} and its history were deleted.`, 'Case deleted');
      navigate('/staff/complaints', { replace: true });
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete case');
      setBusy(false);
    }
  };

  const patch = async (payload, successMsg) => {
    setBusy(true);
    try {
      await complaintApi.update(id, payload);
      toast.success(successMsg, 'Case updated');
      refresh();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update case');
    } finally {
      setBusy(false);
    }
  };

  const doEscalate = async () => {
    if (!escalateReason.trim()) {
      toast.warning('Give a reason for the escalation.', 'Reason required');
      return;
    }
    setBusy(true);
    try {
      await complaintApi.escalate(id, { department, reason: escalateReason.trim() });
      toast.success(`Case escalated to ${department}.`, 'Escalated');
      setEscalateOpen(false);
      setEscalateReason('');
      refresh();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not escalate');
    } finally {
      setBusy(false);
    }
  };

  const doResolve = async () => {
    setBusy(true);
    try {
      await complaintApi.update(id, {
        status: resolveStatus,
        resolutionNotes: resolutionNotes.trim() || null,
      });
      toast.success(`Case marked ${titleCase(resolveStatus).toLowerCase()}.`, 'Case closed');
      setResolveOpen(false);
      setResolutionNotes('');
      refresh();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not close case');
    } finally {
      setBusy(false);
    }
  };

  if (complaint.loading) {
    return (
      <div className="page">
        <Skeleton width="35%" height={26} className="mb-3" />
        <div className="grid" style={{ gridTemplateColumns: '1.6fr 1fr' }}>
          <Card className="card-pad">
            <SkeletonText lines={8} />
          </Card>
          <Card className="card-pad">
            <SkeletonText lines={5} />
          </Card>
        </div>
      </div>
    );
  }

  if (complaint.error) {
    return (
      <div className="page">
        <ErrorState message={complaint.error} onRetry={complaint.reload} />
      </div>
    );
  }

  const c = complaint.data;
  const closed = c.status === 'RESOLVED' || c.status === 'UNRESOLVED';

  return (
    <div className="page">
      <Link to="/staff/complaints" className="small">
        ← Back to all cases
      </Link>

      <div className="page-head mt-2">
        <div>
          <div className="eyebrow mono-num">{c.reference}</div>
          <h1>{c.subject}</h1>
          <p className="lede">
            Raised by {c.customerName} · {titleCase(c.category)} · {c.ageDays} day
            {c.ageDays === 1 ? '' : 's'} old
          </p>
        </div>
        <div className="row row-gap-2 wrap">
          <StatusBadge value={c.priority} />
          <StatusBadge value={c.status} />
          {c.escalatedToDepartment && (
            <StatusBadge value="ESC" tone="warning" label={`Escalated → ${c.escalatedToDepartment}`} />
          )}
        </div>
      </div>

      <div className="grid" style={{ gridTemplateColumns: '1.6fr 1fr', alignItems: 'start' }}>
        {/* ------------------------------------------------------ Timeline */}
        <div className="stack">
          <Card>
            <CardHead title="Case history" subtitle="Everything said on this case, oldest first" />
            <div className="card-body">
              {timeline.loading ? (
                <SkeletonText lines={6} />
              ) : timeline.error ? (
                <ErrorState message={timeline.error} onRetry={timeline.reload} />
              ) : (
                <Timeline
                  entries={timeline.data}
                  currentUserId={user.id}
                  onEdit={openNoteEdit}
                  onDelete={setDeletingNote}
                />
              )}
            </div>
          </Card>

          <Card>
            <CardHead title="Add an update" subtitle="Posts to the case history" />
            <div className="card-body">
              <form onSubmit={postNote} className="stack">
                <Field
                  label="Start from a template (optional)"
                  hint={
                    templates.data?.length
                      ? `Templates for "${categoryLabel(c.category)}" are listed first.`
                      : 'No active templates yet.'
                  }
                >
                  <Select value={templateId} onChange={(e) => applyTemplate(e.target.value)} disabled={templates.loading}>
                    <option value="">Write from scratch</option>
                    {[...(templates.data ?? [])]
                      .sort((a, b) => (b.category === c.category) - (a.category === c.category))
                      .map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.category === c.category ? '★ ' : ''}
                          {t.title}
                        </option>
                      ))}
                  </Select>
                </Field>
                <div className="form-grid">
                  <Field label="Channel">
                    <Select value={noteType} onChange={(e) => setNoteType(e.target.value)}>
                      {NOTE_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {titleCase(t)}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  <Field label="Subject (optional)">
                    <input
                      className="input"
                      value={noteSubject}
                      onChange={(e) => setNoteSubject(e.target.value)}
                      placeholder="Short headline"
                    />
                  </Field>
                </div>
                <Field label="Message">
                  <Textarea
                    rows={4}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="What did you do or find out?"
                    required
                  />
                </Field>
                <div className="row row-gap-3 wrap">
                  <Checkbox
                    label="Also send this to the customer"
                    checked={notify}
                    onChange={(e) => setNotify(e.target.checked)}
                  />
                  <span className="spacer" />
                  <Button type="submit" loading={busy} disabled={!note.trim()}>
                    Post update
                  </Button>
                </div>
              </form>
            </div>
          </Card>
        </div>

        {/* --------------------------------------------------------- Side */}
        <div className="stack">
          <Card>
            <CardHead title="Case management" />
            <div className="card-body stack">
              <Field label="Status">
                <Select
                  value={c.status}
                  disabled={busy}
                  onChange={(e) => patch({ status: e.target.value }, `Status set to ${titleCase(e.target.value)}.`)}
                >
                  {STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {titleCase(s)}
                    </option>
                  ))}
                </Select>
              </Field>

              <Field label="Priority">
                <Select
                  value={c.priority}
                  disabled={busy}
                  onChange={(e) => patch({ priority: e.target.value }, `Priority set to ${titleCase(e.target.value)}.`)}
                >
                  {PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {titleCase(p)}
                    </option>
                  ))}
                </Select>
              </Field>

              <Field label="Owner">
                <Select
                  value={c.assignedToId ?? ''}
                  disabled={busy || staff.loading}
                  onChange={(e) =>
                    e.target.value &&
                    patch({ assignedToId: Number(e.target.value) }, 'Case owner updated.')
                  }
                >
                  <option value="">Unassigned</option>
                  {(staff.data ?? []).map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.fullName} — {u.roleLabel}
                    </option>
                  ))}
                </Select>
              </Field>

              <div className="row row-gap-2">
                <Button
                  variant="secondary"
                  className="grow"
                  onClick={() => setEscalateOpen(true)}
                  disabled={busy || c.status === 'RESOLVED'}
                >
                  ⬆ Escalate
                </Button>
                <Button
                  variant="outline"
                  className="grow"
                  onClick={() => setResolveOpen(true)}
                  disabled={busy}
                >
                  Close case
                </Button>
              </div>
              <Button variant="ghost" size="sm" onClick={() => setDeleteCaseOpen(true)} disabled={busy}>
                🗑 Delete case (spam or duplicate)
              </Button>

              {c.status === 'RESOLVED' && (
                <div className="inline-note ok">
                  <span aria-hidden="true">✓</span>
                  <span>Resolved {dateTimeFmt(c.resolvedAt)}</span>
                </div>
              )}
              {c.escalatedAt && (
                <div className="inline-note warn">
                  <span aria-hidden="true">⬆</span>
                  <span>
                    Escalated to <strong>{c.escalatedToDepartment}</strong> on{' '}
                    {dateTimeFmt(c.escalatedAt)}
                  </span>
                </div>
              )}
              {closed && c.resolutionNotes && (
                <div>
                  <div className="section-title">Resolution</div>
                  <p className="small">{c.resolutionNotes}</p>
                </div>
              )}
            </div>
          </Card>

          <Card>
            <CardHead title="Customer" />
            <div className="card-body">
              <dl className="dl">
                <dt>Name</dt>
                <dd>{c.customerName}</dd>
                <dt>Email</dt>
                <dd>{c.customerEmail}</dd>
                {c.bookingReference && (
                  <>
                    <dt>Booking</dt>
                    <dd className="mono-num">{c.bookingReference}</dd>
                    <dt>Safari</dt>
                    <dd>{c.packageName}</dd>
                  </>
                )}
              </dl>
              <Link to={`/staff/customers/${c.customerId}`}>
                <Button variant="outline" size="sm" className="mt-2">
                  Open customer profile
                </Button>
              </Link>
            </div>
          </Card>
        </div>
      </div>

      {/* ----------------------------------------------------- Escalation */}
      <Modal
        open={escalateOpen}
        title="Escalate this case"
        onClose={() => !busy && setEscalateOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEscalateOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="secondary" onClick={doEscalate} loading={busy}>
              Escalate case
            </Button>
          </>
        }
      >
        <div className="stack">
          <p className="small muted">
            Escalating moves the case to another department, raises its priority and notifies the
            customer that it is being looked at more urgently.
          </p>
          <Field label="Escalate to">
            <Select value={department} onChange={(e) => setDepartment(e.target.value)}>
              {DEPARTMENTS.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Reason">
            <Textarea
              rows={4}
              value={escalateReason}
              onChange={(e) => setEscalateReason(e.target.value)}
              placeholder="Why does this need another department?"
              required
            />
          </Field>
        </div>
      </Modal>

      {/* -------------------------------------------------------- Closing */}
      <Modal
        open={resolveOpen}
        title="Close this case"
        onClose={() => !busy && setResolveOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setResolveOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={doResolve} loading={busy}>
              Close case
            </Button>
          </>
        }
      >
        <div className="stack">
          <Field label="Outcome">
            <Select value={resolveStatus} onChange={(e) => setResolveStatus(e.target.value)}>
              <option value="RESOLVED">Resolved — the customer got a fix</option>
              <option value="UNRESOLVED">Unresolved — closed without a fix</option>
            </Select>
          </Field>
          <Field label="Resolution notes">
            <Textarea
              rows={4}
              value={resolutionNotes}
              onChange={(e) => setResolutionNotes(e.target.value)}
              placeholder="What was done, and what was the outcome?"
            />
          </Field>
          {resolveStatus === 'RESOLVED' && (
            <div className="inline-note">
              <span aria-hidden="true">ℹ</span>
              <span>The customer is emailed automatically when a case is resolved.</span>
            </div>
          )}
        </div>
      </Modal>

      {/* ------------------------------------------------------ Note edit */}
      <Modal
        open={!!editingNote}
        title="Edit your note"
        onClose={() => !busy && setEditingNote(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditingNote(null)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={saveNoteEdit} loading={busy} disabled={!editText.trim()}>
              Save note
            </Button>
          </>
        }
      >
        <div className="stack">
          <Field label="Subject (optional)">
            <input className="input" value={editSubject} onChange={(e) => setEditSubject(e.target.value)} />
          </Field>
          <Field label="Message">
            <Textarea rows={5} value={editText} onChange={(e) => setEditText(e.target.value)} />
          </Field>
          <div className="inline-note">
            <span aria-hidden="true">ℹ</span>
            <span>The note will show as edited. Messages already emailed to the customer are not recalled.</span>
          </div>
        </div>
      </Modal>

      {/* ---------------------------------------------------- Note delete */}
      <Modal
        open={!!deletingNote}
        title="Delete this note?"
        onClose={() => !busy && setDeletingNote(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeletingNote(null)} disabled={busy}>
              Keep it
            </Button>
            <Button variant="danger" onClick={doDeleteNote} loading={busy}>
              Delete note
            </Button>
          </>
        }
      >
        {deletingNote && (
          <div className="stack">
            <p className="small">
              Remove your {titleCase(deletingNote.type).toLowerCase()} from {dateTimeFmt(deletingNote.createdAt)}?
            </p>
            <div className="card card-pad-sm small" style={{ whiteSpace: 'pre-wrap' }}>
              {deletingNote.message}
            </div>
          </div>
        )}
      </Modal>

      {/* ---------------------------------------------------- Case delete */}
      <Modal
        open={deleteCaseOpen}
        title="Delete this case?"
        onClose={() => !busy && setDeleteCaseOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleteCaseOpen(false)} disabled={busy}>
              Keep case
            </Button>
            <Button variant="danger" onClick={doDeleteCase} loading={busy}>
              Delete case
            </Button>
          </>
        }
      >
        <div className="stack">
          <p>
            Permanently delete <strong>{c.reference}</strong> ("{c.subject}") and its whole timeline?
          </p>
          <div className="inline-note warn">
            <span aria-hidden="true">⚠</span>
            <span>
              Use this for spam and duplicates. For a genuine case that is finished, use Close case
              so it stays in the customer's history and in reporting.
            </span>
          </div>
        </div>
      </Modal>
    </div>
  );
}
