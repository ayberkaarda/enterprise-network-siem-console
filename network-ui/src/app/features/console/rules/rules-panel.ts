import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../../services/auth.service';
import {
  SiemApiService,
  describeProblem,
  problemErrorCode,
} from '../../../services/siem-api.service';
import { IncidentSeverity, Rule, RuleRequest } from '../../../services/siem.models';

/** Sentinel `editingId` meaning "the form is open for a brand-new rule". */
const NEW_RULE_ID = -1;

/** Shown on every control a non-ADMIN role is not allowed to trigger. */
const MANAGE_ROLE_HINT = 'Bu aksiyon ADMIN rolü gerektirir; sunucu da reddeder.';

/**
 * Correlation rules CRUD screen. Listing is open to any authenticated role;
 * create/edit/delete are gated the same way the incident-transition and
 * device-action buttons already are elsewhere in the console: the control
 * stays visible but disabled, with a tooltip explaining why, and the
 * handler itself also refuses to act — the button state is a convenience,
 * not the only thing standing between a VIEWER and a write request.
 */
@Component({
  selector: 'app-rules-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rules-panel.html',
  styleUrl: './rules-panel.css',
})
export class RulesPanel implements OnInit {
  private readonly api = inject(SiemApiService);
  private readonly auth = inject(AuthService);

  readonly severities: readonly IncidentSeverity[] = [
    'CRITICAL',
    'HIGH',
    'MEDIUM',
    'LOW',
    'INFO',
  ];

  rules: Rule[] = [];
  loading = false;
  loaded = false;
  listError: string | null = null;

  editingId: number | null = null;
  formError: string | null = null;
  submitting = false;

  formName = '';
  formEnabled = true;
  formConditionJson = '{}';
  formThresholdCount = 3;
  formWindowSeconds = 300;
  formSeverity: IncidentSeverity = 'MEDIUM';

  pendingDeleteId: number | null = null;
  rowError: Record<number, string> = {};

  get canManage(): boolean {
    return this.auth.hasRole('ADMIN');
  }

  get roleHint(): string {
    return this.canManage ? '' : MANAGE_ROLE_HINT;
  }

  get isEditing(): boolean {
    return this.editingId !== null;
  }

  get isCreating(): boolean {
    return this.editingId === NEW_RULE_ID;
  }

  ngOnInit(): void {
    this.loadRules();
  }

  loadRules(): void {
    this.loading = true;
    this.listError = null;
    this.api.getRules({ page: 0, size: 100, sort: 'createdAt,desc' }).subscribe({
      next: (result) => {
        this.rules = result.content ?? [];
        this.loading = false;
        this.loaded = true;
      },
      error: (error: unknown) => {
        this.listError = describeProblem(error);
        this.loading = false;
        this.loaded = true;
      },
    });
  }

  startCreate(): void {
    if (!this.canManage) return;
    this.editingId = NEW_RULE_ID;
    this.resetForm();
  }

  startEdit(rule: Rule): void {
    if (!this.canManage) return;
    this.editingId = rule.id;
    this.formName = rule.name;
    this.formEnabled = rule.enabled;
    this.formConditionJson = rule.conditionJson;
    this.formThresholdCount = rule.thresholdCount;
    this.formWindowSeconds = rule.windowSeconds;
    this.formSeverity = rule.severity;
    this.formError = null;
  }

  cancelEdit(): void {
    this.editingId = null;
    this.resetForm();
  }

  submit(): void {
    if (!this.canManage || this.submitting || this.editingId === null) return;
    if (!this.formName.trim()) {
      this.formError = 'Kural adı zorunludur.';
      return;
    }
    if (!Number.isFinite(this.formThresholdCount) || this.formThresholdCount < 1) {
      this.formError = 'Eşik adedi en az 1 olmalıdır.';
      return;
    }
    if (!Number.isFinite(this.formWindowSeconds) || this.formWindowSeconds < 1) {
      this.formError = 'Pencere süresi en az 1 saniye olmalıdır.';
      return;
    }
    try {
      JSON.parse(this.formConditionJson);
    } catch {
      this.formError = 'Koşul JSON geçerli bir JSON metni değil.';
      return;
    }

    const body: RuleRequest = {
      name: this.formName.trim(),
      enabled: this.formEnabled,
      conditionJson: this.formConditionJson,
      thresholdCount: this.formThresholdCount,
      windowSeconds: this.formWindowSeconds,
      severity: this.formSeverity,
    };

    const isCreate = this.isCreating;
    this.submitting = true;
    this.formError = null;

    const request$ = isCreate
      ? this.api.createRule(body)
      : this.api.updateRule(this.editingId as number, body);

    request$.subscribe({
      next: (saved) => {
        this.submitting = false;
        this.rules = isCreate
          ? [saved, ...this.rules]
          : this.rules.map((r) => (r.id === saved.id ? saved : r));
        this.editingId = null;
        this.resetForm();
      },
      error: (error: unknown) => {
        this.submitting = false;
        this.formError = describeProblem(error);
      },
    });
  }

  toggleEnabled(rule: Rule): void {
    if (!this.canManage) return;
    const body: RuleRequest = {
      name: rule.name,
      enabled: !rule.enabled,
      conditionJson: rule.conditionJson,
      thresholdCount: rule.thresholdCount,
      windowSeconds: rule.windowSeconds,
      severity: rule.severity,
    };
    this.rowError = { ...this.rowError, [rule.id]: '' };
    this.api.updateRule(rule.id, body).subscribe({
      next: (saved) => {
        this.rules = this.rules.map((r) => (r.id === saved.id ? saved : r));
      },
      error: (error: unknown) => {
        this.rowError = { ...this.rowError, [rule.id]: describeProblem(error) };
      },
    });
  }

  deleteRule(rule: Rule): void {
    if (!this.canManage || this.pendingDeleteId !== null) return;
    if (!confirm(`"${rule.name}" kuralını silmek istediğinize emin misiniz?`)) return;

    this.pendingDeleteId = rule.id;
    this.rowError = { ...this.rowError, [rule.id]: '' };
    this.api.deleteRule(rule.id).subscribe({
      next: () => {
        this.pendingDeleteId = null;
        this.rules = this.rules.filter((r) => r.id !== rule.id);
      },
      error: (error: unknown) => {
        this.pendingDeleteId = null;
        this.rowError = { ...this.rowError, [rule.id]: describeProblem(error) };
        // A 404 means the local copy is stale; re-reading is what makes the
        // table match the server's real current set.
        if (problemErrorCode(error) === 'RULE_NOT_FOUND') {
          this.loadRules();
        }
      },
    });
  }

  dismissRowError(id: number): void {
    this.rowError = { ...this.rowError, [id]: '' };
  }

  severityClass(severity: IncidentSeverity): string {
    return `sev-${severity.toLowerCase()}`;
  }

  private resetForm(): void {
    this.formName = '';
    this.formEnabled = true;
    this.formConditionJson = '{}';
    this.formThresholdCount = 3;
    this.formWindowSeconds = 300;
    this.formSeverity = 'MEDIUM';
    this.formError = null;
  }
}
