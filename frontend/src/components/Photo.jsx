import { useState } from 'react';
import { BrandMark } from './Brand';

const isUrl = (v) => typeof v === 'string' && /^https?:\/\//i.test(v);

/**
 * A photograph that fades in once loaded. Anything that is not a URL (older packages store an
 * art slug) or that fails to load falls back to a branded placeholder with the caption.
 */
export function Photo({ src, alt = '', caption, className = '', style, badge, eager = false, children }) {
  const [state, setState] = useState('loading');
  const usable = isUrl(src) && state !== 'error';

  return (
    <div className={`photo-frame ${className}`.trim()} style={style}>
      {usable ? (
        <img
          src={src}
          alt={alt}
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
          className={state === 'loaded' ? 'loaded' : ''}
          onLoad={() => setState('loaded')}
          onError={() => setState('error')}
        />
      ) : (
        <div className="photo-fallback">
          <BrandMark />
          {caption && <span>{caption}</span>}
        </div>
      )}
      {badge && <span className="photo-badge">{badge}</span>}
      {children}
    </div>
  );
}
