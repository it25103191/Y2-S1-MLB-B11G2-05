/**
 * Editorial content for the public site: photography, the island map, the season calendar and
 * trip styles. Catalogue data (parks, packages, prices, seats) always comes from the API.
 */
import { unsplash } from '../brand';

export const HERO_SLIDES = [
  { src: unsplash('photo-1705936981595-dea87508ce84', 1800), big: 'Still', small: 'is how you find them', place: 'YALA · BLOCK I', time: '06:12' },
  { src: unsplash('photo-1674556275189-e78fd6223e6d', 1800), big: 'Gather', small: 'three hundred at one lake', place: 'MINNERIYA', time: '17:40' },
  { src: unsplash('photo-1520646924857-261be3037bc7', 1800), big: 'Deep', small: 'blue whales off the south', place: 'MIRISSA', time: '07:05' },
];

export const AUTH_PHOTO = unsplash('photo-1566650576880-6740b03eaad1', 1600);

/**
 * Map pins, keyed by a slug. `match` finds the park in the API by name, so a park created by
 * staff lights up its pin as soon as its name mentions the place.
 */
export const MAP_PARKS = [
  { id: 'yala', label: 'Yala', lon: 81.4, lat: 6.38, major: true, match: /yala/i },
  { id: 'wilpattu', label: 'Wilpattu', lon: 80.03, lat: 8.43, major: true, match: /wilpattu/i },
  { id: 'minneriya', label: 'Minneriya', lon: 80.85, lat: 8.03, major: true, match: /minneriya/i },
  { id: 'udawalawe', label: 'Udawalawe', lon: 80.88, lat: 6.47, major: true, left: true, match: /udawalawe/i },
  { id: 'kaudulla', label: 'Kaudulla', lon: 80.9, lat: 8.17, match: /kaudulla/i },
  { id: 'wasgamuwa', label: 'Wasgamuwa', lon: 80.93, lat: 7.72, match: /wasgamuwa/i },
  { id: 'galoya', label: 'Gal Oya', lon: 81.43, lat: 7.22, left: true, match: /gal oya/i },
  { id: 'kumana', label: 'Kumana', lon: 81.7, lat: 6.56, left: true, match: /kumana/i },
  { id: 'bundala', label: 'Bundala', lon: 81.22, lat: 6.2, left: true, match: /bundala/i },
  { id: 'horton', label: 'Horton Plains', lon: 80.8, lat: 6.81, left: true, match: /horton/i },
  { id: 'sinharaja', label: 'Sinharaja', lon: 80.47, lat: 6.41, left: true, match: /sinharaja/i },
  { id: 'mirissa', label: 'Mirissa', lon: 80.46, lat: 5.97, match: /mirissa/i },
  { id: 'trinco', label: 'Trincomalee', lon: 81.22, lat: 8.58, left: true, match: /trinco/i },
  { id: 'kalpitiya', label: 'Kalpitiya', lon: 79.78, lat: 8.22, match: /kalpitiya/i },
];

/** Photography for parks we know; anything else falls back to the branded placeholder. */
export const PARK_PHOTOS = [
  { match: /yala/i, src: unsplash('photo-1661768508643-e260f6f8e06c', 1000) },
  { match: /wilpattu/i, src: unsplash('photo-1734121512475-2e063f8d9769', 1000) },
  { match: /minneriya|kaudulla/i, src: unsplash('photo-1534545872802-0579930815c2', 1000) },
  { match: /udawalawe/i, src: unsplash('photo-1509763877072-959eda51e6d6', 1000) },
];

export const parkPhoto = (name = '') => PARK_PHOTOS.find((p) => p.match.test(name))?.src ?? null;
export const mapIdForPark = (name = '') => MAP_PARKS.find((p) => p.match.test(name))?.id ?? null;

/** Stylised coastline, clockwise from Point Pedro, as (lon, lat). */
export const COAST = [
  [80.23, 9.83], [80.05, 9.82], [79.9, 9.76], [79.87, 9.7], [80.0, 9.66], [80.16, 9.62], [80.33, 9.585],
  [80.2, 9.545], [80.07, 9.5], [80.03, 9.3], [79.97, 9.06], [79.9, 8.98], [79.72, 9.07], [79.74, 9.1],
  [79.93, 9.0], [79.94, 8.8], [79.88, 8.56], [79.72, 8.3], [79.77, 7.95], [79.8, 7.57], [79.83, 7.21],
  [79.85, 6.93], [79.96, 6.58], [79.99, 6.42], [80.1, 6.14], [80.22, 6.03], [80.43, 5.97], [80.59, 5.92],
  [80.8, 6.02], [81.12, 6.12], [81.33, 6.22], [81.52, 6.33], [81.72, 6.53], [81.84, 6.88], [81.87, 7.08],
  [81.82, 7.41], [81.7, 7.72], [81.47, 8.13], [81.36, 8.3], [81.3, 8.52], [81.21, 8.62], [81.18, 8.72],
  [81.08, 8.84], [80.97, 8.95], [80.82, 9.27], [80.55, 9.47], [80.42, 9.57], [80.34, 9.72],
];

export const TRIP_STYLES = [
  { title: 'Leopard trails', parks: 'Yala · Wilpattu · Kumana', search: 'leopard' },
  { title: 'The Gathering', parks: 'Minneriya · Kaudulla · Wasgamuwa', search: 'gathering' },
  { title: 'Whales & dolphins', parks: 'Mirissa · Trincomalee · Kalpitiya', search: 'whale' },
  { title: 'Sloth bears & night', parks: 'Wilpattu · Yala', search: 'sloth' },
  { title: 'Family safaris', parks: 'Udawalawe · Minneriya', search: 'family' },
  { title: 'Short breaks', parks: 'Two or three nights from Colombo', maxDays: 3 },
];

const NE = 'North-east monsoon · rain in the north and east, the south stays dry';
const IM1 = 'First inter-monsoon · hot, with afternoon storms';
const SW = 'South-west monsoon · wet south-west coast, dry zone parks at their driest';
const IM2 = 'Second inter-monsoon · island-wide showers, parks turn green';

/** Indicative conditions by month. `rating` is 1-5; 0 means the park is usually closed. */
export const SEASONS = [
  { title: 'January: the south is <em>dry</em>.', monsoon: NE, rain: 45, copy: 'The north-east monsoon waters the east; the south and west are clear and green. Whale season is in full swing off Mirissa.', picks: [['Mirissa', 'Blue whales on the continental shelf', 5], ['Bundala', 'Flamingos and winter migrants', 5], ['Yala', 'Green season, leopards on the rocks', 4], ['Udawalawe', 'Elephants in every season', 4]] },
  { title: 'February: the <em>clear</em> month.', monsoon: NE, rain: 30, copy: 'Settled weather almost everywhere. Lakes are still full in Wilpattu and the high plains are crisp at dawn.', picks: [['Mirissa', 'Calm seas, reliable blue whale sightings', 5], ['Yala', 'Good light, mating leopards', 4], ['Wilpattu', 'Lakes full, forest opening up', 4], ['Horton Plains', 'Frosty mornings, sambar on the plains', 4]] },
  { title: 'March: leopards <em>on the move</em>.', monsoon: IM1, rain: 25, copy: 'Water holes begin to shrink and game concentrates around them. Our favourite month for Yala and Wilpattu together.', picks: [['Yala', 'Drying water holes concentrate game', 5], ['Wilpattu', 'Leopards walking the villu edges', 5], ['Sinharaja', 'Dry spell for mixed bird flocks', 4], ['Mirissa', 'Whales before the swell', 4]] },
  { title: 'April: <em>inter-monsoon</em> heat.', monsoon: IM1, rain: 40, copy: 'Hot mornings, thundery afternoons. Plan drives early and late, and let the midday storms roll through.', picks: [['Wilpattu', 'Sloth bears feeding on palu fruit', 5], ['Yala', 'Leopards and early sloth bears', 4], ['Kumana', 'Waders arriving to nest', 4], ['Mirissa', 'Season winding down', 3]] },
  { title: 'May: the <em>south-west</em> turns.', monsoon: SW, rain: 55, copy: 'Rain arrives on the south-west coast, while the dry zone parks go golden. Whales begin to move to the east coast.', picks: [['Kumana', 'Nesting season at Kumana Villu', 5], ['Yala', 'Dry, open, excellent visibility', 4], ['Wilpattu', 'Sloth bears still active', 4], ['Trincomalee', 'Whales arriving in the east', 3]] },
  { title: 'June: <em>drought</em> brings them out.', monsoon: SW, rain: 50, copy: 'The dry zone is at its driest and every animal comes to the last water. Peak season for leopards and sloth bears.', picks: [['Yala', 'Leopards and sloth bears at the last water', 5], ['Kumana', 'Painted storks and spoonbills', 5], ['Trincomalee', 'Blue whales close to shore', 4], ['Gal Oya', 'Elephants swimming between islands', 4]] },
  { title: 'July: the <em>Gathering</em> begins.', monsoon: SW, rain: 45, copy: 'As Minneriya tank recedes, herds walk in from the surrounding forest every afternoon. Yala stays excellent.', picks: [['Yala', 'Peak leopard season', 5], ['Minneriya', 'Herds start arriving at the tank', 4], ['Trincomalee', 'Whales and dolphins in calm seas', 4], ['Gal Oya', 'Boat safaris on the reservoir', 4]] },
  { title: 'August: the <em>Gathering</em>.', monsoon: SW, rain: 40, copy: 'Up to three hundred elephants on one lakeshore — the largest gathering of Asian elephants anywhere.', picks: [['Minneriya', 'Up to 300 elephants on one lakeshore', 5], ['Kaudulla', "The herds' second pasture", 5], ['Trincomalee', 'Blue whales close to shore', 5], ['Yala', 'Last weeks before the annual closure', 4]] },
  { title: 'September: <em>elephants</em>, and whales in the east.', monsoon: SW, rain: 35, copy: "The Gathering is at its height and the east coast is calm. Yala's main block closes for about six weeks to let the park rest.", picks: [['Minneriya', 'The Gathering at its height', 5], ['Kaudulla', 'Herds drifting north as water drops', 5], ['Trincomalee', 'Blue whales and spinner dolphins', 5], ['Yala Block I', 'Annual closure, usually into October', 0]] },
  { title: 'October: the <em>first rains</em>.', monsoon: IM2, rain: 65, copy: 'Island-wide showers turn the parks green. Yala reopens mid-month, and the herds move on from Minneriya.', picks: [['Kaudulla', 'The Gathering moves as Minneriya fills', 4], ['Udawalawe', 'Calves and green grass', 4], ['Yala', 'Reopens mid-month, quiet roads', 3], ['Wilpattu', 'Lush, with fewer visitors', 3]] },
  { title: 'November: the <em>migrants</em> arrive.', monsoon: IM2, rain: 60, copy: 'Flamingos and waders fly in from the north, and whale season opens again off the south coast.', picks: [['Bundala', 'Flamingos and migratory waders', 5], ['Mirissa', 'Whale season opens', 4], ['Udawalawe', 'Elephants in every season', 4], ['Sinharaja', 'Rainforest at its richest', 3]] },
  { title: 'December: the <em>festive</em> south.', monsoon: NE, rain: 45, copy: 'The south and west dry out for the high season. Book early — jeep slots in Yala fill weeks ahead.', picks: [['Mirissa', 'Blue whales most mornings', 5], ['Bundala', 'Winter migrants at their peak', 5], ['Yala', 'Green, lively, busy — go early', 4], ['Horton Plains', "Clear views from World's End", 3]] },
];

export const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
