/** Complaint categories as shown to people, in the order customers see them. */
export const COMPLAINT_CATEGORIES = [
  { value: 'GENERAL_INQUIRY', label: 'General question' },
  { value: 'BOOKING', label: 'Booking problem' },
  { value: 'PAYMENT', label: 'Payment or billing' },
  { value: 'GUIDE', label: 'Guide' },
  { value: 'VEHICLE', label: 'Vehicle' },
  { value: 'PARK_EXPERIENCE', label: 'On-trip experience' },
  { value: 'OTHER', label: 'Something else' },
];

export const categoryLabel = (value) =>
  COMPLAINT_CATEGORIES.find((c) => c.value === value)?.label ?? 'Any category';

/** Placeholders a reply template may use. The server rejects any others. */
export const TEMPLATE_PLACEHOLDERS = [
  { token: 'customerName', label: 'Customer name' },
  { token: 'caseReference', label: 'Case reference' },
  { token: 'bookingReference', label: 'Booking reference' },
];

/** Replaces {{token}} placeholders with values; unknown or missing values are left readable. */
export function fillPlaceholders(text, values) {
  if (!text) return '';
  return text.replace(/\{\{\s*([A-Za-z]+)\s*}}/g, (match, token) =>
    values[token] !== undefined && values[token] !== null && values[token] !== ''
      ? values[token]
      : `[${token}]`,
  );
}
