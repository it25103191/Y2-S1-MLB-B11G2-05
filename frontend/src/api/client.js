import axios from 'axios';

export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api';
export const TOKEN_KEY = 'safari.tms.token';

export const client = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
});

/** Callback registered by AuthContext so a rejected token can tear down the session. */
let onUnauthorized = null;
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function storeToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

client.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    // A 401 on anything other than the login call means the stored token is no longer good.
    const isLoginCall = error.config?.url?.includes('/auth/login');
    if (status === 401 && !isLoginCall && onUnauthorized) {
      onUnauthorized();
    }
    return Promise.reject(error);
  },
);

/** Turns any axios failure into a readable sentence for toasts and error states. */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) return fallback;
  const data = error.response?.data;
  if (data?.fieldErrors) {
    const first = Object.values(data.fieldErrors)[0];
    if (first) return first;
  }
  if (data?.message) return data.message;
  if (error.code === 'ECONNABORTED') return 'The server took too long to respond.';
  if (error.message === 'Network Error') {
    return 'Cannot reach the server. Make sure the backend is running on port 8080.';
  }
  return error.message || fallback;
}

/** Field-level validation errors, keyed by field name. */
export function fieldErrors(error) {
  return error?.response?.data?.fieldErrors ?? {};
}

/** Extra structured payload attached to conflict responses (capacity, clashes, ...). */
export function errorDetails(error) {
  return error?.response?.data?.details ?? null;
}
