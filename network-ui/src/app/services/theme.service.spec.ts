import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { DEFAULT_THEME, THEME_STORAGE_KEY, THEMES, ThemeId, ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  it('lists exactly the four themes, gunmetal first', () => {
    const ids = THEMES.map((theme) => theme.id);
    expect(ids).toEqual(['gunmetal', 'daylight', 'phosphor', 'high-contrast']);
  });

  it('defaults to gunmetal when nothing is stored', () => {
    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe(DEFAULT_THEME);
    expect(document.documentElement.dataset['theme']).toBe('gunmetal');
  });

  it('falls back to gunmetal when the stored value is invalid', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'not-a-real-theme');

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('gunmetal');
  });

  it('falls back to gunmetal when the stored value is missing entirely', () => {
    localStorage.removeItem(THEME_STORAGE_KEY);

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('gunmetal');
  });

  it('picks up a valid stored theme on construction', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'phosphor');

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('phosphor');
    expect(document.documentElement.dataset['theme']).toBe('phosphor');
  });

  it('setTheme updates the signal, the document attribute and storage together', () => {
    const service = TestBed.inject(ThemeService);

    service.setTheme('daylight');

    expect(service.theme()).toBe('daylight');
    expect(document.documentElement.dataset['theme']).toBe('daylight');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('daylight');
  });

  it('setTheme can move through every theme in turn', () => {
    const service = TestBed.inject(ThemeService);
    const ids: readonly ThemeId[] = ['phosphor', 'high-contrast', 'daylight', 'gunmetal'];

    for (const id of ids) {
      service.setTheme(id);
      expect(service.theme()).toBe(id);
      expect(document.documentElement.dataset['theme']).toBe(id);
      expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe(id);
    }
  });

  it('does not throw when storage access fails', () => {
    const originalGetItem = Storage.prototype.getItem;
    const originalSetItem = Storage.prototype.setItem;
    Storage.prototype.getItem = () => {
      throw new Error('storage blocked');
    };
    Storage.prototype.setItem = () => {
      throw new Error('storage blocked');
    };

    try {
      expect(() => {
        const service = TestBed.inject(ThemeService);
        service.setTheme('high-contrast');
      }).not.toThrow();
    } finally {
      Storage.prototype.getItem = originalGetItem;
      Storage.prototype.setItem = originalSetItem;
    }
  });
});
