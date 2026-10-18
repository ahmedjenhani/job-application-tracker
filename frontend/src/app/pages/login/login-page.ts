import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api';
import { AuthService } from '../../core/auth';

const DEMO_EMAIL = 'demo@example.com';
const DEMO_PASSWORD = 'Demo@1234';
/** After this long we tell the user the (free) server is probably waking up. */
const SLOW_AFTER_MS = 3000;

@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './login-page.html',
  styleUrl: '../auth-page.scss',
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly pending = signal(false);
  protected readonly slow = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.pending()) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    await this.signIn(email, password);
  }

  protected async useDemo(): Promise<void> {
    this.form.setValue({ email: DEMO_EMAIL, password: DEMO_PASSWORD });
    await this.signIn(DEMO_EMAIL, DEMO_PASSWORD);
  }

  private async signIn(email: string, password: string): Promise<void> {
    this.error.set(null);
    this.pending.set(true);
    const slowTimer = setTimeout(() => this.slow.set(true), SLOW_AFTER_MS);
    try {
      await new Promise<void>((resolve, reject) =>
        this.auth.login(email, password).subscribe({ next: () => resolve(), error: reject }),
      );
      await this.router.navigate(['/board']);
    } catch (err) {
      this.error.set(errorMessage(err));
    } finally {
      clearTimeout(slowTimer);
      this.pending.set(false);
      this.slow.set(false);
    }
  }
}
