import { dateTimeFmt, titleCase } from './ui';

const ICONS = {
  EMAIL: '✉',
  SMS: '💬',
  PHONE_CALL: '☎',
  IN_APP_NOTE: '📝',
  STATUS_CHANGE: '⇄',
  ESCALATION: '⬆',
  SYSTEM: '⚙',
};

const DOT_CLASS = {
  ESCALATION: 'escalation',
  STATUS_CHANGE: 'status',
};

/**
 * Renders a communication history as a vertical timeline.
 *
 * Pass `currentUserId` with `onEdit` / `onDelete` to let people change their own hand-written
 * notes. Entries the server marks `locked` (the opening message, status changes, escalations)
 * never offer those actions.
 */
export default function Timeline({ entries, currentUserId, onEdit, onDelete }) {
  if (!entries?.length) {
    return <p className="muted small">No messages on this case yet.</p>;
  }

  const canModify = (e) =>
    !e.locked && currentUserId !== undefined && e.authorId === currentUserId && (onEdit || onDelete);

  return (
    <div className="timeline">
      {entries.map((e) => {
        const dotClass = DOT_CLASS[e.type] ?? (e.direction === 'INBOUND' ? 'inbound' : '');
        return (
          <div className="tl-item" key={e.id}>
            <span className={`tl-dot ${dotClass}`} aria-hidden="true">
              {ICONS[e.type] ?? '•'}
            </span>
            <div className="tl-head">
              <span className="tl-who">{e.authorName}</span>
              <span className="badge badge-neutral">{titleCase(e.type)}</span>
              {e.direction === 'INBOUND' && <span className="badge badge-info">From customer</span>}
              {e.direction === 'INTERNAL' && <span className="badge badge-warning">Internal</span>}
              <span className="tl-when">
                {dateTimeFmt(e.createdAt)}
                {e.editedAt && <span title={`Edited ${dateTimeFmt(e.editedAt)}`}> · edited</span>}
              </span>
              {canModify(e) && (
                <span className="row row-gap-1" style={{ marginLeft: 'auto' }}>
                  {onEdit && (
                    <button type="button" className="link-btn tiny" onClick={() => onEdit(e)}>
                      Edit
                    </button>
                  )}
                  {onDelete && (
                    <button type="button" className="link-btn tiny" onClick={() => onDelete(e)}>
                      Delete
                    </button>
                  )}
                </span>
              )}
            </div>
            {e.subject && <div className="small strong mt-1">{e.subject}</div>}
            <div className="tl-msg">{e.message}</div>
          </div>
        );
      })}
    </div>
  );
}
