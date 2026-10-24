import { useCallback, useEffect, useRef, useState } from 'react';

const IDLE = { status: 'idle', data: null, error: null };

/**
 * Load a resource with explicit idle/loading/success/error states.
 *
 * A failed request is always reported as `error` — never silently rendered as
 * empty data. Comparison order of `deps` controls reloads.
 */
export function useAsyncResource(loader, deps = []) {
  const [state, setState] = useState(IDLE);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const aliveRef = useRef(true);

  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  const reload = useCallback(async () => {
    setState({ status: 'loading', data: null, error: null });
    try {
      const data = await loaderRef.current();
      if (aliveRef.current) {
        setState({ status: 'success', data, error: null });
      }
    } catch (error) {
      if (aliveRef.current) {
        setState({ status: 'error', data: null, error });
      }
    }
  }, []);

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { ...state, reload };
}
