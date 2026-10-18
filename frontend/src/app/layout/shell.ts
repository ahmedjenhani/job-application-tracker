import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { timer } from 'rxjs';
import { errorMessage } from '../core/api';
import { ApplicationStore } from '../core/application-store';
import { AuthService } from '../core/auth';
import { AppNotification } from '../core/models';
import { NotificationService } from '../core/data-services';
import { ThemeService } from '../core/theme';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    DatePipe,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatBadgeModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly notifications = inject(NotificationService);
  private readonly store = inject(ApplicationStore);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  constructor() {
    this.store.load().catch((err) => this.snack.open(errorMessage(err), 'OK', { duration: 6000 }));
    // Reminders are created by a daily job on the server, so a slow poll is plenty.
    timer(0, 60_000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.notifications.refresh().catch(() => undefined));
  }

  protected async openNotification(n: AppNotification): Promise<void> {
    if (!n.read) {
      await this.notifications.markRead(n.id).catch(() => undefined);
    }
    if (n.applicationId) {
      await this.router.navigate(['/board'], { queryParams: { open: n.applicationId } });
    }
  }

  protected markAllRead(): void {
    this.notifications.markAllRead().catch(() => undefined);
  }

  protected logout(): void {
    this.store.clear();
    this.notifications.clear();
    this.auth.logout();
  }
}
