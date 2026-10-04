import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi } from '../api/api';
import { getToken, setUnauthorizedHandler, storeToken } from '../api/client';

const AuthContext = createContext(null);

export const ROLE_LABELS = {
  CUSTOMER: 'Customer',
  OPERATIONS_MANAGER: 'Operations Manager',
  CUSTOMER_RELATIONS_OFFICER: 'Customer Relations Officer',
  SAFARI_VEHICLE_COORDINATOR: 'Safari Vehicle Coordinator',
  FINANCE_RESERVATIONS_EXECUTIVE: 'Finance & Reservations Executive',
  FINANCE_ACCOUNTS_OFFICER: 'Finance Accounts Officer',
};

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [booting, setBooting] = useState(true);

  const logout = useCallback(() => {
    storeToken(null);
    setUser(null);
  }, []);

  // Let the axios interceptor end the session when the API rejects our token.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      storeToken(null);
      setUser(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  // Rehydrate from a stored token on first paint / hard refresh.
  useEffect(() => {
    let cancelled = false;
    async function boot() {
      if (!getToken()) {
        setBooting(false);
        return;
      }
      try {
        const me = await authApi.me();
        if (!cancelled) setUser(me);
      } catch {
        storeToken(null);
        if (!cancelled) setUser(null);
      } finally {
        if (!cancelled) setBooting(false);
      }
    }
    boot();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const res = await authApi.login({ email, password });
    storeToken(res.token);
    setUser(res.user);
    return res.user;
  }, []);

  const register = useCallback(async (payload) => {
    const res = await authApi.register(payload);
    storeToken(res.token);
    setUser(res.user);
    return res.user;
  }, []);

  /** Merges fields into the signed-in user after a profile change, without a round trip. */
  const updateUser = useCallback((patch) => {
    setUser((u) => (u ? { ...u, ...patch } : u));
  }, []);

  const value = useMemo(
    () => ({
      user,
      booting,
      login,
      register,
      logout,
      updateUser,
      isAuthenticated: !!user,
      isStaff: !!user && user.role !== 'CUSTOMER',
      isCustomer: user?.role === 'CUSTOMER',
      hasRole: (...roles) => !!user && roles.includes(user.role),
    }),
    [user, booting, login, register, logout, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
