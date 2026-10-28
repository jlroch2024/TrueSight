// The logos' settings for a screen of tiles: the Logo.dev token from the backend, and the website domains of the
// companies without a ticker, so their logos can be found too. Both are niceties: if either cannot be loaded, tiles
// and the graph show initials.
import { useEffect, useMemo, useState } from 'react';
import { logoApi } from '../api/logos';
import { settingsApi } from '../api/settings';
import type { LogoSettings } from './Tile';

/** names: the stored names of the companies on the screen that have no ticker. */
export function useLogoSettings(names: string[]): LogoSettings {
  // Empty until the settings arrive, or when no Logo.dev token is set.
  const [token, setToken] = useState('');
  const [domains, setDomains] = useState<Record<string, string>>({});
  // The same names in any order ask once.
  const wanted = useMemo(() => [...new Set(names)].sort().join('\n'), [names]);

  useEffect(() => {
    let active = true;
    settingsApi
      .get()
      .then((settings) => {
        if (active) setToken(settings.logoToken ?? '');
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  // Only with a Logo.dev token. A failure just leaves their letters.
  useEffect(() => {
    if (!token || !wanted) return;
    let active = true;
    logoApi
      .domains(wanted.split('\n'))
      .then((found) => {
        if (active) setDomains(found);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [token, wanted]);

  return useMemo(() => ({ token, domains }), [token, domains]);
}
