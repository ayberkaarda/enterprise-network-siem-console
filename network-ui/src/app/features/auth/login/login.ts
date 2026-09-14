import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../../services/auth.service';
import { REDIRECT_PARAM } from '../../../services/auth.guard';
import { describeProblem } from '../../../services/siem-api.service';

/** Where a successful sign-in goes when no return address was recorded. */
const DEFAULT_LANDING = '/';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  username = '';
  password = '';

  /** True while the credentials are in flight; the form stays disabled. */
  readonly submitting = signal(false);

  /**
   * Already mapped to a user-facing sentence. A 401 INVALID_CREDENTIALS is
   * explained by the shared error table, so the raw server text never reaches
   * the screen and the wording matches every other failure in the console.
   */
  readonly error = signal<string | null>(null);

  get canSubmit(): boolean {
    return !this.submitting() && this.username.trim().length > 0 && this.password.length > 0;
  }

  submit(): void {
    if (!this.canSubmit) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        this.submitting.set(false);
        // The password is dropped from the component the moment it is no
        // longer needed, rather than lingering in a field for the session.
        this.password = '';
        void this.router.navigateByUrl(this.landingUrl());
      },
      error: (failure: unknown) => {
        this.submitting.set(false);
        this.password = '';
        this.error.set(describeProblem(failure));
      },
    });
  }

  /** The page the guard bounced the user away from, or the console root. */
  private landingUrl(): string {
    const requested = this.route.snapshot.queryParamMap.get(REDIRECT_PARAM);
    // Only in-app paths are honoured; an absolute URL in the query string
    // would turn this redirect into an open redirect.
    if (requested && requested.startsWith('/') && !requested.startsWith('//')) {
      return requested;
    }
    return DEFAULT_LANDING;
  }
}
