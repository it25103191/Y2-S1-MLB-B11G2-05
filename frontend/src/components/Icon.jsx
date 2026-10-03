/**
 * A small set of 1.5px line icons, drawn on a 24px grid, used in the staff navigation and in
 * a few controls. Keeps the console consistent instead of mixing emoji styles across platforms.
 */
const PATHS = {
  dashboard: 'M4 13h6V4H4zM14 20h6v-9h-6zM4 20h6v-4H4zM14 4v4h6V4z',
  target: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 12h.01',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  package: 'M4 8l8-4 8 4v8l-8 4-8-4zM4 8l8 4 8-4M12 12v8',
  park: 'M12 3l7 12H5zM12 15v6M8 21h8',
  compass: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM15.5 8.5l-2 5-5 2 2-5z',
  schedule: 'M4 5h16v15H4zM4 9h16M9 13h3M9 16h7',
  car: 'M5 16V11l2-5h10l2 5v5M4 16h16v2H4zM7.5 18.5v1.5M16.5 18.5v1.5M7 12.5h.01M17 12.5h.01',
  guide: 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  permit: 'M6 3h9l3 3v15H6zM14 3v4h4M9 12h6M9 16h4',
  chat: 'M4 5h16v11H9l-5 4z',
  users: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 6.5M18 14.5a6.5 6.5 0 0 1 3.5 5.5',
  badge: 'M4 6h16v13H4zM9 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM6 16a3 3 0 0 1 6 0M14 10h4M14 14h3',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM5 20a7 7 0 0 1 14 0',
  template: 'M5 4h14v16H5zM8 8h8M8 12h8M8 16h5',
  bell: 'M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15zM10 21h4',
  card: 'M3 6h18v12H3zM3 10h18M7 15h3',
  refund: 'M4 12a8 8 0 1 0 2.3-5.6M4 4v4h4',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4',
  menu: 'M4 7h16M4 12h16M4 17h16',
  close: 'M6 6l12 12M18 6L6 18',
  globe: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z',
  external: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  exchange: 'M4 8h13l-3-3M20 16H7l3 3',
};

export function Icon({ name, size = 18, className = '', title }) {
  const d = PATHS[name];
  if (!d) return null;
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : 'true'}
      role={title ? 'img' : undefined}
    >
      {title && <title>{title}</title>}
      <path d={d} />
    </svg>
  );
}
