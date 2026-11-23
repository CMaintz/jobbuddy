/** Generation defaults have no backend home yet, so they live in localStorage. */
export interface GenDefaults {
  voice: string;
  lang: string;
  length: string;
  /** Default CV template id used by Quick apply (undefined = system default). */
  cvTemplate?: string;
}

const GEN_DEFAULTS_KEY = 'jb-gen-defaults';

export function loadGenDefaults(): GenDefaults {
  try {
    return { voice: 'Warm', lang: 'English', length: 'Standard', ...JSON.parse(localStorage.getItem(GEN_DEFAULTS_KEY) ?? '{}') };
  } catch {
    return { voice: 'Warm', lang: 'English', length: 'Standard' };
  }
}

export function saveGenDefaults(defaults: GenDefaults): void {
  localStorage.setItem(GEN_DEFAULTS_KEY, JSON.stringify(defaults));
}
