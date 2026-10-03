import { forwardRef, useEffect } from 'react';
import { createPortal } from 'react-dom';

/* ------------------------------------------------------------------ Button */

export function Button({
  children,
  variant = 'primary',
  size,
  loading = false,
  disabled,
  className = '',
  type = 'button',
  ...rest
}) {
  const variants = {
    primary: '',
    secondary: 'btn-secondary',
    accent: 'btn-accent',
    outline: 'btn-outline',
    ghost: 'btn-ghost',
    danger: 'btn-danger',
  };
  const classes = [
    'btn',
    variants[variant] ?? '',
    size === 'sm' ? 'btn-sm' : size === 'lg' ? 'btn-lg' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button type={type} className={classes} disabled={disabled || loading} {...rest}>
      {loading && <span className="btn-spin" aria-hidden="true" />}
      {children}
    </button>
  );
}

/* -------------------------------------------------------------------- Card */

export function Card({ children, className = '', hover = false, ...rest }) {
  return (
    <div className={`card${hover ? ' card-hover' : ''} ${className}`.trim()} {...rest}>
      {children}
    </div>
  );
}

export function CardHead({ title, subtitle, actions, children }) {
  return (
    <div className="card-head">
      <div>
        {title && <h3>{title}</h3>}
        {subtitle && <div className="small muted">{subtitle}</div>}
        {children}
      </div>
      {actions && <div className="row row-gap-2">{actions}</div>}
    </div>
  );
}

/* ------------------------------------------------------------------ Fields */

export function Field({ label, error, hint, children, className = '', span2 = false }) {
  return (
    <div className={`field ${span2 ? 'span-2' : ''} ${className}`.trim()}>
      {label && <label>{label}</label>}
      {children}
      {error ? <span className="err">{error}</span> : hint ? <span className="hint">{hint}</span> : null}
    </div>
  );
}

export const Input = forwardRef(function Input({ error, className = '', ...rest }, ref) {
  return <input ref={ref} className={`input ${error ? 'invalid' : ''} ${className}`.trim()} {...rest} />;
});

export const Textarea = forwardRef(function Textarea({ error, className = '', ...rest }, ref) {
  return <textarea ref={ref} className={`textarea ${error ? 'invalid' : ''} ${className}`.trim()} {...rest} />;
});

export function Select({ error, className = '', children, ...rest }) {
  return (
    <select className={`select ${error ? 'invalid' : ''} ${className}`.trim()} {...rest}>
      {children}
    </select>
  );
}

export function Checkbox({ label, ...rest }) {
  return (
    <label className="checkline">
      <input type="checkbox" {...rest} />
      <span>{label}</span>
    </label>
  );
}

/* ------------------------------------------------------------------- Badge */

export function Badge({ tone = 'neutral', children, dot = false }) {
  return (
    <span className={`badge badge-${tone}`}>
      {dot && <span className="badge-dot" aria-hidden="true" />}
      {children}
    </span>
  );
}

/* --------------------------------------------------------------- Skeletons */

export function Skeleton({ width, height = 12, className = '', style }) {
  return <div className={`skel ${className}`.trim()} style={{ width, height, ...style }} />;
}

export function SkeletonText({ lines = 3, widths }) {
  const w = widths ?? ['100%', '92%', '68%', '84%', '75%'];
  return (
    <div className="stack-sm">
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} width={w[i % w.length]} height={12} />
      ))}
    </div>
  );
}

export function SkeletonCards({ count = 6, height = 190 }) {
  return (
    <div className="grid grid-auto">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="card card-pad stack-sm">
          <Skeleton width="55%" height={18} />
          <Skeleton width="32%" height={11} />
          <Skeleton width="100%" height={height / 3} style={{ marginTop: 10 }} />
          <div className="row row-gap-2" style={{ marginTop: 12 }}>
            <Skeleton width={80} height={26} />
            <Skeleton width={64} height={26} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function SkeletonTable({ rows = 6, cols = 5 }) {
  return (
    <div className="table-wrap">
      <table className="tbl">
        <thead>
          <tr>
            {Array.from({ length: cols }).map((_, i) => (
              <th key={i}>
                <Skeleton width={70} height={9} />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }).map((_, r) => (
            <tr key={r}>
              {Array.from({ length: cols }).map((_, c) => (
                <td key={c}>
                  <Skeleton width={c === 0 ? '70%' : '52%'} height={11} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ------------------------------------------------------------------ States */

export function EmptyState({ icon = '🧭', title, message, action }) {
  return (
    <div className="state">
      <div className="state-icon" aria-hidden="true">
        {icon}
      </div>
      <h3>{title}</h3>
      {message && <p>{message}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="state state-error">
      <div className="state-icon" aria-hidden="true">
        ⚠
      </div>
      <h3>We could not load this</h3>
      <p>{message}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------- Modal */

export function Modal({ open, title, onClose, children, footer, wide = false }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  // Rendered on <body>: pages animate in with a transform, which would otherwise pin this
  // "fixed" overlay to the page instead of the screen and push the dialog out of view.
  return createPortal(
    <div
      className="modal-scrim"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose?.();
      }}
    >
      <div className={`modal${wide ? ' modal-wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button type="button" className="toast-close" onClick={onClose} aria-label="Close dialog">
            &times;
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

/* ------------------------------------------------------------- Formatting */

export const money = (value, currency = 'USD') => {
  const n = Number(value ?? 0);
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(n);
};

export const dateFmt = (value) => {
  if (!value) return '—';
  const d = typeof value === 'string' && value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

export const dateTimeFmt = (value) => {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

/** "in 12 days" / "6 days ago" — used on trip dates and permit expiry. */
export const relativeDays = (value) => {
  if (!value) return '';
  const d = typeof value === 'string' && value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = Math.round((d - today) / 86400000);
  if (diff === 0) return 'today';
  if (diff === 1) return 'tomorrow';
  if (diff === -1) return 'yesterday';
  return diff > 0 ? `in ${diff} days` : `${Math.abs(diff)} days ago`;
};

export const titleCase = (s) =>
  (s ?? '')
    .toString()
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());

/** Today in YYYY-MM-DD, for date-input min attributes. */
export const todayISO = () => new Date().toISOString().slice(0, 10);

export const isoPlusDays = (days) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
};
