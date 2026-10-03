import { client } from './client';

const unwrap = (p) => p.then((r) => r.data);

/** Drops null/undefined/'' so optional filters never become `?minPrice=` in the URL. */
function clean(params = {}) {
  const out = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== null && v !== undefined && v !== '') out[k] = v;
  });
  return out;
}

export const authApi = {
  login: (payload) => unwrap(client.post('/auth/login', payload)),
  register: (payload) => unwrap(client.post('/auth/register', payload)),
  me: () => unwrap(client.get('/auth/me')),
  updateMe: (payload) => unwrap(client.patch('/auth/me', payload)),
  changePassword: (payload) => unwrap(client.post('/auth/me/password', payload)),
  closeAccount: (password) => unwrap(client.delete('/auth/me', { data: { password } })),
};

export const staffAccountApi = {
  list: () => unwrap(client.get('/staff-accounts')),
  create: (payload) => unwrap(client.post('/staff-accounts', payload)),
  update: (id, payload) => unwrap(client.put(`/staff-accounts/${id}`, payload)),
  setActive: (id, active) => unwrap(client.patch(`/staff-accounts/${id}/status`, { active })),
  remove: (id) => unwrap(client.delete(`/staff-accounts/${id}`)),
};

export const currencyApi = {
  rates: () => unwrap(client.get('/currency')),
  update: (code, unitsPerUsd) => unwrap(client.put(`/currency/${code}`, { unitsPerUsd })),
};

export const parkApi = {
  list: (activeOnly = false) => unwrap(client.get('/parks', { params: { activeOnly } })),
  get: (id) => unwrap(client.get(`/parks/${id}`)),
  create: (payload) => unwrap(client.post('/parks', payload)),
  update: (id, payload) => unwrap(client.put(`/parks/${id}`, payload)),
  remove: (id) => unwrap(client.delete(`/parks/${id}`)),
};

export const packageApi = {
  list: (filters = {}) => unwrap(client.get('/packages', { params: clean(filters) })),
  get: (id) => unwrap(client.get(`/packages/${id}`)),
  availability: (id, date) => unwrap(client.get(`/packages/${id}/availability`, { params: { date } })),
  quote: (id, date, participants, excludeBookingId) =>
    unwrap(client.get(`/packages/${id}/quote`, { params: clean({ date, participants, excludeBookingId }) })),
  create: (payload) => unwrap(client.post('/packages', payload)),
  update: (id, payload) => unwrap(client.put(`/packages/${id}`, payload)),
  setActive: (id, value) => unwrap(client.patch(`/packages/${id}/active`, null, { params: { value } })),
  remove: (id) => unwrap(client.delete(`/packages/${id}`)),
};

export const reportApi = {
  dashboard: (from, to) => unwrap(client.get('/reports/dashboard', { params: clean({ from, to }) })),
};

export const kpiTargetApi = {
  list: () => unwrap(client.get('/kpi-targets')),
  get: (id) => unwrap(client.get(`/kpi-targets/${id}`)),
  create: (payload) => unwrap(client.post('/kpi-targets', payload)),
  update: (id, payload) => unwrap(client.put(`/kpi-targets/${id}`, payload)),
  remove: (id) => unwrap(client.delete(`/kpi-targets/${id}`)),
};

export const paymentApi = {
  list: () => unwrap(client.get('/payments')),
  summary: () => unwrap(client.get('/payments/summary')),
  get: (id) => unwrap(client.get(`/payments/${id}`)),
  forBooking: (bookingId) => unwrap(client.get(`/payments/booking/${bookingId}`)),
  invoice: (bookingId) => unwrap(client.get(`/payments/invoice/${bookingId}`)),
  pay: (payload) => unwrap(client.post('/payments', payload)),
  recordOffline: (payload) => unwrap(client.post('/payments/offline', payload)),
  voidPayment: (id, reason) => unwrap(client.post(`/payments/${id}/void`, { reason })),
  remove: (id) => unwrap(client.delete(`/payments/${id}`)),
};

export const refundApi = {
  list: () => unwrap(client.get('/refunds')),
  get: (id) => unwrap(client.get(`/refunds/${id}`)),
  forBooking: (bookingId) => unwrap(client.get(`/refunds/booking/${bookingId}`)),
  quote: (bookingId) => unwrap(client.get(`/refunds/quote/${bookingId}`)),
  request: (payload) => unwrap(client.post('/refunds', payload)),
  approve: (id, payload) => unwrap(client.post(`/refunds/${id}/approve`, payload)),
  reject: (id, payload) => unwrap(client.post(`/refunds/${id}/reject`, payload)),
  process: (id) => unwrap(client.post(`/refunds/${id}/process`)),
  update: (id, payload) => unwrap(client.put(`/refunds/${id}`, payload)),
  remove: (id) => unwrap(client.delete(`/refunds/${id}`)),
};

export const permitApi = {
  list: () => unwrap(client.get('/permits')),
  dashboard: () => unwrap(client.get('/permits/dashboard')),
  expiring: (days) => unwrap(client.get('/permits/expiring', { params: clean({ days }) })),
  awaitingRequest: () => unwrap(client.get('/permits/awaiting-request')),
  forBooking: (bookingId) => unwrap(client.get(`/bookings/${bookingId}/permits`)),
  get: (id) => unwrap(client.get(`/permits/${id}`)),
  request: (payload) => unwrap(client.post('/permits', payload)),
  approve: (id, payload) => unwrap(client.post(`/permits/${id}/approve`, payload)),
  reject: (id, payload) => unwrap(client.post(`/permits/${id}/reject`, payload)),
  renew: (id, payload) => unwrap(client.post(`/permits/${id}/renew`, payload)),
  update: (id, payload) => unwrap(client.put(`/permits/${id}`, payload)),
  remove: (id) => unwrap(client.delete(`/permits/${id}`)),
};

export const complaintApi = {
  list: () => unwrap(client.get('/complaints')),
  mine: () => unwrap(client.get('/complaints/mine')),
  get: (id) => unwrap(client.get(`/complaints/${id}`)),
  timeline: (id) => unwrap(client.get(`/complaints/${id}/timeline`)),
  create: (payload) => unwrap(client.post('/complaints', payload)),
  addNote: (id, payload) => unwrap(client.post(`/complaints/${id}/notes`, payload)),
  update: (id, payload) => unwrap(client.patch(`/complaints/${id}`, payload)),
  escalate: (id, payload) => unwrap(client.post(`/complaints/${id}/escalate`, payload)),
  remove: (id) => unwrap(client.delete(`/complaints/${id}`)),
  updateNote: (id, noteId, payload) => unwrap(client.put(`/complaints/${id}/notes/${noteId}`, payload)),
  deleteNote: (id, noteId) => unwrap(client.delete(`/complaints/${id}/notes/${noteId}`)),
};

export const replyTemplateApi = {
  list: (activeOnly = false) => unwrap(client.get('/reply-templates', { params: { activeOnly } })),
  get: (id) => unwrap(client.get(`/reply-templates/${id}`)),
  create: (payload) => unwrap(client.post('/reply-templates', payload)),
  update: (id, payload) => unwrap(client.put(`/reply-templates/${id}`, payload)),
  remove: (id) => unwrap(client.delete(`/reply-templates/${id}`)),
};

export const customerApi = {
  list: () => unwrap(client.get('/customers')),
  profile: (id) => unwrap(client.get(`/customers/${id}`)),
  staffUsers: () => unwrap(client.get('/staff-users')),
};

export const notificationApi = {
  list: () => unwrap(client.get('/notifications')),
};

export const vehicleApi = {
  list: () => unwrap(client.get('/vehicles')),
  get: (id) => unwrap(client.get(`/vehicles/${id}`)),
  create: (payload) => unwrap(client.post('/vehicles', payload)),
  update: (id, payload) => unwrap(client.put(`/vehicles/${id}`, payload)),
  setStatus: (id, value) => unwrap(client.patch(`/vehicles/${id}/status`, null, { params: { value } })),
  remove: (id) => unwrap(client.delete(`/vehicles/${id}`)),
};

export const guideApi = {
  list: () => unwrap(client.get('/guides')),
  get: (id) => unwrap(client.get(`/guides/${id}`)),
  create: (payload) => unwrap(client.post('/guides', payload)),
  update: (id, payload) => unwrap(client.put(`/guides/${id}`, payload)),
  setStatus: (id, value) => unwrap(client.patch(`/guides/${id}/status`, null, { params: { value } })),
  remove: (id) => unwrap(client.delete(`/guides/${id}`)),
};

export const assignmentApi = {
  list: () => unwrap(client.get('/assignments')),
  get: (id) => unwrap(client.get(`/assignments/${id}`)),
  schedule: (from, to) => unwrap(client.get('/assignments/schedule', { params: { from, to } })),
  suggest: (bookingId, strategy) =>
    unwrap(client.get(`/assignments/suggest/${bookingId}`, { params: clean({ strategy }) })),
  create: (payload) => unwrap(client.post('/assignments', payload)),
  update: (id, payload) => unwrap(client.put(`/assignments/${id}`, payload)),
  changeStatus: (id, status) => unwrap(client.patch(`/assignments/${id}/status`, { status })),
  remove: (id) => unwrap(client.delete(`/assignments/${id}`)),
};

export const bookingApi = {
  list: () => unwrap(client.get('/bookings')),
  mine: () => unwrap(client.get('/bookings/mine')),
  get: (id) => unwrap(client.get(`/bookings/${id}`)),
  history: (id) => unwrap(client.get(`/bookings/${id}/history`)),
  awaitingAssignment: () => unwrap(client.get('/bookings/awaiting-assignment')),
  create: (payload) => unwrap(client.post('/bookings', payload)),
  cancel: (id, reason) => unwrap(client.post(`/bookings/${id}/cancel`, { reason })),
  update: (id, payload) => unwrap(client.put(`/bookings/${id}`, payload)),
  remove: (id) => unwrap(client.delete(`/bookings/${id}`)),
  changeStatus: (id, status) => unwrap(client.patch(`/bookings/${id}/status`, { status })),
};
