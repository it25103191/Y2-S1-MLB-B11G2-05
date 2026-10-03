import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';

const ToastContext = createContext(null);

const ICONS = {
  success: '✓',
  error: '✕',
  warning: '⚠',
  info: 'ℹ',
};

const DEFAULT_TITLES = {
  success: 'Done',
  error: 'Something went wrong',
  warning: 'Heads up',
  info: 'Note',
};

let seq = 0;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef(new Map());

  const remove = useCallback((id) => {
    // Play the exit animation first, then drop the node.
    setToasts((list) => list.map((t) => (t.id === id ? { ...t, leaving: true } : t)));
    const t = setTimeout(() => {
      setToasts((list) => list.filter((x) => x.id !== id));
      timers.current.delete(id);
    }, 240);
    timers.current.set(id, t);
  }, []);

  const push = useCallback(
    (variant, message, title, duration = 4200) => {
      const id = ++seq;
      setToasts((list) => [
        ...list,
        { id, variant, message, title: title ?? DEFAULT_TITLES[variant], leaving: false },
      ]);
      const timer = setTimeout(() => remove(id), duration);
      timers.current.set(`auto-${id}`, timer);
      return id;
    },
    [remove],
  );

  const api = useMemo(
    () => ({
      success: (msg, title) => push('success', msg, title),
      error: (msg, title) => push('error', msg, title, 6000),
      warning: (msg, title) => push('warning', msg, title, 5200),
      info: (msg, title) => push('info', msg, title),
      dismiss: remove,
    }),
    [push, remove],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-host" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.variant}${t.leaving ? ' leaving' : ''}`}>
            <span className="toast-icon" aria-hidden="true">
              {ICONS[t.variant]}
            </span>
            <div className="toast-body">
              <div className="toast-title">{t.title}</div>
              {t.message && <div className="toast-msg">{t.message}</div>}
            </div>
            <button
              type="button"
              className="toast-close"
              onClick={() => remove(t.id)}
              aria-label="Dismiss notification"
            >
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
