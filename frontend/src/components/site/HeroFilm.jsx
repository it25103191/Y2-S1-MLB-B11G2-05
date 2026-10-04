import { useEffect, useState } from 'react';
import { HERO_SLIDES } from '../../config/lanka';

const SLIDE_MS = 6500;

/**
 * The home page "film": stills that crossfade with a slow zoom, a caption that tracks in letter
 * by letter, a camera-style timestamp and a progress bar per slide.
 */
export default function HeroFilm() {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => setIndex((i) => (i + 1) % HERO_SLIDES.length), SLIDE_MS);
    return () => clearTimeout(t);
  }, [index]);

  const slide = HERO_SLIDES[index];

  return (
    <div className="film" aria-label="Wildlife across Sri Lanka">
      {HERO_SLIDES.map((s, i) => (
        <div key={s.src} className={`film-slide${i === index ? ' on' : ''}`}>
          <img src={s.src} alt="" loading={i === 0 ? 'eager' : 'lazy'} fetchpriority={i === 0 ? 'high' : undefined} />
        </div>
      ))}
      {/* Keyed so the caption animation restarts with every slide. */}
      <div className="film-kinetic play" key={index} aria-hidden="true">
        <div>
          <div className="film-big">{slide.big}</div>
          <div className="film-small">{slide.small}</div>
        </div>
      </div>
      <div className="film-foot">
        <span className="film-stamp">
          {slide.place} · <b>{slide.time}</b>
        </span>
        <span className="film-progress" key={`p${index}`}>
          {HERO_SLIDES.map((s, i) => (
            <i key={s.src} className={i < index ? 'done' : i === index ? 'on' : ''} />
          ))}
        </span>
      </div>
    </div>
  );
}
