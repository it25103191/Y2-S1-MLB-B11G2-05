/** One place for the brand, so a rename touches a single file. */
export const BRAND = {
  name: 'Ceylon Trails',
  tagline: 'Wild Sri Lanka',
  staffTagline: 'Operations console',
  blurb: 'Tailor-made and small-group wildlife safaris across Sri Lanka, planned by naturalists who live here.',
};

/** Builds an Unsplash CDN URL at a sensible size for the slot it fills. */
export const unsplash = (id, width = 1400) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=78`;
