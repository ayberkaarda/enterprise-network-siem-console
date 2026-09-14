import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { RulesPanel } from './rules-panel';
import { SiemApiService } from '../../../services/siem-api.service';
import { AuthService } from '../../../services/auth.service';
import { AuthRole, AuthUser, Page, Rule } from '../../../services/siem.models';

function page(content: Rule[]): Page<Rule> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: content.length };
}

function rule(overrides: Partial<Rule> & { id: number }): Rule {
  return {
    name: 'Ardışık cihaz flap',
    enabled: true,
    conditionJson: '{"type":"flap"}',
    thresholdCount: 3,
    windowSeconds: 300,
    severity: 'MEDIUM',
    createdAt: '2026-09-14T10:00:00Z',
    ...overrides,
  };
}

/** Minimal stand-in for AuthService: just the two members RulesPanel reads. */
class AuthStub {
  role: AuthRole = 'ADMIN';

  currentUser(): AuthUser | null {
    return { username: 'tester', role: this.role };
  }

  hasRole(...roles: readonly AuthRole[]): boolean {
    return roles.includes(this.role);
  }
}

class ApiStub {
  getRules = vi.fn().mockReturnValue(of(page([rule({ id: 1 })])));
  createRule = vi.fn().mockReturnValue(of(rule({ id: 2, name: 'Yeni kural' })));
  updateRule = vi.fn().mockReturnValue(of(rule({ id: 1, enabled: false })));
  deleteRule = vi.fn().mockReturnValue(of(undefined));
}

function buttonWithText(el: HTMLElement, text: string): HTMLButtonElement | null {
  return (
    (Array.from(el.querySelectorAll('button')) as HTMLButtonElement[]).find((b) =>
      b.textContent?.trim().includes(text),
    ) ?? null
  );
}

describe('RulesPanel', () => {
  let auth: AuthStub;
  let api: ApiStub;
  let fixture: ComponentFixture<RulesPanel>;
  let component: RulesPanel;

  function setup(role: AuthRole): void {
    auth = new AuthStub();
    auth.role = role;
    api = new ApiStub();
    TestBed.configureTestingModule({
      imports: [RulesPanel],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: SiemApiService, useValue: api },
      ],
    });
    fixture = TestBed.createComponent(RulesPanel);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('as ADMIN', () => {
    beforeEach(() => setup('ADMIN'));

    it('loads the rule list on init', () => {
      expect(api.getRules).toHaveBeenCalled();
      expect(component.rules).toHaveLength(1);
    });

    it('reports canManage as true', () => {
      expect(component.canManage).toBe(true);
    });

    it('opens the create form', () => {
      component.startCreate();
      expect(component.isEditing).toBe(true);
      expect(component.isCreating).toBe(true);
    });

    it('renders the create/edit/delete controls as enabled', () => {
      const el = fixture.nativeElement as HTMLElement;
      expect(buttonWithText(el, 'Yeni Kural')?.disabled).toBe(false);
      expect(buttonWithText(el, 'Düzenle')?.disabled).toBe(false);
      expect(buttonWithText(el, 'Sil')?.disabled).toBe(false);
    });

    it('submits a create request and prepends the saved rule', () => {
      component.startCreate();
      component.formName = 'Yeni kural';
      component.submit();
      expect(api.createRule).toHaveBeenCalledOnce();
      expect(component.rules[0].id).toBe(2);
      expect(component.isEditing).toBe(false);
    });

    it('rejects a create submission whose condition is not valid JSON', () => {
      component.startCreate();
      component.formName = 'Bozuk kural';
      component.formConditionJson = '{not json';
      component.submit();
      expect(api.createRule).not.toHaveBeenCalled();
      expect(component.formError).toContain('JSON');
    });

    it('toggles a rule enabled/disabled via PUT', () => {
      component.toggleEnabled(component.rules[0]);
      expect(api.updateRule).toHaveBeenCalledWith(1, expect.objectContaining({ enabled: false }));
    });

    it('deletes a rule after confirmation', () => {
      vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
      component.deleteRule(component.rules[0]);
      expect(api.deleteRule).toHaveBeenCalledWith(1);
      expect(component.rules).toHaveLength(0);
      vi.unstubAllGlobals();
    });

    it('does not delete when the confirmation is declined', () => {
      vi.stubGlobal('confirm', vi.fn().mockReturnValue(false));
      component.deleteRule(component.rules[0]);
      expect(api.deleteRule).not.toHaveBeenCalled();
      vi.unstubAllGlobals();
    });
  });

  describe.each<AuthRole>(['VIEWER', 'ANALYST'])('as %s', (role) => {
    beforeEach(() => setup(role));

    it('reports canManage as false', () => {
      expect(component.canManage).toBe(false);
    });

    it('still loads and shows the rule list (read access)', () => {
      expect(api.getRules).toHaveBeenCalled();
      expect(component.rules).toHaveLength(1);
    });

    it('renders the create/edit/delete/toggle controls as disabled', () => {
      const el = fixture.nativeElement as HTMLElement;
      expect(buttonWithText(el, 'Yeni Kural')?.disabled).toBe(true);
      expect(buttonWithText(el, 'Düzenle')?.disabled).toBe(true);
      expect(buttonWithText(el, 'Sil')?.disabled).toBe(true);
      expect(buttonWithText(el, 'AÇIK')?.disabled).toBe(true);
    });

    it('does not open the create form even when triggered directly', () => {
      component.startCreate();
      expect(component.isEditing).toBe(false);
    });

    it('does not open the edit form even when triggered directly', () => {
      component.startEdit(component.rules[0]);
      expect(component.isEditing).toBe(false);
    });

    it('never calls the write endpoints when the handlers are triggered directly', () => {
      component.startCreate();
      component.formName = 'denenecek';
      component.submit();
      expect(api.createRule).not.toHaveBeenCalled();

      component.toggleEnabled(component.rules[0]);
      expect(api.updateRule).not.toHaveBeenCalled();

      component.deleteRule(component.rules[0]);
      expect(api.deleteRule).not.toHaveBeenCalled();
    });
  });
});
