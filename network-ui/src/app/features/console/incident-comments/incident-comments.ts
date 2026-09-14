import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../../services/auth.service';
import { SiemApiService, describeProblem } from '../../../services/siem-api.service';
import { IncidentComment, IncidentCommentRequest } from '../../../services/siem.models';

/** Shown next to the disabled/hidden form for a role that cannot write comments. */
const READ_ONLY_ROLE_HINT = 'VIEWER rolü yorum ekleyemez; sunucu da reddeder.';

/**
 * Collapsible comment thread embedded in one incident card.
 *
 * Reading is open to every authenticated role (the server allows it), so the
 * list itself is never gated. Only the add-comment form is: `canRespond`
 * mirrors the same ANALYST/ADMIN gate the incident-transition button already
 * uses, because the server enforces that gate on this endpoint too. The
 * author field is never free text — it is always the signed-in username, so
 * nobody can post a comment under another operator's name.
 */
@Component({
  selector: 'app-incident-comments',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './incident-comments.html',
  styleUrl: './incident-comments.css',
})
export class IncidentComments {
  private readonly api = inject(SiemApiService);
  private readonly auth = inject(AuthService);

  readonly incidentId = input.required<number>();

  expanded = false;
  loading = false;
  loaded = false;
  error: string | null = null;
  comments: IncidentComment[] = [];

  formBody = '';
  submitting = false;
  submitError: string | null = null;

  get canRespond(): boolean {
    return this.auth.canRespond();
  }

  get roleHint(): string {
    return this.canRespond ? '' : READ_ONLY_ROLE_HINT;
  }

  get authorUsername(): string {
    return this.auth.currentUser()?.username ?? '—';
  }

  toggleExpanded(): void {
    this.expanded = !this.expanded;
    if (this.expanded && !this.loaded) {
      this.loadComments();
    }
  }

  loadComments(): void {
    this.loading = true;
    this.error = null;
    this.api.getIncidentComments(this.incidentId()).subscribe({
      next: (comments) => {
        this.comments = [...comments].sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
        );
        this.loading = false;
        this.loaded = true;
      },
      error: (error: unknown) => {
        this.error = describeProblem(error);
        this.loading = false;
        this.loaded = true;
      },
    });
  }

  dismissError(): void {
    this.error = null;
  }

  submit(): void {
    if (!this.canRespond || this.submitting) return;
    const body = this.formBody.trim();
    if (!body) return;

    const request: IncidentCommentRequest = { author: this.authorUsername, body };
    this.submitting = true;
    this.submitError = null;

    this.api.addIncidentComment(this.incidentId(), request).subscribe({
      next: (saved) => {
        this.submitting = false;
        this.comments = [...this.comments, saved];
        this.loaded = true;
        this.formBody = '';
      },
      error: (error: unknown) => {
        this.submitting = false;
        this.submitError = describeProblem(error);
      },
    });
  }

  dismissSubmitError(): void {
    this.submitError = null;
  }
}
