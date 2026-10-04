import { useMemo } from 'react';
import { COAST, MAP_PARKS } from '../../config/lanka';

const S = 118;
const LON0 = 79.55;
const LAT0 = 9.97;
const xy = ([lon, lat]) => [(lon - LON0) * S, (LAT0 - lat) * S];

/** Closed Catmull-Rom spline through the coastline points, as cubic Bézier segments. */
function smoothPath(points, tension = 0.42) {
  const p = points.map(xy);
  const n = p.length;
  let d = `M${p[0][0].toFixed(1)},${p[0][1].toFixed(1)}`;
  for (let i = 0; i < n; i += 1) {
    const p0 = p[(i - 1 + n) % n];
    const p1 = p[i];
    const p2 = p[(i + 1) % n];
    const p3 = p[(i + 2) % n];
    const c1 = [p1[0] + ((p2[0] - p0[0]) * tension) / 3, p1[1] + ((p2[1] - p0[1]) * tension) / 3];
    const c2 = [p2[0] - ((p3[0] - p1[0]) * tension) / 3, p2[1] - ((p3[1] - p1[1]) * tension) / 3];
    d += ` C${c1[0].toFixed(1)},${c1[1].toFixed(1)} ${c2[0].toFixed(1)},${c2[1].toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`;
  }
  return `${d} Z`;
}

const WIDTH = (82 - LON0) * S;
const HEIGHT = (LAT0 - 5.8) * S;

/** Stylised island map with national park pins. Hovering or focusing a pin selects it. */
export default function SriLankaMap({ active, onPick }) {
  const land = useMemo(() => smoothPath(COAST), []);

  return (
    <svg
      className="lk-map"
      viewBox={`-40 -10 ${WIDTH + 80} ${HEIGHT + 20}`}
      role="img"
      aria-label="Map of Sri Lanka showing national parks"
    >
      {[6, 7, 8, 9].map((lat) => {
        const y = xy([0, lat])[1];
        return <line key={lat} className="grid-line" x1="0" x2={WIDTH} y1={y} y2={y} />;
      })}
      <path className="land" d={land} />
      <text className="sea" x="-30" y={xy([0, 8.9])[1]}>Gulf of</text>
      <text className="sea" x="-30" y={xy([0, 8.9])[1] + 15}>Mannar</text>
      <text className="sea" x={xy([81.5, 0])[0]} y={xy([0, 9.5])[1]}>Bay of Bengal</text>
      <text className="sea" x={xy([80.9, 0])[0]} y={xy([0, 5.86])[1]}>Indian Ocean</text>
      {MAP_PARKS.map((park) => {
        const [x, y] = xy([park.lon, park.lat]);
        const tx = park.left ? x - 10 : x + 10;
        return (
          <g
            key={park.id}
            className={`pin${park.major ? ' major' : ''}${active === park.id ? ' on' : ''}`}
            tabIndex={0}
            onMouseEnter={() => onPick?.(park.id)}
            onFocus={() => onPick?.(park.id)}
            onClick={() => onPick?.(park.id)}
          >
            <title>{park.label}</title>
            <circle className="ring" cx={x} cy={y} r="6" />
            <circle className="core" cx={x} cy={y} r={active === park.id ? 6 : 4} />
            <text x={tx} y={y + 3.5} textAnchor={park.left ? 'end' : 'start'}>
              {park.label}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
