import { useState } from 'react';
import { bookingApi, complaintApi } from '../../api/api';
import { useAuth } from '../../context/AuthContext';
import { COMPLAINT_CATEGORIES as CATEGORIES } from '../../config/complaints';
import { useApi } from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { errorMessage, fieldErrors } from '../../api/client';
import StatusBadge from '../../components/StatusBadge';
import Timeline from '../../components/Timeline';
import {
  Button,
  Card,
  CardHead,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Modal,
  Select,
  Skeleton,
  SkeletonText,
  Textarea,
  dateFmt,
  titleCase,
} from '../../components/ui';

const BLANK = { subject: '', description: '', category: 'GENERAL_INQUIRY', bookingId: '' };

export default function MySupportPage() {
  const toast = useToast();
  const cases = useApi(() => complaintApi.mine(), []);
  const bookings = useApi(() => bookingApi.mine(), []);

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const { user } = useAuth();
  const [viewing, setViewing] = useState(null);
  const [reply, setReply] = useState('');
  const [editingReply, setEditingReply] = useState(null);
  const [editText, setEditText] = useState('');
  const [deletingReply, setDeletingReply] = useState(null);
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);

  const timeline = useApi(
    () => (viewing ? complaintApi.timeline(viewing.id) : Promise.resolve([])),
    [viewing?.id],
  );

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    try {
      const created = await complaintApi.create({
        subject: form.subject,
        description: form.description,
        category: form.category,
        bookingId: form.bookingId ? Number(form.bookingId) : null,
      });
      toast.success(
        `Your case ${created.reference} is with our customer relations team.`,
        'Message sent',
      );
      setOpen(false);
      setForm(BLANK);
      cases.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err), 'Could not send your message');
    } finally {
      setBusy(false);
    }
  };

  const postReply = async () => {
    if (!reply.trim()) return;
    setBusy(true);
    try {
      await complaintApi.addNote(viewing.id, { message: reply.trim() });
      toast.success('Your reply has been added to the case.', 'Reply sent');
      setReply('');
      timeline.reload();
      cases.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not send reply');
    } finally {
      setBusy(false);
    }
  };

  const openCase = (c) => {
    setViewing(c);
    setReply('');
    setEditingReply(null);
    setDeletingReply(null);
    setConfirmWithdraw(false);
  };

  // A case can be withdrawn only while it is open and nobody from our team has replied yet.
  const canWithdraw =
    viewing?.status === 'OPEN' && !timeline.loading && !(timeline.data ?? []).some((e) => e.authorIsStaff);

  const withdraw = async () => {
    setBusy(true);
    try {
      await complaintApi.remove(viewing.id);
      toast.success(`Case ${viewing.reference} has been withdrawn.`, 'Withdrawn');
      setViewing(null);
      cases.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not withdraw');
    } finally {
      setBusy(false);
    }
  };

  const saveReplyEdit = async () => {
    if (!editText.trim()) return;
    setBusy(true);
    try {
      await complaintApi.updateNote(viewing.id, editingReply.id, { message: editText.trim() });
      toast.success('Your reply has been updated.', 'Saved');
      setEditingReply(null);
      timeline.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not update reply');
    } finally {
      setBusy(false);
    }
  };

  const doDeleteReply = async () => {
    setBusy(true);
    try {
      await complaintApi.deleteNote(viewing.id, deletingReply.id);
      toast.success('Your reply has been removed.', 'Deleted');
      setDeletingReply(null);
      timeline.reload();
    } catch (err) {
      toast.error(errorMessage(err), 'Could not delete reply');
    } finally {
      setBusy(false);
    }
  };

  const rows = cases.data ?? [];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Support</div>
          <h1>Help &amp; enquiries</h1>
          <p className="lede">
            Ask us anything about a trip, or report a problem. You can follow the status of every
            case here.
          </p>
        </div>
        <Button onClick={() => setOpen(true)}>+ New message</Button>
      </div>

      {cases.loading ? (
        <div className="stack">
          {[0, 1].map((i) => (
            <Card key={i} className="card-pad">
              <Skeleton width="40%" height={16} />
              <Skeleton width="70%" height={11} style={{ marginTop: 10 }} />
            </Card>
          ))}
        </div>
      ) : cases.error ? (
        <ErrorState message={cases.error} onRetry={cases.reload} />
      ) : rows.length === 0 ? (
        <EmptyState
          icon="💬"
          title="No messages yet"
          message="If something needs sorting out, or you just have a question, send us a message and we will pick it up."
          action={<Button onClick={() => setOpen(true)}>Send a message</Button>}
        />
      ) : (
        <div className="stack">
          {rows.map((c) => (
            <Card key={c.id} className="card-pad card-hover" style={{ cursor: 'pointer' }} onClick={() => openCase(c)}>
              <div className="row row-gap-2 wrap mb-1">
                <StatusBadge value={c.status} />
                {c.escalatedToDepartment && (
                  <StatusBadge value="ESC" tone="warning" label={`With ${c.escalatedToDepartment}`} />
                )}
                <span className="tiny muted mono-num">{c.reference}</span>
                <span className="spacer" />
                <span className="tiny muted">Raised {dateFmt(c.createdAt)}</span>
              </div>
              <h3>{c.subject}</h3>
              <div className="small muted mt-1">
                {titleCase(c.category)}
                {c.bookingReference ? ` · booking ${c.bookingReference}` : ''}
              </div>
              {c.status === 'RESOLVED' && c.resolutionNotes && (
                <div className="inline-note ok mt-2">
                  <span aria-hidden="true">✓</span>
                  <span>{c.resolutionNotes}</span>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      {/* --------------------------------------------------- New message */}
      <Modal
        open={open}
        title="Send us a message"
        onClose={() => !busy && setOpen(false)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={submit} loading={busy}>
              Send message
            </Button>
          </>
        }
      >
        <form onSubmit={submit} className="stack" noValidate>
          <Field label="What is this about?" error={errors.category}>
            <Select value={form.category} onChange={set('category')}>
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Related booking (optional)" hint="Link this to a specific trip if it helps.">
            <Select value={form.bookingId} onChange={set('bookingId')}>
              <option value="">Not about a specific booking</option>
              {(bookings.data ?? []).map((b) => (
                <option key={b.id} value={b.id}>
                  {b.bookingReference} — {b.packageName} ({dateFmt(b.tripDate)})
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Subject" error={errors.subject}>
            <Input
              value={form.subject}
              onChange={set('subject')}
              placeholder="A one-line summary"
              error={errors.subject}
              required
            />
          </Field>

          <Field label="Tell us more" error={errors.description}>
            <Textarea
              rows={6}
              value={form.description}
              onChange={set('description')}
              placeholder="What happened, and what would you like us to do?"
              error={errors.description}
              required
            />
          </Field>
        </form>
      </Modal>

      {/* ------------------------------------------------------ Case view */}
      <Modal
        open={!!viewing}
        title={viewing ? viewing.subject : ''}
        onClose={() => !busy && setViewing(null)}
        wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setViewing(null)} disabled={busy}>
              Close
            </Button>
            {canWithdraw && !confirmWithdraw && (
              <Button variant="outline" onClick={() => setConfirmWithdraw(true)} disabled={busy}>
                Withdraw case
              </Button>
            )}
            {confirmWithdraw && (
              <>
                <Button variant="ghost" onClick={() => setConfirmWithdraw(false)} disabled={busy}>
                  Keep it
                </Button>
                <Button variant="danger" onClick={withdraw} loading={busy}>
                  Yes, withdraw
                </Button>
              </>
            )}
            {viewing && viewing.status !== 'RESOLVED' && !confirmWithdraw && !editingReply && !deletingReply && (
              <Button onClick={postReply} loading={busy} disabled={!reply.trim()}>
                Send reply
              </Button>
            )}
          </>
        }
      >
        {viewing && (
          <div className="stack">
            <div className="row row-gap-2 wrap">
              <StatusBadge value={viewing.status} />
              <StatusBadge value={viewing.priority} />
              <span className="tiny muted mono-num">{viewing.reference}</span>
              <span className="spacer" />
              <span className="tiny muted">Raised {dateFmt(viewing.createdAt)}</span>
            </div>

            {viewing.status === 'RESOLVED' && viewing.resolutionNotes && (
              <div className="inline-note ok">
                <span aria-hidden="true">✓</span>
                <div>
                  <strong>Resolved</strong>
                  <div className="tiny mt-1">{viewing.resolutionNotes}</div>
                </div>
              </div>
            )}

            <div>
              <div className="section-title">Conversation</div>
              {timeline.loading ? (
                <SkeletonText lines={5} />
              ) : timeline.error ? (
                <ErrorState message={timeline.error} onRetry={timeline.reload} />
              ) : (
                <Timeline
                  entries={(timeline.data ?? []).filter((e) => e.direction !== 'INTERNAL')}
                  currentUserId={user.id}
                  onEdit={(e) => {
                    setDeletingReply(null);
                    setEditingReply(e);
                    setEditText(e.message);
                  }}
                  onDelete={(e) => {
                    setEditingReply(null);
                    setDeletingReply(e);
                  }}
                />
              )}
            </div>

            {confirmWithdraw && (
              <div className="inline-note danger">
                <span aria-hidden="true">⚠</span>
                <span>
                  Withdrawing removes this case and its messages completely. Use it if you no longer
                  need our help.
                </span>
              </div>
            )}

            {editingReply ? (
              <div className="stack-sm">
                <Field label="Edit your reply">
                  <Textarea rows={3} value={editText} onChange={(e) => setEditText(e.target.value)} />
                </Field>
                <div className="row row-gap-2">
                  <span className="spacer" />
                  <Button variant="ghost" size="sm" onClick={() => setEditingReply(null)} disabled={busy}>
                    Cancel
                  </Button>
                  <Button size="sm" onClick={saveReplyEdit} loading={busy} disabled={!editText.trim()}>
                    Save reply
                  </Button>
                </div>
              </div>
            ) : deletingReply ? (
              <div className="inline-note warn">
                <span aria-hidden="true">⚠</span>
                <div style={{ flex: 1 }}>
                  <div>Delete your reply from {dateFmt(deletingReply.createdAt)}?</div>
                  <div className="row row-gap-2 mt-1">
                    <Button variant="ghost" size="sm" onClick={() => setDeletingReply(null)} disabled={busy}>
                      Keep it
                    </Button>
                    <Button variant="danger" size="sm" onClick={doDeleteReply} loading={busy}>
                      Delete reply
                    </Button>
                  </div>
                </div>
              </div>
            ) : viewing.status !== 'RESOLVED' && !confirmWithdraw && (
              <Field label="Add a reply">
                <Textarea
                  rows={3}
                  value={reply}
                  onChange={(e) => setReply(e.target.value)}
                  placeholder="Anything else we should know?"
                />
              </Field>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
