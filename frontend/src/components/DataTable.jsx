import { useMemo, useState } from 'react';
import { EmptyState, Input } from './ui';

/**
 * Client-side sortable / searchable table.
 *
 * columns: [{ key, header, render?, sortValue?, searchable?, sortable?, width?, align?, className? }]
 */
export default function DataTable({
  columns,
  rows,
  rowKey = (r, i) => r.id ?? i,
  initialSort,
  searchable = true,
  searchPlaceholder = 'Search…',
  filters = null,
  onRowClick,
  rowClassName,
  emptyTitle = 'Nothing here yet',
  emptyMessage = 'Once records exist they will appear in this table.',
  emptyIcon = '📋',
  toolbarExtra = null,
}) {
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState(initialSort ?? null); // { key, dir }

  const searchableCols = useMemo(
    () => columns.filter((c) => c.searchable !== false),
    [columns],
  );

  const cellText = (col, row) => {
    if (col.sortValue) return col.sortValue(row);
    const raw = row[col.key];
    return raw ?? '';
  };

  const filtered = useMemo(() => {
    if (!query.trim()) return rows;
    const q = query.trim().toLowerCase();
    return rows.filter((row) =>
      searchableCols.some((col) => String(cellText(col, row) ?? '').toLowerCase().includes(q)),
    );
  }, [rows, query, searchableCols]);

  const sorted = useMemo(() => {
    if (!sort) return filtered;
    const col = columns.find((c) => c.key === sort.key);
    if (!col) return filtered;
    const dir = sort.dir === 'asc' ? 1 : -1;
    return [...filtered].sort((a, b) => {
      const av = cellText(col, a);
      const bv = cellText(col, b);
      if (av === bv) return 0;
      if (av === null || av === undefined || av === '') return 1;
      if (bv === null || bv === undefined || bv === '') return -1;
      if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir;
      const an = Number(av);
      const bn = Number(bv);
      if (!Number.isNaN(an) && !Number.isNaN(bn) && String(av).trim() !== '' && String(bv).trim() !== '') {
        return (an - bn) * dir;
      }
      return String(av).localeCompare(String(bv), undefined, { numeric: true }) * dir;
    });
  }, [filtered, sort, columns]);

  const toggleSort = (col) => {
    if (col.sortable === false) return;
    setSort((cur) => {
      if (!cur || cur.key !== col.key) return { key: col.key, dir: 'asc' };
      if (cur.dir === 'asc') return { key: col.key, dir: 'desc' };
      return null;
    });
  };

  const showToolbar = searchable || filters || toolbarExtra;

  return (
    <div>
      {showToolbar && (
        <div className="table-toolbar">
          {searchable && (
            <Input
              className="search-input"
              type="search"
              value={query}
              placeholder={searchPlaceholder}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Search table"
            />
          )}
          {filters}
          <span className="spacer" />
          {toolbarExtra}
          <span className="table-count">
            {sorted.length} of {rows.length} {rows.length === 1 ? 'record' : 'records'}
          </span>
        </div>
      )}

      {sorted.length === 0 ? (
        <EmptyState
          icon={emptyIcon}
          title={rows.length === 0 ? emptyTitle : 'No matches'}
          message={
            rows.length === 0
              ? emptyMessage
              : 'No rows match your search or filters. Try widening them.'
          }
        />
      ) : (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                {columns.map((col) => {
                  const active = sort?.key === col.key;
                  return (
                    <th
                      key={col.key}
                      style={{ width: col.width, textAlign: col.align }}
                      className={col.sortable === false ? '' : 'sortable'}
                      onClick={() => toggleSort(col)}
                      scope="col"
                    >
                      {col.header}
                      {col.sortable !== false && (
                        <span className="sort-ind" aria-hidden="true">
                          {active ? (sort.dir === 'asc' ? '▲' : '▼') : ''}
                        </span>
                      )}
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {sorted.map((row, i) => (
                <tr
                  key={rowKey(row, i)}
                  className={[onRowClick ? 'clickable' : '', rowClassName?.(row) ?? '']
                    .filter(Boolean)
                    .join(' ')}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {columns.map((col) => (
                    <td
                      key={col.key}
                      style={{ textAlign: col.align }}
                      className={col.className}
                    >
                      {col.render ? col.render(row) : row[col.key]}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
