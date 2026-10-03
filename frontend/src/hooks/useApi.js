import { useCallback, useEffect, useRef, useState } from 'react';
import { errorMessage } from '../api/client';

/**
 * Runs an async loader and tracks loading / error / data.
 *
 * `deps` behaves like a useEffect dependency list: change them and the loader re-runs,
 * which is what powers the dashboard filters and search re-queries.
 */
export function useApi(loader, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState(null);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const reqId = useRef(0);

  const run = useCallback(async () => {
    const id = ++reqId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await loaderRef.current();
      // Ignore results from a superseded request so fast filter changes cannot race.
      if (id === reqId.current) setData(result);
      return result;
    } catch (err) {
      if (id === reqId.current) setError(errorMessage(err));
      return null;
    } finally {
      if (id === reqId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (immediate) run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, loading, error, reload: run, setData };
}
