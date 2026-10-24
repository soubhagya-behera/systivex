import { createContext, useCallback, useContext, useMemo, useState } from 'react';

/**
 * In-memory bearer-token store (DECISIONS.md D33).
 *
 * The token lives only in React state and is lost on reload. It is never
 * written to localStorage, sessionStorage, IndexedDB, cookies, the URL, or logs
 * (there is intentionally no browser-storage access anywhere in this module).
 */
const TokenContext = createContext(null);

export function TokenProvider({ children }) {
  const [token, setTokenState] = useState(null);

  const setToken = useCallback((value) => {
    setTokenState(value && value.trim() ? value.trim() : null);
  }, []);
  const clear = useCallback(() => setTokenState(null), []);

  const value = useMemo(
    () => ({ token, setToken, clear, hasToken: Boolean(token) }),
    [token, setToken, clear],
  );

  return <TokenContext.Provider value={value}>{children}</TokenContext.Provider>;
}

export function useToken() {
  const ctx = useContext(TokenContext);
  if (!ctx) {
    throw new Error('useToken must be used inside a TokenProvider');
  }
  return ctx;
}
