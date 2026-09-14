import { Injectable, Signal, signal } from '@angular/core';

/** The four palettes the console ships. Layout and typography never vary. */
export type ThemeId = 'gunmetal' | 'daylight' | 'phosphor' | 'high-contrast';

export interface ThemeOption {
  readonly id: ThemeId;
  readonly label: string;
  readonly description: string;
}

/** Where the chosen theme lives between reloads. */
export const THEME_STORAGE_KEY = 'siem.theme';

/** Applied whenever no preference has ever been stored, or the stored one is unusable. */
export const DEFAULT_THEME: ThemeId = 'gunmetal';

const THEME_IDS: readonly ThemeId[] = ['gunmetal', 'daylight', 'phosphor', 'high-contrast'];

/** Four real choices, each with a short description for the settings screen. */
export const THEMES: readonly ThemeOption[] = [
  {
    id: 'gunmetal',
    label: 'Gunmetal',
    description: 'Soğuk yeşil-gri tonlarında karanlık konsol teması. Varsayılan.',
  },
  {
    id: 'daylight',
    label: 'Gündüz',
    description: 'Aydınlık NOC duvarları ve parlak ortamlar için açık renkli tema.',
  },
  {
    id: 'phosphor',
    label: 'Fosfor',
    description: 'Aynı yeşil-gri ailede daha koyu ve doygun, CRT telemetri hissi veren varyant.',
  },
  {
    id: 'high-contrast',
    label: 'Yüksek Kontrast',
    description: 'Neredeyse siyah-beyaz; düşük görme durumları için AAA hedefli kontrast oranları.',
  },
];

function isThemeId(value: string | null): value is ThemeId {
  return value !== null && (THEME_IDS as readonly string[]).includes(value);
}

function readStored(): ThemeId {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return isThemeId(stored) ? stored : DEFAULT_THEME;
  } catch {
    // Private-mode browsers and blocked storage must not break bootstrap.
    return DEFAULT_THEME;
  }
}

function writeStored(id: ThemeId): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, id);
  } catch {
    // Preference then lasts only as long as this document; nothing else changes.
  }
}

/**
 * Owns the operator's chosen colour theme.
 *
 * `index.html` carries a small inline script that reads the same storage key
 * and sets `data-theme` on the document element before Angular bootstraps,
 * so the first paint never flashes the wrong theme. This service is what
 * changes the theme after that, keeping the signal, the DOM attribute and
 * `localStorage` in sync — the same read-with-fallback shape `AuthService`
 * already uses for the stored session tokens.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _theme = signal<ThemeId>(readStored());

  /** Currently active theme id. */
  readonly theme: Signal<ThemeId> = this._theme.asReadonly();

  /** The four selectable themes, in display order. */
  readonly themes: readonly ThemeOption[] = THEMES;

  constructor() {
    // Re-affirms the attribute the inline script already set. Harmless when
    // it already matches; it is what makes the attribute correct even if
    // this service is constructed after some other code touched the DOM.
    this.applyToDocument(this._theme());
  }

  setTheme(id: ThemeId): void {
    this._theme.set(id);
    this.applyToDocument(id);
    writeStored(id);
  }

  private applyToDocument(id: ThemeId): void {
    try {
      document.documentElement.dataset['theme'] = id;
    } catch {
      // No document (e.g. a non-browser test host) — the signal is still
      // correct, only the visual side effect is skipped.
    }
  }
}
