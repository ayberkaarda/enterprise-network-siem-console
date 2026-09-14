import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { IncidentComments } from './incident-comments';
import { SiemApiService } from '../../../services/siem-api.service';
import { AuthService } from '../../../services/auth.service';
import { AuthRole, AuthUser, IncidentComment } from '../../../services/siem.models';

function comment(overrides: Partial<IncidentComment> & { id: number }): IncidentComment {
  return {
    incidentId: 7,
    author: 'analyst1',
    body: 'İlk bulgu notu',
    createdAt: '2026-09-14T10:00:00Z',
    ...overrides,
  };
}

/** Minimal stand-in for AuthService: just the members IncidentComments reads. */
class AuthStub {
  role: AuthRole = 'ANALYST';
  username = 'analyst1';

  currentUser(): AuthUser | null {
    return { username: this.username, role: this.role };
  }

  canRespond(): boolean {
    return this.role === 'ANALYST' || this.role === 'ADMIN';
  }
}

class ApiStub {
  getIncidentComments = vi.fn().mockReturnValue(
    of([
      comment({ id: 2, createdAt: '2026-09-14T11:00:00Z', body: 'İkinci yorum' }),
      comment({ id: 1, createdAt: '2026-09-14T10:00:00Z', body: 'İlk yorum' }),
    ]),
  );
  addIncidentComment = vi.fn().mockReturnValue(
    of(comment({ id: 3, createdAt: '2026-09-14T12:00:00Z', body: 'Yeni yorum' })),
  );
}

function problem(errorCode: string): HttpErrorResponse {
  return new HttpErrorResponse({ status: 400, error: { errorCode } });
}

describe('IncidentComments', () => {
  let auth: AuthStub;
  let api: ApiStub;
  let fixture: ComponentFixture<IncidentComments>;
  let component: IncidentComments;

  function setup(role: AuthRole): void {
    auth = new AuthStub();
    auth.role = role;
    api = new ApiStub();
    TestBed.configureTestingModule({
      imports: [IncidentComments],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: SiemApiService, useValue: api },
      ],
    });
    fixture = TestBed.createComponent(IncidentComments);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('incidentId', 7);
    fixture.detectChanges();
  }

  describe('as ANALYST', () => {
    beforeEach(() => setup('ANALYST'));

    it('does not fetch comments until expanded', () => {
      expect(api.getIncidentComments).not.toHaveBeenCalled();
    });

    it('fetches and sorts comments chronologically on first expand', () => {
      component.toggleExpanded();
      expect(api.getIncidentComments).toHaveBeenCalledWith(7);
      expect(component.comments.map((c) => c.id)).toEqual([1, 2]);
      expect(component.loading).toBe(false);
      expect(component.loaded).toBe(true);
    });

    it('does not re-fetch on a second expand', () => {
      component.toggleExpanded();
      component.toggleExpanded();
      component.toggleExpanded();
      expect(api.getIncidentComments).toHaveBeenCalledOnce();
    });

    it('renders an error state when the list read fails', () => {
      api.getIncidentComments.mockReturnValueOnce(throwError(() => problem('SOMETHING')));
      const el = fixture.nativeElement as HTMLElement;
      el.querySelector('button')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      fixture.detectChanges();
      expect(component.error).toBeTruthy();
      expect(el.querySelector('.inline-error')).toBeTruthy();
    });

    it('shows the add-comment form with the signed-in username locked in', () => {
      const el = fixture.nativeElement as HTMLElement;
      el.querySelector('button')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      fixture.detectChanges();
      expect(el.querySelector('textarea[name="commentBody"]')).toBeTruthy();
      expect(component.authorUsername).toBe('analyst1');
    });

    it('submits a comment using the current username as author and appends it', () => {
      component.toggleExpanded();
      component.formBody = 'Yeni yorum';
      component.submit();
      expect(api.addIncidentComment).toHaveBeenCalledWith(7, {
        author: 'analyst1',
        body: 'Yeni yorum',
      });
      expect(component.comments.some((c) => c.id === 3)).toBe(true);
      expect(component.formBody).toBe('');
      expect(component.submitting).toBe(false);
    });

    it('does not submit an empty or whitespace-only comment', () => {
      component.toggleExpanded();
      component.formBody = '   ';
      component.submit();
      expect(api.addIncidentComment).not.toHaveBeenCalled();
    });

    it('renders a submit error without discarding the draft', () => {
      api.addIncidentComment.mockReturnValueOnce(throwError(() => problem('VALIDATION_FAILED')));
      component.toggleExpanded();
      component.formBody = 'Denenecek';
      component.submit();
      expect(component.submitError).toBeTruthy();
      expect(component.submitting).toBe(false);
    });
  });

  describe('as VIEWER', () => {
    beforeEach(() => setup('VIEWER'));

    it('reports canRespond as false', () => {
      expect(component.canRespond).toBe(false);
    });

    it('still allows reading the comment list', () => {
      component.toggleExpanded();
      expect(api.getIncidentComments).toHaveBeenCalledWith(7);
      expect(component.comments).toHaveLength(2);
    });

    it('does not render the add-comment form', () => {
      const el = fixture.nativeElement as HTMLElement;
      el.querySelector('button')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      fixture.detectChanges();
      expect(el.querySelector('textarea[name="commentBody"]')).toBeNull();
    });

    it('never calls addIncidentComment when submit is triggered directly', () => {
      component.toggleExpanded();
      component.formBody = 'denenecek';
      component.submit();
      expect(api.addIncidentComment).not.toHaveBeenCalled();
    });
  });
});
